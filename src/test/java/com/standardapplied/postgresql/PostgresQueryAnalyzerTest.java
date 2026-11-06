/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PostgresQueryAnalyzer core analysis")
class PostgresQueryAnalyzerTest {

  @Test
  @DisplayName("minimal select produces exact analysis")
  void shouldAnalyzeMinimalSelect() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT 1");

    assertEquals(StatementKind.SELECT, analysis.statementKind());
    assertEquals(1, analysis.statementCount());
    assertEquals(List.of(), analysis.relations());
    assertEquals(List.of(), analysis.columns());
    assertEquals(List.of(), analysis.functions());
    assertEquals(Set.of(), analysis.parameters());
    assertEquals(Set.of(), analysis.features());
    assertEquals("select 1", analysis.normalizedSql());
  }

  @Test
  @DisplayName("simple select reports relation and columns")
  void shouldAnalyzeSimpleSelect() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT id, name FROM users WHERE active = true");

    assertEquals(StatementKind.SELECT, analysis.statementKind());
    assertEquals(
        List.of(new RelationReference(null, "users", null, RelationReference.Kind.PHYSICAL)),
        analysis.relations());
    assertEquals(
        List.of(
            new ColumnReference(null, "id"),
            new ColumnReference(null, "name"),
            new ColumnReference(null, "active")),
        analysis.columns());
  }

  @Test
  @DisplayName("join preserves aliases and qualifiers")
  void shouldAnalyzeJoin() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT u.id, o.total FROM users u JOIN orders o ON o.user_id = u.id");

    assertEquals(
        List.of(
            new RelationReference(null, "users", "u", RelationReference.Kind.PHYSICAL),
            new RelationReference(null, "orders", "o", RelationReference.Kind.PHYSICAL)),
        analysis.relations());
    assertEquals(
        List.of(
            new ColumnReference("u", "id"),
            new ColumnReference("o", "total"),
            new ColumnReference("o", "user_id"),
            new ColumnReference("u", "id")),
        analysis.columns());
  }

  @Test
  @DisplayName("aggregate query captures functions in select, group by and having")
  void shouldAnalyzeAggregate() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT count(id), lower(name) FROM users GROUP BY lower(name)"
                + " HAVING max(age) > 21");

    var names = analysis.functions().stream().map(FunctionReference::name).toList();
    assertEquals(List.of("count", "lower", "lower", "max"), names);
    assertEquals(Set.of(), analysis.features());
  }

  @Test
  @DisplayName("window function flags WINDOW and captures function")
  void shouldAnalyzeWindow() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT rank() OVER (PARTITION BY dept ORDER BY salary) FROM emp");

    assertTrue(analysis.features().contains(QueryFeature.WINDOW));
    assertEquals("rank", analysis.functions().getFirst().name());
  }

  @Test
  @DisplayName("named window clause flags WINDOW")
  void shouldAnalyzeNamedWindow() {
    var analysis =
        PostgresQueryAnalyzer.analyze("SELECT sum(x) OVER w FROM t WINDOW w AS (PARTITION BY y)");

    assertTrue(analysis.features().contains(QueryFeature.WINDOW));
  }

  @Test
  @DisplayName("set operations flag SET_OPERATION")
  void shouldAnalyzeSetOperations() {
    for (var op : List.of("UNION", "UNION ALL", "INTERSECT", "EXCEPT")) {
      var analysis = PostgresQueryAnalyzer.analyze("SELECT a FROM t1 " + op + " SELECT a FROM t2");
      assertTrue(
          analysis.features().contains(QueryFeature.SET_OPERATION), op + " should be flagged");
      assertEquals(2, analysis.relations().size(), op);
    }
  }

  @Test
  @DisplayName("quoted and schema-qualified identifiers are preserved")
  void shouldPreserveQuotedIdentifiers() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT \"Weird Name\", MixedCase FROM \"MySchema\".\"MyTable\" mt,"
                + " analytics.events");

    assertEquals(
        List.of(
            new RelationReference("MySchema", "MyTable", "mt", RelationReference.Kind.PHYSICAL),
            new RelationReference("analytics", "events", null, RelationReference.Kind.PHYSICAL)),
        analysis.relations());
    assertEquals("MySchema", analysis.relations().getFirst().schema());
    assertEquals(
        List.of(new ColumnReference(null, "Weird Name"), new ColumnReference(null, "mixedcase")),
        analysis.columns());
    assertEquals("analytics", analysis.relations().get(1).schema());
  }

  @Test
  @DisplayName("nested correlated subquery keeps physical relations at all depths")
  void shouldAnalyzeCorrelatedSubquery() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT u.id FROM users u WHERE EXISTS ("
                + "SELECT 1 FROM orders o WHERE o.user_id = u.id AND o.total > ("
                + "SELECT avg(total) FROM orders))");

    assertTrue(analysis.features().contains(QueryFeature.SUBQUERY));
    var names = analysis.relations().stream().map(RelationReference::name).toList();
    assertEquals(List.of("users", "orders", "orders"), names);
  }

  @Test
  @DisplayName("CTE references are distinguished from physical relations")
  void shouldDistinguishCteFromPhysical() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH active AS (SELECT * FROM users WHERE active) SELECT * FROM active, orders");

    assertEquals(
        List.of(
            new RelationReference(null, "users", null, RelationReference.Kind.PHYSICAL),
            new RelationReference(null, "active", null, RelationReference.Kind.CTE),
            new RelationReference(null, "orders", null, RelationReference.Kind.PHYSICAL)),
        analysis.relations());
    assertTrue(analysis.features().contains(QueryFeature.CTE));
  }

  @Test
  @DisplayName("non-recursive CTE body referencing its own name is physical")
  void shouldTreatSelfReferenceInNonRecursiveCteAsPhysical() {
    var analysis =
        PostgresQueryAnalyzer.analyze("WITH users AS (SELECT * FROM users) SELECT * FROM users");

    assertEquals(
        List.of(
            new RelationReference(null, "users", null, RelationReference.Kind.PHYSICAL),
            new RelationReference(null, "users", null, RelationReference.Kind.CTE)),
        analysis.relations());
  }

  @Test
  @DisplayName("recursive CTE self-reference is a CTE reference")
  void shouldTreatSelfReferenceInRecursiveCteAsCte() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH RECURSIVE tree AS ("
                + "SELECT id, parent_id FROM nodes WHERE parent_id IS NULL"
                + " UNION ALL SELECT n.id, n.parent_id FROM nodes n JOIN tree t"
                + " ON n.parent_id = t.id) SELECT * FROM tree");

    assertTrue(analysis.features().contains(QueryFeature.RECURSIVE_CTE));
    var kinds = analysis.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("nodes:PHYSICAL", "nodes:PHYSICAL", "tree:CTE", "tree:CTE"), kinds);
  }

  @Test
  @DisplayName("later CTE sees earlier sibling CTE")
  void shouldScopeSiblingCtes() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH a AS (SELECT 1 AS x), b AS (SELECT x FROM a) SELECT * FROM b");

    assertEquals(
        List.of(
            new RelationReference(null, "a", null, RelationReference.Kind.CTE),
            new RelationReference(null, "b", null, RelationReference.Kind.CTE)),
        analysis.relations());
  }

  @Test
  @DisplayName("inner CTE shadows outer physical relation without losing outer physical refs")
  void shouldHandleShadowedCteNames() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT * FROM users WHERE id IN ("
                + "WITH users AS (SELECT owner_id FROM projects)"
                + " SELECT owner_id FROM users)");

    var kinds = analysis.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("users:PHYSICAL", "projects:PHYSICAL", "users:CTE"), kinds);
  }

  @Test
  @DisplayName("schema-qualified reference never matches a CTE name")
  void shouldNotMatchSchemaQualifiedAsCte() {
    var analysis =
        PostgresQueryAnalyzer.analyze("WITH users AS (SELECT 1) SELECT * FROM public.users, users");

    assertEquals(
        List.of(
            new RelationReference("public", "users", null, RelationReference.Kind.PHYSICAL),
            new RelationReference(null, "users", null, RelationReference.Kind.CTE)),
        analysis.relations());
  }

  @Test
  @DisplayName("CTE name never hides an insert target")
  void shouldKeepInsertTargetPhysicalDespiteCteName() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH target AS (SELECT 1 AS id) INSERT INTO target SELECT id FROM target");

    var kinds = analysis.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("target:PHYSICAL", "target:CTE"), kinds);
  }

  @Test
  @DisplayName("CTE name never hides update and delete targets")
  void shouldKeepUpdateAndDeleteTargetsPhysicalDespiteCteName() {
    var update =
        PostgresQueryAnalyzer.analyze(
            "WITH target AS (SELECT 1 AS id) UPDATE target SET id = 2 FROM target");
    var updateKinds = update.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("target:PHYSICAL", "target:CTE"), updateKinds);

    var delete =
        PostgresQueryAnalyzer.analyze(
            "WITH target AS (SELECT 1 AS id) DELETE FROM target USING target t WHERE t.id = 1");
    var deleteKinds = delete.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("target:PHYSICAL", "target:CTE"), deleteKinds);
  }

  @Test
  @DisplayName("merge accepts a leading with clause and resolves the using source as a CTE")
  void shouldAnalyzeMergeWithCte() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH src AS (SELECT id FROM staging) MERGE INTO target t USING src s"
                + " ON t.id = s.id WHEN NOT MATCHED THEN INSERT VALUES (s.id)");

    assertEquals(StatementKind.MERGE, analysis.statementKind());
    assertTrue(analysis.features().contains(QueryFeature.CTE));
    var kinds = analysis.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("staging:PHYSICAL", "target:PHYSICAL", "src:CTE"), kinds);
  }

  @Test
  @DisplayName("CTE name never hides a merge target")
  void shouldKeepMergeTargetPhysicalDespiteCteName() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH target AS (SELECT 1 AS id) MERGE INTO target USING target t"
                + " ON target.id = t.id WHEN MATCHED THEN DO NOTHING");

    var kinds = analysis.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("target:PHYSICAL", "target:CTE"), kinds);
  }

  @Test
  @DisplayName("CTE name never hides a select into target")
  void shouldKeepSelectIntoTargetPhysicalDespiteCteName() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH target AS (SELECT 1 AS id) SELECT id INTO target FROM target");

    var kinds = analysis.relations().stream().map(r -> r.name() + ":" + r.kind()).toList();
    assertEquals(List.of("target:PHYSICAL", "target:CTE"), kinds);
  }

  @Test
  @DisplayName("recursive CTE sees later sibling CTE")
  void shouldScopeForwardReferenceInRecursiveWith() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH RECURSIVE x AS (SELECT * FROM y), y AS (SELECT 1) SELECT * FROM x");

    assertEquals(
        List.of(
            new RelationReference(null, "y", null, RelationReference.Kind.CTE),
            new RelationReference(null, "x", null, RelationReference.Kind.CTE)),
        analysis.relations());
  }

  @Test
  @DisplayName("identifier folding is ascii-only so distinct unicode names stay distinct")
  void shouldFoldOnlyAsciiIdentifierCase() {
    var upper = PostgresQueryAnalyzer.analyze("SELECT marker FROM Таблица");
    var lower = PostgresQueryAnalyzer.analyze("SELECT marker FROM таблица");

    assertEquals("Таблица", upper.relations().getFirst().name());
    assertEquals("таблица", lower.relations().getFirst().name());
  }

  @Test
  @DisplayName("functions are captured in select, where, join, group, window and from")
  void shouldCaptureFunctionsEverywhere() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT lower(a), rank() OVER (ORDER BY nullif(b, 0))"
                + " FROM t JOIN generate_series(1, 10) g ON abs(t.x) = g"
                + " WHERE coalesce(t.y, now()) IS NOT NULL"
                + " GROUP BY lower(a), date_trunc('day', t.created_at)");

    var names = analysis.functions().stream().map(FunctionReference::name).toList();
    assertTrue(
        names.containsAll(
            List.of(
                "lower",
                "rank",
                "nullif",
                "generate_series",
                "abs",
                "coalesce",
                "now",
                "date_trunc")),
        names.toString());
  }

  @Test
  @DisplayName("schema-qualified function keeps schema and location")
  void shouldCaptureSchemaQualifiedFunction() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT pg_catalog.now()");

    var function = analysis.functions().getFirst();
    assertEquals("pg_catalog", function.schema());
    assertEquals("now", function.name());
    assertEquals(1, function.line());
    assertEquals(7, function.column());
  }

  @Test
  @DisplayName("special-form functions are reported by keyword")
  void shouldCaptureSpecialFormFunctions() {
    var analysis =
        PostgresQueryAnalyzer.analyze("SELECT CAST(a AS int), EXTRACT(YEAR FROM b) FROM t");

    var names = analysis.functions().stream().map(FunctionReference::name).toList();
    assertEquals(List.of("cast", "extract"), names);
  }

  @Test
  @DisplayName("insert reports target relation, columns and parameters")
  void shouldAnalyzeInsert() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "INSERT INTO audit.events (kind, payload) VALUES (:kind, :payload)");

    assertEquals(StatementKind.INSERT, analysis.statementKind());
    assertEquals(
        List.of(new RelationReference("audit", "events", null, RelationReference.Kind.PHYSICAL)),
        analysis.relations());
    assertEquals(
        List.of(new ColumnReference(null, "kind"), new ColumnReference(null, "payload")),
        analysis.columns());
    assertEquals(Set.of("kind", "payload"), analysis.parameters());
  }

  @Test
  @DisplayName("on conflict arbiter columns are reported")
  void shouldReportOnConflictArbiterColumns() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "INSERT INTO t (a) VALUES (1) ON CONFLICT (tenant_id) DO NOTHING");

    assertEquals(
        List.of(new ColumnReference(null, "a"), new ColumnReference(null, "tenant_id")),
        analysis.columns());
  }

  @Test
  @DisplayName("update reports set targets and alias")
  void shouldAnalyzeUpdate() {
    var analysis =
        PostgresQueryAnalyzer.analyze("UPDATE users u SET name = :name WHERE u.id = :id");

    assertEquals(StatementKind.UPDATE, analysis.statementKind());
    assertEquals(
        List.of(new RelationReference(null, "users", "u", RelationReference.Kind.PHYSICAL)),
        analysis.relations());
    assertEquals(
        List.of(new ColumnReference(null, "name"), new ColumnReference("u", "id")),
        analysis.columns());
  }

  @Test
  @DisplayName("delete using reports both relations")
  void shouldAnalyzeDeleteUsing() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "DELETE FROM sessions s USING users u WHERE s.user_id = u.id AND u.banned");

    assertEquals(StatementKind.DELETE, analysis.statementKind());
    assertEquals(
        List.of(
            new RelationReference(null, "sessions", "s", RelationReference.Kind.PHYSICAL),
            new RelationReference(null, "users", "u", RelationReference.Kind.PHYSICAL)),
        analysis.relations());
  }

  @Test
  @DisplayName("alias without AS keyword is preserved")
  void shouldCaptureBareAlias() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT * FROM users AS u");

    assertEquals("u", analysis.relations().getFirst().alias());
  }

  @Test
  @DisplayName("star projection reports star column reference")
  void shouldReportStarColumn() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT *, t.* FROM t");

    assertTrue(analysis.features().contains(QueryFeature.STAR_PROJECTION));
    assertEquals(
        List.of(new ColumnReference(null, "*"), new ColumnReference("t", "*")), analysis.columns());
    assertTrue(analysis.columns().getFirst().star());
    assertNull(analysis.columns().getFirst().qualifier());
  }

  @Test
  @DisplayName("array subscript keeps column name without subscript")
  void shouldHandleArraySubscript() {
    var analysis = PostgresQueryAnalyzer.analyze("SELECT tags[1] FROM posts");

    assertEquals(List.of(new ColumnReference(null, "tags")), analysis.columns());
  }

  @Test
  @DisplayName("join using reports the join columns")
  void shouldReportJoinUsingColumns() {
    var analysis =
        PostgresQueryAnalyzer.analyze("SELECT a.id FROM a JOIN b USING (tenant_id, secret)");

    assertEquals(
        List.of(
            new ColumnReference("a", "id"),
            new ColumnReference(null, "tenant_id"),
            new ColumnReference(null, "secret")),
        analysis.columns());
  }

  @Test
  @DisplayName("drop table and drop view report their target relations")
  void shouldReportDroppedRelations() {
    var dropTable = PostgresQueryAnalyzer.analyze("DROP TABLE private.users, audit");

    assertEquals(StatementKind.DDL, dropTable.statementKind());
    assertEquals(
        List.of(
            new RelationReference("private", "users", null, RelationReference.Kind.PHYSICAL),
            new RelationReference(null, "audit", null, RelationReference.Kind.PHYSICAL)),
        dropTable.relations());
    assertEquals(
        List.of(new RelationReference("private", "v", null, RelationReference.Kind.PHYSICAL)),
        PostgresQueryAnalyzer.analyze("DROP VIEW IF EXISTS private.v").relations());
    assertEquals(
        List.of(new RelationReference(null, "m", null, RelationReference.Kind.PHYSICAL)),
        PostgresQueryAnalyzer.analyze("DROP MATERIALIZED VIEW m").relations());
    assertEquals(
        List.of(new RelationReference(null, "f", null, RelationReference.Kind.PHYSICAL)),
        PostgresQueryAnalyzer.analyze("DROP FOREIGN TABLE f").relations());
  }

  @Test
  @DisplayName("drop of non-relation objects reports no relations")
  void shouldNotReportNonRelationDrops() {
    assertEquals(List.of(), PostgresQueryAnalyzer.analyze("DROP COLLATION c").relations());
    assertEquals(List.of(), PostgresQueryAnalyzer.analyze("DROP INDEX idx").relations());
    assertEquals(List.of(), PostgresQueryAnalyzer.analyze("DROP SCHEMA s").relations());
  }

  @Test
  @DisplayName("ddl targeting a relation through an object name reports the relation")
  void shouldReportAnyNameRelationTargets() {
    var expected =
        List.of(new RelationReference("private", "users", null, RelationReference.Kind.PHYSICAL));

    assertEquals(
        expected,
        PostgresQueryAnalyzer.analyze("COMMENT ON TABLE private.users IS 'x'").relations());
    assertEquals(
        expected,
        PostgresQueryAnalyzer.analyze("SECURITY LABEL ON TABLE private.users IS 'x'").relations());
    assertEquals(
        expected, PostgresQueryAnalyzer.analyze("DROP TRIGGER tr ON private.users").relations());
    assertEquals(
        expected,
        PostgresQueryAnalyzer.analyze("DROP POLICY IF EXISTS p ON private.users").relations());
    assertEquals(
        expected, PostgresQueryAnalyzer.analyze("DROP RULE r ON private.users").relations());
    assertEquals(
        expected,
        PostgresQueryAnalyzer.analyze("COMMENT ON CONSTRAINT c ON private.users IS 'x'")
            .relations());
    assertEquals(
        expected,
        PostgresQueryAnalyzer.analyze("COMMENT ON TRIGGER tr ON private.users IS 'x'").relations());
  }

  @Test
  @DisplayName("comment and security label on a column report the relation and column")
  void shouldReportColumnTargetRelations() {
    var comment = PostgresQueryAnalyzer.analyze("COMMENT ON COLUMN private.users.email IS 'x'");

    assertEquals(
        List.of(new RelationReference("private", "users", null, RelationReference.Kind.PHYSICAL)),
        comment.relations());
    assertEquals(List.of(new ColumnReference("private.users", "email")), comment.columns());
    assertEquals(
        List.of(new RelationReference(null, "users", null, RelationReference.Kind.PHYSICAL)),
        PostgresQueryAnalyzer.analyze("SECURITY LABEL ON COLUMN users.email IS 'x'").relations());
  }

  @Test
  @DisplayName("comments on non-relation objects report no relations")
  void shouldNotReportNonRelationAnyNameTargets() {
    assertEquals(
        List.of(), PostgresQueryAnalyzer.analyze("COMMENT ON SEQUENCE s IS 'x'").relations());
    assertEquals(
        List.of(),
        PostgresQueryAnalyzer.analyze("COMMENT ON CONSTRAINT c ON DOMAIN d IS 'x'").relations());
    assertEquals(
        List.of(),
        PostgresQueryAnalyzer.analyze("COMMENT ON OPERATOR CLASS oc USING btree IS 'x'")
            .relations());
    assertEquals(
        List.of(),
        PostgresQueryAnalyzer.analyze("SECURITY LABEL ON SEQUENCE s IS 'x'").relations());
  }

  @Test
  @DisplayName("json aggregates are reported as functions and accept filter and over clauses")
  void shouldReportJsonAggregates() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "SELECT JSON_OBJECTAGG(k : v) FILTER (WHERE v IS NOT NULL) OVER (PARTITION BY g),"
                + " JSON_ARRAYAGG(v ORDER BY v) FROM t");

    var names = analysis.functions().stream().map(FunctionReference::name).toList();
    assertTrue(names.contains("json_objectagg"), names.toString());
    assertTrue(names.contains("json_arrayagg"), names.toString());
    assertTrue(analysis.features().contains(QueryFeature.WINDOW));
  }

  @Test
  @DisplayName("search and cycle clauses parse and keep cte resolution intact")
  void shouldAnalyzeSearchAndCycleClauses() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "WITH RECURSIVE tr AS (SELECT id, pid FROM edges UNION ALL"
                + " SELECT e.id, e.pid FROM edges e JOIN tr ON e.pid = tr.id)"
                + " SEARCH DEPTH FIRST BY id SET ord"
                + " CYCLE id SET looped USING path"
                + " SELECT * FROM tr WHERE weight > :w");

    assertTrue(analysis.features().contains(QueryFeature.RECURSIVE_CTE));
    assertEquals(Set.of("w"), analysis.parameters());
    var kinds =
        analysis.relations().stream()
            .map(relation -> relation.name() + ":" + relation.kind())
            .toList();
    assertTrue(kinds.contains("edges:PHYSICAL"), kinds.toString());
    assertTrue(kinds.contains("tr:CTE"), kinds.toString());
  }

  @Test
  @DisplayName("merge returning and by source/target variants report their references")
  void shouldAnalyzeModernMerge() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "MERGE INTO t USING s ON t.id = s.id"
                + " WHEN NOT MATCHED BY SOURCE THEN UPDATE SET a = :a"
                + " WHEN NOT MATCHED BY TARGET THEN INSERT VALUES (1)"
                + " RETURNING t.id, s.id");

    assertEquals(StatementKind.MERGE, analysis.statementKind());
    assertEquals(Set.of("a"), analysis.parameters());
    assertTrue(analysis.columns().contains(new ColumnReference("t", "id")));
    assertTrue(analysis.columns().contains(new ColumnReference("s", "id")));
  }

  @Test
  @DisplayName("xmltable and json_table are function relations with aliases")
  void shouldReportTableFunctionRelations() {
    var xml =
        PostgresQueryAnalyzer.analyze(
            "SELECT * FROM XMLTABLE('/r' PASSING x COLUMNS c1 int PATH 'c1') AS xt");
    assertTrue(
        xml.relations()
            .contains(
                new RelationReference(null, "xmltable", "xt", RelationReference.Kind.FUNCTION)),
        xml.relations().toString());
    assertTrue(xml.features().contains(QueryFeature.FUNCTION_RELATION));

    var json =
        PostgresQueryAnalyzer.analyze(
            "SELECT jt.* FROM JSON_TABLE(j, '$[*]' AS root COLUMNS (seq FOR ORDINALITY,"
                + " id int PATH '$.id', has_kids boolean EXISTS PATH '$.kids',"
                + " NESTED PATH '$.kids[*]' COLUMNS (kid text PATH '$.name'))) AS jt"
                + " WHERE jt.id = :id");
    assertTrue(
        json.relations()
            .contains(
                new RelationReference(null, "json_table", "jt", RelationReference.Kind.FUNCTION)),
        json.relations().toString());
    assertTrue(json.features().contains(QueryFeature.FUNCTION_RELATION));
    assertEquals(Set.of("id"), json.parameters());
  }

  @Test
  @DisplayName("statements nested in a function body do not add to the statement count")
  void shouldNotCountFunctionBodyStatements() {
    var analysis =
        PostgresQueryAnalyzer.analyze(
            "CREATE PROCEDURE p() LANGUAGE SQL BEGIN ATOMIC"
                + " DELETE FROM audit; INSERT INTO audit VALUES (1); END");

    assertEquals(1, analysis.statementCount());
    assertTrue(analysis.relations().stream().anyMatch(relation -> relation.name().equals("audit")));
  }

  @Test
  @DisplayName("string constants separated by a newline concatenate as in postgresql")
  void shouldAcceptNewlineConcatenatedStrings() {
    assertEquals(1, PostgresQueryAnalyzer.analyze("SELECT 'a'\n'b'").statementCount());
    assertEquals(
        1, PostgresQueryAnalyzer.analyze("SELECT 'a' -- note\n 'b'\n'c'").statementCount());
    assertEquals(
        1, PostgresQueryAnalyzer.analyze("SELECT U&'d!0061t'\n'x' UESCAPE '!'").statementCount());
  }

  @Test
  @DisplayName("string adjacency without a plain newline separation stays a syntax error")
  void shouldRejectSameLineStringAdjacency() {
    assertThrows(
        QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT 'a' 'b'"));
    assertThrows(
        QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT 'a' /*\n*/ 'b'"));
    assertThrows(
        QueryAnalysisException.class, () -> PostgresQueryAnalyzer.analyze("SELECT $$a$$\n'b'"));
  }
}
