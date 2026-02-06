/*
 * RunLengthDecodeFilter.java
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
 * Stream filter implementing RunLengthDecode.
 * <p>
 * Decodes data encoded with a simple run-length compression scheme.
 * A length byte determines the operation:
 * <ul>
 *   <li>0-127: Copy the next (length+1) bytes literally</li>
 *   <li>129-255: Repeat the next byte (257-length) times</li>
 *   <li>128: End of data</li>
 * </ul>
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class RunLengthDecodeFilter extends StreamFilter {

    private static final int EOD = 128;

    private int state = 0;  // 0=waiting for length, 1=literal copy, 2=run repeat
    private int remaining = 0;
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
            
            switch (state) {
                case 0: // Waiting for length byte
                    if (b == EOD) {
                        ended = true;
                        break;
                    } else if (b < 128) {
                        // Literal run: copy next (b+1) bytes
                        remaining = b + 1;
                        state = 1;
                    } else {
                        // Repeat run: repeat next byte (257-b) times
                        remaining = 257 - b;
                        state = 2;
                    }
                    break;
                    
                case 1: // Literal copy
                    output.write(b);
                    remaining--;
                    if (remaining == 0) {
                        state = 0;
                    }
                    break;
                    
                case 2: // Repeat run
                    for (int i = 0; i < remaining; i++) {
                        output.write(b);
                    }
                    remaining = 0;
                    state = 0;
                    break;
            }
            
            if (ended) {
                break;
            }
        }
        
        if (output.size() > 0) {
            writeToNext(ByteBuffer.wrap(output.toByteArray()));
        }
        
        return bytesConsumed;
    }

    @Override
    public void close() throws IOException {
        if (next != null) {
            next.close();
        }
        open = false;
    }

    @Override
    public void reset() {
        super.reset();
        state = 0;
        remaining = 0;
        ended = false;
    }

}
