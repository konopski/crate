/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

tree grammar StatementBuilder;

options {
    tokenVocab = Statement;
    output = AST;
    ASTLabelType = CommonTree;
}

@header {
    package io.crate.sql.parser;

    import io.crate.sql.tree.*;

    import java.util.ArrayList;
    import java.util.List;
    import java.util.Locale;
    import com.google.common.collect.ImmutableList;
    import com.google.common.base.Objects;
    import com.google.common.base.Optional;
}

@members {
    private int parameterPos = 1;

    @Override
    protected Object recoverFromMismatchedToken(IntStream input, int tokenType, BitSet follow)
            throws RecognitionException
    {
        throw new MismatchedTokenException(tokenType, input);
    }

    @Override
    public Object recoverFromMismatchedSet(IntStream input, RecognitionException e, BitSet follow)
            throws RecognitionException
    {
        throw e;
    }
}

@rulecatch {
    catch (RecognitionException re) {
        throw new IllegalArgumentException("bad tree from parser: " + getErrorMessage(re, getTokenNames()), re);
    }
}


statement returns [Statement value]
    : query                     { $value = $query.value; }
    | explain                   { $value = $explain.value; }
    | showTables                { $value = $showTables.value; }
    | showSchemas               { $value = $showSchemas.value; }
    | showCatalogs              { $value = $showCatalogs.value; }
    | showColumns               { $value = $showColumns.value; }
    | showPartitions            { $value = $showPartitions.value; }
    | showFunctions             { $value = $showFunctions.value; }
    | createTable               { $value = $createTable.value; }
    | alterTable                { $value = $alterTable.value; }
    | alterBlobTable            { $value = $alterBlobTable.value; }
    | createBlobTable           { $value = $createBlobTable.value; }
    | createMaterializedView    { $value = $createMaterializedView.value; }
    | refreshMaterializedView   { $value = $refreshMaterializedView.value; }
    | createAlias               { $value = $createAlias.value; }
    | dropAlias                 { $value = $dropAlias.value; }
    | dropTable                 { $value = $dropTable.value; }
    | dropBlobTable             { $value = $dropBlobTable.value; }
    | insert                    { $value = $insert.value; }
    | delete                    { $value = $delete.value; }
    | update                    { $value = $update.value; }
    | copyFrom                  { $value = $copyFrom.value; }
    | createAnalyzer            { $value = $createAnalyzer.value; }
    | refresh                   { $value = $refresh.value; }
    ;

query returns [Query value]
    : ^(QUERY queryExpr) { $value = $queryExpr.value; }
    ;

queryExpr returns [Query value]
    : withClause?
      queryBody
      orderClause?
      limitClause?
      offsetClause?
        { $value = new Query(
            Optional.fromNullable($withClause.value),
            $queryBody.value,
            Objects.firstNonNull($orderClause.value, ImmutableList.<SortItem>of()),
            Optional.fromNullable($limitClause.value),
            Optional.fromNullable($offsetClause.value));
        }
    ;

queryBody returns [QueryBody value]
    : querySpec             { $value = $querySpec.value; }
    | setOperation          { $value = $setOperation.value; }
    | tableSubquery         { $value = $tableSubquery.value; }
    | namedTable            { $value = $namedTable.value; }
    ;

querySpec returns [QuerySpecification value]
    : ^(QUERY_SPEC
        selectClause
        fromClause?
        whereClause?
        groupClause?
        havingClause?
        orderClause?
        limitClause?
        offsetClause?)
        { $value = new QuerySpecification(
            $selectClause.value,
            $fromClause.value,
            Optional.fromNullable($whereClause.value),
            Objects.firstNonNull($groupClause.value, ImmutableList.<Expression>of()),
            Optional.fromNullable($havingClause.value),
            Objects.firstNonNull($orderClause.value, ImmutableList.<SortItem>of()),
            Optional.fromNullable($limitClause.value),
            Optional.fromNullable($offsetClause.value));
        }
    ;

setOperation returns [SetOperation value]
    : ^(UNION q1=queryBody q2=queryBody d=distinct[true])       { $value = new Union(ImmutableList.<Relation>of($q1.value, $q2.value), $d.value); }
    | ^(INTERSECT q1=queryBody q2=queryBody d=distinct[true])   { $value = new Intersect(ImmutableList.<Relation>of($q1.value, $q2.value), $d.value); }
    | ^(EXCEPT q1=queryBody q2=queryBody d=distinct[true])      { $value = new Except($q1.value, $q2.value, $d.value); }
    ;

