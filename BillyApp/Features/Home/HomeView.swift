import SwiftUI

private enum PresenceMode: String, CaseIterable, Identifiable {
    case online, offline
    var id: String { rawValue }
    var title: String { NSLocalizedString(rawValue, comment: "") }
}

struct HomeView: View {
    @EnvironmentObject var bleVM: BleViewModel
    @EnvironmentObject var userManager: UserManager

    @State private var mode: PresenceMode = .offline
    @State private var showNameSheet = false

    let onOpenChat: (String) -> Void
    let onOpenProfile: (String) -> Void

    var body: some View {
        NavigationStack {
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(20)
                .navigationTitle(NSLocalizedString("Home", comment: ""))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            showNameSheet = true
                        } label: {
                            Label(
                                userManager.getUserName().isEmpty
                                    ? "Set name" : userManager.getUserName(),
                                systemImage: "person.crop.circle"
                            )
                            .labelStyle(.titleAndIcon)
                        }
                        .accessibilityLabel(Text("Edit your name"))
                    }
                }
        }
        .onAppear {
            mode = userManager.isBleOnline() ? .online : .offline
            // Se il nome non è valido, apri subito lo sheet di setup
            if !userManager.hasValidName() {
                showNameSheet = true
            }
        }
        .onChange(of: mode) { newValue in
            switch newValue {
            case .online:
                // Gate: richiedi nome valido prima di avviare BLE
                guard userManager.hasValidName() else {
                    mode = .offline
                    showNameSheet = true
                    return
                }
                userManager.setBleOnline(true)
                _ = BluetoothManager.shared  // boot effettivo solo quando serve
            case .offline:
                userManager.setBleOnline(false)
            }
        }
        .sheet(isPresented: $showNameSheet) {
            NameSetupView(
                initialName: userManager.getUserName(),
                onCancel: {
                    // Se non c'è nome e l'utente cancella, rimani offline
                    if !userManager.hasValidName() { mode = .offline }
                },
                onSaved: { newName in
                    let id = userManager.ensureUserIdIfNeeded()
                    userManager.saveUser(id: id, name: newName)
                    // Non forziamo automaticamente online; lasciamo all’utente la scelta.
                }
            )
        }
    }

    @ViewBuilder
    private var content: some View {
        VStack(alignment: .leading, spacing: 16) {
            header

            Picker(selection: $mode) {
                ForEach(PresenceMode.allCases) { m in
                    Text(m.title).tag(m)
                }
            } label: {
                Text("presence")
            }
            .pickerStyle(.segmented)
            .accessibilityLabel(Text("presence"))
            .padding(.bottom, 4)

            if bleVM.encounters.isEmpty {
                EmptyStateView(
                    titleKey: "No one nearby yet",
                    subtitleKey: mode == .online
                        ? "Stay online to be discoverable"
                        : "Switch to online to be discoverable"
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(bleVM.encounters) { enc in
                            PersonCard(
                                encounter: enc,
                                onOpenProfile: onOpenProfile,
                                onOpenChat: onOpenChat
                            )
                            .accessibilityElement(children: .combine)
                            .accessibilityLabel(Text("\(enc.name), near you"))
                        }
                    }
                    .padding(.top, 8)
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(NSLocalizedString("People nearby", comment: ""))
                .font(.title.bold())
                .accessibilityAddTraits(.isHeader)

            HStack(spacing: 8) {
                StatusDot(isOn: mode == .online)
                Text(
                    mode == .online
                        ? NSLocalizedString("online", comment: "")
                        : NSLocalizedString("offline", comment: "")
                )
                .font(.callout.weight(.semibold))
                .foregroundStyle(mode == .online ? .green : .secondary)
                Spacer()
                Text(
                    userManager.getUserName().isEmpty ? "Set your name" : userManager.getUserName()
                )
                .font(.callout)
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .truncationMode(.middle)
            }
            .padding(10)
            .background(
                .ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}

private struct StatusDot: View {
    let isOn: Bool
    var body: some View {
        Circle()
            .fill(isOn ? Color.green : Color.gray)
            .frame(width: 8, height: 8)
            .accessibilityHidden(true)
    }
}

private struct EmptyStateView: View {
    let titleKey: String
    let subtitleKey: String
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "person.2")
                .font(.system(size: 42, weight: .semibold))
                .foregroundStyle(.secondary)

            Text(NSLocalizedString(titleKey, comment: ""))
                .font(.title3.weight(.semibold))

            Text(NSLocalizedString(subtitleKey, comment: ""))
                .font(.callout)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 10)
        }
        .padding(.vertical, 24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(UIColor.systemBackground))
                .shadow(color: .black.opacity(0.06), radius: 16, x: 0, y: 6)
        )
        .padding(.top, 32)
    }
}

struct PersonCard: View {
    let encounter: ResolvedEncounter
    let onOpenProfile: (String) -> Void
    let onOpenChat: (String) -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: { onOpenProfile(encounter.name) }) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color(UIColor.secondarySystemBackground))
                            .frame(width: 48, height: 48)
                        Text(String(encounter.name.prefix(1)).uppercased())
                            .font(.system(.title3, design: .rounded).weight(.bold))
                            .foregroundStyle(.primary)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(encounter.name)
                            .font(.headline)
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Text(NSLocalizedString("near you", comment: ""))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Spacer(minLength: 8)

            Button(action: { onOpenChat(encounter.name) }) {
                Text(NSLocalizedString("chat", comment: ""))
                    .font(.callout.weight(.semibold))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(.black, in: Capsule())
                    .foregroundStyle(.white)
                    .accessibilityLabel(Text("Chat with \(encounter.name)"))
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(UIColor.systemBackground))
                .shadow(color: .black.opacity(0.05), radius: 10, x: 0, y: 4)
        )
    }
}
