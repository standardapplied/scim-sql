/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PostgreSQL regression examples vendored from ANTLR grammars-v4 at commit
 * 76093c04af6a51f38a67d14f7e71ff0a9b4400da (sql/postgresql/examples). Each file must analyze end to
 * end; failures indicate a regression in the vendored grammar or the analyzer.
 *
 * <p>Local modification: psql meta-command lines ({@code \d+}, {@code \set}) were removed and
 * client-side {@code \;} separators replaced with plain {@code ;}, because the analyzer
 * deliberately rejects psql meta-commands as non-SQL.
 */
@DisplayName("Upstream regression corpus")
class UpstreamCorpusTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "select.sql",
        "join.sql",
        "aggregates.sql",
        "union.sql",
        "with.sql",
        "window.sql",
        "insert.sql",
        "update.sql",
        "delete.sql",
        "case.sql",
        "subselect.sql",
        "limit.sql",
        "transactions.sql",
        "select_having.sql",
        "select_distinct.sql"
      })
  @DisplayName("corpus file analyzes end to end")
  void shouldAnalyzeCorpusFile(String file) {
    var analysis = PostgresQueryAnalyzer.analyze(read(file));

    assertNotNull(analysis.normalizedSql());
    assertTrue(analysis.statementCount() > 0, file);
  }

  private static String read(String file) {
    try (var in = UpstreamCorpusTest.class.getResourceAsStream("/postgresql-corpus/" + file)) {
      assertNotNull(in, file + " missing from corpus");
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
