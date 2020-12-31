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
package io.trino.operator.scalar;

import io.prestosql.operator.aggregation.TypedSet;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.function.Convention;
import io.prestosql.spi.function.Description;
import io.prestosql.spi.function.OperatorDependency;
import io.prestosql.spi.function.ScalarFunction;
import io.prestosql.spi.function.SqlType;
import io.prestosql.spi.function.TypeParameter;
import io.prestosql.spi.type.Type;
import io.prestosql.type.BlockTypeOperators.BlockPositionEqual;
import io.prestosql.type.BlockTypeOperators.BlockPositionHashCode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.prestosql.operator.aggregation.TypedSet.createEqualityTypedSet;
import static io.prestosql.spi.function.InvocationConvention.InvocationArgumentConvention.BLOCK_POSITION;
import static io.prestosql.spi.function.InvocationConvention.InvocationReturnConvention.FAIL_ON_NULL;
import static io.prestosql.spi.function.InvocationConvention.InvocationReturnConvention.NULLABLE_RETURN;
import static io.prestosql.spi.function.OperatorType.EQUAL;
import static io.prestosql.spi.function.OperatorType.HASH_CODE;
import static io.prestosql.spi.type.BigintType.BIGINT;

@ScalarFunction("array_union")
@Description("Union elements of the two given arrays")
public final class ArrayUnionFunction
{
    private ArrayUnionFunction() {}

    @TypeParameter("E")
    @SqlType("array(E)")
    public static Block union(
            @TypeParameter("E") Type type,
            @OperatorDependency(
                    operator = EQUAL,
                    argumentTypes = {"E", "E"},
                    convention = @Convention(arguments = {BLOCK_POSITION, BLOCK_POSITION}, result = NULLABLE_RETURN)) BlockPositionEqual elementEqual,
            @OperatorDependency(
                    operator = HASH_CODE,
                    argumentTypes = "E",
                    convention = @Convention(arguments = BLOCK_POSITION, result = FAIL_ON_NULL)) BlockPositionHashCode elementHashCode,
            @SqlType("array(E)") Block leftArray,
            @SqlType("array(E)") Block rightArray)
    {
        int leftArrayCount = leftArray.getPositionCount();
        int rightArrayCount = rightArray.getPositionCount();
        TypedSet typedSet = createEqualityTypedSet(type, elementEqual, elementHashCode, leftArrayCount + rightArrayCount, "array_union");
        BlockBuilder distinctElementBlockBuilder = type.createBlockBuilder(null, leftArrayCount + rightArrayCount);
        appendTypedArray(leftArray, type, typedSet, distinctElementBlockBuilder);
        appendTypedArray(rightArray, type, typedSet, distinctElementBlockBuilder);

        return distinctElementBlockBuilder.build();
    }

    private static void appendTypedArray(Block array, Type type, TypedSet typedSet, BlockBuilder blockBuilder)
    {
        for (int i = 0; i < array.getPositionCount(); i++) {
            if (!typedSet.contains(array, i)) {
                typedSet.add(array, i);
                type.appendTo(array, i, blockBuilder);
            }
        }
    }

    @SqlType("array(bigint)")
    public static Block bigintUnion(@SqlType("array(bigint)") Block leftArray, @SqlType("array(bigint)") Block rightArray)
    {
        int leftArrayCount = leftArray.getPositionCount();
        int rightArrayCount = rightArray.getPositionCount();
        LongSet set = new LongOpenHashSet(leftArrayCount + rightArrayCount);
        BlockBuilder distinctElementBlockBuilder = BIGINT.createBlockBuilder(null, leftArrayCount + rightArrayCount);
        AtomicBoolean containsNull = new AtomicBoolean(false);
        appendBigintArray(leftArray, containsNull, set, distinctElementBlockBuilder);
        appendBigintArray(rightArray, containsNull, set, distinctElementBlockBuilder);

        return distinctElementBlockBuilder.build();
    }

    private static void appendBigintArray(Block array, AtomicBoolean containsNull, LongSet set, BlockBuilder blockBuilder)
    {
        for (int i = 0; i < array.getPositionCount(); i++) {
            if (array.isNull(i)) {
                if (!containsNull.get()) {
                    containsNull.set(true);
                    blockBuilder.appendNull();
                }
                continue;
            }
            long value = BIGINT.getLong(array, i);
            if (set.add(value)) {
                BIGINT.writeLong(blockBuilder, value);
            }
        }
    }
}
