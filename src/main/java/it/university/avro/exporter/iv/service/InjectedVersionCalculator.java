package it.university.avro.exporter.iv.service;

import it.university.avro.exporter.iv.domain.TicketCsvRow;
import it.university.avro.exporter.iv.domain.TicketWithInjectedVersionRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class InjectedVersionCalculator {

    private static final String NOT_AVAILABLE = "n/a";
    private final AffectedVersionExpander affectedVersionExpander;

    private final VersionOrderCatalogFactory versionOrderCatalogFactory;

    public InjectedVersionCalculator(
            final VersionOrderCatalogFactory versionOrderCatalogFactory,
            final AffectedVersionExpander affectedVersionExpander
    ) {
        this.versionOrderCatalogFactory = Objects.requireNonNull(
                versionOrderCatalogFactory,
                "versionOrderCatalogFactory must not be null"
        );
        this.affectedVersionExpander = Objects.requireNonNull(
                affectedVersionExpander,
                "affectedVersionExpander must not be null"
        );
    }

    public List<TicketWithInjectedVersionRow> enrich(final List<TicketCsvRow> rows) {
        Objects.requireNonNull(rows, "rows must not be null");

        final VersionOrderCatalog versionCatalog = versionOrderCatalogFactory.create(rows);
        final double averageP = calculateAverageProportion(rows, versionCatalog);

        final List<TicketWithInjectedVersionRow> result = new ArrayList<>();
        for (TicketCsvRow row : rows) {
            final String injectedVersion = hasAffectedVersions(row)
                    ? findOldestAffectedVersion(row, versionCatalog)
                    : estimateInjectedVersion(row, versionCatalog, averageP);

            final AffectedVersionExpander.ExpandedAffectedVersions expandedAffectedVersions =
                    affectedVersionExpander.expand(row, versionCatalog, injectedVersion);

            result.add(new TicketWithInjectedVersionRow(
                    row.ticketId(),
                    row.createdDate(),
                    row.closedDate(),
                    row.openingVersion(),
                    row.openingVersionDate(),
                    expandedAffectedVersions.affectedVersionCount(),
                    expandedAffectedVersions.affectedVersions(),
                    row.fixedVersion(),
                    row.fixedVersionDate(),
                    injectedVersion
            ));
        }

        return List.copyOf(result);
    }

    private double calculateAverageProportion(
            final List<TicketCsvRow> rows,
            final VersionOrderCatalog versionCatalog
    ) {
        double totalP = 0.0d;
        int ticketsUsedForAverage = 0;

        for (TicketCsvRow row : rows) {
            if (!hasAffectedVersions(row)) {
                continue;
            }

            final String oldestAffectedVersion = findOldestAffectedVersion(row, versionCatalog);
            final double ticketP = calculateProportion(row, oldestAffectedVersion, versionCatalog);

            totalP += ticketP;
            ticketsUsedForAverage++;

            System.out.printf(
                    Locale.ROOT,
                    "Ticket %s -> IV=%s, P=%.6f, cumulative P=%.6f%n",
                    row.ticketId(),
                    oldestAffectedVersion,
                    ticketP,
                    totalP
            );
        }

        if (ticketsUsedForAverage == 0) {
            throw new IllegalStateException("No tickets with affected version available to compute average P");
        }

        final double averageP = totalP / ticketsUsedForAverage;

        System.out.printf(
                Locale.ROOT,
                "Tickets used for average P = %d%n",
                ticketsUsedForAverage
        );

        System.out.printf(
                Locale.ROOT,
                "Average P = %.6f / %d = %.6f%n",
                totalP,
                ticketsUsedForAverage,
                averageP
        );

        return averageP;
    }

    private double calculateProportion(
            final TicketCsvRow row,
            final String injectedVersion,
            final VersionOrderCatalog versionCatalog
    ) {
        final int fv = versionCatalog.positionOf(row.fixedVersion());
        final int ov = versionCatalog.positionOf(row.openingVersion());
        final int iv = versionCatalog.positionOf(injectedVersion);

        if (fv < ov) {
            throw new IllegalArgumentException("Fixed version comes before opening version for ticket " + row.ticketId());
        }

        if (iv > fv) {
            throw new IllegalArgumentException("Injected version comes after fixed version for ticket " + row.ticketId());
        }

        final int denominator = fv - ov;
        if (denominator == 0) {
            return 0.0d;
        }

        return (double) (fv - iv) / denominator;
    }

    private String estimateInjectedVersion(
            final TicketCsvRow row,
            final VersionOrderCatalog versionCatalog,
            final double averageP
    ) {
        final int fv = versionCatalog.positionOf(row.fixedVersion());
        final int ov = versionCatalog.positionOf(row.openingVersion());

        if (fv < ov) {
            throw new IllegalArgumentException(
                    "Fixed version comes before opening version for ticket " + row.ticketId()
            );
        }

        final double estimatedIvPosition = fv - ((fv - ov) * averageP);

        final int flooredPosition = (int) Math.floor(estimatedIvPosition);

        final int clampedPosition = clamp(
                flooredPosition,
                1,
                versionCatalog.maxPosition()
        );

        return versionCatalog.versionAtPosition(clampedPosition);
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
        if (affectedVersions == null || affectedVersions.isBlank() || NOT_AVAILABLE.equalsIgnoreCase(affectedVersions.trim())) {
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

    private int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }
}