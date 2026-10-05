/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.scimsql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SCIM Filter Evaluator")
class ScimEngineTest {

  private ScimEngine engine;

  @BeforeEach
  void setUp() {
    engine = new ScimEngine();
  }

  @Nested
  @DisplayName("Simple Comparison Tests")
  class SimpleComparisonTests {

    @Test
    @DisplayName("Equal operator")
    void shouldHandleEqualOperator() {
      Filter result = engine.parseFilter("userName eq \"john.doe\"", "t", null);
      assertEquals("t.user_name = :userName1", result.toClause());
      var map = result.context().params();
      assertEquals(1, map.size());
    }

    @Test
    @DisplayName("Not equal operator")
    void shouldHandleNotEqualOperator() {
      Filter result = engine.parseFilter("userName ne \"john.doe\"", "t", null);
      assertEquals("t.user_name != :userName1", result.toClause());
    }

    @Test
    @DisplayName("Contains operator")
    void shouldHandleContainsOperator() {
      Filter result = engine.parseFilter("userName co \"john.doe\"", "t", null);
      assertEquals("LOWER(t.user_name) LIKE '%' || LOWER(:userName1) || '%'", result.toClause());
    }

    @Test
    @DisplayName("Starts with operator")
    void shouldHandleStartsWithOperator() {
      Filter result = engine.parseFilter("userName sw \"john.doe\"", "t", null);
      assertEquals("LOWER(t.user_name) LIKE LOWER(:userName1) || '%'", result.toClause());
    }

    @Test
    @DisplayName("Ends with operator")
    void shouldHandleEndsWithOperator() {
      Filter result = engine.parseFilter("userName ew \"john.doe\"", "t", null);
      assertEquals("LOWER(t.user_name) LIKE '%' || LOWER(:userName1)", result.toClause());
    }

    @Test
    @DisplayName("Greater than operator")
    void shouldHandleGreaterThanOperator() {
      Filter result = engine.parseFilter("age gt 25", "t", null);
      assertEquals("t.age > :age1", result.toClause());
    }

    @Test
    @DisplayName("Greater than or equal operator")
    void shouldHandleGreaterThanOrEqualOperator() {
      Filter result = engine.parseFilter("age ge 25", "t", null);
      assertEquals("t.age >= :age1", result.toClause());
    }

    @Test
    @DisplayName("Less than operator")
    void shouldHandleLessThanOperator() {
      Filter result = engine.parseFilter("age lt 25", "t", null);
      assertEquals("t.age < :age1", result.toClause());
    }

    @Test
    @DisplayName("Less than or equal operator")
    void shouldHandleLessThanOrEqualOperator() {
      Filter result = engine.parseFilter("age le 25", "t", null);
      assertEquals("t.age <= :age1", result.toClause());
    }

    @Test
    @DisplayName("Present operator")
    void shouldHandlePresentOperator() {
      Filter result = engine.parseFilter("userName pr", "t", null);
      assertEquals("t.user_name IS NOT NULL", result.toClause());
      assertNotNull(result.context());
    }
  }

  @Nested
  @DisplayName("Logical Operator Tests")
  class LogicalOperatorTests {

    @Test
    @DisplayName("AND operator")
    void shouldHandleAndOperator() {
      Filter result = engine.parseFilter("userName eq \"john\" and active eq true", "t", null);
      assertEquals("t.user_name = :userName1 AND t.active = :active1", result.toClause());
    }

    @Test
    @DisplayName("OR operator")
    void shouldHandleOrOperator() {
      Filter result = engine.parseFilter("userName eq \"john\" or userName eq \"jane\"", "t", null);
      assertEquals("t.user_name = :userName1 OR t.user_name = :userName2", result.toClause());
    }

    @Test
    @DisplayName("NOT operator")
    void shouldHandleNotOperator() {
      Filter result = engine.parseFilter("not (active eq true)", "t", null);
      assertEquals("NOT (t.active = :active1)", result.toClause());
      assertNotNull(result.context());
    }

