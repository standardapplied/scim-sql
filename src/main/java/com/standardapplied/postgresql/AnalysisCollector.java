/*
 * Copyright (c) 2026 Standard Applied Intelligence Labs
 * SPDX-License-Identifier: MIT
 */

package com.standardapplied.postgresql;

import com.standardapplied.postgresql.parser.PostgreSQLParser;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Alias_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Any_nameContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.ColumnrefContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.CommentstmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Common_table_exprContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.DeletestmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.DropstmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.For_locking_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Func_alias_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Func_applicationContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Func_expr_common_subexprContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Func_expr_windowlessContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Func_nameContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Func_tableContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Index_elemContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Insert_column_itemContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Insert_targetContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.InsertstmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Into_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Join_qualContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Json_aggregate_funcContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Json_tableContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Locked_rels_listContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.MergestmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Object_type_any_nameContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Over_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.PlsqlvariablenameContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Qualified_nameContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Qualified_name_listContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Relation_exprContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Relation_expr_opt_aliasContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.RootContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.SeclabelstmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Select_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Select_no_parensContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Select_with_parensContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.SelectstmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Set_targetContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Simple_select_intersectContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Simple_select_pramaryContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.StmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Table_refContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Tablesample_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Target_starContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.UpdatestmtContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Values_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.Window_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.With_clauseContext;
import com.standardapplied.postgresql.parser.PostgreSQLParser.XmltableContext;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

final class AnalysisCollector {

  private static final Set<String> DDL_RULES =
      Set.of(
          "definestmt",
          "indexstmt",
          "viewstmt",
          "rulestmt",
          "renamestmt",
          "removeaggrstmt",
          "removefuncstmt",
          "removeoperstmt",
          "commentstmt",
          "seclabelstmt",
          "grantstmt",
          "revokestmt",
          "grantrolestmt",
          "revokerolestmt",
          "importforeignschemastmt",
          "reassignownedstmt");

  private static final Set<String> RELATION_OBJECT_TYPES =
      Set.of("table", "view", "materializedview", "foreigntable");

  private final List<RelationReference> relations = new ArrayList<>();
  private final List<ColumnReference> columns = new ArrayList<>();
  private final List<FunctionReference> functions = new ArrayList<>();
  private final Set<String> parameters = new LinkedHashSet<>();
  private final EnumSet<QueryFeature> features = EnumSet.noneOf(QueryFeature.class);

  QueryAnalysis collect(RootContext root, String normalizedSql) {
    List<StmtContext> statements = root.stmtblock().stmtmulti().stmt();
    if (statements.isEmpty()) {
      throw new QueryAnalysisException("no sql statement found", -1, -1);
    }
    if (statements.size() > 1) {
      features.add(QueryFeature.MULTIPLE_STATEMENTS);
    }
    for (StmtContext statement : statements) {
      scan(statement, Set.of());
    }
    return new QueryAnalysis(
        classify(statements.getFirst()),
        statements.size(),
        relations,
        columns,
        functions,
        parameters,
        features,
        normalizedSql);
  }

  private static StatementKind classify(StmtContext statement) {
    if (!(statement.getChild(0) instanceof ParserRuleContext child)) {
      return StatementKind.UNKNOWN;
    }
    String rule = PostgreSQLParser.ruleNames[child.getRuleIndex()];
    return switch (rule) {
      case "selectstmt" -> StatementKind.SELECT;
      case "insertstmt" -> StatementKind.INSERT;
      case "updatestmt" -> StatementKind.UPDATE;
      case "deletestmt" -> StatementKind.DELETE;
      case "mergestmt" -> StatementKind.MERGE;
      default ->
          rule.startsWith("create")
                  || rule.startsWith("alter")
                  || rule.startsWith("drop")
                  || DDL_RULES.contains(rule)
              ? StatementKind.DDL
              : StatementKind.UTILITY;
    };
  }