restrictedSelectStmt returns [Query value]
    : selectClause fromClause
        { $value = new Query(
            Optional.<With>absent(),
            new QuerySpecification(
                $selectClause.value,
                $fromClause.value,
                Optional.<Expression>absent(),
                ImmutableList.<Expression>of(),
                Optional.<Expression>absent(),
                ImmutableList.<SortItem>of(),
                Optional.<Expression>absent(),
                Optional.<Expression>absent()),
            ImmutableList.<SortItem>of(),
            Optional.<Expression>absent(),
            Optional.<Expression>absent());
        }
    ;

withClause returns [With value]
    : ^(WITH recursive withList) { $value = new With($recursive.value, $withList.value); }
    ;

recursive returns [boolean value]
    : RECURSIVE { $value = true; }
    |           { $value = false; }
    ;

withList returns [List<WithQuery> value = new ArrayList<>()]
    : ^(WITH_LIST ( withQuery { $value.add($withQuery.value); } )+ )
    ;

withQuery returns [WithQuery value]
    : ^(WITH_QUERY i=ident q=query c=aliasedColumns?) { $value = new WithQuery($i.value, $q.value, $c.value); }
    ;

selectClause returns [Select value]
    : ^(SELECT d=distinct[false] s=selectList) { $value = new Select($d.value, $s.value); }
    ;

distinct[boolean defaultValue] returns [boolean value]
    : DISTINCT  { $value = true; }
    | ALL       { $value = false; }
    |           { $value = $defaultValue; }
    ;

selectList returns [List<SelectItem> value = new ArrayList<>()]
    : ^(SELECT_LIST ( selectItem { $value.add($selectItem.value); } )+ )
    ;

selectItem returns [SelectItem value]
    :
      ^(SELECT_ITEM expr ident?)                       { $value = new SingleColumn($expr.value, Optional.fromNullable($ident.value)); }
    | (^(ALL_COLUMNS qname)) => ^(ALL_COLUMNS qname)   { $value = new AllColumns($qname.value); }
    | ALL_COLUMNS                                      { $value = new AllColumns(); }
    ;

fromClause returns [List<Relation> value]
    : ^(FROM t=relationList) { $value = $t.value; }
    ;

whereClause returns [Expression value]
    : ^(WHERE expr) { $value = $expr.value; }
    ;

groupClause returns [List<Expression> value]
    : ^(GROUP_BY exprList) { $value = $exprList.value; }
    ;

havingClause returns [Expression value]
    : ^(HAVING expr) { $value = $expr.value; }
    ;

orderClause returns [List<SortItem> value = new ArrayList<>()]
    : ^(ORDER_BY ( sortItem { $value.add($sortItem.value); } )+ )
    ;

sortItem returns [SortItem value]
    : ^(SORT_ITEM expr o=ordering n=nullOrdering) { $value = new SortItem($expr.value, $o.value, $n.value); }
    ;

ordering returns [SortItem.Ordering value]
    : ASC  { $value = SortItem.Ordering.ASCENDING; }
    | DESC { $value = SortItem.Ordering.DESCENDING; }
    ;

nullOrdering returns [SortItem.NullOrdering value]
    : FIRST { $value = SortItem.NullOrdering.FIRST; }
    | LAST  { $value = SortItem.NullOrdering.LAST; }
    |       { $value = SortItem.NullOrdering.UNDEFINED; }
    ;

limitClause returns [Expression value]
    : ^(LIMIT integer) { $value = new LongLiteral($integer.value); }
    | ^(LIMIT parameterExpr) { $value = $parameterExpr.value; }
    ;

offsetClause returns [Expression value]
    : ^(OFFSET integer) { $value = new LongLiteral($integer.value); }
    | ^(OFFSET parameterExpr) { $value = $parameterExpr.value; }
    ;

sampleType returns [SampledRelation.Type value]
    : BERNOULLI { $value = SampledRelation.Type.BERNOULLI; }
    | SYSTEM    { $value = SampledRelation.Type.SYSTEM; }
    ;

stratifyOn returns [List<Expression> value]
    : ^(STRATIFY_ON exprList) { $value = $exprList.value; }
    ;

relationList returns [List<Relation> value = new ArrayList<>()]
    : ( relation { $value.add($relation.value); } )+
    ;

