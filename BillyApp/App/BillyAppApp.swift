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
                .background(
                    LinearGradient(
                        colors: [
                            Color(UIColor.systemBackground),
                            Color(UIColor.secondarySystemBackground),
                        ],
                        startPoint: .top, endPoint: .bottom
                    )
                    .ignoresSafeArea()
                )
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    private let log = Logger(subsystem: "com.acme.billyapp", category: "AppDelegate")
    private let bluetoothTaskId = "com.acme.billyapp.bluetooth-processing"

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        registerBackgroundTasks()  // 1) REGISTRA SEMPRE all’avvio
        scheduleBackgroundProcessing()  // 2) poi programma il task
        return true
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        scheduleBackgroundProcessing()
    }

    // MARK: - BGTasks

    private func registerBackgroundTasks() {
        let ok = BGTaskScheduler.shared.register(
            forTaskWithIdentifier: bluetoothTaskId,
            using: nil
        ) { [weak self] task in
            guard let self else { return }
            self.handleBluetoothProcessing(task: task)
        }

        if ok {
            log.info("BGTask registered: \(self.bluetoothTaskId, privacy: .public)")
        } else {
            log.error(
                "BGTask already registered or failed: \(self.bluetoothTaskId, privacy: .public)")
        }
    }

    private func handleBluetoothProcessing(task: BGTask) {
        guard let processingTask = task as? BGProcessingTask else {
            task.setTaskCompleted(success: false)
            return
        }

        // Re-schedule ASAP to keep periodic processing
        scheduleBackgroundProcessing()

        // Esegui il lavoro minimo in modo sicuro (gated dal nome e dallo stato Online)
        processingTask.expirationHandler = { [weak self] in
            self?.log.error("BGTask expiration reached")
        }

        Task { @MainActor in
            // Avvia il BLE solo se l’utente ha già impostato un nome ed è Online
            if UserManager.shared.hasValidName(), UserManager.shared.isBleOnline() {
                _ = BluetoothManager.shared  // bootstrap BLE stack
            }
            processingTask.setTaskCompleted(success: true)
        }
    }

    private func scheduleBackgroundProcessing() {
        let req = BGProcessingTaskRequest(identifier: bluetoothTaskId)
        req.requiresNetworkConnectivity = false
        req.requiresExternalPower = false
        req.earliestBeginDate = Date(timeIntervalSinceNow: 60)
        do {
            try BGTaskScheduler.shared.submit(req)
            log.info("BGTask submitted: \(self.bluetoothTaskId, privacy: .public)")
        } catch {
            log.error("Failed to schedule BG task: \(String(describing: error), privacy: .public)")
        }
    }
}
