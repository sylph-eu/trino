/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.tests;

import io.trino.tempto.ProductTest;
import io.trino.tempto.Requires;
import io.trino.tempto.fulfillment.table.hive.tpch.ImmutableTpchTablesRequirements.ImmutableNationTable;
import org.testng.annotations.Test;

import static io.prestosql.tests.TestGroups.CREATE_TABLE;
import static io.trino.tempto.assertions.QueryAssert.assertThat;
import static io.trino.tempto.query.QueryExecutor.query;
import static java.lang.String.format;

@Requires(ImmutableNationTable.class)
public class TestCreateTable
        extends ProductTest
{
    @Test(groups = CREATE_TABLE)
    public void shouldCreateTableAsSelect()
    {
        String tableName = "create_table_as_select";
        query(format("DROP TABLE IF EXISTS %s", tableName));
        query(format("CREATE TABLE %s(nationkey, name) AS SELECT n_nationkey, n_name FROM nation", tableName));
        assertThat(query(format("SELECT * FROM %s", tableName))).hasRowsCount(25);
    }

    @Test(groups = CREATE_TABLE)
    public void shouldCreateTableAsEmptySelect()
    {
        String tableName = "create_table_as_empty_select";
        query(format("DROP TABLE IF EXISTS %s", tableName));
        query(format("CREATE TABLE %s(nationkey, name) AS SELECT n_nationkey, n_name FROM nation WHERE 0 is NULL", tableName));
        assertThat(query(format("SELECT nationkey, name FROM %s", tableName))).hasRowsCount(0);
    }
}
