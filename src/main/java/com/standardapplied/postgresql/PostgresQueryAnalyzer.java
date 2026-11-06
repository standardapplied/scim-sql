/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import com.standardapplied.postgresql.parser.PostgreSQLLexer;
import com.standardapplied.postgresql.parser.PostgreSQLParser;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;

/**
 * Parses complete PostgreSQL statements and reports structural facts.
 *
 * <p>{@link #analyze(String)} parses the whole input through EOF — trailing garbage is a syntax
 * error — and returns an immutable {@link QueryAnalysis}. Prohibited but syntactically valid
 * statements (DDL, COPY, multiple statements, and so on) analyze successfully so callers can
 * produce precise policy errors; only malformed or unsafe input is rejected with a {@link
 * QueryAnalysisException}.
 *
 * <p>Input is bounded to {@value #MAX_LENGTH} characters, {@value #MAX_TOKENS} tokens, and {@value
 * #MAX_NESTING_DEPTH} levels of bracket nesting, which together bound parse time and memory on
 * hostile input. SQL text and literal content never appear in exception messages and are never
 * logged.
 *
 * <p>Three lexically valid PostgreSQL forms are rejected outright because they cannot be analyzed
 * faithfully: psql meta-commands (backslash commands), which are not SQL and could smuggle
 * executable commands past analysis, Unicode-escaped identifiers ({@code U&"..."}), whose effective
 * name differs from their spelling and could evade name-based policies, and identifiers longer than
 * {@value #MAX_IDENTIFIER_BYTES} UTF-8 bytes, which PostgreSQL silently truncates so the reported
 * name would differ from the one the server resolves.
 */
public final class PostgresQueryAnalyzer {

  /** Maximum accepted input length in characters. */
  public static final int MAX_LENGTH = 200_000;

  /** Maximum accepted number of lexed tokens. */
  public static final int MAX_TOKENS = 50_000;

  /** Maximum accepted parenthesis and bracket nesting depth. */
  public static final int MAX_NESTING_DEPTH = 128;

  /** Maximum accepted identifier length in UTF-8 bytes, matching a default NAMEDATALEN build. */
  public static final int MAX_IDENTIFIER_BYTES = 63;

  private static final Set<Integer> VERBATIM_TOKEN_TYPES =
      Set.of(
          PostgreSQLLexer.QuotedIdentifier,
          PostgreSQLLexer.StringConstant,
          PostgreSQLLexer.UnicodeEscapeStringConstant,
          PostgreSQLLexer.EscapeStringConstant,
          PostgreSQLLexer.BinaryStringConstant,
          PostgreSQLLexer.HexadecimalStringConstant,
          PostgreSQLLexer.BeginDollarStringConstant,
          PostgreSQLLexer.DollarText,
          PostgreSQLLexer.EndDollarStringConstant,
          PostgreSQLLexer.PLSQLVARIABLENAME,
          PostgreSQLLexer.PLSQLIDENTIFIER,
          PostgreSQLLexer.PARAM);

  private PostgresQueryAnalyzer() {}

  /**
   * Analyzes a complete PostgreSQL SQL string.
   *
   * @param sql one or more complete SQL statements
   * @return the structural analysis
   * @throws QueryAnalysisException on null, blank, or oversized input, unrecognized tokens, syntax
   *     errors, excessive nesting, or when no statement is present
   */
  public static QueryAnalysis analyze(String sql) {
    if (sql == null || sql.isBlank()) {
      throw new QueryAnalysisException("sql must not be null or blank", -1, -1);
    }
    if (sql.length() > MAX_LENGTH) {
      throw new QueryAnalysisException("sql exceeds " + MAX_LENGTH + " characters", -1, -1);
    }
    try {
      var tokens = lex(sql);
      var root = parse(tokens);
      return new AnalysisCollector().collect(root, normalize(tokens));
    } catch (StackOverflowError e) {
      throw new QueryAnalysisException("sql nesting exceeds parser capacity", -1, -1);
    }
  }

