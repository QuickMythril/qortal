package org.qortal.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;

import org.qortal.network.RNSNetwork;
import io.reticulum.link.Link;
import io.reticulum.packet.Packet;
import io.reticulum.identity.Identity;
import io.reticulum.channel.Channel;
import io.reticulum.destination.Destination;
import io.reticulum.destination.DestinationType;
import io.reticulum.destination.Direction;

import static io.reticulum.identity.IdentityKnownDestination.recall;
//import static io.reticulum.identity.IdentityKnownDestination.recallAppData;
import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import lombok.Data;
import lombok.AccessLevel;

@Data
@Slf4j
public class RNSPeer {

    private byte[] destinationHash;
    private Link destinationLink;
    private Identity destinationIdentity;
    @Setter(AccessLevel.PACKAGE) private long creationTimestamp;
    private Long lastAccessTimestamp;

    // constructors
    public RNSPeer (byte[] dhash) {
        this.destinationHash = dhash;
        this.destinationIdentity = recall(dhash);
        Link newLink = new Link(
            new Destination(
                    this.destinationIdentity,
                    Direction.OUT,
                    DestinationType.SINGLE,
                    RNSNetwork.APP_NAME,
                    "core"
            )
        );
        this.destinationLink = newLink;
        destinationLink.setPacketCallback(this::packetCallback);
    }

    public RNSPeer (Link newLink) {
        this.destinationHash = newLink.getDestination().getHash();
        this.destinationLink = newLink;
        this.destinationIdentity = newLink.getRemoteIdentity();
        setCreationTimestamp(System.currentTimeMillis());
        this.lastAccessTimestamp = null;
        destinationLink.setPacketCallback(this::packetCallback);
    }

    public RNSPeer () {
        this.destinationHash = null;
        this.destinationLink = null;
        this.destinationIdentity = null;
        setCreationTimestamp(System.currentTimeMillis());
        this.lastAccessTimestamp = null;
    }

    // utilities (change Link type, call tasks, ...)
    //...

    private void packetCallback(byte[] message, Packet packet) {
        log.debug("Message raw {}", message);
        log.debug("Packet {}", packet.toString());
        // ...
    }

    public Link getLink() {
        if (isNull(getDestinationLink())) {
            Link newLink = new Link(
                new Destination(
                        this.destinationIdentity,
                        Direction.OUT,
                        DestinationType.SINGLE,
                        RNSNetwork.APP_NAME,
                        "core"
                )
            );
            this.destinationLink = newLink;
            return newLink;
        }
        return getDestinationLink();
    }

    public Channel getChannel() {
        if (isNull(getDestinationLink())) {
            log.warn("link is null.");
            return null;
        }
        setLastAccessTimestamp(System.currentTimeMillis());
        return getDestinationLink().getChannel();
    }

    public void resetPeer () {
        this.destinationHash = null;
        this.destinationLink = null;
        this.destinationIdentity = null;
        this.lastAccessTimestamp = null;
    }
    
}