relation returns [Relation value]
    : relationType      { $value = $relationType.value; }
    | aliasedRelation   { $value = $aliasedRelation.value; }
    | sampledRelation   { $value = $sampledRelation.value; }
    ;

relationType returns [Relation value]
    : namedTable       { $value = $namedTable.value; }
    | tableSubquery    { $value = $tableSubquery.value; }
    | joinedTable      { $value = $joinedTable.value; }
    | joinRelation     { $value = $joinRelation.value; }
    ;

namedTable returns [Table value]
    : ^(TABLE qname) { $value = new Table($qname.value); }
    ;

joinedTable returns [Relation value]
    : ^(JOINED_TABLE relation) { $value = $relation.value; }
    ;

joinRelation returns [Join value]
    : ^(CROSS_JOIN a=relation b=relation)                               { $value = new Join(Join.Type.CROSS, $a.value, $b.value, Optional.<JoinCriteria>absent()); }
    | ^(QUALIFIED_JOIN t=joinType c=joinCriteria a=relation b=relation) { $value = new Join($t.value, $a.value, $b.value, Optional.fromNullable($c.value)); }
    ;

aliasedRelation returns [AliasedRelation value]
    : ^(ALIASED_RELATION r=relation i=ident c=aliasedColumns?) { $value = new AliasedRelation($r.value, $i.value, $c.value); }
    ;

sampledRelation returns [SampledRelation value]
    : ^(SAMPLED_RELATION r=relation t=sampleType p=expr st=stratifyOn?) { $value = new SampledRelation($r.value, $t.value, $p.value, Optional.fromNullable($st.value)); }
    ;

aliasedColumns returns [List<String> value]
    : ^(ALIASED_COLUMNS identList) { $value = $identList.value; }
    ;

joinType returns [Join.Type value]
    : INNER_JOIN { $value = Join.Type.INNER; }
    | LEFT_JOIN  { $value = Join.Type.LEFT; }
    | RIGHT_JOIN { $value = Join.Type.RIGHT; }
    | FULL_JOIN  { $value = Join.Type.FULL; }
    ;

joinCriteria returns [JoinCriteria value]
    : NATURAL            { $value = new NaturalJoin(); }
    | ^(ON expr)         { $value = new JoinOn($expr.value); }
    | ^(USING identList) { $value = new JoinUsing($identList.value); }
    ;

tableSubquery returns [TableSubquery value]
    : ^(TABLE_SUBQUERY query) { $value = new TableSubquery($query.value); }
    ;

singleExpression returns [Expression value]
    : expr EOF { $value = $expr.value; }
    ;

expr returns [Expression value]
    : NULL                  { $value = new NullLiteral(); }
    | qname                 { $value = new QualifiedNameReference($qname.value); }
    | subscript             { $value = $subscript.value; }
    | parameterExpr         { $value = $parameterExpr.value; }
    | functionCall          { $value = $functionCall.value; }
    | arithmeticExpression  { $value = $arithmeticExpression.value; }
    | comparisonExpression  { $value = $comparisonExpression.value; }
    | ^(AND a=expr b=expr)  { $value = LogicalBinaryExpression.and($a.value, $b.value); }
    | ^(OR a=expr b=expr)   { $value = LogicalBinaryExpression.or($a.value, $b.value); }
    | ^(NOT e=expr)         { $value = new NotExpression($e.value); }
    | ^(DATE string)        { $value = new DateLiteral($string.value); }
    | ^(TIME string)        { $value = new TimeLiteral($string.value); }
    | ^(TIMESTAMP string)   { $value = new TimestampLiteral($string.value); }
    | string                { $value = new StringLiteral($string.value); }
    | integer               { $value = new LongLiteral($integer.value); }
    | decimal               { $value = new DoubleLiteral($decimal.value); }
    | TRUE                  { $value = BooleanLiteral.TRUE_LITERAL; }
    | FALSE                 { $value = BooleanLiteral.FALSE_LITERAL; }
    | intervalValue         { $value = $intervalValue.value; }
    | predicate             { $value = $predicate.value; }
    | ^(IN_LIST exprList)   { $value = new InListExpression($exprList.value); }
    | ^(NEGATIVE e=expr)    { $value = new NegativeExpression($e.value); }
    | caseExpression        { $value = $caseExpression.value; }
    | query                 { $value = new SubqueryExpression($query.value); }
    | extract               { $value = $extract.value; }
    | current_time          { $value = $current_time.value; }
    | cast                  { $value = $cast.value; }
    ;

