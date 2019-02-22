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
        rename(dir, true);
    }

    public static void rename(String dir, boolean action) {

        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            // Sort supported media by date taken
            LOG.info("Step1. Scanning [{}]", dir);
            FileInfo[] sorted = paths.filter(Files::isRegularFile).map(Path::toFile).filter(FileMetadata.IS_SUPPORTED).
                    map(FileInfo::new).sorted().toArray(FileInfo[]::new);

            // Set the positional value, starting at 1000
            LOG.info("Step2. Processing [fileCount={}]", sorted.length);
            for (int position = 0; position < sorted.length; position++) {
                FileInfo p = sorted[position];
                p.setPosition(position + 1000);

                // Determine the implied filename post the re-sort
                String targetFileName = p.getRelativeName("IMG", true);
                File targetFile = new File(p.getSourceFile().getParent(), targetFileName);

                if (action) {
                    // Set the creation and modification dates then rename the file
                    updateDates(p);
                    p.getSourceFile().renameTo(targetFile);
                    // Checkpoint log
                    if (position % 1000 == 0) {
                        LOG.info(" Checkpoint [{}]", targetFile.getName());
                    }

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
