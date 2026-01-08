package edu.yeditepe.cse471.p2p.streaming;

import edu.yeditepe.cse471.p2p.model.Peer;
import edu.yeditepe.cse471.p2p.network.ControlClient;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamSession {
    public interface Listener {
        void onLog(String msg);
        void onPeerUpdate(Peer peer, int chunkIndex, double progress01, String status);
        void onGlobalBuffer(double progress01, int contiguousChunks);
        void onReadyToPlay(Path localFile);
        void onCompleted(Path localFile);
    }

    private final String contentHash;
    private final String displayName;
    private final List<Peer> sources;
    private final long fileSizeBytes;
    private final int numChunks;
    private final Path outputFile;
    private final Listener listener;

    private final ControlClient client = new ControlClient();
    private final ExecutorService pool;
    private final AtomicInteger downloadedChunks = new AtomicInteger(0);
    private final BitSet received;
    private final int[] attempts;

    private final AtomicBoolean startedPlayback = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    // Buffer/playback rule: start when we have N contiguous chunks from start.
    private final int playThresholdChunks;

    public StreamSession(String contentHash,
                         String displayName,
                         List<Peer> sources,
                         long fileSizeBytes,
                         int numChunks,
                         Path outputFile,
                         int playThresholdChunks,
                         Listener listener) {
        this.contentHash = contentHash;
        this.displayName = displayName;
        this.sources = List.copyOf(sources);
        this.fileSizeBytes = fileSizeBytes;
        this.numChunks = numChunks;
        this.outputFile = outputFile;
        this.playThresholdChunks = Math.max(1, playThresholdChunks);
        this.listener = listener;

        this.pool = Executors.newFixedThreadPool(Math.max(2, Math.min(8, sources.size())));
        this.received = new BitSet(numChunks);
        this.attempts = new int[numChunks];
    }

    public void start() throws IOException {
        if (sources.isEmpty()) {
            log("No sources available for " + displayName);
            return;
        }

        Files.createDirectories(outputFile.getParent());
        try (RandomAccessFile raf = new RandomAccessFile(outputFile.toFile(), "rw")) {
            raf.setLength(fileSizeBytes);
        }

        log("Streaming started: " + displayName + " chunks=" + numChunks + " size=" + fileSizeBytes);

        for (int i = 0; i < numChunks; i++) {
            int chunkIndex = i;
            pool.submit(() -> downloadOne(chunkIndex));
        }

        pool.submit(this::monitorLoop);
    }

    public void stop() {
        stopped.set(true);
        pool.shutdownNow();
    }

    private void downloadOne(int chunkIndex) {
        if (stopped.get()) return;

        synchronized (received) {
            if (received.get(chunkIndex)) return; // duplicate
        }

        // Round-robin peer selection, with retry.
        int attempt = attempts[chunkIndex]++;
        Peer peer = sources.get(Math.floorMod(chunkIndex + attempt, sources.size()));

        try {
            listener.onPeerUpdate(peer, chunkIndex, progress01(), "Requesting");
            var payload = client.requestChunk(peer, contentHash, chunkIndex, 4000);
            byte[] data = payload.bytes();

            // Write chunk at correct offset (out-of-order allowed).
            long offset = Chunker.chunkOffset(chunkIndex);
            try (RandomAccessFile raf = new RandomAccessFile(outputFile.toFile(), "rw")) {
                raf.seek(offset);
                raf.write(data);
            }

            boolean wasNew;
            synchronized (received) {
                wasNew = !received.get(chunkIndex);
                if (wasNew) received.set(chunkIndex);
            }

            if (wasNew) {
                int done = downloadedChunks.incrementAndGet();
                listener.onPeerUpdate(peer, chunkIndex, progress01(), "OK");

                // Update global buffer status.
                int contiguous = contiguousFromStart();
                listener.onGlobalBuffer(done / (double) numChunks, contiguous);

                // Start playback when buffer threshold met.
                if (!startedPlayback.get() && contiguous >= playThresholdChunks) {
                    if (startedPlayback.compareAndSet(false, true)) {
                        listener.onReadyToPlay(outputFile);
                    }
                }

                if (done == numChunks) {
                    listener.onCompleted(outputFile);
                    stop();
                }
            }

        } catch (Exception e) {
            listener.onPeerUpdate(peer, chunkIndex, progress01(), "Retry");

            // Re-submit later if not received yet.
            if (!stopped.get()) {
                sleepQuiet(150 + Math.min(2000, attempts[chunkIndex] * 200));
                downloadOne(chunkIndex);
            }
        }
    }

    private void monitorLoop() {
        while (!stopped.get()) {
            sleepQuiet(500);
            int contiguous = contiguousFromStart();
            listener.onGlobalBuffer(progress01(), contiguous);
        }
    }

    private double progress01() {
        return numChunks == 0 ? 0.0 : downloadedChunks.get() / (double) numChunks;
    }

    private int contiguousFromStart() {
        synchronized (received) {
            int i = 0;
            while (i < numChunks && received.get(i)) i++;
            return i;
        }
    }

    private void log(String msg) {
        if (listener != null) listener.onLog(msg);
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
