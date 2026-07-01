package com.securevault.api;

import com.securevault.config.ConfigurationManager;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Web server entry point using JDK's built-in HttpServer.
 * Eliminates external dependencies like Spring Boot or Javalin.
 */
public final class ApiServer {

    private HttpServer server;
    private final int port;

    public ApiServer() {
        this.port = ConfigurationManager.getInstance().getServerPort();
    }

    /**
     * Starts the HTTP Server and registers handlers.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Register static files handler
        StaticFileHandler staticHandler = new StaticFileHandler();
        server.createContext("/", staticHandler);
        server.createContext("/public/", staticHandler);

        // Register API handlers
        server.createContext("/api/auth/register", new AuthHandler("register"));
        server.createContext("/api/auth/login", new AuthHandler("login"));
        server.createContext("/api/auth/logout", new AuthHandler("logout"));
        server.createContext("/api/auth/session", new AuthHandler("session"));
        
        server.createContext("/api/vault", new VaultHandler());
        server.createContext("/api/files", new FileHandler());
        server.createContext("/api/analytics", new AnalyticsHandler());

        // Use custom thread executor for handling concurrent web requests
        server.setExecutor(Executors.newFixedThreadPool(
                ConfigurationManager.getInstance().getThreadPoolSize()
        ));

        server.start();
        System.out.println("[ApiServer] Web Server started at http://localhost:" + port);
    }

    /**
     * Stops the HTTP Server.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[ApiServer] Web Server stopped.");
        }
    }
}
