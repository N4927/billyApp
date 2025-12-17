import BillySDK
import Combine
import SwiftUI
import os.log

// MARK: - Debug Feature (Vertical Slice)

/// @desc The main entry point for the Developer Tools.
/// Organized into tabs for better navigability and separation of concerns.
struct DebugView: View {
    @StateObject private var viewModel: DebugViewModel

    init(viewModel: DebugViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    var body: some View {
        TabView {
            DashboardView(viewModel: viewModel)
                .tabItem {
                    Label("Dashboard", systemImage: "gauge")
                }

            NetworkView(viewModel: viewModel)
                .tabItem {
                    Label("Network", systemImage: "network")
                }

            LogsView(viewModel: viewModel)
                .tabItem {
                    Label("Console", systemImage: "terminal.fill")
                }
        }
        .navigationTitle("Developer Tools")
        .onAppear {
            viewModel.startMonitoring()
        }
    }
}

// MARK: - Sub-Views

struct DashboardView: View {
    @ObservedObject var viewModel: DebugViewModel

    var body: some View {
        List {
            Section(header: Text("Core State")) {
                LabeledContent("Time Slot", value: "\(viewModel.currentSlot)")
                LabeledContent("Broadcast BID") {
                    Text(viewModel.currentBid)
                        .font(.system(.caption, design: .monospaced))
                        .textSelection(.enabled)
                }
                LabeledContent("Active Users", value: "\(viewModel.activeUsers.count)")
            }

            Section(header: Text("Active Users (Real-time)")) {
                if viewModel.activeUsers.isEmpty {
                    Text("No users in range")
                        .foregroundColor(.secondary)
                        .italic()
                } else {
                    ForEach(viewModel.activeUsers, id: \.name) { user in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(user.name)
                                    .font(.headline)
                                Text(
                                    "Last Seen: \(Date(timeIntervalSince1970: Double(user.lastSeen.epochSeconds)).formatted(.relative(presentation: .named)))"
                                )
                                .font(.caption)
                                .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "person.circle.fill")
                                .foregroundColor(.green)
                        }
                    }
                }
            }
        }
    }
}

struct NetworkView: View {
    @ObservedObject var viewModel: DebugViewModel

    var body: some View {
        List {
            Section(header: Text("Sync Status")) {
                LabeledContent("Last Sync", value: viewModel.lastSyncTime)
                LabeledContent("Result") {
                    Text(viewModel.lastSyncResult)
                        .foregroundColor(viewModel.lastSyncResult == "Success" ? .green : .red)
                        .bold()
                }
            }

            Section(header: Text("Discovery")) {
                LabeledContent("Last Discovered BID") {
                    Text(viewModel.lastDiscoveredBid)
                        .font(.system(.caption, design: .monospaced))
                        .textSelection(.enabled)
                }
            }

            Section(header: Text("Maintenance Operations")) {
                Button(action: viewModel.startScanning) {
                    Label("Start Scanning", systemImage: "play.fill")
                }

                Button(action: viewModel.stopScanning) {
                    Label("Stop Scanning", systemImage: "stop.fill")
                }

                Button(action: viewModel.forceSync) {
                    Label("Force Sync Queue", systemImage: "arrow.triangle.2.circlepath")
                }

                Button(action: viewModel.forceBatchRefresh) {
                    Label("Refill Advertising Batch", systemImage: "key.fill")
                }

                Button(role: .destructive, action: viewModel.clearKeychain) {
                    Label("Nuke Keychain (Logout)", systemImage: "trash")
                }
            }
        }
    }
}

struct LogsView: View {
    @ObservedObject var viewModel: DebugViewModel
    @State private var searchText = ""
    @State private var selectedLevel: LogLevel? = nil
    @State private var selectedCategory: String? = nil
    @State private var showShareSheet = false

