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

package io.crate.analyze;

import io.crate.metadata.MetaDataModule;
import io.crate.metadata.TableIdent;
import io.crate.metadata.blob.BlobSchemaInfo;
import io.crate.metadata.blob.BlobTableInfo;
import io.crate.metadata.sys.MetaDataSysModule;
import io.crate.metadata.table.SchemaInfo;
import org.elasticsearch.common.inject.Module;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RefreshAnalyzerTest extends BaseAnalyzerTest {
    private static TableIdent TEST_BLOB_TABLE_IDENT = new TableIdent("blob", "blobs");

    static class TestMetaDataModule extends MetaDataModule {

        @Override
        protected void bindSchemas() {
            super.bindSchemas();
            SchemaInfo schemaInfo = mock(SchemaInfo.class);
            BlobTableInfo blobTableInfo = mock(BlobTableInfo.class);
            when(blobTableInfo.ident()).thenReturn(TEST_BLOB_TABLE_IDENT);
            when(schemaInfo.getTableInfo(TEST_BLOB_TABLE_IDENT.name())).thenReturn(blobTableInfo);
            schemaBinder.addBinding(BlobSchemaInfo.NAME).toInstance(schemaInfo);
        }
    }

    @Override
    protected List<Module> getModules() {
        List<Module> modules = super.getModules();
        modules.addAll(Arrays.<Module>asList(
                new TestModule(),
                new TestMetaDataModule(),
                new MetaDataSysModule()
        ));
        return modules;
    }

    @Test
    public void testRefreshSystemTable() throws Exception {
        RefreshTableAnalysis analysis = (RefreshTableAnalysis)analyze("refresh table sys.shards");
        assertTrue(analysis.schema().systemSchema());
        assertThat(analysis.table().ident().name(), is("shards"));
    }

    @Test
    public void testRefreshBlobTable() throws Exception {
        RefreshTableAnalysis analysis = (RefreshTableAnalysis)analyze("refresh table blob.blobs");
        assertThat(analysis.table().ident().schema(), is("blob"));
        assertThat(analysis.table().ident().name(), is("blobs"));

    }
}
