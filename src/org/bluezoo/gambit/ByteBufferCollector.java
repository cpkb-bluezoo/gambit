/*
 * ByteBufferCollector.java
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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Stream consumer that collects all written data into a {@link ByteBuffer}.
 * <p>
 * Uses NIO throughout: data is accumulated in a growable heap {@code ByteBuffer}.
 * Call {@link #toByteBuffer()} after the pipeline is closed to get a read-only
 * view of the collected data.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class ByteBufferCollector implements StreamConsumer {

    private static final int INITIAL_CAPACITY = 8192;

    private ByteBuffer buffer;
    private boolean open = true;

    /**
     * Creates a new collector with default initial capacity.
     */
    public ByteBufferCollector() {
        this.buffer = ByteBuffer.allocate(INITIAL_CAPACITY);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int remaining = src.remaining();
        if (remaining == 0) {
            return 0;
        }
        ensureCapacity(remaining);
        buffer.put(src);
        return remaining;
    }

    private void ensureCapacity(int additional) {
        if (buffer.remaining() >= additional) {
            return;
        }
        int newCapacity = buffer.capacity();
        while (buffer.position() + additional > newCapacity) {
            newCapacity = Math.max(newCapacity * 2, buffer.position() + additional);
        }
        buffer.flip();
        ByteBuffer grown = ByteBuffer.allocate(newCapacity);
        grown.put(buffer);
        buffer = grown;
    }

    @Override
    public void close() throws IOException {
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        buffer.clear();
        if (buffer.capacity() > INITIAL_CAPACITY) {
            buffer = ByteBuffer.allocate(INITIAL_CAPACITY);
        }
        open = true;
    }

    /**
     * Returns a read-only view of the collected data.
     * Call after the pipeline has been closed. The buffer is flipped (position 0, limit at end).
     *
     * @return a read-only duplicate of the collected data
     */
    public ByteBuffer toByteBuffer() {
        ByteBuffer out = buffer.duplicate();
        out.flip();
        return out.asReadOnlyBuffer();
    }
}
