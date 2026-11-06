/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Feature detection at any depth")
class FeatureDetectionTest {

  private static void assertFeature(String sql, QueryFeature feature) {
    var analysis = PostgresQueryAnalyzer.analyze(sql);
    assertTrue(analysis.features().contains(feature), feature + " expected for: " + sql);
  }

  private static void assertNoFeature(String sql, QueryFeature feature) {
    var analysis = PostgresQueryAnalyzer.analyze(sql);
    assertFalse(analysis.features().contains(feature), feature + " unexpected for: " + sql);
  }

  @Test
  @DisplayName("writable CTE is flagged for insert, update and delete bodies")
  void shouldFlagWritableCte() {
    assertFeature(
        "WITH gone AS (DELETE FROM sessions WHERE expired RETURNING id) SELECT * FROM gone",
        QueryFeature.WRITABLE_CTE);
    assertFeature(
        "WITH ins AS (INSERT INTO t VALUES (1) RETURNING id) SELECT * FROM ins",
        QueryFeature.WRITABLE_CTE);
    assertFeature(
        "WITH upd AS (UPDATE t SET a = 1 RETURNING id) SELECT * FROM upd",
        QueryFeature.WRITABLE_CTE);
    assertNoFeature("WITH ro AS (SELECT 1) SELECT * FROM ro", QueryFeature.WRITABLE_CTE);
  }

  @Test
  @DisplayName("writable CTE is flagged when nested deep inside a subquery")
  void shouldFlagNestedWritableCte() {
    assertFeature(
        "SELECT * FROM t WHERE x IN ("
            + "SELECT y FROM ("
            + "WITH w AS (DELETE FROM inner_t RETURNING y) SELECT y FROM w) sub)",
        QueryFeature.WRITABLE_CTE);
  }

  @Test
  @DisplayName("select into is flagged")
  void shouldFlagSelectInto() {
    assertFeature("SELECT * INTO backup_users FROM users", QueryFeature.SELECT_INTO);
  }

  @Test
  @DisplayName("row locking clauses are flagged")
  void shouldFlagRowLocks() {
    assertFeature("SELECT * FROM t FOR UPDATE", QueryFeature.ROW_LOCK);
    assertFeature("SELECT * FROM t FOR NO KEY UPDATE", QueryFeature.ROW_LOCK);
    assertFeature("SELECT * FROM t FOR SHARE NOWAIT", QueryFeature.ROW_LOCK);
    assertFeature("SELECT * FROM t FOR KEY SHARE SKIP LOCKED", QueryFeature.ROW_LOCK);
    assertNoFeature("SELECT * FROM t FOR READ ONLY", QueryFeature.ROW_LOCK);
  }

  @Test
  @DisplayName("row lock nested in a subquery is flagged")
  void shouldFlagNestedRowLock() {
    assertFeature(
        "SELECT * FROM outer_t WHERE id IN (SELECT id FROM inner_t FOR UPDATE)",
        QueryFeature.ROW_LOCK);
  }

  @Test
  @DisplayName("lateral relations are flagged")
  void shouldFlagLateral() {
    assertFeature(
        "SELECT * FROM users u, LATERAL (SELECT * FROM orders o WHERE o.user_id = u.id) x",
        QueryFeature.LATERAL);
    assertFeature(
        "SELECT * FROM users u JOIN LATERAL generate_series(1, u.n) g ON true",
        QueryFeature.LATERAL);
  }

  @Test
  @DisplayName("function relations are flagged and reported")
  void shouldFlagFunctionRelation() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT * FROM generate_series(1, 10) AS g(n)");

