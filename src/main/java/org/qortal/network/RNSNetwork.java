package org.qortal.network;

import java.io.IOException;
//import java.nio.channels.SelectionKey;
//import java.io.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.util.*;
//import java.util.function.BiConsumer;
//import java.util.function.Consumer;
//import java.util.function.Function;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicLong;

//import org.qortal.data.network.PeerData;
import org.qortal.repository.DataException;
//import org.qortal.settings.Settings;
import org.qortal.settings.Settings;
//import org.qortal.utils.NTP;

//import com.fasterxml.jackson.annotation.JsonGetter;

import org.apache.commons.codec.binary.Hex;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import io.reticulum.Reticulum;
import io.reticulum.Transport;
import io.reticulum.destination.Destination;
import io.reticulum.destination.DestinationType;
import io.reticulum.destination.Direction;
import io.reticulum.identity.Identity;
import io.reticulum.interfaces.ConnectionInterface;
import io.reticulum.destination.ProofStrategy;
import io.reticulum.transport.AnnounceHandler;
import static io.reticulum.constant.ReticulumConstant.CONFIG_FILE_NAME;
//import static io.reticulum.identity.IdentityKnownDestination.recall;
//import static io.reticulum.identity.IdentityKnownDestination.recallAppData;
//import static io.reticulum.destination.Direction.OUT;

import lombok.extern.slf4j.Slf4j;
import lombok.Synchronized;
import io.reticulum.link.Link;
import io.reticulum.link.LinkStatus;
//import io.reticulum.packet.PacketReceipt;
import io.reticulum.packet.Packet;

//import static io.reticulum.link.LinkStatus.ACTIVE;
import static io.reticulum.link.LinkStatus.CLOSED;
import static io.reticulum.link.LinkStatus.PENDING;
import static io.reticulum.link.LinkStatus.STALE;

import static java.nio.charset.StandardCharsets.UTF_8;
//import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

//import org.qortal.network.Network.NetworkProcessor;
//import org.qortal.utils.ExecuteProduceConsume;
//import org.qortal.utils.NamedThreadFactory;

//import java.time.Instant;

//import org.qortal.network.RNSPeer;

@Slf4j
public class RNSNetwork {

    static final String APP_NAME = "qortal";
    private Reticulum reticulum;
    private Identity server_identity;
    private Destination baseDestination;     // service base (initially: anything node2node)
    //private Destination dataDestination;   // qdn services (eg. files like music, videos etc)
    //private Destination liveDestination;   // live/dynamic peer list (eg. video conferencing)
    // the following should be retrieved from settings
    private static Integer MAX_PEERS = 3;
    private static Integer MIN_DESIRED_PEERS = 3;
    //private final Integer MAX_PEERS = Settings.getInstance().getMaxReticulumPeers();
    //private final Integer MIN_DESIRED_PEERS = Settings.getInstance().getMinDesiredReticulumPeers();
    static final String defaultConfigPath = new String(".reticulum"); // if empty will look in Reticulums default paths
    //private final String defaultConfigPath = Settings.getInstance().getDefaultConfigPathForReticulum();

    //private static final Logger logger = LoggerFactory.getLogger(RNSNetwork.class);

    //private final List<Link> linkedPeers = Collections.synchronizedList(new ArrayList<>());
    //private List<Link> immutableLinkedPeers = Collections.emptyList();
    private final List<RNSPeer> linkedPeers = Collections.synchronizedList(new ArrayList<>());

    //private final ExecuteProduceConsume rnsNetworkEPC;
    private static final long NETWORK_EPC_KEEPALIVE = 1000L; // 1 second
    private volatile boolean isShuttingDown = false;
    private int totalThreadCount = 0;

    // TODO: settings - MaxReticulumPeers, MaxRNSNetworkThreadPoolSize (if needed)
    
