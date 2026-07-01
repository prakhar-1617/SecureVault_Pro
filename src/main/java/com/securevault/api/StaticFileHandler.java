package com.securevault.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StaticFileHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow GET requests for static files
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
            return;
        }

        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.equals("/index.html")) {
            path = "/public/index.html";
        } else if (path.equals("/dashboard") || path.equals("/dashboard.html")) {
            path = "/public/dashboard.html";
        } else {
            path = "/public" + path;
        }

        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                // If not found in classpath (packaged JAR), try reading from filesystem (development mode)
                // Remove leading slash for local file path relative to src/main/resources
                String localPath = "src/main/resources" + path;
                java.io.File file = new java.io.File(localPath);
                if (file.exists() && file.isFile()) {
                    serveFile(exchange, new java.io.FileInputStream(file), path);
                } else {
                    byte[] response = "404 Not Found".getBytes();
                    exchange.sendResponseHeaders(404, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                }
            } else {
                serveFile(exchange, is, path);
            }
        }
    }

    private void serveFile(HttpExchange exchange, InputStream is, String path) throws IOException {
        String contentType = "text/plain";
        if (path.endsWith(".html")) {
            contentType = "text/html";
        } else if (path.endsWith(".css")) {
            contentType = "text/css";
        } else if (path.endsWith(".js")) {
            contentType = "application/javascript";
        } else if (path.endsWith(".png")) {
            contentType = "image/png";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (path.endsWith(".svg")) {
            contentType = "image/svg+xml";
        }

        exchange.getResponseHeaders().set("Content-Type", contentType);

        byte[] bytes = is.readAllBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
