package it.university.avro.refactoringdataset.git;

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

    private final LocalGitCommandExecutor gitCommandExecutor;
    private final Map<String, Integer> changeSetSizeCache;

    public LocalGitHistoryMetricExtractor() {
        this.gitCommandExecutor = new LocalGitCommandExecutor();
        this.changeSetSizeCache = new HashMap<>();
    }

    public HistoryMetrics extract(Path gitRoot, String repositoryRelativePath) {
        String log = gitLogUntilHead(gitRoot, repositoryRelativePath);
        List<CommitTouch> touches = parseCommitTouches(log);

        if (touches.isEmpty()) {
            return HistoryMetrics.empty();
        }

        Set<String> authors = new LinkedHashSet<>();
        Set<String> fixes = new LinkedHashSet<>();

        int locTouched = 0;
        int locAdded = 0;
        int maxLocAdded = 0;
        int churn = 0;
        int maxChurn = 0;
        int changeSetSize = 0;
        int maxChangeSet = 0;
        long weightedAgeNumerator = 0L;
        int weightedAgeDenominator = 0;

        LocalDate firstTouchDate = null;
        LocalDate referenceDate = resolveHeadDate(gitRoot);

        for (CommitTouch touch : touches) {
            if (!touch.author().isBlank()) {
                authors.add(touch.author());
            }

            collectBugIds(touch.fullMessage(), fixes);

            int touchedLines = touch.addedLines() + touch.deletedLines();
            locTouched += touchedLines;
            locAdded += touch.addedLines();
            maxLocAdded = Math.max(maxLocAdded, touch.addedLines());

            int commitChurn = Math.abs(touch.addedLines() - touch.deletedLines());
            churn += commitChurn;
            maxChurn = Math.max(maxChurn, commitChurn);

            int currentChangeSetSize = resolveChangeSetSize(gitRoot, touch.commitHash());
            changeSetSize += currentChangeSetSize;
            maxChangeSet = Math.max(maxChangeSet, currentChangeSetSize);

            if (touch.commitDate() != null) {
                if (firstTouchDate == null || touch.commitDate().isBefore(firstTouchDate)) {
                    firstTouchDate = touch.commitDate();
                }

                if (touchedLines > 0) {
                    long ageAtReference = Math.max(0L, ChronoUnit.DAYS.between(touch.commitDate(), referenceDate));
                    weightedAgeNumerator += ageAtReference * touchedLines;
                    weightedAgeDenominator += touchedLines;
                }
            }
        }

        int revs = touches.size();
        double avgLocAdded = revs == 0 ? 0.0 : (double) locAdded / revs;
        double avgChurn = revs == 0 ? 0.0 : (double) churn / revs;
        double avgChangeSet = revs == 0 ? 0.0 : (double) changeSetSize / revs;
        int age = firstTouchDate == null ? 0 : (int) Math.max(0L, ChronoUnit.DAYS.between(firstTouchDate, referenceDate));
        double weightedAge = weightedAgeDenominator == 0 ? (double) age : (double) weightedAgeNumerator / weightedAgeDenominator;

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

    private String gitLogUntilHead(Path gitRoot, String repositoryRelativePath) {
        GitCommandResult result = gitCommandExecutor.execute(
                gitRoot,
                List.of(
                        "git",
                        "log",
                        "--follow",
                        "--date=iso-strict",
                        "--format=@@COMMIT@@%H\u001f%an\u001f%cI\u001f%B@@END_MESSAGE@@",
                        "--numstat",
                        "HEAD",
                        "--",
                        repositoryRelativePath
                )
        );

        if (!result.isSuccess()) {
            System.out.println("[GIT-HISTORY-WARNING] path=" + repositoryRelativePath + " | reason=" + result.output());
            return "";
        }

        return result.output();
    }

    private LocalDate resolveHeadDate(Path gitRoot) {
        GitCommandResult result = gitCommandExecutor.execute(
                gitRoot,
                List.of("git", "log", "-1", "--date=iso-strict", "--format=%cI", "HEAD")
        );

        if (!result.isSuccess() || result.output().isBlank()) {
            return LocalDate.now();
        }

        String rawValue = result.output().trim();
        return LocalDate.parse(rawValue.substring(0, Math.min(10, rawValue.length())));
    }

    private int resolveChangeSetSize(Path gitRoot, String commitHash) {
        if (commitHash == null || commitHash.isBlank()) {
            return 0;
        }

        return changeSetSizeCache.computeIfAbsent(commitHash, key -> countChangedFiles(gitRoot, key));
    }

    private int countChangedFiles(Path gitRoot, String commitHash) {
        GitCommandResult result = gitCommandExecutor.execute(
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

    private List<CommitTouch> parseCommitTouches(String gitLogOutput) {
        if (gitLogOutput == null || gitLogOutput.isBlank()) {
            return List.of();
        }

        String[] lines = gitLogOutput.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<CommitTouch> touches = new ArrayList<>();

        String currentCommitHash = "";
        String currentAuthor = "";
        LocalDate currentCommitDate = null;
        StringBuilder currentMessage = new StringBuilder();
        int currentAdded = 0;
        int currentDeleted = 0;
        boolean insideCommit = false;
        boolean insideMessage = false;

        for (String line : lines) {
            if (line.startsWith("@@COMMIT@@")) {
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

                insideCommit = true;
                insideMessage = true;
                currentAdded = 0;
                currentDeleted = 0;
                currentMessage = new StringBuilder();

                String metadata = line.substring("@@COMMIT@@".length());
                String[] parts = metadata.split("\u001f", -1);

                currentCommitHash = parts.length > 0 ? parts[0].trim() : "";
                currentAuthor = parts.length > 1 ? parts[1].trim() : "";
                currentCommitDate = parts.length > 2 ? parseCommitDate(parts[2].trim()) : null;
                if (parts.length > 3) {
                    String firstMessagePart = parts[3].replace("@@END_MESSAGE@@", "").trim();
                    if (!firstMessagePart.isBlank()) {
                        currentMessage.append(firstMessagePart).append('\n');
                    }
                    if (parts[3].contains("@@END_MESSAGE@@")) {
                        insideMessage = false;
                    }
                }
                continue;
            }

            if (!insideCommit) {
                continue;
            }

            if (insideMessage) {
                if (line.contains("@@END_MESSAGE@@")) {
                    String messageLine = line.replace("@@END_MESSAGE@@", "").trim();
                    if (!messageLine.isBlank()) {
                        currentMessage.append(messageLine).append('\n');
                    }
                    insideMessage = false;
                    continue;
                }

                currentMessage.append(line).append('\n');
                continue;
            }

            String[] numstat = line.split("\t");
            if (numstat.length < 3) {
                continue;
            }

            currentAdded += parseNumstatValue(numstat[0]);
            currentDeleted += parseNumstatValue(numstat[1]);
        }

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

        return List.copyOf(touches);
    }

    private void collectBugIds(String message, Set<String> collector) {
        if (message == null || message.isBlank()) {
            return;
        }

        Matcher matcher = BUG_ID_PATTERN.matcher(message.toUpperCase());
        while (matcher.find()) {
            collector.add(matcher.group());
        }
    }

    private LocalDate parseCommitDate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return LocalDate.parse(rawValue.substring(0, Math.min(10, rawValue.length())));
    }

    private int parseNumstatValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank() || rawValue.equals("-")) {
            return 0;
        }
        return Integer.parseInt(rawValue.trim());
    }

    private record CommitTouch(
            String commitHash,
            String author,
            LocalDate commitDate,
            String fullMessage,
            int addedLines,
            int deletedLines
    ) {
    }
}
