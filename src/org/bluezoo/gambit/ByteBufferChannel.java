/*
 * ByteBufferChannel.java
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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * {@link SeekableByteChannel} that reads from a {@link ByteBuffer}.
 * <p>
 * Allows the PDF parser to read from in-memory data (e.g. decoded object
 * stream content) using the same channel-based API as file I/O. The buffer
 * is not modified; position is tracked in a duplicate.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class ByteBufferChannel implements SeekableByteChannel {

    private final ByteBuffer buffer;
    private boolean open = true;

    /**
     * Creates a channel that reads from the given buffer.
     * The buffer's position and limit are not modified; a duplicate is used internally.
     *
     * @param source the buffer to read from (read-only duplicate is used)
     */
    public ByteBufferChannel(ByteBuffer source) {
        this.buffer = source.duplicate();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
        if (!buffer.hasRemaining()) {
            return -1;
        }
        int n = Math.min(buffer.remaining(), dst.remaining());
        if (n == 0) {
            return 0;
        }
        int limit = buffer.limit();
        buffer.limit(buffer.position() + n);
        dst.put(buffer);
        buffer.limit(limit);
        return n;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new IOException("Read-only channel");
    }

    @Override
    public long position() throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
        return buffer.position();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
        if (newPosition < 0 || newPosition > buffer.limit()) {
            throw new IllegalArgumentException("Position out of range: " + newPosition);
        }
        buffer.position((int) newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        if (!open) {
            throw new ClosedChannelException();
        }
        return buffer.limit();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new IOException("Read-only channel");
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() throws IOException {
        open = false;
    }
}
