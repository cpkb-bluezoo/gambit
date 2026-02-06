/*
 * ASCIIHexDecodeFilter.java
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
 * Stream filter implementing ASCIIHexDecode.
 * <p>
 * Decodes hexadecimal-encoded data. The encoded data consists of hex digit
 * pairs with optional whitespace, terminated by '>'.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class ASCIIHexDecodeFilter extends StreamFilter {

    private int pendingNibble = -1;  // High nibble waiting for low nibble
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
            
            if (b == '>') {
                // End of data marker
                if (pendingNibble >= 0) {
                    // Trailing nibble - assume low nibble is 0
                    output.write(pendingNibble << 4);
                    pendingNibble = -1;
                }
                ended = true;
                break;
            }
            
            // Skip whitespace
            if (isWhitespace(b)) {
                continue;
            }
            
            int nibble = hexValue(b);
            if (nibble < 0) {
                continue; // Invalid character - skip
            }
            
            if (pendingNibble < 0) {
                pendingNibble = nibble;
            } else {
                output.write((pendingNibble << 4) | nibble);
                pendingNibble = -1;
            }
        }
        
        if (output.size() > 0) {
            writeToNext(ByteBuffer.wrap(output.toByteArray()));
        }
        
        return bytesConsumed;
    }

    @Override
    public void close() throws IOException {
        // Handle any pending nibble
        if (pendingNibble >= 0) {
            writeToNext(ByteBuffer.wrap(new byte[] { (byte) (pendingNibble << 4) }));
            pendingNibble = -1;
        }
        
        if (next != null) {
            next.close();
        }
        open = false;
    }

    @Override
    public void reset() {
        super.reset();
        pendingNibble = -1;
        ended = false;
    }

    private boolean isWhitespace(int b) {
        return b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32;
    }

    private int hexValue(int b) {
        if (b >= '0' && b <= '9') {
            return b - '0';
        } else if (b >= 'A' && b <= 'F') {
            return b - 'A' + 10;
        } else if (b >= 'a' && b <= 'f') {
            return b - 'a' + 10;
        }
        return -1;
    }

}
