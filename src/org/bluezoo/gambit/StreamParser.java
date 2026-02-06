/*
 * StreamParser.java
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

/**
 * Interface for parsers that consume stream content.
 * <p>
 * Extends {@link StreamConsumer} (and thus {@link java.nio.channels.WritableByteChannel})
 * to use standard NIO semantics:
 * <ul>
 *   <li>{@code write(ByteBuffer)} - parse data from the buffer</li>
 *   <li>{@code close()} - finalize parsing</li>
 * </ul>
 * <p>
 * Stream parsers receive decoded stream data in chunks via the {@code write}
 * method. Data may arrive incrementally, so parsers must handle partial tokens
 * at buffer boundaries.
 * <p>
 * The parser should consume as much data as it can from the buffer and advance
 * the buffer's position accordingly. If it cannot process all data (e.g., a
 * token spans the buffer boundary), it should leave the unconsumed data in
 * the buffer for the next call.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public interface StreamParser extends StreamConsumer {

    // Inherits from StreamConsumer:
    // - int write(ByteBuffer src) throws IOException
    // - boolean isOpen()
    // - void close() throws IOException
    // - void reset()

}
