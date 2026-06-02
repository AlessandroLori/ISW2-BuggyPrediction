package it.university.avro.refactoringdataset.git;

import it.university.avro.metrics.git.GitCommandResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public final class LocalGitCommandExecutor {

    public GitCommandResult execute(Path workingDirectory, List<String> command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            return new GitCommandResult(exitCode, output);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to execute command: " + String.join(" ", command), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing command: " + String.join(" ", command), exception);
        }
    }
}
