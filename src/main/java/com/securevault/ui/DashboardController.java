package com.securevault.ui;

import com.securevault.auth.SessionManager;
import com.securevault.auth.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Button;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Controller for the main Dashboard window.
 * Wires sidebar buttons to dynamically load child FXML views inside a center StackPane.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label lastLoginLabel;
    @FXML private Button filesMenuBtn;
    @FXML private Button passwordsMenuBtn;
    @FXML private Button analyticsMenuBtn;
    @FXML private Button settingsMenuBtn;
    @FXML private StackPane contentPane;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        SessionManager session = SessionManager.getInstance();
        if (session.isActive()) {
            User user = session.getCurrentUser();
            welcomeLabel.setText("Welcome, " + user.getUsername());
            if (user.getLastLogin() != null) {
                lastLoginLabel.setText("Last login: " + user.getLastLogin().format(DATE_FORMAT));
            } else {
                lastLoginLabel.setText("First session");
            }
        }
        
        // Load default screen (Files)
        loadScreen("/fxml/file_manager.fxml");
    }

    @FXML
    private void showFilesScreen(ActionEvent event) {
        setActiveButton(filesMenuBtn);
        loadScreen("/fxml/file_manager.fxml");
    }

    @FXML
    private void showPasswordsScreen(ActionEvent event) {
        setActiveButton(passwordsMenuBtn);
        loadScreen("/fxml/vault.fxml");
    }

    @FXML
    private void showAnalyticsScreen(ActionEvent event) {
        setActiveButton(analyticsMenuBtn);
        loadScreen("/fxml/analytics.fxml");
    }

    @FXML
    private void showSettingsScreen(ActionEvent event) {
        setActiveButton(settingsMenuBtn);
        loadScreen("/fxml/settings.fxml");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        SessionManager.getInstance().logout();
        NavigationManager.switchScene("/fxml/login.fxml", "SecureVault Pro — Log In");
    }

    private void loadScreen(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);
        } catch (IOException e) {
            System.err.println("[DashboardController] Failed to load FXML: " + fxmlPath);
            e.printStackTrace();
        }
    }

    private void setActiveButton(Button activeBtn) {
        // Reset all menu buttons to default styling classes
        filesMenuBtn.getStyleClass().removeAll("menu-btn-active");
        passwordsMenuBtn.getStyleClass().removeAll("menu-btn-active");
        analyticsMenuBtn.getStyleClass().removeAll("menu-btn-active");
        settingsMenuBtn.getStyleClass().removeAll("menu-btn-active");

        // Add active styling to clicked button
        activeBtn.getStyleClass().add("menu-btn-active");
    }
}
