import XCTest
@testable import BillyApp

@MainActor
final class BleViewModelTests: XCTestCase {
    func testAppendEncounterMergesByName() async throws {
        let vm = BleViewModel(center: .default)

        await MainActor.run {
            NotificationCenter.default.post(
                name: .encounterDiscovered, object: nil,
                userInfo: ["name": "Alice", "idHex": "X1", "rssi": -50, "timestamp": Int64(1)]
            )
            NotificationCenter.default.post(
                name: .encounterDiscovered, object: nil,
                userInfo: ["name": "Alice", "idHex": "X2", "rssi": -52, "timestamp": Int64(2)]
            )
        }

        try await Task.sleep(nanoseconds: 50_000_000)
        let list = await vm.encounters
        XCTAssertEqual(list.count, 1)
        XCTAssertEqual(list.first?.name, "Alice")
        XCTAssertEqual(list.first?.count, 2)
    }
}
