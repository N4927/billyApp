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

    let onOpenChat: (String) -> Void
    let onOpenProfile: (String) -> Void

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [
                        Color(UIColor.systemBackground), Color(UIColor.secondarySystemBackground),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                .ignoresSafeArea()

                content
                    .padding(20)
                    .navigationTitle(NSLocalizedString("Home", comment: ""))
                    .navigationBarTitleDisplayMode(.inline)
            }
        }
        .onAppear {
            mode = userManager.isBleOnline() ? .online : .offline
            if userManager.getUserName() == "Anonimo" {
                // Seed a predictable demo identity for BLE crypto
                userManager.saveUser(id: "ecb73c72d94f1a23", name: "Alice")
            }
        }
        .onChange(of: mode) { newValue in
            switch newValue {
            case .online:
                userManager.setBleOnline(true)
                _ = BluetoothManager.shared  // ensure boot
            case .offline:
                userManager.setBleOnline(false)
            }
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
                Text(userManager.getUserName())
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
