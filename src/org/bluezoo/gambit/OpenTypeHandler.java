/*
 * OpenTypeHandler.java
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

/**
 * Handler interface for OpenType/TrueType font parsing events.
 * <p>
 * This interface receives events as an OpenType font is parsed. The events
 * are organized hierarchically:
 * <ol>
 *   <li>Font-level events ({@code startFont}/{@code endFont})</li>
 *   <li>Table events (one set per table in the font)</li>
 *   <li>Table-specific content events</li>
 * </ol>
 * <p>
 * For PDF processing, the most important tables are:
 * <ul>
 *   <li>{@code cmap} - Character to glyph mapping</li>
 *   <li>{@code hmtx}/{@code vmtx} - Glyph metrics</li>
 *   <li>{@code head} - Font header with units per em</li>
 *   <li>{@code glyf} - TrueType glyph outlines</li>
 *   <li>{@code CFF } - CFF glyph outlines</li>
 *   <li>{@code name} - Font naming information</li>
 * </ul>
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public interface OpenTypeHandler {

    // ========================================================================
    // Font-level events
    // ========================================================================

    /**
     * Called at the start of font parsing.
     *
     * @param sfntVersion the sfnt version tag (0x00010000 for TrueType, 'OTTO' for CFF)
     * @param numTables the number of tables in the font
     */
    void startFont(int sfntVersion, int numTables);

    /**
     * Called at the end of font parsing.
     */
    void endFont();

    /**
     * Called for each table in the table directory.
     * <p>
     * This is called during initial parsing of the table directory, before
     * any table content is parsed.
     *
     * @param tag the 4-character table tag (e.g., "cmap", "glyf", "head")
     * @param checksum the table checksum
     * @param offset the offset to the table from the beginning of the font
     * @param length the length of the table in bytes
     */
    void tableDirectory(String tag, int checksum, int offset, int length);

    // ========================================================================
    // head table - Font Header
    // ========================================================================

    /**
     * Called with data from the 'head' table.
     *
     * @param unitsPerEm the number of units per em (typically 1000 or 2048)
     * @param xMin minimum x coordinate across all glyphs
     * @param yMin minimum y coordinate across all glyphs
     * @param xMax maximum x coordinate across all glyphs
     * @param yMax maximum y coordinate across all glyphs
     * @param macStyle style flags (bit 0=bold, bit 1=italic, etc.)
     * @param indexToLocFormat format of the 'loca' table (0=short, 1=long)
     */
    void headTable(int unitsPerEm, short xMin, short yMin, short xMax, short yMax,
                   int macStyle, int indexToLocFormat);

    // ========================================================================
    // hhea table - Horizontal Header
    // ========================================================================

    /**
     * Called with data from the 'hhea' table.
     *
     * @param ascender typographic ascent
     * @param descender typographic descent (typically negative)
     * @param lineGap typographic line gap
     * @param advanceWidthMax maximum advance width
     * @param numberOfHMetrics number of entries in hmtx table
     */
    void hheaTable(short ascender, short descender, short lineGap,
                   int advanceWidthMax, int numberOfHMetrics);

    // ========================================================================
    // vhea table - Vertical Header (optional)
    // ========================================================================

    /**
     * Called with data from the 'vhea' table (if present).
     *
     * @param ascender vertical typographic ascent
     * @param descender vertical typographic descent
     * @param lineGap vertical typographic line gap
     * @param advanceHeightMax maximum advance height
     * @param numberOfVMetrics number of entries in vmtx table
     */
    void vheaTable(short ascender, short descender, short lineGap,
                   int advanceHeightMax, int numberOfVMetrics);

    // ========================================================================
    // maxp table - Maximum Profile
    // ========================================================================

    /**
     * Called with data from the 'maxp' table.
     *
     * @param numGlyphs the number of glyphs in the font
     */
    void maxpTable(int numGlyphs);

    // ========================================================================
    // OS/2 table - OS/2 and Windows Metrics
    // ========================================================================

    /**
     * Called with data from the 'OS/2' table.
     *
     * @param version table version
     * @param avgCharWidth average character width
     * @param weightClass visual weight (100-900, 400=normal, 700=bold)
     * @param widthClass visual width (1-9, 5=normal)
     * @param fsType embedding licensing rights
     * @param subscriptXSize subscript horizontal font size
     * @param subscriptYSize subscript vertical font size
     * @param subscriptXOffset subscript x offset
     * @param subscriptYOffset subscript y offset
     * @param superscriptXSize superscript horizontal font size
     * @param superscriptYSize superscript vertical font size
     * @param superscriptXOffset superscript x offset
     * @param superscriptYOffset superscript y offset
     * @param strikeoutSize strikeout stroke thickness
     * @param strikeoutPosition strikeout stroke position
     * @param familyClass font family classification
     */
    void os2Table(int version, short avgCharWidth, int weightClass, int widthClass,
                  int fsType, short subscriptXSize, short subscriptYSize,
                  short subscriptXOffset, short subscriptYOffset,
                  short superscriptXSize, short superscriptYSize,
                  short superscriptXOffset, short superscriptYOffset,
                  short strikeoutSize, short strikeoutPosition, short familyClass);

    /**
     * Called with Panose classification from OS/2 table.
     *
     * @param panose 10-byte PANOSE classification
     */
    void os2Panose(byte[] panose);

    /**
     * Called with Unicode range bits from OS/2 table.
     *
     * @param ulUnicodeRange1 bits 0-31
     * @param ulUnicodeRange2 bits 32-63
     * @param ulUnicodeRange3 bits 64-95
     * @param ulUnicodeRange4 bits 96-127
     */
    void os2UnicodeRanges(int ulUnicodeRange1, int ulUnicodeRange2,
                          int ulUnicodeRange3, int ulUnicodeRange4);

    /**
     * Called with vendor ID and selection flags from OS/2 table.
     *
     * @param vendorID 4-character vendor identifier
     * @param fsSelection font selection flags
     * @param firstCharIndex minimum Unicode code point
     * @param lastCharIndex maximum Unicode code point
     */
    void os2Selection(String vendorID, int fsSelection,
                      int firstCharIndex, int lastCharIndex);

    /**
     * Called with typographic metrics from OS/2 table (version 2+).
     *
     * @param typoAscender typographic ascender
     * @param typoDescender typographic descender
     * @param typoLineGap typographic line gap
     * @param winAscent Windows clipping ascent
     * @param winDescent Windows clipping descent
     */
    void os2TypoMetrics(short typoAscender, short typoDescender, short typoLineGap,
                        int winAscent, int winDescent);

    /**
     * Called with x-height and cap-height from OS/2 table (version 2+).
     *
     * @param xHeight x-height
     * @param capHeight cap height
     */
    void os2Heights(short xHeight, short capHeight);

    // ========================================================================
    // name table - Naming Table
    // ========================================================================

    /**
     * Called at the start of the 'name' table.
     *
     * @param format name table format (0 or 1)
     * @param count number of name records
     */
    void startNameTable(int format, int count);

    /**
     * Called for each name record.
     *
     * @param platformID platform identifier (0=Unicode, 1=Mac, 3=Windows)
     * @param encodingID platform-specific encoding
     * @param languageID platform-specific language
     * @param nameID name identifier (see {@link NameId})
     * @param value the name string value
     */
    void nameRecord(int platformID, int encodingID, int languageID,
                    int nameID, String value);

    /**
     * Called at the end of the 'name' table.
     */
    void endNameTable();

    // ========================================================================
    // cmap table - Character to Glyph Mapping
    // ========================================================================

    /**
     * Called at the start of the 'cmap' table.
     *
     * @param version table version
     * @param numTables number of encoding subtables
     */
    void startCmapTable(int version, int numTables);

    /**
     * Called at the start of a cmap encoding subtable.
     *
     * @param platformID platform identifier
     * @param encodingID platform-specific encoding
     * @param format subtable format (0, 2, 4, 6, 8, 10, 12, 13, 14)
     */
    void startCmapSubtable(int platformID, int encodingID, int format);

    /**
     * Called for each character to glyph mapping.
     * <p>
     * For format 4 and similar subtables, this may be called many times
     * with sequential or range-based mappings.
     *
     * @param charCode the character code (Unicode code point for Unicode platforms)
     * @param glyphId the glyph index
     */
    void cmapMapping(int charCode, int glyphId);

    /**
     * Called at the end of a cmap encoding subtable.
     */
    void endCmapSubtable();

    /**
     * Called at the end of the 'cmap' table.
     */
    void endCmapTable();

    // ========================================================================
    // hmtx table - Horizontal Metrics
    // ========================================================================

    /**
     * Called at the start of the 'hmtx' table.
     *
     * @param numberOfHMetrics number of full hMetric entries
     * @param numGlyphs total number of glyphs
     */
    void startHmtxTable(int numberOfHMetrics, int numGlyphs);

    /**
     * Called for each horizontal metric entry.
     *
     * @param glyphId the glyph index
     * @param advanceWidth the advance width in font units
     * @param leftSideBearing the left side bearing in font units
     */
    void horizontalMetric(int glyphId, int advanceWidth, short leftSideBearing);

    /**
     * Called at the end of the 'hmtx' table.
     */
    void endHmtxTable();

    // ========================================================================
    // vmtx table - Vertical Metrics (optional)
    // ========================================================================

    /**
     * Called at the start of the 'vmtx' table.
     *
     * @param numberOfVMetrics number of full vMetric entries
     * @param numGlyphs total number of glyphs
     */
    void startVmtxTable(int numberOfVMetrics, int numGlyphs);

    /**
     * Called for each vertical metric entry.
     *
     * @param glyphId the glyph index
     * @param advanceHeight the advance height in font units
     * @param topSideBearing the top side bearing in font units
     */
    void verticalMetric(int glyphId, int advanceHeight, short topSideBearing);

    /**
     * Called at the end of the 'vmtx' table.
     */
    void endVmtxTable();

    // ========================================================================
    // post table - PostScript Information
    // ========================================================================

    /**
     * Called with data from the 'post' table header.
     *
     * @param format post table format (1.0, 2.0, 2.5, 3.0, 4.0)
     * @param italicAngle italic angle in degrees
     * @param underlinePosition underline position
     * @param underlineThickness underline thickness
     * @param isFixedPitch true if font is monospaced
     */
    void postTable(float format, float italicAngle, short underlinePosition,
                   short underlineThickness, boolean isFixedPitch);

    /**
     * Called for each glyph name (format 2.0 only).
     *
     * @param glyphId the glyph index
     * @param name the PostScript glyph name
     */
    void postGlyphName(int glyphId, String name);

    // ========================================================================
    // glyf table - TrueType Glyph Outlines
    // ========================================================================

    /**
     * Called at the start of processing glyph outlines.
     *
     * @param numGlyphs the number of glyphs
     */
    void startGlyphTable(int numGlyphs);

    /**
     * Called at the start of a simple glyph (contour-based outline).
     *
     * @param glyphId the glyph index
     * @param numberOfContours number of contours (positive)
     * @param xMin minimum x coordinate
     * @param yMin minimum y coordinate
     * @param xMax maximum x coordinate
     * @param yMax maximum y coordinate
     */
    void startSimpleGlyph(int glyphId, int numberOfContours,
                          short xMin, short yMin, short xMax, short yMax);

    /**
     * Called at the start of a glyph contour.
     *
     * @param contourIndex the contour index (0-based)
     */
    void startContour(int contourIndex);

    /**
     * Called for each point in a contour.
     *
     * @param x the x coordinate in font units
     * @param y the y coordinate in font units
     * @param onCurve true if this is an on-curve point, false for control point
     */
    void contourPoint(short x, short y, boolean onCurve);

    /**
     * Called at the end of a glyph contour.
     */
    void endContour();

    /**
     * Called at the end of a simple glyph.
     */
    void endSimpleGlyph();

    /**
     * Called at the start of a composite glyph (made of other glyphs).
     *
     * @param glyphId the glyph index
     * @param xMin minimum x coordinate
     * @param yMin minimum y coordinate
     * @param xMax maximum x coordinate
     * @param yMax maximum y coordinate
     */
    void startCompositeGlyph(int glyphId, short xMin, short yMin, short xMax, short yMax);

    /**
     * Called for each component in a composite glyph.
     *
     * @param componentGlyphId the glyph index of the component
     * @param flags component flags
     * @param argument1 first positioning argument (meaning depends on flags)
     * @param argument2 second positioning argument
     * @param transform optional 2x2 transformation matrix (may be null)
     */
    void compositeComponent(int componentGlyphId, int flags,
                            int argument1, int argument2, float[] transform);

    /**
     * Called at the end of a composite glyph.
     */
    void endCompositeGlyph();

    /**
     * Called for an empty glyph (no outline).
     *
     * @param glyphId the glyph index
     */
    void emptyGlyph(int glyphId);

    /**
     * Called at the end of processing glyph outlines.
     */
    void endGlyphTable();

    // ========================================================================
    // CFF table - Compact Font Format
    // ========================================================================

    /**
     * Called at the start of CFF data.
     *
     * @param major CFF major version
     * @param minor CFF minor version
     */
    void startCFF(int major, int minor);

    /**
     * Called with the font name from the CFF Name INDEX.
     *
     * @param name the font name
     */
    void cffFontName(String name);

    /**
     * Called for each Top DICT operator.
     *
     * @param operator the operator (see CFF specification)
     * @param operands the operand values
     */
    void cffTopDictOperator(int operator, Number[] operands);

    /**
     * Called for each string in the CFF String INDEX.
     *
     * @param sid the string identifier
     * @param value the string value
     */
    void cffString(int sid, String value);

    /**
     * Called at the start of CFF CharStrings (glyph outlines).
     *
     * @param numGlyphs the number of glyphs
     */
    void startCFFCharStrings(int numGlyphs);

    /**
     * Called for each CFF CharString (Type 2 charstring).
     *
     * @param glyphId the glyph index
     * @param charString the raw CharString data
     */
    void cffCharString(int glyphId, ByteBuffer charString);

    /**
     * Called at the end of CFF CharStrings.
     */
    void endCFFCharStrings();

    /**
     * Called at the end of CFF data.
     */
    void endCFF();

    // ========================================================================
    // kern table - Kerning (legacy)
    // ========================================================================

    /**
     * Called at the start of the 'kern' table.
     *
     * @param version table version
     * @param nTables number of subtables
     */
    void startKernTable(int version, int nTables);

    /**
     * Called for each kerning pair (format 0 subtables).
     *
     * @param leftGlyph left glyph index
     * @param rightGlyph right glyph index
     * @param value kerning value in font units
     */
    void kernPair(int leftGlyph, int rightGlyph, short value);

    /**
     * Called at the end of the 'kern' table.
     */
    void endKernTable();

    // ========================================================================
    // GPOS/GSUB tables - OpenType Layout (simplified)
    // ========================================================================

    /**
     * Called with a script record from GPOS or GSUB.
     *
     * @param table "GPOS" or "GSUB"
     * @param scriptTag 4-character script tag (e.g., "latn", "cyrl")
     */
    void layoutScript(String table, String scriptTag);

    /**
     * Called with a feature record from GPOS or GSUB.
     *
     * @param table "GPOS" or "GSUB"
     * @param featureTag 4-character feature tag (e.g., "kern", "liga")
     */
    void layoutFeature(String table, String featureTag);

    // ========================================================================
    // Unknown/raw table data
    // ========================================================================

    /**
     * Called for tables that are not specifically parsed.
     * <p>
     * This allows handlers to process additional tables if needed.
     *
     * @param tag the 4-character table tag
     * @param data the raw table data
     */
    void rawTable(String tag, ByteBuffer data);

    // ========================================================================
    // Name ID constants
    // ========================================================================

    /**
     * Standard name IDs for the 'name' table.
     */
    interface NameId {
        int COPYRIGHT = 0;
        int FONT_FAMILY = 1;
        int FONT_SUBFAMILY = 2;
        int UNIQUE_ID = 3;
        int FULL_NAME = 4;
        int VERSION = 5;
        int POSTSCRIPT_NAME = 6;
        int TRADEMARK = 7;
        int MANUFACTURER = 8;
        int DESIGNER = 9;
        int DESCRIPTION = 10;
        int VENDOR_URL = 11;
        int DESIGNER_URL = 12;
        int LICENSE = 13;
        int LICENSE_URL = 14;
        int TYPOGRAPHIC_FAMILY = 16;
        int TYPOGRAPHIC_SUBFAMILY = 17;
        int COMPATIBLE_FULL = 18;
        int SAMPLE_TEXT = 19;
        int POSTSCRIPT_CID = 20;
        int WWS_FAMILY = 21;
        int WWS_SUBFAMILY = 22;
        int LIGHT_BACKGROUND_PALETTE = 23;
        int DARK_BACKGROUND_PALETTE = 24;
        int VARIATIONS_POSTSCRIPT_PREFIX = 25;
    }

}

