package org.qortal.repository.hsqldb;

import org.qortal.data.voting.PollData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.data.voting.VoteOnPollData;
import org.qortal.repository.DataException;
import org.qortal.repository.VotingRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class HSQLDBVotingRepository implements VotingRepository {

    protected HSQLDBRepository repository;

    public HSQLDBVotingRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    // Polls

    @Override
    public List<PollData> getAllPolls(Integer limit, Integer offset, Boolean reverse) throws DataException {
        if (limit == null || limit <= 0) limit = 100; // Default limit
        if (offset == null || offset < 0) offset = 0;

        StringBuilder sql = new StringBuilder(512);
        sql.append("SELECT p.poll_name, p.description, p.creator, p.owner, p.published_when, po.option_name ")
           .append("FROM Polls p ")
           .append("LEFT JOIN PollOptions po ON p.poll_name = po.poll_name ")
           .append("ORDER BY p.poll_name");

        if (reverse != null && reverse)
            sql.append(" DESC");

        sql.append(" LIMIT ? OFFSET ?");

        Map<String, PollData> pollsMap = new LinkedHashMap<>();

        try (PreparedStatement preparedStatement = this.repository.prepareStatement(sql.toString())) {
            preparedStatement.setInt(1, limit);
            preparedStatement.setInt(2, offset);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    String pollName = resultSet.getString(1);
                    PollData pollData = pollsMap.computeIfAbsent(pollName, k -> {
                        try {
                            return new PollData(
                                resultSet.getBytes(3),
                                resultSet.getString(4),
                                pollName,
                                resultSet.getString(2),
                                new ArrayList<>(),
                                resultSet.getLong(5)
                            );
                        } catch (SQLException e) {
                            throw new RuntimeException(e); // Simplified exception handling
                        }
                    });

                    String optionName = resultSet.getString(6);
                    if (optionName != null)
                        pollData.getPollOptions().add(new PollOptionData(optionName));
                }
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch polls from repository", e);
        }

        return new ArrayList<>(pollsMap.values());
    }

    @Override
    public PollData fromPollName(String pollName) throws DataException {
        String sql = "SELECT p.description, p.creator, p.owner, p.published_when, po.option_name "
                   + "FROM Polls p LEFT JOIN PollOptions po ON p.poll_name = po.poll_name "
                   + "WHERE p.poll_name = ? ORDER BY po.option_index ASC";

        try (PreparedStatement preparedStatement = this.repository.prepareStatement(sql)) {
            preparedStatement.setString(1, pollName);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                PollData pollData = null;
                List<PollOptionData> options = new ArrayList<>();

                while (resultSet.next()) {
                    if (pollData == null) {
                        pollData = new PollData(
                            resultSet.getBytes(2),
                            resultSet.getString(3),
                            pollName,
                            resultSet.getString(1),
                            options,
                            resultSet.getLong(4)
                        );
                    }
                    String optionName = resultSet.getString(5);
                    if (optionName != null)
                        options.add(new PollOptionData(optionName));
                }

                return pollData;
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch poll by name", e);
        }
    }

    @Override
    public boolean pollExists(String pollName) throws DataException {
        try {
            return this.repository.exists("Polls", "poll_name = ?", pollName);
        } catch (SQLException e) {
            throw new DataException("Unable to check for poll in repository", e);
        }
    }

    @Override
    public void save(PollData pollData) throws DataException {
        String pollSql = "INSERT INTO Polls (poll_name, description, creator, owner, published_when) VALUES (?, ?, ?, ?, ?)";
        String optionsSql = "INSERT INTO PollOptions (poll_name, option_index, option_name) VALUES (?, ?, ?)";

        try (PreparedStatement pollStatement = this.repository.prepareStatement(pollSql)) {
            pollStatement.setString(1, pollData.getPollName());
            pollStatement.setString(2, pollData.getDescription());
            pollStatement.setBytes(3, pollData.getCreatorPublicKey());
            pollStatement.setString(4, pollData.getOwner());
            pollStatement.setLong(5, pollData.getPublished());
            pollStatement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Unable to save poll into repository", e);
        }

        try (PreparedStatement optionsStatement = this.repository.prepareStatement(optionsSql)) {
            List<PollOptionData> pollOptions = pollData.getPollOptions();
            for (int index = 0; index < pollOptions.size(); ++index) {
                optionsStatement.setString(1, pollData.getPollName());
                optionsStatement.setInt(2, index);
                optionsStatement.setString(3, pollOptions.get(index).getOptionName());
                optionsStatement.addBatch();
            }
            optionsStatement.executeBatch();
        } catch (SQLException e) {
            throw new DataException("Unable to save poll options into repository", e);
        }
    }

    @Override
    public void delete(String pollName) throws DataException {
        try {
            this.repository.delete("Polls", "poll_name = ?", pollName);
        } catch (SQLException e) {
            throw new DataException("Unable to delete poll from repository", e);
        }
    }

    // Votes

    @Override
    public List<VoteOnPollData> getVotes(String pollName) throws DataException {
        String sql = "SELECT voter, option_index FROM PollVotes WHERE poll_name = ?";

        List<VoteOnPollData> votes = new ArrayList<>();
        try (ResultSet resultSet = this.repository.checkedExecute(sql, pollName)) {
            while (resultSet.next()) {
                votes.add(new VoteOnPollData(
                    pollName,
                    resultSet.getBytes(1),
                    resultSet.getInt(2)
                ));
            }
            return votes;
        } catch (SQLException e) {
            throw new DataException("Unable to fetch poll votes from repository", e);
        }
    }

    @Override
    public VoteOnPollData getVote(String pollName, byte[] voterPublicKey) throws DataException {
        String sql = "SELECT option_index FROM PollVotes WHERE poll_name = ? AND voter = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, pollName, voterPublicKey)) {
            if (resultSet.next()) {
                return new VoteOnPollData(
                    pollName,
                    voterPublicKey,
                    resultSet.getInt(1)
                );
            }
            return null;
        } catch (SQLException e) {
            throw new DataException("Unable to fetch poll vote from repository", e);
        }
    }

    @Override
    public void save(VoteOnPollData voteOnPollData) throws DataException {
        String sql = "INSERT INTO PollVotes (poll_name, voter, option_index) VALUES (?, ?, ?)";

        try (PreparedStatement statement = this.repository.prepareStatement(sql)) {
            statement.setString(1, voteOnPollData.getPollName());
            statement.setBytes(2, voteOnPollData.getVoterPublicKey());
            statement.setInt(3, voteOnPollData.getOptionIndex());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Unable to save poll vote into repository", e);
        }
    }

    @Override
    public void delete(String pollName, byte[] voterPublicKey) throws DataException {
        try {
            this.repository.delete("PollVotes", "poll_name = ? AND voter = ?", pollName, voterPublicKey);
        } catch (SQLException e) {
            throw new DataException("Unable to delete poll vote from repository", e);
        }
    }
}
