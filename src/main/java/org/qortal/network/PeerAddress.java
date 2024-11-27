package org.qortal.network;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import org.qortal.settings.Settings;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.net.*;

/**
 * Encapsulates parsing, rendering, and resolving peer addresses,
 * including late-stage resolving before actual use by a socket.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class PeerAddress {

    // Properties
    private String host; // Hostname or IP address (bracketed if IPv6)
    private int port;

    // Private constructor to enforce factory usage
    private PeerAddress(String host, int port) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        this.host = host;
        this.port = port;
    }

    // Default constructor for JAXB
    protected PeerAddress() {
    }

    // Factory Methods

    /**
     * Constructs a PeerAddress from a connected socket.
     *
     * @param socket the connected socket
     * @return the PeerAddress
     */
    public static PeerAddress fromSocket(Socket socket) {
        InetSocketAddress socketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
        InetAddress address = socketAddress.getAddress();

        String host = InetAddresses.toAddrString(address);

        // Ensure IPv6 addresses are properly bracketed
        if (address instanceof Inet6Address) {
            host = "[" + host + "]";
        }

        return new PeerAddress(host, socketAddress.getPort());
    }

    /**
     * Constructs a PeerAddress from a string containing hostname/IP and optional port.
     * IPv6 addresses must be enclosed in brackets.
     *
     * @param addressString the address string
     * @return the PeerAddress
     * @throws IllegalArgumentException if the input is invalid
     */
    public static PeerAddress fromString(String addressString) {
        boolean isBracketed = addressString.startsWith("[");

        // Parse the host and port
        HostAndPort hostAndPort = HostAndPort.fromString(addressString)
                .withDefaultPort(Settings.getInstance().getDefaultListenPort())
                .requireBracketsForIPv6();

        String host = hostAndPort.getHost();

        // Validate host as IP literal or hostname
        if (host.contains(":") || host.matches("[0-9.]+")) {
            try {
                InetAddresses.forString(host); // Validate IP literal
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid IP literal: " + host, e);
            }
        }

        // Enforce IPv6 brackets for consistency
        if (isBracketed) {
            host = "[" + host + "]";
        }

        return new PeerAddress(host, hostAndPort.getPort());
    }

    // Getters

    /** @return the hostname or IP address (bracketed if IPv6) */
    public String getHost() {
        return this.host;
    }

    /** @return the port number */
    public int getPort() {
        return this.port;
    }

    // Conversions

    /**
     * Converts this PeerAddress to an InetSocketAddress, performing DNS resolution if necessary.
     *
     * @return the InetSocketAddress
     * @throws UnknownHostException if the host cannot be resolved
     */
    public InetSocketAddress toSocketAddress() throws UnknownHostException {
        InetSocketAddress socketAddress = new InetSocketAddress(this.host, this.port);

        if (socketAddress.isUnresolved()) {
            throw new UnknownHostException("Unable to resolve host: " + this.host);
        }

        return socketAddress;
    }

    @Override
    public String toString() {
        return this.host + ":" + this.port;
    }

    // Utilities

    /**
     * Checks if another PeerAddress is equal to this one.
     *
     * @param other the other PeerAddress
     * @return true if they are equal, false otherwise
     */
    public boolean equals(PeerAddress other) {
        if (other == null) {
            return false;
        }

        // Ports must match, and hostnames/IPs must be case-insensitively equal
        return this.port == other.port && this.host.equalsIgnoreCase(other.host);
    }
}
