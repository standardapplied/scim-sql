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

  private static final BaseErrorListener FAIL_ON_SYNTAX_ERROR =
      new BaseErrorListener() {
        @Override
        public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e) {
          throw new ParseCancellationException(
              "Invalid filter syntax at position " + charPositionInLine + ": " + msg);
        }
      };

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
   * @throws IllegalArgumentException if the expression is missing or is not a complete filter
   */
  public Filter parseFilter(
      String filterExpression,
      String prefix,
      Function<ComparisonFilter, ComparisonFilter> compareFilterBuilder) {
    var tree = parse(filterExpression);
    return new ScimEvaluator(prefix, compareFilterBuilder).visit(tree.query());
  }

  private static ScimParser.FilterContext parse(String filterExpression) {
    if (filterExpression == null || filterExpression.isBlank()) {
      throw new IllegalArgumentException("Filter must be specified");
    }
    try {
      var lexer = new ScimLexer(CharStreams.fromString(filterExpression.strip()));
      lexer.removeErrorListeners();
      lexer.addErrorListener(FAIL_ON_SYNTAX_ERROR);

      var parser = new ScimParser(new CommonTokenStream(lexer));
      parser.removeErrorListeners();
      parser.addErrorListener(FAIL_ON_SYNTAX_ERROR);

      return parser.filter();
    } catch (ParseCancellationException e) {
      throw new IllegalArgumentException("Failed to parse filter: " + e.getMessage(), e);
    }
  }
}
