package edu.yeditepe.cse471.p2p;
import javafx.scene.Parent;
import edu.yeditepe.cse471.p2p.model.AvailableVideo;
import edu.yeditepe.cse471.p2p.model.Peer;
import edu.yeditepe.cse471.p2p.model.SharedFile;
import edu.yeditepe.cse471.p2p.network.*;
import edu.yeditepe.cse471.p2p.streaming.StreamSession;
import java.awt.Desktop;
import edu.yeditepe.cse471.p2p.ui.ActiveStreamRow;
import edu.yeditepe.cse471.p2p.util.AppConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class App extends Application {

    // Core state
    private final PeerRegistry registry = new PeerRegistry();
    private final LocalCatalogBuilder catalogBuilder = new LocalCatalogBuilder();
    private final ControlClient controlClient = new ControlClient();

    private final Map<String, SharedFile> localFilesByHash = new ConcurrentHashMap<>();

    private ControlServer controlServer;
    private DiscoveryService discoveryService;

    private Path rootFolder;
    private Path bufferFolder;

    private boolean connected = false;

    // UI state
    private final ObservableList<AvailableVideo> availableVideos = FXCollections.observableArrayList();
    private final ObservableList<ActiveStreamRow> activeStreams = FXCollections.observableArrayList();

    private TextField searchField;
    private ListView<AvailableVideo> availableList;
    private TableView<ActiveStreamRow> activeTable;
    private TextArea eventLog;
    private ProgressBar globalBufferBar;
    private Label statusLabel;
    private Label foldersLabel;

    


    @Override
    public void start(Stage stage) {
        stage.setTitle("P2P Video Streaming Application - " + AppConfig.peerName());

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage));
        root.setCenter(buildMainContent());
        root.setBottom(buildBottomLog());

        Scene scene = new Scene(root, 1000, 700);
        stage.setScene(scene);
        stage.show();

        log("Ready. Set folders, then Stream -> Connect.");
    }

    private MenuBar buildMenuBar(Stage stage) {
        Menu stream = new Menu("Stream");

        MenuItem connectItem = new MenuItem("Connect");
        connectItem.setOnAction(e -> connect());

        MenuItem disconnectItem = new MenuItem("Disconnect");
        disconnectItem.setOnAction(e -> disconnect());

        MenuItem setRoot = new MenuItem("Set Root Video Folder");
        setRoot.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Root Video Folder");
            var f = dc.showDialog(stage);
            if (f != null) {
                rootFolder = f.toPath();
                log("Root folder set: " + rootFolder);
                updateFoldersLabel();
            }
        });

        MenuItem setBuffer = new MenuItem("Set Buffer Folder");
        setBuffer.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Buffer Folder");
            var f = dc.showDialog(stage);
            if (f != null) {
                bufferFolder = f.toPath();
                log("Buffer folder set: " + bufferFolder);
                updateFoldersLabel();
            }
        });

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> {
            disconnect();
            Platform.exit();
        });

        stream.getItems().addAll(connectItem, disconnectItem, new SeparatorMenuItem(), setRoot, setBuffer, new SeparatorMenuItem(), exit);

        Menu help = new Menu("Help");
        MenuItem about = new MenuItem("About");
        about.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("About");
            a.setHeaderText("P2P Video Streaming Application");
            a.setContentText("Developer: " + AppConfig.peerName() + "\nCourse: CSE471\n" +
                    "This project discovers peers via limited-scope UDP multicast and streams 256KB chunks.");
            a.showAndWait();
        });
        help.getItems().add(about);

        return new MenuBar(stream, help);
    }

    private Parent buildMainContent() {
        VBox left = new VBox(8);
        left.setPadding(new Insets(10));
        left.setPrefWidth(320);

        statusLabel = new Label("Status: DISCONNECTED");
        foldersLabel = new Label("Root: (not set)   Buffer: (not set)");

        HBox searchRow = new HBox(6);
        searchField = new TextField();
        searchField.setPromptText("Search videos...");
        Button searchBtn = new Button("Search");
        searchBtn.setOnAction(e -> runSearch());
        searchRow.getChildren().addAll(searchField, searchBtn);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Label availableLbl = new Label("Available Videos on Network");
        availableList = new ListView<>(availableVideos);
        availableList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AvailableVideo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.uiLabel());
            }
        });
        availableList.setOnMouseClicked(evt -> {
            if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
                AvailableVideo v = availableList.getSelectionModel().getSelectedItem();
                if (v != null) startStreaming(v);
            }
        });

        left.getChildren().addAll(statusLabel, foldersLabel, new Separator(), searchRow, availableLbl, availableList);
        VBox.setVgrow(availableList, Priority.ALWAYS);

        VBox right = new VBox(10);
        right.setPadding(new Insets(10));

        Label activeLbl = new Label("Active Streams");
        activeTable = new TableView<>(activeStreams);
        activeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ActiveStreamRow, String> colVideo = new TableColumn<>("Video");
        colVideo.setCellValueFactory(c -> c.getValue().videoProperty());

        TableColumn<ActiveStreamRow, String> colPeer = new TableColumn<>("Source Peer");
        colPeer.setCellValueFactory(c -> c.getValue().sourcePeerProperty());

        TableColumn<ActiveStreamRow, Number> colChunk = new TableColumn<>("Chunk");
        colChunk.setCellValueFactory(c -> c.getValue().chunkProperty());

        TableColumn<ActiveStreamRow, Number> colProg = new TableColumn<>("Progress %");
        colProg.setCellValueFactory(c -> c.getValue().progress01Property().multiply(100));

        TableColumn<ActiveStreamRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());

        activeTable.getColumns().addAll(colVideo, colPeer, colChunk, colProg, colStatus);

       
        StackPane videoPane = new StackPane(new Label("Video playback via VLC Media Player"));
        videoPane.setMinHeight(280);
        


        
        HBox bufferRow = new HBox(8);
        Label bufferLbl = new Label("Global Buffer Status");
        globalBufferBar = new ProgressBar(0);
        globalBufferBar.setPrefWidth(300);
        bufferRow.getChildren().addAll(bufferLbl, globalBufferBar);

        right.getChildren().addAll(activeLbl, activeTable, videoPane, bufferRow);
        VBox.setVgrow(activeTable, Priority.ALWAYS);

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.32);
        return split;
    }

    private Pane buildBottomLog() {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10));
        Label lbl = new Label("Event Log");
        eventLog = new TextArea();
        eventLog.setEditable(false);
        eventLog.setPrefRowCount(6);
        box.getChildren().addAll(lbl, eventLog);
        return box;
    }

    private void updateFoldersLabel() {
        String r = rootFolder == null ? "(not set)" : rootFolder.toString();
        String b = bufferFolder == null ? "(not set)" : bufferFolder.toString();
        foldersLabel.setText("Root: " + r + "   Buffer: " + b);
    }

    private void connect() {
        if (connected) return;

        try {
           
            if (rootFolder != null) {
                var catalog = catalogBuilder.build(rootFolder);
                localFilesByHash.clear();
                for (SharedFile f : catalog) {
                    localFilesByHash.put(f.contentHash, f);
                }
                registry.setLocalCatalog(catalog);
                log("Local catalog built. Files: " + catalog.size());
            } else {
                registry.setLocalCatalog(List.of());
                log("Root folder not set; local catalog is empty.");
            }

            controlServer = new ControlServer(
                    () -> localFilesByHash.values(),
                    (hash) -> localFilesByHash.get(hash)
            );
            controlServer.start();

            discoveryService = new DiscoveryService(registry, this::onNewPeerDiscovered);
            discoveryService.start();

            connected = true;
            statusLabel.setText("Status: CONNECTED");
            log("Connected to overlay network.");

        } catch (Exception e) {
            log("Connect failed: " + e);
            disconnect();
        }
    }

    private void disconnect() {
        if (!connected) {
            statusLabel.setText("Status: DISCONNECTED");
            return;
        }

        try {
            if (discoveryService != null) discoveryService.stop();
            if (controlServer != null) controlServer.stop();
        } catch (Exception ignored) {}

        connected = false;
        availableVideos.clear();
        activeStreams.clear();
        statusLabel.setText("Status: DISCONNECTED");
        log("Disconnected.");
    }

    private void onNewPeerDiscovered(Peer peer) {
        
        new Thread(() -> {
            try {
                log("Discovered peer: " + peer);
                var files = controlClient.requestCatalog(peer, 2500);
                registry.setPeerCatalog(peer.peerId, files);
                log("Catalog fetched from " + peer.peerName + " (" + files.size() + " files)");

                
                Platform.runLater(this::runSearch);

            } catch (IOException e) {
                log("Catalog fetch failed for " + peer + ": " + e.getMessage());
            }
        }, "catalog-fetch-" + peer.peerId).start();
    }

    private void runSearch() {
        String q = searchField.getText();
        var results = registry.search(q);
        availableVideos.setAll(results);
        log("Search for \"" + q + "\" returned " + results.size() + " result(s).");
    }

    private void startStreaming(AvailableVideo v) {
        if (bufferFolder == null) {
            log("Set Buffer Folder before streaming.");
            return;
        }

       
        List<Peer> sources = new ArrayList<>(v.sources);
        if (sources.isEmpty()) {
            Optional<SharedFile> local = registry.localFileByHash(v.contentHash);
            if (local.isPresent()) {
                log("Video is local only. (Streaming from self not implemented in skeleton). File: " + local.get().localPath);
                return;
            }
            log("No sources available.");
            return;
        }

        String safeName = v.displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String outName = safeName + "." + v.contentHash.substring(0, 8) + ".buffer";
        Path outFile = bufferFolder.resolve(outName);

       
        Map<String, ActiveStreamRow> rowByPeer = new HashMap<>();
        for (Peer p : sources) {
            ActiveStreamRow r = new ActiveStreamRow(v.displayName, p.toString());
            rowByPeer.put(p.peerId, r);
        }
        activeStreams.setAll(rowByPeer.values());

        StreamSession session = new StreamSession(
                v.contentHash,
                v.displayName,
                sources,
                v.sizeBytes,
                v.numChunks,
                outFile,
                8,
                new StreamSession.Listener() {
                    @Override
                    public void onLog(String msg) {
                        App.this.log(msg);
                    }

                    @Override
                    public void onPeerUpdate(Peer peer, int chunkIndex, double progress01, String status) {
                        Platform.runLater(() -> {
                            ActiveStreamRow r = rowByPeer.get(peer.peerId);
                            if (r != null) {
                                r.setChunk(chunkIndex);
                                r.setProgress01(progress01);
                                r.setStatus(status);
                            }
                        });
                    }

                    @Override
                    public void onGlobalBuffer(double progress01, int contiguousChunks) {
                        Platform.runLater(() -> globalBufferBar.setProgress(progress01));
                    }

                    @Override
                    public void onReadyToPlay(Path localFile) {
                        log("Buffer threshold met. Starting playback: " + localFile);
                    }

                    @Override
                    public void onCompleted(Path localFile) {
                        log("Download complete: " + localFile);
                         String fileName = localFile.getFileName().toString();

       
                 if (!fileName.endsWith(".buffer")) {
               log("Not a buffer file, skipping open: " + localFile);
                return;
                 }

              String finalName = fileName.substring(0, fileName.indexOf(".mp4") + 4);
              Path finalMp4 = localFile.getParent().resolve(finalName);

               try {
              Files.move(localFile, finalMp4, StandardCopyOption.REPLACE_EXISTING);
              log("Final video assembled: " + finalMp4);

        
              Desktop.getDesktop().open(finalMp4.toFile());

              } catch (Exception e) {
           log("Failed to finalize video: " + e.getMessage());
            }


                        
        }
                }
        );

        new Thread(() -> {
            try {
                session.start();
            } catch (Exception e) {
                log("Stream failed: " + e.getMessage());
            }
        }, "stream-" + Instant.now().toEpochMilli()).start();
    }

    private void log(String msg) {
        Platform.runLater(() -> {
            eventLog.appendText("[" + Instant.now() + "] " + msg + "\n");
        });
    }

    public static void main(String[] args) {
      
        launch(args);
    }
}