    // Constructor
    private RNSNetwork () {
        try {
            initConfig(defaultConfigPath);
            reticulum = new Reticulum(defaultConfigPath);
            log.info("reticulum instance created: {}", reticulum.toString());
        } catch (IOException e) {
            log.error("unable to create Reticulum network", e);
        }

        //        Settings.getInstance().getMaxRNSNetworkThreadPoolSize(),   // statically set to 5 below
        //ExecutorService RNSNetworkExecutor = new ThreadPoolExecutor(1,
        //        5,
        //        NETWORK_EPC_KEEPALIVE, TimeUnit.SECONDS,
        //        new SynchronousQueue<Runnable>(),
        //        new NamedThreadFactory("RNSNetwork-EPC"));
        //rnsNetworkEPC = new RNSNetworkProcessor(RNSNetworkExecutor);
    }

    // Note: potentially create persistent server_identity (utility rnid) and load it from file
    public void start() throws IOException, DataException {

        // create identity either from file or new (creating new keys)
        var serverIdentityPath = reticulum.getStoragePath().resolve(APP_NAME);
        if (Files.isReadable(serverIdentityPath)) {
            server_identity = Identity.fromFile(serverIdentityPath);
            log.info("server identity loaded from file {}", serverIdentityPath.toString());
        } else {
            server_identity = new Identity();
            log.info("new server identity created dynamically.");
        }
        log.debug("Server Identity: {}", server_identity.toString());

        // show the ifac_size of the configured interfaces (debug code)
        for (ConnectionInterface i: Transport.getInstance().getInterfaces() ) {
            log.info("interface {}, length: {}", i.getInterfaceName(), i.getIfacSize());
        }

        baseDestination = new Destination(
            server_identity,
            Direction.IN,
            DestinationType.SINGLE,
            APP_NAME,
            "core"
        );
        //// ideas for other entry points
        //dataDestination = new Destination(
        //    server_identity,
        //    Direction.IN,
        //    DestinationType.SINGLE,
        //    APP_NAME,
        //    "core",
        //    "qdn"
        //);
        //liveDestination = new Destination(
        //    server_identity,
        //    Direction.IN,
        //    DestinationType.SINGLE,
        //    APP_NAME,
        //    "core",
        //    "live"
        //);
        log.info("Destination "+Hex.encodeHexString(baseDestination.getHash())+" "+baseDestination.getName()+" running.");
        //log.info("Destination "+Hex.encodeHexString(dataDestination.getHash())+" "+dataDestination.getName()+" running.");
   
        baseDestination.setProofStrategy(ProofStrategy.PROVE_ALL);
        //dataDestination.setProofStrategy(ProofStrategy.PROVE_ALL);

        baseDestination.setAcceptLinkRequests(true);
        //dataDestination.setAcceptLinkRequests(true);
        //baseDestination.setLinkEstablishedCallback(this::linkExtabishedCallback);
        baseDestination.setPacketCallback(this::packetCallback);
        //baseDestination.setPacketCallback((message, packet) -> {
        //    log.info("xyz - Message raw {}", message);
        //    log.info("xyz - Packet {}", packet.toString());
        //});
        
        Transport.getInstance().registerAnnounceHandler(new QAnnounceHandler());
        log.info("announceHandlers: {}", Transport.getInstance().getAnnounceHandlers());

        baseDestination.announce();
        //dataDestination.announce();
        log.info("Sent initial announce from {} ({})", Hex.encodeHexString(baseDestination.getHash()), baseDestination.getName());
   
        // Start up first networking thread (the "server loop")
        //rnsNetworkEPC.start();
    }

    public void shutdown() {
        isShuttingDown = true;
        log.info("shutting down Reticulum");

        // Stop processing threads (the "server loop")
        //try {
        //    if (!this.rnsNetworkEPC.shutdown(5000)) {
        //        logger.warn("Network threads failed to terminate");
        //    }
        //} catch (InterruptedException e) {
        //    logger.warn("Interrupted while waiting for networking threads to terminate");
        //}
        
        // Disconnect peers and terminate Reticulum
        for (RNSPeer p : linkedPeers) {
            if (nonNull(p.getLink())) {
                p.getLink().teardown();
            }
        }
        reticulum.exitHandler();
    }