  private static CommonTokenStream lex(String sql) {
    var lexer = new PostgreSQLLexer(CharStreams.fromString(sql));
    lexer.removeErrorListeners();
    lexer.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            throw new QueryAnalysisException("unrecognized token", line, charPositionInLine);
          }
        });
    var tokens = new CommonTokenStream(lexer);
    tokens.fill();
    checkBounds(tokens);
    return tokens;
  }

  private static void checkBounds(CommonTokenStream tokens) {
    if (tokens.size() > MAX_TOKENS) {
      throw new QueryAnalysisException("sql exceeds " + MAX_TOKENS + " tokens", -1, -1);
    }
    int depth = 0;
    for (Token token : tokens.getTokens()) {
      int type = token.getType();
      if (type == PostgreSQLLexer.UnicodeQuotedIdentifier) {
        throw new QueryAnalysisException(
            "unicode escaped identifiers are not supported",
            token.getLine(),
            token.getCharPositionInLine());
      }
      checkIdentifierLength(token, type);
      if (type == PostgreSQLLexer.OPEN_PAREN || type == PostgreSQLLexer.OPEN_BRACKET) {
        depth++;
        if (depth > MAX_NESTING_DEPTH) {
          throw new QueryAnalysisException(
              "sql exceeds nesting depth of " + MAX_NESTING_DEPTH,
              token.getLine(),
              token.getCharPositionInLine());
        }
      } else if (type == PostgreSQLLexer.CLOSE_PAREN || type == PostgreSQLLexer.CLOSE_BRACKET) {
        depth = Math.max(0, depth - 1);
      }
    }
  }

  private static void checkIdentifierLength(Token token, int type) {
    if (type != PostgreSQLLexer.Identifier && type != PostgreSQLLexer.QuotedIdentifier) {
      return;
    }
    String identifier = token.getText();
    if (type == PostgreSQLLexer.QuotedIdentifier) {
      identifier = identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"");
    }
    if (identifier.getBytes(StandardCharsets.UTF_8).length > MAX_IDENTIFIER_BYTES) {
      throw new QueryAnalysisException(
          "identifiers longer than " + MAX_IDENTIFIER_BYTES + " bytes are not supported",
          token.getLine(),
          token.getCharPositionInLine());
    }
  }

  private static PostgreSQLParser.RootContext parse(CommonTokenStream tokens) {
    var parser = new PostgreSQLParser(tokens);
    parser.removeErrorListeners();
    parser.setErrorHandler(new BailErrorStrategy());
    parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
    try {
      return parser.root();
    } catch (ParseCancellationException sllFailure) {
      tokens.seek(0);
      parser.reset();
      parser.getInterpreter().setPredictionMode(PredictionMode.LL);
      try {
        return parser.root();
      } catch (ParseCancellationException llFailure) {
        throw syntaxError(llFailure);
      }
    }
  }

  private static QueryAnalysisException syntaxError(ParseCancellationException failure) {
    if (failure.getCause() instanceof RecognitionException recognition
        && recognition.getOffendingToken() != null) {
      var token = recognition.getOffendingToken();
      return new QueryAnalysisException(
          "invalid sql syntax", token.getLine(), token.getCharPositionInLine());
    }
    return new QueryAnalysisException("invalid sql syntax", -1, -1);
  }

  private static String normalize(CommonTokenStream tokens) {
    var normalized = new StringBuilder();
    int previousType = Token.INVALID_TYPE;
    for (Token token : tokens.getTokens()) {
      int type = token.getType();
      if (type == Token.EOF || token.getChannel() != Token.DEFAULT_CHANNEL) {
        continue;
      }
      if (!normalized.isEmpty() && !insideDollarString(previousType, type)) {
        normalized.append(' ');
      }
      var text = token.getText();
      normalized.append(
          VERBATIM_TOKEN_TYPES.contains(type) ? text : AnalysisCollector.asciiLowercase(text));
      previousType = type;
    }
    return normalized.toString();
  }

  private static boolean insideDollarString(int previousType, int type) {
    return (previousType == PostgreSQLLexer.BeginDollarStringConstant
            || previousType == PostgreSQLLexer.DollarText)
        && (type == PostgreSQLLexer.DollarText || type == PostgreSQLLexer.EndDollarStringConstant);
  }
}
