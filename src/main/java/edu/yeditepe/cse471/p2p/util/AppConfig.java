package edu.yeditepe.cse471.p2p.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public final class AppConfig {
    private AppConfig() {}

    public static final String PEER_ID = UUID.randomUUID().toString();

    public static String peerName() {
        return System.getProperty("p2p.peerName", "Peer");
    }

    public static int controlPort() {
        return Integer.parseInt(System.getProperty("p2p.controlPort", "47101"));
    }

    public static String discoveryGroup() {
        return System.getProperty("p2p.discoveryGroup", "230.71.47.1");
    }

    public static int discoveryPort() {
        return Integer.parseInt(System.getProperty("p2p.discoveryPort", "47100"));
    }

    public static int discoveryTtl() {
        return Integer.parseInt(System.getProperty("p2p.discoveryTtl", "1"));
    }

    public static int discoveryAnnounceIntervalMs() {
        return Integer.parseInt(System.getProperty("p2p.discoveryAnnounceMs", "5000"));
    }

    public static InetAddress localHost() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new RuntimeException("Cannot resolve local host", e);
        }
    }
}
