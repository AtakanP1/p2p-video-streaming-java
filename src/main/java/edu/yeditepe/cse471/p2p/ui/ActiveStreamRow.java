package edu.yeditepe.cse471.p2p.ui;

import javafx.beans.property.*;

public class ActiveStreamRow {
    private final StringProperty video = new SimpleStringProperty("");
    private final StringProperty sourcePeer = new SimpleStringProperty("");
    private final IntegerProperty chunk = new SimpleIntegerProperty(0);
    private final DoubleProperty progress01 = new SimpleDoubleProperty(0.0);
    private final StringProperty status = new SimpleStringProperty("");

    public ActiveStreamRow(String video, String sourcePeer) {
        setVideo(video);
        setSourcePeer(sourcePeer);
        setStatus("...");
    }

    public StringProperty videoProperty() { return video; }
    public StringProperty sourcePeerProperty() { return sourcePeer; }
    public IntegerProperty chunkProperty() { return chunk; }
    public DoubleProperty progress01Property() { return progress01; }
    public StringProperty statusProperty() { return status; }

    public String getVideo() { return video.get(); }
    public void setVideo(String v) { video.set(v); }

    public String getSourcePeer() { return sourcePeer.get(); }
    public void setSourcePeer(String v) { sourcePeer.set(v); }

    public int getChunk() { return chunk.get(); }
    public void setChunk(int v) { chunk.set(v); }

    public double getProgress01() { return progress01.get(); }
    public void setProgress01(double v) { progress01.set(v); }

    public String getStatus() { return status.get(); }
    public void setStatus(String v) { status.set(v); }
}
