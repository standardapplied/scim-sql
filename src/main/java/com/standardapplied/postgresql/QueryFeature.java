/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

/** Syntactic constructs a policy layer may need to distinguish, detected at any nesting depth. */
public enum QueryFeature {
  /** A {@code WITH} clause is present. */
  CTE,
  /** A {@code WITH RECURSIVE} clause is present. */
  RECURSIVE_CTE,
  /** A CTE body is a non-SELECT statement (INSERT, UPDATE, or DELETE). */
  WRITABLE_CTE,
  /** A parenthesized SELECT is used as an expression, derived table, or set-returning source. */
  SUBQUERY,
  /** {@code UNION}, {@code INTERSECT}, or {@code EXCEPT} combines query branches. */
  SET_OPERATION,
  /** A window function {@code OVER} clause or a {@code WINDOW} definition is present. */
  WINDOW,
  /** {@code SELECT ... INTO} creates a table from the result set. */
  SELECT_INTO,
  /** A row-locking clause such as {@code FOR UPDATE} or {@code FOR SHARE} is present. */
  ROW_LOCK,
  /** A {@code LATERAL} relation is present in a FROM clause. */
  LATERAL,
  /** A set-returning function is used as a relation in a FROM clause. */
  FUNCTION_RELATION,
  /** A star projection ({@code *} or {@code alias.*}) is present. */
  STAR_PROJECTION,
  /** A {@code VALUES} list is used as a relation in a FROM clause. */
  VALUES_RELATION,
  /** The input contains more than one statement. */
  MULTIPLE_STATEMENTS
}
