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

package io.crate.integrationtests;

import com.google.common.base.Joiner;
import io.crate.action.sql.SQLAction;
import io.crate.action.sql.SQLRequest;
import io.crate.action.sql.SQLResponse;
import io.crate.test.integration.CrateIntegrationTest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;
import java.util.HashMap;

import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;


@CrateIntegrationTest.ClusterScope(numNodes = 2, scope = CrateIntegrationTest.Scope.SUITE)
public class InformationSchemaTest extends SQLTransportIntegrationTest {

    static Joiner dotJoiner = Joiner.on('.');
    static Joiner commaJoiner = Joiner.on(", ");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    private SQLResponse response;

    public SQLResponse execute(String statement, Object[] args) {
        response = super.execute(statement, args);
        return response;
    }

    public SQLResponse execute(String statement) {
        return execute(statement, new Object[0]);
    }


    private void serviceSetup() {
        execute("create table t1 (col1 integer primary key, " +
                    "col2 string) clustered into 7 " +
                    "shards");
        execute(
            "create table t2 (col1 integer primary key, " +
                "col2 string) clustered into " +
                "10 shards");
        execute(
            "create table t3 (col1 integer, col2 string) with (number_of_replicas=8)");
        refresh();
    }

    @After
    public void cleanUp() throws Exception {
        wipeIndices("_all");
    }

    @Test
    public void testDefaultTables() throws Exception {
        execute("select * from information_schema.tables order by schema_name, table_name");
        assertEquals(6L, response.rowCount());

        assertArrayEquals(response.rows()[0], new Object[]{"information_schema", "columns", 1, "0", null});
        assertArrayEquals(response.rows()[1], new Object[]{"information_schema", "table_constraints", 1, "0", null});
        assertArrayEquals(response.rows()[2], new Object[]{"information_schema", "tables", 1, "0", null});
        assertArrayEquals(response.rows()[3], new Object[]{"sys", "cluster", 1, "0", null});
        assertArrayEquals(response.rows()[4], new Object[]{"sys", "nodes", 1, "0", null});
        assertArrayEquals(response.rows()[5], new Object[]{"sys", "shards", 1, "0", null});
    }

    @Test
    public void testSearchInformationSchemaTablesRefresh() throws Exception {
        serviceSetup();

        execute("select * from information_schema.tables");
        assertEquals(9L, response.rowCount());

        client().execute(SQLAction.INSTANCE,
            new SQLRequest("create table t4 (col1 integer, col2 string)")).actionGet();

        // create table causes a cluster event that will then cause to rebuild the information schema
        // wait until it's rebuild
        Thread.sleep(10);

        execute("select * from information_schema.tables");
        assertEquals(10L, response.rowCount());
    }


    @Test
    public void testSelectStarFromInformationSchemaTableWithOrderBy() throws Exception {
        execute("create table test (col1 integer primary key, col2 string)");
        execute("create table foo (col1 integer primary key, " +
                "col2 string) clustered by(col1) into 3 shards");
        ensureGreen();
        execute("select * from INFORMATION_SCHEMA.Tables where schema_name='doc' order by table_name asc");
        assertEquals(2L, response.rowCount());
        assertEquals("doc", response.rows()[0][0]);
        assertEquals("foo", response.rows()[0][1]);
        assertEquals(3, response.rows()[0][2]);
        assertEquals("1", response.rows()[0][3]);
        assertEquals("col1", response.rows()[0][4]);

        assertEquals("doc", response.rows()[1][0]);
        assertEquals("test", response.rows()[1][1]);
        assertEquals(5, response.rows()[1][2]);
        assertEquals("1", response.rows()[1][3]);
        assertEquals("col1", response.rows()[1][4]);
    }

