package it.university.avro.exporter.iv.service;

import it.university.avro.exporter.iv.domain.TicketCsvRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AffectedVersionExpander {

    private static final String NOT_AVAILABLE = "n/a";

    public ExpandedAffectedVersions expand(
            final TicketCsvRow row,
            final VersionOrderCatalog versionCatalog,
            final String injectedVersion
    ) {
        Objects.requireNonNull(row, "row must not be null");
        Objects.requireNonNull(versionCatalog, "versionCatalog must not be null");
        Objects.requireNonNull(injectedVersion, "injectedVersion must not be null");

        final String startingAffectedVersion = hasAffectedVersions(row)
                ? findOldestAffectedVersion(row, versionCatalog)
                : injectedVersion;

        final List<String> expandedVersions = versionCatalog.versionsFromInclusiveToExclusive(
                startingAffectedVersion,
                row.fixedVersion()
        );

        return new ExpandedAffectedVersions(
                expandedVersions.size(),
                String.join(";", expandedVersions)
        );
    }

    private String findOldestAffectedVersion(
            final TicketCsvRow row,
            final VersionOrderCatalog versionCatalog
    ) {
        return splitAffectedVersions(row.affectedVersion()).stream()
                .min((left, right) -> Integer.compare(
                        versionCatalog.positionOf(left),
                        versionCatalog.positionOf(right)
                ))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No affected versions available for ticket " + row.ticketId()
                ));
    }

    private boolean hasAffectedVersions(final TicketCsvRow row) {
        return row.affectedVersion() != null
                && !row.affectedVersion().isBlank()
                && !NOT_AVAILABLE.equalsIgnoreCase(row.affectedVersion().trim());
    }

    private List<String> splitAffectedVersions(final String affectedVersions) {
        if (affectedVersions == null
                || affectedVersions.isBlank()
                || NOT_AVAILABLE.equalsIgnoreCase(affectedVersions.trim())) {
            return List.of();
        }

        final String[] tokens = affectedVersions.split(";");
        final List<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                result.add(token.trim());
            }
        }
        return List.copyOf(result);
    }

    public record ExpandedAffectedVersions(int affectedVersionCount, String affectedVersions) {
    }
}