import BackgroundTasks
import SwiftUI
import os

@main
struct BillyAppApp: App {
    @StateObject private var container = CompositionRoot.bootstrap()
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            HomeView(onOpenChat: { _ in }, onOpenProfile: { _ in })
                .environmentObject(container.bleViewModel)
                .environmentObject(container.userManager)
                .tint(.black)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    private let log = Logger(subsystem: "com.acme.billyapp", category: "AppDelegate")

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Boot BLE stack (safe: CoreBluetooth doesn’t prompt permissions)
        _ = BluetoothManager.shared
        scheduleBackgroundProcessing()
        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        scheduleBackgroundProcessing()
    }

    private func scheduleBackgroundProcessing() {
        let req = BGProcessingTaskRequest(identifier: "com.acme.billyapp.bluetooth-processing")
        req.requiresNetworkConnectivity = false
        req.requiresExternalPower = false
        req.earliestBeginDate = Date(timeIntervalSinceNow: 60)
        do {
            try BGTaskScheduler.shared.submit(req)
        } catch {
            log.error("Failed to schedule BG task: \(String(describing: error), privacy: .public)")
        }
    }
}
