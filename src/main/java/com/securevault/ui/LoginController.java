package com.securevault.ui;

import com.securevault.auth.AuthenticationService;
import com.securevault.auth.PasswordHasher;
import com.securevault.auth.SessionManager;
import com.securevault.auth.User;
import com.securevault.exceptions.AuthenticationException;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Arrays;

/**
 * Controller for the Login screen.
 * Handles authentication and PBKDF2 key derivation in a background task
 * to prevent freezing the JavaFX Application Thread (a key requirement for rich GUIs).
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    private final AuthenticationService authService;
    private final PasswordHasher hasher;

    public LoginController() {
        this.authService = new AuthenticationService();
        this.hasher = new PasswordHasher();
    }

    @FXML
    public void initialize() {
        errorLabel.setText("");
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter both username and password.");
            return;
        }

        loginButton.setDisable(true);
        loginButton.setText("Authenticating...");
        errorLabel.setText("");

        // Perform authentication + heavy PBKDF2 key derivation on background thread
        Task<UserSessionResult> loginTask = new Task<>() {
            @Override
            protected UserSessionResult call() {
                // Step 1: Validate login against DB
                User user = authService.login(username, password);
                
                // Step 2: Derive AES key from password and unique salt (heavy iterations)
                byte[] derivedKey = hasher.deriveKey(password.toCharArray(), user.getSalt());
                
                return new UserSessionResult(user, derivedKey);
            }
        };

        loginTask.setOnSucceeded(e -> {
            UserSessionResult result = loginTask.getValue();
            // Store session
            SessionManager.getInstance().login(result.user(), result.derivedKey());
            
            // Zero out local copy of the key bytes immediately
            Arrays.fill(result.derivedKey(), (byte) 0);
            
            // Switch screen to Dashboard
            NavigationManager.switchScene("/fxml/dashboard.fxml", "SecureVault Pro — Dashboard");
        });

        loginTask.setOnFailed(e -> {
            Throwable ex = loginTask.getException();
            loginButton.setDisable(false);
            loginButton.setText("Log In");
            if (ex instanceof AuthenticationException || ex.getCause() instanceof AuthenticationException) {
                errorLabel.setText(ex.getMessage());
            } else {
                errorLabel.setText("An unexpected error occurred during login.");
                ex.printStackTrace();
            }
        });

        new Thread(loginTask).start();
    }

    @FXML
    private void showRegister(ActionEvent event) {
        NavigationManager.switchScene("/fxml/register.fxml", "SecureVault Pro — Create Account");
    }

    // Helper class to pass data between threads securely
    private record UserSessionResult(User user, byte[] derivedKey) {}
}
