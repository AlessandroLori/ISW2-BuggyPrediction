package it.university.avro.refactoringdataset.git;

import it.university.avro.common.ApplicationLog;
import it.university.avro.metrics.domain.HistoryMetrics;
import it.university.avro.metrics.git.GitCommandResult;

import java.nio.file.Path;
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

public final class LocalGitHistoryMetricExtractor {

    private static final Pattern BUG_ID_PATTERN = Pattern.compile("AVRO-\\d+");
    private static final String COMMIT_MARKER = "@@COMMIT@@";
    private static final String END_MESSAGE_MARKER = "@@END_MESSAGE@@";
    private static final String FIELD_SEPARATOR = "\u001f";

    private final LocalGitCommandExecutor gitCommandExecutor;
    private final Map<String, Integer> changeSetSizeCache;

    public LocalGitHistoryMetricExtractor() {
        this.gitCommandExecutor = new LocalGitCommandExecutor();
        this.changeSetSizeCache = new HashMap<>();
    }

    public HistoryMetrics extract(final Path gitRoot, final String repositoryRelativePath) {
        final List<CommitTouch> touches = parseCommitTouches(gitLogUntilHead(gitRoot, repositoryRelativePath));

        if (touches.isEmpty()) {
            return HistoryMetrics.empty();
        }

        final LocalDate referenceDate = resolveHeadDate(gitRoot);
        final HistoryAccumulator accumulator = new HistoryAccumulator(referenceDate);

        for (CommitTouch touch : touches) {
            accumulator.include(touch, resolveChangeSetSize(gitRoot, touch.commitHash()));
        }

        return accumulator.toMetrics();
    }

    private String gitLogUntilHead(final Path gitRoot, final String repositoryRelativePath) {
        final GitCommandResult result = gitCommandExecutor.execute(
                gitRoot,
                List.of(
                        "git",
                        "log",
                        "--follow",
                        "--date=iso-strict",
                        "--format=" + COMMIT_MARKER + "%H" + FIELD_SEPARATOR + "%an"
                                + FIELD_SEPARATOR + "%cI" + FIELD_SEPARATOR + "%B" + END_MESSAGE_MARKER,
                        "--numstat",
                        "HEAD",
                        "--",
                        repositoryRelativePath
                )
        );

        if (!result.isSuccess()) {
            ApplicationLog.info("[GIT-HISTORY-WARNING] path=" + repositoryRelativePath + " | reason=" + result.output());
            return "";
        }

        return result.output();
    }

    private LocalDate resolveHeadDate(final Path gitRoot) {
        final GitCommandResult result = gitCommandExecutor.execute(
                gitRoot,
                List.of("git", "log", "-1", "--date=iso-strict", "--format=%cI", "HEAD")
        );

        if (!result.isSuccess() || result.output().isBlank()) {
            return LocalDate.now();
        }

        final String rawValue = result.output().trim();
        return LocalDate.parse(rawValue.substring(0, Math.min(10, rawValue.length())));
    }

    private int resolveChangeSetSize(final Path gitRoot, final String commitHash) {
        if (commitHash == null || commitHash.isBlank()) {
            return 0;
        }

        return changeSetSizeCache.computeIfAbsent(commitHash, key -> countChangedFiles(gitRoot, key));
    }

