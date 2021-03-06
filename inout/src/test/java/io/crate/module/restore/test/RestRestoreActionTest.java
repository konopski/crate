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

package io.crate.module.restore.test;

import io.crate.action.dump.DumpAction;
import io.crate.action.export.ExportAction;
import io.crate.action.export.ExportRequest;
import io.crate.action.export.ExportResponse;
import io.crate.action.import_.ImportRequest;
import io.crate.action.import_.ImportResponse;
import io.crate.action.restore.RestoreAction;
import io.crate.module.AbstractRestActionTest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.crate.test.integration.PathAccessor.stringFromPath;

public class RestRestoreActionTest extends AbstractRestActionTest {

    /**
     * Restore previously dumped data from the default location
     */
    @Test
    public void testRestoreDumpedData() throws Exception {
        deleteDefaultDir();
        deleteAll();
        setUpSecondNode();
        // create sample data
        prepareCreate("users").setSettings(
            ImmutableSettings.builder().loadFromClasspath("essetup/settings/test_b.json").build()
        ).addMapping("d", stringFromPath("/essetup/mappings/test_b.json", getClass())).execute().actionGet();

        waitForRelocation(ClusterHealthStatus.GREEN);

        client().index(new IndexRequest("users", "d", "1").source("{\"name\": \"item1\"}")).actionGet();
        client().index(new IndexRequest("users", "d", "2").source("{\"name\": \"item2\"}")).actionGet();
        refresh();

        client().admin().cluster().prepareHealth().setWaitForGreenStatus().
            setWaitForNodes("2").setWaitForRelocatingShards(0).execute().actionGet();

        // dump data and recreate empty index
        executeDumpRequest("");

        // delete all
        deleteAll();
        client().admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();

        // run restore without pyload relative directory
        ImportResponse response = executeRestoreRequest("");
        List<Map<String, Object>> imports = getImports(response);
        refresh();
        assertEquals(2, imports.size());

        assertTrue(existsWithField("1", "name", "item1", "users", "d"));
        assertTrue(existsWithField("2", "name", "item2", "users", "d"));

        ClusterStateRequest clusterStateRequest = Requests.clusterStateRequest().metaData(true).indices("users");
        IndexMetaData metaData = client().admin().cluster().state(clusterStateRequest).actionGet().getState().metaData().index("users");
        assertEquals("{\"d\":{\"properties\":{\"name\":{\"type\":\"string\",\"index\":\"not_analyzed\",\"store\":true}}}}",
                metaData.mappings().get("d").source().toString());
        assertEquals(2, metaData.numberOfShards());
        assertEquals(0, metaData.numberOfReplicas());
    }


    private boolean existsWithField(String id, String field, String value, String index, String type) {
        GetRequestBuilder rb = new GetRequestBuilder(client(), index);
        GetResponse res = rb.setType(type).setId(id).execute().actionGet();
        return res.isExists() && res.getSourceAsMap().get(field).equals(value);
    }

    private static List<Map<String, Object>> getImports(ImportResponse resp) {
        return get(resp, "imports");
    }

    private static List<Map<String, Object>> get(ImportResponse resp, String key) {
        Map<String, Object> res = null;
        try {
            res = toMap(resp);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return (List<Map<String, Object>>) res.get(key);
    }

    private ImportResponse executeRestoreRequest(String source) {
        ImportRequest request = new ImportRequest();
        request.source(source);
        return client().execute(RestoreAction.INSTANCE, request).actionGet();
    }

    private ExportResponse executeDumpRequest(String source) {
        ExportRequest exportRequest = new ExportRequest();
        exportRequest.source(source);
        return client().execute(DumpAction.INSTANCE, exportRequest).actionGet();
    }

    /**
     * Helper method to delete an already existing dump directory
     */
    private void deleteDefaultDir() {
        ExportRequest exportRequest = new ExportRequest();
        exportRequest.source("{\"output_file\": \"dump\", \"fields\": [\"_source\", \"_id\", \"_index\", \"_type\"], \"force_overwrite\": true, \"explain\": true}");
        ExportResponse explain = client().execute(ExportAction.INSTANCE, exportRequest).actionGet();

        try {
            Map<String, Object> res = toMap(explain);
            List<Map<String, String>> list = (ArrayList<Map<String, String>>) res.get("exports");
            for (Map<String, String> map : list) {
                File defaultDir = new File(map.get("output_file").toString());
                if (defaultDir.exists()) {
                    for (File c : defaultDir.listFiles()) {
                        c.delete();
                    }
                    defaultDir.delete();
                }
            }
        } catch (IOException e) {
        }
    }

}