    @Test
    public void testSelectStarFromInformationSchemaTableWithOrderByAndLimit() throws Exception {
        execute("create table test (col1 integer primary key, col2 string)");
        execute("create table foo (col1 integer primary key, col2 string) clustered into 3 shards");
        ensureGreen();
        execute("select * from INFORMATION_SCHEMA.Tables where schema_name='doc' order by table_name asc limit 1");
        assertEquals(1L, response.rowCount());
        assertEquals("doc", response.rows()[0][0]);
        assertEquals("foo", response.rows()[0][1]);
        assertEquals(3, response.rows()[0][2]);
        assertEquals("1", response.rows()[0][3]);
    }

    @Test
    public void testSelectStarFromInformationSchemaTableWithOrderByTwoColumnsAndLimit() throws Exception {
        execute("create table test (col1 integer primary key, col2 string) clustered into 1 shards");
        execute("create table foo (col1 integer primary key, col2 string) clustered into 3 shards");
        execute("create table bar (col1 integer primary key, col2 string) clustered into 3 shards");
        ensureGreen();
        execute("select table_name, number_of_shards from INFORMATION_SCHEMA.Tables where schema_name='doc' " +
                "order by number_of_shards desc, table_name asc limit 2");
        assertEquals(2L, response.rowCount());

        assertEquals("bar", response.rows()[0][0]);
        assertEquals(3, response.rows()[0][1]);
        assertEquals("foo", response.rows()[1][0]);
        assertEquals(3, response.rows()[1][1]);
    }

    @Test
    public void testSelectStarFromInformationSchemaTableWithOrderByAndLimitOffset() throws Exception {
        execute("create table test (col1 integer primary key, col2 string)");
        execute("create table foo (col1 integer primary key, col2 string) clustered into 3 shards");
        ensureGreen();
        execute("select * from INFORMATION_SCHEMA.Tables where schema_name='doc' order by table_name asc limit 1 offset 1");
        assertEquals(1L, response.rowCount());
        assertEquals("doc", response.rows()[0][0]);
        assertEquals("test", response.rows()[0][1]);
        assertEquals(5, response.rows()[0][2]);
        assertEquals("1", response.rows()[0][3]);
        assertEquals("col1", response.rows()[0][4]);
    }

    @Test
    public void testSelectFromInformationSchemaTable() throws Exception {
        execute("select TABLE_NAME from INFORMATION_SCHEMA.Tables where schema_name='doc'");
        assertEquals(0L, response.rowCount());

        execute("create table test (col1 integer primary key, col2 string)");
        ensureGreen();

        execute("select table_name, number_of_shards, number_of_replicas, " +
                "clustered_by from INFORMATION_SCHEMA.Tables where schema_name='doc' ");
        assertEquals(1L, response.rowCount());
        assertEquals("test", response.rows()[0][0]);
        assertEquals(5, response.rows()[0][1]);
        assertEquals("1", response.rows()[0][2]);
        assertEquals("col1", response.rows()[0][3]);
    }

    @Test
    public void testSelectBlobTablesFromInformationSchemaTable() throws Exception {
        execute("select TABLE_NAME from INFORMATION_SCHEMA.Tables where schema_name='blob'");
        assertEquals(0L, response.rowCount());

        // TODO: replace with "create blob table test" SQL stmt when supported
        prepareCreate(".blob_test")
                .setSettings(new HashMap<String, Object>(){{put("blobs_enabled", true);}})
                .execute().actionGet();
        ensureGreen();

        execute("select table_name, number_of_shards, number_of_replicas, " +
                "clustered_by from INFORMATION_SCHEMA.Tables where schema_name='blob' ");
        assertEquals(1L, response.rowCount());
        assertEquals("test", response.rows()[0][0]);
        assertEquals(5, response.rows()[0][1]);
        assertEquals("1", response.rows()[0][2]);
        assertEquals("digest", response.rows()[0][3]);
    }

    @Test
    public void testSelectStarFromInformationSchemaTable() throws Exception {
        execute("create table test (col1 integer, col2 string)");
        ensureGreen();
        execute("select * from INFORMATION_SCHEMA.Tables where schema_name='doc'");
        assertEquals(1L, response.rowCount());
        assertEquals("doc", response.rows()[0][0]);
        assertEquals("test", response.rows()[0][1]);
        assertEquals(5, response.rows()[0][2]);
        assertEquals("1", response.rows()[0][3]);
        assertEquals("_id", response.rows()[0][4]);
    }