exprList returns [List<Expression> value = new ArrayList<>()]
    : ( expr { $value.add($expr.value); } )*
    ;

parameterExpr returns [ParameterExpression value]
    : '$' integer { $value = new ParameterExpression(Integer.parseInt($integer.value)); }
    | '?'         { $value = new ParameterExpression(parameterPos++); }
    ;

subscript returns [SubscriptExpression value]
    :   ^('[' a=expr b=expr) { $value = new SubscriptExpression($a.value, $b.value); }
    ;

qname returns [QualifiedName value]
    : ^(QNAME i=identList) { $value = new QualifiedName($i.value); }
    ;

qnameList returns [List<QualifiedNameReference> value = new ArrayList<>()]
    : ( qname { $value.add(new QualifiedNameReference($qname.value)); } )+
    ;

identList returns [List<String> value = new ArrayList<>()]
    : ( ident { $value.add($ident.value); } )+
    ;

/*
 * case sensitivity like it is in postgres
 * see also http://www.thenextage.com/wordpress/postgresql-case-sensitivity-part-1-the-ddl/
 *
 * unfortunately this has to be done in the parser because afterwards the
 * knowledge of the IDENT / QUOTED_IDENT difference is lost
 */
ident returns [String value]
    : i=IDENT        { $value = $i.text.toLowerCase(Locale.ENGLISH); }
    | q=QUOTED_IDENT { $value = $q.text; }
    ;

string returns [String value]
    : s=STRING { $value = $s.text; }
    ;

integer returns [String value]
    : s=INTEGER_VALUE { $value = $s.text; }
    ;

decimal returns [String value]
    : s=DECIMAL_VALUE { $value = $s.text; }
    ;

functionCall returns [FunctionCall value]
    : ^(FUNCTION_CALL n=qname w=window? d=distinct[false] a=exprList) { $value = new FunctionCall($n.value, $w.value, $d.value, $a.value); }
    ;

window returns [Window value]
    : ^(WINDOW windowPartition? orderClause? windowFrame?)
        { $value = new Window(
            Objects.firstNonNull($windowPartition.value, ImmutableList.<Expression>of()),
            Objects.firstNonNull($orderClause.value, ImmutableList.<SortItem>of()),
            $windowFrame.value);
        }
    ;

windowPartition returns [List<Expression> value = new ArrayList<>()]
    : ^(PARTITION_BY exprList) { $value = $exprList.value; }
    ;

windowFrame returns [WindowFrame value]
    : ^(RANGE s=frameBound e=frameBound?) { $value = new WindowFrame(WindowFrame.Type.RANGE, $s.value, $e.value); }
    | ^(ROWS s=frameBound e=frameBound?)  { $value = new WindowFrame(WindowFrame.Type.ROWS, $s.value, $e.value); }
    ;

frameBound returns [FrameBound value]
    : UNBOUNDED_PRECEDING { $value = new FrameBound(FrameBound.Type.UNBOUNDED_PRECEDING); }
    | UNBOUNDED_FOLLOWING { $value = new FrameBound(FrameBound.Type.UNBOUNDED_FOLLOWING); }
    | CURRENT_ROW         { $value = new FrameBound(FrameBound.Type.CURRENT_ROW); }
    | ^(PRECEDING expr)   { $value = new FrameBound(FrameBound.Type.PRECEDING, $expr.value); }
    | ^(FOLLOWING expr)   { $value = new FrameBound(FrameBound.Type.FOLLOWING, $expr.value); }
    ;

extract returns [Extract value]
    : ^(EXTRACT field=IDENT expr) { $value = new Extract($expr.value, Extract.Field.valueOf($field.text.toUpperCase())); }
    ;

cast returns [Cast value]
    : ^(CAST expr IDENT) { $value = new Cast($expr.value, $IDENT.text); }
    ;

current_time returns [CurrentTime value]
    : CURRENT_DATE                   { $value = new CurrentTime(CurrentTime.Type.DATE); }
    | CURRENT_TIME                   { $value = new CurrentTime(CurrentTime.Type.TIME); }
    | CURRENT_TIMESTAMP              { $value = new CurrentTime(CurrentTime.Type.TIMESTAMP); }
    | ^(CURRENT_TIME integer)        { $value = new CurrentTime(CurrentTime.Type.TIME, Integer.valueOf($integer.value)); }
    | ^(CURRENT_TIMESTAMP integer)   { $value = new CurrentTime(CurrentTime.Type.TIMESTAMP, Integer.valueOf($integer.value)); }
    ;

