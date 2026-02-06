/*
 * StreamConsumer.java
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

import java.nio.channels.WritableByteChannel;

/**
 * Marker interface for stream consumers in a feedforward pipeline.
 * <p>
 * Extends {@link WritableByteChannel} to use standard NIO semantics:
 * <ul>
 *   <li>{@code write(ByteBuffer)} - consume data from the buffer</li>
 *   <li>{@code close()} - signal end of stream</li>
 * </ul>
 * <p>
 * Consumers must handle incremental data delivery - data may arrive in
 * multiple chunks across multiple {@code write} calls. If a consumer
 * cannot process all data in a buffer (e.g., incomplete token), it should
 * consume what it can and leave the rest in the buffer.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public interface StreamConsumer extends WritableByteChannel {

    /**
     * Resets the consumer state for reuse.
     */
    void reset();

}
