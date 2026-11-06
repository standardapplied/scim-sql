/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

/**
 * Thrown when SQL cannot be analyzed: null, blank, or oversized input, unrecognized tokens, syntax
 * errors, excessive nesting, or an empty statement list.
 *
 * <p>The message carries only a stable reason and, when available, a line and column. It never
 * contains SQL text, literals, or parser internals, so it is safe to log and to return to callers.
 */
public class QueryAnalysisException extends RuntimeException {

  private final String reason;
  private final int line;
  private final int column;

  public QueryAnalysisException(String reason, int line, int column) {
    super(line >= 1 ? "%s at line %d, column %d".formatted(reason, line, column) : reason);
    this.reason = reason;
    this.line = line;
    this.column = column;
  }

  /** Stable, content-free description of the failure. */
  public String reason() {
    return reason;
  }

  /** 1-based line of the failure, or -1 when not applicable. */
  public int line() {
    return line;
  }

  /** 0-based column of the failure, or -1 when not applicable. */
  public int column() {
    return column;
  }
}
