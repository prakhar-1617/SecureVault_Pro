package com.securevault.ui;

import com.securevault.config.ConfigurationManager;
import com.securevault.database.DatabaseManager;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.sql.Connection;

/**
 * Controller for the Configurations / Settings screen.
 * Reads configurations, supports validating and saving updates back to config.properties,
 * and reports database connection status.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class SettingsController {

    @FXML private TextField threadPoolField;
    @FXML private TextField cacheSizeField;
    @FXML private ComboBox<String> defaultAlgoBox;
    @FXML private TextField bufferSizeField;
    @FXML private TextField nioThresholdField;
    @FXML private TextField auditFlushField;

    @FXML private Label dbUrlLabel;
    @FXML private Label dbStatusLabel;
    @FXML private Label statusLabel;

    private final ConfigurationManager config;

    public SettingsController() {
        this.config = ConfigurationManager.getInstance();
    }

    @FXML
    public void initialize() {
        // Configure Default Algo Box options
        defaultAlgoBox.setItems(FXCollections.observableArrayList("AES", "XOR", "CAESAR"));

        loadCurrentSettings();
        checkDatabaseStatus();
    }

    @FXML
    private void handleSaveSettings(ActionEvent event) {
        statusLabel.setText("");
        statusLabel.setTextFill(Color.WHITE);

        try {
            // Validate integer/long formats
            int threadPoolSize = Integer.parseInt(threadPoolField.getText().trim());
            int cacheSize = Integer.parseInt(cacheSizeField.getText().trim());
            int bufferSize = Integer.parseInt(bufferSizeField.getText().trim());
            long nioThreshold = Long.parseLong(nioThresholdField.getText().trim());
            int auditFlush = Integer.parseInt(auditFlushField.getText().trim());

            String defaultAlgo = defaultAlgoBox.getValue();

            if (threadPoolSize <= 0 || cacheSize <= 0 || bufferSize <= 0 || nioThreshold <= 0 || auditFlush <= 0) {
                showError("Values must be greater than zero.");
                return;
            }

            if (defaultAlgo == null) {
                showError("Please select a default encryption algorithm.");
                return;
            }

            // Save to ConfigurationManager (persists to properties file)
            config.setProperty("thread.pool.size", String.valueOf(threadPoolSize));
            config.setProperty("cache.size", String.valueOf(cacheSize));
            config.setProperty("default.algorithm", defaultAlgo);
            config.setProperty("buffer.size", String.valueOf(bufferSize));
            config.setProperty("nio.threshold.bytes", String.valueOf(nioThreshold));
            config.setProperty("audit.flush.interval.seconds", String.valueOf(auditFlush));

            statusLabel.setTextFill(Color.web("#10b981")); // success green
            statusLabel.setText("Configurations saved and applied successfully!");

        } catch (NumberFormatException ex) {
            showError("Please enter valid numeric values for pool size, cache size, buffer, threshold, and flush interval.");
        } catch (Exception ex) {
            showError("Error saving configurations: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    private void handleResetSettings(ActionEvent event) {
        // Reset to default config values
        threadPoolField.setText("4");
        cacheSizeField.setText("50");
        defaultAlgoBox.setValue("AES");
        bufferSizeField.setText("4096");
        nioThresholdField.setText("10485760");
        auditFlushField.setText("10");
        
        statusLabel.setTextFill(Color.web("#f59e0b")); // warning orange
        statusLabel.setText("Values reset to default. Click Save to apply.");
    }

    private void loadCurrentSettings() {
        threadPoolField.setText(String.valueOf(config.getThreadPoolSize()));
        cacheSizeField.setText(String.valueOf(config.getCacheSize()));
        defaultAlgoBox.setValue(config.getDefaultAlgorithm());
        bufferSizeField.setText(String.valueOf(config.getBufferSize()));
        nioThresholdField.setText(String.valueOf(config.getNioThresholdBytes()));
        auditFlushField.setText(String.valueOf(config.getAuditFlushIntervalSeconds()));
        
        // Hide password/sensitive parts of the JDBC URL for UI display
        String rawUrl = config.getDbUrl();
        int queryIdx = rawUrl.indexOf('?');
        dbUrlLabel.setText(queryIdx >= 0 ? rawUrl.substring(0, queryIdx) : rawUrl);
    }

    private void checkDatabaseStatus() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            if (conn != null && conn.isValid(2)) {
                dbStatusLabel.setText("CONNECTED");
                dbStatusLabel.setTextFill(Color.web("#10b981"));
            } else {
                dbStatusLabel.setText("DISCONNECTED");
                dbStatusLabel.setTextFill(Color.web("#ef4444"));
            }
        } catch (Exception e) {
            dbStatusLabel.setText("CONNECTION ERROR");
            dbStatusLabel.setTextFill(Color.web("#ef4444"));
        }
    }

    private void showError(String msg) {
        statusLabel.setTextFill(Color.web("#ef4444")); // error red
        statusLabel.setText(msg);
    }
}
