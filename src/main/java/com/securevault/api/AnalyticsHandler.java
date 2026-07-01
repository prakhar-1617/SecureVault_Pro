package com.securevault.api;

import com.securevault.analytics.AlgorithmStat;
import com.securevault.analytics.AnalyticsReport;
import com.securevault.analytics.AnalyticsService;
import com.securevault.auth.SessionManager;
import com.securevault.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class AnalyticsHandler implements HttpHandler {

    private final AnalyticsService analyticsService;

    public AnalyticsHandler() {
        this.analyticsService = AnalyticsService.getInstance();
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

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            AnalyticsReport report = analyticsService.generateReport();
            
            Map<String, Object> response = new HashMap<>();
            response.put("totalEncryptions", report.getTotalEncryptions());
            response.put("totalDecryptions", report.getTotalDecryptions());
            response.put("totalFailures", report.getTotalFailures());
            response.put("totalBytesEncrypted", report.getTotalBytesEncrypted());

            // Build algorithm stats list JSON manually
            PriorityQueue<AlgorithmStat> stats = report.getAlgorithmStats();
            // Duplicate the queue to safely read it without destroying the report's queue
            PriorityQueue<AlgorithmStat> statsCopy = new PriorityQueue<>(stats);
            List<String> statsJsonList = new ArrayList<>();
            
            while (!statsCopy.isEmpty()) {
                AlgorithmStat stat = statsCopy.poll();
                Map<String, Object> statMap = new HashMap<>();
                statMap.put("algorithm", stat.algorithm());
                statMap.put("operationType", stat.operationType());
                statMap.put("avgDurationMs", stat.avgDurationMs());
                statMap.put("minDurationMs", stat.minDurationMs());
                statMap.put("maxDurationMs", stat.maxDurationMs());
                statMap.put("avgQueueWaitMs", stat.avgQueueWaitMs());
                statMap.put("avgMemoryKb", stat.avgMemoryKb());
                statMap.put("operationCount", stat.operationCount());
                statsJsonList.add(JsonUtil.toJson(statMap));
            }

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"totalEncryptions\":").append(report.getTotalEncryptions()).append(",");
            sb.append("\"totalDecryptions\":").append(report.getTotalDecryptions()).append(",");
            sb.append("\"totalFailures\":").append(report.getTotalFailures()).append(",");
            sb.append("\"totalBytesEncrypted\":").append(report.getTotalBytesEncrypted()).append(",");
            sb.append("\"algorithmStats\":[");
            for (int i = 0; i < statsJsonList.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(statsJsonList.get(i));
            }
            sb.append("]}");

            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
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
