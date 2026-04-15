package it.university.avro.metrics.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public final class GitCommandExecutor {

    public GitCommandResult execute(final Path workingDirectory, final List<String> command) {
        try {
            final ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDirectory.toFile());
            processBuilder.redirectErrorStream(true);

            final Process process = processBuilder.start();
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            final int exitCode = process.waitFor();

            return new GitCommandResult(exitCode, output);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to execute command: " + String.join(" ", command), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing command: " + String.join(" ", command), exception);
        }
    }

    public GitCommandResult executeOrThrow(final Path workingDirectory, final List<String> command) {
        final GitCommandResult result = execute(workingDirectory, command);

        if (!result.isSuccess()) {
            throw new IllegalStateException(
                    "Git command failed: " + String.join(" ", command) + System.lineSeparator() + result.output()
            );
        }

        return result;
    }
}