arithmeticExpression returns [ArithmeticExpression value]
    : ^(t=arithmeticType a=expr b=expr) { $value = new ArithmeticExpression($t.value, $a.value, $b.value); }
    ;

arithmeticType returns [ArithmeticExpression.Type value]
    : '+' { $value = ArithmeticExpression.Type.ADD; }
    | '-' { $value = ArithmeticExpression.Type.SUBTRACT; }
    | '*' { $value = ArithmeticExpression.Type.MULTIPLY; }
    | '/' { $value = ArithmeticExpression.Type.DIVIDE; }
    | '%' { $value = ArithmeticExpression.Type.MODULUS; }
    ;

comparisonExpression returns [ComparisonExpression value]
    : ^(t=comparisonType a=expr b=expr) { $value = new ComparisonExpression($t.value, $a.value, $b.value); }
    ;

comparisonType returns [ComparisonExpression.Type value]
    : EQ                    { $value = ComparisonExpression.Type.EQUAL; }
    | NEQ                   { $value = ComparisonExpression.Type.NOT_EQUAL; }
    | LT                    { $value = ComparisonExpression.Type.LESS_THAN; }
    | LTE                   { $value = ComparisonExpression.Type.LESS_THAN_OR_EQUAL; }
    | GT                    { $value = ComparisonExpression.Type.GREATER_THAN; }
    | GTE                   { $value = ComparisonExpression.Type.GREATER_THAN_OR_EQUAL; }
    | IS_DISTINCT_FROM      { $value = ComparisonExpression.Type.IS_DISTINCT_FROM; }
    ;

intervalValue returns [IntervalLiteral value]
    : ^(INTERVAL s=string q=intervalQualifier g=intervalSign) { $value = new IntervalLiteral($s.value, $q.value, $g.value); }
    ;

// TODO: this needs to be structured data
intervalQualifier returns [String value]
    : t=nonSecond                   { $value = $t.value; }
    | ^(t=nonSecond p=integer)      { $value = String.format("\%s (\%s)", $t.value, $p.value); }
    | SECOND                        { $value = "SECOND"; }
    | ^(SECOND p=integer)           { $value = String.format("SECOND (\%s)", $p.value); }
    | ^(SECOND p=integer s=integer) { $value = String.format("SECOND (\%s, \%s)", $p.value, $s.value); }
    ;

nonSecond returns [String value]
    : t=(YEAR | MONTH | DAY | HOUR | MINUTE) { $value = $t.text; }
    ;

intervalSign returns [IntervalLiteral.Sign value]
    : NEGATIVE { $value = IntervalLiteral.Sign.NEGATIVE; }
    |          { $value = IntervalLiteral.Sign.POSITIVE; }
    ;

predicate returns [Expression value]
    : ^(BETWEEN v=expr min=expr max=expr) { $value = new BetweenPredicate($v.value, $min.value, $max.value); }
    | ^(LIKE v=expr p=expr esc=expr?)     { $value = new LikePredicate($v.value, $p.value, $esc.value); }
    | ^(IS_NULL expr)                     { $value = new IsNullPredicate($expr.value); }
    | ^(IS_NOT_NULL expr)                 { $value = new IsNotNullPredicate($expr.value); }
    | ^(IN v=expr list=expr)              { $value = new InPredicate($v.value, $list.value); }
    | ^(EXISTS q=query)                   { $value = new ExistsPredicate($q.value); }
    ;

caseExpression returns [Expression value]
    : ^(NULLIF a=expr b=expr)                { $value = new NullIfExpression($a.value, $b.value); }
    | ^(COALESCE exprList)                   { $value = new CoalesceExpression($exprList.value); }
    | ^(SIMPLE_CASE v=expr whenList e=expr?) { $value = new SimpleCaseExpression($v.value, $whenList.value, $e.value); }
    | ^(SEARCHED_CASE whenList e=expr?)      { $value = new SearchedCaseExpression($whenList.value, $e.value); }
    | ^(IF c=expr t=expr f=expr?)            { $value = new IfExpression($c.value, $t.value, $f.value); }
    ;

whenList returns [List<WhenClause> value = new ArrayList<>()]
    : ( ^(WHEN a=expr b=expr) { $value.add(new WhenClause($a.value, $b.value)); } )+
    ;

