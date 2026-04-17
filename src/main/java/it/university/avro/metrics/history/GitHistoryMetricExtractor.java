package it.university.avro.metrics.history;

import it.university.avro.metrics.domain.BugTicket;
import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.git.TemporaryGitRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHistoryMetricExtractor {

    private static final Pattern BUG_ID_PATTERN = Pattern.compile("AVRO-\\d+");
    private static final int EPSILON_DAYS = 1;

    public HistoryExtractionResult extract(
            final TemporaryGitRepository repository,
            final String previousTagExclusive,
            final String currentTagInclusive,
            final String classPath,
            final Map<String, BugTicket> knownTickets
    ) {
        final String releaseWindowLog = repository.gitLogForPathInReleaseWindow(
                previousTagExclusive,
                currentTagInclusive,
                classPath
        );

        final String cumulativeLog = repository.gitLogForPathUntilTag(
                currentTagInclusive,
                classPath
        );

        final List<CommitTouch> releaseWindowTouches = parseCommitTouches(releaseWindowLog);
        final List<CommitTouch> cumulativeTouches = parseCommitTouches(cumulativeLog);

        final int revs = releaseWindowTouches.size();

        final Set<String> authors = new LinkedHashSet<>();
        int locTouched = 0;
        int locAdded = 0;
        int maxLocAdded = 0;
        int churn = 0;
        int maxChurn = 0;

        for (CommitTouch commitTouch : releaseWindowTouches) {
            authors.add(commitTouch.author());

            locTouched += commitTouch.addedLines() + commitTouch.deletedLines();
            locAdded += commitTouch.addedLines();
            maxLocAdded = Math.max(maxLocAdded, commitTouch.addedLines());

            final int commitChurn = Math.abs(commitTouch.addedLines() - commitTouch.deletedLines());
            churn += commitChurn;
            maxChurn = Math.max(maxChurn, commitChurn);
        }

        final double avgLocAdded = revs == 0 ? 0.0 : (double) locAdded / revs;
        final double avgChurn = revs == 0 ? 0.0 : (double) churn / revs;

        final Set<String> cumulativeFixes = new LinkedHashSet<>();
        for (CommitTouch commitTouch : cumulativeTouches) {
            collectValidBugIds(commitTouch, knownTickets, cumulativeFixes);
        }

        final HistoryMetrics metrics = new HistoryMetrics(
                revs,
                cumulativeFixes.size(),
                authors.size(),
                locTouched,
                locAdded,
                maxLocAdded,
                avgLocAdded,
                churn,
                maxChurn,
                avgChurn
        );

        return new HistoryExtractionResult(
                metrics,
                !releaseWindowTouches.isEmpty(),
                !cumulativeTouches.isEmpty()
        );
    }

    private List<CommitTouch> parseCommitTouches(final String gitLogOutput) {
        if (gitLogOutput == null || gitLogOutput.isBlank()) {
            return List.of();
        }

        final String[] lines = gitLogOutput.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        final List<CommitTouch> touches = new ArrayList<>();

        String currentAuthor = null;
        LocalDate currentCommitDate = null;
        String currentSubject = null;
        int currentAdded = 0;
        int currentDeleted = 0;
        boolean insideCommit = false;

        for (String line : lines) {
            if (line.startsWith("@@COMMIT@@")) {
                if (insideCommit) {
                    touches.add(new CommitTouch(
                            currentAuthor,
                            currentCommitDate,
                            currentSubject,
                            currentAdded,
                            currentDeleted
                    ));
                }

                insideCommit = true;
                currentAdded = 0;
                currentDeleted = 0;

                final String metadata = line.substring("@@COMMIT@@".length());
                final String[] parts = metadata.split("\u001f", -1);

                currentAuthor = parts.length > 1 ? parts[1].trim() : "";
                currentCommitDate = parts.length > 2 ? parseCommitDate(parts[2].trim()) : null;
                currentSubject = parts.length > 3 ? parts[3].trim() : "";
                continue;
            }

            if (!insideCommit) {
                continue;
            }

            final String[] numstat = line.split("\t");
            if (numstat.length < 3) {
                continue;
            }

            currentAdded += parseNumstatValue(numstat[0]);
            currentDeleted += parseNumstatValue(numstat[1]);
        }

        if (insideCommit) {
            touches.add(new CommitTouch(
                    currentAuthor,
                    currentCommitDate,
                    currentSubject,
                    currentAdded,
                    currentDeleted
            ));
        }

        return List.copyOf(touches);
    }

    private void collectValidBugIds(
            final CommitTouch commitTouch,
            final Map<String, BugTicket> knownTickets,
            final Set<String> collector
    ) {
        if (commitTouch.subject() == null || commitTouch.subject().isBlank() || commitTouch.commitDate() == null) {
            return;
        }

        final Matcher matcher = BUG_ID_PATTERN.matcher(commitTouch.subject().toUpperCase());
        while (matcher.find()) {
            final String bugId = matcher.group();
            final BugTicket ticket = knownTickets.get(bugId);

            if (ticket == null) {
                continue;
            }

            if (isCommitDateConsistent(commitTouch.commitDate(), ticket)) {
                collector.add(bugId);
            }
        }
    }

    private boolean isCommitDateConsistent(final LocalDate commitDate, final BugTicket ticket) {
        final LocalDate lowerBound = ticket.creationDate().minusDays(EPSILON_DAYS);
        final LocalDate upperBound = ticket.closedDate().plusDays(EPSILON_DAYS);

        return !commitDate.isBefore(lowerBound) && !commitDate.isAfter(upperBound);
    }

    private LocalDate parseCommitDate(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        if (rawValue.length() >= 10) {
            return LocalDate.parse(rawValue.substring(0, 10));
        }

        return LocalDate.parse(rawValue);
    }

    private int parseNumstatValue(final String rawValue) {
        if (rawValue == null || rawValue.isBlank() || rawValue.equals("-")) {
            return 0;
        }

        return Integer.parseInt(rawValue.trim());
    }

    private record CommitTouch(
            String author,
            LocalDate commitDate,
            String subject,
            int addedLines,
            int deletedLines
    ) {
    }
}