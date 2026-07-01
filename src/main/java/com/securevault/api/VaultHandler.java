package com.securevault.api;

import com.securevault.auth.SessionManager;
import com.securevault.util.JsonUtil;
import com.securevault.vault.Credential;
import com.securevault.vault.PasswordVaultService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VaultHandler implements HttpHandler {

    private final PasswordVaultService vaultService;

    public VaultHandler() {
        this.vaultService = new PasswordVaultService();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        setCorsHeaders(exchange);

        // Security check: must have active session
        SessionManager session = SessionManager.getInstance();
        if (!session.isActive()) {
            sendError(exchange, 401, "Unauthorized: Please log in");
            return;
        }

        try {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                handleGet(exchange, session.getCurrentUser().getUserId());
            } else if ("POST".equalsIgnoreCase(method)) {
                handlePost(exchange, session.getCurrentUser().getUserId());
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDelete(exchange);
            } else {
                sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleGet(HttpExchange exchange, int userId) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> queryParams = parseQueryParams(query);
        String keyword = queryParams.get("q");

        List<Map<String, Object>> responseList = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            Map<String, Credential> searchResults = vaultService.search(userId, keyword);
            for (Credential cred : searchResults.values()) {
                responseList.add(serializeCredential(cred));
            }
        } else {
            List<Credential> list = vaultService.getCredentialsForUser(userId);
            for (Credential cred : list) {
                responseList.add(serializeCredential(cred));
            }
        }

        // Return JSON list manually since JsonUtil only serializes maps
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < responseList.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonUtil.toJson(responseList.get(i)));
        }
        sb.append("]");
        sendJson(exchange, 200, sb.toString());
    }

    private void handlePost(HttpExchange exchange, int userId) throws IOException {
        String body = readBody(exchange);
        Map<String, String> params = JsonUtil.parse(body);
        
        String credIdStr = params.get("credId");
        String website = params.get("website");
        String username = params.get("username");
        String password = params.get("password");
        String notes = params.get("notes");

        if (website == null || username == null || password == null) {
            sendError(exchange, 400, "Missing required parameters");
            return;
        }

        if (credIdStr != null && !credIdStr.trim().isEmpty() && !credIdStr.equals("null")) {
            // Update
            int credId = Integer.parseInt(credIdStr.trim());
            vaultService.updateCredential(credId, website, username, password, notes);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Credential updated successfully");
            sendJson(exchange, 200, JsonUtil.toJson(resp));
        } else {
            // Create
            vaultService.addCredential(userId, website, username, password, notes);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Credential created successfully");
            sendJson(exchange, 200, JsonUtil.toJson(resp));
        }
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> queryParams = parseQueryParams(query);
        String idStr = queryParams.get("id");

        if (idStr == null || idStr.isEmpty()) {
            sendError(exchange, 400, "Missing credential ID");
            return;
        }

        int credId = Integer.parseInt(idStr);
        vaultService.deleteCredential(credId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Credential deleted successfully");
        sendJson(exchange, 200, JsonUtil.toJson(resp));
    }

    private Map<String, Object> serializeCredential(Credential cred) {
        Map<String, Object> m = new HashMap<>();
        m.put("credId", cred.getCredId());
        m.put("website", cred.getWebsite());
        m.put("username", cred.getUsername());
        m.put("password", vaultService.decryptPassword(cred));
        m.put("notes", cred.getNotes() != null ? cred.getNotes() : "");
        m.put("lastModified", cred.getLastModified().toString());
        return m;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(k, v);
        }
        return map;
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
