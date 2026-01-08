# p2p-video-streaming-java

## Key Features
- Fully decentralized **P2P architecture**
- Peer discovery via **UDP flooding with TTL limitation**
- Video transmission in **256 KB fixed-size chunks**
- **Out-of-order chunk delivery** from multiple peers
- Playback while downloading using a **buffering mechanism**
- Detection of missing and duplicate chunks
- Hash-based identification of identical videos with different filenames
- Graphical User Interface (GUI) with:
  - Network connection controls
  - Video search functionality
  - Active stream monitoring
  - Global buffer status indicator

## Technologies
- Java
- TCP / UDP sockets
- Java Swing (GUI)
- External video player library (e.g., VLCj)
