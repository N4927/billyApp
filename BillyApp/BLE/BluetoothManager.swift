import BackgroundTasks
import CoreBluetooth
import Foundation
import os.log

final class BluetoothManager: NSObject {
    static let shared = BluetoothManager()

    private let centralQueue = DispatchQueue(label: "billy.central", qos: .utility)
    private let peripheralQueue = DispatchQueue(label: "billy.peripheral", qos: .utility)

    private var advertiser: PeripheralServer?
    private var scanner: CentralClient?

    weak var sink: EncounterSink?

    private let log = BLELog.logger("BLE")

    override init() {
        super.init()
        // NON avviare subito. L’avvio lo decide l’UI (online) o il BG task.
    }

    func goOnline() {
        if advertiser == nil { advertiser = PeripheralServer(queue: peripheralQueue) }
        if scanner == nil { scanner = CentralClient(queue: centralQueue, sink: self) }
        scanner?.startScan()
        log.info("BLE ONLINE")
    }

    func goOffline() {
        scanner?.stopScan()
        advertiser?.stopAdvertising()
        log.info("BLE OFFLINE")
    }
}

extension BluetoothManager: EncounterSink {
    func onEncounter(name: String, idHex: String, rssi: Int, timestamp: Int64) {
        NotificationCenter.default.post(
            name: .encounterDiscovered,
            object: nil,
            userInfo: ["name": name, "idHex": idHex, "rssi": rssi, "timestamp": timestamp]
        )
    }
}
