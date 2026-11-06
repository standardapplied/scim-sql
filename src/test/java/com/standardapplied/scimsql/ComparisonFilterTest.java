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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ComparisonFilter")
class ComparisonFilterTest {

  @Test
  @DisplayName("unsupported operator throws")
  void shouldThrowForUnsupportedOperator() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("test", context);
    var filter = new ComparisonFilter(attr, "xx", value, context);

    assertThrows(IllegalArgumentException.class, filter::toClause);
  }

  @Test
  @DisplayName("null context throws NullPointerException")
  void shouldThrowForNullContext() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("test", context);

    assertThrows(NullPointerException.class, () -> new ComparisonFilter(attr, "eq", value, null));
  }

  @Test
  @DisplayName("accessors return constructor values")
  void shouldExposeAccessors() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("test", context);
    var filter = new ComparisonFilter(attr, "eq", value, context);

    assertSame(attr, filter.attribute());
    assertEquals("eq", filter.operator());
    assertSame(value, filter.value());
    assertSame(context, filter.context());
  }

  @Test
  @DisplayName("paramKey prefixes with colon")
  void shouldPrefixWithColon() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("test", context);
    var filter = new ComparisonFilter(attr, "eq", value, context);

    assertEquals(":key1", filter.paramKey("key1"));
  }

  @Test
  @DisplayName("ListFilter wraps ComparisonFilter")
  void shouldCreateListFilter() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("test", context);
    var base = new ComparisonFilter(attr, "eq", value, context);

    var listFilter = new ComparisonFilter.ListFilter(base);

    assertSame(base.attribute(), listFilter.attribute());
    assertEquals(base.operator(), listFilter.operator());
    assertSame(base.value(), listFilter.value());
    assertSame(base.context(), listFilter.context());
  }

  @Test
  @DisplayName("eq with non-JSON value produces = clause")
  void shouldProduceEqualClause() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("john", context);
    var filter = new ComparisonFilter(attr, "eq", value, context);

    assertEquals("t.name = :name1", filter.toClause());
  }

  @Test
  @DisplayName("toClause with UUID value wraps in CAST")
  void shouldCastUuid() {
    var context = new Context();
    var attr = new AttributeFilter("id", null, "t", context);
    var value = new ValueFilter("abc-123", ValueFilter.ValueType.UUID, context);
    var filter = new ComparisonFilter(attr, "eq", value, context);

    assertEquals("t.id = CAST(:id1 AS UUID)", filter.toClause());
  }

  @Test
  @DisplayName("toClause with timestamp value wraps in CAST")
  void shouldCastTimestamp() {
    var context = new Context();
    var attr = new AttributeFilter("createdAt", null, "t", context);
    var value = new ValueFilter("2026-01-01T00:00:00Z", ValueFilter.ValueType.TIMESTAMP, context);
    var filter = new ComparisonFilter(attr, "gt", value, context);

    assertEquals("t.created_at > CAST(:createdAt1 AS timestamptz)", filter.toClause());
  }

  @Test
  @DisplayName("toClause with JSON eq uses containment operator")
  void shouldUseContainmentForJsonEq() {
    var context = new Context();
    var attr = new AttributeFilter("meta", null, "t", context);
    var value = new ValueFilter("{\"k\":\"v\"}", ValueFilter.ValueType.JSON, context);
    var filter = new ComparisonFilter(attr, "eq", value, context);

    assertEquals("t.meta @> CAST(:meta1 AS jsonb)", filter.toClause());
  }

  @Test
  @DisplayName("toClause with JSON ne uses != not containment")
  void shouldUseNotEqualForJsonNe() {
    var context = new Context();
    var attr = new AttributeFilter("meta", null, "t", context);
    var value = new ValueFilter("{\"k\":\"v\"}", ValueFilter.ValueType.JSON, context);
    var filter = new ComparisonFilter(attr, "ne", value, context);

    assertEquals("t.meta != CAST(:meta1 AS jsonb)", filter.toClause());
  }

  @Test
  @DisplayName("toClause with non-ValueFilter value skips type casting")
  void shouldSkipCastForNonValueFilter() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var nonValue = new AttributeFilter("other", null, "t", context);
    var filter = new ComparisonFilter(attr, "eq", nonValue, context);

    var clause = filter.toClause();
    assertTrue(clause.startsWith("t.name = :"));
    assertFalse(clause.contains("CAST"));
  }
}
