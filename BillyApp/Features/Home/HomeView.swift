import SwiftUI

struct HomeView: View {
    @EnvironmentObject var bleVM: BleViewModel
    @StateObject private var userManager = UserManager()
    @State private var isOnline = false

    let onOpenChat: (String) -> Void
    let onOpenProfile: (String) -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text("People nearby").font(.title2.bold()).padding(.vertical, 8)

                HStack(spacing: 8) {
                    toggle("online", isSelected: isOnline) {
                        guard !isOnline else { return }
                        isOnline = true
                        userManager.setBleOnline(true)
                        _ = BluetoothManager.shared
                        BluetoothManager.shared.sink = nil  // già collegato via NotificationCenter
                    }
                    toggle("offline", isSelected: !isOnline) {
                        guard isOnline else { return }
                        isOnline = false
                        userManager.setBleOnline(false)
                    }
                }

                if bleVM.encounters.isEmpty {
                    Spacer()
                    Text("No one nearby yet").foregroundColor(.gray).frame(
                        maxWidth: .infinity, alignment: .center)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(bleVM.encounters) { enc in
                                PersonCard(
                                    encounter: enc,
                                    onOpenProfile: onOpenProfile,
                                    onOpenChat: onOpenChat)
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .navigationTitle("Home")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onAppear { isOnline = userManager.isBleOnline() }
    }

    private func toggle(_ text: String, isSelected: Bool, action: @escaping () -> Void) -> some View
    {
        Button(action: action) {
            Text(NSLocalizedString(text, comment: ""))
                .fontWeight(.medium)
                .foregroundColor(isSelected ? .white : .black)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
        }
        .background(isSelected ? Color.black : Color(UIColor.systemGray5))
        .cornerRadius(20)
        .accessibilityLabel(Text(text))
    }
}

struct PersonCard: View {
    let encounter: ResolvedEncounter
    let onOpenProfile: (String) -> Void
    let onOpenChat: (String) -> Void

    var body: some View {
        HStack {
            Button(action: { onOpenProfile(encounter.name) }) {
                HStack {
                    ZStack {
                        Circle().fill(Color(UIColor.systemGray5)).frame(width: 48, height: 48)
                        Text(encounter.name.prefix(1).uppercased())
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.black)
                    }
                    Spacer().frame(width: 12)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(encounter.name).font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.black)
                        Text("near you").font(.system(size: 13)).foregroundColor(.gray)
                    }
                }
            }.buttonStyle(.plain)

            Spacer()

            Button(action: { onOpenChat(encounter.name) }) {
                Text("chat")
                    .font(.system(size: 14))
                    .foregroundColor(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 6)
                    .background(Color.black)
                    .cornerRadius(8)
            }
        }
        .padding(.vertical, 8)
    }
}
