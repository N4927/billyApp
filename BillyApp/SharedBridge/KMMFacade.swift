import Foundation
import Shared
import os.log

enum KMMError: Swift.Error {
    case invalidInput
    case unknown(String)
}

struct KMMFacade {
    private static let log = Logger(subsystem: "com.acme.billyapp", category: "KMMFacade")

    /// Resolve a 16-byte payload (8B timestamp BE + 8B cipher) via KMM FakeServer.
    static func resolveUser(from payload16: Data) -> User? {
        guard payload16.count == 16 else { return nil }
        let tsBytes = Array(payload16.prefix(8))
        let ts: Int64 = Int64.fromBigEndianBytes(tsBytes)
        let kotlinBytes = payload16.toKotlinByteArray()
        return FakeServer.shared.resolveRotatingId(
            encryptedPayload: kotlinBytes,
            receivedTimestamp: ts
        )
    }

    /// Build the CryptographyManager from a user secret (16B).
    static func makeCrypto(sharedSecret: [UInt8]) -> CryptographyManager {
        let arr = sharedSecret.toKotlinByteArray()
        return CryptographyManager(sharedSecret: arr)
    }
}
