/*
 * CMapHandler.java
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

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Handler interface for PDF CMap parsing events (e.g. ToUnicode CMaps).
 * <p>
 * CMaps define mappings from character codes to Unicode or CID. The most common
 * use in PDF is the ToUnicode CMap (from /ToUnicode in a font), which maps
 * character codes to UTF-16BE Unicode. This handler receives events for
 * codespacerange, bfchar (single code to Unicode), and bfrange (range to
 * Unicode or array of Unicode).
 * <p>
 * Hex strings in the CMap stream are delivered as decoded byte arrays (e.g.
 * {@code <0041>} as one byte 0x41; {@code <0041>} for Unicode U+0041 as two
 * bytes 0x00 0x41 in UTF-16BE).
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public interface CMapHandler {

    /**
     * Called at the start of CMap parsing.
     */
    void startCMap();

    /**
     * Called at the end of CMap parsing.
     */
    void endCMap();

    /**
     * Defines the code space range (character code length).
     * Typically one or more pairs; each pair defines the byte length of codes in that range.
     *
     * @param low  minimum code (hex-decoded bytes)
     * @param high maximum code (hex-decoded bytes)
     */
    void codeSpaceRange(ByteBuffer low, ByteBuffer high);

    /**
     * Single character code to Unicode mapping (beginbfchar / endbfchar).
     *
     * @param fromCode  source character code (hex-decoded)
     * @param toUnicode destination Unicode, UTF-16BE (hex-decoded; may be multiple code units for one character)
     */
    void bfchar(ByteBuffer fromCode, ByteBuffer toUnicode);

    /**
     * Range of character codes mapping to a single Unicode value (beginbfrange).
     * For each code in [from, to], the Unicode value is startUnicode + (code - from).
     *
     * @param from       start of source range (hex-decoded)
     * @param to         end of source range (hex-decoded)
     * @param startUnicode destination Unicode for the first code, UTF-16BE (hex-decoded)
     */
    void bfrange(ByteBuffer from, ByteBuffer to, ByteBuffer startUnicode);

    /**
     * Range of character codes mapping to an array of Unicode values (beginbfrange with array).
     *
     * @param from  start of source range (hex-decoded)
     * @param to    end of source range (hex-decoded)
     * @param dests array of Unicode values, UTF-16BE (hex-decoded); length must be (to - from + 1)
     */
    void bfrange(ByteBuffer from, ByteBuffer to, List<ByteBuffer> dests);
}
