/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.scimsql;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ScimEvaluator extends ScimBaseVisitor<Filter> {

  private static final Function<ComparisonFilter, ComparisonFilter> DEFAULT_COMPARE_FILTER_BUILDER =
      filter -> filter;

  private final String prefix;
  private final Function<ComparisonFilter, ComparisonFilter> compareFilterBuilder;
  private final Context context;

  public ScimEvaluator(
      String prefix, Function<ComparisonFilter, ComparisonFilter> compareFilterBuilder) {
    this.prefix = Objects.requireNonNull(prefix);
    this.compareFilterBuilder =
        compareFilterBuilder != null ? compareFilterBuilder : DEFAULT_COMPARE_FILTER_BUILDER;
    this.context = new Context();
  }

  @Override
  public Filter visitFilter(ScimParser.FilterContext ctx) {
    return visit(ctx.query());
  }

  @Override
  public Filter visitParenExp(ScimParser.ParenExpContext ctx) {
    Filter inner = visit(ctx.query());
    if (ctx.NOT() != null) {
      return new NotFilter(inner);
    }
    return new ParenFilter(inner);
  }

  @Override
  public Filter visitLogicalExp(ScimParser.LogicalExpContext ctx) {
    Filter left = visit(ctx.query(0));
    Filter right = visit(ctx.query(1));
    String operator = ctx.LOGICAL_OPERATOR().getText().toLowerCase();

    return switch (operator) {
      case "and" -> new AndFilter(left, right);
      case "or" -> new OrFilter(left, right);
      default -> throw new IllegalArgumentException("Unknown logical operator: " + operator);
    };
  }

  @Override
  public Filter visitPresentExp(ScimParser.PresentExpContext ctx) {
    Filter attributePath = visitAttrPath(ctx.attrPath());
    context.reference(attributePath);
    return new PresentFilter(attributePath);
  }

  @Override
  public Filter visitInExp(ScimParser.InExpContext ctx) {
    Filter attributePath = visitAttrPath(ctx.attrPath());
    Filter values = visitArrayValue(ctx.arrayValue());
    context.reference(attributePath);
    return new InFilter(attributePath, (ArrayValueFilter) values, context);
  }

  @Override
  public Filter visitCompareExp(ScimParser.CompareExpContext ctx) {
    Filter attributePath = visitAttrPath(ctx.attrPath());
    String operator = ctx.op.getText();
    Filter value = visit(ctx.value());

    var comparison =
        compareFilterBuilder.apply(new ComparisonFilter(attributePath, operator, value, context));
    context.reference(comparison.attribute());
    return comparison;
  }

  @Override
  public Filter visitAttrPath(ScimParser.AttrPathContext ctx) {
    String attrName = ctx.ATTRNAME().getText();
    if (ctx.subAttr() != null) {
      Filter subAttr = visitSubAttr(ctx.subAttr());
      return new AttributeFilter(attrName, subAttr, prefix, context);
    }
    return new AttributeFilter(attrName, null, prefix, context);
  }

  @Override
  public Filter visitSubAttr(ScimParser.SubAttrContext ctx) {
    String attrName = ctx.attrPath().ATTRNAME().getText();
    return new AttributeFilter(attrName, null, "", context);
  }

  @Override
  public Filter visitArrayValue(ScimParser.ArrayValueContext ctx) {
    List<Filter> values = new ArrayList<>();
    for (var valueCtx : ctx.value()) {
      values.add(visit(valueCtx));
    }

    return new ArrayValueFilter(values, context);
  }

  @Override
  public Filter visitDouble(ScimParser.DoubleContext ctx) {
    return new ValueFilter(Double.parseDouble(ctx.DOUBLE().getText()), context);
  }

  @Override
  public Filter visitLong(ScimParser.LongContext ctx) {
    try {
      return new ValueFilter(new BigDecimal(ctx.getText()).longValueExact(), context);
    } catch (ArithmeticException e) {
      throw new FilterSyntaxException(
          "Failed to parse filter: not a whole number in range: " + ctx.getText(), e);
    }
  }

  @Override
  public Filter visitBoolean(ScimParser.BooleanContext ctx) {
    return new ValueFilter(Boolean.parseBoolean(ctx.BOOLEAN().getText()), context);
  }

  @Override
  public Filter visitString(ScimParser.StringContext ctx) {
    String text = ctx.STRING().getText();
    // Remove surrounding quotes
    text = text.substring(1, text.length() - 1);
    // Handle escaped characters
    text =
        text.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\b", "\b")
            .replace("\\f", "\f")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    return new ValueFilter(text, context);
  }

  @Override
  public Filter visitJsonString(ScimParser.JsonStringContext ctx) {
    String text = ctx.JSON_STRING().getText();
    // Remove surrounding quotes and $ prefix: "${...}" -> "{...}"
    text = text.substring(2, text.length() - 1);
    // Handle escaped characters
    text =
        text.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\b", "\b")
            .replace("\\f", "\f")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    return new ValueFilter(text, ValueFilter.ValueType.JSON, context);
  }

  @Override
  public Filter visitUuidString(ScimParser.UuidStringContext ctx) {
    String text = ctx.UUID_STRING().getText();
    // Remove surrounding quotes and # prefix, normalize to lowercase
    text = text.substring(2, text.length() - 1).toLowerCase();
    return new ValueFilter(text, ValueFilter.ValueType.UUID, context);
  }

  @Override
  public Filter visitTimestampString(ScimParser.TimestampStringContext ctx) {
    String text = ctx.TIMESTAMP_STRING().getText();
    // Remove surrounding quotes and @ prefix: "@2025-11-12T22:07:34.995962737Z" ->
    // "2025-11-12T22:07:34.995962737Z"
    text = text.substring(2, text.length() - 1);
    return new ValueFilter(text, ValueFilter.ValueType.TIMESTAMP, context);
  }

  @Override
  public Filter visitNull(ScimParser.NullContext ctx) {
    return new ValueFilter("null", ValueFilter.ValueType.NULL, context);
  }
}
