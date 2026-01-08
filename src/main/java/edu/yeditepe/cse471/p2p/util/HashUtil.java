package edu.yeditepe.cse471.p2p.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

public final class HashUtil {
    private HashUtil() {}

    public static String sha256Hex(Path file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (read == 0) continue;
                md.update(buf, 0, read);
            }
        }

        return toHex(md.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
