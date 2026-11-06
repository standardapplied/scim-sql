/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.scimsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Filter.camelToSnake")
class FilterTest {

  @Test
  @DisplayName("null returns null")
  void shouldReturnNullForNull() {
    assertNull(Filter.camelToSnake(null));
  }

  @Test
  @DisplayName("empty returns empty")
  void shouldReturnEmptyForEmpty() {
    assertEquals("", Filter.camelToSnake(""));
  }

  @Test
  @DisplayName("single lowercase character")
  void shouldHandleSingleChar() {
    assertEquals("a", Filter.camelToSnake("a"));
  }

  @Test
  @DisplayName("single uppercase character")
  void shouldHandleSingleUpperChar() {
    assertEquals("a", Filter.camelToSnake("A"));
  }

  @Test
  @DisplayName("already snake_case")
  void shouldHandleAlreadySnakeCase() {
    assertEquals("user_name", Filter.camelToSnake("user_name"));
  }

  @Test
  @DisplayName("simple camelCase")
  void shouldConvertSimpleCamelCase() {
    assertEquals("user_name", Filter.camelToSnake("userName"));
  }

  @Test
  @DisplayName("multiple uppercase letters")
  void shouldConvertMultipleUppercase() {
    assertEquals("created_at_utc", Filter.camelToSnake("createdAtUtc"));
  }

  @Test
  @DisplayName("leading uppercase")
  void shouldConvertLeadingUppercase() {
    assertEquals("user_name", Filter.camelToSnake("UserName"));
  }

  @Test
  @DisplayName("all lowercase")
  void shouldHandleAllLowercase() {
    assertEquals("name", Filter.camelToSnake("name"));
  }
}