  private void scan(ParseTree node, Set<String> cteScope) {
    if (node instanceof TerminalNode) {
      return;
    }
    With_clauseContext withClause = ownedWithClause(node);
    if (withClause != null) {
      scanWithOwner((ParserRuleContext) node, withClause, cteScope);
      return;
    }
    inspect(node, cteScope);
    for (int i = 0; i < node.getChildCount(); i++) {
      scan(node.getChild(i), cteScope);
    }
  }

  private static With_clauseContext ownedWithClause(ParseTree node) {
    return switch (node) {
      case Select_no_parensContext c -> c.with_clause();
      case InsertstmtContext c -> c.with_clause_() != null ? c.with_clause_().with_clause() : null;
      case UpdatestmtContext c -> c.with_clause_() != null ? c.with_clause_().with_clause() : null;
      case DeletestmtContext c -> c.with_clause_() != null ? c.with_clause_().with_clause() : null;
      case MergestmtContext c -> c.with_clause_() != null ? c.with_clause_().with_clause() : null;
      default -> null;
    };
  }

  private void scanWithOwner(
      ParserRuleContext owner, With_clauseContext withClause, Set<String> outerScope) {
    features.add(QueryFeature.CTE);
    boolean recursive = withClause.RECURSIVE() != null;
    if (recursive) {
      features.add(QueryFeature.RECURSIVE_CTE);
    }
    List<Common_table_exprContext> ctes = withClause.cte_list().common_table_expr();
    List<String> names = ctes.stream().map(cte -> identifierText(cte.name().colid())).toList();
    for (int i = 0; i < ctes.size(); i++) {
      Common_table_exprContext cte = ctes.get(i);
      if (cte.preparablestmt().selectstmt() == null) {
        features.add(QueryFeature.WRITABLE_CTE);
      }
      List<String> visibleNames = recursive ? names : names.subList(0, i);
      Set<String> bodyScope = union(outerScope, visibleNames);
      scan(cte.preparablestmt(), bodyScope);
    }
    Set<String> fullScope = union(outerScope, names);
    ParseTree skip = withClause;
    while (skip.getParent() != owner) {
      skip = (ParseTree) skip.getParent();
    }
    for (int i = 0; i < owner.getChildCount(); i++) {
      ParseTree child = owner.getChild(i);
      if (child != skip) {
        scan(child, fullScope);
      }
    }
  }

  private static Set<String> union(Set<String> scope, List<String> names) {
    if (names.isEmpty()) {
      return scope;
    }
    Set<String> merged = new HashSet<>(scope);
    merged.addAll(names);
    return merged;
  }

