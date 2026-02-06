/*
 * FlateDecodeFilter.java
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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Stream filter implementing FlateDecode (zlib/deflate decompression).
 * <p>
 * Supports incremental decompression - input can arrive in chunks.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class FlateDecodeFilter extends StreamFilter {

    private static final int OUTPUT_BUFFER_SIZE = 8192;

    private Inflater inflater;
    private byte[] inputBuffer;
    private byte[] outputBuffer;
    private int predictor = 1;  // Default: no predictor
    private int columns = 1;
    private int colors = 1;
    private int bitsPerComponent = 8;

    public FlateDecodeFilter() {
        this.inflater = new Inflater();
        this.outputBuffer = new byte[OUTPUT_BUFFER_SIZE];
    }

    @Override
    public void setParams(java.util.Map<Name, Object> params) {
        super.setParams(params);
        if (params != null) {
            Object pred = params.get(new Name("Predictor"));
            if (pred instanceof Number) {
                predictor = ((Number) pred).intValue();
            }
            Object cols = params.get(new Name("Columns"));
            if (cols instanceof Number) {
                columns = ((Number) cols).intValue();
            }
            Object colrs = params.get(new Name("Colors"));
            if (colrs instanceof Number) {
                colors = ((Number) colrs).intValue();
            }
            Object bpc = params.get(new Name("BitsPerComponent"));
            if (bpc instanceof Number) {
                bitsPerComponent = ((Number) bpc).intValue();
            }
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int bytesConsumed = src.remaining();
        
        // Copy input to byte array for Inflater
        int len = src.remaining();
        if (inputBuffer == null || inputBuffer.length < len) {
            inputBuffer = new byte[len];
        }
        src.get(inputBuffer, 0, len);
        
        inflater.setInput(inputBuffer, 0, len);
        
        try {
            while (!inflater.needsInput() && !inflater.finished()) {
                int count = inflater.inflate(outputBuffer);
                if (count > 0) {
                    ByteBuffer output = ByteBuffer.wrap(outputBuffer, 0, count);
                    
                    // Apply predictor if needed
                    if (predictor > 1) {
                        output = applyPredictor(output);
                    }
                    
                    writeToNext(output);
                }
            }
        } catch (DataFormatException e) {
            throw new IOException("FlateDecode error: " + e.getMessage(), e);
        }
        
        return bytesConsumed;
    }

    @Override
    public void close() throws IOException {
        // Flush any remaining data
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(outputBuffer);
                if (count > 0) {
                    ByteBuffer output = ByteBuffer.wrap(outputBuffer, 0, count);
                    if (predictor > 1) {
                        output = applyPredictor(output);
                    }
                    writeToNext(output);
                } else {
                    break;
                }
            }
        } catch (DataFormatException e) {
            throw new IOException("FlateDecode error: " + e.getMessage(), e);
        }
        
        if (next != null) {
            next.close();
        }
        open = false;
    }

    @Override
    public void reset() {
        super.reset();
        inflater.reset();
        predictor = 1;
        columns = 1;
        colors = 1;
        bitsPerComponent = 8;
        predictorPrevRow = null;
        predictorAccumulator.clear();
    }

    // Predictor support
    private ByteBuffer predictorAccumulator = ByteBuffer.allocate(65536);
    private byte[] predictorPrevRow;

    /**
     * Applies PNG predictor to the decompressed data.
     * <p>
     * Predictors 10-15 are PNG predictors where each row is prefixed with
     * a filter byte indicating the prediction algorithm.
     */
    private ByteBuffer applyPredictor(ByteBuffer data) throws IOException {
        if (predictor == 1) {
            return data; // No prediction
        }
        
        if (predictor == 2) {
            // TIFF Predictor 2 (horizontal differencing)
            return applyTIFFPredictor(data);
        }
        
        if (predictor >= 10 && predictor <= 15) {
            // PNG predictors
            return applyPNGPredictor(data);
        }
        
        return data;
    }

    private ByteBuffer applyTIFFPredictor(ByteBuffer data) {
        // TIFF Predictor 2: horizontal differencing
        int bytesPerPixel = (colors * bitsPerComponent + 7) / 8;
        int rowBytes = (columns * colors * bitsPerComponent + 7) / 8;
        
        byte[] input = new byte[data.remaining()];
        data.get(input);
        
        for (int row = 0; row < input.length / rowBytes; row++) {
            int rowStart = row * rowBytes;
            for (int i = bytesPerPixel; i < rowBytes; i++) {
                input[rowStart + i] = (byte) (input[rowStart + i] + input[rowStart + i - bytesPerPixel]);
            }
        }
        
        return ByteBuffer.wrap(input);
    }

    private ByteBuffer applyPNGPredictor(ByteBuffer data) throws IOException {
        // Accumulate data since rows may span chunks
        predictorAccumulator.put(data);
        predictorAccumulator.flip();
        
        int bytesPerPixel = (colors * bitsPerComponent + 7) / 8;
        int rowBytes = columns * bytesPerPixel;  // Data bytes per row (excluding filter byte)
        int fullRowBytes = rowBytes + 1;  // Including filter byte
        
        if (predictorPrevRow == null) {
            predictorPrevRow = new byte[rowBytes];
        }
        
        java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
        
        while (predictorAccumulator.remaining() >= fullRowBytes) {
            int filterByte = predictorAccumulator.get() & 0xFF;
            byte[] row = new byte[rowBytes];
            predictorAccumulator.get(row);
            
            switch (filterByte) {
                case 0: // None
                    break;
                case 1: // Sub
                    for (int i = bytesPerPixel; i < rowBytes; i++) {
                        row[i] = (byte) (row[i] + row[i - bytesPerPixel]);
                    }
                    break;
                case 2: // Up
                    for (int i = 0; i < rowBytes; i++) {
                        row[i] = (byte) (row[i] + predictorPrevRow[i]);
                    }
                    break;
                case 3: // Average
                    for (int i = 0; i < rowBytes; i++) {
                        int left = (i >= bytesPerPixel) ? (row[i - bytesPerPixel] & 0xFF) : 0;
                        int up = predictorPrevRow[i] & 0xFF;
                        row[i] = (byte) (row[i] + (left + up) / 2);
                    }
                    break;
                case 4: // Paeth
                    for (int i = 0; i < rowBytes; i++) {
                        int a = (i >= bytesPerPixel) ? (row[i - bytesPerPixel] & 0xFF) : 0;
                        int b = predictorPrevRow[i] & 0xFF;
                        int c = (i >= bytesPerPixel) ? (predictorPrevRow[i - bytesPerPixel] & 0xFF) : 0;
                        row[i] = (byte) (row[i] + paethPredictor(a, b, c));
                    }
                    break;
            }
            
            result.write(row);
            System.arraycopy(row, 0, predictorPrevRow, 0, rowBytes);
        }
        
        // Keep remaining data for next call
        predictorAccumulator.compact();
        
        return ByteBuffer.wrap(result.toByteArray());
    }

    private int paethPredictor(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        if (pb <= pc) {
            return b;
        }
        return c;
    }

}
