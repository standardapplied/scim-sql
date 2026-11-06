/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

/**
 * Coarse classification of the first statement in an analyzed SQL string.
 *
 * <p>{@code CREATE}, {@code ALTER}, {@code DROP}, and other schema- or privilege-changing
 * statements (including {@code GRANT}/{@code REVOKE}, {@code COMMENT ON}, and {@code CREATE INDEX})
 * classify as {@link #DDL}. Everything else that is not DML — {@code COPY}, {@code CALL}, {@code
 * DO}, {@code SET}, {@code SHOW}, {@code EXPLAIN}, {@code TRUNCATE}, transaction control, cursors,
 * and similar commands — classifies as {@link #UTILITY}.
 */
public enum StatementKind {
  SELECT,
  INSERT,
  UPDATE,
  DELETE,
  MERGE,
  DDL,
  UTILITY,
  UNKNOWN
}