    @Test
    public void testSelectFromTableConstraints() throws Exception {

        execute("select * from INFORMATION_SCHEMA.table_constraints order by schema_name asc, table_name asc");
        assertEquals(4L, response.rowCount());
        assertThat(response.cols(), arrayContaining("schema_name", "table_name", "constraint_name",
                "constraint_type"));
        assertThat(dotJoiner.join(response.rows()[0][0], response.rows()[0][1]), is("information_schema.columns"));
        assertThat(commaJoiner.join((Collection<?>)response.rows()[0][2]), is("schema_name, table_name, column_name"));
        assertThat(dotJoiner.join(response.rows()[1][0], response.rows()[1][1]), is("information_schema.tables"));
        assertThat(commaJoiner.join((Collection<?>)response.rows()[1][2]), is("schema_name, table_name"));
        assertThat(dotJoiner.join(response.rows()[2][0], response.rows()[2][1]), is("sys.nodes"));
        assertThat(commaJoiner.join((Collection<?>)response.rows()[2][2]), is("id"));
        assertThat(dotJoiner.join(response.rows()[3][0], response.rows()[3][1]), is("sys.shards"));
        assertThat(commaJoiner.join((Collection<?>)response.rows()[3][2]), is("schema_name, table_name, id"));

        execute("create table test (col1 integer primary key, col2 string)");
        ensureGreen();
        execute("select constraint_type, constraint_name, " +
                "table_name from information_schema.table_constraints where schema_name='doc'");
        assertEquals(1L, response.rowCount());
        assertEquals("PRIMARY_KEY", response.rows()[0][0]);
        assertThat(commaJoiner.join((Collection<?>) response.rows()[0][1]), is("col1"));
        assertEquals("test", response.rows()[0][2]);
    }

    @Test
    public void testRefreshTableConstraints() throws Exception {
        execute("create table test (col1 integer primary key, col2 string)");
        ensureGreen();
        execute("select table_name, constraint_name from INFORMATION_SCHEMA" +
                ".table_constraints where schema_name='doc'");
        assertEquals(1L, response.rowCount());
        assertEquals("test", response.rows()[0][0]);
        assertThat(commaJoiner.join((Collection<?>) response.rows()[0][1]), is("col1"));

        execute("create table test2 (col1a string primary key, col2a timestamp)");
        ensureGreen();
        execute("select table_name, constraint_name from INFORMATION_SCHEMA.table_constraints where schema_name='doc' order by table_name asc");

        assertEquals(2L, response.rowCount());
        assertEquals("test2", response.rows()[1][0]);
        assertThat(commaJoiner.join((Collection<?>) response.rows()[1][1]), is("col1a"));
    }

