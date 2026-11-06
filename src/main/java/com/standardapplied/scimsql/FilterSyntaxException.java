/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

/**
 * Thrown when a filter expression is not one complete filter the engine can evaluate.
 *
 * <p>It is the caller of the filter who got it wrong, typically a client. Catch this type to answer
 * with a client error rather than a server error.
 */
public final class FilterSyntaxException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  FilterSyntaxException(String message, Throwable cause) {
    super(message, cause);
  }
}
