package edu.yeditepe.cse471.p2p.model;

import java.util.ArrayList;
import java.util.List;

public class AvailableVideo {
    public String contentHash;
    public String displayName;
    public long sizeBytes;
    public int numChunks;
    public final List<Peer> sources = new ArrayList<>();

    public String uiLabel() {
        String shortHash = contentHash == null ? "" : contentHash.substring(0, Math.min(10, contentHash.length()));
        return displayName + "  [" + shortHash + "]  (sources: " + sources.size() + ")";
    }
}
