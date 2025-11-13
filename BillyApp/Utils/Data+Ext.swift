import Foundation

extension Data {
    static func + (lhs: Data, rhs: Data) -> Data {
        var out = Data(capacity: lhs.count + rhs.count)
        out.append(lhs)
        out.append(rhs)
        return out
    }
}
