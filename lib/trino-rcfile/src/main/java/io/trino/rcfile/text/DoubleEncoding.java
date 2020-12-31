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
package io.trino.rcfile.text;

import io.airlift.slice.Slice;
import io.airlift.slice.SliceOutput;
import io.prestosql.rcfile.ColumnData;
import io.prestosql.rcfile.EncodeOutput;
import io.prestosql.rcfile.RcFileCorruptionException;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.type.Type;

public class DoubleEncoding
        implements TextColumnEncoding
{
    private final Type type;
    private final Slice nullSequence;
    private final StringBuilder buffer = new StringBuilder();

    public DoubleEncoding(Type type, Slice nullSequence)
    {
        this.type = type;
        this.nullSequence = nullSequence;
    }

    @Override
    public void encodeColumn(Block block, SliceOutput output, EncodeOutput encodeOutput)
    {
        for (int position = 0; position < block.getPositionCount(); position++) {
            if (block.isNull(position)) {
                output.writeBytes(nullSequence);
            }
            else {
                encodeValue(block, position, output);
            }
            encodeOutput.closeEntry();
        }
    }

    @Override
    public void encodeValueInto(int depth, Block block, int position, SliceOutput output)
    {
        encodeValue(block, position, output);
    }

    private void encodeValue(Block block, int position, SliceOutput output)
    {
        double value = type.getDouble(block, position);
        buffer.setLength(0);
        buffer.append(value);
        for (int index = 0; index < buffer.length(); index++) {
            output.writeByte(buffer.charAt(index));
        }
    }

    @Override
    public Block decodeColumn(ColumnData columnData)
            throws RcFileCorruptionException
    {
        int size = columnData.rowCount();
        BlockBuilder builder = type.createBlockBuilder(null, size);

        Slice slice = columnData.getSlice();
        for (int i = 0; i < size; i++) {
            int offset = columnData.getOffset(i);
            int length = columnData.getLength(i);
            if (length == 0 || nullSequence.equals(0, nullSequence.length(), slice, offset, length)) {
                builder.appendNull();
            }
            else {
                type.writeDouble(builder, parseDouble(slice, offset, length));
            }
        }
        return builder.build();
    }

    @Override
    public void decodeValueInto(int depth, BlockBuilder builder, Slice slice, int offset, int length)
            throws RcFileCorruptionException
    {
        type.writeDouble(builder, parseDouble(slice, offset, length));
    }

    private static double parseDouble(Slice slice, int start, int length)
            throws RcFileCorruptionException
    {
        try {
            return Double.parseDouble(slice.toStringAscii(start, length));
        }
        catch (NumberFormatException e) {
            throw new RcFileCorruptionException(e, "Invalid double value");
        }
    }
}
