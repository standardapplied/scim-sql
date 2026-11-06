/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

import java.util.List;
import java.util.stream.Collectors;

public record ArrayValueFilter(List<Filter> values, Context context) implements Filter {

  @Override
  public String toClause() {
    return values.stream().map(Filter::toClause).collect(Collectors.joining(", "));
  }
}
