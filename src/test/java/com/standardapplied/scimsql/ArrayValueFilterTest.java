/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ArrayValueFilter")
class ArrayValueFilterTest {

  @Test
  @DisplayName("toClause joins value clauses with comma")
  void shouldJoinValueClauses() {
    var context = new Context();
    var values =
        List.<Filter>of(
            new ValueFilter(1L, context),
            new ValueFilter(2L, context),
            new ValueFilter(3L, context));
    var filter = new ArrayValueFilter(values, context);

    assertEquals("1, 2, 3", filter.toClause());
  }

  @Test
  @DisplayName("toClause with single value")
  void shouldHandleSingleValue() {
    var context = new Context();
    var values = List.<Filter>of(new ValueFilter("hello", context));
    var filter = new ArrayValueFilter(values, context);

    assertEquals("'hello'", filter.toClause());
  }

  @Test
  @DisplayName("toClause with empty values")
  void shouldHandleEmptyValues() {
    var context = new Context();
    var filter = new ArrayValueFilter(List.of(), context);

    assertEquals("", filter.toClause());
  }

  @Test
  @DisplayName("record accessors work")
  void shouldExposeRecordAccessors() {
    var context = new Context();
    var values = List.<Filter>of(new ValueFilter("a", context));
    var filter = new ArrayValueFilter(values, context);

    assertEquals(values, filter.values());
    assertSame(context, filter.context());
  }
}
