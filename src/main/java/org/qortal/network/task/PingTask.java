package org.qortal.network.task;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.network.Peer;
import org.qortal.network.message.Message;
import org.qortal.network.message.MessageType;
import org.qortal.network.message.PingMessage;
import org.qortal.utils.ExecuteProduceConsume.Task;
import org.qortal.utils.NTP;

public class PingTask implements Task {
    private static final Logger LOGGER = LogManager.getLogger(PingTask.class);

    private final Peer peer;
    private final Long now;
    private String name; // Lazy initialization

    public PingTask(Peer peer, Long now) {
        this.peer = peer;
        this.now = now;
    }

    @Override
    public String getName() {
        if (name == null) {
            name = "PingTask::" + peer;
        }
        return name;
    }

    @Override
    public void perform() throws InterruptedException {
        try {
            // Create a PingMessage
            PingMessage pingMessage = new PingMessage();
            Message responseMessage = peer.getResponse(pingMessage);

            // Validate the response
            if (responseMessage == null || responseMessage.getType() != MessageType.PING) {
                LOGGER.debug("[{}] No PING response received from {} for PING ID {}",
                        peer.getPeerConnectionId(), peer, pingMessage.getId());
                peer.disconnect("No PING response received");
                return;
            }

            // Calculate round-trip time and update peer
            long currentTime = NTP.getTime();
            if (currentTime == null) {
                LOGGER.warn("NTP time unavailable during PING task for peer {}", peer);
                peer.disconnect("NTP time unavailable");
                return;
            }

            peer.setLastPing(currentTime - now);
            LOGGER.debug("[{}] PING successful with {} (round-trip time: {} ms)",
                    peer.getPeerConnectionId(), peer, currentTime - now);

        } catch (Exception e) {
            LOGGER.error("Error during PING task for peer {}: {}", peer, e.getMessage(), e);
            peer.disconnect("Error during PING task");
        }
    }
}
