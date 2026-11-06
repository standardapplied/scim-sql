/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Named parameters")
class NamedParameterTest {

  @Test
  @DisplayName("parameters are captured and deduplicated in order")
  void shouldCaptureAndDeduplicate() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT * FROM events WHERE created_at >= :start_at AND created_at < :end_at"
                + " AND user_id = :user_id AND tenant_id = :user_id");

    assertEquals(List.of("start_at", "end_at", "user_id"), List.copyOf(analysis.parameters()));
  }

  @Test
  @DisplayName("parameter names are reported exactly as written")
  void shouldPreserveParameterCase() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT :userId, :USER_ID");

    assertEquals(Set.of("userId", "USER_ID"), analysis.parameters());
  }

  @Test
  @DisplayName("parameters work in every expression position")
  void shouldCaptureParametersEverywhere() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "INSERT INTO t (a, b) VALUES (:a, coalesce(:b, 0));"
                + "UPDATE t SET a = :c WHERE id = ANY(ARRAY[:d]);"
                + "SELECT * FROM t WHERE x IN (:e, :f) ORDER BY y LIMIT :g OFFSET :h");

    assertEquals(Set.of("a", "b", "c", "d", "e", "f", "g", "h"), analysis.parameters());
  }

  @Test
  @DisplayName("typecast, json operators and slices do not produce parameters")
  void shouldNotConfuseOperators() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT a::text, b ->> 'k', c #>> '{x,y}', arr[1:2] FROM t WHERE d = :real_param");

    assertEquals(Set.of("real_param"), analysis.parameters());
  }

  @Test
  @DisplayName("json key colon adjacency is an operator, not a parameter")
  void shouldNotConfuseJsonColon() {
    var spaced = PostgresQueryAnalyzer.analyze("SELECT JSON_OBJECT('a' : owner) FROM t");
    var compact = PostgresQueryAnalyzer.analyze("SELECT JSON_OBJECT('a':owner) FROM t");

    assertEquals(Set.of(), compact.parameters());
    assertEquals(spaced.normalizedSql(), compact.normalizedSql());
    assertEquals(
        Set.of(), PostgresQueryAnalyzer.analyze("SELECT JSON_OBJECT(k:v) FROM t").parameters());
  }

  @Test
  @DisplayName("json keys and values may still be parameters")
  void shouldCaptureJsonValueParameters() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT JSON_OBJECT('k':owner) FROM t WHERE id = :id AND x = JSON_OBJECT('v' : :v)");

    assertEquals(Set.of("id", "v"), analysis.parameters());
    assertEquals(
        Set.of("key", "val"),
        PostgresQueryAnalyzer.analyze("SELECT JSON_OBJECT(:key : :val)").parameters());
  }

  @Test
  @DisplayName("array slices with identifier bounds parse and produce no parameters")
  void shouldNotConfuseSliceBounds() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT arr[lo:hi], arr[1:n], arr[f(x):g(y)] FROM t" + " WHERE x = :p");

    assertEquals(Set.of("p"), analysis.parameters());
  }

  @Test
  @DisplayName("colon directly after an opening bracket stays a parameter")
  void shouldKeepParameterAfterOpeningBracket() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT arr[:idx] FROM t");

    assertEquals(Set.of("idx"), analysis.parameters());
  }

  @Test
  @DisplayName("colon-like content in strings, comments and quoted identifiers is inert")
  void shouldIgnoreColonContent() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT ':not_a_param', \":also_not\", e':nope', $$ :still_not $$"
                + " -- :comment_param\n"
                + " /* :block_param */ FROM t WHERE x = :yes");

    assertEquals(Set.of("yes"), analysis.parameters());
  }

  @Test
  @DisplayName("parameters are captured at any depth including CTE bodies")
  void shouldCaptureNestedParameters() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH recent AS (SELECT * FROM events WHERE at > :since)"
                + " SELECT * FROM recent WHERE kind = ANY(SELECT kind FROM kinds"
                + " WHERE weight > :min_weight)");

    assertEquals(Set.of("since", "min_weight"), analysis.parameters());
  }

  @Test
  @DisplayName("bare colon fails")
  void shouldRejectBareColon() {
    assertThrows(
        QueryAnalysisException.class,
        () -> PostgresQueryAnalyzer.analyze("SELECT * FROM t WHERE id = :"));
  }

  @Test
  @DisplayName("colon followed by space and name fails")
  void shouldRejectDetachedName() {
    assertThrows(
        QueryAnalysisException.class,
        () -> PostgresQueryAnalyzer.analyze("SELECT * FROM t WHERE id = : user_id"));
  }

  @Test
  @DisplayName("quoted parameter syntax fails")
  void shouldRejectQuotedParameter() {
    assertThrows(
        QueryAnalysisException.class,
        () -> PostgresQueryAnalyzer.analyze("SELECT * FROM t WHERE id = :\"user_id\""));
  }

  @Test
  @DisplayName("parameter cannot be used as a relation or alias")
  void shouldRejectParameterAsIdentifier() {
    assertThrows(
        QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT 1 FROM :tbl"));
    assertThrows(
        QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT 1 AS :alias"));
  }

  @Test
  @DisplayName("positional dollar parameters are not named parameters")
  void shouldIgnorePositionalParameters() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT * FROM t WHERE a = $1 AND b = :b");

    assertEquals(Set.of("b"), analysis.parameters());
  }

  @Test
  @DisplayName("values are never bound or substituted")
  void shouldOnlyReportNames() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT :p");

    assertTrue(analysis.normalizedSql().contains(":p"));
  }
}
