package org.qortal.network;

import com.google.common.primitives.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.crypto.Crypto;
import org.qortal.crypto.MemoryPoW;
import org.qortal.network.message.*;
import org.qortal.settings.Settings;
import org.qortal.utils.DaemonThreadFactory;
import org.qortal.utils.NTP;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

public enum Handshake {
    STARTED(null) {
        @Override
        public Handshake onMessage(Peer peer, Message message) {
            return HELLO;
        }

        @Override
        public void action(Peer peer) {
            // No action needed for STARTED state
        }
    },
    HELLO(MessageType.HELLO) {
        @Override
        public Handshake onMessage(Peer peer, Message message) {
            HelloMessage helloMessage = (HelloMessage) message;

            if (!validateHelloMessage(peer, helloMessage)) {
                return null;
            }

            return CHALLENGE;
        }

        @Override
        public void action(Peer peer) {
            sendHelloMessage(peer);
        }
    },
    CHALLENGE(MessageType.CHALLENGE) {
        @Override
        public Handshake onMessage(Peer peer, Message message) {
            ChallengeMessage challengeMessage = (ChallengeMessage) message;

            if (isSelfConnection(peer, challengeMessage)) {
                return CHALLENGE; // Stay in CHALLENGE state for self-connection
            }

            if (!validatePeerPublicKey(peer, challengeMessage)) {
                return null;
            }

            return RESPONSE;
        }

        @Override
        public void action(Peer peer) {
            sendChallengeMessage(peer);
        }
    },
    RESPONSE(MessageType.RESPONSE) {
        @Override
        public Handshake onMessage(Peer peer, Message message) {
            if (!validateResponse(peer, (ResponseMessage) message)) {
                return null;
            }

            // If inbound peer, switch to RESPONDING to send RESPONSE
            if (!peer.isOutbound()) {
                return RESPONDING;
            }

            return COMPLETED;
        }

        @Override
        public void action(Peer peer) {
            sendResponseMessage(peer);
        }
    },
    RESPONDING(null) {
        @Override
        public Handshake onMessage(Peer peer, Message message) {
            // Should not receive messages in RESPONDING state
            return null;
        }

        @Override
        public void action(Peer peer) {
            // No action needed
        }
    },
    COMPLETED(null) {
        @Override
        public Handshake onMessage(Peer peer, Message message) {
            // No messages expected in COMPLETED state
            return null;
        }

        @Override
        public void action(Peer peer) {
            // No action needed
        }
    };

    private static final Logger LOGGER = LogManager.getLogger(Handshake.class);

    // Constants for handshake validation
    private static final long MAX_TIMESTAMP_DELTA = 30 * 1000L; // milliseconds
    private static final long PEER_VERSION_131 = 0x0100030001L;
    private static final String MIN_PEER_VERSION = "4.1.1";

    private static final int POW_BUFFER_SIZE_PRE_131 = 8 * 1024 * 1024; // bytes
    private static final int POW_DIFFICULTY_PRE_131 = 8; // leading zero bits
    private static final int POW_BUFFER_SIZE_POST_131 = 2 * 1024 * 1024; // bytes
    private static final int POW_DIFFICULTY_POST_131 = 2; // leading zero bits

    private static final ExecutorService RESPONSE_EXECUTOR = Executors.newFixedThreadPool(
            Settings.getInstance().getNetworkPoWComputePoolSize(),
            new DaemonThreadFactory("Network-PoW", Settings.getInstance().getHandshakeThreadPriority())
    );

    private static final byte[] ZERO_CHALLENGE = new byte[ChallengeMessage.CHALLENGE_LENGTH];

    public final MessageType expectedMessageType;

    Handshake(MessageType expectedMessageType) {
        this.expectedMessageType = expectedMessageType;
    }

    public abstract Handshake onMessage(Peer peer, Message message);

    public abstract void action(Peer peer);

    // HELLO State Helpers
    private static boolean validateHelloMessage(Peer peer, HelloMessage helloMessage) {
        long timestampDelta = Math.abs(helloMessage.getTimestamp() - NTP.getTime());

        if (timestampDelta > MAX_TIMESTAMP_DELTA) {
            LOGGER.debug(() -> String.format("Peer %s HELLO timestamp too divergent (±%d > %d)", 
                    peer, timestampDelta, MAX_TIMESTAMP_DELTA));
            return false;
        }

        if (!validateVersion(peer, helloMessage.getVersionString())) {
            return false;
        }

        Network.getInstance().ourPeerAddressUpdated(helloMessage.getSenderPeerAddress());
        return true;
    }

