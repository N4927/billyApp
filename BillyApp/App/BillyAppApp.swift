import BackgroundTasks
import SwiftUI

@main
struct BillyAppApp: App {
    @StateObject private var container = CompositionRoot.bootstrap()
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            HomeView(onOpenChat: { _ in }, onOpenProfile: { _ in })
                .environmentObject(container.bleViewModel)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        _ = BluetoothManager.shared
        scheduleBG()
        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        scheduleBG()
    }

    private func scheduleBG() {
        let req = BGProcessingTaskRequest(identifier: "com.acme.billyapp.bluetooth-processing")
        req.requiresNetworkConnectivity = false
        req.requiresExternalPower = false
        req.earliestBeginDate = Date(timeIntervalSinceNow: 60)
        try? BGTaskScheduler.shared.submit(req)
    }
}
