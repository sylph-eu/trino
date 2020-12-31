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
package io.trino.spi.type;

import io.airlift.slice.XxHash64;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.block.BlockBuilderStatus;
import io.prestosql.spi.block.Int96ArrayBlockBuilder;
import io.prestosql.spi.block.PageBuilderStatus;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.function.BlockIndex;
import io.prestosql.spi.function.BlockPosition;
import io.prestosql.spi.function.ScalarOperator;

import static io.airlift.slice.SizeOf.SIZE_OF_LONG;
import static io.prestosql.spi.function.OperatorType.COMPARISON;
import static io.prestosql.spi.function.OperatorType.EQUAL;
import static io.prestosql.spi.function.OperatorType.LESS_THAN;
import static io.prestosql.spi.function.OperatorType.LESS_THAN_OR_EQUAL;
import static io.prestosql.spi.function.OperatorType.XX_HASH_64;
import static io.prestosql.spi.type.DateTimeEncoding.packDateTimeWithZone;
import static io.prestosql.spi.type.DateTimeEncoding.unpackMillisUtc;
import static io.prestosql.spi.type.DateTimeEncoding.unpackZoneKey;
import static io.prestosql.spi.type.TypeOperatorDeclaration.extractOperatorDeclaration;
import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.lookup;

/**
 * The representation is a 96-bit value that contains the milliseconds from the epoch + session key
 * in the first long and the fractional increment in the remaining integer, as a number of picoseconds
 * additional to the epoch millisecond.
 */
