package it.university.avro.metrics.history;

import it.university.avro.metrics.domain.BugTicket;
import it.university.avro.metrics.git.TemporaryGitRepository;
import it.university.avro.metrics.util.ClassPathNormalizer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuggyClassLabelResolver {

    private static final Pattern BUG_ID_PATTERN = Pattern.compile("AVRO-\\d+");
    private static final int EPSILON_DAYS = 1;

    public Map<String, Set<String>> resolveTouchedClassesByTicket(
            final TemporaryGitRepository repository,
            final Map<String, BugTicket> knownTickets
    ) {
        final String gitLogOutput = repository.gitLogAllWithChangedPaths();

        if (gitLogOutput == null || gitLogOutput.isBlank() || knownTickets.isEmpty()) {
            return Map.of();
        }

        final List<CommitWithPaths> commits = parseCommits(gitLogOutput);
        final Map<String, Set<String>> touchedClassesByTicket = new LinkedHashMap<>();

        for (CommitWithPaths commit : commits) {
            if (commit.subject() == null || commit.subject().isBlank() || commit.commitDate() == null) {
                continue;
            }

            final Set<String> normalizedClasses = commit.changedPaths().stream()
                    .filter(this::isProductionJavaPath)
                    .map(ClassPathNormalizer::normalize)
                    .filter(path -> !path.isBlank())
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);

            if (normalizedClasses.isEmpty()) {
                continue;
            }

            final Matcher matcher = BUG_ID_PATTERN.matcher(commit.subject().toUpperCase());
            while (matcher.find()) {
                final String bugId = matcher.group();
                final BugTicket ticket = knownTickets.get(bugId);

                if (ticket == null || !isCommitDateConsistent(commit.commitDate(), ticket)) {
                    continue;
                }

                touchedClassesByTicket
                        .computeIfAbsent(bugId, ignored -> new LinkedHashSet<>())
                        .addAll(normalizedClasses);
            }
        }

        final Map<String, Set<String>> immutableMap = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : touchedClassesByTicket.entrySet()) {
            immutableMap.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutableMap);
    }

    private List<CommitWithPaths> parseCommits(final String gitLogOutput) {
        final String[] lines = gitLogOutput.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        final List<CommitWithPaths> commits = new ArrayList<>();

        LocalDate currentCommitDate = null;
        String currentSubject = null;
        Set<String> currentPaths = new LinkedHashSet<>();
        boolean insideCommit = false;

        for (String line : lines) {
            if (line.startsWith("@@COMMIT@@")) {
                if (insideCommit) {
                    commits.add(new CommitWithPaths(currentCommitDate, currentSubject, Set.copyOf(currentPaths)));
                }

                insideCommit = true;
                currentPaths = new LinkedHashSet<>();

                final String metadata = line.substring("@@COMMIT@@".length());
                final String[] parts = metadata.split("\u001f", -1);
                currentCommitDate = parts.length > 1 ? parseCommitDate(parts[1].trim()) : null;
                currentSubject = parts.length > 2 ? parts[2].trim() : "";
                continue;
            }

            if (!insideCommit) {
                continue;
            }

            final String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                currentPaths.add(trimmed);
            }
        }

        if (insideCommit) {
            commits.add(new CommitWithPaths(currentCommitDate, currentSubject, Set.copyOf(currentPaths)));
        }

        return List.copyOf(commits);
    }

    private boolean isProductionJavaPath(final String path) {
        if (path == null || path.isBlank() || !path.endsWith(".java")) {
            return false;
        }

        final String normalized = path.replace('\\', '/').toLowerCase();
        return !normalized.contains("/src/test/")
                && !normalized.contains("/test/")
                && !normalized.contains("/src/it/")
                && !normalized.contains("/integration-test/");
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

    private record CommitWithPaths(
            LocalDate commitDate,
            String subject,
            Set<String> changedPaths
    ) {
    }
}