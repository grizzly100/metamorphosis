package org.grizzlytech.metamorphosis;

import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import org.grizzlytech.metamorphosis.metadata.MetadataDirectoryFix;
import org.grizzlytech.metamorphosis.util.Index;
import org.grizzlytech.metamorphosis.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Rename media files, ordering by date taken ascending
 */
public class FileRenamer {

    private static final Logger LOG = LoggerFactory.getLogger(FileRenamer.class);

    private static final int START_AT = 1000;

    public static void main(String[] args) {
        String dir = args[0];
        boolean action = true;
        String prefix = "IMG";

        // Handle case where photo dates are wrong due to incorrect camera date setting
        // setTimeOffset(Instant.parse("2004-01-01T00:00:00Z"), Instant.parse("2010-12-25T15:00:00Z"));
        //MetadataDirectoryFix.applyFixes();

        // Scan files, sorting into increasing date taken order
        FileInfo[] files = scan(dir, true);

        // Identify duplicates ( [0]=actual and [1]=false positive )
        List<List<FileInfo>>[] duplicates = findDuplicates(files);

        // If there are duplicates, print them, otherwise renameFile the files
        if (duplicates[0].size() > 0) {
            printDuplicates("DUP:", false, duplicates[0]);
            printDuplicates("FSE:", false, duplicates[1]);
        } else {
            renameFiles(files, action, prefix);
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
    private static FileInfo[] scan(String dir, boolean sort) {
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
    private static List<List<FileInfo>>[] findDuplicates(FileInfo[] files) {
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
        return info.getLocalDateAsText() + "_" + info.getFileLength();
    }

    private static void printDuplicates(String prefix, boolean target, List<List<FileInfo>> duplicates) {
        int groupId = 0;
        int counter = 0;
        for (List<FileInfo> group : duplicates) {
            String groupName = String.format("%04d", ++groupId);
            String command;
            Instant priorDate = null;
            for (FileInfo d : group) {
                // Recommend deletion only if subsequent timestamps are the same (to the nearest second)
                command = (priorDate == null || !TimeUtil.withinASecond(priorDate, d.getDateTaken())) ? "REM" : "DEL";
                // command = (priorDate == null) ? "REM" : "DEL";
                LOG.info("{} {} {} {} {} {} {} \"{}\"", prefix, groupName,
                        d.getMD5Checksum(), // content hash
                        d.getLocalDateAsText() + " " + d.getLocalTimeAsText(), // date and time to nearest second
                        String.format("%010d", d.getFileLength()), // dateAndSize index
                        (target) ? "T" : "S", // target (if renamed) or source filename
                        command, // retain (REM) or delete (DEL)
                        (target) ? d.getTargetFile().getAbsolutePath() : d.getSourceFile().getAbsolutePath());
                ++counter;
                priorDate = d.getDateTaken();
            }
        }
    }

    private static void renameFiles(FileInfo[] files, boolean action, String prefix) {
        // Set the positional value, starting at 1000
        LOG.info("Renaming [fileCount={}]", files.length);
        List<FileInfo> conflicts = new LinkedList<>();
        for (int position = 0; position < files.length; position++) {
            FileInfo info = files[position];
            info.setPosition(position + START_AT);

            // Determine the target filename post the re-sort
            int index = -1;
            do {
                info.setTargetFile(info.getRelativeFile(prefix, ++index));
            }
            while (info.renameConflicts());

            if (action) {
                // Set the creation and modification dates then renameFile the file
                updateDates(info);
                if (info.renameRequired()) {
                    boolean renamed = renameFile(info.getSourceFile(), info.getTargetFile());
                    if (renamed && index > 0) {
                        conflicts.add(info);
                    }
                }
                // Checkpoint log
                if (position % 1000 == 0) {
                    LOG.info(" Checkpoint [{}]", info.getTargetFile());
                }

            } else {
                // Emit proposals
                LOG.info("move \"{}\" \"{}\"", info.getSourceFileName(), info.getTargetFile());
            }
        }

        // Assume conflicts (that forced indexing) now removed
        for (FileInfo info : conflicts) {
            File newTargetFile = info.getRelativeFile(prefix, 0);
            if (renameFile(info.getTargetFile(), newTargetFile)) {
                info.setTargetFile(newTargetFile);
            }
        }
        LOG.info("Done");
    }

    private static void updateDates(FileInfo p) {
        if (p.getDateTaken() != null) {
            FileMetadata.setFileDate(p.getSourceFile(), p.getDateTaken());
        }
    }

    private static boolean renameFile(File sourceFile, File targetFile) {
        boolean ret = false;
        if (targetFile.exists()) {
            LOG.error("CONFLICT: Cannot renameFile [{}] to [{}] as target already exists",
                    sourceFile, targetFile);
        } else {
            ret = sourceFile.renameTo(targetFile);
            if (ret) {
                LOG.debug("moved \"{}\" \"{}\"", sourceFile, targetFile);
            } else {
                LOG.error("FAILED: Rename failed for [{}] to [{}]", sourceFile, targetFile);
            }
        }
        return ret;
    }

    /**
     * Call this method if the camera time was incorrect.
     *
     * @param photoCameraTime time a sample photo was taken as recorded in the metadata
     * @param photoActualTime actual time you believe the sample photo was taken
     */
    private static void setTimeOffset(Instant photoCameraTime, Instant photoActualTime) {
        if (photoCameraTime != null && photoActualTime != null) {
            long timeOffset = photoActualTime.getEpochSecond() - photoCameraTime.getEpochSecond();
            LOG.info("Setting time offset of {} seconds", timeOffset);
            FileMetadata.setTimeOffset(timeOffset);
        }
    }
}
