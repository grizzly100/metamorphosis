package org.grizzlytech.metamorphosis;

import org.grizzlytech.metamorphosis.util.MD5Checksum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Metadata associated with a media file
 */
public class FileInfo implements Comparable<FileInfo> {
    private static final Logger LOG = LoggerFactory.getLogger(FileInfo.class);

    /**
     * The file who metadata will be examined
     */
    private File sourceFile;

    /**
     * Original date/time text media was taken
     */
    private Instant dateTaken;

    private long fileLength = -1;

    private String md5Checksum = null;

    /**
     * Relative position of the file (post sorting)
     */
    private int position;

    public FileInfo(File sourceFile) {
        setSourceFile(sourceFile);
    }

    // Getters and Setters

    public File getSourceFile() {
        return this.sourceFile;
    }

    public void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
        this.dateTaken = FileMetadata.getDateTakenElseDefault(sourceFile);
        this.fileLength = this.sourceFile.length();
    }

    public String getSourceFileName() {
        return this.sourceFile.getName();
    }

    public Instant getDateTaken() {
        return this.dateTaken;
    }

    public long getFileLength() {
        return this.fileLength;
    }

    public String getMD5Checksum() {
        if ((this.md5Checksum == null) && (getFileLength() > 0)) {
            this.md5Checksum = MD5Checksum.getMD5Checksum(getSourceFile());
        }
        return md5Checksum;
    }

    public String getLocalDateAsText() {
        final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault());
        return FORMAT.format(this.dateTaken);
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        String sb = getClass().getName() + "{" + "sourceFile=" + sourceFile +
                ", dateTaken=" + getDateTaken() + '\'' +
                ", position=" + position +
                '}';
        return sb;
    }

    @Override
    public int compareTo(FileInfo o) {
        if (o == this) {
            return 0;
        } else if (this.getDateTaken() == null) {
            return -1;
        } else if (o.getDateTaken() == null) {
            return 1;
        } else if (this.getDateTaken().equals(o.getDateTaken())) {
            // Sort on source filename for photos taken during the same second
            return this.getSourceFileName().compareTo(o.getSourceFileName());
        } else {
            // Sort on the timestamp
            return this.getDateTaken().compareTo(o.getDateTaken());
        }
    }

    /**
     * Return the relative positional name of the file
     *
     * @param prefix      for example, "IMG"
     * @param includeDate to include YYYYMMDD date (if successfully parsed)
     * @return the relative file name
     */
    public String getRelativeName(String prefix, boolean includeDate) {
        final String DELIMITER = "_";
        StringBuilder builder = new StringBuilder();
        if (prefix != null) {
            builder.append(prefix);
            builder.append(DELIMITER);
        }
        if (includeDate && this.dateTaken != null) {
            builder.append(getLocalDateAsText());
            builder.append(DELIMITER);
        }
        builder.append(position);
        builder.append(FileMetadata.getExtension(this.sourceFile));
        return builder.toString();
    }
}
