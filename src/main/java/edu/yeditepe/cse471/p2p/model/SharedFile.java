package edu.yeditepe.cse471.p2p.model;

import edu.yeditepe.cse471.p2p.streaming.Chunker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class SharedFile {
    public String contentHash;     // SHA-256
    public long sizeBytes;
    public int numChunks;
    public List<String> names = new ArrayList<>();

    // Local-only fields
    public transient Path localPath;

    public SharedFile() {}

    public SharedFile(String contentHash, long sizeBytes, Path localPath, String displayName) {
        this.contentHash = contentHash;
        this.sizeBytes = sizeBytes;
        this.numChunks = Chunker.numChunks(sizeBytes);
        this.localPath = localPath;
        this.names.add(displayName);
    }

    public String primaryName() {
        return names.isEmpty() ? contentHash.substring(0, 12) : names.get(0);
    }
}
