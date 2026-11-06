/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Hostile and malformed input")
class HostileInputTest {

  @Test
  @DisplayName("null and blank input are rejected")
  void shouldRejectNullAndBlank() {
    assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(null));
    assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(""));
    assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("   \n\t"));
  }

  @Test
  @DisplayName("oversized input is rejected before parsing")
  void shouldRejectOversizedInput() {
    var oversized = "SELECT " + "1,".repeat(PostgresQueryAnalyzer.MAX_LENGTH / 2) + "1";
    var exception =
        assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(oversized));
    assertTrue(exception.reason().contains("characters"));
  }

  @Test
  @DisplayName("token floods are rejected")
  void shouldRejectTokenFlood() {
    var flood = "SELECT " + "1,".repeat(60_000) + "1";
    assertTrue(flood.length() <= PostgresQueryAnalyzer.MAX_LENGTH);
    var exception =
        assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(flood));
    assertTrue(exception.reason().contains("tokens"));
  }

  @Test
  @DisplayName("deep nesting is rejected deterministically and quickly")
  void shouldRejectDeepNesting() {
    var depth = PostgresQueryAnalyzer.MAX_NESTING_DEPTH + 1;
    var nested = "SELECT " + "(".repeat(depth) + "1" + ")".repeat(depth);
    assertTimeoutPreemptively(
        Duration.ofSeconds(10),
        () -> {
          var exception =
              assertThrows(
                  QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(nested));
          assertTrue(exception.reason().contains("nesting"));
        });
  }

  @Test
  @DisplayName("nesting at the limit parses")
  void shouldParseModerateNesting() {
    var nested = "SELECT " + "(".repeat(40) + "1" + ")".repeat(40);
    assertEquals(1, PostgresQueryAnalyzer.analyze(nested).statementCount());
  }

  @Test
  @DisplayName("semicolons inside every string form do not create statements")
  void shouldIgnoreEmbeddedSemicolons() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT 'a;b', e'c;d', $$e;f$$, $tag$g;h$tag$, \"col;name\" FROM t"
                + " -- trailing ; comment\n /* block ; comment */");

    assertEquals(1, analysis.statementCount());
  }

  @Test
  @DisplayName("trailing semicolons do not add statements")
  void shouldIgnoreTrailingSemicolons() {
    assertEquals(1, PostgresQueryAnalyzer.analyze("SELECT 1;").statementCount());
    assertEquals(1, PostgresQueryAnalyzer.analyze("SELECT 1;;;").statementCount());
  }

  @Test
  @DisplayName("only semicolons or comments is rejected as no statement")
  void shouldRejectEmptyStatementList() {
    for (var sql : new String[] {";;", "-- just a comment", "/* nothing */"}) {
      var exception =
          assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(sql));
      assertTrue(
          exception.reason().contains("no sql statement") || exception.reason().contains("blank"),
          sql + " -> " + exception.reason());
    }
  }

  @Test
  @DisplayName("trailing garbage is rejected with a position")
  void shouldRejectTrailingGarbage() {
    var exception =
        assertThrows(
            QueryAnalysisException.class,
            () -> PostgresQueryAnalyzer.analyze("SELECT 1 FROM t klaatu barada nikto"));
    assertEquals("invalid sql syntax", exception.reason());
    assertEquals(1, exception.line());
  }

  @Test
  @DisplayName("syntax errors never leak sql content")
  void shouldNotLeakSqlContent() {
    var secret = "SuperSecretLiteral12345";
    var exception =
        assertThrows(
            QueryAnalysisException.class,
            () -> PostgresQueryAnalyzer.analyze("SELECT WHERE '" + secret + "' &&& %%%"));
    assertFalse(exception.getMessage().contains(secret));
    assertFalse(String.valueOf(exception.reason()).contains(secret));
  }

  @Test
  @DisplayName("unterminated strings and dollar quotes fail cleanly")
  void shouldRejectUnterminatedLiterals() {
    assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT 'abc"));
    assertThrows(
        QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT $tag$abc"));
  }

  @Test
  @DisplayName("unicode and exotic identifiers parse")
  void shouldParseUnicodeIdentifiers() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT \"名前\", \"a\"\"b\" FROM \"таблица\"");

    assertEquals("таблица", analysis.relations().getFirst().name());
    assertEquals("名前", analysis.columns().getFirst().name());
    assertEquals("a\"b", analysis.columns().get(1).name());
  }

  @Test
  @DisplayName("nested block comments are handled")
  void shouldHandleNestedBlockComments() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT 1 /* outer /* inner ; */ still outer */");

    assertEquals(1, analysis.statementCount());
  }

  @Test
  @DisplayName("deeply nested block comments lex in linear time and constant stack")
  void shouldHandleDeepBlockCommentNesting() {
    var sql = "SELECT 1 " + "/*".repeat(5_000) + "x" + "*/".repeat(5_000);
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> assertEquals(1, PostgresQueryAnalyzer.analyze(sql).statementCount()));
  }

  @Test
  @DisplayName("unterminated nested block comment is rejected")
  void shouldRejectUnterminatedBlockComment() {
    assertThrows(
        QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT 1 /* /* x */"));
    assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT 1 /*"));
  }

  @Test
  @DisplayName("psql meta-commands are rejected, never treated as statement separators")
  void shouldRejectPsqlMetaCommands() {
    for (var sql :
        new String[] {
          "SELECT 1; \\! id\n",
          "SELECT 1;\n\\echo pwned\n",
          "\\copy secrets TO '/tmp/out'",
          "SELECT 1\\; SELECT 2;"
        }) {
      assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(sql), sql);
    }
  }

  @Test
  @DisplayName("unicode escaped identifiers are rejected, unicode escaped strings parse")
  void shouldRejectUnicodeEscapedIdentifiers() {
    var exception =
        assertThrows(
            QueryAnalysisException.class,
            () -> PostgresQueryAnalyzer.analyze("SELECT * FROM U&\"d\\0061t\""));
    assertTrue(exception.reason().contains("unicode"));
    assertThrows(
        QueryAnalysisException.class,
        () -> PostgresQueryAnalyzer.analyze("SELECT * FROM U&\"d!0061t\" UESCAPE '!'"));
    assertEquals(1, PostgresQueryAnalyzer.analyze("SELECT U&'\\0061'").statementCount());
  }

  @Test
  @DisplayName("identifiers longer than 63 bytes are rejected before analysis")
  void shouldRejectOverlengthIdentifiers() {
    var atLimit = "a".repeat(PostgresQueryAnalyzer.MAX_IDENTIFIER_BYTES);
    for (var sql :
        new String[] {
          "SELECT 1 FROM " + atLimit + "x",
          "SELECT 1 FROM \"" + atLimit + "x\"",
          "SELECT \"" + "я".repeat(32) + "\" FROM t"
        }) {
      var exception =
          assertThrows(QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze(sql), sql);
      assertTrue(exception.reason().contains("63 bytes"), exception.reason());
    }
  }

  @Test
  @DisplayName("identifiers at exactly 63 bytes parse, measured after quote unescaping")
  void shouldAcceptIdentifiersAtByteLimit() {
    var atLimit = "a".repeat(PostgresQueryAnalyzer.MAX_IDENTIFIER_BYTES);
    assertEquals(
        atLimit,
        PostgresQueryAnalyzer.analyze("SELECT 1 FROM " + atLimit).relations().getFirst().name());

    var escaped = "a".repeat(62) + "\"\"";
    assertEquals(
        "a".repeat(62) + "\"",
        PostgresQueryAnalyzer.analyze("SELECT 1 FROM \"" + escaped + "\"")
            .relations()
            .getFirst()
            .name());
  }

  @Test
  @DisplayName("dollar tag confusion does not break statement counting")
  void shouldHandleDollarTagTricks() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT $a$ $b$ ; $a$, $b$x$b$ FROM t");

    assertEquals(1, analysis.statementCount());
  }

  @Test
  @DisplayName("hostile inputs complete within the time budget")
  void shouldStayWithinTimeBudget() {
    var wide = "SELECT " + "abs(x) + ".repeat(2_000) + "1 FROM t";
    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () -> assertEquals(1, PostgresQueryAnalyzer.analyze(wide).statementCount()));
  }

  @Test
  @DisplayName("exception positions are 1-based line and 0-based column")
  void shouldReportErrorPosition() {
    var exception =
        assertThrows(
            QueryAnalysisException.class,
            () -> PostgresQueryAnalyzer.analyze("SELECT 1\nFROM t WHERE"));
    assertTrue(exception.line() >= 1);
    assertTrue(exception.column() >= 0);
    assertTrue(exception.getMessage().contains("line"));
  }
}
