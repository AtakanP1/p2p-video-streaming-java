package edu.yeditepe.cse471.p2p.streaming;

public final class Chunker {
    private Chunker() {}


    public static final int CHUNK_SIZE = 256 * 1024;

    public static int numChunks(long fileSizeBytes) {
        if (fileSizeBytes <= 0) return 0;
        return (int) ((fileSizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE);
    }

    public static long chunkOffset(int chunkIndex) {
        return (long) chunkIndex * CHUNK_SIZE;
    }
}
