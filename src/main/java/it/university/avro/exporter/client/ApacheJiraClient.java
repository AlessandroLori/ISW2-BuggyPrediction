package it.university.avro.exporter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.university.avro.exporter.api.JiraIssueApiModel;
import it.university.avro.exporter.api.JiraSearchResponse;
import it.university.avro.exporter.api.JiraVersionApiModel;
import it.university.avro.exporter.config.ExporterConfiguration;
import it.university.avro.exporter.exception.JiraClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class ApacheJiraClient implements JiraClient {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final String ACCEPT_HEADER = "application/json";
    private static final String CONTENT_TYPE_HEADER = "application/json";
    private static final String USER_AGENT = "AvroTicketDetailsExporter/1.0";

    private final ExporterConfiguration configuration;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ApacheJiraClient(final ExporterConfiguration configuration) {
        this(configuration, HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build(), new ObjectMapper());
    }

    ApacheJiraClient(
            final ExporterConfiguration configuration,
            final HttpClient httpClient,
            final ObjectMapper objectMapper
    ) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<JiraVersionApiModel> fetchProjectVersions() {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(configuration.baseUrl() + "/rest/api/2/project/" + configuration.projectKey() + "/versions"))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", ACCEPT_HEADER)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        final String responseBody = sendRequest(request);
        try {
            final JiraVersionApiModel[] versions = objectMapper.readValue(responseBody, JiraVersionApiModel[].class);
            return Arrays.asList(versions);
        } catch (final IOException exception) {
            throw new JiraClientException("Unable to parse project versions response", exception);
        }
    }

    @Override
    public List<JiraIssueApiModel> fetchBugIssues() {
        final List<JiraIssueApiModel> issues = new ArrayList<>();
        int startAt = 0;
        boolean hasMorePages = true;

        while (hasMorePages) {
            final String requestBody = buildSearchRequestBody(startAt);
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configuration.baseUrl() + "/rest/api/2/search"))
                    .timeout(HTTP_TIMEOUT)
                    .header("Accept", ACCEPT_HEADER)
                    .header("Content-Type", CONTENT_TYPE_HEADER)
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            final JiraSearchResponse response = parseSearchResponse(sendRequest(request));
            final List<JiraIssueApiModel> pageIssues = response.issues() == null ? List.of() : response.issues();
            issues.addAll(pageIssues);

            startAt += pageIssues.size();
            hasMorePages = startAt < response.total() && !pageIssues.isEmpty();
        }

        return issues;
    }

    private JiraSearchResponse parseSearchResponse(final String responseBody) {
        try {
            return objectMapper.readValue(responseBody, JiraSearchResponse.class);
        } catch (final IOException exception) {
            throw new JiraClientException("Unable to parse Jira search response", exception);
        }
    }

    private String buildSearchRequestBody(final int startAt) {
        final String jql = "project = " + configuration.projectKey()
                + " AND issuetype = Bug"
                + " AND status in (Resolved, Closed)"
                + " ORDER BY key";

        final ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("jql", jql);
        requestBody.put("startAt", startAt);
        requestBody.put("maxResults", configuration.searchPageSize());
        requestBody.putArray("fields")
                .add("created")
                .add("resolutiondate")
                .add("versions")
                .add("fixVersions");

        return requestBody.toString();
    }

    private String sendRequest(final HttpRequest request) {
        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response.statusCode(), response.body(), request.uri().toString());
            return response.body();
        } catch (final IOException exception) {
            throw new JiraClientException("HTTP communication error while calling Jira", exception);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("HTTP request interrupted while calling Jira", exception);
        }
    }

    private void ensureSuccess(final int statusCode, final String body, final String url) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new JiraClientException(
                    "Jira call failed with HTTP " + statusCode + " for URL " + url + ". Body: " + body
            );
        }
    }
}
