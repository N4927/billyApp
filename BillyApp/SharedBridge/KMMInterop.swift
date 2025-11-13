import Foundation
import Shared

// MARK: - Swift <-> Kotlin ByteArray

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let arr = KotlinByteArray(size: Int32(count))
        withUnsafeBytes { (ptr: UnsafeRawBufferPointer) in
            for (idx, byte) in ptr.enumerated() {
                arr.set(index: Int32(idx), value: Int8(bitPattern: byte))
            }
        }
        return arr
    }
}

extension Array where Element == UInt8 {
    func toKotlinByteArray() -> KotlinByteArray {
        let arr = KotlinByteArray(size: Int32(count))
        for (i, b) in self.enumerated() {
            arr.set(index: Int32(i), value: Int8(bitPattern: b))
        }
        return arr
    }
}

extension KotlinByteArray {
    func toData() -> Data {
        var buffer = [UInt8](repeating: 0, count: Int(self.size))
        for i in 0..<Int(self.size) {
            buffer[i] = UInt8(bitPattern: self.get(index: Int32(i)))
        }
        return Data(buffer)
    }
}

// MARK: - Helpers per endian

extension FixedWidthInteger {
    var bigEndianBytes: [UInt8] {
        var be = self.bigEndian
        return withUnsafeBytes(of: &be) { Array($0) }
    }
    static func fromBigEndianBytes(_ bytes: [UInt8]) -> Self {
        precondition(bytes.count == MemoryLayout<Self>.size)
        return bytes.withUnsafeBytes { $0.load(as: Self.self) }.bigEndian
    }
}
