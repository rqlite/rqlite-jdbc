package io.rqlite.client;

import io.rqlite.json.JsonObject;

import java.io.Serializable;

/**
 * Describes why a statement failed.
 * <p>
 * This is the decoded form of the {@code error_v2} object returned by rqlite v10.6.0 and later.
 * When the response comes from an older rqlite, or the legacy error format was requested, only
 * {@link #message} is set, taken from the {@code error} field. The {@code sqlite} codes are present
 * only when the failure originated in SQLite (for example a constraint violation); they are absent
 * for errors such as a timeout.
 */
public class L4Error implements Serializable {

  private static final long serialVersionUID = 1L;

  /** The error message reported by rqlite. */
  public String message;

  /** The SQLite result code (see https://www.sqlite.org/rescode.html), or null if not SQLite-specific. */
  public Integer code;

  /** The SQLite extended result code, or null if not SQLite-specific. */
  public Integer extendedCode;

  /** The OS errno reported by SQLite, or null when not applicable. */
  public Integer systemErrno;

  public boolean isSqlite() {
    return code != null;
  }

  public static L4Error message(String message) {
    if (message == null) {
      return null;
    }
    var error = new L4Error();
    error.message = message;
    return error;
  }

  /**
   * Builds an error preferring the structured {@code error_v2} object, falling back to the
   * deprecated {@code error} string. Returns null when neither is present.
   */
  public static L4Error parse(JsonObject errorV2, String legacyMessage) {
    if (errorV2 != null) {
      var error = new L4Error();
      error.message = errorV2.get("message") != null
        ? errorV2.getString("message", legacyMessage)
        : legacyMessage;
      var sqlite = errorV2.get("sqlite");
      if (sqlite != null && sqlite.isObject()) {
        var s = sqlite.asObject();
        if (s.get("code") != null) {
          error.code = s.getInt("code", 0);
        }
        if (s.get("extended_code") != null) {
          error.extendedCode = s.getInt("extended_code", 0);
        }
        if (s.get("system_errno") != null) {
          error.systemErrno = s.getInt("system_errno", 0);
        }
      }
      return error;
    }
    return message(legacyMessage);
  }

  @Override public String toString() {
    return message;
  }

}
