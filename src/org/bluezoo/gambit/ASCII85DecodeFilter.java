/*
 * ASCII85DecodeFilter.java
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Stream filter implementing ASCII85Decode (also known as btoa encoding).
 * <p>
 * Decodes base-85 encoded data. Each group of 5 ASCII characters represents
 * 4 bytes. The special character 'z' represents four zero bytes. The stream
 * is terminated by '~>'.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class ASCII85DecodeFilter extends StreamFilter {

    private static final long[] POW85 = { 85*85*85*85, 85*85*85, 85*85, 85, 1 };

    private int[] tuple = new int[5];
    private int tupleCount = 0;
    private boolean sawTilde = false;
    private boolean ended = false;

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (ended) {
            return src.remaining();
        }
        
        int bytesConsumed = src.remaining();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        while (src.hasRemaining()) {
            int b = src.get() & 0xFF;
            
            // Check for end marker
            if (sawTilde) {
                if (b == '>') {
                    // End of data - flush remaining
                    flushTuple(output);
                    ended = true;
                    break;
                } else {
                    sawTilde = false;
                }
            }
            
            if (b == '~') {
                sawTilde = true;
                continue;
            }
            
            // Skip whitespace
            if (isWhitespace(b)) {
                continue;
            }
            
            // Handle 'z' shortcut (four zero bytes)
            if (b == 'z') {
                if (tupleCount != 0) {
                    throw new IOException("ASCII85: 'z' inside tuple");
                }
                output.write(0);
                output.write(0);
                output.write(0);
                output.write(0);
                continue;
            }
            
            // Valid character range: '!' (33) to 'u' (117)
            if (b < '!' || b > 'u') {
                continue; // Invalid - skip
            }
            
            tuple[tupleCount++] = b - '!';
            
            if (tupleCount == 5) {
                // Decode full tuple
                long value = 0;
                for (int i = 0; i < 5; i++) {
                    value += tuple[i] * POW85[i];
                }
                output.write((int) (value >> 24) & 0xFF);
                output.write((int) (value >> 16) & 0xFF);
                output.write((int) (value >> 8) & 0xFF);
                output.write((int) value & 0xFF);
                tupleCount = 0;
            }
        }
        
        if (output.size() > 0) {
            writeToNext(ByteBuffer.wrap(output.toByteArray()));
        }
        
        return bytesConsumed;
    }

    @Override
    public void close() throws IOException {
        // Flush any remaining partial tuple
        if (tupleCount > 0) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            flushTuple(output);
            if (output.size() > 0) {
                writeToNext(ByteBuffer.wrap(output.toByteArray()));
            }
        }
        
        if (next != null) {
            next.close();
        }
        open = false;
    }

    @Override
    public void reset() {
        super.reset();
        tupleCount = 0;
        sawTilde = false;
        ended = false;
    }

    private void flushTuple(ByteArrayOutputStream output) {
        if (tupleCount == 0) {
            return;
        }
        
        // Pad with 'u' (84) and decode
        for (int i = tupleCount; i < 5; i++) {
            tuple[i] = 84;
        }
        
        long value = 0;
        for (int i = 0; i < 5; i++) {
            value += tuple[i] * POW85[i];
        }
        
        // Output n-1 bytes for n-character partial tuple
        int outputBytes = tupleCount - 1;
        for (int i = 0; i < outputBytes; i++) {
            output.write((int) (value >> (24 - i * 8)) & 0xFF);
        }
        
        tupleCount = 0;
    }

    private boolean isWhitespace(int b) {
        return b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32;
    }

}
