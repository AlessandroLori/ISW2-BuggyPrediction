package it.university.avro.refactoringdataset.sonar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SonarCloudCodeSmellClient {

    private static final int HTTP_OK = 200;
    private static final int PAGE_SIZE = 500;

    private final SonarCloudConfiguration configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SonarCloudCodeSmellClient(SonarCloudConfiguration configuration) {
        this.configuration = configuration;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public int countCodeSmells(String repositoryRelativePath) {
        if (!configuration.isEnabled()) {
            return unavailable("SonarCloud configuration is disabled", repositoryRelativePath);
        }

        String normalizedPath = normalizePath(repositoryRelativePath);
        Optional<ComponentCandidate> component = resolveComponent(normalizedPath);
        if (component.isEmpty()) {
            return unavailable(missingComponentMessage(normalizedPath), normalizedPath);
        }

        String componentKey = component.get().key();
        Optional<Integer> issueSearchCount = readIssueSearchCount(componentKey);
        Optional<Integer> measureValue = readCodeSmellMeasure(componentKey);

        if (issueSearchCount.isPresent()) {
            logResolvedSmellCount(normalizedPath, component.get(), issueSearchCount.get(), measureValue);
            return issueSearchCount.get();
        }

        if (measureValue.isPresent()) {
            System.out.println("[SONAR-SMELL-WARNING] issues/search unavailable; using code_smells measure fallback"
                    + " | source=" + normalizedPath
                    + " | componentKey=" + componentKey
                    + " | componentPath=" + component.get().path()
                    + " | measureCodeSmells=" + measureValue.get());
            return measureValue.get();
        }

        return unavailable(
                "Unable to read SonarCloud code smells for componentKey=" + componentKey,
                normalizedPath
        );
    }

    private Optional<ComponentCandidate> resolveComponent(String repositoryRelativePath) {
        for (String overrideCandidate : environmentOverrideCandidates(repositoryRelativePath)) {
            Optional<ComponentCandidate> component = readComponent(overrideCandidate, false);
            if (component.isPresent() && componentPathMatches(component.get().path(), repositoryRelativePath)) {
                return component;
            }
            if (component.isPresent()) {
                System.out.println("[SONAR-SMELL-WARNING] ignoring environment component override because path does not match"
                        + " | expected=" + repositoryRelativePath
                        + " | overrideKey=" + overrideCandidate
                        + " | overridePath=" + component.get().path());
            }
        }

        for (String directCandidate : directComponentKeyCandidates(repositoryRelativePath)) {
            Optional<ComponentCandidate> component = readComponent(directCandidate, false);
            if (component.isPresent() && componentPathMatches(component.get().path(), repositoryRelativePath)) {
                return component;
            }
        }

        return searchExactFileComponent(repositoryRelativePath);
    }

    private List<String> environmentOverrideCandidates(String repositoryRelativePath) {
        String simpleName = Path.of(repositoryRelativePath).getFileName().toString().replace(".java", "");
        String safeSimpleName = simpleName.toUpperCase().replaceAll("[^A-Z0-9]", "_");

        List<String> keys = List.of(
                safeSimpleName + "_COMPONENT",
                "SONAR_COMPONENT_" + safeSimpleName
        );

        List<String> candidates = new ArrayList<>();
        for (String key : keys) {
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                candidates.add(value.trim());
            }
        }
        return candidates;
    }

    private List<String> directComponentKeyCandidates(String repositoryRelativePath) {
        Set<String> candidates = new LinkedHashSet<>();
        addProjectCandidate(candidates, repositoryRelativePath);

        int sourceRootIndex = repositoryRelativePath.indexOf("src/main/java/");
        if (sourceRootIndex >= 0) {
            addProjectCandidate(candidates, repositoryRelativePath.substring(sourceRootIndex));
        }

        int langJavaIndex = repositoryRelativePath.indexOf("lang/java/");
        if (langJavaIndex >= 0) {
            addProjectCandidate(candidates, repositoryRelativePath.substring(langJavaIndex));
        }

        return List.copyOf(candidates);
    }

    private void addProjectCandidate(Set<String> candidates, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        candidates.add(configuration.projectKey() + ":" + normalizePath(relativePath));
    }

    private Optional<ComponentCandidate> searchExactFileComponent(String repositoryRelativePath) {
        String fileName = Path.of(repositoryRelativePath).getFileName().toString();
        String url = configuration.baseUrl()
                + "/api/components/tree?component=" + encode(configuration.projectKey())
                + "&qualifiers=FIL"
                + "&q=" + encode(fileName)
                + "&ps=" + PAGE_SIZE
                + branchParameter();

        Optional<JsonNode> response = getJson(url);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        JsonNode components = response.get().path("components");
        if (!components.isArray()) {
            return Optional.empty();
        }

        List<ComponentCandidate> matchingCandidates = readExactPathCandidates(components, fileName, repositoryRelativePath);
        return selectExactCandidate(matchingCandidates, repositoryRelativePath);
    }

    private List<ComponentCandidate> readExactPathCandidates(
            JsonNode components,
            String fileName,
            String repositoryRelativePath
    ) {
        List<ComponentCandidate> candidates = new ArrayList<>();
        for (JsonNode component : components) {
            String key = component.path("key").asText("");
            String path = normalizePath(component.path("path").asText(""));
            String componentName = component.path("name").asText("");

            boolean sameName = fileName.equals(Path.of(path).getFileName().toString()) || fileName.equals(componentName);
            boolean samePath = componentPathMatches(path, repositoryRelativePath);
            if (!key.isBlank() && !path.isBlank() && sameName && samePath) {
                candidates.add(new ComponentCandidate(key, path));
            }
        }
        return candidates;
    }

    private Optional<ComponentCandidate> selectExactCandidate(
            List<ComponentCandidate> candidates,
            String repositoryRelativePath
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        System.out.println("[SONAR-SMELL-WARNING] multiple exact components found for "
                + repositoryRelativePath + "; unable to choose safely");
        for (ComponentCandidate candidate : candidates) {
            System.out.println("  candidate=" + candidate.key() + " | path=" + candidate.path());
        }
        return Optional.empty();
    }

    private boolean componentPathMatches(String componentPath, String repositoryRelativePath) {
        String normalizedComponentPath = normalizePath(componentPath);
        for (String acceptedPath : acceptedPathSuffixes(repositoryRelativePath)) {
            if (normalizedComponentPath.equals(acceptedPath) || normalizedComponentPath.endsWith("/" + acceptedPath)) {
                return true;
            }
        }
        return false;
    }

    private List<String> acceptedPathSuffixes(String repositoryRelativePath) {
        Set<String> suffixes = new LinkedHashSet<>();
        suffixes.add(repositoryRelativePath);

        int sourceRootIndex = repositoryRelativePath.indexOf("src/main/java/");
        if (sourceRootIndex >= 0) {
            suffixes.add(repositoryRelativePath.substring(sourceRootIndex));
        }

        int langJavaIndex = repositoryRelativePath.indexOf("lang/java/");
        if (langJavaIndex >= 0) {
            suffixes.add(repositoryRelativePath.substring(langJavaIndex));
        }

        return List.copyOf(suffixes);
    }

    private Optional<ComponentCandidate> readComponent(String componentKey, boolean logWarnings) {
        String url = configuration.baseUrl()
                + "/api/components/show?component=" + encode(componentKey)
                + branchParameter();

        Optional<JsonNode> response = getJson(url, logWarnings);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        JsonNode component = response.get().path("component");
        String key = component.path("key").asText("");
        String path = normalizePath(component.path("path").asText(""));
        if (key.isBlank() || path.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ComponentCandidate(key, path));
    }

    private Optional<Integer> readIssueSearchCount(String componentKey) {
        String url = configuration.baseUrl()
                + "/api/issues/search?componentKeys=" + encode(componentKey)
                + "&types=CODE_SMELL"
                + "&resolved=false"
                + "&ps=1"
                + branchParameter();

        Optional<JsonNode> response = getJson(url);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        JsonNode pagingTotal = response.get().path("paging").path("total");
        if (pagingTotal.isNumber()) {
            return Optional.of(pagingTotal.asInt());
        }

        JsonNode total = response.get().path("total");
        return total.isNumber() ? Optional.of(total.asInt()) : Optional.empty();
    }

    private Optional<Integer> readCodeSmellMeasure(String componentKey) {
        String url = configuration.baseUrl()
                + "/api/measures/component?component=" + encode(componentKey)
                + "&metricKeys=code_smells"
                + branchParameter();

        Optional<JsonNode> response = getJson(url);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        JsonNode measures = response.get().path("component").path("measures");
        if (!measures.isArray()) {
            return Optional.empty();
        }

        for (JsonNode measure : measures) {
            if ("code_smells".equals(measure.path("metric").asText())) {
                String value = measure.path("value").asText("0");
                return Optional.of(parseInteger(value));
            }
        }
        return Optional.empty();
    }

    private void logResolvedSmellCount(
            String repositoryRelativePath,
            ComponentCandidate component,
            int issueSearchCodeSmells,
            Optional<Integer> measureValue
    ) {
        String measureText = measureValue.map(String::valueOf).orElse("unavailable");
        if (measureValue.isPresent() && measureValue.get() != issueSearchCodeSmells) {
            System.out.println("[SONAR-SMELL-WARNING] issues-search/measure mismatch"
                    + " | source=" + repositoryRelativePath
                    + " | componentKey=" + component.key()
                    + " | componentPath=" + component.path()
                    + " | issueSearchCodeSmells=" + issueSearchCodeSmells
                    + " | measureCodeSmells=" + measureText
                    + " | using=issueSearchCodeSmells");
            return;
        }

        System.out.println("[SONAR-SMELL] source=" + repositoryRelativePath
                + " | componentKey=" + component.key()
                + " | componentPath=" + component.path()
                + " | code_smells=" + issueSearchCodeSmells
                + " | issueSearchCodeSmells=" + issueSearchCodeSmells
                + " | measureCodeSmells=" + measureText);
    }

    private Optional<JsonNode> getJson(String url) {
        return getJson(url, true);
    }

    private Optional<JsonNode> getJson(String url, boolean logWarnings) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET();

            if (!configuration.token().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + configuration.token());
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() != HTTP_OK) {
                if (logWarnings) {
                    System.out.println("[SONAR-API-WARNING] status=" + response.statusCode()
                            + " | url=" + sanitizeUrl(url)
                            + " | body=" + response.body());
                }
                return Optional.empty();
            }

            JsonNode jsonNode = objectMapper.readTree(response.body());
            if (hasApiErrors(jsonNode)) {
                if (logWarnings) {
                    System.out.println("[SONAR-API-WARNING] errors=" + jsonNode.path("errors")
                            + " | url=" + sanitizeUrl(url));
                }
                return Optional.empty();
            }
            return Optional.of(jsonNode);
        } catch (IOException exception) {
            if (logWarnings) {
                System.out.println("[SONAR-API-WARNING] url=" + sanitizeUrl(url) + " | reason=" + exception.getMessage());
            }
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (logWarnings) {
                System.out.println("[SONAR-API-WARNING] url=" + sanitizeUrl(url) + " | reason=interrupted");
            }
            return Optional.empty();
        } catch (IllegalArgumentException exception) {
            if (logWarnings) {
                System.out.println("[SONAR-API-WARNING] url=" + sanitizeUrl(url) + " | reason=" + exception.getMessage());
            }
            return Optional.empty();
        }
    }

    private boolean hasApiErrors(JsonNode jsonNode) {
        JsonNode errors = jsonNode.path("errors");
        return errors.isArray() && !errors.isEmpty();
    }

    private int unavailable(String message, String repositoryRelativePath) {
        String details = message + System.lineSeparator()
                + "SonarCloud source path: " + repositoryRelativePath + System.lineSeparator()
                + "Expected componentKey example: "
                + configuration.projectKey() + ":" + normalizePath(repositoryRelativePath) + System.lineSeparator()
                + "Set SONAR_TOKEN if the project is private or the API requires authentication.";

        if (configuration.failOnUnavailable()) {
            throw new IllegalStateException(details);
        }

        System.out.println("[SONAR-SMELL-WARNING] " + details.replace(System.lineSeparator(), " | "));
        return -1;
    }

    private String missingComponentMessage(String repositoryRelativePath) {
        return "SonarCloud component not found with exact path. The file is probably not part of the already completed "
                + "analysis, or the project key/base directory used by SonarCloud is different.";
    }

    private String branchParameter() {
        return configuration.branch().isBlank() ? "" : "&branch=" + encode(configuration.branch());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private int parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    private String sanitizeUrl(String url) {
        return url.replaceAll("token=[^&]+", "token=***");
    }

    private record ComponentCandidate(String key, String path) {
    }
}