    private static boolean validateVersion(Peer peer, String versionString) {
        Matcher matcher = peer.VERSION_PATTERN.matcher(versionString);
        if (!matcher.lookingAt()) {
            LOGGER.debug(() -> String.format("Peer %s sent invalid HELLO version string '%s'", peer, versionString));
            return false;
        }

        peer.setPeersVersion(versionString, extractVersionNumber(matcher));
        return peer.isAtLeastVersion(MIN_PEER_VERSION) && peer.isAllowedVersion();
    }

    private static long extractVersionNumber(Matcher matcher) {
        long version = 0;
        for (int g = 1; g <= 3; ++g) {
            version = (version << 16) | Long.parseLong(matcher.group(g));
        }
        return version;
    }

    private static void sendHelloMessage(Peer peer) {
        Message helloMessage = new HelloMessage(
                NTP.getTime(),
                Controller.getInstance().getVersionString(),
                peer.getPeerData().getAddress().toString()
        );

        if (!peer.sendMessage(helloMessage)) {
            peer.disconnect("Failed to send HELLO");
        }
    }

    // CHALLENGE State Helpers
    private static boolean isSelfConnection(Peer peer, ChallengeMessage challengeMessage) {
        byte[] peersPublicKey = challengeMessage.getPublicKey();
        byte[] ourPublicKey = Network.getInstance().getOurPublicKey();

        if (!Arrays.equals(peersPublicKey, ourPublicKey)) {
            return false;
        }

        if (peer.isOutbound()) {
            Network.getInstance().noteToSelf(peer);
        } else {
            peer.sendMessage(new ChallengeMessage(ourPublicKey, ZERO_CHALLENGE));
        }

        return true;
    }

    private static void sendChallengeMessage(Peer peer) {
        Message challengeMessage = new ChallengeMessage(
                Network.getInstance().getOurPublicKey(),
                peer.getOurChallenge()
        );

        if (!peer.sendMessage(challengeMessage)) {
            peer.disconnect("Failed to send CHALLENGE");
        }
    }

    // RESPONSE State Helpers
    private static boolean validateResponse(Peer peer, ResponseMessage responseMessage) {
        byte[] sharedSecret = Network.getInstance().getSharedSecret(peer.getPeersPublicKey());
        byte[] expectedData = Crypto.digest(Bytes.concat(sharedSecret, peer.getPeersChallenge()));

        if (!Arrays.equals(expectedData, responseMessage.getData())) {
            LOGGER.debug(() -> String.format("Peer %s sent incorrect RESPONSE data", peer));
            return false;
        }

        return MemoryPoW.verify2(responseMessage.getData(), determinePoWBuffer(peer), determinePoWDifficulty(peer), responseMessage.getNonce());
    }

    private static int determinePoWBuffer(Peer peer) {
        return peer.getPeersVersion() < PEER_VERSION_131 ? POW_BUFFER_SIZE_PRE_131 : POW_BUFFER_SIZE_POST_131;
    }

    private static int determinePoWDifficulty(Peer peer) {
        return peer.getPeersVersion() < PEER_VERSION_131 ? POW_DIFFICULTY_PRE_131 : POW_DIFFICULTY_POST_131;
    }

    private static void sendResponseMessage(Peer peer) {
        RESPONSE_EXECUTOR.execute(() -> {
            if (peer.isStopping()) return;

            byte[] sharedSecret = Network.getInstance().getSharedSecret(peer.getPeersPublicKey());
            byte[] data = Crypto.digest(Bytes.concat(sharedSecret, peer.getPeersChallenge()));

            int powBuffer = determinePoWBuffer(peer);
            int powDifficulty = determinePoWDifficulty(peer);

            Integer nonce = MemoryPoW.compute2(data, powBuffer, powDifficulty);

            if (!peer.sendMessage(new ResponseMessage(nonce, data))) {
                peer.disconnect("Failed to send RESPONSE");
            }

            if (!peer.isOutbound()) {
                peer.setHandshakeStatus(COMPLETED);
                Network.getInstance().onHandshakeCompleted(peer);
            }
        });
    }
}
