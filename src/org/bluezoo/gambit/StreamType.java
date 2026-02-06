/*
 * StreamType.java
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
 * Identifies the type of a PDF stream based on context.
 * <p>
 * Streams in PDF are typed by their context (where they are referenced from),
 * not by the stream object itself. This enum allows the parser to track
 * the expected type of a stream and dispatch parsing to the appropriate handler.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public enum StreamType {

    /**
     * Default/unknown stream type. Stream bytes are passed to PDFHandler
     * but no specialized parsing is performed.
     */
    DEFAULT,

    /**
     * Page content stream (from /Contents in a Page or Form XObject).
     * Parsed as PDF graphics operators and dispatched to PDFContentHandler.
     */
    CONTENT,

    /**
     * ToUnicode CMap stream (from /ToUnicode in a Font).
     * Defines character code to Unicode mappings.
     */
    CMAP,

    /**
     * XMP metadata stream (from /Metadata).
     * Contains XML metadata about the document or object.
     */
    METADATA,

    /**
     * Type 1 font program (from /FontFile in a Type1 font descriptor).
     */
    FONT_TYPE1,

    /**
     * TrueType/OpenType font program with TrueType outlines
     * (from /FontFile2 in a TrueType font descriptor).
     * Contains 'glyf' table for glyph outlines.
     */
    FONT_TRUETYPE,

    /**
     * OpenType font program with CFF outlines
     * (from /FontFile3 with /Subtype OpenType or CIDFontType0C).
     * Contains 'CFF ' table for glyph outlines.
     */
    FONT_OPENTYPE_CFF,

    /**
     * Standalone CFF font data (not in OpenType wrapper).
     * (from /FontFile3 with /Subtype Type1C or CIDFontType0C).
     */
    FONT_CFF,

    /**
     * ICC color profile (from ICC-based color space).
     */
    ICC_PROFILE,

    /**
     * Object stream containing compressed objects (PDF 1.5+).
     */
    OBJECT_STREAM,

    /**
     * XRef stream containing cross-reference data (PDF 1.5+).
     */
    XREF_STREAM

}

