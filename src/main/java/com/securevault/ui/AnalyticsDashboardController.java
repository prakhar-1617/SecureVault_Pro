package com.securevault.ui;

import com.securevault.analytics.AlgorithmStat;
import com.securevault.analytics.AnalyticsReport;
import com.securevault.analytics.AnalyticsService;
import com.securevault.storage.FileStorageService;
import com.securevault.thread.ThreadPoolManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Controller for the System Analytics screen.
 * Queries performance stats asynchronously and updates JavaFX charts and KPI cards.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class AnalyticsDashboardController {

    @FXML private Label totalOpsLabel;
    @FXML private Label avgDurationLabel;
    @FXML private Label cacheRatioLabel;
    @FXML private Label activeThreadsLabel;

    @FXML private BarChart<String, Number> latencyChart;
    @FXML private CategoryAxis algoAxis;
    @FXML private PieChart distributionChart;

    private final AnalyticsService analyticsService;
    private final FileStorageService storageService;

    public AnalyticsDashboardController() {
        this.analyticsService = AnalyticsService.getInstance();
        this.storageService = FileStorageService.getInstance();
    }

    @FXML
    public void initialize() {
        loadStats();
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadStats();
    }

    private void loadStats() {
        Task<AnalyticsDataPack> task = new Task<>() {
            @Override
            protected AnalyticsDataPack call() {
                AnalyticsReport report = analyticsService.generateReport();
                double cacheRatio = storageService.getCache().getHitRatio();
                
                int activeCount = ThreadPoolManager.getInstance().getActiveCount();
                if (activeCount < 0) {
                    activeCount = ThreadPoolManager.getInstance().getPoolSize(); // Fallback
                }
                
                return new AnalyticsDataPack(report, cacheRatio, activeCount);
            }
        };

        task.setOnSucceeded(e -> {
            AnalyticsDataPack pack = task.getValue();
            updateUI(pack);
        });

        new Thread(task).start();
    }

    private void updateUI(AnalyticsDataPack pack) {
        AnalyticsReport report = pack.report();
        
        long totalOps = report.getTotalEncryptions() + report.getTotalDecryptions();
        totalOpsLabel.setText(String.valueOf(totalOps));
        
        // Calculate average duration across all algorithms
        double sumDuration = 0;
        long totalCount = 0;
        
        // Group algorithm stats for charts
        Map<String, Double> latencyMap = new HashMap<>();
        Map<String, Long> usageMap = new HashMap<>();
        
        PriorityQueue<AlgorithmStat> stats = report.getAlgorithmStats();
        if (stats != null) {
            // PriorityQueue is destructive on iteration/polling in sorted order,
            // so we copy it or poll it to extract data.
            PriorityQueue<AlgorithmStat> tempQueue = new PriorityQueue<>(stats);
            while (!tempQueue.isEmpty()) {
                AlgorithmStat stat = tempQueue.poll();
                sumDuration += (stat.avgDurationMs() * stat.operationCount());
                totalCount += stat.operationCount();
                
                // Only show encryption latency on the bar chart
                if ("ENCRYPT".equals(stat.operationType())) {
                    latencyMap.put(stat.algorithm(), stat.avgDurationMs());
                }
                // Accumulate usage (encrypt + decrypt counts)
                usageMap.put(stat.algorithm(), usageMap.getOrDefault(stat.algorithm(), 0L) + stat.operationCount());
            }
        }
        
        double overallAvg = totalCount == 0 ? 0.0 : sumDuration / totalCount;
        avgDurationLabel.setText(String.format("%.1f ms", overallAvg));
        cacheRatioLabel.setText(String.format("%.1f%%", pack.cacheRatio()));
        activeThreadsLabel.setText(String.valueOf(pack.activeThreads()));

        // --- Render Bar Chart (Latency) ---
        latencyChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Double> entry : latencyMap.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        latencyChart.getData().add(series);

        // --- Render Pie Chart (Distribution) ---
        distributionChart.getData().clear();
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (Map.Entry<String, Long> entry : usageMap.entrySet()) {
            pieData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
        }
        distributionChart.setData(pieData);
    }

    private record AnalyticsDataPack(AnalyticsReport report, double cacheRatio, int activeThreads) {}
}