    var filteredLogs: [LogEntry] {
        viewModel.logs.filter { log in
            let matchesSearch =
                searchText.isEmpty || log.message.localizedCaseInsensitiveContains(searchText)
                || log.category.localizedCaseInsensitiveContains(searchText)

            let matchesLevel = selectedLevel == nil || log.level == selectedLevel
            let matchesCategory = selectedCategory == nil || log.category == selectedCategory

            return matchesSearch && matchesLevel && matchesCategory
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Toolbar
            VStack(spacing: 12) {
                // Top Row: Level Filter & Actions
                HStack {
                    Picker("Level", selection: $selectedLevel) {
                        Text("All").tag(Optional<LogLevel>.none)
                        ForEach(LogLevel.allCases, id: \.self) { level in
                            Text(level.rawValue).tag(Optional(level))
                        }
                    }
                    .pickerStyle(.segmented)
                    .frame(maxWidth: .infinity)

                    HStack(spacing: 16) {
                        Button(action: { viewModel.clearLogs() }) {
                            Image(systemName: "trash")
                                .foregroundColor(.red)
                        }

                        Button(action: { showShareSheet = true }) {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                    .padding(.leading, 8)
                }
                .padding(.horizontal)

                // Bottom Row: Member/Category Filters (The "Pills")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        FilterChip(
                            title: "ALL",
                            count: viewModel.logs.count,
                            isSelected: selectedCategory == nil
                        ) {
                            selectedCategory = nil
                        }

                        ForEach(viewModel.availableCategories, id: \.self) { category in
                            let count = viewModel.logs.filter { $0.category == category }.count
                            FilterChip(
                                title: category.uppercased(),
                                count: count,
                                isSelected: selectedCategory == category
                            ) {
                                selectedCategory = category
                            }
                        }
                    }
                    .padding(.horizontal)
                }
            }
            .padding(.vertical, 12)
            .background(Color(.systemGroupedBackground))
            .overlay(
                Rectangle()
                    .frame(height: 1)
                    .foregroundColor(Color(.separator)),
                alignment: .bottom
            )

            // Log List
            if filteredLogs.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "text.magnifyingglass")
                        .font(.largeTitle)
                        .foregroundColor(.secondary)
                    Text("No logs found")
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(filteredLogs) { log in
                    LogRows(log: log)
                        .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 4, trailing: 16))
                        .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
                .searchable(text: $searchText, prompt: "Search logs...")
            }
        }
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(activityItems: [viewModel.exportLogs()])
        }
    }
}

