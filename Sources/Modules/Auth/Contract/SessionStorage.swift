import Foundation

// PORT (Interface)
// Defines the contract for session storage.
// This belongs to the Domain Layer (Core).

/**
 * @desc The Domain Interface for Session Storage.
 * Defines how authentication tokens are persisted and retrieved.
 */
protocol SessionStorage {
    
    /**
     * @desc Retrieves the current Access Token.
     * @returns The access token string, or nil if not found.
     */
    func getAccessToken() -> String?
    
    /**
     * @desc Retrieves the current Refresh Token.
     * @returns The refresh token string, or nil if not found.
     */
    func getRefreshToken() -> String?
    
    /**
     * @desc Persists the authentication tokens securely.
     * @param access - The new access token.
     * @param refresh - The new refresh token.
     */
    func saveTokens(access: String, refresh: String)
    
    /**
     * @desc Clears all stored tokens.
     * Used during logout or session expiration.
     */
    func clearTokens()
}
