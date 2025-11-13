import CoreBluetooth
import Foundation
import os.log

protocol EncounterSink: AnyObject {
    func onEncounter(name: String, idHex: String, rssi: Int, timestamp: Int64)
}

final class CentralClient: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private let log = Logger(subsystem: "com.acme.billyapp", category: "Central")
    private var central: CBCentralManager!
    private var seen = Set<UUID>()
    private weak var sink: EncounterSink?
    private let queue: DispatchQueue

    init(queue: DispatchQueue, sink: EncounterSink?) {
        self.queue = queue
        self.sink = sink
        super.init()
        self.central = CBCentralManager(delegate: self, queue: queue)
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        guard central.state == .poweredOn else { return }
        central.scanForPeripherals(
            withServices: [PeripheralServer.serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
        log.info("[Central] Scanning...")
    }

    func centralManager(
        _ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any], rssi RSSI: NSNumber
    ) {
        guard !seen.contains(peripheral.identifier) else { return }
        seen.insert(peripheral.identifier)
        peripheral.delegate = self
        central.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([PeripheralServer.serviceUUID])
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard
            let service = peripheral.services?.first(where: {
                $0.uuid == PeripheralServer.serviceUUID
            })
        else {
            central.cancelPeripheralConnection(peripheral)
            return
        }
        peripheral.discoverCharacteristics([PeripheralServer.payloadCharUUID], for: service)
    }

    func peripheral(
        _ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?
    ) {
        guard
            let ch = service.characteristics?.first(where: {
                $0.uuid == PeripheralServer.payloadCharUUID
            })
        else {
            central.cancelPeripheralConnection(peripheral)
            return
        }
        peripheral.readValue(for: ch)
    }

    func peripheral(
        _ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        defer { central.cancelPeripheralConnection(peripheral) }
        guard error == nil, let data = characteristic.value,
            data.count == PeripheralServer.payloadLength
        else { return }

        // Resolve via KMM
        if let user = KMMFacade.resolveUser(from: data) {
            let ts = Int64.fromBigEndianBytes(Array(data.prefix(8)))
            sink?.onEncounter(
                name: user.displayName, idHex: peripheral.identifier.uuidString, rssi: -99,
                timestamp: ts)
            log.info("[Central] Encounter resolved: \(user.displayName, privacy: .public)")
        }
    }
}
