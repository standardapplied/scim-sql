/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import java.util.Objects;

/**
 * A syntactic reference to a column.
 *
 * <p>Unquoted identifiers are reported case-folded to lowercase, matching PostgreSQL semantics;
 * quoted identifiers are reported exactly. Star usage is reported with {@code "*"} as the name.
 * Ownership of unqualified columns is not resolved — a bare column name may belong to any relation
 * in scope.
 *
 * @param qualifier dotted qualifier preceding the column name, or null when unqualified
 * @param name the column name, or {@code "*"} for star usage
 */
public record ColumnReference(String qualifier, String name) {

  public ColumnReference {
    Objects.requireNonNull(name, "name must not be null");
  }

  /** True when this reference is a star projection ({@code *} or {@code alias.*}). */
  public boolean star() {
    return "*".equals(name);
  }
}
