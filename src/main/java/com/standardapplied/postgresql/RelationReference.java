/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import java.util.Objects;

/**
 * A syntactic reference to a relation.
 *
 * <p>Unquoted identifiers are reported case-folded to lowercase, matching PostgreSQL semantics;
 * quoted identifiers are reported exactly. An unqualified name that matches an in-scope CTE name is
 * reported as {@link Kind#CTE}; classification is purely syntactic and does not resolve catalog
 * objects.
 *
 * @param schema dotted qualifier preceding the relation name, or null when unqualified
 * @param name the relation, CTE, or function name
 * @param alias the alias bound to this relation, or null
 * @param kind how the relation is referenced
 */
public record RelationReference(String schema, String name, String alias, Kind kind) {

  /** How a relation is referenced. */
  public enum Kind {
    /** A schema-qualified or unqualified physical relation name. */
    PHYSICAL,
    /** An unqualified name matching a common table expression in scope. */
    CTE,
    /** A set-returning function used as a relation. */
    FUNCTION
  }

  public RelationReference {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
  }
}
