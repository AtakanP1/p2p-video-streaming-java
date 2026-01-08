package edu.yeditepe.cse471.p2p.model;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Objects;

public class Peer {
    public String peerId;
    public String peerName;
    public InetAddress address;
    public int controlPort;
    public Instant lastSeen;

    public Peer() {}

    public Peer(String peerId, String peerName, InetAddress address, int controlPort) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.address = address;
        this.controlPort = controlPort;
        this.lastSeen = Instant.now();
    }

    public String key() {
        return peerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer peer)) return false;
        return Objects.equals(peerId, peer.peerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId);
    }

    @Override
    public String toString() {
        return peerName + " (" + address.getHostAddress() + ":" + controlPort + ")";
    }
}
