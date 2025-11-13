import Foundation

final class CompositionRoot: ObservableObject {
    let bleViewModel: BleViewModel

    init(bleViewModel: BleViewModel) {
        self.bleViewModel = bleViewModel
    }
    
    @MainActor
    static func bootstrap() -> CompositionRoot {
        // DI minimale
        let bleVM = BleViewModel()
        return CompositionRoot(bleViewModel: bleVM)
    }
}
