package edu.yeditepe.cse471.p2p.network;

import edu.yeditepe.cse471.p2p.model.AvailableVideo;
import edu.yeditepe.cse471.p2p.model.Peer;
import edu.yeditepe.cse471.p2p.model.SharedFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PeerRegistry {
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, SharedFile>> catalogsByPeerId = new ConcurrentHashMap<>();
    private final Map<String, SharedFile> localCatalog = new ConcurrentHashMap<>();

    public void upsertPeer(Peer peer) {
        peers.put(peer.peerId, peer);
    }

    public void removePeer(String peerId) {
        peers.remove(peerId);
        catalogsByPeerId.remove(peerId);
    }

    public Collection<Peer> peers() {
        return peers.values();
    }

    public void setPeerCatalog(String peerId, List<SharedFile> files) {
        Map<String, SharedFile> map = new HashMap<>();
        for (SharedFile f : files) {
            map.put(f.contentHash, f);
        }
        catalogsByPeerId.put(peerId, map);
    }

    public void setLocalCatalog(Collection<SharedFile> files) {
        localCatalog.clear();
        for (SharedFile f : files) {
            localCatalog.put(f.contentHash, f);
        }
    }

    public Optional<SharedFile> localFileByHash(String hash) {
        return Optional.ofNullable(localCatalog.get(hash));
    }

    public List<Peer> sourcesForHash(String hash) {
        List<Peer> res = new ArrayList<>();
        for (var entry : catalogsByPeerId.entrySet()) {
            if (entry.getValue().containsKey(hash)) {
                Peer p = peers.get(entry.getKey());
                if (p != null) res.add(p);
            }
        }
        
        return res;
    }

    public List<AvailableVideo> search(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();

     
        Map<String, AvailableVideo> agg = new LinkedHashMap<>();

       
        for (var peerEntry : catalogsByPeerId.entrySet()) {
            Peer peer = peers.get(peerEntry.getKey());
            if (peer == null) continue;
            for (SharedFile f : peerEntry.getValue().values()) {
                String name = f.primaryName();
                if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;

                AvailableVideo v = agg.computeIfAbsent(f.contentHash, h -> {
                    AvailableVideo av = new AvailableVideo();
                    av.contentHash = f.contentHash;
                    av.displayName = name;
                    av.sizeBytes = f.sizeBytes;
                    av.numChunks = f.numChunks;
                    return av;
                });
                v.sources.add(peer);

            
                if (v.displayName.length() < name.length()) {
                    v.displayName = name;
                }
            }
        }

        
        for (SharedFile f : localCatalog.values()) {
            String name = f.primaryName();
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
            AvailableVideo v = agg.computeIfAbsent(f.contentHash, h -> {
                AvailableVideo av = new AvailableVideo();
                av.contentHash = f.contentHash;
                av.displayName = name;
                av.sizeBytes = f.sizeBytes;
                av.numChunks = f.numChunks;
                return av;
            });
        }

        return agg.values().stream()
                .sorted(Comparator.comparing(a -> a.displayName.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }
}
