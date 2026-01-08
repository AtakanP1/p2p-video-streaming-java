package edu.yeditepe.cse471.p2p.network;

import edu.yeditepe.cse471.p2p.model.Peer;
import edu.yeditepe.cse471.p2p.util.AppConfig;
import edu.yeditepe.cse471.p2p.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;


public class DiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);

    private final PeerRegistry registry;
    private final Consumer<Peer> onNewPeer;

    private MulticastSocket socket;
    private InetAddress group;
    private volatile boolean running;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private Future<?> listenerFuture;


    private final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();

    public interface Consumer<T> {
        void accept(T t);
    }

    public DiscoveryService(PeerRegistry registry, Consumer<Peer> onNewPeer) {
        this.registry = registry;
        this.onNewPeer = onNewPeer;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        running = true;

        group = InetAddress.getByName(AppConfig.discoveryGroup());
        InetSocketAddress addr =
        new InetSocketAddress(AppConfig.discoveryPort());

MulticastSocket ms = new MulticastSocket(null);
ms.setReuseAddress(true);
ms.bind(addr);

socket = ms;
        socket.setReuseAddress(true);
        socket.setTimeToLive(AppConfig.discoveryTtl()); //scope sınırı
        socket.joinGroup(group);

        listenerFuture = scheduler.submit(this::listenLoop);
        scheduler.scheduleAtFixedRate(this::safeAnnounce, 0,
                AppConfig.discoveryAnnounceIntervalMs(), TimeUnit.MILLISECONDS);

        log.info("Discovery started on {}:{} TTL={}", group.getHostAddress(), AppConfig.discoveryPort(), AppConfig.discoveryTtl());
    }

    public synchronized void stop() {
        running = false;
        if (listenerFuture != null) listenerFuture.cancel(true);
        scheduler.shutdownNow();
        if (socket != null) {
            try { socket.leaveGroup(group); } catch (Exception ignored) {}
            try { socket.close(); } catch (Exception ignored) {}
        }
        log.info("Discovery stopped");
    }

    private void safeAnnounce() {
        try {
            announce();
        } catch (Exception e) {
            log.warn("Announce failed: {}", e.toString());
        }
    }

    public void announce() throws IOException {
        DiscoveryMessage msg = new DiscoveryMessage();
        msg.type = "ANNOUNCE";
        msg.messageId = UUID.randomUUID().toString();
        msg.peerId = AppConfig.PEER_ID;
        msg.peerName = AppConfig.peerName();
        msg.controlPort = AppConfig.controlPort();
        msg.epochMs = System.currentTimeMillis();

        byte[] data = JsonUtil.MAPPER.writeValueAsBytes(msg);
        DatagramPacket pkt = new DatagramPacket(data, data.length, group, AppConfig.discoveryPort());
        socket.send(pkt);
    }

    private void listenLoop() {
        byte[] buf = new byte[64 * 1024];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);

        while (running) {
            try {
                socket.receive(pkt);
                String json = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage msg = JsonUtil.MAPPER.readValue(json, DiscoveryMessage.class);
                if (msg.peerId == null || msg.messageId == null) continue;

                if (!seenMessageIds.add(msg.messageId)) continue;
                if (AppConfig.PEER_ID.equals(msg.peerId)) continue; // ignore self

                Peer peer = new Peer(msg.peerId, msg.peerName,
                        pkt.getAddress(), msg.controlPort);
                peer.lastSeen = Instant.now();

                boolean isNew = registry.peers().stream().noneMatch(p -> p.peerId.equals(peer.peerId));
                registry.upsertPeer(peer);

                if (isNew && onNewPeer != null) {
                    onNewPeer.accept(peer);
                }

            } catch (SocketException se) {
                if (running) log.warn("Socket error: {}", se.toString());
            } catch (Exception e) {
                log.warn("Discovery parse error: {}", e.toString());
            }
        }
    }
}
