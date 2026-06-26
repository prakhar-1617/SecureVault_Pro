package com.securevault.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Static navigator that acts as a controller manager for stage scene changes.
 * Centralizes UI switching logic so individual controllers don't need references
 * to the stage.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class NavigationManager {

    private static Stage primaryStage;

    private NavigationManager() {}

    /**
     * Initializes the manager with the primary JavaFX stage.
     *
     * @param stage the primary application stage
     */
    public static void setStage(Stage stage) {
        primaryStage = stage;
    }

    /**
     * Loads and switches the primary stage scene to the specified FXML layout.
     *
     * @param fxmlPath classpath resource path to the FXML layout (e.g. "/fxml/login.fxml")
     * @param title    new window title
     */
    public static void switchScene(String fxmlPath, String title) {
        if (primaryStage == null) {
            System.err.println("[NavigationManager] Stage not set.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(NavigationManager.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            
            primaryStage.setScene(scene);
            primaryStage.setTitle(title);
            primaryStage.centerOnScreen();
            primaryStage.show();
            
            System.out.println("[NavigationManager] Navigated to screen: " + fxmlPath);
        } catch (IOException e) {
            System.err.println("[NavigationManager] Critical: Failed to switch scene to: " + fxmlPath);
            e.printStackTrace();
        }
    }
}
