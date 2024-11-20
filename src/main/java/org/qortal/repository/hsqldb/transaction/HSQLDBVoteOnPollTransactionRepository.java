package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.VoteOnPollTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBVoteOnPollTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBVoteOnPollTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT poll_name, option_index, previous_option_index FROM VoteOnPollTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String pollName = resultSet.getString(1);
            int optionIndex = resultSet.getInt(2);

            Integer previousOptionIndex = getNullableInteger(resultSet, 3);

            return new VoteOnPollTransactionData(baseTransactionData, pollName, optionIndex, previousOptionIndex);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch vote on poll transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        VoteOnPollTransactionData voteOnPollTransactionData = (VoteOnPollTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("VoteOnPollTransactions")
                .bind("signature", voteOnPollTransactionData.getSignature())
                .bind("poll_name", voteOnPollTransactionData.getPollName())
                .bind("voter", voteOnPollTransactionData.getVoterPublicKey())
                .bind("option_index", voteOnPollTransactionData.getOptionIndex())
                .bind("previous_option_index", voteOnPollTransactionData.getPreviousOptionIndex());

        executeSave(saveHelper);
    }

    // Helper method to execute SQL queries
    private ResultSet executeQuery(String sql, Object... params) throws DataException {
        try {
            return repository.checkedExecute(sql, params);
        } catch (SQLException e) {
            throw new DataException("Error executing query", e);
        }
    }

    // Helper method to retrieve nullable Integer values
    private Integer getNullableInteger(ResultSet resultSet, int columnIndex) throws SQLException {
        int value = resultSet.getInt(columnIndex);
        return (value == 0 && resultSet.wasNull()) ? null : value;
    }

    // Helper method to execute save operations
    private void executeSave(HSQLDBSaver saveHelper) throws DataException {
        try {
            saveHelper.execute(repository);
        } catch (SQLException e) {
            throw new DataException("Error saving transaction into repository", e);
        }
    }
}
