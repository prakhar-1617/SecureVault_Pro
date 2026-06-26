package com.securevault.ui;

import com.securevault.auth.AuthenticationService;
import com.securevault.exceptions.AuthenticationException;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller for the Register screen.
 * Validates requirements and executes password hashing on a background thread.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private Button registerButton;

    private final AuthenticationService authService;

    public RegisterController() {
        this.authService = new AuthenticationService();
    }

    @FXML
    public void initialize() {
        errorLabel.setText("");
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            errorLabel.setText("Please fill out all fields.");
            return;
        }

        if (!password.equals(confirm)) {
            errorLabel.setText("Passwords do not match.");
            return;
        }

        registerButton.setDisable(true);
        registerButton.setText("Creating Account...");
        errorLabel.setText("");

        Task<Void> registerTask = new Task<>() {
            @Override
            protected Void call() {
                authService.register(username, password);
                return null;
            }
        };

        registerTask.setOnSucceeded(e -> {
            // Success - redirect to login
            NavigationManager.switchScene("/fxml/login.fxml", "SecureVault Pro — Log In");
        });

        registerTask.setOnFailed(e -> {
            registerButton.setDisable(false);
            registerButton.setText("Register");
            Throwable ex = registerTask.getException();
            if (ex instanceof AuthenticationException || ex.getCause() instanceof AuthenticationException) {
                errorLabel.setText(ex.getMessage());
            } else {
                errorLabel.setText("Registration failed. Please try again.");
                ex.printStackTrace();
            }
        });

        new Thread(registerTask).start();
    }

    @FXML
    private void showLogin(ActionEvent event) {
        NavigationManager.switchScene("/fxml/login.fxml", "SecureVault Pro — Log In");
    }
}
