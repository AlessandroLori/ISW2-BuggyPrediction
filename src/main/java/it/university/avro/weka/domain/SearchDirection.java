package it.university.avro.weka.domain;

import weka.attributeSelection.GreedyStepwise;

public enum SearchDirection {
    FORWARD("Forward", false),
    BACKWARD("Backward", true);

    private final String outputPrefix;
    private final boolean backward;

    SearchDirection(final String outputPrefix, final boolean backward) {
        this.outputPrefix = outputPrefix;
        this.backward = backward;
    }

    public String outputPrefix() {
        return outputPrefix;
    }

    public String cliSpecification() {
        if (backward) {
            return "weka.attributeSelection.GreedyStepwise -B";
        }
        return "weka.attributeSelection.GreedyStepwise";
    }

    public GreedyStepwise buildSearch() {
        final GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(backward);
        return search;
    }
}
