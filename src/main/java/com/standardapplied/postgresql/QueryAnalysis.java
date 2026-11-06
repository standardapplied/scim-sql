/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable structural description of an analyzed SQL string.
 *
 * <p>When the input holds multiple statements, {@link #statementKind()} describes the first
 * statement, {@link QueryFeature#MULTIPLE_STATEMENTS} is set, and references and features are
 * aggregated across all statements.
 *
 * @param statementKind classification of the first statement
 * @param statementCount number of statements; semicolons inside strings, dollar-quoted values, and
 *     comments do not create statements
 * @param relations every syntactically reachable relation reference, in source order
 * @param columns every syntactically reachable column reference, including star usage
 * @param functions every syntactically reachable function call, in source order
 * @param parameters deduplicated named-parameter names, without the leading colon, exactly as
 *     written
 * @param features policy-relevant syntactic constructs detected at any depth
 * @param normalizedSql deterministic single-line form, stable across whitespace, comments, and
 *     keyword or unquoted-identifier case, suitable for hashing and audit comparison
 */
public record QueryAnalysis(
    StatementKind statementKind,
    int statementCount,
    List<RelationReference> relations,
    List<ColumnReference> columns,
    List<FunctionReference> functions,
    Set<String> parameters,
    Set<QueryFeature> features,
    String normalizedSql) {

  public QueryAnalysis {
    Objects.requireNonNull(statementKind, "statementKind must not be null");
    Objects.requireNonNull(normalizedSql, "normalizedSql must not be null");
    if (statementCount < 1) {
      throw new IllegalArgumentException("statementCount must be at least 1");
    }
    relations = List.copyOf(relations);
    columns = List.copyOf(columns);
    functions = List.copyOf(functions);
    parameters = Collections.unmodifiableSet(new LinkedHashSet<>(parameters));
    features =
        features.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(features));
  }
}
