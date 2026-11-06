# Third-Party Notices

## ANTLR grammars-v4 PostgreSQL grammar

The PostgreSQL grammar and its Java support sources under
`src/main/antlr4/com/standardapplied/postgresql/parser/` and
`src/main/java/com/standardapplied/postgresql/parser/` are vendored from the
[ANTLR grammars-v4](https://github.com/antlr/grammars-v4) project.

| | |
|---|---|
| Upstream repository | `https://github.com/antlr/grammars-v4` |
| Upstream path | `sql/postgresql` |
| Pinned commit | `76093c04af6a51f38a67d14f7e71ff0a9b4400da` (2026-06-20) |

Vendored files:

- `PostgreSQLLexer.g4` (from `sql/postgresql/PostgreSQLLexer.g4`)
- `PostgreSQLParser.g4` (from `sql/postgresql/PostgreSQLParser.g4`)
- `COPYRIGHT` (from `sql/postgresql/COPYRIGHT`, verbatim)
- `PostgreSQLLexerBase.java`, `PostgreSQLParserBase.java`,
  `LexerDispatchingErrorListener.java`, `ParserDispatchingErrorListener.java`
  (from `sql/postgresql/Java/`)

The test corpus under `src/test/resources/postgresql-corpus/` is vendored from
`sql/postgresql/examples/` at the same commit. Generated lexer/parser sources are
build output under `target/generated-sources/` and are never hand-edited.

### Local modifications

Every deviation from upstream is listed here and marked with a
`scim-sql local modification` comment at the change site:

1. `PostgreSQLParser.g4` — added `plsqlvariablename` as a labeled `c_expr`
   alternative (`# c_expr_namedparam`) so named parameters (`:start_at`,
   `:user_id`) are first-class expression values.
2. `PostgreSQLParser.g4` — removed `PLSQLVARIABLENAME` from the `identifier`
   rule so `:name` can never be an identifier, alias, or relation name.
3. `PostgreSQLParser.g4` — split `createfunctionstmt` into a header plus
   either the upstream AS-string option list or the PostgreSQL 14+ unquoted
   SQL body (`RETURN expr` / `BEGIN ATOMIC stmt; ... END`), which upstream
   does not parse.
4. `PostgreSQLParser.g4` — closed modern-PostgreSQL gaps against `gram.y`
   (PostgreSQL 14 through 18), each also marked in place:
   `json_aggregate_func` was defined upstream but never referenced (wired
   into `func_expr` / `func_expr_windowless`, and its `json_returning_clause`
   made optional as in `gram.y`); MERGE gained the PostgreSQL 17 `RETURNING`
   clause and `WHEN NOT MATCHED BY SOURCE | BY TARGET`; `group_clause`
   accepts the PostgreSQL 14 `ALL | DISTINCT` set quantifier;
   `common_table_expr` gained the PostgreSQL 14 `SEARCH` and `CYCLE`
   clauses; the PostgreSQL 16 `IS [NOT] JSON` predicate was added to
   `a_expr_is_not`; the PostgreSQL 17 `JSON_TABLE` table function was added
   and wired into `table_ref`; `UNIQUE`/`PRIMARY KEY` constraints accept the
   PostgreSQL 18 `WITHOUT OVERLAPS` marker; `xmltable_column_option_el`
   accepts `PATH` explicitly (the lexer keyword cannot match the generic
   identifier option); `json_format_clause` uses the real `FORMAT` token —
   upstream's `FORMAT_LA` literal is Bison lookahead-token residue that
   never occurs in SQL text, so `FORMAT JSON` could never parse.
5. `PostgreSQLLexer.g4` — fixed the upstream `BREADTH: 'BREATH'` typo that
   made `SEARCH BREADTH FIRST` unparseable.
6. `PostgreSQLLexerBase.java` — `nextToken()` splits a greedy
   `PLSQLVARIABLENAME` match into `COLON` plus the re-lexed name whenever the
   previous default-channel token can end an expression, so JSON `key:value`
   separators and array-slice bounds (`arr[lo:hi]`) written without
   whitespace stay operators instead of becoming named parameters.
7. `PostgreSQLLexerBase.java` — nested block comments are lexed iteratively
   with a depth counter instead of the upstream recursive rule, which was
   quadratic-time and could overflow the stack on adversarial nesting.
8. `*.java` support files — added a
   `package com.standardapplied.postgresql.parser;` declaration (upstream files have no
   package). The files are excluded from code formatting to keep them
   diffable against upstream.

Known consequences of the `:name` named-parameter extension:

- A colon directly after `[` binds to the parameter extension, so
  `arr[:name]` is a subscript by named parameter, not a slice with an
  omitted lower bound. Slices with an expression lower bound (`arr[lo:hi]`,
  `arr[1:2]`) are unaffected.
- The `:"identifier"` PL/SQL form is rejected.

### Expected grammar warnings

Upstream ships two benign ANTLR warning-146 lexer rules
(`AfterEscapeStringConstantMode_NotContinued` and
`AfterEscapeStringConstantWithNewlineMode_NotContinued`). These are pinned by
`GrammarWarningsTest`; the build fails on any new grammar warning or error.

### Upgrading the grammar

1. Pick a new upstream commit and re-copy the files listed above.
2. Re-apply the local modifications (search for `scim-sql local modification`).
3. Update the pinned commit here and in `UpstreamCorpusTest`, refresh the
   corpus files, and run `mvn clean verify`. Review any change in
   `GrammarWarningsTest` expectations deliberately.

### Licenses

The grammar files carry the MIT license of their authors (Tunnel Vision
Laboratories, LLC and Oleksii Kovalov) in their headers, preserved verbatim.
The upstream `COPYRIGHT` file is preserved verbatim next to the grammars and
reproduced here as required:

```
PostgreSQL Database Management System
(formerly known as Postgres, then as Postgres95)

Portions Copyright (c) 1996-2020, PostgreSQL Global Development Group

Portions Copyright (c) 1994, The Regents of the University of California

Permission to use, copy, modify, and distribute this software and its
documentation for any purpose, without fee, and without a written agreement
is hereby granted, provided that the above copyright notice and this
paragraph and the following two paragraphs appear in all copies.

IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING
LOST PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS
DOCUMENTATION, EVEN IF THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
ON AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATIONS TO
PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
```