    /* TODO: enable when other information schema tables are implemented
    @Test
    public void testSelectFromRoutines() throws Exception {
        String stmt1 = "CREATE ANALYZER myAnalyzer WITH (" +
                "  TOKENIZER whitespace," +
                "  TOKEN_FILTERS (" +
                "     myTokenFilter WITH (" +
                "      type='snowball'," +
                "      language='german'" +
                "    )," +
                "    kstem" +
                "  )" +
                ")";
        execute(stmt1);
        execute("CREATE ANALYZER myOtherAnalyzer extends german (" +
                "  stopwords=[?, ?, ?]" +
                ")", new Object[]{"der", "die", "das"});
        ensureGreen();
        execute("SELECT * from INFORMATION_SCHEMA.routines where routine_definition != " +
                "'BUILTIN' order by routine_name asc");
        assertEquals(2L, response.rowCount());

        assertEquals("myanalyzer", response.rows()[0][0]);
        assertEquals("ANALYZER", response.rows()[0][1]);
        assertEquals("CREATE ANALYZER myanalyzer WITH (TOKENIZER whitespace, " +
                "TOKEN_FILTERS WITH (" +
                "mytokenfilter WITH (\"language\"='german',\"type\"='snowball'), kstem)" +
                ")", response.rows()[0][2]);

        assertEquals("myotheranalyzer", response.rows()[1][0]);
        assertEquals("ANALYZER", response.rows()[1][1]);
        assertEquals(
                "CREATE ANALYZER myotheranalyzer EXTENDS german WITH (\"stopwords\"=['der','die','das'])",
                response.rows()[1][2]
        );
    }

    @Test
    public void testSelectBuiltinAnalyzersFromRoutines() throws Exception {
        execute("SELECT routine_name from INFORMATION_SCHEMA.routines WHERE " +
               "\"routine_type\"='ANALYZER' AND \"routine_definition\"='BUILTIN' order by " +
                "routine_name desc");
        assertEquals(42L, response.rowCount());
        String[] analyzerNames = new String[response.rows().length];
        for (int i=0; i<response.rowCount(); i++) {
            analyzerNames[i] = (String)response.rows()[i][0];
        }
        assertEquals(
                "whitespace, turkish, thai, swedish, stop, standard_html_strip, standard, spanish, " +
                "snowball, simple, russian, romanian, portuguese, persian, pattern, " +
                "norwegian, latvian, keyword, italian, irish, indonesian, hungarian, " +
                "hindi, greek, german, galician, french, finnish, english, dutch, default, " +
                "danish, czech, classic, cjk, chinese, catalan, bulgarian, brazilian, " +
                "basque, armenian, arabic",
                Joiner.on(", ").join(analyzerNames)
        );
    }

    @Test
    public void testSelectBuiltinTokenizersFromRoutines() throws Exception {
        execute("SELECT routine_name from INFORMATION_SCHEMA.routines WHERE " +
                "\"routine_type\"='TOKENIZER' AND \"routine_definition\"='BUILTIN' order by " +
                "routine_name asc");
        assertEquals(13L, response.rowCount());
        String[] tokenizerNames = new String[response.rows().length];
        for (int i=0; i<response.rowCount(); i++) {
            tokenizerNames[i] = (String)response.rows()[i][0];
        }
        assertEquals(
                "classic, edgeNGram, edge_ngram, keyword, letter, lowercase, nGram, ngram, " +
                        "path_hierarchy, pattern, standard, uax_url_email, whitespace",
                Joiner.on(", ").join(tokenizerNames)
        );
    }

    @Test
    public void testSelectBuiltinTokenFiltersFromRoutines() throws Exception {
        execute("SELECT routine_name from INFORMATION_SCHEMA.routines WHERE " +
                "\"routine_type\"='TOKEN_FILTER' AND \"routine_definition\"='BUILTIN' order by " +
                "routine_name asc");
        assertEquals(44L, response.rowCount());
        String[] tokenFilterNames = new String[response.rows().length];
        for (int i=0; i<response.rowCount(); i++) {
            tokenFilterNames[i] = (String)response.rows()[i][0];
        }
        assertEquals(
                "arabic_normalization, arabic_stem, asciifolding, brazilian_stem, cjk_bigram, " +
                "cjk_width, classic, common_grams, czech_stem, dictionary_decompounder, " +
                "dutch_stem, edgeNGram, edge_ngram, elision, french_stem, german_stem, hunspell, " +
                "hyphenation_decompounder, keep, keyword_marker, keyword_repeat, kstem, " +
                "length, lowercase, nGram, ngram, pattern_capture, pattern_replace, " +
                "persian_normalization, porter_stem, reverse, russian_stem, shingle, " +
                "snowball, standard, stemmer, stemmer_override, stop, synonym, trim, " +
                "truncate, type_as_payload, unique, word_delimiter",
                Joiner.on(", ").join(tokenFilterNames)
        );
    }

    @Test
    public void testSelectBuiltinCharFiltersFromRoutines() throws Exception {
        execute("SELECT routine_name from INFORMATION_SCHEMA.routines WHERE " +
                "\"routine_type\"='CHAR_FILTER' AND \"routine_definition\"='BUILTIN' order by " +
                "routine_name asc");
        assertEquals(4L, response.rowCount());
        String[] charFilterNames = new String[response.rows().length];
        for (int i=0; i<response.rowCount(); i++) {
            charFilterNames[i] = (String)response.rows()[i][0];
        }
        assertEquals(
                "htmlStrip, html_strip, mapping, pattern_replace",
                Joiner.on(", ").join(charFilterNames)
        );
    }

    @Test
    public void testTableConstraintsWithOrderBy() throws Exception {
        execute("create table test1 (col11 integer primary key, col12 float)");
        execute("create table test2 (col21 double primary key, col22 string)");
        execute("create table abc (col31 integer primary key, col32 string)");

        ensureGreen();
        execute("select table_name from INFORMATION_SCHEMA.table_constraints ORDER BY " +
                "table_name");
        assertEquals(3L, response.rowCount());
        assertEquals(response.rows()[0][0], "abc");
        assertEquals(response.rows()[1][0], "test1");
        assertEquals(response.rows()[2][0], "test2");
    }
    */

