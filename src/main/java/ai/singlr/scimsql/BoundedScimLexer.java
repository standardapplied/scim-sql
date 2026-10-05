/*
 * Copyright (c) 2026 Singular
 * SPDX-License-Identifier: MIT
 */

package ai.singlr.scimsql;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;

/**
 * A lexer that rejects a filter the parser and the evaluator cannot handle faithfully.
 *
 * <p>Parsing and evaluating a filter recurses once per level of parentheses and once per logical
 * operator. Without a bound, a filter built for the purpose exhausts the thread's stack. The limits
 * are far above any filter written for a real query. A list of values ({@code in [...]}) does not
 * recurse and is not limited.
 *
 * <p>An attribute path is {@code alias.attribute} at most. That is all the evaluator uses, and a
 * deeper path would otherwise be cut short without notice.
 */
final class BoundedScimLexer extends ScimLexer {

  static final int MAX_NESTING_DEPTH = 50;
  static final int MAX_LOGICAL_OPERATORS = 500;
  static final int MAX_PATH_SEGMENTS = 2;

  private int nestingDepth;
  private int logicalOperators;
  private int pathSegments = 1;

  BoundedScimLexer(CharStream input) {
    super(input);
  }

  @Override
  public Token nextToken() {
    var token = super.nextToken();
    switch (token.getType()) {
      case LPAREN -> {
        if (++nestingDepth > MAX_NESTING_DEPTH) {
          reject(token, "nested deeper than " + MAX_NESTING_DEPTH + " levels");
        }
      }
      case RPAREN -> nestingDepth = Math.max(0, nestingDepth - 1);
      case LOGICAL_OPERATOR -> {
        if (++logicalOperators > MAX_LOGICAL_OPERATORS) {
          reject(token, "more than " + MAX_LOGICAL_OPERATORS + " logical operators");
        }
      }
      case DOT -> {
        if (++pathSegments > MAX_PATH_SEGMENTS) {
          reject(token, "attribute path deeper than alias.attribute");
        }
      }
      case ATTRNAME -> {}
      default -> pathSegments = 1;
    }
    return token;
  }

  private void reject(Token token, String message) {
    getErrorListenerDispatch()
        .syntaxError(this, token, token.getLine(), token.getCharPositionInLine(), message, null);
  }
}
