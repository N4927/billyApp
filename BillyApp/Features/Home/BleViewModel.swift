import Combine
import Foundation
import os

struct ResolvedEncounter: Identifiable, Equatable {
    let id = UUID()
    let name: String
    let count: Int
    let idHex: String
    let timestamp: Int64
}

@MainActor
final class BleViewModel: ObservableObject {
    @Published private(set) var encounters: [ResolvedEncounter] = []
    private let logger = AppLogger.make(category: "BleVM")
    private var bag = Set<AnyCancellable>()

    init(center: NotificationCenter = .default) {
        center.publisher(for: .encounterDiscovered)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] note in
                guard
                    let name = note.userInfo?["name"] as? String,
                    let idHex = note.userInfo?["idHex"] as? String,
                    let rssi = note.userInfo?["rssi"] as? Int,
                    let ts = note.userInfo?["timestamp"] as? Int64
                else { return }
                self?.appendEncounter(name: name, idHex: idHex, rssi: rssi, ts: ts)
            }
            .store(in: &bag)
    }

    private func appendEncounter(name: String, idHex: String, rssi: Int, ts: Int64) {
        if let idx = encounters.firstIndex(where: { $0.name == name }) {
            var current = encounters[idx]
            encounters[idx] = ResolvedEncounter(
                name: current.name, count: current.count + 1, idHex: idHex, timestamp: ts)
        } else {
            encounters.append(ResolvedEncounter(name: name, count: 1, idHex: idHex, timestamp: ts))
        }
        logger.info("encounters=\(self.encounters.count, privacy: .public)")
    }
}
