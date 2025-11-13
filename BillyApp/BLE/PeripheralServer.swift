import CoreBluetooth
import Foundation
import Shared
import os.log

final class PeripheralServer: NSObject, CBPeripheralManagerDelegate {
    static let serviceUUID = CBUUID(string: "B5A3F1D0-5C1C-4E42-B2AA-19E7FDB8F4D5")
    static let payloadCharUUID = CBUUID(string: "B5A3F1D1-5C1C-4E42-B2AA-19E7FDB8F4D5")
    static let payloadLength = 16

    private let log = Logger(subsystem: "com.acme.billyapp", category: "Peripheral")
    private var mgr: CBPeripheralManager!
    private var payloadChar: CBMutableCharacteristic!
    private let queue: DispatchQueue

    init(queue: DispatchQueue) {
        self.queue = queue
        super.init()
        self.mgr = CBPeripheralManager(delegate: self, queue: queue)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        guard peripheral.state == .poweredOn else { return }
        setupService()
    }

    private func setupService() {
        payloadChar = CBMutableCharacteristic(
            type: Self.payloadCharUUID,
            properties: [.read],
            value: nil,
            permissions: [.readable]
        )
        let service = CBMutableService(type: Self.serviceUUID, primary: true)
        service.characteristics = [payloadChar]

        mgr.add(service)
        mgr.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [Self.serviceUUID],
            CBAdvertisementDataLocalNameKey: "BillyApp",
        ])
        log.info("[Peripheral] Advertising started")
    }

    @MainActor
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest)
    {
        guard request.characteristic.uuid == Self.payloadCharUUID else {
            mgr.respond(to: request, withResult: .attributeNotFound)
            return
        }
        request.value = buildPayload()
        mgr.respond(to: request, withResult: .success)
    }

    // 8-byte timestamp (big-endian) + 8-byte ciphertext → 16 bytes
    @MainActor
    private func buildPayload() -> Data {
        let ts = Int64(Date().timeIntervalSince1970)
        let tsBytes = ts.bigEndianBytes
        let cipher8: [UInt8]
        do {
            let crypto = try UserManager.shared.getCryptographyManager()
            let id = UserManager.shared.getPersonalIdentifier()
            let full = crypto.encryptRotatingIdentifier(personalIdHex: id, timestamp: ts)
            let fullData = full.toData()
            cipher8 = Array(fullData.suffix(8))
        } catch {
            cipher8 = Array(repeating: 0, count: 8)
        }
        return Data(tsBytes + cipher8)
    }
}