explain returns [Statement value]
    : ^(EXPLAIN explainOptions? statement) { $value = new Explain($statement.value, $explainOptions.value); }
    ;

explainOptions returns [List<ExplainOption> value = new ArrayList<>()]
    : ^(EXPLAIN_OPTIONS ( explainOption { $value.add($explainOption.value); } )+ )
    ;

explainOption returns [ExplainOption value]
    : ^(EXPLAIN_FORMAT TEXT)      { $value = new ExplainFormat(ExplainFormat.Type.TEXT); }
    | ^(EXPLAIN_FORMAT GRAPHVIZ)  { $value = new ExplainFormat(ExplainFormat.Type.GRAPHVIZ); }
    | ^(EXPLAIN_TYPE LOGICAL)     { $value = new ExplainType(ExplainType.Type.LOGICAL); }
    | ^(EXPLAIN_TYPE DISTRIBUTED) { $value = new ExplainType(ExplainType.Type.DISTRIBUTED); }
    ;

showTables returns [Statement value]
    : ^(SHOW_TABLES from=showTablesFrom? like=showTablesLike?) { $value = new ShowTables($from.value, $like.value); }
    ;

showTablesFrom returns [QualifiedName value]
    : ^(FROM qname) { $value = $qname.value; }
    ;

showTablesLike returns [String value]
    : ^(LIKE string) { $value = $string.value; }
    ;

showSchemas returns [Statement value]
    : ^(SHOW_SCHEMAS from=showSchemasFrom?) { $value = new ShowSchemas(Optional.fromNullable($from.value)); }
    ;

showSchemasFrom returns [String value]
    : ^(FROM ident) { $value = $ident.value; }
    ;

showCatalogs returns [Statement value]
    : SHOW_CATALOGS { $value = new ShowCatalogs(); }
    ;

showColumns returns [Statement value]
    : ^(SHOW_COLUMNS qname) { $value = new ShowColumns($qname.value); }
    ;

showPartitions returns [Statement value]
    : ^(SHOW_PARTITIONS qname whereClause? orderClause? limitClause? offsetClause?)
        { $value = new ShowPartitions(
            $qname.value,
            Optional.fromNullable($whereClause.value),
            Objects.firstNonNull($orderClause.value, ImmutableList.<SortItem>of()),
            Optional.fromNullable($limitClause.value),
            Optional.fromNullable($offsetClause.value));
        }
    ;

showFunctions returns [Statement value]
    : SHOW_FUNCTIONS { $value = new ShowFunctions(); }
    ;

createMaterializedView returns [Statement value]
    : ^(CREATE_MATERIALIZED_VIEW qname refreshView=viewRefresh? select=restrictedSelectStmt)
        { $value = new CreateMaterializedView($qname.value, Optional.fromNullable($refreshView.value), $select.value); }
    ;

refreshMaterializedView returns [Statement value]
    : ^(REFRESH_MATERIALIZED_VIEW qname) { $value = new RefreshMaterializedView($qname.value); }
    ;

viewRefresh returns [String value]
    : ^(REFRESH integer) { $value = $integer.value; }
    ;

createAlias returns [Statement value]
    : ^(CREATE_ALIAS qname remote=forRemote) { $value = new CreateAlias($qname.value, $remote.value); }
    ;

dropAlias returns [Statement value]
    : ^(DROP_ALIAS qname) { $value = new DropAlias($qname.value); }
    ;

forRemote returns [QualifiedName value]
    : ^(FOR qname) { $value = $qname.value; }
    ;

dropBlobTable returns [Statement value]
    : ^(DROP_BLOB_TABLE namedTable) { $value = new DropBlobTable($namedTable.value); }
    ;

dropTable returns [Statement value]
    : ^(DROP_TABLE namedTable) { $value = new DropTable($namedTable.value); }
    ;

insert returns [Statement value]
    : ^(INSERT namedTable values=insertValues cols=insertColumnsList?)
        {
            $value = new Insert($namedTable.value,
                                $values.value,
                                Objects.firstNonNull($cols.value, ImmutableList.<QualifiedNameReference>of())
                                );
        }
    ;

insertValues returns [List<ValuesList> value = new ArrayList<>()]
    : ^(INSERT_VALUES (valuesList { $value.add($valuesList.value); })+)
    ;

