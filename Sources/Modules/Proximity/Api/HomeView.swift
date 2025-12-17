import SwiftUI

/// @desc The Main Dashboard View ("Billy Radar").
/// Displays the list of nearby users detected by the Proximity Engine and provides controls to stop/start scanning.
struct HomeView: View {

    /// Observed Session Manager to handle Logout actions.
    @ObservedObject var sessionManager: SessionManager

    /// The ViewModel driving the UI state.
    @StateObject var viewModel: HomeViewModel

    /// Factory for the Debug View (Dependency Injection).
    private let makeDebugView: () -> AnyView

    /**
     * @desc Initializes the HomeView.
     * @param sessionManager - The global session manager.
     * @param viewModel - The HomeViewModel instance.
     * @param makeDebugView - Closure to create the Debug View.
     */
    init(
        sessionManager: SessionManager, viewModel: HomeViewModel,
        makeDebugView: @escaping () -> AnyView
    ) {
        self.sessionManager = sessionManager
        _viewModel = StateObject(wrappedValue: viewModel)
        self.makeDebugView = makeDebugView
    }

    var body: some View {
        VStack(spacing: 20) {
            // Header
            HStack {
                Image(systemName: "dot.radiowaves.left.and.right")
                    .font(.largeTitle)
                    .foregroundColor(.blue)
                Text("Billy Radar")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                Spacer()
            }
            .padding()

            // State-Driven UI
            switch viewModel.state {
            case .idle:
                idleView
            case .scanning(let users):
                scanningView(users: users)
            case .error(let message):
                errorView(message: message)
            }

            // Controls
            VStack(spacing: 10) {
                if case .scanning = viewModel.state {
                    HStack {
                        ProgressView()
                            .scaleEffect(0.8)
                        Text("Engine Running")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                HStack {
                    Button(action: {
                        sessionManager.logout()
                    }) {
                        Text("Logout")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.red)
                            .cornerRadius(10)
                    }

                    // Debug Entry Point (Hidden/Small)
                    NavigationLink(destination: makeDebugView()) {
                        Image(systemName: "wrench.and.screwdriver")
                            .foregroundColor(.gray)
                            .padding()
                    }
                }
            }
            .padding()
        }
        .onAppear {
            // Ensure engine is running when view appears
            viewModel.startScanning()
        }
    }

    // MARK: - Subviews

    private var idleView: some View {
        VStack {
            Spacer()
            Text("Engine Paused")
                .font(.headline)
                .foregroundColor(.gray)
            Button("Start Scanning") {
                viewModel.startScanning()
            }
            .padding()
            Spacer()
        }
    }

    private func scanningView(users: [UserUIModel]) -> some View {
        Group {
            if users.isEmpty {
                VStack {
                    Spacer()
                    Image(systemName: "person.3.fill")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 80, height: 80)
                        .foregroundColor(.gray.opacity(0.3))
                    Text("No users nearby")
                        .font(.headline)
                        .foregroundColor(.gray)
                    Text("Scanning for contacts...")
                        .font(.caption)
                        .foregroundColor(.gray)
                    Spacer()
                }
            } else {
                List(users, id: \.name) { user in
                    HStack {
                        VStack(alignment: .leading) {
                            Text(user.name)
                                .font(.headline)
                            Text("Last seen: \(user.lastSeenText)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                    }
                }
                .listStyle(.plain)
            }
        }
    }

    private func errorView(message: String) -> some View {
        VStack {
            Spacer()
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.red)
                .font(.largeTitle)
            Text("Error")
                .font(.headline)
            Text(message)
                .font(.caption)
                .multilineTextAlignment(.center)
            Button("Retry") {
                viewModel.startScanning()
            }
            .padding()
            Spacer()
        }
    }
}
