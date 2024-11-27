package org.qortal.network.task;

import org.qortal.network.Network;
import org.qortal.network.Peer;
import org.qortal.network.message.Message;
import org.qortal.utils.ExecuteProduceConsume.Task;

public class MessageTask implements Task {
    private final Peer peer;
    private final Message nextMessage;
    private String name; // Lazy initialization

    public MessageTask(Peer peer, Message nextMessage) {
        this.peer = peer;
        this.nextMessage = nextMessage;
    }

    @Override
    public String getName() {
        if (name == null) {
            name = "MessageTask::" + peer + "::" + nextMessage.getType();
        }
        return name;
    }

    @Override
    public void perform() throws InterruptedException {
        try {
            Network.getInstance().onMessage(peer, nextMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            System.err.println("Error processing message task: " + e.getMessage());
        }
    }
}