/// @desc A single row in the log console.
/// Designed to be dense yet readable, with expandable details.
struct LogRows: View {
    let log: LogEntry
    @State private var isExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                // 1. Timestamp & Level Indicator
                VStack(alignment: .trailing, spacing: 4) {
                    Text(
                        log.timestamp.formatted(
                            .dateTime.hour().minute().second())
                    )
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .foregroundColor(.secondary)

                    Text(log.level.rawValue)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(log.level.color))
                }
                .frame(width: 60, alignment: .trailing)

                // 2. Main Content
                VStack(alignment: .leading, spacing: 4) {
                    // Header: Category (Member) & Thread
                    HStack {
                        Text(log.category.uppercased())
                            .font(.system(size: 9, weight: .bold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color(log.level.color).opacity(0.15))
                            .foregroundColor(Color(log.level.color))
                            .cornerRadius(4)

                        Text("•")
                            .foregroundColor(.secondary)
                            .font(.caption2)

                        Text(log.thread)
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundColor(.secondary)

                        Spacer()

                        if log.context != nil || log.error != nil {
                            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }

                    // Message
                    Text(log.message)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(log.level == .error ? .red : .primary)
                        .fixedSize(horizontal: false, vertical: true)
                        .lineLimit(isExpanded ? nil : 3)
                }
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
            .onTapGesture {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isExpanded.toggle()
                }
            }

            // 3. Expanded Details (Context & Error)
            if isExpanded {
                VStack(alignment: .leading, spacing: 8) {
                    Divider()
                        .padding(.leading, 72)

                    // Error Section
                    if let error = log.error {
                        HStack(alignment: .top) {
                            Text("ERR")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.white)
                                .padding(2)
                                .background(Color.red)
                                .cornerRadius(2)

                            VStack(alignment: .leading, spacing: 2) {
                                Text("ERROR DETAILS")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.red)
                                Text(error)
                                    .font(.system(size: 11, design: .monospaced))
                                    .foregroundColor(.primary)
                            }
                        }
                        .padding(.leading, 72)
                        .padding(.vertical, 4)
                    }

                    // Context Section (JSON-like)
                    if let context = log.context, !context.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("CONTEXT")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.secondary)
                                .padding(.leading, 72)

                            VStack(alignment: .leading, spacing: 2) {
                                ForEach(context.sorted(by: { $0.key < $1.key }), id: \.key) {
                                    key, value in
                                    HStack(alignment: .top) {
                                        Text(key)
                                            .font(
                                                .system(
                                                    size: 10, weight: .semibold, design: .monospaced
                                                )
                                            )
                                            .foregroundColor(.blue)
                                        Text("=")
                                            .font(.system(size: 10, design: .monospaced))
                                            .foregroundColor(.secondary)
                                        Text(value)
                                            .font(.system(size: 10, design: .monospaced))
                                            .foregroundColor(.primary)
                                    }
                                }
                            }
                            .padding(8)
                            .background(Color(.secondarySystemBackground))
                            .cornerRadius(6)
                            .padding(.leading, 72)
                        }
                    }

                    // Metadata Footer
                    Text("Subsystem: \(log.subsystem) | ID: \(log.id.uuidString.prefix(8))")
                        .font(.system(size: 8, design: .monospaced))
                        .foregroundColor(.secondary)
                        .padding(.leading, 72)
                        .padding(.bottom, 8)
                }
            }

            Divider()
        }
    }
}

struct FilterChip: View {
    let title: String
    let count: Int
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Text(title)
                    .font(.system(size: 11, weight: .semibold))

                Text("\(count)")
                    .font(.system(size: 9, weight: .bold))
                    .padding(.horizontal, 4)
                    .padding(.vertical, 1)
                    .background(isSelected ? Color.white.opacity(0.3) : Color.black.opacity(0.1))
                    .cornerRadius(4)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(isSelected ? Color.blue : Color(.systemGray5))
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.blue.opacity(0.3), lineWidth: isSelected ? 0 : 1)
            )
        }
        .scaleEffect(isSelected ? 1.05 : 1.0)
        .animation(.spring(response: 0.3), value: isSelected)
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

@MainActor
class DebugViewModel: ObservableObject {
    // Core State
    @Published var currentSlot: Int64 = 0
    @Published var currentBid: String = "Loading..."
    @Published var activeUsers: [ResolvedUser] = []

    // Network State
    @Published var lastSyncTime: String = "Never"
    @Published var lastSyncResult: String = "Idle"
    @Published var lastDiscoveredBid: String = "None"

    // Logs
    @Published var logs: [LogEntry] = []
    @Published var availableCategories: [String] = []

    private let core: BillyCore
    private let uiState: ResolvedRepository
    private let storage: SessionStorage
    private let startProximityUseCase: StartProximityUseCase
    private let stopProximityUseCase: StopProximityUseCase
    private var cancellables = Set<AnyCancellable>()

    init(
        core: BillyCore,
        uiState: ResolvedRepository,
        storage: SessionStorage,
        startProximityUseCase: StartProximityUseCase,
        stopProximityUseCase: StopProximityUseCase
    ) {
        self.core = core
        self.uiState = uiState
        self.storage = storage
        self.startProximityUseCase = startProximityUseCase
        self.stopProximityUseCase = stopProximityUseCase
    }

