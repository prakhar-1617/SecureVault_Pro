package com.securevault;

import com.securevault.api.ApiServer;
import java.io.IOException;

/**
 * Main bootstrap class to start the SecureVault Pro Web API server.
 */
public class Main {
    public static void main(String[] args) {
        try {
            ApiServer server = new ApiServer();
            server.start();

            // Add a shutdown hook to clean up resources gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[Main] Shutdown hook triggered. Stopping server...");
                server.stop();
            }));

            // Keep the main thread alive
            System.out.println("[Main] SecureVault Pro is running. Press Ctrl+C to terminate.");
            Thread.currentThread().join();
        } catch (IOException | InterruptedException e) {
            System.err.println("[Main] Critical error starting application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
