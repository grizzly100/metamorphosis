package org.grizzlytech.metamorphosis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Rename media files, ordering by date taken ascending
 */
public class FileRenamer {

    private static final Logger LOG = LoggerFactory.getLogger(FileRenamer.class);

    public static void main(String[] args) {
        String dir = args[0];
        rename(dir, false);
    }

    public static void rename(String dir, boolean action) {

        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            // Sort supported media by date taken
            FileInfo[] sorted = paths.filter(Files::isRegularFile).map(Path::toFile).filter(FileMetadata.IS_SUPPORTED).
                    map(FileInfo::new).sorted().toArray(FileInfo[]::new);

            // Set the positional value, starting at 1000
            for (int position = 0; position < sorted.length; position++) {
                FileInfo p = sorted[position];
                p.setPosition(position + 1000);

                // Determine the implied filename post the re-sort
                String targetFileName = p.getRelativeName("IMG", true);
                File targetFile = new File(p.getSourceFile().getParent(), targetFileName);

                if (action) {
                    p.getSourceFile().renameTo(targetFile);
                    updateDates(p);
                } else {
                    // Emit proposals
                    LOG.info("move \"{}\" \"{}\"", p.getSourceFileName(), targetFile.getName());
                }
            }

        } catch (IOException ex) {
            LOG.error("Err", ex);
        }
    }

    protected static void updateDates(FileInfo p) {
        if (p.getDateTaken() != null) {
            FileMetadata.setFileDate(p.getSourceFile(), p.getDateTaken());
        }
    }
}
