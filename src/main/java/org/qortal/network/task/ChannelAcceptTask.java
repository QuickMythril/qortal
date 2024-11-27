package org.qortal.network.task;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.arbitrary.ArbitraryDataFileManager;
import org.qortal.network.Network;
import org.qortal.network.Peer;
import org.qortal.network.PeerAddress;
import org.qortal.settings.Settings;
import org.qortal.utils.ExecuteProduceConsume.Task;
import org.qortal.utils.NTP;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

public class ChannelAcceptTask implements Task {
    private static final Logger LOGGER = LogManager.getLogger(ChannelAcceptTask.class);

    private final ServerSocketChannel serverSocketChannel;

    public ChannelAcceptTask(ServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
    }

    @Override
    public String getName() {
        return "ChannelAcceptTask";
    }

    @Override
    public void perform() throws InterruptedException {
        final Network network = Network.getInstance();

        try {
            // Check if max peer limit is reached
            int currentPeerCount = network.getImmutableConnectedPeers().size();
            if (currentPeerCount >= network.getMaxPeers()) {
                LOGGER.debug("Incoming connection ignored: server is full ({} peers connected)", currentPeerCount);
                return;
            }

            // Accept new socket connection
            SocketChannel socketChannel = serverSocketChannel.accept();
            network.setInterestOps(serverSocketChannel, SelectionKey.OP_ACCEPT);

            // Handle null case for socketChannel
            if (socketChannel == null) {
                return;
            }

            // Process the accepted connection
            handleNewConnection(network, socketChannel);
        } catch (IOException e) {
            LOGGER.error("Error during connection accept: {}", e.getMessage(), e);
        }
    }

    private void handleNewConnection(Network network, SocketChannel socketChannel) throws IOException {
        PeerAddress address = PeerAddress.fromSocket(socketChannel.socket());
        List<String> fixedNetwork = Settings.getInstance().getFixedNetwork();

        // Check if peer is allowed in fixed network
        if (fixedNetwork != null && !fixedNetwork.isEmpty() && network.ipNotInFixedList(address, fixedNetwork)) {
            LOGGER.debug("Connection rejected: peer {} not in fixed network list", address);
            closeSocket(socketChannel);
            return;
        }

        // Determine connection limits
        int maxPeers = Settings.getInstance().getMaxPeers();
        int maxDataPeers = Settings.getInstance().getMaxDataPeers();
        int maxRegularPeers = maxPeers - maxDataPeers;

        int connectedDataPeers = network.getImmutableConnectedDataPeers().size();
        int connectedRegularPeers = network.getImmutableConnectedNonDataPeers().size();

        boolean isDataPeer = ArbitraryDataFileManager.getInstance().isPeerRequestingData(address.getHost());
        boolean connectionLimitReached = (isDataPeer && connectedDataPeers >= maxDataPeers)
                || (!isDataPeer && connectedRegularPeers >= maxRegularPeers);

        // Double-check maxPeers limit
        if (network.getImmutableConnectedPeers().size() >= maxPeers) {
            connectionLimitReached = true;
        }

        if (connectionLimitReached) {
            LOGGER.debug("Connection rejected: server is full for {} peer type", isDataPeer ? "data" : "regular");
            closeSocket(socketChannel);
            return;
        }

        // Check NTP synchronization
        final Long now = NTP.getTime();
        if (now == null) {
            LOGGER.debug("Connection rejected: NTP time unavailable for peer {}", address);
            closeSocket(socketChannel);
            return;
        }

        // Accept the new peer
        acceptPeer(network, socketChannel, address, isDataPeer);
    }

    private void acceptPeer(Network network, SocketChannel socketChannel, PeerAddress address, boolean isDataPeer) {
        try {
            LOGGER.debug("Connection accepted from peer {}", address);

            Peer newPeer = new Peer(socketChannel);
            if (isDataPeer) {
                long maxConnectionAge = Settings.getInstance().getMaxDataPeerConnectionTime() * 1000L;
                newPeer.setMaxConnectionAge(maxConnectionAge);
            }
            newPeer.setIsDataPeer(isDataPeer);
            network.addConnectedPeer(newPeer);

            // Notify the network that the peer is ready
            network.onPeerReady(newPeer);
        } catch (IOException e) {
            LOGGER.error("Failed to accept peer {}: {}", address, e.getMessage(), e);
            closeSocket(socketChannel);
        }
    }

    private void closeSocket(SocketChannel socketChannel) {
        if (socketChannel != null && socketChannel.isOpen()) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close socket: {}", e.getMessage(), e);
            }
        }
    }
}
