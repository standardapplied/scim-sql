/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

public record PresentFilter(Filter attribute) implements Filter {

  @Override
  public String toClause() {
    return "%s IS NOT NULL".formatted(Filter.camelToSnake(attribute.toClause()));
  }

  @Override
  public Context context() {
    return attribute.context();
  }
}
