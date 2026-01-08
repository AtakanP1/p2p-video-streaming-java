package edu.yeditepe.cse471.p2p.network;

import edu.yeditepe.cse471.p2p.model.SharedFile;
import edu.yeditepe.cse471.p2p.network.ControlMessages.CatalogResponse;
import edu.yeditepe.cse471.p2p.network.ControlMessages.ChunkRequest;
import edu.yeditepe.cse471.p2p.network.ControlMessages.ChunkResponseHeader;
import edu.yeditepe.cse471.p2p.network.ControlMessages.ControlRequest;
import edu.yeditepe.cse471.p2p.network.ControlMessages.ErrorResponse;
import edu.yeditepe.cse471.p2p.streaming.Chunker;
import edu.yeditepe.cse471.p2p.util.AppConfig;
import edu.yeditepe.cse471.p2p.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static edu.yeditepe.cse471.p2p.network.ControlMessages.*;

public class ControlServer {
    private static final Logger log = LoggerFactory.getLogger(ControlServer.class);

    private final ExecutorService pool = Executors.newCachedThreadPool();

    private volatile boolean running;
    private ServerSocket serverSocket;

    private final java.util.function.Supplier<Collection<SharedFile>> localCatalogSupplier;
    private final java.util.function.Function<String, SharedFile> localFileByHash;

    public ControlServer(
            java.util.function.Supplier<Collection<SharedFile>> localCatalogSupplier,
            java.util.function.Function<String, SharedFile> localFileByHash
    ) {
        this.localCatalogSupplier = localCatalogSupplier;
        this.localFileByHash = localFileByHash;
    }

    public synchronized void start() throws IOException {
    if (running) return;

    ServerSocket ss = null;
    try {
        ss = new ServerSocket(AppConfig.controlPort());
        serverSocket = ss;
        running = true;
        pool.submit(this::acceptLoop);
        log.info("Control server listening on port {}", AppConfig.controlPort());
    } catch (IOException e) {
        running = false;
        if (ss != null) {
            try { ss.close(); } catch (IOException ignored) {}
   }
        throw e;
    }
}


    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket sock = serverSocket.accept();
                pool.submit(() -> handle(sock));
            } catch (IOException e) {
                if (running) log.warn("Accept error: {}", e.toString());
            }
        }
    }

    private void handle(Socket sock) {
        try (sock;
             InputStream in = new BufferedInputStream(sock.getInputStream());
             OutputStream out = new BufferedOutputStream(sock.getOutputStream())) {

            // Read a single JSON line.
            String line = readLine(in, 64 * 1024);
            if (line == null) return;

            ControlRequest base = JsonUtil.MAPPER.readValue(line, ControlRequest.class);
            if (base.type == null) return;

            switch (base.type) {
                case "CATALOG_REQUEST" -> {
                    CatalogResponse resp = new CatalogResponse();
                    resp.files.addAll(localCatalogSupplier.get());
                    writeJsonLine(out, resp);
                }
                case "CHUNK_REQUEST" -> {
                    ChunkRequest req = JsonUtil.MAPPER.readValue(line, ChunkRequest.class);
                    SharedFile f = localFileByHash.apply(req.contentHash);
                    if (f == null || f.localPath == null) {
                        ErrorResponse er = new ErrorResponse();
                        er.message = "File not found: " + req.contentHash;
                        writeJsonLine(out, er);
                        return;
                    }

                    byte[] chunk = readChunk(f.localPath, req.chunkIndex, f.sizeBytes);
                    ChunkResponseHeader hdr = new ChunkResponseHeader();
                    hdr.contentHash = f.contentHash;
                    hdr.chunkIndex = req.chunkIndex;
                    hdr.lengthBytes = chunk.length;
                    hdr.fileSizeBytes = f.sizeBytes;
                    hdr.numChunks = f.numChunks;
                    writeJsonLine(out, hdr);
                    out.write(chunk);
                    out.flush();
                }
                default -> {
                    ErrorResponse er = new ErrorResponse();
                    er.message = "Unknown request type: " + base.type;
                    writeJsonLine(out, er);
                }
            }

        } catch (Exception e) {
            log.warn("Control handler error: {}", e.toString());
        }
    }

    private static void writeJsonLine(OutputStream out, Object obj) throws IOException {
        byte[] json = JsonUtil.MAPPER.writeValueAsBytes(obj);
        out.write(json);
        out.write('\n');
        out.flush();
    }

    private static String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            bos.write(b);
            if (bos.size() > maxBytes) throw new IOException("Line too long");
        }
        if (bos.size() == 0 && b == -1) return null;
        return bos.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] readChunk(Path path, int chunkIndex, long fileSizeBytes) throws IOException {
        long offset = Chunker.chunkOffset(chunkIndex);
        if (offset < 0 || offset >= fileSizeBytes) return new byte[0];
        int max = (int) Math.min(Chunker.CHUNK_SIZE, fileSizeBytes - offset);
        byte[] buf = new byte[max];
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(offset);
            raf.readFully(buf);
        }
        return buf;
    }
}
