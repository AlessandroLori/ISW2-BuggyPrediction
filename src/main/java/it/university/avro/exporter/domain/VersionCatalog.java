package it.university.avro.exporter.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class VersionCatalog {

    private final Map<String, JiraVersion> versionsById;
    private final Map<String, JiraVersion> versionsByName;
    private final List<JiraVersion> releasedVersionsWithDate;

    public VersionCatalog(final List<JiraVersion> versions) {
        final List<JiraVersion> safeVersions = List.copyOf(Objects.requireNonNull(versions, "versions must not be null"));
        this.versionsById = safeVersions.stream()
                .filter(version -> version.id() != null && !version.id().isBlank())
                .collect(Collectors.toUnmodifiableMap(JiraVersion::id, Function.identity(), (left, right) -> left));
        this.versionsByName = safeVersions.stream()
                .filter(version -> version.name() != null && !version.name().isBlank())
                .collect(Collectors.toUnmodifiableMap(JiraVersion::name, Function.identity(), (left, right) -> left));
        this.releasedVersionsWithDate = safeVersions.stream()
                .filter(JiraVersion::released)
                .filter(version -> version.releaseDate() != null)
                .sorted(Comparator.comparing(JiraVersion::releaseDate))
                .toList();
    }

    public JiraVersion findByReference(final VersionReference reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        if (reference.id() != null && !reference.id().isBlank()) {
            final JiraVersion version = versionsById.get(reference.id());
            if (version != null) {
                return version;
            }
        }
        if (reference.name() != null && !reference.name().isBlank()) {
            return versionsByName.get(reference.name());
        }
        return null;
    }

    public List<JiraVersion> releasedVersionsWithDate() {
        return releasedVersionsWithDate;
    }
}
