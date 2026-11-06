/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import java.util.Objects;

/**
 * A syntactic reference to a function call.
 *
 * <p>Unquoted identifiers are reported case-folded to lowercase, matching PostgreSQL semantics;
 * quoted identifiers are reported exactly. Special-form functions ({@code CAST}, {@code COALESCE},
 * {@code EXTRACT}, {@code CURRENT_DATE}, and similar) are reported by their lowercase keyword with
 * a null schema.
 *
 * @param schema dotted qualifier preceding the function name, or null when unqualified
 * @param name the function name
 * @param line 1-based line of the call site
 * @param column 0-based column of the call site
 */
public record FunctionReference(String schema, String name, int line, int column) {

  public FunctionReference {
    Objects.requireNonNull(name, "name must not be null");
  }
}
