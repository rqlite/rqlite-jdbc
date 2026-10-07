package io.rqlite;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Err;
import io.rqlite.client.L4Statement;
import io.rqlite.jdbc.L4Jdbc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Loads a trimmed Chinook database into rqlite for regression testing.
 *
 * <p>Tables are dropped in child-to-parent order because rqlite runs with foreign key
 * enforcement enabled. Loading is idempotent and safe to run repeatedly against a shared
 * server.
 */
public class ChinookFixture {

  /** Child-to-parent drop order (respects foreign keys). */
  public static final String[] DROP_ORDER = {
    "InvoiceLine", "PlaylistTrack", "Track", "Album", "Invoice",
    "Customer", "Employee", "Playlist", "Genre", "MediaType", "Artist"
  };

  private static String read(String resource) {
    try (InputStream is = ChinookFixture.class.getResourceAsStream(resource)) {
      if (is == null) {
        throw new IllegalStateException("Missing resource: " + resource);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static void load(L4Client client) {
    var statements = new ArrayList<L4Statement>();
    for (var table : DROP_ORDER) {
      statements.add(new L4Statement().sql("DROP TABLE IF EXISTS [" + table + "]"));
    }
    statements.addAll(java.util.Arrays.asList(L4Jdbc.split(read("/chinook/chinook-schema.sql"))));
    statements.addAll(java.util.Arrays.asList(L4Jdbc.split(read("/chinook/chinook-data.sql"))));

    var response = client.execute(true, statements.toArray(new L4Statement[0]));
    if (response.results != null) {
      for (var result : response.results) {
        L4Err.checkResult(result);
      }
    }
  }

}
