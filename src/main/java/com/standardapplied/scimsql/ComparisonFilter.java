/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

import java.util.Objects;

public class ComparisonFilter implements Filter {
  private final Filter attribute;
  private final String operator;
  private final Filter value;
  private final Context context;

  public ComparisonFilter(Filter attribute, String operator, Filter value, Context context) {
    this.attribute = attribute;
    this.operator = operator;
    this.value = value;
    this.context = Objects.requireNonNull(context);
  }

  public Filter attribute() {
    return attribute;
  }

  public String operator() {
    return operator;
  }

  public Filter value() {
    return value;
  }

  @Override
  public Context context() {
    return context;
  }

  @Override
  public String toClause() {
    String key = Filter.camelToSnake(attribute.toClause());
    String paramKey = context.process(attribute, value, this::paramKey);

    if (value instanceof ValueFilter valueFilter) {
      if (valueFilter.isUuid()) {
        paramKey = "CAST(%s AS UUID)".formatted(paramKey);
      } else if (valueFilter.isTimestamp()) {
        paramKey = "CAST(%s AS timestamptz)".formatted(paramKey);
      } else if (valueFilter.isJson()) {
        paramKey = "CAST(%s AS jsonb)".formatted(paramKey);
      }
    }

    return switch (operator) {
      case "eq" -> {
        if (value instanceof ValueFilter valueFilter && valueFilter.isJson()) {
          yield String.format("%s @> %s", key, paramKey);
        }
        yield String.format("%s = %s", key, paramKey);
      }
      case "ne" -> String.format("%s != %s", key, paramKey);
      case "gt" -> String.format("%s > %s", key, paramKey);
      case "lt" -> String.format("%s < %s", key, paramKey);
      case "ge" -> String.format("%s >= %s", key, paramKey);
      case "le" -> String.format("%s <= %s", key, paramKey);
      case "co" -> String.format("LOWER(%s) LIKE '%%' || LOWER(%s) || '%%'", key, paramKey);
      case "sw" -> String.format("LOWER(%s) LIKE LOWER(%s) || '%%'", key, paramKey);
      case "ew" -> String.format("LOWER(%s) LIKE '%%' || LOWER(%s)", key, paramKey);
      default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
    };
  }

  public String paramKey(String indexedKey) {
    return ":" + indexedKey;
  }

  public static class ListFilter extends ComparisonFilter {
    public ListFilter(ComparisonFilter filter) {
      super(filter.attribute(), filter.operator(), filter.value(), filter.context());
    }
  }
}
