package it.university.avro.refactoringdataset.git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class LocalGitRepositoryResolver {

    public Optional<Path> resolveGitRoot(Path sourcePath) {
        Path current = sourcePath.toAbsolutePath().normalize();
        if (Files.isRegularFile(current)) {
            current = current.getParent();
        }

        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return Optional.of(current);
            }
            current = current.getParent();
        }

        return Optional.empty();
    }
}
