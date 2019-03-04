package org.grizzlytech.metamorphosis.util;

import org.grizzlytech.metamorphosis.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Create MD5 checksum for a file
 */
public class MD5Checksum {

    private static final Logger LOG = LoggerFactory.getLogger(MD5Checksum.class);

    public static byte[] createChecksum(File file) throws IOException, NoSuchAlgorithmException {
        InputStream fis = new FileInputStream(file);

        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance("MD5");
        int numRead;

        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);

        fis.close();
        return complete.digest();
    }

    public static String getMD5Checksum(File file) {
        String result = "";

        try {
            byte[] b = createChecksum(file);

            for (int i = 0; i < b.length; i++) {
                result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
            }
        } catch (NoSuchAlgorithmException | IOException ex) {
            LOG.error("Error building checksum for {}", file.getAbsolutePath(), ex);
        }
        return result;
    }
}