    @Test
    public void testDefaultColumns() throws Exception {
        execute("select * from information_schema.columns order by schema_name, table_name");
        assertEquals(45L, response.rowCount());
    }

    @Test
    public void testColumnsColumns() throws Exception {
        execute("select * from information_schema.columns where schema_name='information_schema' and table_name='columns' order by ordinal_position asc");
        assertEquals(5, response.rowCount());
        short ordinal = 1;
        assertArrayEquals(response.rows()[0], new Object[]{"information_schema", "columns", "schema_name", ordinal++, "string"});
        assertArrayEquals(response.rows()[1], new Object[]{"information_schema", "columns", "table_name", ordinal++, "string"});
        assertArrayEquals(response.rows()[2], new Object[]{"information_schema", "columns", "column_name", ordinal++, "string"});
        assertArrayEquals(response.rows()[3], new Object[]{"information_schema", "columns", "ordinal_position", ordinal++, "short"});
        assertArrayEquals(response.rows()[4], new Object[]{"information_schema", "columns", "data_type", ordinal++, "string"});
    }

    @Test
    public void testSelectFromTableColumns() throws Exception {
        execute("create table test (col1 integer, col2 string index off, age integer)");
        ensureGreen();
        execute("select * from INFORMATION_SCHEMA.Columns where schema_name='doc'");
        assertEquals(3L, response.rowCount());
        assertEquals("doc", response.rows()[0][0]);
        assertEquals("test", response.rows()[0][1]);
        assertEquals("age", response.rows()[0][2]);
        short expected = 1;
        assertEquals(expected, response.rows()[0][3]);
        assertEquals("integer", response.rows()[0][4]);

        assertEquals("col1", response.rows()[1][2]);

        assertEquals("col2", response.rows()[2][2]);
    }

    @Test
    public void testSelectFromTableColumnsRefresh() throws Exception {
        execute("create table test (col1 integer, col2 string, age integer)");
        ensureGreen();
        execute("select table_name, column_name, " +
                "ordinal_position, data_type from INFORMATION_SCHEMA.Columns where schema_name='doc'");
        assertEquals(3L, response.rowCount());
        assertEquals("test", response.rows()[0][0]);

        execute("create table test2 (col1 integer, col2 string, age integer)");
        ensureGreen();
        execute("select table_name, column_name, " +
                "ordinal_position, data_type from INFORMATION_SCHEMA.Columns " +
                "where schema_name='doc' " +
                "order by table_name");

        assertEquals(6L, response.rowCount());
        assertEquals("test", response.rows()[0][0]);
        assertEquals("test2", response.rows()[4][0]);
    }

