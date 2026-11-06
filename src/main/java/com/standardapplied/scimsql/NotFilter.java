/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

public record NotFilter(Filter filter) implements Filter {

  @Override
  public String toClause() {
    return String.format("NOT (%s)", filter.toClause());
  }

  @Override
  public Context context() {
    return filter.context();
  }
}
