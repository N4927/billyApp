import XCTest
import Combine
@testable import BillyApp

@MainActor
final class BleViewModelTests: XCTestCase {

    // Fresh bag per test; do not reuse across tests
    private var bag: Set<AnyCancellable>!

    override func setUp() {
        super.setUp()
        bag = []
    }

    override func tearDown() {
        // Cancel any remaining subscriptions to avoid late fulfills
        bag.forEach { $0.cancel() }
        bag.removeAll()
        bag = nil
        super.tearDown()
    }

    func testAppendEncounterMergesByName() {
        // Use a dedicated center so tests don't interfere via .default
        let center = NotificationCenter()
        let vm = BleViewModel(center: center)

        // We expect two emissions (two posts), then the pipeline auto-cancels via prefix(2)
        let exp = expectation(description: "encounters updated twice")

        var updates = 0
        vm.$encounters
            .dropFirst()       // skip initial []
            .prefix(2)         // receive exactly 2 updates, then complete
            .sink { _ in
                updates += 1
                if updates == 2 { exp.fulfill() }
            }
            .store(in: &bag)

        center.post(
            name: .encounterDiscovered, object: nil,
            userInfo: ["name": "Alice", "idHex": "X1", "rssi": -50, "timestamp": Int64(1)]
        )
        center.post(
            name: .encounterDiscovered, object: nil,
            userInfo: ["name": "Alice", "idHex": "X2", "rssi": -52, "timestamp": Int64(2)]
        )

        wait(for: [exp], timeout: 1.0)

        XCTAssertEqual(vm.encounters.count, 1)
        XCTAssertEqual(vm.encounters.first?.name, "Alice")
        XCTAssertEqual(vm.encounters.first?.count, 2)
    }

    func testClear() {
        let center = NotificationCenter()
        let vm = BleViewModel(center: center)

        // Wait only for the first append, then the pipeline auto-cancels via prefix(1)
        let exp = expectation(description: "first encounter appended")
        vm.$encounters
            .dropFirst()
            .prefix(1)
            .sink { _ in exp.fulfill() }
            .store(in: &bag)

        center.post(
            name: .encounterDiscovered, object: nil,
            userInfo: ["name": "Bob", "idHex": "Y1", "rssi": -40, "timestamp": Int64(1)]
        )

        wait(for: [exp], timeout: 1.0)

        XCTAssertFalse(vm.encounters.isEmpty, "Expected non-empty after post")

        vm.clear()

        XCTAssertTrue(vm.encounters.isEmpty, "Expected empty after clear()")
    }
}
