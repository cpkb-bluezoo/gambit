/*
 * StreamFilter.java
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
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 * Abstract base class for stream filters in the decoding pipeline.
 * <p>
 * A filter transforms data (e.g., decompresses, decodes) and writes the
 * result to the next channel in the chain.
 * <p>
 * Filters must handle incremental data - input may arrive in chunks.
 * If a filter cannot process all input (e.g., needs more data to complete
 * a decode operation), it must buffer the remaining data internally.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public abstract class StreamFilter implements StreamConsumer {

    /** The next channel in the pipeline chain. */
    protected WritableByteChannel next;

    /** Filter parameters from the stream dictionary's DecodeParms. */
    protected Map<Name, Object> params;

    /** Whether this filter is open. */
    protected boolean open = true;

    /**
     * Sets the next channel in the pipeline chain.
     *
     * @param next the next channel
     */
    public void setNext(WritableByteChannel next) {
        this.next = next;
    }

    /**
     * Sets the filter parameters (from DecodeParms in the stream dictionary).
     *
     * @param params the decode parameters, or null if none
     */
    public void setParams(Map<Name, Object> params) {
        this.params = params;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        open = true;
    }

    /**
     * Convenience method to write data to the next channel.
     *
     * @param data the data to write
     * @return the number of bytes written
     * @throws IOException if an I/O error occurs
     */
    protected int writeToNext(ByteBuffer data) throws IOException {
        if (next != null && data.hasRemaining()) {
            return next.write(data);
        }
        return 0;
    }

    /**
     * Creates a filter instance for the given filter name.
     *
     * @param filterName the PDF filter name (e.g., "FlateDecode", "ASCIIHexDecode")
     * @return a new filter instance, or null if the filter is not supported
     */
    public static StreamFilter create(String filterName) {
        switch (filterName) {
            case "FlateDecode":
            case "Fl":
                return new FlateDecodeFilter();
            
            case "ASCIIHexDecode":
            case "AHx":
                return new ASCIIHexDecodeFilter();
            
            case "ASCII85Decode":
            case "A85":
                return new ASCII85DecodeFilter();
            
            case "LZWDecode":
            case "LZW":
                return new LZWDecodeFilter();
            
            case "RunLengthDecode":
            case "RL":
                return new RunLengthDecodeFilter();
            
            // TODO: CCITTFaxDecode, JBIG2Decode, DCTDecode, JPXDecode, Crypt
            
            default:
                return null;
        }
    }

}
