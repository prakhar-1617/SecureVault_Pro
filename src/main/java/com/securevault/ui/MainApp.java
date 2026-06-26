package com.securevault.ui;

import com.securevault.database.DatabaseManager;
import com.securevault.thread.ThreadPoolManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main JavaFX Application class.
 * Boots the application and handles graceful resource cleanup during shutdown.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        NavigationManager.setStage(primaryStage);
        
        // Start by showing the Login screen
        NavigationManager.switchScene("/fxml/login.fxml", "SecureVault Pro — Log In");
        
        primaryStage.setResizable(false);
    }

    @Override
    public void stop() {
        System.out.println("[MainApp] Application stopping. Cleaning up resources...");
        
        // Gracefully shutdown daemon thread workers
        try {
            ThreadPoolManager.getInstance().shutdown();
        } catch (Exception e) {
            System.err.println("Error shutting down ThreadPoolManager: " + e.getMessage());
        }

        // Close MySQL connections
        try {
            DatabaseManager.getInstance().shutdown();
        } catch (Exception e) {
            System.err.println("Error shutting down DatabaseManager: " + e.getMessage());
        }

        System.out.println("[MainApp] Cleanup complete. Shutdown successful.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
