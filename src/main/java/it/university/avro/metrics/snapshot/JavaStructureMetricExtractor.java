package it.university.avro.metrics.snapshot;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

public final class JavaStructureMetricExtractor {

    public StructuralMetrics extract(final String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return StructuralMetrics.empty();
        }

        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(sourceCode);

            int maxNestingDepth = 0;
            int decisionPoints = 0;

            for (MethodDeclaration method : compilationUnit.findAll(MethodDeclaration.class)) {
                if (method.getBody().isEmpty()) {
                    continue;
                }

                final Node body = method.getBody().get();
                maxNestingDepth = Math.max(maxNestingDepth, computeMaxNesting(body, 0));
                decisionPoints += countDecisionPoints(body);
            }

            for (ConstructorDeclaration constructor : compilationUnit.findAll(ConstructorDeclaration.class)) {
                final Node body = constructor.getBody();
                maxNestingDepth = Math.max(maxNestingDepth, computeMaxNesting(body, 0));
                decisionPoints += countDecisionPoints(body);
            }

            return new StructuralMetrics(maxNestingDepth, decisionPoints);
        } catch (ParseProblemException exception) {
            System.out.println("[STRUCTURE-PARSE-SUSPECT] reason=" + exception.getMessage());
            return StructuralMetrics.empty();
        }
    }

    private int computeMaxNesting(final Node node, final int currentDepth) {
        int maxDepth = currentDepth;

        for (Node child : node.getChildNodes()) {
            final boolean controlStructure = isNestingControlNode(child);
            final int nextDepth = controlStructure ? currentDepth + 1 : currentDepth;
            maxDepth = Math.max(maxDepth, computeMaxNesting(child, nextDepth));
        }

        return maxDepth;
    }

    private int countDecisionPoints(final Node node) {
        int count = 0;

        if (node instanceof IfStmt
                || node instanceof ForStmt
                || node instanceof ForEachStmt
                || node instanceof WhileStmt
                || node instanceof DoStmt
                || node instanceof ConditionalExpr
                || node instanceof CatchClause) {
            count++;
        }

        if (node instanceof SwitchEntry switchEntry && !switchEntry.getLabels().isEmpty()) {
            count++;
        }

        for (Node child : node.getChildNodes()) {
            count += countDecisionPoints(child);
        }

        return count;
    }

    private boolean isNestingControlNode(final Node node) {
        return node instanceof IfStmt
                || node instanceof ForStmt
                || node instanceof ForEachStmt
                || node instanceof WhileStmt
                || node instanceof DoStmt
                || node instanceof SwitchStmt
                || node instanceof SwitchEntry
                || node instanceof CatchClause
                || node instanceof ConditionalExpr;
    }
}