    @Test
    public void testSelectFromTableColumnsMultiField() throws Exception {
        execute("create table test (col1 string, col2 string," +
                "index col1_col2_ft using fulltext(col1, col2))");
        ensureGreen();
        execute("select table_name, column_name," +
                "ordinal_position, data_type from INFORMATION_SCHEMA.Columns where schema_name='doc'");
        assertEquals(2L, response.rowCount());

        assertEquals("test", response.rows()[0][0]);
        assertEquals("col1", response.rows()[0][1]);
        short expected = 1;
        assertEquals(expected, response.rows()[0][2]);
        assertEquals("string", response.rows()[0][3]);

        assertEquals("test", response.rows()[1][0]);
        assertEquals("col2", response.rows()[1][1]);
        expected = 2;
        assertEquals(expected, response.rows()[1][2]);
        assertEquals("string", response.rows()[1][3]);
    }

    /* TODO: enable when information_schema.indices is implemented
    @SuppressWarnings("unchecked")
    @Test
    public void testSelectFromTableIndices() throws Exception {
        execute("create table test (col1 string, col2 string, " +
                "col3 string index using fulltext, " +
                "col4 string index off, " +
                "index col1_col2_ft using fulltext(col1, col2) with(analyzer='english'))");
        ensureGreen();
        execute("select table_name, index_name, method, columns, properties " +
                "from INFORMATION_SCHEMA.Indices");
        assertEquals(4L, response.rowCount());

        assertEquals("test", response.rows()[0][0]);
        assertEquals("col1", response.rows()[0][1]);
        assertEquals("plain", response.rows()[0][2]);
        assertTrue(response.rows()[0][3] instanceof List);
        assertThat((List<String>) response.rows()[0][3], contains("col1"));
        assertEquals("", response.rows()[0][4]);

        assertEquals("test", response.rows()[1][0]);
        assertEquals("col2", response.rows()[1][1]);
        assertEquals("plain", response.rows()[1][2]);
        assertThat((List<String>) response.rows()[1][3], contains("col2"));
        assertEquals("", response.rows()[1][4]);

        assertEquals("test", response.rows()[2][0]);
        assertEquals("col1_col2_ft", response.rows()[2][1]);
        assertEquals("fulltext", response.rows()[2][2]);
        assertThat((List<String>) response.rows()[2][3], contains("col1", "col2"));
        assertEquals("analyzer=english", response.rows()[2][4]);

        assertEquals("test", response.rows()[3][0]);
        assertEquals("col3", response.rows()[3][1]);
        assertEquals("fulltext", response.rows()[3][2]);
        assertThat((List<String>) response.rows()[3][3], contains("col3"));
        assertEquals("analyzer=standard", response.rows()[3][4]);
    }*/

    @Test
    public void testGlobalAggregation() throws Exception {
        execute("select max(ordinal_position) from information_schema.columns");
        assertEquals(1, response.rowCount());

        short max_ordinal = 21;
        assertEquals(max_ordinal, response.rows()[0][0]);

        execute("create table t1 (id integer, col1 string)");
        ensureGreen();
        execute("select max(ordinal_position) from information_schema.columns where schema_name='doc'");
        assertEquals(1, response.rowCount());

        max_ordinal = 2;
        assertEquals(max_ordinal, response.rows()[0][0]);

    }

    @Test
    public void testGlobalAggregationMany() throws Exception {
        execute("create table t1 (id integer, col1 string) clustered into 10 shards with(number_of_replicas=14)");
        execute("create table t2 (id integer, col1 string) clustered into 5 shards with(number_of_replicas=7)");
        execute("create table t3 (id integer, col1 string) clustered into 3 shards with(number_of_replicas=2)");
        ensureYellow();
        execute("select min(number_of_shards), max(number_of_shards), avg(number_of_shards)," +
                "sum(number_of_shards) from information_schema.tables where schema_name='doc'");
        assertEquals(1, response.rowCount());

        assertEquals(3, response.rows()[0][0]);
        assertEquals(10, response.rows()[0][1]);
        assertEquals(6.0d, response.rows()[0][2]);
        assertEquals(18.0d, response.rows()[0][3]);
    }

