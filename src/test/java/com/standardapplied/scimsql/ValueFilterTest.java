/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ValueFilter")
class ValueFilterTest {

  private final Context context = new Context();

  @Test
  @DisplayName("toClause for NULL type")
  void shouldReturnNullLiteral() {
    var filter = new ValueFilter("null", ValueFilter.ValueType.NULL, context);
    assertEquals("NULL", filter.toClause());
  }

  @Test
  @DisplayName("toClause for string value")
  void shouldReturnQuotedString() {
    var filter = new ValueFilter("hello", context);
    assertEquals("'hello'", filter.toClause());
  }

  @Test
  @DisplayName("toClause escapes single quotes in string")
  void shouldEscapeSingleQuotes() {
    var filter = new ValueFilter("it's", context);
    assertEquals("'it''s'", filter.toClause());
  }

  @Test
  @DisplayName("toClause for boolean value")
  void shouldReturnBooleanLiteral() {
    var filter = new ValueFilter(true, ValueFilter.ValueType.BOOLEAN, context);
    assertEquals("true", filter.toClause());
  }

  @Test
  @DisplayName("toClause for number value")
  void shouldReturnNumberLiteral() {
    var filter = new ValueFilter(42L, ValueFilter.ValueType.NUMBER, context);
    assertEquals("42", filter.toClause());
  }

  @Test
  @DisplayName("toClause for double value")
  void shouldReturnDoubleLiteral() {
    var filter = new ValueFilter(3.14, ValueFilter.ValueType.NUMBER, context);
    assertEquals("3.14", filter.toClause());
  }

  @Test
  @DisplayName("toClause throws for unsupported type")
  void shouldThrowForUnsupportedType() {
    var filter = new ValueFilter(List.of(), ValueFilter.ValueType.STRING, context);
    assertThrows(IllegalArgumentException.class, filter::toClause);
  }

  @Test
  @DisplayName("isUuid returns true for UUID type")
  void shouldIdentifyUuid() {
    var filter = new ValueFilter("abc", ValueFilter.ValueType.UUID, context);
    assertTrue(filter.isUuid());
    assertFalse(filter.isTimestamp());
    assertFalse(filter.isJson());
  }

  @Test
  @DisplayName("isTimestamp returns true for TIMESTAMP type")
  void shouldIdentifyTimestamp() {
    var filter = new ValueFilter("abc", ValueFilter.ValueType.TIMESTAMP, context);
    assertFalse(filter.isUuid());
    assertTrue(filter.isTimestamp());
    assertFalse(filter.isJson());
  }

  @Test
  @DisplayName("isJson returns true for JSON type")
  void shouldIdentifyJson() {
    var filter = new ValueFilter("abc", ValueFilter.ValueType.JSON, context);
    assertFalse(filter.isUuid());
    assertFalse(filter.isTimestamp());
    assertTrue(filter.isJson());
  }

  @Test
  @DisplayName("two-arg constructor defaults to STRING type")
  void shouldDefaultToStringType() {
    var filter = new ValueFilter("test", context);
    assertEquals(ValueFilter.ValueType.STRING, filter.type());
  }

  @Test
  @DisplayName("record accessors work")
  void shouldExposeRecordAccessors() {
    var filter = new ValueFilter("val", ValueFilter.ValueType.UUID, context);
    assertEquals("val", filter.value());
    assertEquals(ValueFilter.ValueType.UUID, filter.type());
    assertSame(context, filter.context());
  }
}
