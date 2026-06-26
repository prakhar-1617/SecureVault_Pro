package com.securevault.ui;

import com.securevault.auth.SessionManager;
import com.securevault.vault.Credential;
import com.securevault.vault.PasswordVaultService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;

/**
 * Controller for the Password Vault screen.
 * Implements CRUD actions on credentials, automatic form population on selection,
 * password masking, search, and a Clipboard utility for decrypted passwords.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class VaultController {

    @FXML private TextField searchField;
    @FXML private TableView<Credential> credentialsTable;
    @FXML private TableColumn<Credential, Integer> idCol;
    @FXML private TableColumn<Credential, String> websiteCol;
    @FXML private TableColumn<Credential, String> usernameCol;
    @FXML private TableColumn<Credential, String> passwordCol;
    @FXML private TableColumn<Credential, String> modifiedCol;

    @FXML private Label formTitleLabel;
    @FXML private TextField websiteField;
    @FXML private TextField usernameField;
    @FXML private TextField passwordField;
    @FXML private TextArea notesField;
    @FXML private Label formErrorLabel;
    @FXML private Button saveBtn;
    @FXML private Button clearBtn;
    @FXML private Button showPasswordBtn;
    @FXML private Button deleteBtn;

    private final PasswordVaultService vaultService;
    private int currentUserId;
    private Credential selectedCredential;
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public VaultController() {
        this.vaultService = new PasswordVaultService();
        SessionManager session = SessionManager.getInstance();
        this.currentUserId = session.isActive() ? session.getCurrentUser().getUserId() : -1;
    }

    @FXML
    public void initialize() {
        formErrorLabel.setText("");

        // Setup columns (Typesafe record or class getters)
        idCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getCredId()).asObject());
        websiteCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getWebsite()));
        usernameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUsername()));
        passwordCol.setCellValueFactory(cellData -> new SimpleStringProperty("••••••••"));
        modifiedCol.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getLastModified() != null ? cellData.getValue().getLastModified().format(DATE_FORMAT) : ""
        ));

        // Listen for row selections to load into form for Editing
        credentialsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                selectedCredential = newSelection;
                populateFormForEdit(newSelection);
            }
        });

        // Double click row to show password
        credentialsTable.setRowFactory(tv -> {
            TableRow<Credential> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    decryptAndShowPassword(row.getItem());
                }
            });
            return row;
        });

        loadCredentials();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        loadCredentials();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        searchField.clear();
        loadCredentials();
    }

    @FXML
    private void handleShowPassword(ActionEvent event) {
        Credential selected = credentialsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a credential row to decrypt.");
            return;
        }
        decryptAndShowPassword(selected);
    }

    @FXML
    private void handleDeleteCredential(ActionEvent event) {
        Credential selected = credentialsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a credential row to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Password Entry");
        confirm.setContentText("Are you sure you want to delete the login credentials for '" + selected.getWebsite() + "'? This cannot be undone.");

        confirm.showAndWait().ifPresent(btnType -> {
            if (btnType == ButtonType.OK) {
                vaultService.deleteCredential(selected.getCredId());
                handleClearForm(null);
                loadCredentials();
            }
        });
    }

    @FXML
    private void handleSaveCredential(ActionEvent event) {
        String website = websiteField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String notes = notesField.getText() == null ? "" : notesField.getText().trim();

        if (website.isEmpty() || username.isEmpty() || password.isEmpty()) {
            formErrorLabel.setText("Please fill out website, username, and password.");
            return;
        }

        formErrorLabel.setText("");

        if (selectedCredential == null) {
            // Add new
            vaultService.addCredential(currentUserId, website, username, password, notes);
            showAlert(Alert.AlertType.INFORMATION, "Saved", "New credential stored successfully.");
        } else {
            // Update existing
            vaultService.updateCredential(selectedCredential.getCredId(), website, username, password, notes);
            showAlert(Alert.AlertType.INFORMATION, "Updated", "Credential updated successfully.");
        }

        handleClearForm(null);
        loadCredentials();
    }

    @FXML
    private void handleClearForm(ActionEvent event) {
        selectedCredential = null;
        formTitleLabel.setText("Add New Credential");
        websiteField.clear();
        usernameField.clear();
        passwordField.clear();
        notesField.clear();
        formErrorLabel.setText("");
        credentialsTable.getSelectionModel().clearSelection();
    }

    private void decryptAndShowPassword(Credential credential) {
        try {
            String decryptedPassword = vaultService.decryptPassword(credential);

            Alert showPassAlert = new Alert(Alert.AlertType.INFORMATION);
            showPassAlert.setTitle("Decrypted Credentials");
            showPassAlert.setHeaderText("Service: " + credential.getWebsite());
            
            // Format content beautifully
            showPassAlert.setContentText("Username: " + credential.getUsername() + "\nPassword: " + decryptedPassword);

            // Add Custom Clipboard Button
            ButtonType copyBtnType = new ButtonType("📋 Copy Password");
            ButtonType closeBtnType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            showPassAlert.getButtonTypes().setAll(copyBtnType, closeBtnType);

            // Style Alert Dialog
            DialogPane pane = showPassAlert.getDialogPane();
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
            pane.getStyleClass().add("glass-panel");

            showPassAlert.showAndWait().ifPresent(btn -> {
                if (btn == copyBtnType) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(decryptedPassword);
                    clipboard.setContent(content);
                }
            });
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Decryption Error", e.getMessage());
        }
    }

    private void populateFormForEdit(Credential cred) {
        formTitleLabel.setText("Edit Credential");
        websiteField.setText(cred.getWebsite());
        usernameField.setText(cred.getUsername());
        
        // Decrypt password to show in the form for editing
        try {
            passwordField.setText(vaultService.decryptPassword(cred));
        } catch (Exception e) {
            passwordField.clear();
        }
        
        notesField.setText(cred.getNotes() != null ? cred.getNotes() : "");
    }

    private void loadCredentials() {
        String query = searchField.getText().trim();
        
        Task<Collection<Credential>> task = new Task<>() {
            @Override
            protected Collection<Credential> call() {
                // TreeMap search guarantees alphabetical sorting on website
                Map<String, Credential> results = vaultService.search(currentUserId, query);
                return results.values();
            }
        };

        task.setOnSucceeded(e -> {
            credentialsTable.setItems(FXCollections.observableArrayList(task.getValue()));
        });

        task.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "Load Failed", "Failed to retrieve credentials from database.");
        });

        new Thread(task).start();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        pane.getStyleClass().add("glass-panel");
        alert.show();
    }
}
