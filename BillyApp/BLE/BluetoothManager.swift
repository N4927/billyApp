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
        registerBackgroundTask()
    }

    private func registerBackgroundTask() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: "com.acme.billyapp.bluetooth-processing", using: nil
        ) { task in
            self.log.info("[BGTask] bluetooth-processing fired")
            // Reschedule next
            let req = BGProcessingTaskRequest(identifier: "com.acme.billyapp.bluetooth-processing")
            req.requiresNetworkConnectivity = false
            req.requiresExternalPower = false
            req.earliestBeginDate = Date(timeIntervalSinceNow: 60)
            try? BGTaskScheduler.shared.submit(req)

            task.expirationHandler = { /* cleanup if needed */  }
            // non completiamo: lasciamo a iOS la gestione
        }
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