valuesList returns [ValuesList value]
    : ^(VALUES_LIST exprList) { $value = new ValuesList($exprList.value); }
    ;

insertColumnsList returns [List<QualifiedNameReference> value]
    : ^(COLUMN_LIST qnameList) { $value = $qnameList.value; }
    ;


delete returns [Statement value]
    : ^(DELETE namedTable where=whereClause?)
        {
            $value = new Delete($namedTable.value, $where.value);
        }
    ;

update returns [Statement value]
    : ^(UPDATE namedTable assignments=assignmentList where=whereClause?)
        {
            $value = new Update($namedTable.value,
                                $assignments.value,
                                $where.value);
        }
    ;

assignmentList returns [List<Assignment> value = new ArrayList<>()]
    : ^(ASSIGNMENT_LIST (assignment { $value.add($assignment.value); })+ )
    ;

assignment returns [Assignment value]
    : ^(ASSIGNMENT subscript expr) { $value = new Assignment($subscript.value, $expr.value); }
    | ^(ASSIGNMENT qname expr) { $value = new Assignment(new QualifiedNameReference($qname.value), $expr.value); }
    ;

copyFrom returns [Statement value]
    : ^(COPY_FROM namedTable path=expr) { $value = new CopyFromStatement($namedTable.value, $path.value); }
    ;

createBlobTable returns [Statement value]
    : ^(CREATE_BLOB_TABLE namedTable clusteredBy? genericProperties?)
        {
            $value = new CreateBlobTable($namedTable.value,
                                         $clusteredBy.value,
                                         $genericProperties.value);
        }
    ;

alterBlobTable returns [Statement value]
    : ^(ALTER_BLOB_TABLE namedTable genericProperties)
        {
            $value = new AlterBlobTable($namedTable.value, $genericProperties.value);
        }
    | ^(ALTER_BLOB_TABLE namedTable identList)
        {
            $value = new AlterBlobTable($namedTable.value, $identList.value);
        }
    ;

alterTable returns [Statement value]
    : ^(ALTER_TABLE namedTable genericProperties)
        {
            $value = new AlterTable($namedTable.value, $genericProperties.value);
        }
    | ^(ALTER_TABLE namedTable identList)
        {
            $value = new AlterTable($namedTable.value, $identList.value);
        }
    ;


createTable returns [Statement value]
    : ^(CREATE_TABLE namedTable tableElementList clusteredBy? genericProperties?)
        {
            $value = new CreateTable($namedTable.value,
                                     $tableElementList.value,
                                     $clusteredBy.value,
                                     $genericProperties.value);
        }
    ;

tableElementList returns [List<TableElement> value = new ArrayList<>()]
    : ^(TABLE_ELEMENT_LIST (tableElement { $value.add($tableElement.value); } )+ )
    ;

tableElement returns [TableElement value]
    : columnDefinition { $value = $columnDefinition.value; }
    | indexDefinition  { $value = $indexDefinition.value; }
    | primaryKeyConstraint { $value = $primaryKeyConstraint.value; }
    ;

columnDefinition returns [ColumnDefinition value]
    : ^(COLUMN_DEF ident dataType columnConstraints)
        {
            $value = new ColumnDefinition($ident.value, $dataType.value, $columnConstraints.value);
        }
    ;

dataType returns [ColumnType value]
    : type=(BOOLEAN | BYTE | SHORT | INT | INTEGER | LONG | FLOAT | DOUBLE | TIMESTAMP | STRING_TYPE | IP )
      {
        $value = new ColumnType($type.text.toLowerCase(Locale.ENGLISH));
      }
    | objectTypeDefinition  { $value = $objectTypeDefinition.value; }
    | ^(ARRAY innerType=dataType)     { $value = CollectionColumnType.array($innerType.value); }
    | ^(SET innerType=dataType)       { $value = CollectionColumnType.set($innerType.value); }
    ;

objectTypeDefinition returns [ObjectColumnType value]
    : ^(OBJECT type=objectType? columnDefinitionList?)
        {
            $value = new ObjectColumnType($type.value, $columnDefinitionList.value);
        }
    ;

objectType returns [String value]
    : type=(DYNAMIC|STRICT|IGNORED) { $value = $type.text.toLowerCase(Locale.ENGLISH); }
    ;

columnDefinitionList returns [List<ColumnDefinition> value = new ArrayList<>()]
    : ^(OBJECT_COLUMNS ( columnDefinition { $value.add($columnDefinition.value); } )+ )
    ;

