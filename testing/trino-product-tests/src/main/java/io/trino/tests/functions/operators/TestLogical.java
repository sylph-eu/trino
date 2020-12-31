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
package io.trino.tests.functions.operators;

import io.trino.tempto.ProductTest;
import org.testng.annotations.Test;

import static io.prestosql.tests.TestGroups.LOGICAL;
import static io.prestosql.tests.TestGroups.QUERY_ENGINE;
import static io.trino.tempto.assertions.QueryAssert.Row.row;
import static io.trino.tempto.assertions.QueryAssert.assertThat;
import static io.trino.tempto.query.QueryExecutor.query;

public class TestLogical
        extends ProductTest
{
    @Test(groups = {LOGICAL, QUERY_ENGINE})
    public void testLogicalOperatorsExists()
    {
        assertThat(query("select true AND true")).containsExactly(row(true));
        assertThat(query("select true OR false")).containsExactly(row(true));
        assertThat(query("select 1 in (1, 2, 3)")).containsExactly(row(true));
        assertThat(query("select 'ala ma kota' like 'ala%'")).containsExactly(row(true));
        assertThat(query("select NOT true")).containsExactly(row(false));
        assertThat(query("select null is null")).containsExactly(row(true));
    }
}
