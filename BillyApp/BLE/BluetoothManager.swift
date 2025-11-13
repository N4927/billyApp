import BackgroundTasks
import CoreBluetooth
import Foundation
import os.log

final class BluetoothManager: NSObject {

    static let shared = BluetoothManager()

    private let centralQueue = DispatchQueue(label: "billy.central", qos: .utility)
    private let peripheralQueue = DispatchQueue(label: "billy.peripheral", qos: .utility)

    private var advertiser: PeripheralServer!
    private var scanner: CentralClient!

    weak var sink: EncounterSink?

    private let log = Logger(subsystem: "com.acme.billyapp", category: "BLE")

    override init() {
        super.init()
        advertiser = PeripheralServer(queue: peripheralQueue)
        scanner = CentralClient(queue: centralQueue, sink: self)
        // RIMOSSO: la registrazione del BGTask ora è in AppDelegate
    }
}

extension BluetoothManager: EncounterSink {
    func onEncounter(name: String, idHex: String, rssi: Int, timestamp: Int64) {
        NotificationCenter.default.post(
            name: .encounterDiscovered, object: nil,
            userInfo: [
                "name": name, "idHex": idHex, "rssi": rssi, "timestamp": timestamp,
            ])
    }
}

extension Notification.Name {
    static let encounterDiscovered = Notification.Name("EncounterDiscovered")
}
