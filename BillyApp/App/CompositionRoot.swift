import Foundation

@MainActor
final class CompositionRoot: ObservableObject {
    let bleViewModel: BleViewModel
    let userManager: UserManager

    init(bleViewModel: BleViewModel, userManager: UserManager) {
        self.bleViewModel = bleViewModel
        self.userManager = userManager
    }

    @MainActor
    static func bootstrap() -> CompositionRoot {
        let userManager = UserManager.shared
        let bleVM = BleViewModel(center: .default)
        return CompositionRoot(bleViewModel: bleVM, userManager: userManager)
    }
}