    assertTrue(analysis.features().contains(QueryFeature.FUNCTION_RELATION));
    assertEquals(
        List.of(
            new RelationReference(null, "generate_series", "g", RelationReference.Kind.FUNCTION)),
        analysis.relations());
  }

  @Test
  @DisplayName("schema-qualified function relation keeps schema")
  void shouldFlagQualifiedFunctionRelation() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT * FROM pg_catalog.pg_ls_dir('.') d");

    assertEquals(
        List.of(
            new RelationReference("pg_catalog", "pg_ls_dir", "d", RelationReference.Kind.FUNCTION)),
        analysis.relations());
  }

  @Test
  @DisplayName("rows from lists every function relation")
  void shouldFlagRowsFrom() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT * FROM ROWS FROM (generate_series(1, 2), generate_series(3, 4)) AS t(a, b)");

    assertTrue(analysis.features().contains(QueryFeature.FUNCTION_RELATION));
    assertEquals(2, analysis.relations().size());
    assertEquals(RelationReference.Kind.FUNCTION, analysis.relations().getFirst().kind());
  }

  @Test
  @DisplayName("values as a relation is flagged, insert values is not")
  void shouldFlagValuesRelation() {
    assertFeature(
        "SELECT * FROM (VALUES (1, 'a'), (2, 'b')) AS v(id, label)", QueryFeature.VALUES_RELATION);
    assertNoFeature("INSERT INTO t VALUES (1, 'a')", QueryFeature.VALUES_RELATION);
  }

  @Test
  @DisplayName("star projections are flagged anywhere")
  void shouldFlagStarProjection() {
    assertFeature("SELECT * FROM t", QueryFeature.STAR_PROJECTION);
    assertFeature("SELECT t.* FROM t", QueryFeature.STAR_PROJECTION);
    assertFeature(
        "WITH d AS (DELETE FROM t RETURNING *) SELECT 1 FROM d", QueryFeature.STAR_PROJECTION);
    assertFeature("SELECT 1 FROM t WHERE x IN (SELECT * FROM s)", QueryFeature.STAR_PROJECTION);
    assertNoFeature("SELECT count(*) FROM t", QueryFeature.STAR_PROJECTION);
  }

  @Test
  @DisplayName("subqueries are flagged, plain selects are not")
  void shouldFlagSubquery() {
    assertFeature("SELECT (SELECT max(id) FROM t) AS m", QueryFeature.SUBQUERY);
    assertFeature("SELECT * FROM (SELECT 1) sub", QueryFeature.SUBQUERY);
    assertFeature("SELECT * FROM t WHERE id IN (SELECT id FROM s)", QueryFeature.SUBQUERY);
    assertNoFeature("SELECT 1", QueryFeature.SUBQUERY);
    assertNoFeature("(SELECT 1)", QueryFeature.SUBQUERY);
    assertNoFeature("WITH a AS (SELECT 1) SELECT * FROM a", QueryFeature.SUBQUERY);
  }

  @Test
  @DisplayName("set operations nested in subqueries are flagged")
  void shouldFlagNestedSetOperation() {
    assertFeature(
        "SELECT * FROM t WHERE id IN (SELECT id FROM a UNION SELECT id FROM b)",
        QueryFeature.SET_OPERATION);
  }

  @Test
  @DisplayName("multiple statements are counted and flagged")
  void shouldFlagMultipleStatements() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT 1; SELECT * FROM t; DELETE FROM t");

    assertEquals(3, analysis.statementCount());
    assertEquals(StatementKind.SELECT, analysis.statementKind());
    assertTrue(analysis.features().contains(QueryFeature.MULTIPLE_STATEMENTS));
    assertEquals(2, analysis.relations().size());
  }

  @Test
  @DisplayName("cte flags are detected inside insert, update and delete statements")
  void shouldFlagCteOnDml() {
    assertFeature(
        "WITH src AS (SELECT * FROM staging) INSERT INTO t SELECT * FROM src", QueryFeature.CTE);
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH src AS (SELECT id FROM staging) UPDATE t SET a = 1"
                + " WHERE id IN (SELECT id FROM src)");
    assertTrue(analysis.features().contains(QueryFeature.CTE));
    var kinds = analysis.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("staging:PHYSICAL", "t:PHYSICAL", "src:CTE"), kinds);
  }
}
