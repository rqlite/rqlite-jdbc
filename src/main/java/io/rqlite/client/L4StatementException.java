package io.rqlite.client;

/**
 * Signals a database-level statement failure, carrying the decoded {@link L4Error} (message and,
 * when available, SQLite result codes) so the JDBC layer can map it to a standard {@code SQLState}
 * and vendor error code.
 */
public class L4StatementException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  public final L4Error error;

  public L4StatementException(L4Error error) {
    super(error != null ? error.message : null);
    this.error = error;
  }

}
