/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.Tool;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ANTLRToolListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the expected ANTLR warnings for the vendored PostgreSQL grammar. Upstream ships two benign
 * warning-146 lexer rules; any new warning or error after a grammar upgrade or local modification
 * must be reviewed and either fixed or pinned here deliberately.
 */
@DisplayName("Vendored grammar warnings are pinned")
class GrammarWarningsTest {

  private static final Path GRAMMAR_DIR =
      Path.of("src", "main", "antlr4", "com", "standardapplied", "postgresql", "parser");

  private static final List<String> EXPECTED_WARNINGS =
      List.of(
          "146:AfterEscapeStringConstantMode_NotContinued",
          "146:AfterEscapeStringConstantWithNewlineMode_NotContinued");

  @Test
  @DisplayName("lexer and parser grammars produce exactly the pinned warnings")
  void shouldMatchPinnedWarnings(@TempDir Path outputDir) throws Exception {
    assertTrue(Files.exists(GRAMMAR_DIR.resolve("PostgreSQLLexer.g4")));
    for (var grammar : List.of("PostgreSQLLexer.g4", "PostgreSQLParser.g4")) {
      Files.copy(GRAMMAR_DIR.resolve(grammar), outputDir.resolve(grammar));
    }

    var warnings = new ArrayList<String>();
    var errors = new ArrayList<String>();

    runTool(outputDir, warnings, errors, "PostgreSQLLexer.g4");
    runTool(outputDir, warnings, errors, "PostgreSQLParser.g4");

    assertEquals(List.of(), errors);
    assertEquals(EXPECTED_WARNINGS, warnings);
  }

  private static void runTool(
      Path outputDir, List<String> warnings, List<String> errors, String grammar) {
    var tool =
        new Tool(
            new String[] {
              "-o",
              outputDir.toString(),
              "-lib",
              outputDir.toString(),
              outputDir.resolve(grammar).toString()
            });
    tool.removeListeners();
    tool.addListener(
        new ANTLRToolListener() {
          @Override
          public void info(String msg) {}

          @Override
          public void error(ANTLRMessage msg) {
            errors.add(render(msg));
          }

          @Override
          public void warning(ANTLRMessage msg) {
            warnings.add(render(msg));
          }
        });
    tool.processGrammarsOnCommandLine();
  }

  private static String render(ANTLRMessage msg) {
    var args = msg.getArgs();
    return msg.getErrorType().code + (args.length > 0 ? ":" + args[0] : "");
  }
}
