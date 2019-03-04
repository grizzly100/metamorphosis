package org.grizzlytech.metamorphosis;

import org.grizzlytech.metamorphosis.util.Index;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

        // Sort supported media by date taken
        LOG.info("Step1. Scanning [{}]", dir);
        FileInfo[] sorted = scan(dir, true);

        // Keep an index of all media with the same date taken and file size
        // This will aid duplicate detection later
        Index<String, FileInfo> dateAndSizeIndex = new Index<>();

        // Set the positional value, starting at 1000
        LOG.info("Step2. Processing [fileCount={}]", sorted.length);
        for (int position = 0; position < sorted.length; position++) {
            FileInfo p = sorted[position];
            p.setPosition(position + 1000);

            String key = p.getDateTaken() + "_" + p.getFileLength();
            dateAndSizeIndex.insert(key, p);

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

        // Duplicate checking
        List<FileInfo> dateAndSizeCollisions = dateAndSizeIndex.getCollisions();
        LOG.info("Step3. Duplicate checking [candidates={}]", dateAndSizeCollisions.size());

        // Re-index the possible duplicates using the md5 hash, which looks at the actual file content
        // MD5 is a more expensive operation, hence only performed on the candidate duplicates
        Index<String, FileInfo> md5Index = new Index<>();
        dateAndSizeCollisions.forEach(p -> md5Index.insert(p.getMD5Checksum(), p));

        // Now print the final list of duplicates
        List<List<FileInfo>> duplicates = md5Index.getGroupedCollisions();
        int groupId = 0;
        int counter = 0;
        for (List<FileInfo> group : duplicates) {
            String groupName = String.format("%04d", ++groupId);
            for (FileInfo d : group) {
                LOG.info("DUP: {} {} {}", groupName, d.getMD5Checksum(), d.getSourceFile().getAbsolutePath());
                ++counter;
            }
        }
        LOG.info("Done. Duplicate checking completed [duplicates={}]", counter);
    }

    public static FileInfo[] scan(String dir, boolean sort) {
        FileInfo[] results = null;
        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            // Sort supported media by date taken
            Stream<FileInfo> stream = paths.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(FileMetadata.IS_SUPPORTED)
                    .map(FileInfo::new);
            // Sort if needed
            if (sort) {
                stream = stream.sorted();
            }

            results = stream.toArray(FileInfo[]::new);
        } catch (IOException ex) {
            LOG.error("Err", ex);
        }
        return results;
    }

    protected static void updateDates(FileInfo p) {
        if (p.getDateTaken() != null) {
            FileMetadata.setFileDate(p.getSourceFile(), p.getDateTaken());
        }
    }
}
