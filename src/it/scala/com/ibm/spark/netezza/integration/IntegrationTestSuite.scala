/**
  * (C) Copyright IBM Corp. 2010, 2015
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *
  */

package com.ibm.spark.netezza.integration

import java.sql.Timestamp

import org.apache.spark.sql.{Row, DataFrame}
import org.netezza.error.NzSQLException

class IntegrationTestSuite extends IntegrationSuiteBase with QueryTest {

  override def beforeAll(): Unit = {
    super.beforeAll()

    def dropTable(tableName: String): Unit = {
      try {
        conn.createStatement().executeUpdate(s"drop table $tableName")
      } catch {
        case e: NzSQLException => print(e.toString)
      }
    }

    def createTable(tableName: String): Unit = {
        conn.createStatement().executeUpdate(
          s"""
             |create table $tableName (
             |boolCol boolean,
             |intCol int,
             |byteIntCol int1,
             |smallIntCol int2,
             |bigInt int8,
             |floatCol float,
             |doubleCol double,
             |charCol char,
             |varcharCol2 varchar(6400),
             |tsCol timestamp
             |)
      """.stripMargin
        )

      // scalastyle:off
      conn.createStatement().executeUpdate(
        s"""
           |insert into $tableName values
           |(false, 2147483647, -128, 32767, 2147483648, 3.4, 5.6, 'a', 'rAnD0m 5Tring',
           |'1969-12-31 16:00:00.0');
           |insert into $tableName values
           |(null, null, null, null, null, null, null, null, null, null)
           """.stripMargin
      )
      // scalastyle:on
    }

    dropTable(testTable)
    createTable(testTable)
  }

  override def afterAll(): Unit = {
    try {
    } finally {
      super.afterAll()
    }
  }

  private def defaultOpts() = {
    Map("url" -> testURL,
      "user" -> user,
      "password" -> password,
      "dbtable" -> testTable,
      "numPartitions" -> Integer.toString(numPartitions))
  }

  test("Test load netezza to a DataFrame") {
    val testDf = sqlContext.read.format("com.ibm.spark.netezza").options(defaultOpts).load()
    verifyAnswer(testDf, TestUtils.expectedData)
  }

  test("Test mixed case table identifiers") {
    val tabName = "\"mixCaseTab\""
    withTable(tabName) {
      executeJdbcStmt(s"create table $tabName(id int , name varchar(10))")
      executeJdbcStmt(s"insert into $tabName values(1 , 'John Doe')")
      val opts = defaultOpts + ("dbTable" -> tabName)
      val testDf = sqlContext.read.format("com.ibm.spark.netezza").options(opts).load()
      val expected = Seq(Row(1, "John Doe"))
      verifyAnswer(testDf, expected)
    }
  }

  test("Test mixed case column names") {
    val opts = defaultOpts + ("dbTable" -> "\"mxCaseColsTab\"")
    val tabName = "mixCaseColTab"
    withTable(tabName) {
      executeJdbcStmt(s"""create table $tabName(id int , "Name" varchar(10))""")
      executeJdbcStmt(s"insert into $tabName values(1 , 'John Doe')")
      val opts = defaultOpts + ("dbTable" -> tabName)
      val testDf = sqlContext.read.format("com.ibm.spark.netezza").options(opts).load()
      assert(testDf.schema.fieldNames(1) == "Name")
      val expected = Seq(Row(1, "John Doe"))
      verifyAnswer(testDf, expected)
    }
  }


  /**
    * Executes the data frame and makes sure the answer matches the expected result.
    *
    * @param df             the [[DataFrame]] to be executed
    * @param expectedAnswer the expected result in a [[Seq]] of [[Row]]s.
    */
  private def verifyAnswer(df: => DataFrame, expectedAnswer: Seq[Row]): Unit = {
    checkAnswer(df, expectedAnswer) match {
      case Some(errorMessage) => fail(errorMessage)
      case None =>
    }
  }


  /**
    * Execute a JDBC statement.
    */
  private def executeJdbcStmt(stmt: String) {
    conn.createStatement().executeUpdate(stmt)
  }

  /**
    * Drops table `tableName` after calling `f`.
    */
  private def withTable(tableNames: String*)(f: => Unit): Unit = {
    try f finally {
      tableNames.foreach { name =>
        executeJdbcStmt(s"DROP TABLE $name")
      }
    }
  }
}