    private void initConfig(String configDir) throws IOException {
        File configDir1 = new File(defaultConfigPath);
        if (!configDir1.exists()) {
            configDir1.mkdir();
        }
        var configPath = Path.of(configDir1.getAbsolutePath());
        Path configFile = configPath.resolve(CONFIG_FILE_NAME);

        if (Files.notExists(configFile)) {
            var defaultConfig = this.getClass().getClassLoader().getResourceAsStream("reticulum_default_config.yml");
            Files.copy(defaultConfig, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void packetCallback(byte[] message, Packet packet) {
        log.info("xyz - Message raw {}", message);
        log.info("xyz - Packet {}", packet.toString());
    }

    //public void announceBaseDestination () {
    //    getBaseDestination().announce();
    //}

    //public Consumer<Link> clientConnected(Link link) {
    //    log.info("Client connected");
    //    link.setLinkClosedCallback(clientDisconnected(link));
    //    link.setPacketCallback(null);
    //}

    //public void clientDisconnected(Link link) {
    //    log.info("Client disconnected");
    //    linkedPeers.remove(link);
    //}

    // client part
    //@Slf4j
    private static class QAnnounceHandler implements AnnounceHandler {
        @Override
        public String getAspectFilter() {
            // handle all announces
            return null;
        }

        @Override
        @Synchronized
        public void receivedAnnounce(byte[] destinationHash, Identity announcedIdentity, byte[] appData) {
            var peerExists = false;

            log.info("Received an announce from {}", Hex.encodeHexString(destinationHash));
            //log.info("aspect: {}", getAspectFilter());
            //log.info("destinationhash: {}, announcedIdentity: {}, appData: {}", destinationHash, announcedIdentity, appData);

            if (nonNull(appData)) {
                log.debug("The announce contained the following app data: {}", new String(appData, UTF_8));
            }

            // add to peer list if we can use more peers
            //synchronized (this) {
                List<RNSPeer> lps =  RNSNetwork.getInstance().getLinkedPeers();
                if (lps.size() < MAX_PEERS) {
                    for (RNSPeer p : lps) {
                        //log.info("peer exists: hash: {}, destinationHash: {}", p.getDestinationLink().getDestination().getHash(), destinationHash);
                        if (Arrays.equals(p.getDestinationLink().getDestination().getHash(), destinationHash)) {
                            peerExists = true;
                            log.debug("peer exists: hash: {}, destinationHash: {}", p.getDestinationLink().getDestination().getHash(), destinationHash);
                            break;
                        }
                    }
                    if (!peerExists) {
                        //log.info("announce handler - cerate new peer: **announcedIdentity**: {}, **recall**: {}", announcedIdentity, recall(destinationHash));
                        RNSPeer newPeer = new RNSPeer(destinationHash);
                        lps.add(newPeer);
                        log.info("added new RNSPeer, Destination - {}, Link: {}", newPeer.getDestinationHash(), newPeer.getDestinationLink());
                    }
                }
            //}
        }
    }

    // Main thread

    //class RNSNetworkProcessor extends ExecuteProduceConsume {
    //
    //    //private final Logger logger = LoggerFactory.getLogger(RNSNetworkProcessor.class);
    //
    //    private final AtomicLong nextConnectTaskTimestamp = new AtomicLong(0L); // ms - try first connect once NTP syncs
    //    private final AtomicLong nextBroadcastTimestamp = new AtomicLong(0L); // ms - try first broadcast once NTP syncs
    //
    //    private Iterator<SelectionKey> channelIterator = null;
    //
    //    RNSNetworkProcessor(ExecutorService executor) {
    //        super(executor);
    //    }
    //
    //    @Override
    //    protected void onSpawnFailure() {
    //        // For debugging:
    //        // ExecutorDumper.dump(this.executor, 3, ExecuteProduceConsume.class);
    //    }
    //
    //    @Override
    //    protected Task produceTask(boolean canBlock) throws InterruptedException {
    //        Task task;
    //
    //        //task = maybeProducePeerMessageTask();
    //        //if (task != null) {
    //        //    return task;
    //        //}
    //        //
    //        //final Long now = NTP.getTime();
    //        //
    //        //task = maybeProducePeerPingTask(now);
    //        //if (task != null) {
    //        //    return task;
    //        //}
    //        //
    //        //task = maybeProduceConnectPeerTask(now);
    //        //if (task != null) {
    //        //    return task;
    //        //}
    //        //
    //        //task = maybeProduceBroadcastTask(now);
    //        //if (task != null) {
    //        //    return task;
    //        //}
    //        //
    //        // Only this method can block to reduce CPU spin
    //        //return maybeProduceChannelTask(canBlock);
    //
    //        // TODO: flesh out the tasks handled by Reticulum
    //        return null;
    //    }
    //    //...TODO: implement abstract methods...
    //}


    // getter / setter
    private static class SingletonContainer {
        private static final RNSNetwork INSTANCE = new RNSNetwork();
    }

    public static RNSNetwork getInstance() {
        return SingletonContainer.INSTANCE;
    }

    public List<RNSPeer> getLinkedPeers() {
        synchronized(this.linkedPeers) {
            //return new ArrayList<>(this.linkedPeers);
            return this.linkedPeers;
        }
    }

    public Integer getTotalPeers() {
        synchronized (this) {
            return linkedPeers.size();
        }
    }

    public Destination getBaseDestination() {
        return baseDestination;
    }

    // maintenance
    
    //private static class AnnounceTimer {
    //    //public void main(String[] args) throws InterruptedException
    //    public void main(String[] args) throws InterruptedException
    //    {
    //        Timer timer = new Timer();
    //        // run timer every 10s (10000ms)
    //        timer.schedule(new TimerTask() {
    //            @Override
    //            public void run() {
    //                System.out.println("AnnounceTimer: " + new java.util.Date());
    //            }
    //        }, 0, 10000);
    //    }
    //}

    @Synchronized
    public void prunePeers() throws DataException {
        // run periodically (by the Controller)
        //log.info("Peer list (linkedPeers): {}",this.linkedPeers.toString());
        //synchronized(this) {
            //List<Link> linkList = getLinkedPeers();
            List<RNSPeer> peerList = this.linkedPeers;
            log.info("List of RNSPeers: {}", this.linkedPeers);
            //log.info("number of links (linkedPeers) before prunig: {}", this.linkedPeers.size());
            Link pLink;
            LinkStatus lStatus;
            for (RNSPeer p: peerList) {
                pLink = p.getLink();
                lStatus = pLink.getStatus();
                //log.debug("link status: "+lStatus.toString());
                // lStatus in: PENDING, HANDSHAKE, ACTIVE, STALE, CLOSED
                if (lStatus == CLOSED) {
                    p.resetPeer();
                    peerList.remove(p);
                } else if (lStatus == STALE) {
                    pLink.teardown();
                    p.resetPeer();
                    peerList.remove(p);
                } else if (lStatus == PENDING) {
                    log.info("prunePeers - link state still {}", lStatus);
                    // TODO: can we help the Link along somehow?
                }
            }
            log.info("number of links (linkedPeers) after prunig: {}", this.linkedPeers.size());
        //}
        maybeAnnounce(getBaseDestination());
    }

    public void maybeAnnounce(Destination d) {
        if (getLinkedPeers().size() < MIN_DESIRED_PEERS) {
            d.announce();
        }
    }

}

