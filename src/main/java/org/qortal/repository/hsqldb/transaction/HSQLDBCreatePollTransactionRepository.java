package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CreatePollTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HSQLDBCreatePollTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBCreatePollTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT owner, poll_name, description FROM CreatePollTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Return null if no result found
            }

            String owner = resultSet.getString("owner");
            String pollName = resultSet.getString("poll_name");
            String description = resultSet.getString("description");

            List<PollOptionData> pollOptions = fetchPollOptions(baseTransactionData.getSignature());

            return new CreatePollTransactionData(baseTransactionData, owner, pollName, description, pollOptions);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch create poll transaction from repository", e);
        }
    }

    private List<PollOptionData> fetchPollOptions(String signature) throws DataException {
        String optionsSql = "SELECT option_name FROM CreatePollTransactionOptions WHERE signature = ? ORDER BY option_index ASC";
        List<PollOptionData> pollOptions = new ArrayList<>();

        try (ResultSet optionsResultSet = this.repository.checkedExecute(optionsSql, signature)) {
            while (optionsResultSet != null && optionsResultSet.next()) {
                String optionName = optionsResultSet.getString("option_name");
                pollOptions.add(new PollOptionData(optionName));
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch poll options from repository", e);
        }

        return pollOptions;
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        CreatePollTransactionData createPollTransactionData = (CreatePollTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("CreatePollTransactions")) {
            saveHelper.bind("signature", createPollTransactionData.getSignature())
                    .bind("creator", createPollTransactionData.getCreatorPublicKey())
                    .bind("owner", createPollTransactionData.getOwner())
                    .bind("poll_name", createPollTransactionData.getPollName())
                    .bind("description", createPollTransactionData.getDescription());
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save create poll transaction into repository", e);
        }

        savePollOptions(createPollTransactionData);
    }

    private void savePollOptions(CreatePollTransactionData createPollTransactionData) throws DataException {
        List<PollOptionData> pollOptions = createPollTransactionData.getPollOptions();

        try (HSQLDBSaver optionSaveHelper = new HSQLDBSaver("CreatePollTransactionOptions")) {
            for (int optionIndex = 0; optionIndex < pollOptions.size(); optionIndex++) {
                PollOptionData pollOptionData = pollOptions.get(optionIndex);

                optionSaveHelper.bind("signature", createPollTransactionData.getSignature())
                        .bind("option_name", pollOptionData.getOptionName())
                        .bind("option_index", optionIndex);

                optionSaveHelper.execute(this.repository);  // Execute for each option
            }
        } catch (SQLException e) {
            throw new DataException("Unable to save poll options into repository", e);
        }
    }
}
