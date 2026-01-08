package edu.yeditepe.cse471.p2p.network;

import edu.yeditepe.cse471.p2p.model.SharedFile;

import java.util.ArrayList;
import java.util.List;

public final class ControlMessages {
    private ControlMessages() {}

    public static class ControlRequest {
        public String type;
    }

    public static class CatalogRequest extends ControlRequest {
        public CatalogRequest() { this.type = "CATALOG_REQUEST"; }
    }

    public static class CatalogResponse {
        public String type = "CATALOG_RESPONSE";
        public List<SharedFile> files = new ArrayList<>();
    }

    public static class ChunkRequest extends ControlRequest {
        public String contentHash;
        public int chunkIndex;

        public ChunkRequest() { this.type = "CHUNK_REQUEST"; }
    }

    public static class ChunkResponseHeader {
        public String type = "CHUNK_RESPONSE";
        public String contentHash;
        public int chunkIndex;
        public int lengthBytes;
        public long fileSizeBytes;
        public int numChunks;
    }

    public static class ErrorResponse {
        public String type = "ERROR";
        public String message;
    }
}
