package org.qortal.repository;

import org.qortal.data.network.PeerData;
import org.qortal.network.PeerAddress;

import java.util.List;

public interface NetworkRepository {

	List<PeerData> getAllPeers() throws DataException;

	void save(PeerData peerData) throws DataException;

	int delete(PeerAddress peerAddress) throws DataException;

	int deleteAllPeers() throws DataException;

}
