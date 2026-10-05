/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.scimsql;

import java.util.function.Function;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;

public class ScimEngine {

  /**
   * Parses a SCIM filter expression into a {@link Filter} that renders a SQL clause with named
   * parameters.
   *
   * <p>The whole expression must be a filter. Anything left over after a complete filter, and any
   * character the grammar does not know, is rejected rather than ignored. Leading and trailing
   * whitespace is ignored.
   *
   * @param filterExpression the SCIM filter expression
   * @param prefix the table alias applied to attributes that do not carry their own
   * @param compareFilterBuilder customises how comparisons render, or {@code null} for the default
   * @return the parsed filter
   * @throws FilterSyntaxException if the expression is missing or is not a complete filter
   */
  public Filter parseFilter(
      String filterExpression,
      String prefix,
      Function<ComparisonFilter, ComparisonFilter> compareFilterBuilder) {
    var tree = parse(filterExpression);
    return new ScimEvaluator(prefix, compareFilterBuilder).visit(tree.query());
  }

  /**
   * Restricts a filter to a scope that must always hold and returns the combined filter expression.
   *
   * <p>Use this to combine a condition the server enforces (an owner, a tenant, a visibility rule)
   * with a filter supplied by a client. Each part is checked to be one complete filter on its own
   * and is then wrapped in parentheses, so the client's part can never widen the scope, whatever
   * operators or attributes it uses. Do not join filter text by hand.
   *
   * @param scope the filter that must always hold
   * @param filter the filter to apply within the scope; when {@code null} or blank the scope is
   *     returned alone
   * @return a filter expression equivalent to {@code (scope) and (filter)}
   * @throws FilterSyntaxException if the filter is not a complete filter
   * @throws IllegalArgumentException if the scope is missing or is not a complete filter; a broken
   *     scope is a mistake in the calling code, never in the client's filter
   */
  public String scopeFilter(String scope, String filter) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException("Scope filter must be specified");
    }
    try {
      parse(scope);
    } catch (FilterSyntaxException e) {
      throw new IllegalArgumentException(
          "Scope filter is not a valid filter: " + e.getMessage(), e);
    }
    if (filter == null || filter.isBlank()) {
      return scope.strip();
    }
    parse(filter);
    return "(%s) and (%s)".formatted(scope.strip(), filter.strip());
  }

  private static ScimParser.FilterContext parse(String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      throw new FilterSyntaxException("Filter must be specified");
    }
    var leadingWhitespace = filterExpression.length() - filterExpression.stripLeading().length();
    var failOnSyntaxError = failOnSyntaxError(leadingWhitespace);
    try {
      var lexer = new ScimLexer(CharStreams.fromString(filterExpression.strip()));
      lexer.removeErrorListeners();
      lexer.addErrorListener(failOnSyntaxError);

      var parser = new ScimParser(new CommonTokenStream(lexer));
      parser.removeErrorListeners();
      parser.addErrorListener(failOnSyntaxError);

      return parser.filter();
    } catch (ParseCancellationException e) {
      throw new FilterSyntaxException("Failed to parse filter: " + e.getMessage(), e);
    }
  }

  private static BaseErrorListener failOnSyntaxError(int positionOffset) {
    return new BaseErrorListener() {
      @Override
      public void syntaxError(
          Recognizer<?, ?> recognizer,
          Object offendingSymbol,
          int line,
          int charPositionInLine,
          String msg,
          RecognitionException e) {
        throw new ParseCancellationException(
            "Invalid filter syntax at position "
                + (positionOffset + charPositionInLine)
                + ": "
                + msg);
      }
    };
  }
}
