package it.university.avro.exporter.client;

import it.university.avro.exporter.api.JiraIssueApiModel;
import it.university.avro.exporter.api.JiraVersionApiModel;

import java.util.List;

public interface JiraClient {

    List<JiraVersionApiModel> fetchProjectVersions();

    List<JiraIssueApiModel> fetchBugIssues();
}
