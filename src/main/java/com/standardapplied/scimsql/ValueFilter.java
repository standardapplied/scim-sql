/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

public record ValueFilter(Object value, ValueType type, Context context) implements Filter {

  public enum ValueType {
    STRING,
    UUID,
    TIMESTAMP,
    JSON,
    BOOLEAN,
    NUMBER,
    NULL
  }

  public ValueFilter(Object value, Context context) {
    this(value, ValueType.STRING, context);
  }

  @Override
  public String toClause() {
    if (type == ValueType.NULL) {
      return "NULL";
    } else if (value instanceof String) {
      return "'" + ((String) value).replace("'", "''") + "'";
    } else if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
  }

  public boolean isUuid() {
    return type == ValueType.UUID;
  }

  public boolean isTimestamp() {
    return type == ValueType.TIMESTAMP;
  }

  public boolean isJson() {
    return type == ValueType.JSON;
  }
}
