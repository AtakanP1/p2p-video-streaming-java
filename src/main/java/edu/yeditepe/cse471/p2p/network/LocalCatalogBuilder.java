package edu.yeditepe.cse471.p2p.network;

import edu.yeditepe.cse471.p2p.model.SharedFile;
import edu.yeditepe.cse471.p2p.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LocalCatalogBuilder {
    private static final Logger log = LoggerFactory.getLogger(LocalCatalogBuilder.class);

    private static final Set<String> VIDEO_EXTS = Set.of(
            "mp4", "mkv", "avi", "mov", "m4v", "webm"
    );

    public Collection<SharedFile> build(Path root) throws IOException {
        if (root == null) return List.of();
        if (!Files.isDirectory(root)) return List.of();

        Map<String, SharedFile> byHash = new HashMap<>();

        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(this::isVideo)
                .forEach(p -> {
                    try {
                        long size = Files.size(p);
                        String hash = HashUtil.sha256Hex(p);
                        String name = p.getFileName().toString();

                        SharedFile existing = byHash.get(hash);
                        if (existing == null) {
                            SharedFile f = new SharedFile(hash, size, p, name);
                            byHash.put(hash, f);
                        } else {
                            if (!existing.names.contains(name)) existing.names.add(name);
                            // Prefer a stable localPath (first one found)
                        }
                    } catch (Exception e) {
                        log.warn("Catalog hash failed for {}: {}", p, e.toString());
                    }
                });
        }

        return byHash.values();
    }

    private boolean isVideo(Path p) {
        String s = p.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = s.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = s.substring(dot + 1);
        return VIDEO_EXTS.contains(ext);
    }
}