  private void inspect(ParseTree node, Set<String> cteScope) {
    switch (node) {
      case Qualified_nameContext relation -> addRelation(relation, cteScope);
      case DropstmtContext drop -> addDropTargets(drop);
      case CommentstmtContext comment -> addCommentTargets(comment);
      case SeclabelstmtContext label -> addSecurityLabelTargets(label);
      case ColumnrefContext column -> addColumn(column);
      case Func_applicationContext call -> addFunction(call);
      case Func_expr_common_subexprContext special ->
          functions.add(
              new FunctionReference(
                  null,
                  asciiLowercase(special.getStart().getText()),
                  special.getStart().getLine(),
                  special.getStart().getCharPositionInLine()));
      case PlsqlvariablenameContext parameter -> parameters.add(parameter.getText().substring(1));
      case Json_aggregate_funcContext aggregate ->
          functions.add(
              new FunctionReference(
                  null,
                  asciiLowercase(aggregate.getStart().getText()),
                  aggregate.getStart().getLine(),
                  aggregate.getStart().getCharPositionInLine()));
      case Func_tableContext functionRelation -> addFunctionRelation(functionRelation);
      case XmltableContext xmlTable -> addTableFunctionRelation(xmlTable);
      case Json_tableContext jsonTable -> addTableFunctionRelation(jsonTable);
      case Target_starContext star -> {
        features.add(QueryFeature.STAR_PROJECTION);
        columns.add(new ColumnReference(null, "*"));
      }
      case Insert_column_itemContext item ->
          columns.add(new ColumnReference(null, identifierText(item.colid())));
      case Set_targetContext target ->
          columns.add(new ColumnReference(null, identifierText(target.colid())));
      case Index_elemContext item when item.colid() != null ->
          columns.add(new ColumnReference(null, identifierText(item.colid())));
      case Join_qualContext join when join.USING() != null ->
          join.name_list()
              .name()
              .forEach(
                  name -> columns.add(new ColumnReference(null, identifierText(name.colid()))));
      case Select_with_parensContext subquery -> {
        var parent = subquery.getParent();
        if (!(parent instanceof SelectstmtContext
            || parent instanceof Select_with_parensContext
            || parent instanceof Simple_select_pramaryContext)) {
          features.add(QueryFeature.SUBQUERY);
        }
      }
      case Select_clauseContext setOp -> {
        if (setOp.simple_select_intersect().size() > 1) {
          features.add(QueryFeature.SET_OPERATION);
        }
      }
      case Simple_select_intersectContext intersect -> {
        if (intersect.simple_select_pramary().size() > 1) {
          features.add(QueryFeature.SET_OPERATION);
        }
      }
      case Into_clauseContext into -> features.add(QueryFeature.SELECT_INTO);
      case For_locking_clauseContext locking -> {
        if (locking.for_locking_items() != null) {
          features.add(QueryFeature.ROW_LOCK);
        }
      }
      case Table_refContext tableRef -> {
        if (tableRef.LATERAL_P() != null) {
          features.add(QueryFeature.LATERAL);
        }
      }
      case Values_clauseContext values -> {
        if (hasTableRefAncestor(values)) {
          features.add(QueryFeature.VALUES_RELATION);
        }
      }
      case Over_clauseContext over -> features.add(QueryFeature.WINDOW);
      case Window_clauseContext window -> features.add(QueryFeature.WINDOW);
      default -> {}
    }
  }

  private void addRelation(Qualified_nameContext relation, Set<String> cteScope) {
    if (relation.getParent() instanceof Qualified_name_listContext list
        && list.getParent() instanceof Locked_rels_listContext) {
      return;
    }
    List<String> parts = nameParts(relation);
    String name = parts.removeLast();
    String schema = parts.isEmpty() ? null : String.join(".", parts);
    RelationReference.Kind kind =
        schema == null && isCteSource(relation) && cteScope.contains(name)
            ? RelationReference.Kind.CTE
            : RelationReference.Kind.PHYSICAL;
    relations.add(new RelationReference(schema, name, aliasFor(relation), kind));
  }

  private void addDropTargets(DropstmtContext drop) {
    if (drop.object_type_name_on_any_name() != null && drop.any_name() != null) {
      addAnyNameRelation(drop.any_name());
      return;
    }
    if (drop.any_name_list_() == null || !isRelationTarget(drop.object_type_any_name())) {
      return;
    }
    for (var anyName : drop.any_name_list_().any_name()) {
      addAnyNameRelation(anyName);
    }
  }

  private void addCommentTargets(CommentstmtContext comment) {
    if (comment.any_name() == null) {
      return;
    }
    if (comment.COLUMN() != null) {
      addColumnTarget(comment.any_name());
    } else if (isRelationTarget(comment.object_type_any_name())
        || comment.object_type_name_on_any_name() != null
        || (comment.CONSTRAINT() != null && comment.DOMAIN_P() == null)) {
      addAnyNameRelation(comment.any_name());
    }
  }

  private void addSecurityLabelTargets(SeclabelstmtContext label) {
    if (label.any_name() == null) {
      return;
    }
    if (label.COLUMN() != null) {
      addColumnTarget(label.any_name());
    } else if (isRelationTarget(label.object_type_any_name())) {
      addAnyNameRelation(label.any_name());
    }
  }

  private static boolean isRelationTarget(Object_type_any_nameContext objectType) {
    return objectType != null
        && RELATION_OBJECT_TYPES.contains(asciiLowercase(objectType.getText()));
  }

  private void addColumnTarget(Any_nameContext anyName) {
    List<String> parts = anyNameParts(anyName);
    String column = parts.removeLast();
    if (parts.isEmpty()) {
      columns.add(new ColumnReference(null, column));
      return;
    }
    columns.add(new ColumnReference(String.join(".", parts), column));
    addPhysicalRelation(parts);
  }

