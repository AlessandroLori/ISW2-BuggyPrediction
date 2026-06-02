package it.university.avro.smelldataset.util;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemanticVersionComparator implements Comparator<String> {

    public static final SemanticVersionComparator INSTANCE = new SemanticVersionComparator();

    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)");

    private SemanticVersionComparator() {
    }

    @Override
    public int compare(final String firstVersion, final String secondVersion) {
        final List<String> firstParts = splitVersion(firstVersion);
        final List<String> secondParts = splitVersion(secondVersion);
        final int maxParts = Math.max(firstParts.size(), secondParts.size());

        for (int index = 0; index < maxParts; index++) {
            final String firstPart = partAt(firstParts, index);
            final String secondPart = partAt(secondParts, index);
            final int comparison = compareVersionPart(firstPart, secondPart);

            if (comparison != 0) {
                return comparison;
            }
        }

        return normalizeVersion(firstVersion).compareTo(normalizeVersion(secondVersion));
    }

    private static List<String> splitVersion(final String version) {
        return List.of(normalizeVersion(version).split("\\."));
    }

    private static String normalizeVersion(final String version) {
        return version.trim()
                .toLowerCase(Locale.ROOT)
                .replaceFirst("^release-", "")
                .replaceFirst("^avro-", "")
                .replaceFirst("^[vV]", "");
    }

    private static String partAt(final List<String> parts, final int index) {
        if (index >= parts.size()) {
            return "0";
        }
        return parts.get(index);
    }

    private static int compareVersionPart(final String firstPart, final String secondPart) {
        final Integer firstNumber = leadingNumber(firstPart);
        final Integer secondNumber = leadingNumber(secondPart);

        if (firstNumber != null && secondNumber != null) {
            final int numericComparison = Integer.compare(firstNumber, secondNumber);
            if (numericComparison != 0) {
                return numericComparison;
            }
        }

        return firstPart.compareTo(secondPart);
    }

    private static Integer leadingNumber(final String value) {
        final Matcher matcher = LEADING_NUMBER.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
