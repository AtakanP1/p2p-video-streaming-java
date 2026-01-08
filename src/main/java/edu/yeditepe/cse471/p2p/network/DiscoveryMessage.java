package edu.yeditepe.cse471.p2p.network;

public class DiscoveryMessage {
    public String type;       // "HELLO" | "ANNOUNCE"
    public String messageId;  // dedup
    public String peerId;
    public String peerName;
    public int controlPort;
    public long epochMs;

    public DiscoveryMessage() {}
}
