import CoreBluetooth
import Foundation
import os.log

protocol EncounterSink: AnyObject {
    func onEncounter(name: String, idHex: String, rssi: Int, timestamp: Int64)
}

final class CentralClient: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private let log = BLELog.logger("Central")
    private var central: CBCentralManager!

    // Per evitare doppie connect e tenere viva la reference finché serve
    private var inflight: [UUID: CBPeripheral] = [:]
    private var lastRSSI: [UUID: Int] = [:]
    private let queue: DispatchQueue
    private weak var sink: EncounterSink?

    private var isScanning = false

    init(queue: DispatchQueue, sink: EncounterSink?) {
        self.queue = queue
        self.sink = sink
        super.init()
        self.central = CBCentralManager(delegate: self, queue: queue)
    }

    // MARK: - Start/Stop Scan (usate da BluetoothManager)
    func startScan() {
        guard central.state == .poweredOn else { return }
        guard !isScanning else { return }
        isScanning = true

        // Sempre filtrato per il nostro Service UUID (riduce rumore e trova solo la nostra app)
        central.scanForPeripherals(
            withServices: [PeripheralServer.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
        log.info(
            "[Central] Scanning (filter=\(PeripheralServer.serviceUUID.uuidString, privacy: .public))"
        )
    }

    func stopScan() {
        guard isScanning else { return }
        central.stopScan()
        isScanning = false
        log.info("[Central] Scan stopped")
    }

    // MARK: - CBCentralManagerDelegate
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        log.info(
            "central.state=\(central.state.rawValue, privacy: .public) auth=\(CBManager.authorization.rawValue, privacy: .public)"
        )
        if central.state == .poweredOn { startScan() }
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber
    ) {

        let id = peripheral.identifier
        if inflight[id] != nil { return }  // già in corso

        lastRSSI[id] = RSSI.intValue
        inflight[id] = peripheral  // 👉 strong reference finché non abbiamo finito
        peripheral.delegate = self

        if BLELog.verbose {
            let localName = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? "-"
            let uuids =
                (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID])?.map(
                    \.uuidString
                ).joined(separator: ",") ?? "-"
            let connectable =
                (advertisementData[CBAdvertisementDataIsConnectable] as? NSNumber)?.boolValue
                ?? true
            log.debug(
                "DISCOVER id=\(id.uuidString, privacy: .public) name=\(peripheral.name ?? "-", privacy: .public) rssi=\(RSSI.intValue, privacy: .public) advName=\(localName, privacy: .public) uuids=\(uuids, privacy: .public) connectable=\(connectable, privacy: .public)"
            )
        }

        central.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        log.info("CONNECTED id=\(peripheral.identifier.uuidString, privacy: .public)")
        peripheral.discoverServices([PeripheralServer.serviceUUID])
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        log.error(
            "FAIL_CONNECT id=\(peripheral.identifier.uuidString, privacy: .public) err=\(String(describing: error), privacy: .public)"
        )
        cleanup(for: peripheral)
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        if let error {
            log.debug(
                "DISCONNECT id=\(peripheral.identifier.uuidString, privacy: .public) err=\(String(describing: error), privacy: .public)"
            )
        }
        cleanup(for: peripheral)
    }

    // MARK: - CBPeripheralDelegate
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            log.error("DISCOVER_SERVICES_ERR \(String(describing: error), privacy: .public)")
        }
        guard
            let service = peripheral.services?.first(where: {
                $0.uuid == PeripheralServer.serviceUUID
            })
        else {
            log.debug("SERVICE_NOT_FOUND on \(peripheral.identifier.uuidString, privacy: .public)")
            central.cancelPeripheralConnection(peripheral)
            return
        }
        log.debug("SERVICE_OK \(service.uuid.uuidString, privacy: .public)")
        peripheral.discoverCharacteristics([PeripheralServer.payloadCharUUID], for: service)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        if let error {
            log.error("DISCOVER_CHARS_ERR \(String(describing: error), privacy: .public)")
        }
        guard
            let ch = service.characteristics?.first(where: {
                $0.uuid == PeripheralServer.payloadCharUUID
            })
        else {
            log.debug("CHAR_NOT_FOUND")
            central.cancelPeripheralConnection(peripheral)
            return
        }
        log.debug("CHAR_OK \(ch.uuid.uuidString, privacy: .public) → readValue")
        peripheral.readValue(for: ch)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        defer { central.cancelPeripheralConnection(peripheral) }
        guard error == nil, let data = characteristic.value else {
            log.error("READ_ERR \(String(describing: error), privacy: .public)")
            return
        }
        guard data.count == PeripheralServer.payloadLength else {
            log.error("READ_LEN_INVALID len=\(data.count, privacy: .public)")
            return
        }

        if BLELog.verbose {
            log.debug(
                "READ_OK id=\(peripheral.identifier.uuidString, privacy: .public) payload=\(data.hex, privacy: .public)"
            )
        }

        if let user = KMMFacade.resolveUser(from: data) {
            let ts = Int64.fromBigEndianBytes(Array(data.prefix(8)))
            let rssi = lastRSSI[peripheral.identifier] ?? -99
            sink?.onEncounter(
                name: user.displayName,
                idHex: peripheral.identifier.uuidString,
                rssi: rssi,
                timestamp: ts
            )
            log.info(
                "ENCOUNTER name=\(user.displayName, privacy: .public) rssi=\(rssi, privacy: .public)"
            )
        } else {
            log.debug("RESOLVE_FAIL")
        }
    }

    // MARK: - Helpers
    private func cleanup(for peripheral: CBPeripheral) {
        lastRSSI[peripheral.identifier] = nil
        inflight[peripheral.identifier] = nil
    }
}
