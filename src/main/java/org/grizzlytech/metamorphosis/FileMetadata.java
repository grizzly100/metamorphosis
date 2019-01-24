package org.grizzlytech.metamorphosis;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.ByteArrayReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifReader;
import com.drew.metadata.file.FileSystemDirectory;
import com.drew.metadata.icc.IccDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.nokia.heif.*;
import com.nokia.heif.Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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


    public static Instant getDateTaken(File file) {
        Instant dateTaken = null;
        String ext = FileMetadata.getExtension(file).toUpperCase();

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

            case ".HEIC":
                dateTaken = getHEICDateTaken(file);
                break;

            default:
                LOG.error("Cannot map for {}", ext);
        }

        if (dateTaken == null) {
            // Dump all the metadata in the case of extraction failure
            LOG.error("Exif extraction failed for {}", file.getAbsolutePath());
            FileMetadata.dump(file);
            dateTaken = getFileDate(file, FILE_CREATION_TIME);
        }

        return dateTaken;
    }


    public static Instant getDate(Metadata metadata, Class<? extends Directory> directoryType, int tag) {
        Date dt = null;
        for (Directory directory : metadata.getDirectoriesOfType(directoryType)) {
            dt = directory.getDate(tag);
            break;
        }
        return (dt != null) ? dt.toInstant() : null;
    }

    public static Instant getJPGDateTaken(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            return getDate(metadata, ExifIFD0Directory.class, ExifDirectoryBase.TAG_DATETIME);
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("getJPGDateTaken: {}", ex);
        }
        return null;
    }

    public static Instant getQTDateTaken(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            return getDate(metadata, QuickTimeDirectory.class, QuickTimeDirectory.TAG_CREATION_TIME);
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("getQTDateTaken: {}", ex);
        }
        return null;
    }

    public static Instant getPNGDateTaken(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            return getDate(metadata, IccDirectory.class, IccDirectory.TAG_PROFILE_DATETIME);
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("getPNGDateTaken: {}", ex);
        }
        return null;
    }

    /**
     * Find the data time from an HEIC file by examining the primary image
     * <p>
     * Locating the primary image and extracting the EXIF byte array is not directly supported by Drew Noakes library.
     * We use the Nokia HEIF reference implementation to perform this task
     *
     * @param file
     * @return
     */
    public static Instant getHEICDateTaken(File file) {
        Instant date = null;
        try {
            HEIF heif = new HEIF();

            heif.load(file.getAbsolutePath());
            ImageItem primaryImage = heif.getPrimaryImage();

            ExifItem ex = null;
            for (MetaItem item : primaryImage.getMetadatas()) {
                if (item instanceof ExifItem) {
                    ex = (ExifItem) item;
                    break;
                }
            }
            if (ex != null) {
                byte[] dt = ex.getDataAsArray();
                Metadata metadata = new Metadata();
                final int HEIF_EXIF_PREAMBLE_LEN = 4;
                new ExifReader().extract(new ByteArrayReader(dt), metadata,
                        HEIF_EXIF_PREAMBLE_LEN + ExifReader.JPEG_SEGMENT_PREAMBLE.length(),
                        null);

                date = getDate(metadata, ExifIFD0Directory.class, ExifDirectoryBase.TAG_DATETIME);
            } else {
                LOG.error("Unable to find exif");
            }
        } catch (Exception e) {
            LOG.error("HEIF {}", e);
        }
        return date;
    }

    public static void dump(File file) {
        LOG.info("Dump {}", file);
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            dump(metadata);
        } catch (IOException | ImageProcessingException ex) {
            LOG.error("Ouch: ", ex);
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