class LongTimestampWithTimeZoneType
        extends TimestampWithTimeZoneType
{
    private static final TypeOperatorDeclaration TYPE_OPERATOR_DECLARATION = extractOperatorDeclaration(LongTimestampWithTimeZoneType.class, lookup(), LongTimestampWithTimeZone.class);

    public LongTimestampWithTimeZoneType(int precision)
    {
        super(precision, LongTimestampWithTimeZone.class);

        if (precision < MAX_SHORT_PRECISION + 1 || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(format("Precision must be in the range [%s, %s]", MAX_SHORT_PRECISION + 1, MAX_PRECISION));
        }
    }

    @Override
    public TypeOperatorDeclaration getTypeOperatorDeclaration(TypeOperators typeOperators)
    {
        return TYPE_OPERATOR_DECLARATION;
    }

    @Override
    public int getFixedSize()
    {
        return Long.BYTES + Integer.BYTES;
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
        return new Int96ArrayBlockBuilder(
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
        return new Int96ArrayBlockBuilder(null, positionCount);
    }

    @Override
    public void appendTo(Block block, int position, BlockBuilder blockBuilder)
    {
        if (block.isNull(position)) {
            blockBuilder.appendNull();
        }
        else {
            blockBuilder.writeLong(getPackedEpochMillis(block, position));
            blockBuilder.writeInt(getFraction(block, position));
            blockBuilder.closeEntry();
        }
    }

    @Override
    public Object getObject(Block block, int position)
    {
        long packedEpochMillis = getPackedEpochMillis(block, position);
        int fraction = getFraction(block, position);

        return LongTimestampWithTimeZone.fromEpochMillisAndFraction(unpackMillisUtc(packedEpochMillis), fraction, unpackZoneKey(packedEpochMillis));
    }

    @Override
    public void writeObject(BlockBuilder blockBuilder, Object value)
    {
        LongTimestampWithTimeZone timestamp = (LongTimestampWithTimeZone) value;

        blockBuilder.writeLong(packDateTimeWithZone(timestamp.getEpochMillis(), timestamp.getTimeZoneKey()));
        blockBuilder.writeInt(timestamp.getPicosOfMilli());
        blockBuilder.closeEntry();
    }

    @Override
    public Object getObjectValue(ConnectorSession session, Block block, int position)
    {
        if (block.isNull(position)) {
            return null;
        }

        long packedEpochMillis = getPackedEpochMillis(block, position);
        int fraction = getFraction(block, position);

        return SqlTimestampWithTimeZone.newInstance(getPrecision(), unpackMillisUtc(packedEpochMillis), fraction, unpackZoneKey(packedEpochMillis));
    }

    private static long getPackedEpochMillis(Block block, int position)
    {
        return block.getLong(position, 0);
    }

    private static long getEpochMillis(Block block, int position)
    {
        return unpackMillisUtc(getPackedEpochMillis(block, position));
    }

    private static int getFraction(Block block, int position)
    {
        return block.getInt(position, SIZE_OF_LONG);
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(LongTimestampWithTimeZone left, LongTimestampWithTimeZone right)
    {
        return equal(
                left.getEpochMillis(),
                left.getPicosOfMilli(),
                right.getEpochMillis(),
                right.getPicosOfMilli());
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        return equal(
                getEpochMillis(leftBlock, leftPosition),
                getFraction(leftBlock, leftPosition),
                getEpochMillis(rightBlock, rightPosition),
                getFraction(rightBlock, rightPosition));
    }

    private static boolean equal(long leftEpochMillis, int leftFraction, long rightEpochMillis, int rightFraction)
    {
        return leftEpochMillis == rightEpochMillis &&
                leftFraction == rightFraction;
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(LongTimestampWithTimeZone value)
    {
        return xxHash64(value.getEpochMillis(), value.getPicosOfMilli());
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(@BlockPosition Block block, @BlockIndex int position)
    {
        return xxHash64(
                getEpochMillis(block, position),
                getFraction(block, position));
    }

    private static long xxHash64(long epochMillis, int fraction)
    {
        return XxHash64.hash(epochMillis) ^ XxHash64.hash(fraction);
    }

    @ScalarOperator(COMPARISON)
    private static long comparisonOperator(LongTimestampWithTimeZone left, LongTimestampWithTimeZone right)
    {
        return comparison(left.getEpochMillis(), left.getPicosOfMilli(), right.getEpochMillis(), right.getPicosOfMilli());
    }

    @ScalarOperator(COMPARISON)
    private static long comparisonOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        return comparison(
                getEpochMillis(leftBlock, leftPosition),
                getFraction(leftBlock, leftPosition),
                getEpochMillis(rightBlock, rightPosition),
                getFraction(rightBlock, rightPosition));
    }

    private static int comparison(long leftEpochMillis, int leftPicosOfMilli, long rightEpochMillis, int rightPicosOfMilli)
    {
        int value = Long.compare(leftEpochMillis, rightEpochMillis);
        if (value != 0) {
            return value;
        }
        return Integer.compare(leftPicosOfMilli, rightPicosOfMilli);
    }

    @ScalarOperator(LESS_THAN)
    private static boolean lessThanOperator(LongTimestampWithTimeZone left, LongTimestampWithTimeZone right)
    {
        return lessThan(left.getEpochMillis(), left.getPicosOfMilli(), right.getEpochMillis(), right.getPicosOfMilli());
    }

    @ScalarOperator(LESS_THAN)
    private static boolean lessThanOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        return lessThan(
                getEpochMillis(leftBlock, leftPosition),
                getFraction(leftBlock, leftPosition),
                getEpochMillis(rightBlock, rightPosition),
                getFraction(rightBlock, rightPosition));
    }

    private static boolean lessThan(long leftEpochMillis, int leftPicosOfMilli, long rightEpochMillis, int rightPicosOfMilli)
    {
        return (leftEpochMillis < rightEpochMillis) ||
                ((leftEpochMillis == rightEpochMillis) && (leftPicosOfMilli < rightPicosOfMilli));
    }

    @ScalarOperator(LESS_THAN_OR_EQUAL)
    private static boolean lessThanOrEqualOperator(LongTimestampWithTimeZone left, LongTimestampWithTimeZone right)
    {
        return lessThanOrEqual(left.getEpochMillis(), left.getPicosOfMilli(), right.getEpochMillis(), right.getPicosOfMilli());
    }

    @ScalarOperator(LESS_THAN_OR_EQUAL)
    private static boolean lessThanOrEqualOperator(@BlockPosition Block leftBlock, @BlockIndex int leftPosition, @BlockPosition Block rightBlock, @BlockIndex int rightPosition)
    {
        return lessThanOrEqual(
                getEpochMillis(leftBlock, leftPosition),
                getFraction(leftBlock, leftPosition),
                getEpochMillis(rightBlock, rightPosition),
                getFraction(rightBlock, rightPosition));
    }

    private static boolean lessThanOrEqual(long leftEpochMillis, int leftPicosOfMilli, long rightEpochMillis, int rightPicosOfMilli)
    {
        return (leftEpochMillis < rightEpochMillis) ||
                ((leftEpochMillis == rightEpochMillis) && (leftPicosOfMilli <= rightPicosOfMilli));
    }
}
