package it.university.avro.weka.domain;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;

public enum ClassifierOption {
    NAIVE_BAYES("NaiveBayes") {
        @Override
        public Classifier buildClassifier() {
            return new NaiveBayes();
        }
    },
    IBK("IBk") {
        @Override
        public Classifier buildClassifier() {
            return new IBk();
        }
    },
    RANDOM_FOREST("RandomForest") {
        @Override
        public Classifier buildClassifier() {
            return new RandomForest();
        }
    };

    private final String outputName;

    ClassifierOption(final String outputName) {
        this.outputName = outputName;
    }

    public String outputName() {
        return outputName;
    }

    public abstract Classifier buildClassifier();
}
