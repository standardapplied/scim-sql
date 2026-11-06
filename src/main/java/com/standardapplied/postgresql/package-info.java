/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

/**
 * Syntactic analysis of complete PostgreSQL statements.
 *
 * <p>{@link com.standardapplied.postgresql.PostgresQueryAnalyzer#analyze(String)} parses a full SQL
 * string through EOF and returns an immutable {@link com.standardapplied.postgresql.QueryAnalysis}
 * describing what the SQL <em>is</em>: statement kind and count, every syntactically reachable
 * relation, column, and function reference, named parameters ({@code :name}), policy-relevant
 * features, and a deterministic normalized form suitable for hashing and audit comparison.
 *
 * <p>This package parses and describes SQL. It does not execute SQL, resolve catalog objects,
 * authorize access, or decide query cost — callers own policy. Analysis is purely syntactic:
 * relations that look like in-scope CTE names are reported as CTE references, everything else as
 * physical or function relations, without pretending to resolve database objects.
 *
 * <p>The grammar is the ANTLR grammars-v4 PostgreSQL grammar, vendored at a pinned upstream commit
 * with one deliberate extension: named parameters such as {@code :start_at} are first-class
 * expression values. A colon that directly continues an expression — a JSON {@code key:value}
 * separator or an array-slice bound such as {@code arr[lo:hi]} — stays an operator; the single
 * ambiguous form {@code arr[:name]} binds to the parameter extension. See the repository NOTICE.md
 * for provenance and the exact local modifications.
 */
package com.standardapplied.postgresql;
