/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.scimsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Context")
class ContextTest {

  @Test
  @DisplayName("process stores value and returns mapped key")
  void shouldProcessAndStoreValue() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("john", context);

    var result = context.process(attr, value, k -> ":" + k);

    assertEquals(":name1", result);
    assertEquals("john", context.indexedParams().get("name1"));
    assertEquals(1, context.params().get("name").size());
  }

  @Test
  @DisplayName("process with non-ValueFilter stores null")
  void shouldStoreNullForNonValueFilter() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var nonValue = new AndFilter(attr, attr);

    context.process(attr, nonValue, k -> ":" + k);

    assertNull(context.indexedParams().get("name1"));
  }

  @Test
  @DisplayName("process increments index for same attribute")
  void shouldIncrementIndexForSameAttribute() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value1 = new ValueFilter("john", context);
    var value2 = new ValueFilter("jane", context);

    assertEquals(":name1", context.process(attr, value1, k -> ":" + k));
    assertEquals(":name2", context.process(attr, value2, k -> ":" + k));
    assertEquals(2, context.params().get("name").size());
  }

  @Test
  @DisplayName("process replaces dots with underscores in indexed key")
  void shouldReplaceDots() {
    var context = new Context();
    var sub = new AttributeFilter("value", null, "", context);
    var attr = new AttributeFilter("email", sub, "t", context);
    var value = new ValueFilter("test", context);

    var result = context.process(attr, value, k -> ":" + k);

    assertEquals(":email_value1", result);
  }

  @Test
  @DisplayName("processArray handles multiple values")
  void shouldProcessArrayValues() {
    var context = new Context();
    var attr = new AttributeFilter("id", null, "t", context);
    var values =
        List.<Filter>of(
            new ValueFilter(1L, context),
            new ValueFilter(2L, context),
            new ValueFilter(3L, context));

    var result = context.processArray(attr, values, k -> ":" + k);

    assertEquals(":id1, :id2, :id3", result);
    assertEquals(3, context.indexedParams().size());
  }

  @Test
  @DisplayName("processArray wraps UUID values with CAST")
  void shouldCastUuidInArray() {
    var context = new Context();
    var attr = new AttributeFilter("id", null, "t", context);
    var values =
        List.<Filter>of(
            new ValueFilter("abc-123", ValueFilter.ValueType.UUID, context),
            new ValueFilter("def-456", ValueFilter.ValueType.UUID, context));

    var result = context.processArray(attr, values, k -> ":" + k);

    assertEquals("CAST(:id1 AS UUID), CAST(:id2 AS UUID)", result);
  }

  @Test
  @DisplayName("processArray handles non-ValueFilter items")
  void shouldHandleNonValueFilterInArray() {
    var context = new Context();
    var attr = new AttributeFilter("id", null, "t", context);
    var dummy = new AttributeFilter("x", null, "", context);
    var values = List.<Filter>of(dummy);

    var result = context.processArray(attr, values, k -> ":" + k);

    assertEquals(":id1", result);
    assertNull(context.indexedParams().get("id1"));
  }

  @Test
  @DisplayName("isValid returns true when all params are valid")
  void shouldReturnTrueForValidParams() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("test", context);
    context.process(attr, value, k -> ":" + k);

    assertTrue(context.isValid(Set.of("name")));
  }

  @Test
  @DisplayName("isValid returns false when param key is not in valid set")
  void shouldReturnFalseForInvalidParams() {
    var context = new Context();
    var attr = new AttributeFilter("name", null, "t", context);
    var value = new ValueFilter("test", context);
    context.process(attr, value, k -> ":" + k);

    assertFalse(context.isValid(Set.of("age")));
  }

  @Test
  @DisplayName("isValid returns true for empty params")
  void shouldReturnTrueForEmptyParams() {
    var context = new Context();
    assertTrue(context.isValid(Set.of()));
  }

  @Test
  @DisplayName("isValid sees an attribute processed as a list")
  void shouldSeeAnAttributeProcessedAsAList() {
    var context = new Context();
    var attr = new AttributeFilter("id", null, "t", context);
    context.processArray(attr, List.<Filter>of(new ValueFilter(1L, context)), k -> ":" + k);

    assertFalse(context.isValid(Set.of("name")));
    assertTrue(context.isValid(Set.of("id")));
  }

  @Test
  @DisplayName("isValid sees a recorded attribute that binds no parameter")
  void shouldSeeARecordedAttributeThatBindsNoParameter() {
    var context = new Context();
    var sub = new AttributeFilter("userName", null, "", context);
    context.reference(new AttributeFilter("u", sub, "t", context));

    assertFalse(context.isValid(Set.of("userName")));
    assertTrue(context.isValid(Set.of("u.userName")));
    assertTrue(context.indexedParams().isEmpty());
  }

  @Test
  @DisplayName("Building a filter by hand records nothing")
  void shouldRecordNothingWhenAFilterIsOnlyBuilt() {
    var context = new Context();
    var attr = new AttributeFilter("scratch", null, "t", context);
    new PresentFilter(attr);
    new ComparisonFilter(attr, "eq", new ValueFilter("x", context), context);

    assertTrue(context.isValid(Set.of()));
  }

  @Test
  @DisplayName("A presence check needs no context to render")
  void shouldRenderAPresenceCheckWithoutAContext() {
    var filter = new PresentFilter(new AttributeFilter("a", null, "t", null));

    assertEquals("t.a IS NOT NULL", filter.toClause());
  }
}
