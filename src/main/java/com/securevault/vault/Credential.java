package com.securevault.vault;

import java.time.LocalDateTime;

/**
 * Model class representing a credential stored in the password vault.
 * Passwords are stored as encrypted bytes in the database to prevent unauthorized access.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class Credential {

    private int credId;
    private int ownerId;
    private String website;
    private String username;
    private byte[] encryptedPassword; // Encrypted using the user's master key derivative
    private String notes;
    private LocalDateTime lastModified;

    public Credential() {}

    public Credential(int credId, int ownerId, String website, String username, byte[] encryptedPassword, String notes, LocalDateTime lastModified) {
        this.credId = credId;
        this.ownerId = ownerId;
        this.website = website;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.notes = notes;
        this.lastModified = lastModified;
    }

    public int getCredId() {
        return credId;
    }

    public void setCredId(int credId) {
        this.credId = credId;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public byte[] getEncryptedPassword() {
        return encryptedPassword;
    }

    public void setEncryptedPassword(byte[] encryptedPassword) {
        this.encryptedPassword = encryptedPassword;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    @Override
    public String toString() {
        return "Credential{" +
                "credId=" + credId +
                ", ownerId=" + ownerId +
                ", website='" + website + '\'' +
                ", username='" + username + '\'' +
                ", notes='" + notes + '\'' +
                ", lastModified=" + lastModified +
                '}';
    }
}
