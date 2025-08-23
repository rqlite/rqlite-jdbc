package io.rqlite.jdbc;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

public class L4Utc {

  public static final ZoneId UtcZid = ZoneId.of("UTC");
  public static final TimeZone UtcTz = TimeZone.getTimeZone("UTC");

  public static LocalDate utcOf(Date x) {
    var calInstance = Calendar.getInstance(UtcTz);
    calInstance.setTimeInMillis(x.getTime());
    return LocalDate.of(
      calInstance.get(Calendar.YEAR),
      calInstance.get(Calendar.MONTH) + 1, // Calendar months are 0-based
      calInstance.get(Calendar.DAY_OF_MONTH)
    );
  }

  public static LocalTime utcOf(Time x) {
    var calInstance = Calendar.getInstance(UtcTz);
    calInstance.setTimeInMillis(x.getTime());
    return LocalTime.of(
      calInstance.get(Calendar.HOUR_OF_DAY),
      calInstance.get(Calendar.MINUTE),
      calInstance.get(Calendar.SECOND)
    );
  }

  public static LocalDateTime utcDateTimeOf(Timestamp x) {
    var instant = x.toInstant();
    var zdt = instant.atZone(UtcZid);
    return zdt.toLocalDateTime();
  }

  public static String utcFmtOf(Timestamp x) {
    var formatter = new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd HH:mm:ss")
      .optionalStart()
      .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true) // Adds .nnnnnnnnn if nanos > 0
      .optionalEnd()
      .toFormatter();
    return utcDateTimeOf(x).format(formatter);
  }

}
