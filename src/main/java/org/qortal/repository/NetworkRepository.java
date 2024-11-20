package org.qortal.repository;

import org.qortal.data.network.PeerData;
import org.qortal.network.PeerAddress;

import java.util.List;
import java.util.Optional;

public interface NetworkRepository {

    public List<PeerData> getAllPeers() throws DataException;
    public void save(PeerData peerData) throws DataException;
    public boolean delete(PeerAddress peerAddress) throws DataException;
    public int deleteAllPeers() throws DataException;
    public Optional<PeerData> getPeerByAddress(PeerAddress peerAddress) throws DataException;
}
