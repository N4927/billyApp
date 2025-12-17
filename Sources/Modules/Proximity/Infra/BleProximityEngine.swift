import BillySDK
import Combine
import CoreBluetooth
import Foundation

/// @desc Infrastructure Adapter: BLE Proximity Engine.
/// Implements the `ProximityEngine` interface using CoreBluetooth.
/// Orchestrates the Hybrid Discovery Strategy (GAP + GATT) defined in the architecture.
///
/// @see report.tex Section 3.3 (Implementation)
/// @see report.tex Section 5.3 (iOS Module)
@MainActor
final class BleProximityEngine: NSObject, ProximityEngine {

    // MARK: - Constants

    // Defined in report.tex Section 3.3.1
    /// The Service UUID used for GATT Fallback and Scanning.
    private let serviceUUID = CBUUID(string: "B177")

    // Defined in report.tex Section 3.3.3
    /// The Characteristic UUID used for reading the BID in GATT Fallback mode.
    private let characteristicUUID = CBUUID(string: "B1770001-5E77-4C00-9000-ABCDEF123456")

    // Defined in report.tex Section 3.3.2
    /// The Company ID used in Manufacturer Specific Data (GAP).
    /// Value: 0x00B1 (Little Endian: B1 00)
    private let companyID: UInt16 = 0x00B1

    // MARK: - Dependencies

    private let core: BillyCore
    private let resolvedRepo: ResolvedRepository
    private let logger: AppLogger

    // MARK: - BLE Managers

    private var centralManager: CBCentralManager!
    private var peripheralManager: CBPeripheralManager!

    // MARK: - State

    public private(set) var isRunning: Bool = false
    private var syncTask: Task<Void, Never>?
    private var rotationTask: Task<Void, Never>?

    // Keep track of active connections for GATT fallback
    private var pendingConnections: [UUID: CBPeripheral] = [:]

    // Debounce cache to prevent spamming connections to the same peripheral
    private var discoveryCooldowns: [UUID: Date] = [:]

    // MARK: - Initialization

    /**
     * @desc Initializes the BLE Engine with KMM Core dependencies.
     * Configures CoreBluetooth managers with State Restoration support.
     *
     * @param core - The KMM Core logic.
     * @param resolvedRepo - The KMM Repository for observing state.
     * @param logger - Structured logger.
     */
    init(core: BillyCore, resolvedRepo: ResolvedRepository, logger: AppLogger) {
        self.core = core
        self.resolvedRepo = resolvedRepo
        self.logger = logger
        super.init()

        // Initialize managers with restoration identifiers for background support
        // Reference: report.tex Section 5.3 (BleController)
        self.centralManager = CBCentralManager(
            delegate: self, queue: nil,
            options: [
                CBCentralManagerOptionRestoreIdentifierKey: "com.billyapp.central",
                CBCentralManagerOptionShowPowerAlertKey: true,
            ])
        self.peripheralManager = CBPeripheralManager(
            delegate: self, queue: nil,
            options: [
                CBPeripheralManagerOptionRestoreIdentifierKey: "com.billyapp.peripheral"
            ])
    }

    // MARK: - Public API

    /**
     * @desc Starts the Proximity Engine.
     * Initiates background sync tasks, scanning, and advertising.
     */
    func start() {
        guard !isRunning else { return }
        isRunning = true
        logger.info("BleProximityEngine: Starting...")

        // 1. Start Background Sync Loop (Queue & Batch)
        startBackgroundTasks()

        // 2. Trigger BLE actions (if powered on)
        if centralManager.state == .poweredOn {
            startScanning()
        }
        if peripheralManager.state == .poweredOn {
            updateAdvertising()
        }
    }

    /**
     * @desc Stops the Proximity Engine.
     * Cancels tasks, stops BLE operations, and cleans up connections.
     */
    func stop() {
        isRunning = false
        logger.info("BleProximityEngine: Stopping...")

        syncTask?.cancel()
        rotationTask?.cancel()

        if centralManager.state == .poweredOn {
            centralManager.stopScan()
        }

        if peripheralManager.state == .poweredOn {
            peripheralManager.stopAdvertising()
        }

        // Cleanup pending connections
        for peripheral in pendingConnections.values {
            centralManager.cancelPeripheralConnection(peripheral)
        }
        pendingConnections.removeAll()
    }

