/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Normalization")
class NormalizationTest {

  private static String normalize(String sql) {
    return PostgresQueryAnalyzer.analyze(sql).normalizedSql();
  }

  @Test
  @DisplayName("whitespace and comments do not change the normalized form")
  void shouldBeStableAcrossFormatting() {
    var canonical = normalize("SELECT id, name FROM users WHERE active = true");

    assertEquals(canonical, normalize("SELECT   id,\n\tname\nFROM users\nWHERE active = true"));
    assertEquals(
        canonical,
        normalize("SELECT id, -- projection\n name /* the name */ FROM users WHERE active = true"));
  }

  @Test
  @DisplayName("keyword and unquoted identifier case do not change the normalized form")
  void shouldBeStableAcrossCase() {
    assertEquals(normalize("select id from Users"), normalize("SELECT ID FROM USERS"));
  }

  @Test
  @DisplayName("quoted identifiers, strings and parameter names are preserved verbatim")
  void shouldPreserveSemanticText() {
    var normalized = normalize("SELECT \"MixedCase\", 'Literal TEXT', :ParamName FROM \"T\"");

    assertEquals("select \"MixedCase\" , 'Literal TEXT' , :ParamName from \"T\"", normalized);
  }

  @Test
  @DisplayName("semantic changes produce different normalized forms")
  void shouldDifferOnSemanticChanges() {
    var canonical = normalize("SELECT a FROM t WHERE x > 1");

    assertNotEquals(canonical, normalize("SELECT b FROM t WHERE x > 1"));
    assertNotEquals(canonical, normalize("SELECT a FROM t2 WHERE x > 1"));
    assertNotEquals(canonical, normalize("SELECT a FROM t WHERE x >= 1"));
    assertNotEquals(canonical, normalize("SELECT a FROM t WHERE x > 2"));
  }

  @Test
  @DisplayName("non-ascii identifier case changes the normalized form")
  void shouldPreserveNonAsciiIdentifierCase() {
    assertEquals("select marker from Таблица", normalize("SELECT marker FROM Таблица"));
    assertNotEquals(
        normalize("SELECT marker FROM Таблица"), normalize("SELECT marker FROM таблица"));
  }

  @Test
  @DisplayName("quoted identifier case changes the normalized form")
  void shouldDifferOnQuotedIdentifierCase() {
    assertNotEquals(
        normalize("SELECT \"Users\".id FROM \"Users\""),
        normalize("SELECT \"users\".id FROM \"users\""));
  }

  @Test
  @DisplayName("parameter name changes the normalized form")
  void shouldDifferOnParameterName() {
    assertNotEquals(
        normalize("SELECT * FROM t WHERE id = :a"), normalize("SELECT * FROM t WHERE id = :b"));
  }

  @Test
  @DisplayName("string continuation normalizes to a single line")
  void shouldStaySingleLine() {
    assertEquals("select 'a' 'b'", normalize("SELECT 'a'\n'b'"));
    assertEquals(-1, normalize("SELECT 'a'\n'b'\n'c' FROM t").indexOf('\n'));
  }

  @Test
  @DisplayName("normalization keeps dollar-quoted content verbatim")
  void shouldPreserveDollarQuotedContent() {
    var normalized = normalize("SELECT $tag$Keep CASE and -- fake comment$tag$");

    assertEquals("select $tag$Keep CASE and -- fake comment$tag$", normalized);
  }
}