    @Test
    public void testGlobalAggregationWithWhere() throws Exception {
        execute("create table t1 (id integer, col1 string) clustered into 10 shards with(number_of_replicas=14)");
        execute("create table t2 (id integer, col1 string) clustered into 5 shards with(number_of_replicas=7)");
        execute("create table t3 (id integer, col1 string) clustered into 3 shards with(number_of_replicas=2)");
        ensureYellow();
        execute("select min(number_of_shards), max(number_of_shards), avg(number_of_shards)," +
                "sum(number_of_shards) from information_schema.tables where schema_name='doc' and table_name != 't1'");
        assertEquals(1, response.rowCount());

        assertEquals(3, response.rows()[0][0]);
        assertEquals(5, response.rows()[0][1]);
        assertEquals(4.0d, response.rows()[0][2]);
        assertEquals(8.0d, response.rows()[0][3]);
    }

    @Test
    public void testGlobalAggregationWithAlias() throws Exception {
        execute("create table t1 (id integer, col1 string) clustered into 10 shards with(number_of_replicas=14)");
        execute("create table t2 (id integer, col1 string) clustered into 5 shards with(number_of_replicas=7)");
        execute("create table t3 (id integer, col1 string) clustered into 3 shards with(number_of_replicas=2)");
        ensureYellow();
        execute("select min(number_of_shards) as min_shards from information_schema.tables where table_name = 't1'");
        assertEquals(1, response.rowCount());

        assertEquals(10, response.rows()[0][0]);
    }

    @Test
    public void testGlobalCount() throws Exception {
        execute("create table t1 (id integer, col1 string) clustered into 10 shards with(number_of_replicas=14)");
        execute("create table t2 (id integer, col1 string) clustered into 5 shards with(number_of_replicas=7)");
        execute("create table t3 (id integer, col1 string) clustered into 3 shards with(number_of_replicas=2)");
        ensureYellow();
        execute("select count(*) from information_schema.tables");
        assertEquals(1, response.rowCount());
        assertEquals(9L, response.rows()[0][0]); // 3 + 5
    }

    @Test
    public void testGlobalCountDistinct() throws Exception {
        execute("create table t3 (id integer, col1 string)");
        ensureGreen();
        execute("select count(distinct schema_name) from information_schema.tables order by count(distinct schema_name)");
        assertEquals(1, response.rowCount());
        assertEquals(3L, response.rows()[0][0]);
    }

    @Test
    public void selectGlobalExpressionGroupBy() throws Exception {
        serviceSetup();
        execute("select table_name, count(column_name), sys.cluster.name " +
                        "from information_schema.columns where schema_name='doc' group by table_name, sys.cluster.name " +
                        "order by table_name");
        assertEquals(3, response.rowCount());

        assertEquals("t1", response.rows()[0][0]);
        assertEquals(2L, response.rows()[0][1]);
        assertEquals(cluster().clusterName(), response.rows()[0][2]);

        assertEquals("t2", response.rows()[1][0]);
        assertEquals(2L, response.rows()[1][1]);
        assertEquals(cluster().clusterName(), response.rows()[0][2]);

        assertEquals("t3", response.rows()[2][0]);
        assertEquals(2L, response.rows()[2][1]);
        assertEquals(cluster().clusterName(), response.rows()[0][2]);
    }

