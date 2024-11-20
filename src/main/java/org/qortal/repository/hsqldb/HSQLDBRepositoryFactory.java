package org.qortal.repository.hsqldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hsqldb.HsqlException;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.jdbc.HSQLDBPool;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryFactory;
import org.qortal.settings.Settings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class HSQLDBRepositoryFactory implements RepositoryFactory {

    private static final Logger LOGGER = LogManager.getLogger(HSQLDBRepositoryFactory.class);

    /** Log getConnection() calls that take longer than this (ms). */
    private static final long SLOW_CONNECTION_THRESHOLD = 1000L;

    private final String connectionUrl;
    private final HSQLDBPool connectionPool;
    private final boolean wasPristine;

    /**
     * Constructs a new RepositoryFactory using the given connection URL.
     *
     * @param connectionUrl Database connection URL.
     * @throws DataException if the repository is in use or cannot be opened.
     */
    public HSQLDBRepositoryFactory(String connectionUrl) throws DataException {
        this.connectionUrl = connectionUrl;

        ensureDatabaseAccessible();
        this.connectionPool = initializeConnectionPool();

        try (Connection connection = this.connectionPool.getConnection()) {
            this.wasPristine = HSQLDBDatabaseUpdates.updateDatabase(connection);
        } catch (SQLException e) {
            throw new DataException("Repository initialization error", e);
        }
    }

    @Override
    public boolean wasPristineAtOpen() {
        return this.wasPristine;
    }

    @Override
    public RepositoryFactory reopen() throws DataException {
        return new HSQLDBRepositoryFactory(this.connectionUrl);
    }

    @Override
    public Repository getRepository() throws DataException {
        try {
            return new HSQLDBRepository(getConnection());
        } catch (SQLException e) {
            throw new DataException("Repository instantiation error", e);
        }
    }

    @Override
    public Repository tryRepository() throws DataException {
        try {
            Connection connection = tryConnection();
            if (connection == null) {
                return null;
            }
            return new HSQLDBRepository(connection);
        } catch (SQLException e) {
            throw new DataException("Repository instantiation error", e);
        }
    }

    @Override
    public void close() throws DataException {
        try {
            this.connectionPool.close(0);
            shutdownDatabase();
        } catch (SQLException e) {
            throw new DataException("Error during repository shutdown", e);
        }
    }

    @Override
    public boolean isDeadlockException(SQLException e) {
        return HSQLDBRepository.isDeadlockException(e);
    }

    /**
     * Ensures the database can be accessed.
     *
     * @throws DataException If the database is locked or inaccessible.
     */
    private void ensureDatabaseAccessible() throws DataException {
        try (Connection connection = DriverManager.getConnection(this.connectionUrl)) {
            // Test connection; it will be auto-closed.
        } catch (SQLException e) {
            handleDatabaseAccessException(e);
        }
    }

    /**
     * Handles database access exceptions during initialization.
     *
     * @param e SQLException thrown during connection attempt.
     * @throws DataException if the repository is locked or an unrecoverable error occurs.
     */
    private void handleDatabaseAccessException(SQLException e) throws DataException {
        Throwable cause = e.getCause();

        if (cause instanceof HsqlException he) {
            int errorCode = he.getErrorCode();
            if (errorCode == -ErrorCode.LOCK_FILE_ACQUISITION_FAILURE) {
                throw new DataException("Unable to lock repository: " + e.getMessage());
            }
            if (errorCode != -ErrorCode.ERROR_IN_LOG_FILE && errorCode != -ErrorCode.M_DatabaseScriptReader_read) {
                throw new DataException("Unable to read repository: " + e.getMessage(), e);
            }
            HSQLDBRepository.attemptRecovery(connectionUrl, "backup");
        } else {
            throw new DataException("Unable to open repository: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes the connection pool.
     *
     * @return A configured HSQLDBPool instance.
     */
    private HSQLDBPool initializeConnectionPool() {
        HSQLDBPool pool = new HSQLDBPool(Settings.getInstance().getRepositoryConnectionPoolSize());
        pool.setUrl(this.connectionUrl);

        Properties properties = new Properties();
        properties.setProperty("close_result", "true"); // Auto-close old ResultSet if Statement creates new ResultSet
        pool.setProperties(properties);

        return pool;
    }

    /**
     * Retrieves a connection from the pool with logging for slow connections.
     *
     * @return A configured SQL connection.
     * @throws SQLException If a connection cannot be obtained.
     */
    private Connection getConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        Connection connection = this.connectionPool.getConnection();
        logSlowConnection(startTime);
        configureConnection(connection);
        return connection;
    }

    /**
     * Attempts to retrieve a connection from the pool.
     *
     * @return A configured SQL connection, or null if no connection is available.
     * @throws SQLException If a connection cannot be configured.
     */
    private Connection tryConnection() throws SQLException {
        Connection connection = this.connectionPool.tryConnection();
        if (connection != null) {
            configureConnection(connection);
        }
        return connection;
    }

    /**
     * Configures a SQL connection for repository use.
     *
     * @param connection The connection to configure.
     * @throws SQLException If the configuration fails.
     */
    private void configureConnection(Connection connection) throws SQLException {
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        connection.setAutoCommit(false);
    }

    /**
     * Logs slow connection retrievals.
     *
     * @param startTime The start time of the connection retrieval.
     */
    private void logSlowConnection(long startTime) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (elapsedTime > SLOW_CONNECTION_THRESHOLD) {
            LOGGER.warn("Connection retrieval took {}ms (threshold: {}ms)", elapsedTime, SLOW_CONNECTION_THRESHOLD);
        }
    }

    /**
     * Shuts down the database by issuing a SHUTDOWN command.
     *
     * @throws SQLException If the shutdown command fails.
     */
    private void shutdownDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(this.connectionUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }
}