    /**
     * @desc Observes the KMM ResolvedRepository for changes in the active user set.
     * @returns An AsyncStream of [User] entities.
     */
    func observeUsers() -> AsyncStream<[User]> {
        return AsyncStream { continuation in
            let observationTask = Task {
                while !Task.isCancelled {
                    // Observe KMM ResolvedRepository
                    if let kmmUsers = resolvedRepo.activeSet.value as? [ResolvedUser] {
                        let domainUsers = kmmUsers.compactMap { kmmUser -> User? in
                            switch DisplayName.create(kmmUser.name) {
                            case .success(let displayName):
                                return User(
                                    name: displayName,
                                    lastSeen: Date(
                                        timeIntervalSince1970: TimeInterval(
                                            kmmUser.lastSeen.epochSeconds))
                                )
                            case .failure:
                                return nil
                            }
                        }
                        continuation.yield(domainUsers)
                    }
                    try? await Task.sleep(nanoseconds: 1 * 1_000_000_000)
                }
            }
            continuation.onTermination = { _ in
                observationTask.cancel()
            }
        }
    }

    // MARK: - Private Logic

    /**
     * @desc Starts the background maintenance tasks (Queue Sync and Key Rotation).
     */
    private func startBackgroundTasks() {
        // Task 1: Sync Queue with Backend
        // Reference: report.tex Section 5.1.4 (Queue Sync)
        syncTask = Task {
            while !Task.isCancelled {
                do {
                    logger.info(
                        "API: Starting Queue Sync...", context: ["operation": "syncQueue"])
                    try await core.syncQueue()
                    logger.info(
                        "API: Queue Sync Completed Successfully",
                        context: ["operation": "syncQueue", "status": "success"])
                } catch {
                    logger.error(
                        "API: Sync Failed", error: error,
                        context: ["operation": "syncQueue", "status": "failed"])
                }
                // Sleep for 10 seconds
                try? await Task.sleep(nanoseconds: 10 * 1_000_000_000)
            }
        }

        // Task 2: Ensure Batch & Rotate Keys
        // Reference: report.tex Section 5.1.3 (Batch Sync)
        rotationTask = Task {
            while !Task.isCancelled {
                do {
                    logger.debug(
                        "API: Checking Advertising Batch...",
                        context: ["operation": "ensureAdvertisingBatch"])
                    try await core.ensureAdvertisingBatch()
                    await MainActor.run {
                        self.updateAdvertising()
                    }
                    logger.debug(
                        "API: Batch Check completed",
                        context: ["operation": "ensureAdvertisingBatch", "status": "success"])
                } catch {
                    logger.error(
                        "API: Batch Error", error: error,
                        context: ["operation": "ensureAdvertisingBatch", "status": "failed"])
                }
                // Sleep for 60 seconds
                try? await Task.sleep(nanoseconds: 60 * 1_000_000_000)
            }
        }
    }

    /**
     * @desc Updates the BLE Advertising data with the current BID.
     * Configures both GAP (Manufacturer Data) and GATT (Service) advertising.
     */
    private func updateAdvertising() {
        guard peripheralManager.state == .poweredOn else { return }

        // Get current BID from Core
        // Note: getCurrentBid returns a KMM Bid object. We access the .hex property.
        guard let bid = core.getCurrentBid() as? String else {
            logger.warning("No BID available for advertising.")
            return
        }

        let bidHex = bid
        guard let bidData = Data(hexString: bidHex) else {
            logger.error("Invalid Hex String from Core: \(bidHex)")
            return
        }

        // 1. Setup GATT Service (for Fallback)
        setupGattService(bidData: bidData)

        // 2. Setup GAP Advertising (Manufacturer Data)
        // Format: [CompanyID (2 bytes)] + [BID (16 bytes)]
        // Reference: report.tex Section 3.3.2 (GAP)
        var msd = Data()
        withUnsafeBytes(of: companyID.littleEndian) { msd.append(contentsOf: $0) }
        msd.append(bidData)

        let advertisementData: [String: Any] = [
            CBAdvertisementDataServiceUUIDsKey: [serviceUUID],
            CBAdvertisementDataManufacturerDataKey: msd,
        ]

        // --- DEBUG: Packet Size & Content ---
        let estimatedSize = 3 + (16 + 2) + (msd.count + 2)
        logger.debug("Packet Size: \(estimatedSize) bytes (Limit: 31)")
        logger.debug("Service UUID: \(serviceUUID.uuidString)")
        logger.debug("Manufacturer Data: \(msd.hexString)")

        if estimatedSize > 31 {
            logger.warning("Packet Overflow: 'Manufacturer Data' will be stripped by iOS.")
        }
        // ------------------------------------

        peripheralManager.stopAdvertising()
        peripheralManager.startAdvertising(advertisementData)
        logger.info("Advertising BID: \(bidHex)")
    }

    /**
     * @desc Configures the GATT Service for the Fallback Strategy.
     * Adds the Service and Characteristic to the Peripheral Manager.
     * @param bidData - The current BID to expose via the Characteristic.
     */
    private func setupGattService(bidData: Data) {
        peripheralManager.removeAllServices()

        let characteristic = CBMutableCharacteristic(
            type: characteristicUUID,
            properties: [.read],
            value: bidData,
            permissions: [.readable]
        )

        let service = CBMutableService(type: serviceUUID, primary: true)
        service.characteristics = [characteristic]

        peripheralManager.add(service)
    }

