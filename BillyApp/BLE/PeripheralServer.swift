import CoreBluetooth
import Foundation
import Shared
import UIKit
import os.log

final class PeripheralServer: NSObject, CBPeripheralManagerDelegate {
    static let serviceUUID = CBUUID(string: "B5A3F1D0-5C1C-4E42-B2AA-19E7FDB8F4D5")
    static let payloadCharUUID = CBUUID(string: "B5A3F1D1-5C1C-4E42-B2AA-19E7FDB8F4D5")
    static let payloadLength = 16

    private let log = BLELog.logger("Peripheral")
    private var mgr: CBPeripheralManager!
    private var payloadChar: CBMutableCharacteristic!
    private let queue: DispatchQueue

    private var didAddService = false
    private var isAdvertising = false

    init(queue: DispatchQueue) {
        self.queue = queue
        super.init()
        self.mgr = CBPeripheralManager(delegate: self, queue: queue)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        log.info("peripheral.state=\(peripheral.state.rawValue, privacy: .public)")
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

        mgr.add(service)  // async → aspettiamo didAdd prima di pubblicizzare
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?
    ) {
        if let error {
            log.error("didAdd service error: \(String(describing: error), privacy: .public)")
        }
        didAddService = (error == nil)
        if didAddService { startAdvertisingIfPossible() }
    }

    private func startAdvertisingIfPossible() {
        guard !isAdvertising, didAddService, mgr.state == .poweredOn else { return }
        let auth = CBManager.authorization
        log.info("authorization=\(auth.rawValue, privacy: .public)")

        // 👉 NIENTE Service Data: iOS non lo supporta nel peripheral manager.
        mgr.startAdvertising([
            CBAdvertisementDataServiceUUIDsKey: [Self.serviceUUID],
            CBAdvertisementDataLocalNameKey: "BillyApp",
        ])
        isAdvertising = true
        log.info("Advertising started (service=\(Self.serviceUUID.uuidString, privacy: .public))")
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error {
            log.error("startAdvertising failed: \(String(describing: error), privacy: .public)")
        }
    }

    @MainActor
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest)
    {
        guard request.characteristic.uuid == Self.payloadCharUUID else {
            mgr.respond(to: request, withResult: .attributeNotFound)
            return
        }
        let value = buildPayload()
        request.value = value
        mgr.respond(to: request, withResult: .success)

        if BLELog.verbose {
            let devName = UIDevice.current.name
            let myId = UserManager.shared.getPersonalIdentifier()
            log.debug(
                "READ by central id=\(request.central.identifier.uuidString, privacy: .public) mtu=\(request.central.maximumUpdateValueLength, privacy: .public)"
            )
            log.debug(
                "payload(16B)=\(value.hex, privacy: .public) localDevice=\(devName, privacy: .public) myId=\(myId, privacy: .public)"
            )
        }
    }

    @MainActor
    // 8B timestamp (big-endian) + 8B ciphertext → 16 bytes
    private func buildPayload() -> Data {
        let ts = Int64(Date().timeIntervalSince1970)
        let tsBytes = ts.bigEndianBytes
        let cipher8: [UInt8]
        do {
            let crypto = try UserManager.shared.getCryptographyManager()
            // 🔑 FIX: Usa personalId dal segreto, non l'UUID utente
            let full = crypto.encryptRotatingIdentifier(timestamp: ts)
            let fullData = full.toData()
            cipher8 = Array(fullData.suffix(8))
        } catch {
            cipher8 = Array(repeating: 0, count: 8)
        }
        return Data(tsBytes + cipher8)
    }

    func stopAdvertising() {
        guard isAdvertising else { return }
        mgr.stopAdvertising()
        isAdvertising = false
        log.info("Advertising stopped")
    }
}