  private void addAnyNameRelation(Any_nameContext anyName) {
    addPhysicalRelation(anyNameParts(anyName));
  }

  private void addPhysicalRelation(List<String> parts) {
    String name = parts.removeLast();
    relations.add(
        new RelationReference(
            parts.isEmpty() ? null : String.join(".", parts),
            name,
            null,
            RelationReference.Kind.PHYSICAL));
  }

  private static List<String> anyNameParts(Any_nameContext anyName) {
    List<String> parts = new ArrayList<>();
    parts.add(identifierText(anyName.colid()));
    if (anyName.attrs() != null) {
      for (var attr : anyName.attrs().attr_name()) {
        parts.add(identifierText(attr));
      }
    }
    return parts;
  }

  private static boolean isCteSource(Qualified_nameContext relation) {
    if (relation.getParent() instanceof MergestmtContext merge) {
      return merge.qualified_name().size() > 1 && merge.qualified_name(1) == relation;
    }
    if (!(relation.getParent() instanceof Relation_exprContext expression)) {
      return false;
    }
    if (!(expression.getParent() instanceof Relation_expr_opt_aliasContext target)) {
      return true;
    }
    return !(target.getParent() instanceof UpdatestmtContext
        || target.getParent() instanceof DeletestmtContext);
  }

  private void addColumn(ColumnrefContext column) {
    List<String> parts = new ArrayList<>();
    parts.add(identifierText(column.colid()));
    boolean star = false;
    if (column.indirection() != null) {
      for (var element : column.indirection().indirection_el()) {
        if (element.attr_name() != null) {
          parts.add(identifierText(element.attr_name()));
        } else if (element.STAR() != null) {
          star = true;
          break;
        } else {
          break;
        }
      }
    }
    if (star) {
      features.add(QueryFeature.STAR_PROJECTION);
      columns.add(new ColumnReference(String.join(".", parts), "*"));
    } else {
      String name = parts.removeLast();
      columns.add(new ColumnReference(parts.isEmpty() ? null : String.join(".", parts), name));
    }
  }

  private void addFunction(Func_applicationContext call) {
    List<String> parts = funcNameParts(call.func_name());
    String name = parts.removeLast();
    functions.add(
        new FunctionReference(
            parts.isEmpty() ? null : String.join(".", parts),
            name,
            call.getStart().getLine(),
            call.getStart().getCharPositionInLine()));
  }

  private void addFunctionRelation(Func_tableContext functionRelation) {
    features.add(QueryFeature.FUNCTION_RELATION);
    String alias = funcTableAlias(functionRelation);
    for (Func_expr_windowlessContext windowless : windowlessFunctions(functionRelation)) {
      if (windowless.func_application() != null) {
        List<String> parts = funcNameParts(windowless.func_application().func_name());
        String name = parts.removeLast();
        relations.add(
            new RelationReference(
                parts.isEmpty() ? null : String.join(".", parts),
                name,
                alias,
                RelationReference.Kind.FUNCTION));
      } else {
        relations.add(
            new RelationReference(
                null,
                asciiLowercase(windowless.getStart().getText()),
                alias,
                RelationReference.Kind.FUNCTION));
      }
    }
  }

  private void addTableFunctionRelation(ParserRuleContext tableFunction) {
    features.add(QueryFeature.FUNCTION_RELATION);
    String alias =
        tableFunction.getParent() instanceof Table_refContext tableRef
            ? followingAlias(tableRef, tableFunction)
            : null;
    relations.add(
        new RelationReference(
            null,
            asciiLowercase(tableFunction.getStart().getText()),
            alias,
            RelationReference.Kind.FUNCTION));
  }

  private static List<Func_expr_windowlessContext> windowlessFunctions(
      Func_tableContext functionRelation) {
    if (functionRelation.func_expr_windowless() != null) {
      return List.of(functionRelation.func_expr_windowless());
    }
    return functionRelation.rowsfrom_list().rowsfrom_item().stream()
        .map(PostgreSQLParser.Rowsfrom_itemContext::func_expr_windowless)
        .toList();
  }