    @Test
    public void selectDynamicObjectAddsSubColumn() throws Exception {
        execute("create table t4 (" +
                "  title string," +
                "  stuff object(dynamic) as (" +
                "    first_name string," +
                "    last_name string" +
                "  )" +
                ") with (number_of_replicas=0)");
        ensureGreen();
        execute("select column_name, ordinal_position from information_schema.columns where table_name='t4'");
        assertEquals(4, response.rowCount());
        assertEquals("stuff", response.rows()[0][0]);
        short ordinal_position = 1;
        assertEquals(ordinal_position++, response.rows()[0][1]);

        assertEquals("stuff.first_name", response.rows()[1][0]);
        assertEquals(ordinal_position++, response.rows()[1][1]);

        assertEquals("stuff.last_name", response.rows()[2][0]);
        assertEquals(ordinal_position++, response.rows()[2][1]);

        assertEquals("title", response.rows()[3][0]);
        assertEquals(ordinal_position++, response.rows()[3][1]);

        execute("insert into t4 (stuff) values (?)", new Object[]{
                new HashMap<String, Object>() {{
                    put("first_name", "Douglas");
                    put("middle_name", "Noel");
                    put("last_name", "Adams");
                }}
        });
        refresh();

        execute("select column_name, ordinal_position from information_schema.columns where table_name='t4'");
        assertEquals(5, response.rowCount());
        assertEquals("stuff", response.rows()[0][0]);
        ordinal_position = 1;
        assertEquals(ordinal_position++, response.rows()[0][1]);

        assertEquals("stuff.first_name", response.rows()[1][0]);
        assertEquals(ordinal_position++, response.rows()[1][1]);

        assertEquals("stuff.last_name", response.rows()[2][0]);
        assertEquals(ordinal_position++, response.rows()[2][1]);

        assertEquals("stuff.middle_name", response.rows()[3][0]);
        assertEquals(ordinal_position++, response.rows()[3][1]);


        assertEquals("title", response.rows()[4][0]);
        assertEquals(ordinal_position++, response.rows()[4][1]);
    }

    @Test
    public void testAddColumnToIgnoredObject() throws Exception {
        execute("create table t4 (" +
                "  title string," +
                "  stuff object(ignored) as (" +
                "    first_name string," +
                "    last_name string" +
                "  )" +
                ")");
        ensureYellow();
        execute("select column_name, ordinal_position from information_schema.columns where table_name='t4'");
        assertEquals(4, response.rowCount());
        short ordinal_position = 1;
        assertEquals("stuff", response.rows()[0][0]);
        assertEquals(ordinal_position++, response.rows()[0][1]);

        assertEquals("stuff.first_name", response.rows()[1][0]);
        assertEquals(ordinal_position++, response.rows()[1][1]);

        assertEquals("stuff.last_name", response.rows()[2][0]);
        assertEquals(ordinal_position++, response.rows()[2][1]);

        assertEquals("title", response.rows()[3][0]);
        assertEquals(ordinal_position++, response.rows()[3][1]);

        execute("insert into t4 (stuff) values (?)", new Object[]{
                new HashMap<String, Object>() {{
                    put("first_name", "Douglas");
                    put("middle_name", "Noel");
                    put("last_name", "Adams");
                }}
        });

        execute("select column_name, ordinal_position from information_schema.columns where table_name='t4'");
        assertEquals(4, response.rowCount());

        ordinal_position = 1;
        assertEquals("stuff", response.rows()[0][0]);
        assertEquals(ordinal_position++, response.rows()[0][1]);

        assertEquals("stuff.first_name", response.rows()[1][0]);
        assertEquals(ordinal_position++, response.rows()[1][1]);

        assertEquals("stuff.last_name", response.rows()[2][0]);
        assertEquals(ordinal_position++, response.rows()[2][1]);

        assertEquals("title", response.rows()[3][0]);
        assertEquals(ordinal_position++, response.rows()[3][1]);
    }

    @Test
    public void testUnknownTypes() throws Exception {
        new Setup(this).setUpObjectMappingWithUnknownTypes();
        execute("select * from information_schema.columns where table_name='ut' order by column_name");
        assertEquals(2, response.rowCount());

        assertEquals("name", response.rows()[0][2]);
        short ordinal_position = 1;
        assertEquals(ordinal_position, response.rows()[0][3]);
        assertEquals("string", response.rows()[0][4]);

        assertEquals("population", response.rows()[1][2]);
        ordinal_position = 2;
        assertEquals(ordinal_position, response.rows()[1][3]);
        assertEquals("long", response.rows()[1][4]);

        // TODO: enable when information_schema.indices is implemented
        //execute("select * from information_schema.indices where table_name='ut' order by index_name");
        //assertEquals(2, response.rowCount());
        //assertEquals("name", response.rows()[0][1]);
        //assertEquals("population", response.rows()[1][1]);

        execute("select sum(number_of_shards) from information_schema.tables");
        assertEquals(1, response.rowCount());
    }
}
