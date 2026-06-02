package it.university.avro.metrics.history;

import it.university.avro.metrics.domain.BugTicket;
import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.git.TemporaryGitRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHistoryMetricExtractor {

    private static final Pattern BUG_ID_PATTERN = Pattern.compile("AVRO-\\d+");
    private static final int EPSILON_DAYS = 1;
    private static final String COMMIT_MARKER = "@@COMMIT@@";
    private static final String FIELD_SEPARATOR = "\u001f";

    private final Map<String, Integer> changeSetSizeCache = new HashMap<>();

    public HistoryExtractionResult extract(
            final TemporaryGitRepository repository,
            final String previousTagExclusive,
            final String currentTagInclusive,
            final LocalDate currentReleaseDate,
            final String classPath,
            final Map<String, BugTicket> knownTickets
    ) {
        final List<CommitTouch> releaseWindowTouches = parseCommitTouches(repository.gitLogForPathInReleaseWindow(
                previousTagExclusive,
                currentTagInclusive,
                classPath
        ));
        final List<CommitTouch> cumulativeTouches = parseCommitTouches(repository.gitLogForPathUntilTag(
                currentTagInclusive,
                classPath
        ));

        final ReleaseWindowStats releaseStats = summarizeReleaseWindow(releaseWindowTouches);
        final CumulativeStats cumulativeStats = summarizeCumulativeHistory(
                repository,
                cumulativeTouches,
                currentReleaseDate,
                knownTickets
        );

        return new HistoryExtractionResult(
                buildMetrics(releaseStats, cumulativeStats, currentReleaseDate),
                !releaseWindowTouches.isEmpty(),
                !cumulativeTouches.isEmpty()
        );
    }

    private ReleaseWindowStats summarizeReleaseWindow(final List<CommitTouch> releaseWindowTouches) {
        final Set<String> authors = new LinkedHashSet<>();
        int locTouched = 0;
        int locAdded = 0;
        int maxLocAdded = 0;
        int churn = 0;
        int maxChurn = 0;

        for (CommitTouch commitTouch : releaseWindowTouches) {
            authors.add(commitTouch.author());
            locTouched += commitTouch.totalTouchedLines();
            locAdded += commitTouch.addedLines();
            maxLocAdded = Math.max(maxLocAdded, commitTouch.addedLines());

            final int commitChurn = commitTouch.churn();
            churn += commitChurn;
            maxChurn = Math.max(maxChurn, commitChurn);
        }

        return new ReleaseWindowStats(
                releaseWindowTouches.size(),
                authors.size(),
                locTouched,
                locAdded,
                maxLocAdded,
                churn,
                maxChurn
        );
    }

    private CumulativeStats summarizeCumulativeHistory(
            final TemporaryGitRepository repository,
            final List<CommitTouch> cumulativeTouches,
            final LocalDate currentReleaseDate,
            final Map<String, BugTicket> knownTickets
    ) {
        final Set<String> cumulativeFixes = new LinkedHashSet<>();
        final AgeAccumulator ageAccumulator = new AgeAccumulator();
        int changeSetSize = 0;
        int maxChangeSet = 0;

        for (CommitTouch commitTouch : cumulativeTouches) {
            collectValidBugIds(commitTouch, knownTickets, cumulativeFixes);

            final int commitChangeSetSize = resolveChangeSetSize(repository, commitTouch.commitHash());
            changeSetSize += commitChangeSetSize;
            maxChangeSet = Math.max(maxChangeSet, commitChangeSetSize);
            ageAccumulator.include(commitTouch, currentReleaseDate);
        }

        return new CumulativeStats(
                cumulativeTouches.size(),
                cumulativeFixes.size(),
                changeSetSize,
                maxChangeSet,
                ageAccumulator.firstTouchDate(),
                ageAccumulator.weightedAgeNumerator(),
                ageAccumulator.weightedAgeDenominator()
        );
    }

    private HistoryMetrics buildMetrics(
            final ReleaseWindowStats releaseStats,
            final CumulativeStats cumulativeStats,
            final LocalDate currentReleaseDate
    ) {
        final double avgLocAdded = releaseStats.revs() == 0
                ? 0.0
                : (double) releaseStats.locAdded() / releaseStats.revs();
        final double avgChurn = releaseStats.revs() == 0
                ? 0.0
                : (double) releaseStats.churn() / releaseStats.revs();
        final double avgChangeSet = cumulativeStats.commitCount() == 0
                ? 0.0
                : (double) cumulativeStats.changeSetSize() / cumulativeStats.commitCount();
        final int age = age(cumulativeStats.firstTouchDate(), currentReleaseDate);
        final double weightedAge = cumulativeStats.weightedAgeDenominator() == 0
                ? (double) age
                : (double) cumulativeStats.weightedAgeNumerator() / cumulativeStats.weightedAgeDenominator();

        return new HistoryMetrics(
                releaseStats.revs(),
                cumulativeStats.fixCount(),
                releaseStats.authorCount(),
                releaseStats.locTouched(),
                releaseStats.locAdded(),
                releaseStats.maxLocAdded(),
                avgLocAdded,
                releaseStats.churn(),
                releaseStats.maxChurn(),
                avgChurn,
                cumulativeStats.changeSetSize(),
                cumulativeStats.maxChangeSet(),
                avgChangeSet,
                age,
                weightedAge
        );
    }

    private int age(final LocalDate firstTouchDate, final LocalDate currentReleaseDate) {
        if (firstTouchDate == null || currentReleaseDate == null) {
            return 0;
        }
        return (int) Math.max(0L, ChronoUnit.DAYS.between(firstTouchDate, currentReleaseDate));
    }

    private List<CommitTouch> parseCommitTouches(final String gitLogOutput) {
        if (gitLogOutput == null || gitLogOutput.isBlank()) {
            return List.of();
        }

        final CommitTouchBuilder builder = new CommitTouchBuilder();
        final String[] lines = gitLogOutput.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        for (String line : lines) {
            if (line.startsWith(COMMIT_MARKER)) {
                builder.startCommit(line.substring(COMMIT_MARKER.length()));
            } else {
                builder.includeNumstatLine(line);
            }
        }

        return builder.finish();
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

            if (ticket != null && isCommitDateConsistent(commitTouch.commitDate(), ticket)) {
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

    private int resolveChangeSetSize(final TemporaryGitRepository repository, final String commitHash) {
        if (commitHash == null || commitHash.isBlank()) {
            return 0;
        }

        return changeSetSizeCache.computeIfAbsent(
                commitHash,
                repository::countChangedFilesInCommit
        );
    }

    private record ReleaseWindowStats(
            int revs,
            int authorCount,
            int locTouched,
            int locAdded,
            int maxLocAdded,
            int churn,
            int maxChurn
    ) {
    }

    private record CumulativeStats(
            int commitCount,
            int fixCount,
            int changeSetSize,
            int maxChangeSet,
            LocalDate firstTouchDate,
            long weightedAgeNumerator,
            int weightedAgeDenominator
    ) {
    }

    private static final class AgeAccumulator {

        private LocalDate firstTouchDate;
        private long weightedAgeNumerator;
        private int weightedAgeDenominator;

        void include(final CommitTouch commitTouch, final LocalDate referenceDate) {
            if (commitTouch.commitDate() == null) {
                return;
            }
            updateFirstTouchDate(commitTouch.commitDate());
            includeWeightedAge(commitTouch, referenceDate);
        }

        private void updateFirstTouchDate(final LocalDate commitDate) {
            if (firstTouchDate == null || commitDate.isBefore(firstTouchDate)) {
                firstTouchDate = commitDate;
            }
        }

        private void includeWeightedAge(final CommitTouch commitTouch, final LocalDate referenceDate) {
            final int touchedLines = commitTouch.totalTouchedLines();
            if (referenceDate != null && touchedLines > 0) {
                final long ageAtRelease = Math.max(0L, ChronoUnit.DAYS.between(commitTouch.commitDate(), referenceDate));
                weightedAgeNumerator += ageAtRelease * touchedLines;
                weightedAgeDenominator += touchedLines;
            }
        }

        LocalDate firstTouchDate() {
            return firstTouchDate;
        }

        long weightedAgeNumerator() {
            return weightedAgeNumerator;
        }

        int weightedAgeDenominator() {
            return weightedAgeDenominator;
        }
    }

    private final class CommitTouchBuilder {

        private final List<CommitTouch> touches = new ArrayList<>();
        private String currentCommitHash;
        private String currentAuthor;
        private LocalDate currentCommitDate;
        private String currentSubject;
        private int currentAdded;
        private int currentDeleted;
        private boolean insideCommit;

        void startCommit(final String metadata) {
            addCurrentCommitIfPresent();
            insideCommit = true;
            currentAdded = 0;
            currentDeleted = 0;

            final String[] parts = metadata.split(FIELD_SEPARATOR, -1);
            currentCommitHash = parts.length > 0 ? parts[0].trim() : "";
            currentAuthor = parts.length > 1 ? parts[1].trim() : "";
            currentCommitDate = parts.length > 2 ? parseCommitDate(parts[2].trim()) : null;
            currentSubject = parts.length > 3 ? parts[3].trim() : "";
        }

        void includeNumstatLine(final String line) {
            if (insideCommit) {
                final String[] numstat = line.split("\t");
                if (numstat.length >= 3) {
                    currentAdded += parseNumstatValue(numstat[0]);
                    currentDeleted += parseNumstatValue(numstat[1]);
                }
            }
        }

        List<CommitTouch> finish() {
            addCurrentCommitIfPresent();
            return List.copyOf(touches);
        }

        private void addCurrentCommitIfPresent() {
            if (insideCommit) {
                touches.add(new CommitTouch(
                        currentCommitHash,
                        currentAuthor,
                        currentCommitDate,
                        currentSubject,
                        currentAdded,
                        currentDeleted
                ));
            }
        }
    }

    private record CommitTouch(
            String commitHash,
            String author,
            LocalDate commitDate,
            String subject,
            int addedLines,
            int deletedLines
    ) {

        int totalTouchedLines() {
            return addedLines + deletedLines;
        }

        int churn() {
            return Math.abs(addedLines - deletedLines);
        }
    }
}
