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
package io.trino.type;

import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.airlift.slice.XxHash64;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.block.BlockBuilderStatus;
import io.prestosql.spi.block.Int128ArrayBlockBuilder;
import io.prestosql.spi.block.PageBuilderStatus;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.function.BlockIndex;
import io.prestosql.spi.function.BlockPosition;
import io.prestosql.spi.function.ScalarOperator;
import io.prestosql.spi.type.AbstractType;
import io.prestosql.spi.type.FixedWidthType;
import io.prestosql.spi.type.StandardTypes;
import io.prestosql.spi.type.TypeOperatorDeclaration;
import io.prestosql.spi.type.TypeOperators;
import io.prestosql.spi.type.TypeSignature;

import java.util.UUID;

import static io.airlift.slice.SizeOf.SIZE_OF_LONG;
import static io.prestosql.spi.block.Int128ArrayBlock.INT128_BYTES;
import static io.prestosql.spi.function.OperatorType.COMPARISON;
import static io.prestosql.spi.function.OperatorType.EQUAL;
import static io.prestosql.spi.function.OperatorType.XX_HASH_64;
import static io.prestosql.spi.type.TypeOperatorDeclaration.extractOperatorDeclaration;
import static java.lang.Long.reverseBytes;
import static java.lang.invoke.MethodHandles.lookup;

/**
 * UUIDs are encoded in big-endian representation (the bytes are stored in
 * the same order as they appear when a UUID is printed in hexadecimal).
 */
public class UuidType
        extends AbstractType
        implements FixedWidthType
{
    private static final TypeOperatorDeclaration TYPE_OPERATOR_DECLARATION = extractOperatorDeclaration(UuidType.class, lookup(), Slice.class);

    public static final UuidType UUID = new UuidType();

    private UuidType()
    {
        super(new TypeSignature(StandardTypes.UUID), Slice.class);
    }

    @Override
    public int getFixedSize()
    {
        return INT128_BYTES;
    }

    @Override
    public BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries, int expectedBytesPerEntry)
    {
        int maxBlockSizeInBytes;
        if (blockBuilderStatus == null) {
            maxBlockSizeInBytes = PageBuilderStatus.DEFAULT_MAX_PAGE_SIZE_IN_BYTES;
        }
        else {
            maxBlockSizeInBytes = blockBuilderStatus.getMaxPageSizeInBytes();
        }
        return new Int128ArrayBlockBuilder(
                blockBuilderStatus,
                Math.min(expectedEntries, maxBlockSizeInBytes / getFixedSize()));
    }

    @Override
    public BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries)
    {
        return createBlockBuilder(blockBuilderStatus, expectedEntries, getFixedSize());
    }

    @Override
    public BlockBuilder createFixedSizeBlockBuilder(int positionCount)
    {
        return new Int128ArrayBlockBuilder(null, positionCount);
    }

    @Override
    public boolean isComparable()
    {
        return true;
    }

    @Override
    public boolean isOrderable()
    {
        return true;
    }

    @Override
    public TypeOperatorDeclaration getTypeOperatorDeclaration(TypeOperators typeOperators)
    {
        return TYPE_OPERATOR_DECLARATION;
    }

    @Override
    public Object getObjectValue(ConnectorSession session, Block block, int position)
    {
        if (block.isNull(position)) {
            return null;
        }
        long high = reverseBytes(block.getLong(position, 0));
        long low = reverseBytes(block.getLong(position, SIZE_OF_LONG));
        return new UUID(high, low).toString();
    }

    @Override
    public void appendTo(Block block, int position, BlockBuilder blockBuilder)
    {
        if (block.isNull(position)) {
            blockBuilder.appendNull();
        }
        else {
            blockBuilder.writeLong(block.getLong(position, 0));
            blockBuilder.writeLong(block.getLong(position, SIZE_OF_LONG));
            blockBuilder.closeEntry();
        }
    }

    @Override
    public void writeSlice(BlockBuilder blockBuilder, Slice value)
    {
        writeSlice(blockBuilder, value, 0, value.length());
    }

    @Override
    public void writeSlice(BlockBuilder blockBuilder, Slice value, int offset, int length)
    {
        if (length != INT128_BYTES) {
            throw new IllegalStateException("Expected entry size to be exactly " + INT128_BYTES + " but was " + length);
        }
        blockBuilder.writeLong(value.getLong(offset));
        blockBuilder.writeLong(value.getLong(offset + SIZE_OF_LONG));
        blockBuilder.closeEntry();
    }

    @Override
    public final Slice getSlice(Block block, int position)
    {
        return Slices.wrappedLongArray(
                block.getLong(position, 0),
                block.getLong(position, SIZE_OF_LONG));
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(Slice left, Slice right)
    {
        return equal(
                left.getLong(0),
                left.getLong(SIZE_OF_LONG),
                right.getLong(0),
                right.getLong(SIZE_OF_LONG));
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        return equal(
                leftBlock.getLong(leftPosition, 0),
                leftBlock.getLong(leftPosition, SIZE_OF_LONG),
                rightBlock.getLong(rightPosition, 0),
                rightBlock.getLong(rightPosition, SIZE_OF_LONG));
    }

    private static boolean equal(long leftLow, long leftHigh, long rightLow, long rightHigh)
    {
        return leftLow == rightLow && leftHigh == rightHigh;
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(Slice value)
    {
        return xxHash64(value.getLong(0), value.getLong(SIZE_OF_LONG));
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(@BlockPosition Block block, @BlockIndex int position)
    {
        return xxHash64(block.getLong(position, 0), block.getLong(position, SIZE_OF_LONG));
    }

    private static long xxHash64(long low, long high)
    {
        return XxHash64.hash(low) ^ XxHash64.hash(high);
    }

    @ScalarOperator(COMPARISON)
    private static long comparisonOperator(Slice left, Slice right)
    {
        return compareLittleEndian(
                left.getLong(0),
                left.getLong(SIZE_OF_LONG),
                right.getLong(0),
                right.getLong(SIZE_OF_LONG));
    }

    @ScalarOperator(COMPARISON)
    private static long comparisonOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        return compareLittleEndian(
                leftBlock.getLong(leftPosition, 0),
                leftBlock.getLong(leftPosition, SIZE_OF_LONG),
                rightBlock.getLong(rightPosition, 0),
                rightBlock.getLong(rightPosition, SIZE_OF_LONG));
    }

    private static int compareLittleEndian(long leftLow64le, long leftHigh64le, long rightLow64le, long rightHigh64le)
    {
        int compare = Long.compareUnsigned(reverseBytes(leftLow64le), reverseBytes(rightLow64le));
        if (compare != 0) {
            return compare;
        }
        return Long.compareUnsigned(reverseBytes(leftHigh64le), reverseBytes(rightHigh64le));
    }
}
