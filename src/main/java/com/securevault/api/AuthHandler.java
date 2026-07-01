package com.securevault.api;

import com.securevault.auth.AuthenticationService;
import com.securevault.auth.PasswordHasher;
import com.securevault.auth.SessionManager;
import com.securevault.auth.User;
import com.securevault.exceptions.AuthenticationException;
import com.securevault.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AuthHandler implements HttpHandler {

    private final String action;
    private final AuthenticationService authService;
    private final PasswordHasher hasher;

    public AuthHandler(String action) {
        this.action = action;
        this.authService = new AuthenticationService();
        this.hasher = new PasswordHasher();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            setCorsHeaders(exchange);
            switch (action) {
                case "register":
                    handleRegister(exchange);
                    break;
                case "login":
                    handleLogin(exchange);
                    break;
                case "logout":
                    handleLogout(exchange);
                    break;
                case "session":
                    handleSession(exchange);
                    break;
                default:
                    sendError(exchange, 404, "Action not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        Map<String, String> params = JsonUtil.parse(body);
        String username = params.get("username");
        String password = params.get("password");

        try {
            User user = authService.register(username, password);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Registration successful");
            sendJson(exchange, 200, JsonUtil.toJson(response));
        } catch (AuthenticationException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            sendJson(exchange, 400, JsonUtil.toJson(response));
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        String body = readBody(exchange);
        Map<String, String> params = JsonUtil.parse(body);
        String username = params.get("username");
        String password = params.get("password");

        try {
            User user = authService.login(username, password);
            
            // Derive user key and update session
            byte[] key = hasher.deriveKey(password.toCharArray(), user.getSalt());
            SessionManager.getInstance().login(user, key);
            // Securely clean up the temporary key reference here since SessionManager copied it
            java.util.Arrays.fill(key, (byte) 0);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("username", user.getUsername());
            sendJson(exchange, 200, JsonUtil.toJson(response));
        } catch (AuthenticationException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            sendJson(exchange, 400, JsonUtil.toJson(response));
        }
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        SessionManager.getInstance().logout();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Logged out successfully");
        sendJson(exchange, 200, JsonUtil.toJson(response));
    }

    private void handleSession(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        SessionManager session = SessionManager.getInstance();
        Map<String, Object> response = new HashMap<>();
        if (session.isActive()) {
            response.put("active", true);
            response.put("username", session.getCurrentUser().getUsername());
        } else {
            response.put("active", false);
        }
        sendJson(exchange, 200, JsonUtil.toJson(response));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("message", message);
        sendJson(exchange, status, JsonUtil.toJson(err));
    }

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, DELETE, PUT");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
    }
}
