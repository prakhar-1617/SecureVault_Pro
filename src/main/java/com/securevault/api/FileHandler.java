package com.securevault.api;

import com.securevault.auth.SessionManager;
import com.securevault.storage.FileMetadata;
import com.securevault.storage.FileStorageService;
import com.securevault.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileHandler implements HttpHandler {

    private final FileStorageService storageService;

    public FileHandler() {
        this.storageService = FileStorageService.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            setCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        setCorsHeaders(exchange);

        SessionManager session = SessionManager.getInstance();
        if (!session.isActive()) {
            sendError(exchange, 401, "Unauthorized");
            return;
        }

        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if (path.equals("/api/files")) {
                if ("GET".equalsIgnoreCase(method)) {
                    handleList(exchange, session.getCurrentUser().getUserId());
                } else if ("POST".equalsIgnoreCase(method)) {
                    handleUpload(exchange, session.getCurrentUser().getUserId());
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange, session.getCurrentUser().getUserId());
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
            } else if (path.equals("/api/files/download")) {
                if ("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
                    handleDownload(exchange);
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
            } else {
                sendError(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleList(HttpExchange exchange, int userId) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> queryParams = parseQueryParams(query);
        String nameFilter = queryParams.get("q");
        String algorithm = queryParams.get("algo");

        List<FileMetadata> files = storageService.searchFiles(
                userId, nameFilter, algorithm, 0, Long.MAX_VALUE, 100, 0
        );

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(JsonUtil.toJson(serializeFile(files.get(i))));
        }
        sb.append("]");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleUpload(HttpExchange exchange, int userId) throws IOException {
        String body = readBody(exchange);
        Map<String, String> params = JsonUtil.parse(body);
        String filePath = params.get("filePath");
        String algorithm = params.get("algorithm");

        if (filePath == null || algorithm == null) {
            sendError(exchange, 400, "Missing filePath or algorithm");
            return;
        }

        try {
            Path sourcePath = Path.of(filePath);
            long fileId = storageService.uploadFile(sourcePath, userId, algorithm);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("fileId", fileId);
            resp.put("message", "File encrypted and uploaded successfully");
            sendJson(exchange, 200, JsonUtil.toJson(resp));
        } catch (Exception e) {
            sendError(exchange, 400, "Upload failed: " + e.getMessage());
        }
    }

    private void handleDownload(HttpExchange exchange) throws IOException {
        Map<String, String> params;
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            params = JsonUtil.parse(body);
        } else {
            String query = exchange.getRequestURI().getQuery();
            params = parseQueryParams(query);
        }

        String idStr = params.get("id");
        String destPathStr = params.get("dest");

        if (idStr == null || destPathStr == null) {
            sendError(exchange, 400, "Missing file ID or destination path");
            return;
        }

        try {
            int fileId = Integer.parseInt(idStr);
            Path dest = Path.of(destPathStr);
            storageService.downloadFile(fileId, dest);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "File decrypted and downloaded successfully to " + destPathStr);
            sendJson(exchange, 200, JsonUtil.toJson(resp));
        } catch (Exception e) {
            sendError(exchange, 400, "Download failed: " + e.getMessage());
        }
    }

    private void handleDelete(HttpExchange exchange, int userId) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        Map<String, String> queryParams = parseQueryParams(query);
        String idStr = queryParams.get("id");

        if (idStr == null || idStr.isEmpty()) {
            sendError(exchange, 400, "Missing file ID");
            return;
        }

        try {
            int fileId = Integer.parseInt(idStr);
            storageService.deleteFile(fileId, userId);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "File record and encrypted copy deleted successfully");
            sendJson(exchange, 200, JsonUtil.toJson(resp));
        } catch (Exception e) {
            sendError(exchange, 400, "Deletion failed: " + e.getMessage());
        }
    }

    private Map<String, Object> serializeFile(FileMetadata meta) {
        Map<String, Object> m = new HashMap<>();
        m.put("fileId", meta.fileId());
        m.put("userId", meta.userId());
        m.put("originalName", meta.originalName());
        m.put("encryptedPath", meta.encryptedPath());
        m.put("checksum", meta.checksum());
        m.put("algorithm", meta.algorithm());
        m.put("fileSize", meta.fileSize());
        m.put("uploadTime", meta.uploadTime());
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
