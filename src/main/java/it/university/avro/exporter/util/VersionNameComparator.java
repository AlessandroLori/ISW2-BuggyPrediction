package it.university.avro.exporter.util;

import java.util.Comparator;

public final class VersionNameComparator implements Comparator<String> {

    @Override
    public int compare(final String left, final String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }

        final String[] leftTokens = left.split("[.-]");
        final String[] rightTokens = right.split("[.-]");

        final int maxLength = Math.max(leftTokens.length, rightTokens.length);
        for (int index = 0; index < maxLength; index++) {
            final String leftToken = index < leftTokens.length ? leftTokens[index] : "0";
            final String rightToken = index < rightTokens.length ? rightTokens[index] : "0";

            final int comparison = compareToken(leftToken, rightToken);
            if (comparison != 0) {
                return comparison;
            }
        }

        return left.compareToIgnoreCase(right);
    }

    private int compareToken(final String leftToken, final String rightToken) {
        final boolean leftNumeric = leftToken.chars().allMatch(Character::isDigit);
        final boolean rightNumeric = rightToken.chars().allMatch(Character::isDigit);

        if (leftNumeric && rightNumeric) {
            return Integer.compare(Integer.parseInt(leftToken), Integer.parseInt(rightToken));
        }

        return leftToken.compareToIgnoreCase(rightToken);
    }
}