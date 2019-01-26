package org.grizzlytech.metamorphosis;

import com.drew.imaging.ImageProcessingException;
import com.drew.lang.ByteArrayReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifReader;
import com.nokia.heif.ExifItem;
import com.nokia.heif.HEIF;
import com.nokia.heif.ImageItem;
import com.nokia.heif.MetaItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Read metadata from HEIC images. Class uses Nokia HEIC API
 */
public class HEICMetadataReader {

    private static final Logger LOG = LoggerFactory.getLogger(HEICMetadataReader.class);

    /**
     * EXIF metadata preamble (See ExifReader.JPEG_SEGMENT_PREAMBLE) is prefixed by this
     */
    private static final byte[] PREAMBLE = {0, 0, 0, 6};

    private static final int JPEG_SEGMENT_PREAMBLE_OFFSET = PREAMBLE.length + ExifReader.JPEG_SEGMENT_PREAMBLE.length();

    /**
     * Locating the primary image and extracting the EXIF byte array is not directly supported by Drew Noakes library.
     * We use the Nokia HEIF reference implementation to perform this task
     *
     * @param file
     * @return
     */
    public static Metadata readMetadata(File file)
            throws ImageProcessingException {
        // Read metadata into byte array
        byte[] data = readExifMetadataAsBytes(file.getAbsolutePath());

        // Read the Exif byte array into the Metadata object using the ExifReader helper class
        Metadata metadata = new Metadata();
        new ExifReader().extract(new ByteArrayReader(data), metadata, JPEG_SEGMENT_PREAMBLE_OFFSET, null);

        return metadata;
    }

    private static byte[] readExifMetadataAsBytes(String filename)
            throws ImageProcessingException {
        try {
            // Obtain the primary image within the HEIC image set
            HEIF heif = new HEIF();
            heif.load(filename);
            ImageItem primaryImage = heif.getPrimaryImage();
            if (primaryImage == null) {
                throw new ImageProcessingException("Unable to locate primaryImage for " + filename);
            }

            // Obtain the Exif metadata from the image
            ExifItem exifItem = null;
            for (MetaItem item : primaryImage.getMetadatas()) {
                if (item instanceof ExifItem) {
                    exifItem = (ExifItem) item;
                    break;
                }
            }
            if (exifItem == null) {
                throw new ImageProcessingException("Unable to locate ExifItem metadata for " + filename);
            }

            // Return the data as a byte array
            return exifItem.getDataAsArray();

        } catch (com.nokia.heif.Exception ex) {
            throw new ImageProcessingException(ex.getMessage(), ex);
        }
    }
}