  private static String funcTableAlias(Func_tableContext functionRelation) {
    if (!(functionRelation.getParent() instanceof Table_refContext tableRef)) {
      return null;
    }
    Func_alias_clauseContext aliasClause = firstSiblingAlias(tableRef, functionRelation);
    if (aliasClause == null) {
      return null;
    }
    if (aliasClause.alias_clause() != null) {
      return identifierText(aliasClause.alias_clause().colid());
    }
    return aliasClause.colid() != null ? identifierText(aliasClause.colid()) : null;
  }

  private static Func_alias_clauseContext firstSiblingAlias(
      Table_refContext tableRef, ParseTree child) {
    for (int i = indexOf(tableRef, child) + 1; i < tableRef.getChildCount(); i++) {
      if (tableRef.getChild(i) instanceof Func_alias_clauseContext alias) {
        return alias;
      }
      break;
    }
    return null;
  }

  private static String aliasFor(Qualified_nameContext relation) {
    var parent = relation.getParent();
    if (parent instanceof Insert_targetContext target) {
      return target.colid() != null ? identifierText(target.colid()) : null;
    }
    if (parent instanceof MergestmtContext merge) {
      return followingAlias(merge, relation);
    }
    if (parent instanceof Relation_exprContext relationExpr) {
      var grandParent = relationExpr.getParent();
      if (grandParent instanceof Relation_expr_opt_aliasContext optAlias) {
        return optAlias.colid() != null ? identifierText(optAlias.colid()) : null;
      }
      if (grandParent instanceof Table_refContext tableRef) {
        return followingAlias(tableRef, relationExpr);
      }
    }
    return null;
  }

  private static String followingAlias(ParserRuleContext parent, ParseTree child) {
    for (int i = indexOf(parent, child) + 1; i < parent.getChildCount(); i++) {
      ParseTree sibling = parent.getChild(i);
      if (sibling instanceof Alias_clauseContext alias) {
        return identifierText(alias.colid());
      }
      if (!(sibling instanceof Tablesample_clauseContext)) {
        return null;
      }
    }
    return null;
  }

  private static int indexOf(ParserRuleContext parent, ParseTree child) {
    for (int i = 0; i < parent.getChildCount(); i++) {
      if (parent.getChild(i) == child) {
        return i;
      }
    }
    return -1;
  }

  private static boolean hasTableRefAncestor(ParserRuleContext context) {
    for (var ancestor = context.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
      if (ancestor instanceof Table_refContext) {
        return true;
      }
      if (ancestor instanceof StmtContext) {
        return false;
      }
    }
    return false;
  }

  private static List<String> nameParts(Qualified_nameContext relation) {
    List<String> parts = new ArrayList<>();
    parts.add(identifierText(relation.colid()));
    if (relation.indirection() != null) {
      for (var element : relation.indirection().indirection_el()) {
        if (element.attr_name() != null) {
          parts.add(identifierText(element.attr_name()));
        } else {
          break;
        }
      }
    }
    return parts;
  }

  private static List<String> funcNameParts(Func_nameContext functionName) {
    if (functionName.type_function_name() != null) {
      List<String> parts = new ArrayList<>();
      parts.add(identifierText(functionName.type_function_name()));
      return parts;
    }
    List<String> parts = new ArrayList<>();
    parts.add(identifierText(functionName.colid()));
    for (var element : functionName.indirection().indirection_el()) {
      if (element.attr_name() != null) {
        parts.add(identifierText(element.attr_name()));
      } else {
        break;
      }
    }
    return parts;
  }

  private static String identifierText(ParserRuleContext identifier) {
    String raw = identifier.getText();
    if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
      return raw.substring(1, raw.length() - 1).replace("\"\"", "\"");
    }
    return asciiLowercase(raw);
  }

  static String asciiLowercase(String value) {
    var folded = new StringBuilder(value.length());
    value
        .codePoints()
        .map(codePoint -> codePoint >= 'A' && codePoint <= 'Z' ? codePoint + 32 : codePoint)
        .forEach(folded::appendCodePoint);
    return folded.toString();
  }
}