    private int countChangedFiles(final Path gitRoot, final String commitHash) {
        final GitCommandResult result = gitCommandExecutor.execute(
                gitRoot,
                List.of(
                        "git",
                        "show",
                        "--format=",
                        "--name-only",
                        "--diff-filter=ACMRTUXB",
                        commitHash
                )
        );

        if (!result.isSuccess() || result.output().isBlank()) {
            return 0;
        }

        return (int) result.output()
                .lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .distinct()
                .count();
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
                builder.includeLine(line);
            }
        }

        return builder.finish();
    }

    private static final class HistoryAccumulator {

        private final LocalDate referenceDate;
        private final Set<String> authors = new LinkedHashSet<>();
        private final Set<String> fixes = new LinkedHashSet<>();
        private int revs;
        private int locTouched;
        private int locAdded;
        private int maxLocAdded;
        private int churn;
        private int maxChurn;
        private int changeSetSize;
        private int maxChangeSet;
        private long weightedAgeNumerator;
        private int weightedAgeDenominator;
        private LocalDate firstTouchDate;

        private HistoryAccumulator(final LocalDate referenceDate) {
            this.referenceDate = referenceDate;
        }

        void include(final CommitTouch touch, final int currentChangeSetSize) {
            revs++;
            includeAuthor(touch.author());
            collectBugIds(touch.fullMessage(), fixes);
            includeLineMetrics(touch);
            includeChangeSetMetrics(currentChangeSetSize);
            includeAgeMetrics(touch);
        }

        private void includeAuthor(final String author) {
            if (!author.isBlank()) {
                authors.add(author);
            }
        }

        private static void collectBugIds(final String message, final Set<String> collector) {
            if (message == null || message.isBlank()) {
                return;
            }

            final Matcher matcher = BUG_ID_PATTERN.matcher(message.toUpperCase());
            while (matcher.find()) {
                collector.add(matcher.group());
            }
        }

        private void includeLineMetrics(final CommitTouch touch) {
            locTouched += touch.totalTouchedLines();
            locAdded += touch.addedLines();
            maxLocAdded = Math.max(maxLocAdded, touch.addedLines());

            final int commitChurn = touch.churn();
            churn += commitChurn;
            maxChurn = Math.max(maxChurn, commitChurn);
        }

        private void includeChangeSetMetrics(final int currentChangeSetSize) {
            changeSetSize += currentChangeSetSize;
            maxChangeSet = Math.max(maxChangeSet, currentChangeSetSize);
        }

        private void includeAgeMetrics(final CommitTouch touch) {
            if (touch.commitDate() == null) {
                return;
            }
            if (firstTouchDate == null || touch.commitDate().isBefore(firstTouchDate)) {
                firstTouchDate = touch.commitDate();
            }
            if (touch.totalTouchedLines() > 0) {
                final long ageAtReference = Math.max(0L, ChronoUnit.DAYS.between(touch.commitDate(), referenceDate));
                weightedAgeNumerator += ageAtReference * touch.totalTouchedLines();
                weightedAgeDenominator += touch.totalTouchedLines();
            }
        }

        HistoryMetrics toMetrics() {
            final double avgLocAdded = revs == 0 ? 0.0 : (double) locAdded / revs;
            final double avgChurn = revs == 0 ? 0.0 : (double) churn / revs;
            final double avgChangeSet = revs == 0 ? 0.0 : (double) changeSetSize / revs;
            final int age = firstTouchDate == null
                    ? 0
                    : (int) Math.max(0L, ChronoUnit.DAYS.between(firstTouchDate, referenceDate));
            final double weightedAge = weightedAgeDenominator == 0
                    ? (double) age
                    : (double) weightedAgeNumerator / weightedAgeDenominator;

            return new HistoryMetrics(
                    revs,
                    fixes.size(),
                    authors.size(),
                    locTouched,
                    locAdded,
                    maxLocAdded,
                    avgLocAdded,
                    churn,
                    maxChurn,
                    avgChurn,
                    changeSetSize,
                    maxChangeSet,
                    avgChangeSet,
                    age,
                    weightedAge
            );
        }
    }

    private static final class CommitTouchBuilder {

        private final List<CommitTouch> touches = new ArrayList<>();
        private String currentCommitHash = "";
        private String currentAuthor = "";
        private LocalDate currentCommitDate;
        private StringBuilder currentMessage = new StringBuilder();
        private int currentAdded;
        private int currentDeleted;
        private boolean insideCommit;
        private boolean insideMessage;

        void startCommit(final String metadata) {
            addCurrentCommitIfPresent();
            insideCommit = true;
            insideMessage = true;
            currentAdded = 0;
            currentDeleted = 0;
            currentMessage = new StringBuilder();

            final String[] parts = metadata.split(FIELD_SEPARATOR, -1);
            currentCommitHash = parts.length > 0 ? parts[0].trim() : "";
            currentAuthor = parts.length > 1 ? parts[1].trim() : "";
            currentCommitDate = parts.length > 2 ? parseCommitDate(parts[2].trim()) : null;
            includeFirstMessageLine(parts.length > 3 ? parts[3] : "");
        }

        private void includeFirstMessageLine(final String rawMessageLine) {
            if (!rawMessageLine.isEmpty()) {
                appendMessageLine(rawMessageLine.replace(END_MESSAGE_MARKER, "").trim());
                insideMessage = !rawMessageLine.contains(END_MESSAGE_MARKER);
            }
        }

        void includeLine(final String line) {
            if (!insideCommit) {
                return;
            }
            if (insideMessage) {
                includeMessageLine(line);
            } else {
                includeNumstatLine(line);
            }
        }

        private void includeMessageLine(final String line) {
            if (line.contains(END_MESSAGE_MARKER)) {
                appendMessageLine(line.replace(END_MESSAGE_MARKER, "").trim());
                insideMessage = false;
                return;
            }
            currentMessage.append(line).append('\n');
        }

        private void appendMessageLine(final String messageLine) {
            if (!messageLine.isBlank()) {
                currentMessage.append(messageLine).append('\n');
            }
        }

        private void includeNumstatLine(final String line) {
            final String[] numstat = line.split("\t");
            if (numstat.length >= 3) {
                currentAdded += parseNumstatValue(numstat[0]);
                currentDeleted += parseNumstatValue(numstat[1]);
            }
        }

        private static LocalDate parseCommitDate(final String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return null;
            }
            return LocalDate.parse(rawValue.substring(0, Math.min(10, rawValue.length())));
        }

        private static int parseNumstatValue(final String rawValue) {
            if (rawValue == null || rawValue.isBlank() || rawValue.equals("-")) {
                return 0;
            }
            return Integer.parseInt(rawValue.trim());
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
                        currentMessage.toString().trim(),
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
            String fullMessage,
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
