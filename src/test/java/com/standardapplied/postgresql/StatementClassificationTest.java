/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Statement classification")
class StatementClassificationTest {

  @ParameterizedTest(name = "{1}: {0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          SELECT 1                                              | SELECT
          TABLE users                                           | SELECT
          (SELECT 1)                                            | SELECT
          INSERT INTO t VALUES (1)                              | INSERT
          UPDATE t SET a = 1                                    | UPDATE
          DELETE FROM t                                         | DELETE
          MERGE INTO t USING s ON t.id = s.id WHEN MATCHED THEN UPDATE SET a = 1 WHEN NOT MATCHED THEN INSERT VALUES (1) | MERGE
          MERGE INTO t USING s ON t.id = s.id WHEN MATCHED THEN DO NOTHING | MERGE
          MERGE INTO t USING s ON t.id = s.id WHEN NOT MATCHED THEN DO NOTHING | MERGE
          MERGE INTO t USING s ON t.id = s.id WHEN MATCHED AND t.a > 1 THEN UPDATE SET a = 2 WHEN MATCHED THEN DELETE WHEN NOT MATCHED THEN INSERT DEFAULT VALUES | MERGE
          MERGE INTO t USING s ON t.id = s.id WHEN MATCHED THEN UPDATE SET a = 1 RETURNING t.id | MERGE
          MERGE INTO t USING s ON t.id = s.id WHEN NOT MATCHED BY SOURCE THEN DELETE WHEN NOT MATCHED BY TARGET THEN INSERT VALUES (1) | MERGE
          SELECT JSON_OBJECTAGG(k : v) FROM t                   | SELECT
          SELECT JSON_ARRAYAGG(v ORDER BY v RETURNING jsonb) FROM t | SELECT
          SELECT a, b FROM t GROUP BY DISTINCT ROLLUP (a), ROLLUP (b) | SELECT
          SELECT x IS JSON OBJECT, y IS NOT JSON ARRAY, z IS JSON WITH UNIQUE KEYS FROM t | SELECT
          WITH RECURSIVE tr AS (SELECT id FROM t2 UNION ALL SELECT t3.id FROM t3 JOIN tr ON t3.pid = tr.id) SEARCH BREADTH FIRST BY id SET ord SELECT * FROM tr | SELECT
          WITH RECURSIVE tr AS (SELECT id FROM t2 UNION ALL SELECT t3.id FROM t3 JOIN tr ON t3.pid = tr.id) CYCLE id SET looped TO true DEFAULT false USING path SELECT * FROM tr | SELECT
          SELECT JSON_OBJECT('a' : x FORMAT JSON RETURNING jsonb FORMAT JSON) FROM t | SELECT
          SELECT breadth, breath, depth, path FROM t            | SELECT
          SELECT a, b FROM t GROUP BY ALL a, b                  | SELECT
          MERGE INTO t USING s ON t.id = s.id WHEN NOT MATCHED BY SOURCE THEN DO NOTHING WHEN NOT MATCHED THEN DO NOTHING | MERGE
          CREATE TABLE tt (r int4range, PRIMARY KEY (r WITHOUT OVERLAPS)) | DDL
          CREATE TABLE tt (id int, r int4range, UNIQUE (id, r WITHOUT OVERLAPS)) | DDL
          WITH src AS (SELECT 1 AS id) MERGE INTO t USING src ON t.id = src.id WHEN MATCHED THEN DO NOTHING | MERGE
          CREATE TABLE t (id int)                               | DDL
          CREATE INDEX idx ON t (id)                            | DDL
          CREATE VIEW v AS SELECT 1                             | DDL
          CREATE MATERIALIZED VIEW mv AS SELECT 1               | DDL
          CREATE ROLE reporting                                 | DDL
          CREATE FUNCTION f() RETURNS int AS 'select 1' LANGUAGE sql | DDL
          CREATE FUNCTION f(i int) RETURNS int LANGUAGE SQL RETURN i + 1 | DDL
          CREATE FUNCTION f(i int) RETURNS int RETURN i + 1     | DDL
          CREATE PROCEDURE p() LANGUAGE SQL BEGIN ATOMIC INSERT INTO audit VALUES (1); END | DDL
          REASSIGN OWNED BY old_role TO new_role                | DDL
          ALTER TABLE t ADD COLUMN b int                        | DDL
          ALTER TABLE t RENAME TO t2                            | DDL
          DROP TABLE t                                          | DDL
          DROP INDEX idx                                        | DDL
          GRANT SELECT ON t TO reporting                        | DDL
          REVOKE SELECT ON t FROM reporting                     | DDL
          COMMENT ON TABLE t IS 'x'                             | DDL
          COPY t FROM STDIN                                     | UTILITY
          COPY (SELECT * FROM t) TO STDOUT                      | UTILITY
          CALL do_things(1)                                     | UTILITY
          DO 'begin end'                                        | UTILITY
          SET search_path = public                              | UTILITY
          SET LOCAL statement_timeout = 100                     | UTILITY
          RESET search_path                                     | UTILITY
          SHOW server_version                                   | UTILITY
          BEGIN                                                 | UTILITY
          COMMIT                                                | UTILITY
          ROLLBACK                                              | UTILITY
          SAVEPOINT sp                                          | UTILITY
          TRUNCATE t                                            | UTILITY
          LOCK TABLE t                                          | UTILITY
          EXPLAIN SELECT 1                                      | UTILITY
          VACUUM t                                              | UTILITY
          ANALYZE t                                             | UTILITY
          PREPARE p AS SELECT 1                                 | UTILITY
          EXECUTE p                                             | UTILITY
          DEALLOCATE p                                          | UTILITY
          LISTEN chan                                           | UTILITY
          NOTIFY chan                                           | UTILITY
          CHECKPOINT                                            | UTILITY
          """)
  void shouldClassify(String sql, StatementKind expected) {
    assertEquals(expected, PostgresQueryAnalyzer.analyze(sql).statementKind());
  }

  @ParameterizedTest(name = "relations survive in {1}: {0}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          EXPLAIN SELECT * FROM users        | users
          CREATE VIEW v AS SELECT * FROM users | users
          CREATE TABLE copy_t AS SELECT * FROM users | users
          CREATE FUNCTION f() RETURNS int LANGUAGE SQL RETURN (SELECT count(*) FROM users) | users
          CREATE PROCEDURE p() LANGUAGE SQL BEGIN ATOMIC DELETE FROM users; END | users
          """)
  void shouldCaptureRelationsInWrappedStatements(String sql, String relation) {
    var names =
        PostgresQueryAnalyzer.analyze(sql).relations().stream()
            .map(RelationReference::name)
            .toList();
    org.junit.jupiter.api.Assertions.assertTrue(names.contains(relation), names.toString());
  }
}
