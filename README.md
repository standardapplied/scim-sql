# scim-sql

Two independent capabilities for **PostgreSQL** in one small library:

1. **SCIM filter → SQL** (`ai.singlr.scimsql`): parses [RFC 7644](https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2.2) filter strings and produces SQL WHERE clauses with named parameters — safe from injection by design. It parses SCIM filter expressions only; it does not validate full SQL.
2. **PostgreSQL query analysis** (`ai.singlr.postgresql`): parses complete SQL statements and reports structural facts — statement kind and count, relations, columns, functions, named parameters, and policy-relevant features — without executing SQL or resolving catalog objects. See [PostgreSQL Query Analysis](#postgresql-query-analysis).

The generated SQL uses PostgreSQL-specific syntax for typed values (`CAST(… AS UUID)`, `CAST(… AS timestamptz)`, `CAST(… AS jsonb)`, `@>` for JSON containment). Standard comparisons (`=`, `!=`, `LIKE`, `IN`, `IS NOT NULL`) are portable across databases. The `compareFilterBuilder` extension point allows overriding SQL generation for other databases.

## Quick Start

Add the dependency:

```xml
<dependency>
    <groupId>ai.singlr</groupId>
    <artifactId>scim-sql</artifactId>
    <version>1.0.0</version>
</dependency>
```

Parse a SCIM filter into SQL:

```java
var engine = new ScimEngine();
var filter = engine.parseFilter("userName eq \"john\"", "p", null);

filter.toClause();
// → "user_name = :p_userName1"

filter.context().indexedParams();
// → {p_userName1=john}
```

The `prefix` parameter namespaces all generated parameter keys, making it safe to combine multiple parsed filters in a single query.

## Supported Operators

| SCIM Operator | SQL Output | Example |
|--------------|------------|---------|
| `eq` | `=` | `name eq "John"` → `name = :name1` |
| `ne` | `!=` | `name ne "John"` → `name != :name1` |
| `gt` | `>` | `age gt 21` → `age > :age1` |
| `lt` | `<` | `age lt 65` → `age < :age1` |
| `ge` | `>=` | `age ge 18` → `age >= :age1` |
| `le` | `<=` | `age le 99` → `age <= :age1` |
| `co` | `LIKE '%…%'` | `name co "oh"` → `LOWER(name) LIKE '%' \|\| LOWER(:name1) \|\| '%'` |
| `sw` | `LIKE '…%'` | `name sw "J"` → `LOWER(name) LIKE LOWER(:name1) \|\| '%'` |
| `ew` | `LIKE '%…'` | `name ew "n"` → `LOWER(name) LIKE '%' \|\| LOWER(:name1)` |
| `pr` | `IS NOT NULL` | `name pr` → `name IS NOT NULL` |
| `in` | `IN (…)` | `status in ["active", "pending"]` → `status IN (:status1, :status2)` |

## Logical Operators

Combine filters with `and`, `or`, `not`, and parentheses:

```java
engine.parseFilter("name eq \"John\" and age gt 21", "p", null).toClause();
// → "name = :p_name1 AND age > :p_age1"

engine.parseFilter("not (active eq true)", "p", null).toClause();
// → "NOT (active = :p_active1)"

engine.parseFilter("(a eq 1 or b eq 2) and c eq 3", "p", null).toClause();
// → "(a = :p_a1 OR b = :p_b1) AND c = :p_c1"
```

`and` binds tighter than `or`, as in SQL. Use parentheses to group.

## Strict Parsing

`parseFilter` accepts one complete filter and nothing else. Input left over after a complete filter, and any character the grammar does not know, throws `FilterSyntaxException` (an `IllegalArgumentException`) naming the position of the first unexpected input. Catch that type to answer a client with a client error. Nothing is skipped or ignored, so a typo can never silently widen a query. Leading and trailing whitespace is ignored.

A missing filter (`null` or blank) or a missing prefix is a mistake in the calling code, not in a client's filter, and throws a plain `IllegalArgumentException`.

```java
engine.parseFilter("status eq \"open\") or (status pr", "p", null);
// → FilterSyntaxException: Failed to parse filter: Invalid filter syntax at position 16: ...

engine.parseFilter("status eq \"open\";", "p", null);
// → FilterSyntaxException: Failed to parse filter: Invalid filter syntax at position 16: ...
```

A filter is also rejected with `FilterSyntaxException` when it exceeds one of these limits, which keep a filter built for the purpose from exhausting the stack:

| Limit | Value |
|-------|-------|
| Levels of nested parentheses | 50 |
| Logical operators (`and`, `or`) | 500 |
| Segments in one attribute path | 10 |

A list of values (`in [...]`) is not limited. A whole number that does not fit in a `long` is rejected the same way.

## Scoping a Client Filter

When a query must always be restricted by a condition the server controls (an owner, a tenant, a visibility rule) and a client may add its own filter, combine the two with `scopeFilter`:

```java
var scope = "ownerId eq \"#550e8400-e29b-41d4-a716-446655440000\"";
var clientFilter = "status eq \"open\" or status pr";

var scoped = engine.scopeFilter(scope, clientFilter);
// → (ownerId eq "#550e8400-e29b-41d4-a716-446655440000") and (status eq "open" or status pr)

engine.parseFilter(scoped, "p", null).toClause();
// → "(p.owner_id = CAST(:ownerId1 AS UUID)) AND (p.status = :status1 OR p.status IS NOT NULL)"
```

Each part is checked to be one complete filter on its own and is then wrapped in parentheses, so the client's part cannot widen the scope, whatever operators or attributes it uses. A `null` or blank client filter returns the scope alone. A client filter that does not parse throws `FilterSyntaxException`. A missing or broken scope is a mistake in the calling code and throws a plain `IllegalArgumentException`.

The result is itself a filter expression, so it fits wherever a single filter is expected, and whatever `scopeFilter` returns, `parseFilter` accepts.

Do not join filter text by hand. `scope + " and " + clientFilter` renders as `owner AND status OR status`, and a client filter containing `or` then matches rows outside the scope.

## Typed Value Prefixes

Values can carry type hints that produce SQL `CAST` expressions. Prefix the value inside the quotes:

| Prefix | Type | SQL Cast | Example Value |
|--------|------|----------|---------------|
| `#` | UUID | `CAST(… AS UUID)` | `"#550e8400-e29b-41d4-a716-446655440000"` |
| `@` | Timestamp | `CAST(… AS timestamptz)` | `"@2026-01-15T10:30:00Z"` |
| `$` | JSON | `CAST(… AS jsonb)` | `"${"key":"value"}"` |

```java
engine.parseFilter("id eq \"#550e8400-e29b-41d4-a716-446655440000\"", "p", null).toClause();
// → "id = CAST(:p_id1 AS UUID)"

engine.parseFilter("metadata eq \"${\"role\":\"admin\"}\"", "p", null).toClause();
// → "metadata @> CAST(:p_metadata1 AS jsonb)"
```

JSON equality uses PostgreSQL's `@>` (contains) operator instead of `=`.

## Attribute Name Conversion

CamelCase attribute names are automatically converted to snake_case column names:

- `userName` → `user_name`
- `createdAtUtc` → `created_at_utc`

Dotted paths (e.g. `name.familyName`) are preserved as-is in the clause and flattened to underscores in parameter keys.

## Parameter Binding

`Context` collects all parameter bindings as the filter tree is evaluated:

```java
var filter = engine.parseFilter("name eq \"John\" and age gt 21", "p", null);
var clause = filter.toClause();
var params = filter.context().indexedParams();

// Use with JDBC named parameters, JOOQ, or any query builder:
// clause  = "name = :p_name1 AND age > :p_age1"
// params  = {p_name1=John, p_age1=21}
```

Use `context().isValid(Set.of("name", "age"))` to allowlist which attributes callers are permitted to filter on.

## Custom Filter Builders

Override SQL generation for specific comparisons by passing a `compareFilterBuilder` function:

```java
var filter = engine.parseFilter(
    "tags eq \"admin\"",
    "p",
    cf -> new ComparisonFilter.ListFilter(cf)  // wraps as ListFilter
);
```

This lets you intercept `ComparisonFilter` instances and return a subclass with custom `toClause()` or `paramKey()` behavior.

## PostgreSQL Query Analysis

`PostgresQueryAnalyzer.analyze(String)` parses a complete PostgreSQL statement (or script) through EOF and returns an immutable `QueryAnalysis`:

```java
var analysis = PostgresQueryAnalyzer.analyze(
    "SELECT u.id, count(*) FROM users u WHERE u.created_at >= :start_at GROUP BY u.id");

analysis.statementKind();   // SELECT
analysis.statementCount();  // 1
analysis.relations();       // [RelationReference[schema=null, name=users, alias=u, kind=PHYSICAL]]
analysis.columns();         // [u.id, u.created_at, u.id]
analysis.functions();       // [count at 1:17]
analysis.parameters();      // [start_at]
analysis.features();        // []
analysis.normalizedSql();   // deterministic form for hashing/audit
```

Key properties:

- **Named parameters** (`:start_at`, `:user_id`) are first-class expression values via a deliberate, documented grammar extension. Names are reported exactly; values are never bound or substituted.
- **Statement kinds**: `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `DDL`, `UTILITY`, `UNKNOWN`. Prohibited-but-valid statements analyze successfully so callers can raise precise policy errors.
- **Features** flag CTEs (plain/recursive/writable), subqueries, set operations, window functions, `SELECT INTO`, row locks, `LATERAL`, function/VALUES relations, star projections, and multiple statements — at any nesting depth.
- **Relations** distinguish physical tables, CTE references, and function relations, preserving aliases and schema qualification. Classification is syntactic; the analyzer does not resolve catalog objects, authorize access, execute SQL, or decide policy.
- **Safety**: input is bounded (length, tokens, nesting depth), errors carry only a stable reason plus line/column, and SQL text is never logged or echoed.

The grammar is the [ANTLR grammars-v4](https://github.com/antlr/grammars-v4) PostgreSQL grammar, vendored at a pinned commit — see [NOTICE.md](NOTICE.md) for provenance, licenses, and the exact local modifications.

## Building

Requires JDK 25+ and Maven.

```bash
mvn package
```

## Code Formatting

Uses [google-java-format](https://github.com/google/google-java-format) via Spotless (2-space indentation, no tabs).

```bash
mvn spotless:apply   # auto-format
mvn spotless:check   # verify (runs on build)
```

## License

[MIT](LICENSE)
