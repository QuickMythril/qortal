package org.qortal.repository.hsqldb;

import org.qortal.data.network.PeerData;
import org.qortal.network.PeerAddress;
import org.qortal.repository.DataException;
import org.qortal.repository.NetworkRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HSQLDBNetworkRepository implements NetworkRepository {

    private static final String TABLE_NAME = "Peers";
    protected final HSQLDBRepository repository;

    public HSQLDBNetworkRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PeerData> getAllPeers() throws DataException {
        String sql = "SELECT address, last_connected, last_attempted, last_misbehaved, added_when, added_by FROM " + TABLE_NAME;
        List<PeerData> peers = new ArrayList<>();

        try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
            if (resultSet == null) {
                return peers;
            }

            do {
                PeerData peerData = parsePeerData(resultSet);
                peers.add(peerData);
            } while (resultSet.next());

        } catch (IllegalArgumentException e) {
            throw new DataException("Invalid peer data encountered in repository", e);
        } catch (SQLException e) {
            throw new DataException("Error fetching peers from repository", e);
        }

        return peers;
    }

    @Override
    public void save(PeerData peerData) throws DataException {
        try {
            new HSQLDBSaver(TABLE_NAME)
                .bind("address", peerData.getAddress().toString())
                .bind("last_connected", peerData.getLastConnected())
                .bind("last_attempted", peerData.getLastAttempted())
                .bind("last_misbehaved", peerData.getLastMisbehaved())
                .bind("added_when", peerData.getAddedWhen())
                .bind("added_by", peerData.getAddedBy())
                .execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Error saving peer into repository", e);
        }
    }

    @Override
    public int delete(PeerAddress peerAddress) throws DataException {
        return executeDelete("address = ?", peerAddress.toString());
    }

    @Override
    public int deleteAllPeers() throws DataException {
        return executeDelete(null);
    }

    // Private helper methods

    private PeerData parsePeerData(ResultSet resultSet) throws SQLException {
        String address = resultSet.getString(1);
        PeerAddress peerAddress = PeerAddress.fromString(address);

        Long lastConnected = getNullableLong(resultSet, 2);
        Long lastAttempted = getNullableLong(resultSet, 3);
        Long lastMisbehaved = getNullableLong(resultSet, 4);
        Long addedWhen = getNullableLong(resultSet, 5);
        String addedBy = resultSet.getString(6);

        return new PeerData(peerAddress, lastAttempted, lastConnected, lastMisbehaved, addedWhen, addedBy);
    }

    private Long getNullableLong(ResultSet resultSet, int columnIndex) throws SQLException {
        long value = resultSet.getLong(columnIndex);
        return resultSet.wasNull() ? null : value;
    }

    private int executeDelete(String whereClause, String... params) throws DataException {
        try {
            return whereClause == null
                ? this.repository.delete(TABLE_NAME)
                : this.repository.delete(TABLE_NAME, whereClause, params);
        } catch (SQLException e) {
            throw new DataException("Error deleting peer(s) from repository", e);
        }
    }
}
