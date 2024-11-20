package org.qortal.repository.hsqldb;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Database helper for building and executing INSERT INTO ... ON DUPLICATE KEY UPDATE ... statements.
 */
public class HSQLDBSaver {

    private static final Calendar UTC_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private final String table;
    private final List<String> columns = new ArrayList<>();
    private final List<Object> values = new ArrayList<>();

    /**
     * Construct a SaveHelper for the given table name.
     *
     * @param table Table name for the SQL operations.
     */
    public HSQLDBSaver(String table) {
        this.table = table;
    }

    /**
     * Adds a column and its corresponding value to the query.
     *
     * @param column Column name.
     * @param value  Value to bind to the column.
     * @return The same HSQLDBSaver object for chaining.
     */
    public HSQLDBSaver bind(String column, Object value) {
        columns.add(column);
        values.add(value);
        return this;
    }

    /**
     * Builds and executes the SQL query.
     *
     * @param repository Repository to execute the query.
     * @return The result of {@link PreparedStatement#execute()}.
     * @throws SQLException If an SQL error occurs.
     */
    public boolean execute(HSQLDBRepository repository) throws SQLException {
        String sql = buildQuery();

        synchronized (HSQLDBRepository.CHECKPOINT_LOCK) {
            try (PreparedStatement preparedStatement = repository.prepareStatement(sql)) {
                bindValues(preparedStatement);
                return preparedStatement.execute();
            } catch (SQLException e) {
                throw repository.examineException(e);
            }
        }
    }

    /**
     * Constructs the SQL query string with placeholders.
     *
     * @return A complete SQL query string.
     */
    private String buildQuery() {
        String columnList = String.join(", ", columns);
        String valuePlaceholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String updatePlaceholders = String.join(", ", columns.stream().map(col -> col + "=?").toArray(String[]::new));

        return String.format(
            "INSERT INTO %s (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s",
            table, columnList, valuePlaceholders, updatePlaceholders
        );
    }

    /**
     * Binds the values to the prepared statement.
     *
     * @param preparedStatement The prepared statement to bind values to.
     * @throws SQLException If an SQL error occurs.
     */
    private void bindValues(PreparedStatement preparedStatement) throws SQLException {
        int size = values.size();

        for (int i = 0; i < size; i++) {
            bindValue(preparedStatement, i + 1, values.get(i)); // Bind for INSERT
            bindValue(preparedStatement, size + i + 1, values.get(i)); // Bind for UPDATE
        }
    }

    /**
     * Binds a single value to the prepared statement, handling special types.
     *
     * @param preparedStatement The prepared statement.
     * @param index             The index to bind at.
     * @param value             The value to bind.
     * @throws SQLException If an SQL error occurs.
     */
    private void bindValue(PreparedStatement preparedStatement, int index, Object value) throws SQLException {
        if (value instanceof BigDecimal) {
            // Retain scale for BigDecimal values.
            preparedStatement.setBigDecimal(index, (BigDecimal) value);
        } else if (value instanceof Timestamp) {
            // Store timestamps in UTC.
            preparedStatement.setTimestamp(index, (Timestamp) value, UTC_CALENDAR);
        } else {
            // Bind as generic object.
            preparedStatement.setObject(index, value);
        }
    }
}