    /**
     * @desc Starts scanning for peripherals advertising the Billy Service UUID.
     */
    private func startScanning() {
        guard centralManager.state == .poweredOn else { return }

        // Reference: report.tex Section 3.3.1 (Prelude - U_srv)
        centralManager.scanForPeripherals(
            withServices: [serviceUUID],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: true]
        )
        logger.info("Scanning started...")
    }
}

// MARK: - CBCentralManagerDelegate (Scanning)

extension BleProximityEngine: @preconcurrency CBCentralManagerDelegate {

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            logger.info("Bluetooth is Powered ON")
            if isRunning { startScanning() }
        case .poweredOff:
            logger.warning("Bluetooth is Powered OFF")
        case .unauthorized:
            logger.error("Bluetooth is Unauthorized")
        case .unsupported:
            logger.error("Bluetooth is Unsupported")
        case .resetting:
            logger.warning("Bluetooth is Resetting")
        case .unknown:
            logger.warning("Bluetooth state is Unknown")
        @unknown default:
            break
        }
    }

    func centralManager(
        _ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any], rssi RSSI: NSNumber
    ) {

        // STRATEGY: Hybrid Discovery
        // Reference: report.tex Section 3.3.3 (Hybrid Discovery)

        // 1. Try GAP (Manufacturer Data)
        if let msd = advertisementData[CBAdvertisementDataManufacturerDataKey] as? Data {
            // Validate Company ID (First 2 bytes)
            // Note: CoreBluetooth usually returns the whole block including Company ID
            if msd.count >= 18 {
                let receivedCompanyID = msd.prefix(2).withUnsafeBytes { $0.load(as: UInt16.self) }
                if receivedCompanyID == companyID.littleEndian {
                    let bidData = msd.subdata(in: 2..<18)
                    let bidHex = bidData.hexString
                    core.ingestPacket(bidHex: bidHex)
                    logger.info("GAP Hit: \(bidHex) (RSSI: \(RSSI))")
                    return
                }
            }
        }

        // 2. Fallback to GATT (iOS Background Sender)
        // If we see the Service UUID but no valid MSD, we connect.
        // We limit concurrent connections to avoid saturation.
        if pendingConnections[peripheral.identifier] == nil {

            // Check Cooldown (Prevent Spam)
            if let lastSeen = discoveryCooldowns[peripheral.identifier],
                Date().timeIntervalSince(lastSeen) < 10
            {
                // Too soon, ignore this peripheral
                return
            }

            logger.info(
                "GATT Fallback: Connecting to \(peripheral.identifier) (Service Found, No MSD)")
            pendingConnections[peripheral.identifier] = peripheral
            peripheral.delegate = self
            centralManager.connect(peripheral, options: nil)
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        peripheral.discoverServices([serviceUUID])
    }

    func centralManager(
        _ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?
    ) {
        logger.error("Failed to connect: \(error?.localizedDescription ?? "Unknown")")
        pendingConnections.removeValue(forKey: peripheral.identifier)
    }

    func centralManager(
        _ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?
    ) {
        pendingConnections.removeValue(forKey: peripheral.identifier)
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        logger.info("Restoring Central Manager State")
        if let peripherals = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] {
            for peripheral in peripherals {
                peripheral.delegate = self
                pendingConnections[peripheral.identifier] = peripheral
                if peripheral.state == .connected {
                    // Resume discovery if needed, or just keep reference
                    peripheral.discoverServices([serviceUUID])
                }
            }
        }
    }
}

// MARK: - CBPeripheralDelegate (GATT Client)

extension BleProximityEngine: @preconcurrency CBPeripheralDelegate {

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services where service.uuid == serviceUUID {
            peripheral.discoverCharacteristics([characteristicUUID], for: service)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?
    ) {
        guard let characteristics = service.characteristics else { return }
        for characteristic in characteristics where characteristic.uuid == characteristicUUID {
            peripheral.readValue(for: characteristic)
        }
    }

    func peripheral(
        _ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if let data = characteristic.value {
            let bidHex = data.hexString
            core.ingestPacket(bidHex: bidHex)
            logger.info("GATT Hit: \(bidHex) (via Connection)")

            // Update Cooldown
            discoveryCooldowns[peripheral.identifier] = Date()
        }

        // Job done, disconnect
        centralManager.cancelPeripheralConnection(peripheral)
    }
}

// MARK: - CBPeripheralManagerDelegate (Advertising)

extension BleProximityEngine: @preconcurrency CBPeripheralManagerDelegate {

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if peripheral.state == .poweredOn && isRunning {
            updateAdvertising()
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, willRestoreState dict: [String: Any])
    {
        logger.info("Restoring Peripheral Manager State")
        // No specific action needed for advertising restoration as we re-trigger updateAdvertising on poweredOn
    }
}
