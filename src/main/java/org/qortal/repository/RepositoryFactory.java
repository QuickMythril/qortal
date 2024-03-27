package org.qortal.repository;

import java.sql.SQLException;

public interface RepositoryFactory {

	boolean wasPristineAtOpen();

	RepositoryFactory reopen() throws DataException;

	Repository getRepository() throws DataException;

	Repository tryRepository() throws DataException;

	void close() throws DataException;

	// Not ideal place for this but implementating class will know the answer without having to open a new DB session
    boolean isDeadlockException(SQLException e);

}
