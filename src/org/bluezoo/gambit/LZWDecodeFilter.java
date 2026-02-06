/*
 * LZWDecodeFilter.java
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
import java.util.ArrayList;
import java.util.List;

/**
 * Stream filter implementing LZWDecode.
 * <p>
 * Implements the LZW decompression algorithm as used in PDF (and TIFF).
 * Uses variable-length codes starting at 9 bits.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class LZWDecodeFilter extends StreamFilter {

    private static final int CLEAR_TABLE = 256;
    private static final int EOD = 257;
    private static final int INITIAL_CODE_LENGTH = 9;
    private static final int MAX_CODE_LENGTH = 12;

    private List<byte[]> table;
    private int codeLength;
    private int nextCode;
    private byte[] prevSequence;
    
    // Bit buffer for reading variable-length codes
    private int bitBuffer;
    private int bitsInBuffer;
    private boolean ended;
    
    // Early change parameter (default true for PDF)
    private boolean earlyChange = true;

    public LZWDecodeFilter() {
        initTable();
    }

    @Override
    public void setParams(java.util.Map<Name, Object> params) {
        super.setParams(params);
        if (params != null) {
            Object ec = params.get(new Name("EarlyChange"));
            if (ec instanceof Number) {
                earlyChange = ((Number) ec).intValue() != 0;
            }
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (ended) {
            return src.remaining();
        }
        
        int bytesConsumed = src.remaining();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        
        while (src.hasRemaining()) {
            // Read next code
            while (bitsInBuffer < codeLength && src.hasRemaining()) {
                bitBuffer = (bitBuffer << 8) | (src.get() & 0xFF);
                bitsInBuffer += 8;
            }
            
            if (bitsInBuffer < codeLength) {
                break; // Need more input
            }
            
            int code = (bitBuffer >> (bitsInBuffer - codeLength)) & ((1 << codeLength) - 1);
            bitsInBuffer -= codeLength;
            
            if (code == EOD) {
                ended = true;
                break;
            }
            
            if (code == CLEAR_TABLE) {
                initTable();
                prevSequence = null;
                continue;
            }
            
            byte[] sequence;
            if (code < nextCode) {
                sequence = table.get(code);
            } else if (code == nextCode && prevSequence != null) {
                // Special case: code not yet in table
                sequence = new byte[prevSequence.length + 1];
                System.arraycopy(prevSequence, 0, sequence, 0, prevSequence.length);
                sequence[prevSequence.length] = prevSequence[0];
            } else {
                throw new IOException("LZW: Invalid code " + code);
            }
            
            output.write(sequence);
            
            // Add new sequence to table
            if (prevSequence != null && nextCode < 4096) {
                byte[] newEntry = new byte[prevSequence.length + 1];
                System.arraycopy(prevSequence, 0, newEntry, 0, prevSequence.length);
                newEntry[prevSequence.length] = sequence[0];
                table.add(newEntry);
                nextCode++;
                
                // Increase code length if needed
                int threshold = earlyChange ? nextCode : nextCode + 1;
                if (threshold >= (1 << codeLength) && codeLength < MAX_CODE_LENGTH) {
                    codeLength++;
                }
            }
            
            prevSequence = sequence;
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
        initTable();
        prevSequence = null;
        bitBuffer = 0;
        bitsInBuffer = 0;
        ended = false;
    }

    private void initTable() {
        table = new ArrayList<>(4096);
        for (int i = 0; i < 256; i++) {
            table.add(new byte[] { (byte) i });
        }
        table.add(null); // CLEAR_TABLE (256)
        table.add(null); // EOD (257)
        nextCode = 258;
        codeLength = INITIAL_CODE_LENGTH;
    }

}
