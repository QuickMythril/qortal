package org.qortal.repository.hsqldb;

import org.qortal.data.naming.NameData;
import org.qortal.repository.DataException;
import org.qortal.repository.NameRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HSQLDBNameRepository implements NameRepository {

    private static final String TABLE_NAME = "Names";
    private final HSQLDBRepository repository;

    public HSQLDBNameRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public NameData fromName(String name) throws DataException {
        return getNameData("name = ?", name);
    }

    @Override
    public boolean nameExists(String name) throws DataException {
        return checkExists("name = ?", name);
    }

    @Override
    public NameData fromReducedName(String reducedName) throws DataException {
        return getNameData("reduced_name = ?", reducedName);
    }

    @Override
    public boolean reducedNameExists(String reducedName) throws DataException {
        return checkExists("reduced_name = ?", reducedName);
    }

    @Override
    public List<NameData> searchNames(String query, boolean prefixOnly, Integer limit, Integer offset, Boolean reverse) throws DataException {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME + " WHERE LCASE(name) LIKE ?");
        List<Object> bindParams = new ArrayList<>();
        bindParams.add(prefixOnly ? query.toLowerCase() + "%" : "%" + query.toLowerCase() + "%");

        appendOrderAndPagination(sql, reverse, limit, offset);

        return getNameDataList(sql.toString(), bindParams.toArray());
    }

    @Override
    public List<NameData> getAllNames(Long after, Integer limit, Integer offset, Boolean reverse) throws DataException {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME);
        List<Object> bindParams = new ArrayList<>();

        if (after != null) {
            sql.append(" WHERE registered_when > ? OR updated_when > ?");
            bindParams.add(after);
            bindParams.add(after);
        }

        appendOrderAndPagination(sql, reverse, limit, offset);

        return getNameDataList(sql.toString(), bindParams.toArray());
    }

    @Override
    public List<NameData> getNamesForSale(Integer limit, Integer offset, Boolean reverse) throws DataException {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME + " WHERE is_for_sale = TRUE");
        appendOrderAndPagination(sql, reverse, limit, offset);

        return getNameDataList(sql.toString());
    }

    @Override
    public List<NameData> getNamesByOwner(String owner, Integer limit, Integer offset, Boolean reverse) throws DataException {
        StringBuilder sql = new StringBuilder("SELECT * FROM " + TABLE_NAME + " WHERE owner = ?");
        appendOrderAndPagination(sql, reverse, limit, offset);

        return getNameDataList(sql.toString(), owner);
    }

    @Override
    public List<String> getRecentNames(long startTimestamp) throws DataException {
        String sql = "SELECT name FROM RegisterNameTransactions JOIN " + TABLE_NAME +
                     " USING (name) JOIN Transactions USING (signature) WHERE created_when >= ?";

        List<String> names = new ArrayList<>();

        try (ResultSet resultSet = this.repository.checkedExecute(sql, startTimestamp)) {
            while (resultSet != null && resultSet.next()) {
                names.add(resultSet.getString(1));
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch recent names from repository", e);
        }

        return names;
    }

    @Override
    public void save(NameData nameData) throws DataException {
        try {
            new HSQLDBSaver(TABLE_NAME)
                .bind("name", nameData.getName())
                .bind("reduced_name", nameData.getReducedName())
                .bind("owner", nameData.getOwner())
                .bind("data", nameData.getData())
                .bind("registered_when", nameData.getRegistered())
                .bind("updated_when", nameData.getUpdated())
                .bind("is_for_sale", nameData.isForSale())
                .bind("sale_price", nameData.getSalePrice())
                .bind("reference", nameData.getReference())
                .bind("creation_group_id", nameData.getCreationGroupId())
                .execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save name info into repository", e);
        }
    }

    @Override
    public void delete(String name) throws DataException {
        try {
            this.repository.delete(TABLE_NAME, "name = ?", name);
        } catch (SQLException e) {
            throw new DataException("Unable to delete name info from repository", e);
        }
    }

    // Private helper methods

    private NameData getNameData(String condition, Object... params) throws DataException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + condition;

        try (ResultSet resultSet = this.repository.checkedExecute(sql, params)) {
            if (resultSet != null && resultSet.next()) {
                return parseNameData(resultSet);
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch name info from repository", e);
        }

        return null;
    }

    private List<NameData> getNameDataList(String sql, Object... params) throws DataException {
        List<NameData> nameDataList = new ArrayList<>();

        try (ResultSet resultSet = this.repository.checkedExecute(sql, params)) {
            while (resultSet != null && resultSet.next()) {
                nameDataList.add(parseNameData(resultSet));
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch name data from repository", e);
        }

        return nameDataList;
    }

    private NameData parseNameData(ResultSet resultSet) throws SQLException {
        String name = resultSet.getString("name");
        String reducedName = resultSet.getString("reduced_name");
        String owner = resultSet.getString("owner");
        String data = resultSet.getString("data");
        long registeredWhen = resultSet.getLong("registered_when");
        Long updatedWhen = getNullableLong(resultSet, "updated_when");
        boolean isForSale = resultSet.getBoolean("is_for_sale");
        Long salePrice = getNullableLong(resultSet, "sale_price");
        byte[] reference = resultSet.getBytes("reference");
        int creationGroupId = resultSet.getInt("creation_group_id");

        return new NameData(name, reducedName, owner, data, registeredWhen, updatedWhen, isForSale, salePrice, reference, creationGroupId);
    }

    private Long getNullableLong(ResultSet resultSet, String columnLabel) throws SQLException {
        long value = resultSet.getLong(columnLabel);
        return resultSet.wasNull() ? null : value;
    }

    private boolean checkExists(String condition, Object... params) throws DataException {
        try {
            return this.repository.exists(TABLE_NAME, condition, params);
        } catch (SQLException e) {
            throw new DataException("Unable to check existence in repository", e);
        }
    }

    private void appendOrderAndPagination(StringBuilder sql, Boolean reverse, Integer limit, Integer offset) {
        sql.append(" ORDER BY name");
        if (Boolean.TRUE.equals(reverse)) {
            sql.append(" DESC");
        }
        HSQLDBRepository.limitOffsetSql(sql, limit, offset);
    }
}
