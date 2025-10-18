package io.rqlite

import io.rqlite.jdbc.L4DbMeta
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.SQLiteDialect

object Users : Table() {
  val id = integer("id")
  val name = varchar("name", 50)
}

fun main() {
  L4Tests.initLogging()
  L4DbMeta.setDriverName("SQLite JDBC")
  Database.registerJdbcDriver("jdbc:rqlite", "io.rqlite.jdbc.L4Driver", SQLiteDialect.dialectName)
  Database.registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
  Database.connect(url = "jdbc:rqlite:http://localhost:4001", driver = "io.rqlite.jdbc.L4Driver")
  transaction {
    addLogger(StdOutSqlLogger)
    SchemaUtils.create(Users)
    Users.insert { it[id] = 0; it[name] = "Alice" }
    Users.insert { it[id] = 1; it[name] = "Bob" }
  }
  transaction {
    addLogger(StdOutSqlLogger)
    Users.selectAll().forEach {
      println(it)
    }
  }
}