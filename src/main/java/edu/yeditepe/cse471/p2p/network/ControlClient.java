package edu.yeditepe.cse471.p2p.network;

import edu.yeditepe.cse471.p2p.model.Peer;
import edu.yeditepe.cse471.p2p.model.SharedFile;
import edu.yeditepe.cse471.p2p.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static edu.yeditepe.cse471.p2p.network.ControlMessages.*;

public class ControlClient {
    private static final Logger log = LoggerFactory.getLogger(ControlClient.class);

    public List<SharedFile> requestCatalog(Peer peer, int timeoutMs) throws IOException {
        CatalogRequest req = new CatalogRequest();
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(peer.address, peer.controlPort), timeoutMs);
            sock.setSoTimeout(timeoutMs);

            OutputStream out = new BufferedOutputStream(sock.getOutputStream());
            InputStream in = new BufferedInputStream(sock.getInputStream());

            writeJsonLine(out, req);
            String line = readLine(in, 1024 * 1024);
            if (line == null) throw new IOException("No response");

            // Peek at response type.
            var node = JsonUtil.MAPPER.readTree(line);
            String type = node.has("type") ? node.get("type").asText() : "";
            if ("CATALOG_RESPONSE".equals(type)) {
                CatalogResponse resp = JsonUtil.MAPPER.treeToValue(node, CatalogResponse.class);
                return resp.files;
            }
            if ("ERROR".equals(type)) {
                ErrorResponse er = JsonUtil.MAPPER.treeToValue(node, ErrorResponse.class);
                throw new IOException(er.message);
            }
            throw new IOException("Unexpected response: " + type);
        }
    }

    public ChunkPayload requestChunk(Peer peer, String contentHash, int chunkIndex, int timeoutMs) throws IOException {
        ChunkRequest req = new ChunkRequest();
        req.contentHash = contentHash;
        req.chunkIndex = chunkIndex;

        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(peer.address, peer.controlPort), timeoutMs);
            sock.setSoTimeout(timeoutMs);

            OutputStream out = new BufferedOutputStream(sock.getOutputStream());
            InputStream in = new BufferedInputStream(sock.getInputStream());

            writeJsonLine(out, req);
            String line = readLine(in, 64 * 1024);
            if (line == null) throw new IOException("No chunk header");

            var node = JsonUtil.MAPPER.readTree(line);
            String type = node.has("type") ? node.get("type").asText() : "";
            if ("ERROR".equals(type)) {
                ErrorResponse er = JsonUtil.MAPPER.treeToValue(node, ErrorResponse.class);
                throw new IOException(er.message);
            }
            if (!"CHUNK_RESPONSE".equals(type)) {
                throw new IOException("Unexpected chunk response: " + type);
            }

            ChunkResponseHeader hdr = JsonUtil.MAPPER.treeToValue(node, ChunkResponseHeader.class);
            byte[] data = in.readNBytes(hdr.lengthBytes);
            if (data.length != hdr.lengthBytes) {
                throw new IOException("Incomplete chunk: expected=" + hdr.lengthBytes + " got=" + data.length);
            }
            return new ChunkPayload(hdr, data);
        }
    }

    public record ChunkPayload(ChunkResponseHeader header, byte[] bytes) {}

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
        return bos.toString(StandardCharsets.UTF_8);
    }
}
