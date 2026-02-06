/*
 * ObjectStream.java
 * Copyright (C) 2025 Chris Burdess
 *
 * This file is part of Gambit, a streaming PDF parser.
 *
 * Gambit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gambit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Gambit.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.bluezoo.gambit;

import java.nio.ByteBuffer;

/**
 * In-memory representation of a decoded PDF object stream (PDF 1.5+).
 * <p>
 * An object stream contains multiple indirect objects stored sequentially.
 * The stream has an index table (N pairs of object number and byte offset)
 * followed by the object data. Offsets in the table are relative to the
 * first object (the /First value in the stream dictionary).
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class ObjectStream {

    private final ObjectId streamId;
    private final ByteBuffer decoded;
    private final int first;
    private final int[] relativeOffsets;

    /**
     * Creates an object stream.
     *
     * @param streamId the object ID of the stream
     * @param decoded the decoded stream data (read-only view; position/limit unchanged)
     * @param first the /First value (byte offset to first object in decoded)
     * @param relativeOffsets for each index i, offset of object i relative to first (length N)
     */
    public ObjectStream(ObjectId streamId, ByteBuffer decoded, int first, int[] relativeOffsets) {
        this.streamId = streamId;
        this.decoded = decoded.duplicate();
        this.first = first;
        this.relativeOffsets = relativeOffsets;
    }

    /**
     * Returns the object ID of this object stream.
     *
     * @return the stream ID
     */
    public ObjectId getStreamId() {
        return streamId;
    }

    /**
     * Returns a read-only duplicate of the decoded stream buffer.
     * Use with {@link #getObjectStartOffset(int)} to parse a specific object.
     *
     * @return a duplicate of the decoded data
     */
    public ByteBuffer getDecoded() {
        return decoded.duplicate().asReadOnlyBuffer();
    }

    /**
     * Returns the byte offset in the decoded stream where the object at the
     * given index starts. This is {@code first + relativeOffsets[index]}.
     *
     * @param index the 0-based index of the object in the stream
     * @return the start offset in decoded
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public int getObjectStartOffset(int index) {
        if (index < 0 || index >= relativeOffsets.length) {
            throw new IndexOutOfBoundsException("Object index " + index + " not in [0, " + relativeOffsets.length + ")");
        }
        return first + relativeOffsets[index];
    }

    /**
     * Returns the number of objects in this stream.
     *
     * @return N
     */
    public int getObjectCount() {
        return relativeOffsets.length;
    }
}
