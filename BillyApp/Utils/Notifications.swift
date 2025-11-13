import Foundation

// Definizione *univoca* della notifica usata in tutto il progetto.
// Usata da: BluetoothManager -> post(.encounterDiscovered)
// Ascoltata da: BleViewModel -> NotificationCenter.publisher(for: .encounterDiscovered)
extension Notification.Name {
    static let encounterDiscovered = Notification.Name("EncounterDiscovered")
}