    @Test
    @DisplayName("IN operator")
    void shouldHandleInOperator() {
      Filter result = engine.parseFilter("id in [1, 2, 3]", "t", null);
      assertEquals("t.id IN (:id1, :id2, :id3)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(3, map.size());

      result =
          engine.parseFilter(
              "id in [\"#59b47990-39d1-46c7-ac88-0808abd49a94\", \"#59b47990-39d1-46c7-ac88-0808abd49a95\"]",
              "t",
              null);
      assertEquals("t.id IN (CAST(:id1 AS UUID), CAST(:id2 AS UUID))", result.toClause());
    }

    @Test
    @DisplayName("Complex nested operators")
    void shouldHandleComplexNestedOperators() {
      Filter result =
          engine.parseFilter(
              "emails.work.value co \"@example.com\" and (age gt 25 or active eq true)", "t", null);
      result.toClause();
      var map = result.context().params();
      assertEquals(3, map.size());

      result =
          engine.parseFilter(
              "customQuery eq \"${\\\"request\\\":{\\\"parentId\\\":\\\"123sfasdfa\\\"}}\"",
              "t",
              null);
      result.toClause();
      map = result.context().params();
      assertEquals(1, map.size());
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorTests {

    @Test
    @DisplayName("No filter")
    void shouldThrowExceptionForNoFilter() {
      assertThrows(IllegalArgumentException.class, () -> engine.parseFilter("", "t", null));
    }

    @Test
    @DisplayName("Invalid operator")
    void shouldThrowExceptionForInvalidOperator() {
      assertThrows(
          IllegalArgumentException.class,
          () -> engine.parseFilter("userName invalid \"john\"", "t", null));
    }

    @Test
    @DisplayName("Missing value")
    void shouldThrowExceptionForMissingValue() {
      assertThrows(
          IllegalArgumentException.class, () -> engine.parseFilter("userName eq", "t", null));
    }

    @Test
    @DisplayName("Invalid attribute path")
    void shouldThrowExceptionForInvalidAttributePath() {
      assertThrows(
          IllegalArgumentException.class,
          () -> engine.parseFilter(".userName eq \"john\"", "t", null));
    }

    @Test
    @DisplayName("Unmatched parentheses")
    void shouldThrowExceptionForUnmatchedParentheses() {
      assertThrows(
          IllegalArgumentException.class,
          () -> engine.parseFilter("(userName eq \"john\"", "t", null));
    }

    @Test
    @DisplayName("Invalid value type")
    void shouldThrowExceptionForInvalidValueType() {
      assertThrows(
          IllegalArgumentException.class, () -> engine.parseFilter("ageeq true", "t", null));
    }
  }

  @Nested
  @DisplayName("Special Cases")
  class SpecialCasesTests {

    @Test
    @DisplayName("Multiple level parentheses")
    void shouldHandleMultipleLevelParentheses() {
      Filter result =
          engine.parseFilter("not (active eq true and (age gt 25 or name co \"john\"))", "t", null);
      assertEquals(
          "NOT (t.active = :active1 AND (t.age > :age1 OR LOWER(t.name) LIKE '%' || LOWER(:name1) || '%'))",
          result.toClause());
    }

    @Test
    @DisplayName("Parenthesized expression exposes context")
    void shouldExposeContextFromParenthesizedExpression() {
      Filter result = engine.parseFilter("(userName eq \"john\")", "t", null);
      assertEquals("(t.user_name = :userName1)", result.toClause());
      assertNotNull(result.context());
      assertEquals("john", result.context().indexedParams().get("userName1"));
    }

    @Test
    @DisplayName("Null literal value")
    void shouldHandleNullValue() {
      Filter result = engine.parseFilter("deletedAt eq null", "t", null);
      assertEquals("t.deleted_at = :deletedAt1", result.toClause());
    }

    @Test
    @DisplayName("Double literal value")
    void shouldHandleDoubleValue() {
      Filter result = engine.parseFilter("score eq 3.14", "t", null);
      assertEquals("t.score = :score1", result.toClause());
      assertEquals(3.14, result.context().indexedParams().get("score1"));
    }

    @Test
    @DisplayName("Negative integer value")
    void shouldHandleNegativeInteger() {
      Filter result = engine.parseFilter("balance eq -100", "t", null);
      assertEquals("t.balance = :balance1", result.toClause());
      assertEquals(-100L, result.context().indexedParams().get("balance1"));
    }

    @Test
    @DisplayName("Negative double value")
    void shouldHandleNegativeDouble() {
      Filter result = engine.parseFilter("temperature eq -3.14", "t", null);
      assertEquals("t.temperature = :temperature1", result.toClause());
      assertEquals(-3.14, result.context().indexedParams().get("temperature1"));
    }

    @Test
    @DisplayName("Double with scientific notation")
    void shouldHandleDoubleScientificNotation() {
      Filter result = engine.parseFilter("score eq 1.5E2", "t", null);
      assertEquals("t.score = :score1", result.toClause());
      assertEquals(150.0, result.context().indexedParams().get("score1"));
    }

    @Test
    @DisplayName("Boolean false value")
    void shouldHandleFalseBoolean() {
      Filter result = engine.parseFilter("active eq false", "t", null);
      assertEquals("t.active = :active1", result.toClause());
      assertEquals(false, result.context().indexedParams().get("active1"));
    }

    @Test
    @DisplayName("Custom compareFilterBuilder wraps as ListFilter")
    void shouldUseCustomCompareFilterBuilder() {
      Filter result = engine.parseFilter("name eq \"test\"", "t", ComparisonFilter.ListFilter::new);
      assertInstanceOf(ComparisonFilter.ListFilter.class, result);
      assertEquals("t.name = :name1", result.toClause());
    }

    @Test
    @DisplayName("IN with string values")
    void shouldHandleInWithStrings() {
      Filter result = engine.parseFilter("status in [\"active\", \"pending\"]", "t", null);
      assertEquals("t.status IN (:status1, :status2)", result.toClause());
      assertEquals("active", result.context().indexedParams().get("status1"));
      assertEquals("pending", result.context().indexedParams().get("status2"));
    }
  }

  @Nested
  @DisplayName("Explicit Alias Tests")
  class ExplicitAliasTests {

    @Test
    @DisplayName("Explicit alias overrides default prefix")
    void shouldUseExplicitAliasInsteadOfDefaultPrefix() {
      Filter result =
          engine.parseFilter("eh.userId eq \"#123e4567-e89b-12d3-a456-426614174000\"", "t", null);
      assertEquals("eh.user_id = CAST(:eh_userId1 AS UUID)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(1, map.size());
      assertEquals("123e4567-e89b-12d3-a456-426614174000", map.get("eh_userId1"));
    }

    @Test
    @DisplayName("Multiple explicit aliases in same filter")
    void shouldHandleMultipleExplicitAliases() {
      Filter result =
          engine.parseFilter(
              "eh.userId eq \"#123e4567-e89b-12d3-a456-426614174000\" or ea.userId eq \"#550e8400-e29b-41d4-a716-446655440000\"",
              "t",
              null);
      assertEquals(
          "eh.user_id = CAST(:eh_userId1 AS UUID) OR ea.user_id = CAST(:ea_userId1 AS UUID)",
          result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
    }

    @Test
    @DisplayName("Mix of explicit alias and default prefix")
    void shouldHandleMixOfExplicitAndDefaultPrefix() {
      Filter result =
          engine.parseFilter(
              "name eq \"test\" and eh.userId eq \"#123e4567-e89b-12d3-a456-426614174000\"",
              "t",
              null);
      assertEquals("t.name = :name1 AND eh.user_id = CAST(:eh_userId1 AS UUID)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
    }

    @Test
    @DisplayName("Explicit alias with string value")
    void shouldHandleExplicitAliasWithStringValue() {
      Filter result = engine.parseFilter("ea.status eq \"confirmed\"", "t", null);
      assertEquals("ea.status = :ea_status1", result.toClause());
      var map = result.context().indexedParams();
      assertEquals("confirmed", map.get("ea_status1"));
    }

    @Test
    @DisplayName("Explicit alias with present operator")
    void shouldHandleExplicitAliasWithPresentOperator() {
      Filter result = engine.parseFilter("eh.userId pr", "t", null);
      assertEquals("eh.user_id IS NOT NULL", result.toClause());
    }
  }

  @Nested
  @DisplayName("UUID Tests")
  class UuidTests {

    @Test
    @DisplayName("UUID equal operator with automatic casting")
    void shouldHandleUuidEqualOperator() {
      Filter result =
          engine.parseFilter("id eq \"#123e4567-e89b-12d3-a456-426614174000\"", "t", null);
      assertEquals("t.id = CAST(:id1 AS UUID)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(1, map.size());
      assertEquals("123e4567-e89b-12d3-a456-426614174000", map.get("id1").toString());
    }

    @Test
    @DisplayName("UUID not equal operator with automatic casting")
    void shouldHandleUuidNotEqualOperator() {
      Filter result =
          engine.parseFilter("userId ne \"#550e8400-e29b-41d4-a716-446655440000\"", "t", null);
      assertEquals("t.user_id != CAST(:userId1 AS UUID)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(1, map.size());
      assertEquals("550e8400-e29b-41d4-a716-446655440000", map.get("userId1").toString());
    }

    @Test
    @DisplayName("UUID in complex filter")
    void shouldHandleUuidInComplexFilter() {
      Filter result =
          engine.parseFilter(
              "id eq \"#123e4567-e89b-12d3-a456-426614174000\" and active eq true", "t", null);
      assertEquals("t.id = CAST(:id1 AS UUID) AND t.active = :active1", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
      assertEquals("123e4567-e89b-12d3-a456-426614174000", map.get("id1").toString());
      assertEquals(true, map.get("active1"));
    }

    @Test
    @DisplayName("Multiple UUIDs in filter")
    void shouldHandleMultipleUuids() {
      Filter result =
          engine.parseFilter(
              "id eq \"#123e4567-e89b-12d3-a456-426614174000\" or id eq \"#550e8400-e29b-41d4-a716-446655440000\"",
              "t",
              null);
      assertEquals("t.id = CAST(:id1 AS UUID) OR t.id = CAST(:id2 AS UUID)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
      assertEquals("123e4567-e89b-12d3-a456-426614174000", map.get("id1").toString());
      assertEquals("550e8400-e29b-41d4-a716-446655440000", map.get("id2").toString());
    }

    @Test
    @DisplayName("UUID with nested attribute path")
    void shouldHandleUuidWithNestedPath() {
      Filter result =
          engine.parseFilter("user.id eq \"#123e4567-e89b-12d3-a456-426614174000\"", "t", null);
      String clause = result.toClause();
      assertTrue(
          clause.contains("CAST(") && clause.contains("AS UUID)"),
          "Should contain UUID cast: " + clause);
    }

    @Test
    @DisplayName("Regular string vs UUID string")
    void shouldDifferentiateRegularStringFromUuid() {
      Filter regularString = engine.parseFilter("name eq \"john\"", "t", null);
      assertEquals("t.name = :name1", regularString.toClause());

      Filter uuidString =
          engine.parseFilter("id eq \"#123e4567-e89b-12d3-a456-426614174000\"", "t", null);
      assertEquals("t.id = CAST(:id1 AS UUID)", uuidString.toClause());
    }

    @Test
    @DisplayName("UUID with uppercase hex digits")
    void shouldHandleUppercaseUuid() {
      Filter result =
          engine.parseFilter("id eq \"#123E4567-E89B-12D3-A456-426614174000\"", "t", null);
      assertEquals("t.id = CAST(:id1 AS UUID)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals("123e4567-e89b-12d3-a456-426614174000", map.get("id1").toString());
    }

    @Test
    @DisplayName("UUID with mixed case hex digits")
    void shouldHandleMixedCaseUuid() {
      Filter result =
          engine.parseFilter("id eq \"#123e4567-E89b-12D3-a456-426614174000\"", "t", null);
      assertEquals("t.id = CAST(:id1 AS UUID)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals("123e4567-e89b-12d3-a456-426614174000", map.get("id1").toString());
    }
  }

  @Nested
  @DisplayName("JSON Tests")
  class JsonTests {

    @Test
    @DisplayName("JSON equal operator with automatic casting and containment")
    void shouldHandleJsonEqualOperator() {
      Filter result =
          engine.parseFilter("metadata eq \"${\\\"key\\\": \\\"value\\\"}\"", "t", null);
      assertEquals("t.metadata @> CAST(:metadata1 AS jsonb)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(1, map.size());
      assertEquals("{\"key\": \"value\"}", map.get("metadata1"));
    }

    @Test
    @DisplayName("JSON with nested structure")
    void shouldHandleNestedJsonStructure() {
      Filter result =
          engine.parseFilter(
              "metaQuery eq \"${\\\"request\\\": {\\\"parentId\\\": \\\"123efg\\\"}}\"", "t", null);
      assertEquals("t.meta_query @> CAST(:metaQuery1 AS jsonb)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals("{\"request\": {\"parentId\": \"123efg\"}}", map.get("metaQuery1"));
    }

    @Test
    @DisplayName("JSON in complex filter")
    void shouldHandleJsonInComplexFilter() {
      Filter result =
          engine.parseFilter(
              "metadata eq \"${\\\"status\\\": \\\"active\\\"}\" and name eq \"test\"", "t", null);
      assertEquals(
          "t.metadata @> CAST(:metadata1 AS jsonb) AND t.name = :name1", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
    }

    @Test
    @DisplayName("Multiple JSON filters")
    void shouldHandleMultipleJsonFilters() {
      Filter result =
          engine.parseFilter(
              "metadata eq \"${\\\"key1\\\": \\\"value1\\\"}\" and config eq \"${\\\"key2\\\": \\\"value2\\\"}\"",
              "t",
              null);
      assertEquals(
          "t.metadata @> CAST(:metadata1 AS jsonb) AND t.config @> CAST(:config1 AS jsonb)",
          result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
    }

    @Test
    @DisplayName("JSON with array value")
    void shouldHandleJsonWithArray() {
      Filter result =
          engine.parseFilter(
              "labels eq \"${\\\"tags\\\": [\\\"important\\\", \\\"urgent\\\"]}\"", "t", null);
      assertEquals("t.labels @> CAST(:labels1 AS jsonb)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals("{\"tags\": [\"important\", \"urgent\"]}", map.get("labels1"));
    }

    @Test
    @DisplayName("Regular string vs JSON string")
    void shouldDifferentiateRegularStringFromJson() {
      Filter regularString = engine.parseFilter("name eq \"test\"", "t", null);
      assertEquals("t.name = :name1", regularString.toClause());

      Filter jsonString =
          engine.parseFilter("metadata eq \"${\\\"key\\\": \\\"value\\\"}\"", "t", null);
      assertEquals("t.metadata @> CAST(:metadata1 AS jsonb)", jsonString.toClause());
    }

    @Test
    @DisplayName("JSON, UUID, and timestamp in same filter")
    void shouldHandleAllSpecialTypesTogether() {
      Filter result =
          engine.parseFilter(
              "id eq \"#123e4567-e89b-12d3-a456-426614174000\" and createdAt gt \"@2025-01-01T00:00:00Z\" and metadata eq \"${\\\"status\\\": \\\"active\\\"}\"",
              "t",
              null);
      assertEquals(
          "t.id = CAST(:id1 AS UUID) AND t.created_at > CAST(:createdAt1 AS timestamptz) AND t.metadata @> CAST(:metadata1 AS jsonb)",
          result.toClause());
      var map = result.context().indexedParams();
      assertEquals(3, map.size());
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("Timestamp equal operator with automatic casting")
    void shouldHandleTimestampEqualOperator() {
      Filter result =
          engine.parseFilter("createdAt eq \"@2025-11-12T22:07:34.995962737Z\"", "t", null);
      assertEquals("t.created_at = CAST(:createdAt1 AS timestamptz)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(1, map.size());
      assertEquals("2025-11-12T22:07:34.995962737Z", map.get("createdAt1"));
    }

    @Test
    @DisplayName("Timestamp not equal operator with automatic casting")
    void shouldHandleTimestampNotEqualOperator() {
      Filter result = engine.parseFilter("updatedAt ne \"@2025-11-15T10:30:00Z\"", "t", null);
      assertEquals("t.updated_at != CAST(:updatedAt1 AS timestamptz)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals(1, map.size());
      assertEquals("2025-11-15T10:30:00Z", map.get("updatedAt1"));
    }

    @Test
    @DisplayName("Timestamp greater than operator")
    void shouldHandleTimestampGreaterThan() {
      Filter result = engine.parseFilter("createdAt gt \"@2025-01-01T00:00:00Z\"", "t", null);
      assertEquals("t.created_at > CAST(:createdAt1 AS timestamptz)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals("2025-01-01T00:00:00Z", map.get("createdAt1"));
    }

    @Test
    @DisplayName("Timestamp less than operator")
    void shouldHandleTimestampLessThan() {
      Filter result = engine.parseFilter("expiresAt lt \"@2026-12-31T23:59:59.999Z\"", "t", null);
      assertEquals("t.expires_at < CAST(:expiresAt1 AS timestamptz)", result.toClause());
      var map = result.context().indexedParams();
      assertEquals("2026-12-31T23:59:59.999Z", map.get("expiresAt1"));
    }

    @Test
    @DisplayName("Timestamp in complex filter")
    void shouldHandleTimestampInComplexFilter() {
      Filter result =
          engine.parseFilter(
              "createdAt gt \"@2025-01-01T00:00:00Z\" and active eq true", "t", null);
      assertEquals(
          "t.created_at > CAST(:createdAt1 AS timestamptz) AND t.active = :active1",
          result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
      assertEquals("2025-01-01T00:00:00Z", map.get("createdAt1"));
      assertEquals(true, map.get("active1"));
    }

    @Test
    @DisplayName("Timestamp with varying precision")
    void shouldHandleTimestampWithVaryingPrecision() {
      Filter noMillis = engine.parseFilter("createdAt eq \"@2025-11-12T22:07:34Z\"", "t", null);
      assertEquals("t.created_at = CAST(:createdAt1 AS timestamptz)", noMillis.toClause());

      Filter withMillis =
          engine.parseFilter("updatedAt eq \"@2025-11-12T22:07:34.995Z\"", "t", null);
      assertEquals("t.updated_at = CAST(:updatedAt1 AS timestamptz)", withMillis.toClause());

      Filter withMicros =
          engine.parseFilter("modifiedAt eq \"@2025-11-12T22:07:34.995962Z\"", "t", null);
      assertEquals("t.modified_at = CAST(:modifiedAt1 AS timestamptz)", withMicros.toClause());

      Filter withNanos =
          engine.parseFilter("processedAt eq \"@2025-11-12T22:07:34.995962737Z\"", "t", null);
      assertEquals("t.processed_at = CAST(:processedAt1 AS timestamptz)", withNanos.toClause());
    }

    @Test
    @DisplayName("Multiple timestamps in filter")
    void shouldHandleMultipleTimestamps() {
      Filter result =
          engine.parseFilter(
              "createdAt gt \"@2025-01-01T00:00:00Z\" and createdAt lt \"@2025-12-31T23:59:59Z\"",
              "t",
              null);
      assertEquals(
          "t.created_at > CAST(:createdAt1 AS timestamptz) AND t.created_at < CAST(:createdAt2 AS timestamptz)",
          result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
      assertEquals("2025-01-01T00:00:00Z", map.get("createdAt1"));
      assertEquals("2025-12-31T23:59:59Z", map.get("createdAt2"));
    }

    @Test
    @DisplayName("Timestamp and UUID in same filter")
    void shouldHandleTimestampAndUuidTogether() {
      Filter result =
          engine.parseFilter(
              "id eq \"#123e4567-e89b-12d3-a456-426614174000\" and createdAt gt \"@2025-01-01T00:00:00Z\"",
              "t",
              null);
      assertEquals(
          "t.id = CAST(:id1 AS UUID) AND t.created_at > CAST(:createdAt1 AS timestamptz)",
          result.toClause());
      var map = result.context().indexedParams();
      assertEquals(2, map.size());
    }

    @Test
    @DisplayName("Regular string vs timestamp string")
    void shouldDifferentiateRegularStringFromTimestamp() {
      Filter regularString = engine.parseFilter("name eq \"2025-11-12\"", "t", null);
      assertEquals("t.name = :name1", regularString.toClause());

      Filter timestampString =
          engine.parseFilter("createdAt eq \"@2025-11-12T22:07:34Z\"", "t", null);
      assertEquals("t.created_at = CAST(:createdAt1 AS timestamptz)", timestampString.toClause());
    }
  }

  @Nested
  @DisplayName("Whole Input")
  class WholeInputTests {

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(
        strings = {
          "userName eq \"john\") or (active pr",
          "userName eq \"john\")",
          "userName eq \"john\" or",
          "userName eq \"john\" and ",
          "userName eq \"john\" active pr",
          "userName eq \"john\" extra",
          "userName pr pr",
          "userName eq \"john\"\"jane\"",
          "(userName eq \"john\") (active pr)",
          "userName eq \"john\" or active pr)"
        })
    @DisplayName("Input left after a complete filter")
    void shouldRejectInputLeftAfterACompleteFilter(String filter) {
      assertThrows(IllegalArgumentException.class, () -> engine.parseFilter(filter, "t", null));
    }

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(
        strings = {
          "userName eq \"john\";",
          "userName eq \"john\" -- comment",
          "userName eq 'john'",
          "userName eq \"john\"\tand active pr",
          "userName eq \"john\"\nor active pr",
          "userName eq \"john",
          "userName = \"john\"",
          "userName eq \"john\" && active pr",
          "user*Name pr",
          "userName eq \"john\" or active pr;"
        })
    @DisplayName("Characters outside the grammar")
    void shouldRejectCharactersOutsideTheGrammar(String filter) {
      assertThrows(IllegalArgumentException.class, () -> engine.parseFilter(filter, "t", null));
    }

    @Test
    @DisplayName("Error names the position of the first unexpected input")
    void shouldReportThePositionOfUnexpectedInput() {
      var ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> engine.parseFilter("userName eq \"john\") or (active pr", "t", null));
      assertTrue(ex.getMessage().contains("position 18"), ex.getMessage());
    }

    @Test
    @DisplayName("Error names the position of a character outside the grammar")
    void shouldReportThePositionOfAnUnknownCharacter() {
      var ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> engine.parseFilter("userName eq \"john\";", "t", null));
      assertTrue(ex.getMessage().contains("position 18"), ex.getMessage());
    }

    @ParameterizedTest(name = "is a syntax error [{0}]")
    @ValueSource(
        strings = {
          "userName eq",
          "userName eq \"john\") or (active pr",
          "userName eq \"john\";",
          "   "
        })
    @DisplayName("A filter that does not parse is a FilterSyntaxException")
    void shouldThrowFilterSyntaxExceptionForAFilterThatDoesNotParse(String filter) {
      assertThrows(FilterSyntaxException.class, () -> engine.parseFilter(filter, "t", null));
    }

    @Test
    @DisplayName("A null filter is a FilterSyntaxException")
    void shouldThrowFilterSyntaxExceptionForANullFilter() {
      assertThrows(FilterSyntaxException.class, () -> engine.parseFilter(null, "t", null));
    }

    @Test
    @DisplayName("Error position counts leading whitespace")
    void shouldReportThePositionInTheOriginalInput() {
      var ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> engine.parseFilter("  userName eq \"john\";", "t", null));
      assertTrue(ex.getMessage().contains("position 20"), ex.getMessage());
    }

    @Test
    @DisplayName("Null filter")
    void shouldRejectNullFilter() {
      var ex =
          assertThrows(IllegalArgumentException.class, () -> engine.parseFilter(null, "t", null));
      assertEquals("Filter must be specified", ex.getMessage());
    }

    @Test
    @DisplayName("Blank filter")
    void shouldRejectBlankFilter() {
      var ex =
          assertThrows(IllegalArgumentException.class, () -> engine.parseFilter("   ", "t", null));
      assertEquals("Filter must be specified", ex.getMessage());
    }

    @Test
    @DisplayName("Surrounding whitespace is ignored")
    void shouldIgnoreSurroundingWhitespace() {
      Filter result = engine.parseFilter("  userName eq \"john\" \n", "t", null);
      assertEquals("t.user_name = :userName1", result.toClause());
    }

    @Test
    @DisplayName("Syntax inside a string value stays data")
    void shouldKeepSyntaxInsideAStringAsData() {
      Filter result = engine.parseFilter("name eq \"a) or (b; -- 'x'\"", "t", null);
      assertEquals("t.name = :name1", result.toClause());
      assertEquals("a) or (b; -- 'x'", result.context().indexedParams().get("name1"));
    }

    @Test
    @DisplayName("and binds tighter than or, as in SQL")
    void shouldRenderMixedOperatorsWithSqlPrecedence() {
      Filter result = engine.parseFilter("a eq 1 or b eq 2 and c eq 3", "t", null);
      assertEquals("t.a = :a1 OR t.b = :b1 AND t.c = :c1", result.toClause());
    }
  }

  @Nested
  @DisplayName("Scope Filter")
  class ScopeFilterTests {

    private static final String OWNER = "ownerId eq \"#59b47990-39d1-46c7-ac88-0808abd49a94\"";

    @Test
    @DisplayName("A filter with or stays inside the scope")
    void shouldKeepAnOrFilterInsideTheScope() {
      var scoped = engine.scopeFilter(OWNER, "status eq \"open\" or status pr");
      assertEquals("(" + OWNER + ") and (status eq \"open\" or status pr)", scoped);

      Filter result = engine.parseFilter(scoped, "t", null);
      assertEquals(
          "(t.owner_id = CAST(:ownerId1 AS UUID)) AND (t.status = :status1 OR t.status IS NOT NULL)",
          result.toClause());
    }

    @Test
    @DisplayName("A filter naming the scope attribute cannot widen the scope")
    void shouldKeepAFilterOnTheScopeAttributeInsideTheScope() {
      var scoped =
          engine.scopeFilter(
              OWNER, "ownerId eq \"#00000000-0000-0000-0000-000000000002\" or status pr");

      Filter result = engine.parseFilter(scoped, "t", null);
      assertEquals(
          "(t.owner_id = CAST(:ownerId1 AS UUID))"
              + " AND (t.owner_id = CAST(:ownerId2 AS UUID) OR t.status IS NOT NULL)",
          result.toClause());
    }

    @Test
    @DisplayName("A scope with or stays grouped")
    void shouldKeepAScopeWithOrGrouped() {
      var scoped = engine.scopeFilter("a eq 1 or b eq 2", "c eq 3");

      Filter result = engine.parseFilter(scoped, "t", null);
      assertEquals("(t.a = :a1 OR t.b = :b1) AND (t.c = :c1)", result.toClause());
    }

    @Test
    @DisplayName("A negated filter stays inside the scope")
    void shouldKeepANegatedFilterInsideTheScope() {
      var scoped = engine.scopeFilter(OWNER, "not (status eq \"open\")");

      Filter result = engine.parseFilter(scoped, "t", null);
      assertEquals(
          "(t.owner_id = CAST(:ownerId1 AS UUID)) AND (NOT (t.status = :status1))",
          result.toClause());
    }

    @Test
    @DisplayName("Scopes nest")
    void shouldNestScopes() {
      var scoped = engine.scopeFilter(OWNER, engine.scopeFilter("active eq true", "age gt 21"));

      Filter result = engine.parseFilter(scoped, "t", null);
      assertEquals(
          "(t.owner_id = CAST(:ownerId1 AS UUID)) AND ((t.active = :active1) AND (t.age > :age1))",
          result.toClause());
    }

    @ParameterizedTest(name = "returns the scope alone for [{0}]")
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("No filter")
    void shouldReturnTheScopeAloneWhenThereIsNoFilter(String filter) {
      assertEquals(OWNER, engine.scopeFilter(OWNER, filter));
    }

    @Test
    @DisplayName("Null filter")
    void shouldReturnTheScopeAloneForANullFilter() {
      assertEquals(OWNER, engine.scopeFilter(OWNER, null));
    }

    @Test
    @DisplayName("Surrounding whitespace is dropped from both parts")
    void shouldDropSurroundingWhitespace() {
      assertEquals(
          "(" + OWNER + ") and (status pr)",
          engine.scopeFilter("  " + OWNER + " ", " status pr\n"));
      assertEquals(OWNER, engine.scopeFilter(" " + OWNER + "  ", null));
    }

    @ParameterizedTest(name = "rejects [{0}]")
    @ValueSource(
        strings = {
          "status eq \"open\") or (status pr",
          "status pr) or (status pr",
          "status pr) or ownerId pr or (status pr",
          "status eq \"open\" or",
          "status eq \"open\";",
          "status eq \"open",
          "(status pr"
        })
    @DisplayName("A filter that is not one complete filter")
    void shouldRejectAFilterThatIsNotOneCompleteFilter(String filter) {
      assertThrows(IllegalArgumentException.class, () -> engine.scopeFilter(OWNER, filter));
    }

    @ParameterizedTest(name = "rejects scope [{0}]")
    @ValueSource(strings = {"", "  ", "ownerId eq", "ownerId pr) or (ownerId pr"})
    @DisplayName("A scope that is missing or not one complete filter")
    void shouldRejectAScopeThatIsMissingOrIncomplete(String scope) {
      assertThrows(IllegalArgumentException.class, () -> engine.scopeFilter(scope, "status pr"));
    }

    @Test
    @DisplayName("A filter that does not parse is a FilterSyntaxException")
    void shouldThrowFilterSyntaxExceptionForAFilterThatDoesNotParse() {
      assertThrows(
          FilterSyntaxException.class,
          () -> engine.scopeFilter(OWNER, "status eq \"open\") or (status pr"));
    }

    @Test
    @DisplayName("A scope that does not parse is the caller's error, not a filter syntax error")
    void shouldBlameTheCallerForAScopeThatDoesNotParse() {
      var ex =
          assertThrows(
              IllegalArgumentException.class, () -> engine.scopeFilter("ownerId eq", "status pr"));
      assertEquals(IllegalArgumentException.class, ex.getClass());
      assertTrue(ex.getMessage().startsWith("Scope filter is not a valid filter"), ex.getMessage());
      assertInstanceOf(FilterSyntaxException.class, ex.getCause());
    }

    @Test
    @DisplayName("Null scope")
    void shouldRejectANullScope() {
      var ex =
          assertThrows(IllegalArgumentException.class, () -> engine.scopeFilter(null, "status pr"));
      assertEquals("Scope filter must be specified", ex.getMessage());
    }

    @Test
    @DisplayName("A missing scope is rejected even without a filter")
    void shouldRejectAMissingScopeEvenWithoutAFilter() {
      assertThrows(IllegalArgumentException.class, () -> engine.scopeFilter(" ", null));
    }

    @Test
    @DisplayName("No generated filter leaves the scope")
    void shouldNeverLetAGeneratedFilterLeaveTheScope() {
      var fragments =
          List.of(
              "status pr",
              "status eq \"open\"",
              "ownerId eq \"#00000000-0000-0000-0000-000000000002\"",
              "age gt 21",
              "id in [1, 2]",
              " or ",
              " and ",
              "not ",
              "(",
              ")",
              " ",
              "\"",
              "\\",
              ";",
              "'",
              "--",
              "[",
              "]",
              ",",
              "pr",
              "eq",
              "status",
              "1",
              "\t");
      var random = new Random(20261005L);
      var accepted = 0;
      var rejected = 0;

      for (var i = 0; i < 20_000; i++) {
        var filter = new StringBuilder();
        var length = 1 + random.nextInt(7);
        for (var j = 0; j < length; j++) {
          filter.append(fragments.get(random.nextInt(fragments.size())));
        }

        String scoped;
        try {
          scoped = engine.scopeFilter(OWNER, filter.toString());
        } catch (IllegalArgumentException ex) {
          rejected++;
          continue;
        }
        if (filter.toString().isBlank()) {
          assertEquals(OWNER, scoped);
          continue;
        }
        accepted++;

        var root = assertInstanceOf(AndFilter.class, engine.parseFilter(scoped, "t", null), scoped);
        var scope = assertInstanceOf(ParenFilter.class, root.left(), scoped);
        assertEquals("(t.owner_id = CAST(:ownerId1 AS UUID))", scope.toClause(), scoped);
        assertInstanceOf(ParenFilter.class, root.right(), scoped);
      }

      assertTrue(accepted > 500, "accepted " + accepted);
      assertTrue(rejected > 500, "rejected " + rejected);
    }
  }
}
