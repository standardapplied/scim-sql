/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

public record ParenFilter(Filter inner) implements Filter {

  @Override
  public Context context() {
    return inner.context();
  }

  @Override
  public String toClause() {
    return "(%s)".formatted(inner.toClause());
  }
}
