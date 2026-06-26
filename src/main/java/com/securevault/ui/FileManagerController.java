package com.securevault.ui;

import com.securevault.auth.SessionManager;
import com.securevault.storage.FileMetadata;
import com.securevault.storage.FileStorageService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Controller for the File Manager screen.
 * Handles file encryption uploads, decryption downloads, list filters,
 * and deletion operations using background threads.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class FileManagerController {

    @FXML private Button selectFileBtn;
    @FXML private Label selectedFileLabel;
    @FXML private ComboBox<String> algoComboBox;
    @FXML private Button encryptUploadBtn;
    
    @FXML private VBox progressContainer;
    @FXML private Label progressStatusLabel;
    @FXML private Label progressPercentLabel;
    @FXML private ProgressBar uploadProgressBar;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterAlgoComboBox;
    
    @FXML private TableView<FileMetadata> filesTable;
    @FXML private TableColumn<FileMetadata, Integer> idCol;
    @FXML private TableColumn<FileMetadata, String> nameCol;
    @FXML private TableColumn<FileMetadata, String> algoCol;
    @FXML private TableColumn<FileMetadata, String> sizeCol;
    @FXML private TableColumn<FileMetadata, String> timeCol;

    @FXML private Button downloadBtn;
    @FXML private Button deleteBtn;
    @FXML private Label tableStatusLabel;

    private final FileStorageService storageService;
    private File selectedFile;
    private int currentUserId;

    public FileManagerController() {
        this.storageService = new FileStorageService();
        SessionManager session = SessionManager.getInstance();
        this.currentUserId = session.isActive() ? session.getCurrentUser().getUserId() : -1;
    }

    @FXML
    public void initialize() {
        // Configure combo boxes
        algoComboBox.setItems(FXCollections.observableArrayList("AES", "XOR", "CAESAR"));
        algoComboBox.setValue("AES");

        filterAlgoComboBox.setItems(FXCollections.observableArrayList("All Algorithms", "AES", "XOR", "CAESAR"));
        filterAlgoComboBox.setValue("All Algorithms");

        // Map Table columns using typesafe lambdas (compatible with records)
        idCol.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().fileId()).asObject());
        nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().originalName()));
        algoCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().algorithm()));
        sizeCol.setCellValueFactory(cellData -> new SimpleStringProperty(formatFileSize(cellData.getValue().fileSize())));
        timeCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().uploadTime()));

        // Double click row shortcut to download
        filesTable.setRowFactory(tv -> {
            TableRow<FileMetadata> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    downloadFile(row.getItem());
                }
            });
            return row;
        });

        // Load initially
        loadFiles();
    }

    @FXML
    private void handleSelectFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Encrypt");
        selectedFile = fileChooser.showOpenDialog(selectFileBtn.getScene().getWindow());

        if (selectedFile != null) {
            selectedFileLabel.setText(selectedFile.getName() + " (" + formatFileSize(selectedFile.length()) + ")");
        } else {
            selectedFileLabel.setText("No file selected");
        }
    }

    @FXML
    private void handleEncryptUpload(ActionEvent event) {
        if (selectedFile == null) {
            showAlert(Alert.AlertType.WARNING, "No File", "Please select a file to encrypt first.");
            return;
        }

        String algo = algoComboBox.getValue();
        if (algo == null) {
            showAlert(Alert.AlertType.WARNING, "No Algorithm", "Please select an encryption algorithm.");
            return;
        }

        // Toggle UI
        setUploadControlsDisabled(true);
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
        uploadProgressBar.setProgress(-1); // Indeterminate
        progressStatusLabel.setText("Hashing and encrypting " + selectedFile.getName() + "...");
        progressPercentLabel.setText("Processing...");

        Path sourcePath = selectedFile.toPath();

        Task<Long> uploadTask = new Task<>() {
            @Override
            protected Long call() {
                return storageService.uploadFile(sourcePath, currentUserId, algo);
            }
        };

        uploadTask.setOnSucceeded(e -> {
            uploadProgressBar.setProgress(1.0);
            progressPercentLabel.setText("Completed!");
            progressStatusLabel.setText("File encrypted and stored successfully.");
            
            // Success alert
            showAlert(Alert.AlertType.INFORMATION, "Success", "File '" + selectedFile.getName() + "' has been encrypted and stored.");
            
            // Reset upload UI
            selectedFile = null;
            selectedFileLabel.setText("No file selected");
            setUploadControlsDisabled(false);
            
            // Hide progress container
            progressContainer.setVisible(false);
            progressContainer.setManaged(false);
            
            // Refresh table
            loadFiles();
        });

        uploadTask.setOnFailed(e -> {
            setUploadControlsDisabled(false);
            progressContainer.setVisible(false);
            progressContainer.setManaged(false);
            Throwable ex = uploadTask.getException();
            showAlert(Alert.AlertType.ERROR, "Encryption Failed", ex.getMessage());
            ex.printStackTrace();
        });

        new Thread(uploadTask).start();
    }

    @FXML
    private void handleSearch(ActionEvent event) {
        loadFiles();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        searchField.clear();
        filterAlgoComboBox.setValue("All Algorithms");
        loadFiles();
    }

    @FXML
    private void handleDownloadSelected(ActionEvent event) {
        FileMetadata selected = filesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a file from the table to decrypt.");
            return;
        }
        downloadFile(selected);
    }

    @FXML
    private void handleDeleteSelected(ActionEvent event) {
        FileMetadata selected = filesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a file from the table to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Stored File");
        confirm.setContentText("Are you sure you want to permanently delete '" + selected.originalName() + "' from the vault? This cannot be undone.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    storageService.deleteFile(selected.fileId(), currentUserId);
                    loadFiles();
                    showAlert(Alert.AlertType.INFORMATION, "Deleted", "File was deleted successfully.");
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Deletion Failed", ex.getMessage());
                }
            }
        });
    }

    private void downloadFile(FileMetadata meta) {
        // Choose destination directory
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Choose Decryption Destination Folder");
        File destDir = dirChooser.showDialog(filesTable.getScene().getWindow());

        if (destDir == null) return;

        Path destPath = destDir.toPath().resolve(meta.originalName());

        // Decrypt in background
        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() {
                storageService.downloadFile(meta.fileId(), destPath);
                return null;
            }
        };

        downloadTask.setOnSucceeded(e -> {
            showAlert(Alert.AlertType.INFORMATION, "Success", "Decryption completed. File saved to: \n" + destPath.toString());
        });

        downloadTask.setOnFailed(e -> {
            Throwable ex = downloadTask.getException();
            showAlert(Alert.AlertType.ERROR, "Decryption Failed", "Verification failure or tampering detected:\n" + ex.getMessage());
            ex.printStackTrace();
        });

        new Thread(downloadTask).start();
    }

    private void loadFiles() {
        String filterName = searchField.getText().trim();
        String filterAlgo = filterAlgoComboBox.getValue();
        if ("All Algorithms".equals(filterAlgo)) {
            filterAlgo = null;
        }

        final String finalAlgo = filterAlgo;
        Task<List<FileMetadata>> loadTask = new Task<>() {
            @Override
            protected List<FileMetadata> call() {
                return storageService.searchFiles(currentUserId, filterName, finalAlgo, 0, Long.MAX_VALUE, 100, 0);
            }
        };

        loadTask.setOnSucceeded(e -> {
            List<FileMetadata> results = loadTask.getValue();
            filesTable.setItems(FXCollections.observableArrayList(results));
            tableStatusLabel.setText("Showing " + results.size() + " files");
        });

        loadTask.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "Load Failed", "Failed to retrieve files from database.");
        });

        new Thread(loadTask).start();
    }

    private void setUploadControlsDisabled(boolean disabled) {
        selectFileBtn.setDisable(disabled);
        algoComboBox.setDisable(disabled);
        encryptUploadBtn.setDisable(disabled);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        // Style alert
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        pane.getStyleClass().add("glass-panel");
        alert.show();
    }
}
