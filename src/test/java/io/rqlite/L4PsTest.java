package io.rqlite;

import io.rqlite.client.L4Client;
import io.rqlite.jdbc.L4Log;
import io.rqlite.jdbc.L4NClob;
import io.rqlite.jdbc.L4Ps;
import io.rqlite.jdbc.L4Utc;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.sql.Date;
import java.time.ZoneId;
import java.util.*;

import static io.rqlite.L4Tests.*;
import static io.rqlite.jdbc.L4Err.*;
import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4PsTest {

  private static final L4Client rq = L4Tests.localClient();

  private static boolean inUtcZone() {
    return ZoneId.systemDefault().equals(L4Utc.UtcZid);
  }

  static {
    if (L4Tests.runIntegrationTests) {
      L4Tests.initLogging();
      L4Log.info("===============================");
      L4Log.info(ZoneId.systemDefault().toString());
      L4Log.info("===============================");

      it("Tests L4Ps query execution and parameter setting", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (" +
          "num_val, bool_val, tiny_val, small_val, int_val, big_val, float_val, double_val, " +
          "text_val, date_val, time_val, ts_val, url_val, clob_val, nclob_val, nstring_val, blob_val" +
          ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Set parameters
        var blobData = "Hello, rqlite!".getBytes(StandardCharsets.UTF_8);
        ps.setBigDecimal(1, new BigDecimal("123.45")); // num_val
        ps.setBoolean(2, true); // bool_val
        ps.setByte(3, (byte) 127); // tiny_val
        ps.setShort(4, (short) 32767); // small_val
        ps.setInt(5, 2147483647); // int_val
        ps.setLong(6, 9223372036854775807L); // big_val
        ps.setFloat(7, 3.14f); // float_val
        ps.setDouble(8, 2.71828); // double_val
        ps.setString(9, "Hello, world!"); // text_val
        ps.setDate(10, Date.valueOf("2023-10-15")); // date_val
        ps.setTime(11, Time.valueOf("14:30:00")); // time_val
        ps.setTimestamp(12, Timestamp.valueOf("2023-10-15 10:30:00")); // ts_val
        ps.setURL(13, new URI("https://example.com").toURL()); // url_val
        ps.setString(14, "This is a CLOB"); // clob_val
        ps.setNString(15, "This is an NCLOB"); // nclob_val
        ps.setNString(16, "This is an NSTRING"); // nstring_val
        ps.setBytes(17, blobData); // blob_val

        // Execute update
        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Query and verify
        var selectPs = new L4Ps(rq, "SELECT * FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals(new BigDecimal("123.45"), rs.getBigDecimal("num_val"));
        assertTrue(rs.getBoolean("bool_val"));
        assertEquals(127, rs.getByte("tiny_val"));
        assertEquals(32767, rs.getShort("small_val"));
        assertEquals(2147483647, rs.getInt("int_val"));
        assertEquals(9223372036854775807L, rs.getLong("big_val"));
        assertEquals(3.14f, rs.getFloat("float_val"), 0.001f);
        assertEquals(2.71828, rs.getDouble("double_val"), 0.001);
        assertEquals("Hello, world!", rs.getString("text_val"));
        assertEquals(
          L4Utc.utcOf(Date.valueOf("2023-10-15")),
          L4Utc.utcOf(rs.getDate("date_val"))
        );
        assertEquals(
          L4Utc.utcOf(Date.valueOf("2023-10-15")).toString(),
          rs.getDate("date_val", Calendar.getInstance()).toString()
        );
        assertEquals(
          L4Utc.utcOf(Time.valueOf("14:30:00")),
          rs.getTime("time_val").toLocalTime()
        );
        assertEquals(
          Timestamp.valueOf("2023-10-15 10:30:00"),
          rs.getTimestamp("ts_val")
        );
        assertEquals(new URI("https://example.com").toURL(), rs.getURL("url_val"));
        assertEquals("This is a CLOB", rs.getClob("clob_val").getSubString(1, 14));
        assertEquals("This is an NCLOB", rs.getNClob("nclob_val").getSubString(1, 16));
        assertEquals("This is an NSTRING", rs.getNString("nstring_val"));
        assertArrayEquals(blobData, rs.getBytes("blob_val"));
        assertFalse(rs.next());
        rs.close();
        ps.close();
        selectPs.close();
      });

      it("Tests L4Ps stream and LOB parameter setting", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (text_val, clob_val, nclob_val, nstring_val, blob_val) VALUES (?, ?, ?, ?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Set stream and LOB parameters
        var text = "Stream text";
        var clobText = "CLOB content";
        var nclobText = "NCLOB content";
        var nstringText = "NSTRING content";
        var blobData = "BLOB data".getBytes(StandardCharsets.UTF_8);

        var clob = new L4NClob();
        clob.setString(1, nclobText);

        ps.setAsciiStream(1, new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)), text.length());
        ps.setClob(2, new javax.sql.rowset.serial.SerialClob(clobText.toCharArray()));
        ps.setNClob(3, clob);
        ps.setNCharacterStream(4, new StringReader(nstringText), nstringText.length());
        ps.setBinaryStream(5, new ByteArrayInputStream(blobData), blobData.length);

        // Execute update
        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify
        var selectPs = new L4Ps(rq, "SELECT text_val, clob_val, nclob_val, nstring_val, blob_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(text, rs.getString("text_val"));
        assertEquals(clobText, rs.getClob("clob_val").getSubString(1, clobText.length()));
        assertEquals(nclobText, rs.getNClob("nclob_val").getSubString(1, nclobText.length()));
        assertEquals(nstringText, rs.getNString("nstring_val"));
        assertArrayEquals(blobData, rs.getBytes("blob_val"));
        assertFalse(rs.next());
        rs.close();
        ps.close();
        selectPs.close();
      });

      it("Tests L4Ps batch execution", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (num_val, text_val) VALUES (?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Add batch entries
        ps.setBigDecimal(1, new BigDecimal("111.11"));
        ps.setString(2, "First");
        ps.addBatch();
        ps.setBigDecimal(1, new BigDecimal("222.22"));
        ps.setString(2, "Second");
        ps.addBatch();

        // Execute batch
        int[] updateCounts = ps.executeBatch();
        assertArrayEquals(new int[]{1, 1}, updateCounts);

        // Verify
        var selectPs = new L4Ps(rq, "SELECT num_val, text_val FROM ps_test_data ORDER BY id");
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(new BigDecimal("111.11"), rs.getBigDecimal("num_val"));
        assertEquals("First", rs.getString("text_val"));
        assertTrue(rs.next());
        assertEquals(new BigDecimal("222.22"), rs.getBigDecimal("num_val"));
        assertEquals("Second", rs.getString("text_val"));
        assertFalse(rs.next());
        rs.close();

        // Test empty batch
        ps.clearBatch();
        updateCounts = ps.executeBatch();
        assertArrayEquals(new int[0], updateCounts);

        ps.close();
        selectPs.close();
      });

      it("Tests L4Ps metadata retrieval", () -> {
        setupPreparedStatementTestTable(rq);
        var selectSql = "SELECT * FROM ps_test_data WHERE id = ?";
        var ps = new L4Ps(rq, selectSql);

        // Test getMetaData
        ps.setInt(1, 999);
        var meta = ps.getMetaData();

        assertEquals(18, meta.getColumnCount());
        assertEquals(Types.INTEGER, meta.getColumnType(1)); // id
        assertEquals(Types.NUMERIC, meta.getColumnType(2)); // num_val
        assertEquals(Types.BOOLEAN, meta.getColumnType(3)); // bool_val
        assertEquals("id", meta.getColumnName(1));
        assertNotNull(ps.getParameterMetaData());

        ps.close();
      });

      it("Tests L4Ps null and object parameter setting", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (num_val, text_val, blob_val) VALUES (?, ?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Set null and object parameters
        ps.setNull(1, Types.NUMERIC);
        ps.setObject(2, "Test object", Types.VARCHAR);
        ps.setObject(3, null, Types.BLOB);
        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify
        var selectPs = new L4Ps(rq, "SELECT num_val, text_val, blob_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertNull(rs.getBigDecimal("num_val"));
        assertTrue(rs.wasNull());
        assertEquals("Test object", rs.getString("text_val"));
        assertNull(rs.getBytes("blob_val"));
        assertTrue(rs.wasNull());
        assertFalse(rs.next());
        rs.close();
        ps.close();
        selectPs.close();
      });

      it("Tests L4Ps advanced stream and LOB methods", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (text_val, clob_val, nclob_val, nstring_val, blob_val) VALUES (?, ?, ?, ?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Set advanced stream and LOB parameters
        var text = "Advanced stream";
        var clobText = "Advanced CLOB";
        var nclobText = "Advanced NCLOB";
        var nstringText = "Advanced NSTRING";
        var blobData = "Advanced BLOB".getBytes(StandardCharsets.UTF_8);

        ps.setAsciiStream(1, new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)));
        ps.setClob(2, new StringReader(clobText));
        ps.setNClob(3, new StringReader(nclobText));
        ps.setNCharacterStream(4, new StringReader(nstringText));
        ps.setBlob(5, new ByteArrayInputStream(blobData));

        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify
        var selectPs = new L4Ps(rq, "SELECT text_val, clob_val, nclob_val, nstring_val, blob_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(text, rs.getString("text_val"));
        assertEquals(clobText, rs.getClob("clob_val").getSubString(1, clobText.length()));
        assertEquals(nclobText, rs.getNClob("nclob_val").getSubString(1, nclobText.length()));
        assertEquals(nstringText, rs.getNString("nstring_val"));
        assertArrayEquals(blobData, rs.getBytes("blob_val"));
        assertFalse(rs.next());
        rs.close();
        ps.close();
        selectPs.close();
      });

      it("Tests L4Ps date and time with calendar", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (date_val, time_val, ts_val) VALUES (?, ?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Set date and time with calendar
        var utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        var date = Date.valueOf("2023-10-15");
        var time = Time.valueOf("14:30:00");
        var timestamp = Timestamp.valueOf("2023-10-15 14:30:00");

        ps.setDate(1, date, utcCalendar);
        ps.setTime(2, time, utcCalendar);
        ps.setTimestamp(3, timestamp, utcCalendar);

        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify
        var selectPs = new L4Ps(rq, "SELECT date_val, time_val, ts_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(
          L4Utc.utcOf(Date.valueOf("2023-10-15")),
          L4Utc.utcOf(rs.getDate("date_val", utcCalendar))
        );
        assertEquals(time, rs.getTime("time_val", utcCalendar));
        assertEquals(timestamp, rs.getTimestamp("ts_val", utcCalendar));
        assertFalse(rs.next());
        rs.close();
        ps.close();
        selectPs.close();
      });

      it("Tests L4Ps batch with error", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (num_val) VALUES (?)";
        var ps = new L4Ps(rq, insertSql);

        // Add valid batch entry
        ps.setBigDecimal(1, new BigDecimal("333.33"));
        ps.addBatch();

        // Add invalid batch entry (invalid syntax)
        var invalidPs = new L4Ps(rq, "INSERT INTO nonexistent_table (num_val) VALUES (?) WHERE WHERE");
        invalidPs.setBigDecimal(1, new BigDecimal("444.44"));
        invalidPs.addBatch();

        // Execute batch with error
        try {
          invalidPs.executeBatch();
          fail("Expected BatchUpdateException");
        } catch (BatchUpdateException e) {
          assertEquals(SqlStateConnectionError, e.getSQLState());
          assertArrayEquals(new int[]{}, e.getUpdateCounts());
        }

        // Verify valid batch
        int[] updateCounts = ps.executeBatch();
        assertArrayEquals(new int[]{1}, updateCounts);

        var selectPs = new L4Ps(rq, "SELECT num_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(new BigDecimal("333.33"), rs.getBigDecimal("num_val"));
        assertFalse(rs.next());
        rs.close();
        ps.close();
        invalidPs.close();
        selectPs.close();
      });

      it("Tests L4Ps closeOnCompletion and inherited methods", () -> {
        setupPreparedStatementTestTable(rq);
        var selectSql = "SELECT * FROM ps_test_data WHERE id = ?";
        var ps = new L4Ps(rq, selectSql);

        // Test closeOnCompletion
        assertFalse(ps.isCloseOnCompletion());
        ps.closeOnCompletion();
        assertTrue(ps.isCloseOnCompletion());
        ps.setInt(1, 999); // Non-existent ID
        var rs = ps.executeQuery();
        assertTrue(ps.isClosed());

        // Test inherited methods
        ps = new L4Ps(rq, selectSql);
        ps.setMaxRows(1);
        assertEquals(1, ps.getMaxRows());
        ps.setQueryTimeout(10);
        assertEquals(10, ps.getQueryTimeout());
        ps.setFetchSize(10);
        assertEquals(10, ps.getFetchSize());
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, ps.getResultSetType());
        assertEquals(ResultSet.CONCUR_READ_ONLY, ps.getResultSetConcurrency());

        ps.close();
      });

      it("Tests L4Ps unsupported operations and error handling", () -> {
        setupPreparedStatementTestTable(rq);
        var selectSql = "SELECT * FROM ps_test_data WHERE id = ?";
        var ps = new L4Ps(rq, selectSql);

        // Test unsupported parameter types
        try {
          ps.setRef(1, null);
          fail("Expected SQLException for setRef");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          ps.setArray(1, null);
          fail("Expected SQLException for setArray");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          ps.setRowId(1, null);
          fail("Expected SQLException for setRowId");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          ps.setSQLXML(1, null);
          fail("Expected SQLException for setSQLXML");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }

        // Test invalid execution methods
        try {
          ps.executeQuery("SELECT * FROM ps_test_data");
          fail("Expected SQLException for executeQuery(String)");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }
        try {
          ps.executeUpdate("UPDATE ps_test_data SET num_val = 1");
          fail("Expected SQLException for executeUpdate(String)");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }
        try {
          ps.execute("SELECT * FROM ps_test_data");
          fail("Expected SQLException for execute(String)");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }

        // Test closed statement
        ps.close();
        try {
          ps.setInt(1, 1);
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
        try {
          ps.executeQuery();
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }

        // Test unwrap
        ps = new L4Ps(rq, selectSql);
        assertTrue(ps.isWrapperFor(PreparedStatement.class));
        assertTrue(ps.isWrapperFor(Statement.class));
        assertTrue(ps.isWrapperFor(Wrapper.class));
        assertFalse(ps.isWrapperFor(String.class));
        assertSame(ps, ps.unwrap(PreparedStatement.class));
        try {
          ps.unwrap(String.class);
          fail("Expected SQLException for invalid unwrap");
        } catch (SQLException e) {
          assertNotNull(e.getMessage());
        }

        ps.close();
      });

      it("Tests L4Ps edge cases and invalid inputs", () -> {
        setupPreparedStatementTestTable(rq);

        // Test null/empty SQL
        try {
          new L4Ps(rq, null);
          fail("Expected SQLException for null SQL");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }
        try {
          new L4Ps(rq, "");
          fail("Expected SQLException for empty SQL");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }

        // Test invalid SQL execution
        var invalidPs = new L4Ps(rq, "SELECT * FROM nonexistent_table WHERE id = ?");
        invalidPs.setInt(1, 1);
        try {
          invalidPs.executeQuery();
          fail("Expected SQLException for invalid table");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }

        // Test clearParameters
        var ps = new L4Ps(rq, "INSERT INTO ps_test_data (num_val, text_val) VALUES (?, ?)");
        ps.setBigDecimal(1, new BigDecimal("123.45"));
        ps.setString(2, "Test");
        ps.clearParameters();
        ps.setNull(1, Types.NUMERIC);
        ps.setNull(2, Types.VARCHAR);
        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify
        var selectPs = new L4Ps(rq, "SELECT num_val, text_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertNull(rs.getBigDecimal("num_val"));
        assertNull(rs.getString("text_val"));
        assertFalse(rs.next());
        rs.close();
        ps.close();
        selectPs.close();
        invalidPs.close();
      });

      it("Tests L4Ps stream, LOB, and null parameter setting methods", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (" +
          "text_val, clob_val, nclob_val, nstring_val, blob_val" +
          ") VALUES (?, ?, ?, ?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Test data
        var unicodeText = "Unicode 世界"; // Text with non-ASCII characters
        var asciiText = "ASCII text";
        var charText = "Character stream text";
        var clobText = "CLOB long content";
        var nclobText = "NCLOB long content";
        var nstringText = "NSTRING content";
        var blobData = "BLOB binary data".getBytes(StandardCharsets.UTF_8);

        // Set parameters using the specified methods
        ps.setUnicodeStream(1, new ByteArrayInputStream(unicodeText.getBytes(StandardCharsets.UTF_16)), unicodeText.getBytes(StandardCharsets.UTF_16).length);
        ps.setClob(2, new StringReader(clobText), clobText.length());
        ps.setNClob(3, new StringReader(nclobText), nclobText.length());
        ps.setCharacterStream(4, new StringReader(nstringText), nstringText.length());
        ps.setBlob(5, new ByteArrayInputStream(blobData), blobData.length);

        // Execute update
        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify inserted data
        var selectPs = new L4Ps(rq, "SELECT text_val, clob_val, nclob_val, nstring_val, blob_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(unicodeText, rs.getString("text_val"));
        assertEquals(clobText, rs.getClob("clob_val").getSubString(1, clobText.length()));
        assertEquals(nclobText, rs.getNClob("nclob_val").getSubString(1, nclobText.length()));
        assertEquals(nstringText, rs.getNString("nstring_val"));
        assertArrayEquals(blobData, rs.getBytes("blob_val"));
        assertFalse(rs.next());
        rs.close();

        // Test additional stream methods and null setting
        ps.clearParameters();
        ps.setAsciiStream(1, new ByteArrayInputStream(asciiText.getBytes(StandardCharsets.US_ASCII)), (long) asciiText.length());
        ps.setCharacterStream(2, new StringReader(charText)); // No length
        ps.setNull(3, Types.NCLOB, "NCLOB"); // Null with typeName
        ps.setBinaryStream(4, new ByteArrayInputStream(nstringText.getBytes(StandardCharsets.UTF_8)), (long) nstringText.length());
        ps.setBlob(5, new javax.sql.rowset.serial.SerialBlob(blobData)); // Blob object

        rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify second insert
        selectPs.setInt(1, 2);
        rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(asciiText, rs.getString("text_val"));
        assertEquals(charText, rs.getClob("clob_val").getSubString(1, charText.length()));
        assertNull(rs.getNClob("nclob_val"));
        assertTrue(rs.wasNull());

        assertArrayEquals(blobData, rs.getBytes("blob_val"));
        assertFalse(rs.next());
        rs.close();

        // Test null stream inputs
        ps.clearParameters();
        ps.setUnicodeStream(1, null, 0);
        ps.setAsciiStream(2, null, 0L);
        ps.setBinaryStream(3, null); // No length
        ps.setCharacterStream(4, null, 0L);
        ps.setBlob(5, (InputStream) null, 0L);

        rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify null inputs
        selectPs.setInt(1, 3);
        rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertNull(rs.getString("text_val"));
        assertNull(rs.getClob("clob_val"));
        assertNull(rs.getNClob("nclob_val"));
        assertNull(rs.getNString("nstring_val"));
        assertNull(rs.getBytes("blob_val"));
        assertFalse(rs.next());
        rs.close();

        // Test error handling for invalid streams
        var invalidStream = new InputStream() {
          @Override public int read() throws IOException {
            throw new IOException("Read error");
          }
        };
        try {
          ps.setUnicodeStream(1, invalidStream, 10);
          fail("Expected SQLException for invalid Unicode stream");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }
        try {
          ps.setAsciiStream(1, invalidStream, 10L);
          fail("Expected SQLException for invalid ASCII stream");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }
        try {
          ps.setBinaryStream(1, invalidStream, 10L);
          fail("Expected SQLException for invalid binary stream");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }

        var invalidReader = new Reader() {
          @Override public int read(char[] cbuf, int off, int len) throws IOException {
            throw new IOException("Read error");
          }
          @Override public void close() {}
        };
        try {
          ps.setCharacterStream(1, invalidReader, 10L);
          fail("Expected SQLException for invalid character stream");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }
        try {
          ps.setClob(1, invalidReader, 10L);
          fail("Expected SQLException for invalid CLOB");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }
        try {
          ps.setNClob(1, invalidReader, 10L);
          fail("Expected SQLException for invalid NCLOB");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }

        ps.close();
        selectPs.close();
      });

      it("Tests L4Ps setObject with target SQL type and scale/length", () -> {
        setupPreparedStatementTestTable(rq);
        var insertSql = "INSERT INTO ps_test_data (" +
          "num_val, int_val, text_val, bool_val, blob_val, date_val, ts_val" +
          ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        var ps = new L4Ps(rq, insertSql);

        // Test data
        var numericVal = new BigDecimal("123.45678");
        var intVal = 42;
        var textVal = "Test string";
        var boolVal = true;
        var blobData = "BLOB data".getBytes(StandardCharsets.UTF_8);
        var dateVal = Date.valueOf("2023-10-15");
        var timestampVal = Timestamp.valueOf("2023-10-15 14:30:00.0");

        // Set parameters using setObject with target SQL type and scale/length
        ps.setObject(1, numericVal, Types.NUMERIC, 2); // Scale to 2 decimal places
        ps.setObject(2, intVal, Types.INTEGER, 0);
        ps.setObject(3, textVal, Types.VARCHAR, textVal.length());
        ps.setObject(4, boolVal, Types.BOOLEAN, 0);
        ps.setObject(5, blobData, Types.BLOB, 0);
        ps.setObject(6, dateVal, Types.DATE, 0);
        ps.setObject(7, timestampVal, Types.TIMESTAMP, 0);

        // Execute update
        int rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify inserted data
        var selectPs = new L4Ps(rq, "SELECT num_val, int_val, text_val, bool_val, blob_val, date_val, ts_val FROM ps_test_data WHERE id = ?");
        selectPs.setInt(1, 1);
        var rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(new BigDecimal("123.46"), rs.getBigDecimal("num_val", 2)); // Rounded to 2 decimal places
        assertEquals(42, rs.getInt("int_val"));
        assertEquals(textVal, rs.getString("text_val"));
        assertTrue(rs.getBoolean("bool_val"));
        assertArrayEquals(blobData, rs.getBytes("blob_val"));
        assertEquals(
          L4Utc.utcOf(Date.valueOf("2023-10-15")),
          L4Utc.utcOf(rs.getDate("date_val"))
        );
        assertEquals(
          L4Utc.utcFmtOf(Timestamp.valueOf("2023-10-15 14:30:00.0")),
          L4Utc.utcFmtOf(rs.getTimestamp("ts_val"))
        );
        assertFalse(rs.next());
        rs.close();

        // Test null input
        ps.clearParameters();
        ps.setObject(1, null, Types.NUMERIC, 2);
        ps.setObject(2, null, Types.INTEGER, 0);
        ps.setObject(3, null, Types.VARCHAR, 0);
        ps.setObject(4, null, Types.BOOLEAN, 0);
        ps.setObject(5, null, Types.BLOB, 0);
        ps.setObject(6, null, Types.DATE, 0);
        ps.setObject(7, null, Types.TIMESTAMP, 0);

        rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify null values
        selectPs.setInt(1, 2);
        rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertNull(rs.getBigDecimal("num_val"));
        assertTrue(rs.wasNull());
        assertEquals(0, rs.getInt("int_val"));
        assertTrue(rs.wasNull());
        assertNull(rs.getString("text_val"));
        assertFalse(rs.getBoolean("bool_val"));
        assertTrue(rs.wasNull());
        assertNull(rs.getBytes("blob_val"));
        assertNull(rs.getDate("date_val"));
        assertNull(rs.getTimestamp("ts_val"));
        assertFalse(rs.next());
        rs.close();

        // Test different input types for the same target type
        ps.clearParameters();
        ps.setObject(1, "456.789", Types.NUMERIC, 1); // String to NUMERIC
        ps.setObject(2, "100", Types.INTEGER, 0); // String to INTEGER
        ps.setObject(3, 123, Types.VARCHAR, 0); // Integer to VARCHAR
        ps.setObject(4, "true", Types.BOOLEAN, 0); // String to BOOLEAN
        ps.setObject(5, blobData, Types.BLOB, 0); // byte[] to BLOB
        ps.setObject(6, "2023-11-01", Types.DATE, 0); // String to DATE
        ps.setObject(7, "2023-11-01 15:45:00", Types.TIMESTAMP, 0); // String to TIMESTAMP

        rowsAffected = ps.executeUpdate();
        assertEquals(1, rowsAffected);

        // Verify converted values
        selectPs.setInt(1, 3);
        rs = selectPs.executeQuery();
        assertTrue(rs.next());
        assertEquals(new BigDecimal("456.8"), rs.getBigDecimal("num_val", 1)); // Rounded to 1 decimal place
        assertEquals(100, rs.getInt("int_val"));
        assertEquals("123", rs.getString("text_val"));
        assertTrue(rs.getBoolean("bool_val"));
        assertArrayEquals(blobData, rs.getBytes("blob_val"));
        assertEquals(L4Utc.utcOf(Date.valueOf("2023-11-01")), L4Utc.utcOf(rs.getDate("date_val")));
        assertEquals(
          Timestamp.valueOf("2023-11-01 15:45:00"),
          rs.getTimestamp("ts_val")
        );
        assertFalse(rs.next());
        rs.close();

        // Test error handling for unsupported SQL type
        try {
          ps.setObject(1, "test", Types.ARRAY, 0);
          fail("Expected SQLException for unsupported SQL type");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidColumn, e.getSQLState());
        }

        // Test invalid parameter conversion
        try {
          ps.setObject(1, "invalid", Types.NUMERIC, 2); // Invalid numeric string
          fail("Expected SQLException for invalid conversion");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidColumn, e.getSQLState());
        }

        ps.close();
        selectPs.close();
      });
    }
  }
}
