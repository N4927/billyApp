import Foundation
import Security
import BillySDK

/**
 * @desc Infrastructure Adapter: Keychain Storage.
 * Implements the KMM `TokenStorage` interface using native iOS `Security` framework.
 * Also implements the Domain `SessionStorage` interface for the App.
 *
 * @note This class bridges the gap between the KMM Core (which needs storage) and the iOS Keychain.
 */
class KeychainStorage: TokenStorage, SessionStorage {
    
    // Keys used to identify items in the Keychain
    private let accessKey = "com.billyapp.auth.access"
    private let refreshKey = "com.billyapp.auth.refresh"
    
    // --- KMM TokenStorage Interface ---
    
    /**
     * @desc Retrieves the Access Token from the Keychain.
     * @returns The token string if found, nil otherwise.
     */
    func getAccessToken() -> String? {
        return read(key: accessKey)
    }
    
    /**
     * @desc Retrieves the Refresh Token from the Keychain.
     * @returns The token string if found, nil otherwise.
     */
    func getRefreshToken() -> String? {
        return read(key: refreshKey)
    }
    
    /**
     * @desc Saves the Access and Refresh tokens to the Keychain.
     * Overwrites any existing tokens with the same keys.
     * @param access - The access token to save.
     * @param refresh - The refresh token to save.
     */
    func saveTokens(access: String, refresh: String) {
        save(key: accessKey, value: access)
        save(key: refreshKey, value: refresh)
    }
    
    /**
     * @desc Removes both Access and Refresh tokens from the Keychain.
     */
    func clearTokens() {
        delete(key: accessKey)
        delete(key: refreshKey)
    }
    
    // --- Private Helpers (Security framework) ---
    
    /**
     * @desc Helper to save a string value to the Keychain.
     * Deletes any existing item with the same key before adding the new one to ensure atomicity.
     * @param key - The account key.
     * @param value - The string value to store.
     */
    private func save(key: String, value: String) {
        let data = Data(value.utf8)
        let query = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecValueData: data
        ] as [String: Any]
        
        // Delete first to ensure update (KISS approach)
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }
    
    /**
     * @desc Helper to read a string value from the Keychain.
     * @param key - The account key to search for.
     * @returns The string value if found, nil otherwise.
     */
    private func read(key: String) -> String? {
        let query = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccount: key,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ] as [String: Any]
        
        var dataTypeRef: AnyObject?
        let status: OSStatus = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
        
        if status == noErr, let data = dataTypeRef as? Data {
            return String(data: data, encoding: .utf8)
        }
        return nil
    }
    
    /**
     * @desc Helper to delete an item from the Keychain.
     * @param key - The account key to delete.
     */
    private func delete(key: String) {
        let query = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccount: key
        ] as [String: Any]
        
        SecItemDelete(query as CFDictionary)
    }
}
