import SwiftUI

/**
 * @desc The primary View for the Authentication Module.
 * Handles both Login and Registration flows based on the ViewModel state.
 */
struct AuthView: View {
    
    /// The ViewModel managing the state and logic for this View.
    @StateObject var viewModel: AuthViewModel
    
    /**
     * @desc Initializes the AuthView with a ViewModel.
     * @param viewModel - The AuthViewModel instance to use.
     */
    init(viewModel: AuthViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }
    
    var body: some View {
        VStack(spacing: 20) {
            Text(viewModel.isRegistering ? "Create Account" : "Welcome Back")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            if let error = viewModel.errorMessage {
                Text(error)
                    .foregroundColor(.red)
                    .font(.caption)
            }
            
            VStack(spacing: 12) {
                if viewModel.isRegistering {
                    TextField("Username", text: $viewModel.username)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .autocapitalization(.none)
                }
                
                TextField("Email", text: $viewModel.email)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                
                SecureField("Password", text: $viewModel.password)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
            }
            .padding(.horizontal)
            
            Button(action: { viewModel.performAction() }) {
                if viewModel.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    Text(viewModel.isRegistering ? "Sign Up" : "Log In")
                        .bold()
                }
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.blue)
            .foregroundColor(.white)
            .cornerRadius(10)
            .padding(.horizontal)
            .disabled(viewModel.isLoading)
            
            Button(action: { viewModel.toggleMode() }) {
                Text(viewModel.isRegistering ? "Already have an account? Log In" : "Don't have an account? Sign Up")
                    .font(.footnote)
                    .foregroundColor(.gray)
            }
            
            Spacer()
        }
        .padding(.top, 50)
    }
}