    func startMonitoring() {
        // 1. Poll for Time-based Core State (Slot, BID)
        Timer.publish(every: 1.0, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in self?.refreshCoreState() }
            .store(in: &cancellables)

        // 2. Subscribe to Logs
        DebugLogger.shared.$logs
            .receive(on: RunLoop.main)
            .sink { [weak self] newLogs in
                self?.logs = newLogs
                self?.updateCategories(from: newLogs)
                self?.analyzeLogs(newLogs)
            }
            .store(in: &cancellables)
    }

    func clearLogs() {
        DebugLogger.shared.clear()
    }

    func exportLogs() -> String {
        return DebugLogger.shared.export()
    }

    private func updateCategories(from logs: [LogEntry]) {
        let categories = Set(logs.map { $0.category })
        self.availableCategories = Array(categories).sorted()
    }

    private func analyzeLogs(_ logs: [LogEntry]) {
        guard let latest = logs.first?.message else { return }

        // Heuristic Analysis of Logs to update UI state
        if latest.contains("Queue Sync Completed Successfully") {
            lastSyncTime = Date().formatted(date: .omitted, time: .standard)
            lastSyncResult = "Success"
        } else if latest.contains("API: Sync Failed") {
            lastSyncTime = Date().formatted(date: .omitted, time: .standard)
            if latest.contains("Serialization") && latest.contains("display_name") {
                lastSyncResult = "Failed (User Not Found)"
            } else {
                lastSyncResult = "Failed"
            }
        } else if latest.contains("GAP Hit") || latest.contains("GATT Hit") {
            // Extract BID: "GAP Hit: <BID> (RSSI: ...)"
            if let range = latest.range(of: "Hit: ") {
                let substring = latest[range.upperBound...]
                let bid = substring.components(separatedBy: " ").first ?? "Unknown"
                lastDiscoveredBid = String(bid)
            }
        }
    }

    private func refreshCoreState() {
        let now = Int64(Date().timeIntervalSince1970)
        self.currentSlot = now / 600  // 10 minutes

        if let bid = core.getCurrentBid() as? String {
            self.currentBid = bid
        } else {
            self.currentBid = "No Batch Available"
        }

        if let users = uiState.activeSet.value as? [ResolvedUser] {
            self.activeUsers = users
        }
    }

    // MARK: - Actions

    func startScanning() {
        startProximityUseCase.execute()
        DebugLogger.shared.log(
            LogEntry(
                level: .info,
                subsystem: "com.billyapp.ios",
                category: "Debug",
                message: "Manual Start Scanning Triggered"
            ))
    }

    func stopScanning() {
        stopProximityUseCase.execute()
        DebugLogger.shared.log(
            LogEntry(
                level: .info,
                subsystem: "com.billyapp.ios",
                category: "Debug",
                message: "Manual Stop Scanning Triggered"
            ))
    }

    func forceSync() {
        Task {
            DebugLogger.shared.log(
                LogEntry(
                    level: .info,
                    subsystem: "com.billyapp.ios",
                    category: "Debug",
                    message: "Manual Sync Triggered"
                ))
            try? await core.syncQueue()
        }
    }

    func forceBatchRefresh() {
        Task {
            DebugLogger.shared.log(
                LogEntry(
                    level: .info,
                    subsystem: "com.billyapp.ios",
                    category: "Debug",
                    message: "Manual Batch Refresh Triggered"
                ))
            try? await core.ensureAdvertisingBatch()
        }
    }

    func clearKeychain() {
        storage.clearTokens()
        DebugLogger.shared.log(
            LogEntry(
                level: .warning,
                subsystem: "com.billyapp.ios",
                category: "Debug",
                message: "Keychain Cleared by User"
            ))
    }
}

extension Color {
    init(_ hex: String) {
        switch hex {
        case "gray": self = .gray
        case "blue": self = .blue
        case "yellow": self = .yellow
        case "red": self = .red
        default: self = .primary
        }
    }
}
