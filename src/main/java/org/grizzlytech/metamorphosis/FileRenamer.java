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
        boolean action = false;
        String prefix = "IMG";

        // Scan files, sorting into increasing date taken order
        FileInfo[] files = scan(dir, true);

        // Identify duplicates ( [0]=actual and [1]=false positive )
        List<List<FileInfo>>[] duplicates = findDuplicates(files);

        // If there are duplicates, print them, otherwise rename the files
        if (duplicates[0].size() > 0) {
            printDuplicates("DUP:", false, duplicates[0]);
            printDuplicates("FSE:", false, duplicates[1]);
        } else {
            rename(files, action, prefix);
            printDuplicates("FSE:", action, duplicates[1]);
        }
    }

    /**
     * Scan a directory for supported media files.
     * Scanning involves extracting the date taken and file size
     *
     * @param dir  directory to scan
     * @param sort whether to sort the results by date taken before returning
     * @return array of FileInfo objects
     */
    public static FileInfo[] scan(String dir, boolean sort) {
        LOG.info("Scanning [{}]", dir);
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
            LOG.error("Scanning error", ex);
        }
        return results;
    }

    /**
     * Find all duplicates in the directory. Duplicates are required to have the same MD5 checksum.
     * <p>
     * Strategy is to quickly find all media files with the same date taken and file size, then
     * to compare the MD5 checksum for the candidate duplicates
     *
     * @param files media files to examine
     */
    public static List<List<FileInfo>>[] findDuplicates(FileInfo[] files) {
        // Index of all media with the same date taken and file size
        Index<String, FileInfo> dateAndSizeIndex = new Index<>();

        LOG.info("Indexing [fileCount={}]", files.length);
        for (FileInfo info : files) {
            dateAndSizeIndex.insert(getDateAndSizeKey(info), info);
        }

        // Duplicate checking
        List<FileInfo> dateAndSizeCollisions = dateAndSizeIndex.getCollisions();
        LOG.info("Duplicate checking [candidates={}]", dateAndSizeCollisions.size());

        // Re-index the possible duplicates using the md5 hash, which looks at the actual file content
        // MD5 is a more expensive operation, hence only performed on the candidate duplicates
        Index<String, FileInfo> md5Index = new Index<>();
        dateAndSizeCollisions.forEach(p -> md5Index.insert(p.getMD5Checksum(), p));

        // Retract md5 collisions to leave "false positives"
        for (FileInfo info : md5Index.getCollisions()) {
            dateAndSizeIndex.retract(getDateAndSizeKey(info), info);
        }

        return new List[]{md5Index.getGroupedCollisions(), dateAndSizeIndex.getGroupedCollisions()};
    }

    private static String getDateAndSizeKey(FileInfo info) {
        return info.getDateTaken() + "_" + info.getFileLength();
    }

    public static void printDuplicates(String prefix, boolean target, List<List<FileInfo>> duplicates) {
        int groupId = 0;
        int counter = 0;
        for (List<FileInfo> group : duplicates) {
            String groupName = String.format("%04d", ++groupId);
            for (FileInfo d : group) {
                LOG.info("{} {} {} {} {} {} \"{}\"", prefix, groupName,
                        d.getMD5Checksum(), // duplicate hash
                        d.getLocalDateAsText(), String.format("%010d", d.getFileLength()), // dateAndSize index
                        (target) ? "T" : "S",
                        (target) ? d.getTargetFile().getAbsolutePath() : d.getSourceFile().getAbsolutePath());
                ++counter;
            }
        }
    }

    public static void rename(FileInfo[] files, boolean action, String prefix) {
        // Set the positional value, starting at 1000
        LOG.info("Renaming [fileCount={}]", files.length);
        for (int position = 0; position < files.length; position++) {
            FileInfo info = files[position];
            info.setPosition(position + 1000);

            // Determine the target filename post the re-sort
            String targetFileName = info.getRelativeName(prefix, true);
            File targetFile = new File(info.getSourceFile().getParent(), targetFileName);
            info.setTargetFile(targetFile);

            if (action) {
                // Set the creation and modification dates then rename the file
                updateDates(info);
                info.getSourceFile().renameTo(targetFile);
                // Checkpoint log
                if (position % 1000 == 0) {
                    LOG.info(" Checkpoint [{}]", targetFile.getName());
                }

            } else {
                // Emit proposals
                LOG.info("move \"{}\" \"{}\"", info.getSourceFileName(), targetFile.getName());
            }
        }
        LOG.info("Done");
    }

    protected static void updateDates(FileInfo p) {
        if (p.getDateTaken() != null) {
            FileMetadata.setFileDate(p.getSourceFile(), p.getDateTaken());
        }
    }
}
