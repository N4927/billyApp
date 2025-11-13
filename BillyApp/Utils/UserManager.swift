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

    // MARK: - User identity

    func saveUser(id: String, name: String, age: Int? = nil, bio: String? = nil) {
        let sanitized = sanitizeName(name)
        defaults.set(id, forKey: keyId)
        defaults.set(sanitized, forKey: keyName)
        if let age {
            defaults.set(age, forKey: keyAge)
        } else {
            defaults.removeObject(forKey: keyAge)
        }
        defaults.set(bio, forKey: keyBio)
        try? initCryptographyManager()
    }

    func updateName(_ name: String) {
        let sanitized = sanitizeName(name)
        defaults.set(sanitized, forKey: keyName)
        try? initCryptographyManager()
    }

    func clearUser() {
        [keyId, keyName, keyAge, keyBio].forEach { defaults.removeObject(forKey: $0) }
        cryptographyManager = nil
    }

    func getUserName() -> String {
        defaults.string(forKey: keyName) ?? ""
    }

    func hasValidName() -> Bool {
        let n = getUserName().trimmingCharacters(in: .whitespacesAndNewlines)
        return !n.isEmpty && n.lowercased() != "anonimo"
    }

    func getPersonalIdentifier() -> String {
        defaults.string(forKey: keyId) ?? "anonymous"
    }

    /// Restituisce l'ID esistente, oppure ne genera e persiste uno nuovo (16 hex chars).
    @discardableResult
    func ensureUserIdIfNeeded() -> String {
        if let existing = defaults.string(forKey: keyId), !existing.isEmpty {
            return existing
        }
        let newId = generateHexId(byteCount: 8)  // 8 bytes -> 16 hex
        defaults.set(newId, forKey: keyId)
        return newId
    }

    // MARK: - BLE secret & Crypto

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

    // MARK: - Presence

    func isBleOnline() -> Bool { defaults.bool(forKey: keyBleOnline) }
    func setBleOnline(_ value: Bool) { defaults.set(value, forKey: keyBleOnline) }

    // MARK: - Helpers

    private func sanitizeName(_ s: String) -> String {
        let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
        let allowed = CharacterSet.alphanumerics.union(.whitespaces)
        let filtered = String(trimmed.unicodeScalars.filter { allowed.contains($0) })
        return String(filtered.prefix(40))
    }

    private func generateHexId(byteCount: Int) -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        _ = SecRandomCopyBytes(kSecRandomDefault, byteCount, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }
}
