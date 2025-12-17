import SwiftUI

/// @desc The Application Entry Point.
/// @responsibility Bootstraps the DI Container and binds the Root View to Session State.
@main
struct BillyApp: App {

    // The Composition Root is owned by the App struct.
    // It is created ONCE and persists for the lifecycle of the app.
    @StateObject private var root = CompositionRoot()

    var body: some Scene {
        WindowGroup {
            RootFlow(
                sessionManager: root.sessionManager,
                root: root
            )
        }
    }
}

/// @desc Separated View to handle High-Level Navigation Flow.
/// @reason Decouples the App struct from the specific View implementations.
struct RootFlow: View {
    @ObservedObject var sessionManager: SessionManager
    let root: CompositionRoot

    var body: some View {
        Group {
            switch sessionManager.state {
            case .startup:
                ProgressView("Bootstrapping...")
                    .onAppear {
                        sessionManager.bootstrap()
                    }

            case .authenticated:
                // PROXIMITY SLICE
                NavigationView {
                    HomeView(
                        sessionManager: sessionManager,
                        viewModel: root.makeHomeViewModel(),
                        makeDebugView: { AnyView(root.makeDebugView()) }
                    )
                }
                .transition(.opacity)

            case .unauthenticated:
                // AUTH SLICE
                AuthView(
                    viewModel: root.makeAuthViewModel()
                )
                .transition(.opacity)
            }
        }
        .animation(.default, value: sessionManager.state)
    }
}
