package org.grizzlytech.metamorphosis;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.icc.IccDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import org.grizzlytech.metamorphosis.imaging.heif.HEIFMetadataReader;
import org.grizzlytech.metamorphosis.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;

/**
 * Utility functions for examining metadata in media files
 */
public class FileMetadata {

    private static final Logger LOG = LoggerFactory.getLogger(FileMetadata.class);

    public static final String FILE_CREATION_TIME = "creationTime";
    public static final String FILE_LAST_MODIFIED_TIME = "lastModifiedTime";

    public static final List<String> SUPPORTED_FORMATS = Arrays.asList(".JPG", ".HEIC", ".MOV", ".MP4", ".PNG");

    public static final Predicate<File> IS_SUPPORTED = f ->
            SUPPORTED_FORMATS.contains(FileMetadata.getExtension(f).toUpperCase());

    public static String getExtension(File file) {
        String path = file.getAbsolutePath();
        return path.substring(path.lastIndexOf("."));
    }

    // File Metadata

    public static Instant getFileDate(File file, String attributeName) {
        Instant date = null;
        try {
            FileTime ft = (FileTime) Files.getAttribute(file.toPath(), attributeName);
            date = ft.toInstant();
        } catch (IOException ex) {
            LOG.error("getFileDate file:[{}] attribute:[{}] error:[{}]", file.getAbsolutePath(), attributeName, ex);
        }
        return date;
    }

    public static void setFileDate(File file, Instant dateTime) {
        try {
            FileTime ft = FileTime.from(dateTime);
            Path path = file.toPath();
            Files.setAttribute(path, FILE_CREATION_TIME, ft);
            Files.setAttribute(path, FILE_LAST_MODIFIED_TIME, ft);
        } catch (IOException ex) {
            LOG.error("setFileDate file:[{}] error:[{}]", file.getAbsolutePath(), ex);
        }
    }


    public static Instant getDateTakenElseDefault(File file) {
        Instant dateTaken = getDateTaken(file);
        if (dateTaken == null) {
            dateTaken = TimeUtil.earliest(getFileDate(file, FILE_CREATION_TIME), getFileDate(file, FILE_LAST_MODIFIED_TIME));
            LOG.info("FALLING BACK TO EARLIEST FILE TIME [{}]", dateTaken);
        }
        return dateTaken;
    }

    public static Instant getDateTaken(File file) {
        Instant dateTaken = null;
        String ext = FileMetadata.getExtension(file).toUpperCase();

        try {
            switch (ext) {
                case ".JPG":
                case ".JPEG":
                    dateTaken = getJPGDateTaken(file);
                    break;

                case ".PNG":
                    dateTaken = getPNGDateTaken(file);
                    break;

                case ".MOV":
                    dateTaken = getQTDateTaken(file);
                    break;

                case ".MP4":
                    dateTaken = getMP4DateTaken(file);
                    break;

                case ".HEIC":
                    dateTaken = getHEIFDateTaken(file);
                    break;

                default:
                    LOG.error("Unsupported file extension {}", ext);
            }

            if (dateTaken == null) {
                LOG.error("(non-exception) problem parsing metadata in {}", file.getAbsolutePath());
            }
        } catch (Exception ex) {
            LOG.error("Exception parsing metadata in {}", file.getAbsolutePath(), ex);
        }

        return dateTaken;
    }


    private static Instant getDate(Metadata metadata, Class<? extends Directory> directoryType, int tag) {
        Date dt = null;
        for (Directory directory : metadata.getDirectoriesOfType(directoryType)) {
            dt = directory.getDate(tag);
            break;
        }
        return (dt != null) ? dt.toInstant() : null;
    }

    private static Instant getJPGDateTaken(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);

            // DateTime is always specified (this appears to be in the "wall time" when movie taken)
            Instant creationDate = getDate(metadata, ExifIFD0Directory.class, ExifDirectoryBase.TAG_DATETIME);
            // Optionally there is sometimes an original date
            Instant originalDate = getDate(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
            // Select the earliest
            return TimeUtil.correctIfAlternativeMateriallyEarlier(creationDate, originalDate, file.getName());
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("getJPGDateTaken: {}", ex);
        }
        return null;
    }

    private static Instant getQTDateTaken(File file) {
        /**
         * For some reason Drew has not added a public TAG for this attribute in QuickTimeMetadataDirectory
         * See _tagIntegerMap.put("com.apple.quicktime.creationdate", 0x0506);
         */
        final int TAG_QUICKTIME_CREATIONDATE = 0x0506; // 1286
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);

            // Creation time is always specified (this appears to be in the "wall time" when movie taken)
            Instant creationTime = getDate(metadata, QuickTimeDirectory.class, QuickTimeDirectory.TAG_CREATION_TIME);

            // Optionally there is sometimes a creation date
            Instant creationDate = getDate(metadata, QuickTimeMetadataDirectory.class, TAG_QUICKTIME_CREATIONDATE);
            // This is useful, but local time seems to be mis-recorded as UTC
            if (creationDate != null) { // unfortunately we guess the timezone to be the system default
                creationDate = TimeUtil.correctZoneOffset(creationDate, ZoneId.systemDefault());
            }

            // Select the earliest (creationTime is assumed, but creationDate is an alternative)
            return TimeUtil.correctIfAlternativeMateriallyEarlier(creationTime, creationDate, file.getName());
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("getQTDateTaken: {}", ex);
        }
        return null;
    }

    private static Instant getMP4DateTaken(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            return getDate(metadata, Mp4Directory.class, Mp4Directory.TAG_CREATION_TIME);
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("getMP4DateTaken: {}", ex);
        }
        return null;
    }

    private static Instant getPNGDateTaken(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            return getDate(metadata, IccDirectory.class, IccDirectory.TAG_PROFILE_DATETIME);
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("getPNGDateTaken: {}", ex);
        }
        return null;
    }

    private static Instant getHEIFDateTaken(File file) {
        try {
            Metadata metadata = HEIFMetadataReader.readMetadata(file);
            return getDate(metadata, ExifIFD0Directory.class, ExifDirectoryBase.TAG_DATETIME);
        } catch (ImageProcessingException ex) {
            LOG.error("getHEIFDateTaken: {}", ex);
        }
        return null;
    }

    public static void dump(File file) {
        LOG.info("Dump {}", file);
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            dump(metadata);
        } catch (Exception ex) {
            LOG.error("Failed to dump {}", file.getAbsolutePath(), ex);
        }
    }

    /**
     * Dump all metadata attributes
     *
     * @param metadata
     */
    public static void dump(Metadata metadata) {
        for (Directory directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {

                String value = String.format("[%s] - %s - %s",
                        directory.getName(), tag.getTagType(), tag.getDescription());
                LOG.info("{}", value);
            }
            if (directory.hasErrors()) {
                for (String error : directory.getErrors()) {
                    System.err.format("ERROR: %s", error);
                }
            }
        }
    }

}