columnConstraints returns [List<ColumnConstraint> value = new ArrayList<>()]
    : (^(CONSTRAINT columnConstraint) { $value.add($columnConstraint.value); } )*
    ;

columnConstraint returns [ColumnConstraint value]
    : PRIMARY_KEY { $value = new PrimaryKeyColumnConstraint(); }
    | INDEX_OFF   { $value = IndexColumnConstraint.OFF; }
    | ^(INDEX indexMethod=ident genericProperties?)
        {
            $value = new IndexColumnConstraint($indexMethod.value, $genericProperties.value);
        }
    ;

genericProperties returns [GenericProperties value = new GenericProperties()]
    : ^( GENERIC_PROPERTIES ( genericProperty { $value.add($genericProperty.value); } )+ )
    ;

genericProperty returns [GenericProperty value]
    : ^(GENERIC_PROPERTY key=ident expr) { $value = new GenericProperty($key.value, ImmutableList.of($expr.value)); }
    | ^(GENERIC_PROPERTY key=ident literalList) { $value = new GenericProperty($key.value, $literalList.value); }
    ;

literalList returns [List<Expression> value]
    : ^(LITERAL_LIST exprList) { $value=$exprList.value; }
    ;

indexDefinition returns [IndexDefinition value]
    : ^(INDEX indexName=ident indexMethod=ident indexColumns genericProperties?)
        {
            $value = new IndexDefinition($indexName.value, $indexMethod.value, $indexColumns.value, $genericProperties.value);
        }
    ;

indexColumns returns [List<Expression> value]
    : ^(INDEX_COLUMNS columnList) { $value = $columnList.value; }
    ;

columnList returns [List<Expression> value = new ArrayList<>()]
    : ( columnListElement { $value.add($columnListElement.value); } )+
    ;

columnListElement returns [Expression value]
    : subscript { $value = $subscript.value; }
    | qname { $value = new QualifiedNameReference($qname.value); }
    ;

primaryKeyConstraint returns [PrimaryKeyConstraint value]
    : ^(PRIMARY_KEY columnList) { $value = new PrimaryKeyConstraint($columnList.value); }
    ;

clusteredBy returns [ClusteredBy value]
    : ^(CLUSTERED integer) { $value = new ClusteredBy(null, $integer.value); }
    | ^(CLUSTERED subscript integer?) { $value = new ClusteredBy($subscript.value, $integer.value); }
    | ^(CLUSTERED qname integer?) { $value = new ClusteredBy(new QualifiedNameReference($qname.value), $integer.value); }
    ;

replicas returns [Integer value]
    : ^(REPLICAS integer) { $value = Integer.parseInt($integer.value); }
    ;


createAnalyzer returns [CreateAnalyzer value]
    : ^(ANALYZER ident extendsAnalyzer? analyzerElementList)
        {
            $value = new CreateAnalyzer($ident.value, $extendsAnalyzer.value, $analyzerElementList.value);
        }
    ;

extendsAnalyzer returns [String value]
    : ^(EXTENDS ident) { $value = $ident.value; }
    ;

analyzerElementList returns [List<AnalyzerElement> value = new ArrayList<>()]
    : ^(ANALYZER_ELEMENTS ( analyzerElement { $value.add($analyzerElement.value); } )+ )
    ;

analyzerElement returns [AnalyzerElement value]
    : ^(TOKENIZER namedProperties)  { $value = new Tokenizer($namedProperties.value); }
    | tokenFilters                  { $value = new TokenFilters($tokenFilters.value); }
    | charFilters                   { $value = new CharFilters($charFilters.value); }
    | genericProperty               { $value = $genericProperty.value; }
    ;


tokenFilters returns [List<NamedProperties> value = new ArrayList<>()]
    : ^(TOKEN_FILTERS ( namedProperties { $value.add($namedProperties.value); } )+ )
    ;

charFilters returns [List<NamedProperties> value = new ArrayList<>()]
    : ^(CHAR_FILTERS ( namedProperties { $value.add($namedProperties.value); } )+ )
    ;

namedProperties returns [NamedProperties value]
    : ^(NAMED_PROPERTIES ident genericProperties?) { $value = new NamedProperties($ident.value, $genericProperties.value); }
    ;

refresh returns [RefreshStatement value]
    : ^(REFRESH namedTable) { $value = new RefreshStatement($namedTable.value); }
    ;
