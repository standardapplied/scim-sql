/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

public record OrFilter(Filter left, Filter right) implements Filter {

  @Override
  public String toClause() {
    return String.format("%s OR %s", left.toClause(), right.toClause());
  }

  @Override
  public Context context() {
    return left.context();
  }
}
