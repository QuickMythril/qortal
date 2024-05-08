package org.qortal.crosschain;

public interface ChainableServer {
    void addResponseTime(long responseTime);

    long averageResponseTime();

    String getHostName();

    int getPort();

    ConnectionType getConnectionType();

    enum ConnectionType {TCP, SSL}
}
