/*
 * StreamDispatcher.java
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
 * Final consumer in the stream decoding pipeline.
 * <p>
 * Dispatches decoded stream data to:
 * <ol>
 *   <li>The {@link PDFHandler#streamContent(ByteBuffer)} method</li>
 *   <li>Optionally, a {@link StreamParser} for specialized parsing (e.g., content streams)</li>
 * </ol>
 * <p>
 * When a stream parser is attached, proper buffer management is performed:
 * if the parser cannot consume all data (e.g., incomplete token at buffer end),
 * the remaining data is retained and re-presented with the next chunk.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class StreamDispatcher implements StreamConsumer {

    private final PDFHandler handler;
    private StreamParser parser;
    private boolean open = true;
    
    // Buffer for parser underflow (data that couldn't be consumed)
    private ByteBuffer parserBuffer;
    private static final int INITIAL_BUFFER_SIZE = 8192;

    /**
     * Creates a new stream dispatcher.
     *
     * @param handler the PDF handler to receive stream content events
     */
    public StreamDispatcher(PDFHandler handler) {
        this.handler = handler;
    }

    /**
     * Sets the stream parser for specialized content parsing.
     *
     * @param parser the stream parser, or null for no parsing
     */
    public void setParser(StreamParser parser) {
        this.parser = parser;
        if (parser != null && parserBuffer == null) {
            parserBuffer = ByteBuffer.allocate(INITIAL_BUFFER_SIZE);
            parserBuffer.flip(); // Empty, ready for reading
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int bytesConsumed = src.remaining();
        
        // Always dispatch to handler (make a duplicate so handler doesn't affect position)
        ByteBuffer handlerView = src.duplicate();
        handler.streamContent(handlerView);
        
        // If we have a parser, feed it with proper buffer management
        if (parser != null) {
            feedParser(src);
        }
        
        return bytesConsumed;
    }

    /**
     * Feeds data to the parser with proper underflow buffer management.
     */
    private void feedParser(ByteBuffer newData) throws IOException {
        ByteBuffer toProcess;
        
        // If we have leftover data from previous call, combine with new data
        if (parserBuffer.hasRemaining()) {
            // Ensure buffer has enough space
            int needed = parserBuffer.remaining() + newData.remaining();
            if (parserBuffer.capacity() < needed) {
                ByteBuffer larger = ByteBuffer.allocate(Math.max(needed, parserBuffer.capacity() * 2));
                larger.put(parserBuffer);
                larger.put(newData);
                larger.flip();
                parserBuffer = larger;
            } else {
                parserBuffer.compact();
                parserBuffer.put(newData);
                parserBuffer.flip();
            }
            toProcess = parserBuffer;
        } else {
            // No leftover, process new data directly
            toProcess = newData.duplicate();
        }
        
        // Let parser consume what it can
        int positionBefore = toProcess.position();
        parser.write(toProcess);
        
        // If parser didn't consume everything, keep the remainder in our buffer
        if (toProcess.hasRemaining()) {
            if (toProcess != parserBuffer) {
                // Copy remaining to our own buffer
                ensureCapacity(toProcess.remaining());
                parserBuffer.clear();
                parserBuffer.put(toProcess);
                parserBuffer.flip();
            }
            // Otherwise toProcess IS parserBuffer and it already has the position set correctly
        } else {
            // All consumed, clear the buffer
            parserBuffer.clear();
            parserBuffer.flip();
        }
    }

    private void ensureCapacity(int needed) {
        if (parserBuffer.capacity() < needed) {
            parserBuffer = ByteBuffer.allocate(Math.max(needed, INITIAL_BUFFER_SIZE));
            parserBuffer.flip();
        }
    }

    @Override
    public void close() throws IOException {
        // Final call to parser with any remaining data
        if (parser != null) {
            if (parserBuffer.hasRemaining()) {
                parser.write(parserBuffer);
            }
            parser.close();
        }
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        if (parserBuffer != null) {
            parserBuffer.clear();
            parserBuffer.flip();
        }
        open = true;
    }

}
