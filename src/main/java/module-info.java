/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

/**
 * SCIM filter expression to parameterized SQL converter and PostgreSQL query analyzer.
 *
 * <p>The {@code com.standardapplied.scimsql} package parses <a
 * href="https://www.rfc-editor.org/rfc/rfc7644#section-3.4.2.2">SCIM filtering</a> expressions and
 * converts them to parameterized SQL WHERE clauses. Supports all SCIM comparison operators (eq, ne,
 * gt, lt, ge, le, co, sw, ew), logical operators (and, or, not), presence (pr), and the in operator
 * with typed values (UUID, timestamp, JSON, boolean, number, string).
 *
 * <p>The {@code com.standardapplied.postgresql} package parses complete PostgreSQL statements and
 * reports structural facts — statement kind and count, referenced relations, columns, functions,
 * named parameters, and policy-relevant syntactic features — without executing SQL or resolving
 * catalog objects.
 */
module com.standardapplied.scimsql {
  requires org.antlr.antlr4.runtime;

  exports com.standardapplied.scimsql;
  exports com.standardapplied.postgresql;
}
