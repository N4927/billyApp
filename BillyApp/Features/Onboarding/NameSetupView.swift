import SwiftUI

struct NameSetupView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @FocusState private var isFocused: Bool

    let onCancel: () -> Void
    let onSaved: (String) -> Void

    init(initialName: String, onCancel: @escaping () -> Void, onSaved: @escaping (String) -> Void) {
        self._name = State(initialValue: initialName)
        self.onCancel = onCancel
        self.onSaved = onSaved
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                VStack(spacing: 8) {
                    Text("Set your display name")
                        .font(.title2.weight(.semibold))
                        .multilineTextAlignment(.center)
                    Text(
                        "Your name is used to generate a secure rotating identifier for nearby discovery."
                    )
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                }
                .padding(.top, 12)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Name")
                        .font(.subheadline.weight(.semibold))
                    TextField("e.g. Alice", text: $name)
                        .textInputAutocapitalization(.words)
                        .disableAutocorrection(true)
                        .textContentType(.name)
                        .submitLabel(.done)
                        .focused($isFocused)
                        .padding(12)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color(UIColor.secondarySystemBackground))
                        )
                        .accessibilityLabel(Text("Your name"))
                }

                Spacer()

                Button(action: {
                    onSaved(name.trimmed())
                    dismiss()
                }) {
                    Text("Save")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(isValid ? Color.black : Color.gray)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .disabled(!isValid)
                .accessibilityLabel(Text("Save name"))
            }
            .padding(20)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
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
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") {
                        onCancel()
                        dismiss()
                    }
                }
            }
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) { isFocused = true }
            }
        }
        .interactiveDismissDisabled(!isValid)  // blocca chiusura accidentale se nome vuoto
    }

    private var isValid: Bool {
        let t = name.trimmed()
        return !t.isEmpty && t.lowercased() != "anonimo"
    }
}

extension String {
    fileprivate func trimmed() -> String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
