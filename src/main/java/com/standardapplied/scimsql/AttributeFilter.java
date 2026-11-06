/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

public record AttributeFilter(String name, Filter subAttribute, String prefix, Context context)
    implements Filter {

  @Override
  public String toClause() {
    if (subAttribute == null) {
      return prefix.isEmpty() ? name : prefix + "." + name;
    }
    return name + "." + subAttribute.toClause();
  }

  @Override
  public String toString() {
    if (subAttribute == null) {
      return name;
    }
    return name + "." + subAttribute.toString();
  }
}
