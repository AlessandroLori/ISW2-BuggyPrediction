package it.university.avro.metrics.history;

import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.git.TemporaryGitRepository;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHistoryMetricExtractor {

    private static final Pattern BUG_ID_PATTERN = Pattern.compile("AVRO-\\d+");

    public HistoryMetrics extract(
            final TemporaryGitRepository repository,
            final String tag,
            final String classPath,
            final Set<String> knownBugIds
    ) {
        final String gitLogOutput = repository.gitLogForPathAtTag(tag, classPath);

        if (gitLogOutput.isBlank()) {
            return HistoryMetrics.empty();
        }

        final String[] lines = gitLogOutput.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        int revs = 0;
        int locTouched = 0;
        int locAdded = 0;
        int maxLocAdded = 0;
        int churn = 0;
        int maxChurn = 0;

        final Set<String> authors = new LinkedHashSet<>();
        final Set<String> fixedBugIds = new LinkedHashSet<>();

        String currentAuthor = null;
        String currentSubject = null;
        int currentAdded = 0;
        int currentDeleted = 0;
        boolean insideCommit = false;

        for (String line : lines) {
            if (line.startsWith("@@COMMIT@@")) {
                if (insideCommit) {
                    revs += 1;

                    authors.add(currentAuthor);

                    locTouched += currentAdded + currentDeleted;
                    locAdded += currentAdded;
                    maxLocAdded = Math.max(maxLocAdded, currentAdded);

                    final int commitChurn = Math.abs(currentAdded - currentDeleted);
                    churn += commitChurn;
                    maxChurn = Math.max(maxChurn, commitChurn);

                    collectBugIds(currentSubject, knownBugIds, fixedBugIds);
                }

                insideCommit = true;
                currentAdded = 0;
                currentDeleted = 0;

                final String metadata = line.substring("@@COMMIT@@".length());
                final String[] parts = metadata.split("\u001f", -1);

                currentAuthor = parts.length > 1 ? parts[1].trim() : "";
                currentSubject = parts.length > 2 ? parts[2].trim() : "";
                continue;
            }

            if (!insideCommit) {
                continue;
            }

            final String[] numstat = line.split("\t");
            if (numstat.length < 3) {
                continue;
            }

            final int added = parseNumstatValue(numstat[0]);
            final int deleted = parseNumstatValue(numstat[1]);

            currentAdded += added;
            currentDeleted += deleted;
        }

        if (insideCommit) {
            revs += 1;

            authors.add(currentAuthor);

            locTouched += currentAdded + currentDeleted;
            locAdded += currentAdded;
            maxLocAdded = Math.max(maxLocAdded, currentAdded);

            final int commitChurn = Math.abs(currentAdded - currentDeleted);
            churn += commitChurn;
            maxChurn = Math.max(maxChurn, commitChurn);

            collectBugIds(currentSubject, knownBugIds, fixedBugIds);
        }

        final double avgLocAdded = revs == 0 ? 0.0 : (double) locAdded / revs;
        final double avgChurn = revs == 0 ? 0.0 : (double) churn / revs;

        return new HistoryMetrics(
                revs,
                fixedBugIds.size(),
                authors.size(),
                locTouched,
                locAdded,
                maxLocAdded,
                avgLocAdded,
                churn,
                maxChurn,
                avgChurn
        );
    }

    private void collectBugIds(
            final String commitSubject,
            final Set<String> knownBugIds,
            final Set<String> collector
    ) {
        if (commitSubject == null || commitSubject.isBlank()) {
            return;
        }

        final Matcher matcher = BUG_ID_PATTERN.matcher(commitSubject.toUpperCase());
        while (matcher.find()) {
            final String bugId = matcher.group();
            if (knownBugIds.contains(bugId)) {
                collector.add(bugId);
            }
        }
    }

    private int parseNumstatValue(final String rawValue) {
        if (rawValue == null || rawValue.isBlank() || rawValue.equals("-")) {
            return 0;
        }

        return Integer.parseInt(rawValue.trim());
    }
}