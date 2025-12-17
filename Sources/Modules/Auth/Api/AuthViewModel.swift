import Foundation

// MARK: - View State

/// @enum AuthViewState
/// @desc Represents the finite states of the Authentication UI.
///       Enforces a strict state machine to prevent invalid UI transitions.
enum AuthViewState: Equatable {
    case idle
    case loading
    case error(String)
    case success
}

// MARK: - ViewModel

/// @class AuthViewModel
/// @desc The Presentation Logic for the Authentication Vertical Slice.
///       Responsible for parsing raw user input into Domain Objects, handling side effects,
///       and managing the UI State Machine.
///
/// @architecture MVVM + Coordinator Pattern (via SessionManager)
/// @pattern Result Oriented Programming
@MainActor
class AuthViewModel: ObservableObject {

    // --- Input (Raw Data) ---
    // Kept as primitives strictly for UI Binding (TextFields).
    // These are NOT trusted until parsed.

    @Published var email = ""
    @Published var password = ""
    @Published var username = ""
    @Published var isRegistering = false

    // --- Output (State) ---

    @Published private(set) var state: AuthViewState = .idle

    // Computed properties for View consumption
    var isLoading: Bool { state == .loading }
    var errorMessage: String? {
        if case .error(let msg) = state { return msg }
        return nil
    }

    // --- Dependencies ---

    private let loginUseCase: LoginUseCase
    private let registerUseCase: RegisterUseCase
    private let sessionManager: SessionManager

    /**
     * @desc Initializes the AuthViewModel with injected dependencies.
     *       Follows Dependency Injection principle for testability.
     */
    init(
        sessionManager: SessionManager,
        loginUseCase: LoginUseCase,
        registerUseCase: RegisterUseCase
    ) {
        self.sessionManager = sessionManager
        self.loginUseCase = loginUseCase
        self.registerUseCase = registerUseCase
    }

    // MARK: - Actions (Commands)

    /**
     * @desc Executes the Authentication Command.
     *       1. Parses raw inputs into Validated Value Objects (Fail Fast).
     *       2. Executes the Domain Use Case via Repository.
     *       3. Updates State Machine based on Result.
     */
    func performAction() {
        // 1. Parse & Validate (Pure Logic)
        let validationResult = parseInputs()

        switch validationResult {
        case .failure(let error):
            self.state = .error(error.localizedDescription)
            return

        case .success(let command):
            // 2. Execute Side Effect (Async)
            execute(command: command)
        }
    }

    /**
     * @desc Toggles the authentication mode.
     *       Resets state to Idle to clear errors.
     */
    func toggleMode() {
        isRegistering.toggle()
        state = .idle
    }

    // MARK: - Internals

    private enum AuthCommand {
        case login(LoginInput)
        case register(RegisterInput)
    }

    /**
     * @desc Parses raw strings into strictly typed Domain Objects.
     *       Implements "Parse, Don't Validate".
     */
    private func parseInputs() -> Result<AuthCommand, ValidationError> {
        // Parse Email
        let emailResult = EmailAddress.create(email)
        // Parse Password
        let passwordResult = Password.create(password)

        // Combine Results (Applicative Style simulation)
        switch (emailResult, passwordResult) {
        case (.success(let validEmail), .success(let validPassword)):

            if isRegistering {
                // Parse Username only if registering
                let usernameResult = Username.create(username)
                switch usernameResult {
                case .success(let validUsername):
                    let input = RegisterInput(
                        username: validUsername, email: validEmail, password: validPassword)
                    return .success(.register(input))
                case .failure(let error):
                    return .failure(error)
                }
            } else {
                let input = LoginInput(email: validEmail, password: validPassword)
                return .success(.login(input))
            }

        case (.failure(let error), _): return .failure(error)
        case (_, .failure(let error)): return .failure(error)
        }
    }

    /**
     * @desc Executes the async command against the Repository.
     *       Handles the Success/Failure railway.
     */
    private func execute(command: AuthCommand) {
        self.state = .loading

        Task {
            do {
                switch command {
                case .login(let input):
                    try await loginUseCase.execute(input: input)
                case .register(let input):
                    try await registerUseCase.execute(input: input)
                }

                // Success Path
                self.state = .success
                sessionManager.authenticate()

            } catch {
                // Failure Path
                // In a real app, we would map Domain Errors to User-Friendly messages here.
                self.state = .error("Authentication failed. Please check your credentials.")
            }
        }
    }
}
