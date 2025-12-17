import Foundation

// SHARED KERNEL - EXTENSIONS
// Utility extensions for primitive types.

extension Data {
    /// @desc Initializes Data from a Hex String.
    /// @param hexString The hex string to parse.
    public init?(hexString: String) {
        let len = hexString.count / 2
        var data = Data(capacity: len)
        var ptr = hexString.startIndex
        for _ in 0..<len {
            let end = hexString.index(ptr, offsetBy: 2)
            let bytes = hexString[ptr..<end]
            if let num = UInt8(bytes, radix: 16) {
                data.append(num)
            } else {
                return nil
            }
            ptr = end
        }
        self = data
    }

    /// @desc Converts Data to a Hex String.
    public var hexString: String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
