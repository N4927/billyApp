import Foundation
import Shared

@MainActor
final class UserManager: ObservableObject {
    static let shared = UserManager()

    private let defaults = UserDefaults.standard
    private let keyId = "user_id"
    private let keyName = "user_name"
    private let keyAge = "user_age"
    private let keyBio = "user_bio"
    private let keyBleOnline = "ble_online"

    private var cryptographyManager: CryptographyManager?

    func saveUser(id: String, name: String, age: Int? = nil, bio: String? = nil) {
        defaults.set(id, forKey: keyId)
        defaults.set(name, forKey: keyName)
        if let age {
            defaults.set(age, forKey: keyAge)
        } else {
            defaults.removeObject(forKey: keyAge)
        }
        defaults.set(bio, forKey: keyBio)
        try? initCryptographyManager()
    }

    func clearUser() {
        [keyId, keyName, keyAge, keyBio].forEach { defaults.removeObject(forKey: $0) }
        cryptographyManager = nil
    }

    func getUserName() -> String {
        defaults.string(forKey: keyName) ?? "Anonimo"
    }

    func getPersonalIdentifier() -> String {
        defaults.string(forKey: keyId) ?? "anonymous"
    }

    func getSecretForBle(length: Int = 16) -> [UInt8] {
        let name = getUserName().lowercased()
        var secret = Array(name.utf8)
        if secret.count < length {
            secret.append(contentsOf: Array(repeating: 0, count: length - secret.count))
        } else if secret.count > length {
            secret = Array(secret.prefix(length))
        }
        return secret
    }

    func getCryptographyManager() throws -> CryptographyManager {
        if let c = cryptographyManager { return c }
        try initCryptographyManager()
        return cryptographyManager!
    }

    private func initCryptographyManager() throws {
        let secret = getSecretForBle(length: 16)
        cryptographyManager = KMMFacade.makeCrypto(sharedSecret: secret)
    }

    func isBleOnline() -> Bool { defaults.bool(forKey: keyBleOnline) }
    func setBleOnline(_ value: Bool) { defaults.set(value, forKey: keyBleOnline) }
}
