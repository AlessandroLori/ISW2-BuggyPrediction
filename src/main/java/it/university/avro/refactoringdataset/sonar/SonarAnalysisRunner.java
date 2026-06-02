package it.university.avro.refactoringdataset.sonar;

import it.university.avro.common.ApplicationLog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class SonarAnalysisRunner {

    private static final int HTTP_OK = 200;

    private final SonarAnalysisConfiguration analysisConfiguration;
    private final SonarCloudConfiguration cloudConfiguration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SonarAnalysisRunner(
            SonarAnalysisConfiguration analysisConfiguration,
            SonarCloudConfiguration cloudConfiguration
    ) {
        this.analysisConfiguration = analysisConfiguration;
        this.cloudConfiguration = cloudConfiguration;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void runBeforeMetricsGeneration() {
        if (!analysisConfiguration.autoAnalyze()) {
            ApplicationLog.info("[SONAR-AUTO] Auto analysis disabled. Set SONAR_AUTO_ANALYZE=true to enable it.");
            return;
        }

        validateConfiguration();
        runScanner();

        if (analysisConfiguration.waitForProcessing()) {
            waitForComputeEngineTask();
        } else {
            ApplicationLog.info("[SONAR-AUTO] Scanner completed, but processing wait is disabled.");
        }
    }

    private void validateConfiguration() {
        if (!cloudConfiguration.isEnabled()) {
            throw new IllegalStateException(
                    "SonarCloud configuration is incomplete. Set SONAR_PROJECT_KEY and SONAR_BASE_URL."
            );
        }
        if (cloudConfiguration.token().isBlank()) {
            throw new IllegalStateException(
                    "SONAR_TOKEN is missing. Export it before running the refactoring dataset generator."
            );
        }
        if (!Files.isDirectory(analysisConfiguration.projectBaseDirectory())) {
            throw new IllegalStateException(
                    "AVRO_PROJECT_ROOT does not exist or is not a directory: "
                            + analysisConfiguration.projectBaseDirectory()
            );
        }
    }

    private void runScanner() {
        List<String> command = new ArrayList<>();
        command.add(analysisConfiguration.scannerCommand());
        command.add("-Dsonar.host.url=" + cloudConfiguration.baseUrl());
        command.add("-Dsonar.token=" + cloudConfiguration.token());
        command.add("-Dsonar.projectKey=" + cloudConfiguration.projectKey());
        command.add("-Dsonar.projectName=" + analysisConfiguration.projectName());
        command.add("-Dsonar.sources=" + analysisConfiguration.sources());
        command.add("-Dsonar.exclusions=" + analysisConfiguration.exclusions());

        addIfNotBlank(command, "-Dsonar.tests=", analysisConfiguration.tests());
        addIfNotBlank(command, "-Dsonar.java.binaries=", analysisConfiguration.javaBinaries());
        addIfNotBlank(command, "-Dsonar.organization=", analysisConfiguration.organization());
        addIfNotBlank(command, "-Dsonar.branch.name=", cloudConfiguration.branch());

        ApplicationLog.info("[SONAR-AUTO] Running Sonar analysis from " + analysisConfiguration.projectBaseDirectory());
        ApplicationLog.info("[SONAR-AUTO] Sources=" + analysisConfiguration.sources());
        ApplicationLog.info("[SONAR-AUTO] ProjectKey=" + cloudConfiguration.projectKey());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(analysisConfiguration.projectBaseDirectory().toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    ApplicationLog.info("[SONAR-SCANNER] " + line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Sonar scanner failed with exit code " + exitCode);
            }
            ApplicationLog.info("[SONAR-AUTO] Scanner execution completed.");
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to execute sonar scanner command '" + analysisConfiguration.scannerCommand()
                            + "'. Install sonar-scanner or set SONAR_SCANNER_COMMAND.",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sonar scanner execution interrupted", exception);
        }
    }

    private void waitForComputeEngineTask() {
        Path reportTaskPath = analysisConfiguration.projectBaseDirectory()
                .resolve(".scannerwork")
                .resolve("report-task.txt");

        if (!Files.isRegularFile(reportTaskPath)) {
            throw new IllegalStateException("Sonar report-task.txt not found: " + reportTaskPath);
        }

        Properties reportTask = new Properties();
        try (var inputStream = Files.newInputStream(reportTaskPath)) {
            reportTask.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Sonar report-task.txt: " + reportTaskPath, exception);
        }

        String ceTaskUrl = reportTask.getProperty("ceTaskUrl", "").trim();
        String ceTaskId = reportTask.getProperty("ceTaskId", "").trim();
        if (ceTaskUrl.isBlank() && !ceTaskId.isBlank()) {
            ceTaskUrl = cloudConfiguration.baseUrl() + "/api/ce/task?id=" + ceTaskId;
        }
        if (ceTaskUrl.isBlank()) {
            throw new IllegalStateException("Sonar report-task.txt does not contain ceTaskUrl or ceTaskId");
        }

        ApplicationLog.info("[SONAR-AUTO] Waiting for SonarCloud processing to finish...");
        Instant deadline = Instant.now().plusSeconds(analysisConfiguration.waitTimeoutSeconds());
        while (Instant.now().isBefore(deadline)) {
            JsonNode task = getJson(ceTaskUrl)
                    .map(json -> json.path("task"))
                    .orElseThrow(() -> new IllegalStateException("Unable to read Sonar CE task status"));

            String status = task.path("status").asText("");
            if ("SUCCESS".equals(status)) {
                ApplicationLog.info("[SONAR-AUTO] SonarCloud processing completed successfully.");
                return;
            }
            if ("FAILED".equals(status) || "CANCELED".equals(status)) {
                String errorMessage = task.path("errorMessage").asText("no_error_message_available");
                throw new IllegalStateException("SonarCloud processing " + status + ": " + errorMessage);
            }

            ApplicationLog.info("[SONAR-AUTO] SonarCloud status=" + status);
            sleepPollInterval();
        }

        throw new IllegalStateException(
                "Timed out while waiting for SonarCloud processing after "
                        + analysisConfiguration.waitTimeoutSeconds() + " seconds"
        );
    }

    private Optional<JsonNode> getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + cloudConfiguration.token())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() != HTTP_OK) {
                ApplicationLog.info("[SONAR-AUTO-WARNING] status=" + response.statusCode() + " | url=" + url);
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(response.body()));
        } catch (IOException exception) {
            ApplicationLog.info("[SONAR-AUTO-WARNING] unable_to_read_json | url=" + url
                    + " | error=" + exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private void sleepPollInterval() {
        try {
            Thread.sleep(Duration.ofSeconds(analysisConfiguration.pollIntervalSeconds()).toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for SonarCloud processing", exception);
        }
    }

    private void addIfNotBlank(List<String> command, String prefix, String value) {
        if (value != null && !value.isBlank()) {
            command.add(prefix + value.trim());
        }
    }
}
