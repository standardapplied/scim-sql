/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.scimsql;

import java.util.function.Function;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

public class ScimEngine {

  private static final String NO_PREFIX = "";

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
   * @throws FilterSyntaxException if the expression is not one complete filter, holds a whole
   *     number that does not fit, names an attribute path deeper than {@code alias.attribute}, or
   *     exceeds a limit: 50 levels of parentheses or 500 logical operators
   * @throws IllegalArgumentException if the expression or the prefix is missing; that is a mistake
   *     in the calling code, never in a client's filter
   */
  public Filter parseFilter(
      String filterExpression,
      String prefix,
      Function<ComparisonFilter, ComparisonFilter> compareFilterBuilder) {
    if (prefix == null) {
      throw new IllegalArgumentException("Prefix must be specified");
    }
    requireSpecified(filterExpression, "Filter");
    return parse(filterExpression, prefix, compareFilterBuilder);
  }

  /**
   * Restricts a filter to a scope that must always hold and returns the combined filter expression.
   *
   * <p>Use this to combine a condition the server enforces (an owner, a tenant, a visibility rule)
   * with a filter supplied by a client. Each part is checked to be one complete filter on its own
   * and is then wrapped in parentheses, so the client's part can never widen the scope, whatever
   * operators or attributes it uses. Do not join filter text by hand.
   *
   * <p>The result is a filter expression, so it fits wherever a single filter is expected. Pass it
   * to {@link #parseFilter} unchanged.
   *
   * @param scope the filter that must always hold
   * @param filter the filter to apply within the scope; when {@code null} or blank the scope is
   *     returned alone
   * @return a filter expression equivalent to {@code (scope) and (filter)}
   * @throws FilterSyntaxException if the filter is not one complete filter that {@link
   *     #parseFilter} accepts
   * @throws IllegalArgumentException if the scope is missing or is not a complete filter; a broken
   *     scope is a mistake in the calling code, never in the client's filter
   */
  public String scopeFilter(String scope, String filter) {
    requireSpecified(scope, "Scope filter");
    try {
      parse(scope, NO_PREFIX, null);
    } catch (FilterSyntaxException e) {
      throw new IllegalArgumentException(
          "Scope filter is not a valid filter: " + e.getMessage(), e);
    }
    if (filter == null || filter.isBlank()) {
      return scope.strip();
    }
    parse(filter, NO_PREFIX, null);
    var scoped = "(%s) and (%s)".formatted(scope.strip(), filter.strip());
    try {
      parse(scoped, NO_PREFIX, null);
    } catch (FilterSyntaxException e) {
      throw new FilterSyntaxException(
          "Failed to parse filter: exceeds a nesting or operator limit once scoped", e);
    }
    return scoped;
  }

  private static void requireSpecified(String filterExpression, String name) {
    if (filterExpression == null || filterExpression.isBlank()) {
      throw new IllegalArgumentException(name + " must be specified");
    }
  }

  private static Filter parse(
      String filterExpression,
      String prefix,
      Function<ComparisonFilter, ComparisonFilter> compareFilterBuilder) {
    var text = filterExpression.strip();
    var failOnSyntaxError = failOnSyntaxError(text, filterExpression.indexOf(text));
    try {
      var lexer = new BoundedScimLexer(CharStreams.fromString(text));
      lexer.removeErrorListeners();
      lexer.addErrorListener(failOnSyntaxError);

      var parser = new ScimParser(new CommonTokenStream(lexer));
      parser.removeErrorListeners();
      parser.addErrorListener(failOnSyntaxError);

      return new ScimEvaluator(prefix, compareFilterBuilder).visit(parser.filter());
    } catch (ParseCancellationException e) {
      throw new FilterSyntaxException("Failed to parse filter: " + e.getMessage(), e);
    }
  }

  /**
   * ANTLR reports a column that restarts after every line break and counts code points. The
   * listener reports the index into the expression the caller passed instead.
   */
  private static BaseErrorListener failOnSyntaxError(String text, int offset) {
    return new BaseErrorListener() {
      @Override
      public void syntaxError(
          Recognizer<?, ?> recognizer,
          Object offendingSymbol,
          int line,
          int charPositionInLine,
          String msg,
          RecognitionException e) {
        var codePointIndex =
            e instanceof LexerNoViableAltException lexerError
                ? lexerError.getStartIndex()
                : ((Token) offendingSymbol).getStartIndex();
        var position = offset + text.offsetByCodePoints(0, codePointIndex);
        throw new ParseCancellationException(
            "Invalid filter syntax at position " + position + ": " + msg);
      }
    };
  }
}
