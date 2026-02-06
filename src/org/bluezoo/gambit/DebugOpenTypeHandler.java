/*
 * DebugOpenTypeHandler.java
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

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Debug implementation of {@link OpenTypeHandler} that prints events to a stream.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class DebugOpenTypeHandler implements OpenTypeHandler {

    private final PrintStream out;
    private int indent = 0;

    public DebugOpenTypeHandler() {
        this(System.out);
    }

    public DebugOpenTypeHandler(PrintStream out) {
        this.out = out;
    }

    private void print(String format, Object... args) {
        for (int i = 0; i < indent; i++) out.print("  ");
        out.printf(format, args);
        out.println();
    }

    // Font-level events

    @Override
    public void startFont(int sfntVersion, int numTables) {
        String versionStr = (sfntVersion == 0x4F54544F) ? "OTTO (CFF)" :
                String.format("%d.%d (TrueType)", sfntVersion >> 16, sfntVersion & 0xFFFF);
        print("startFont(version=%s, tables=%d)", versionStr, numTables);
        indent++;
    }

    @Override
    public void endFont() {
        indent--;
        print("endFont");
    }

    @Override
    public void tableDirectory(String tag, int checksum, int offset, int length) {
        print("table '%s' checksum=0x%08X offset=%d length=%d", tag, checksum, offset, length);
    }

    // head table

    @Override
    public void headTable(int unitsPerEm, short xMin, short yMin, short xMax, short yMax,
                          int macStyle, int indexToLocFormat) {
        print("head: unitsPerEm=%d bbox=[%d,%d,%d,%d] macStyle=0x%X locaFormat=%s",
                unitsPerEm, xMin, yMin, xMax, yMax, macStyle,
                indexToLocFormat == 0 ? "short" : "long");
    }

    // hhea table

    @Override
    public void hheaTable(short ascender, short descender, short lineGap,
                          int advanceWidthMax, int numberOfHMetrics) {
        print("hhea: ascender=%d descender=%d lineGap=%d maxWidth=%d numHMetrics=%d",
                ascender, descender, lineGap, advanceWidthMax, numberOfHMetrics);
    }

    // vhea table

    @Override
    public void vheaTable(short ascender, short descender, short lineGap,
                          int advanceHeightMax, int numberOfVMetrics) {
        print("vhea: ascender=%d descender=%d lineGap=%d maxHeight=%d numVMetrics=%d",
                ascender, descender, lineGap, advanceHeightMax, numberOfVMetrics);
    }

    // maxp table

    @Override
    public void maxpTable(int numGlyphs) {
        print("maxp: numGlyphs=%d", numGlyphs);
    }

    // OS/2 table

    @Override
    public void os2Table(int version, short avgCharWidth, int weightClass, int widthClass,
                         int fsType, short subscriptXSize, short subscriptYSize,
                         short subscriptXOffset, short subscriptYOffset,
                         short superscriptXSize, short superscriptYSize,
                         short superscriptXOffset, short superscriptYOffset,
                         short strikeoutSize, short strikeoutPosition, short familyClass) {
        print("OS/2: version=%d avgWidth=%d weight=%d width=%d fsType=0x%X",
                version, avgCharWidth, weightClass, widthClass, fsType);
    }

    @Override
    public void os2Panose(byte[] panose) {
        print("OS/2 PANOSE: %s", Arrays.toString(panose));
    }

    @Override
    public void os2UnicodeRanges(int r1, int r2, int r3, int r4) {
        print("OS/2 UnicodeRanges: 0x%08X 0x%08X 0x%08X 0x%08X", r1, r2, r3, r4);
    }

    @Override
    public void os2Selection(String vendorID, int fsSelection, int firstChar, int lastChar) {
        print("OS/2: vendor='%s' fsSelection=0x%X charRange=%d-%d",
                vendorID, fsSelection, firstChar, lastChar);
    }

    @Override
    public void os2TypoMetrics(short typoAscender, short typoDescender, short typoLineGap,
                               int winAscent, int winDescent) {
        print("OS/2 typo: asc=%d desc=%d gap=%d winAsc=%d winDesc=%d",
                typoAscender, typoDescender, typoLineGap, winAscent, winDescent);
    }

    @Override
    public void os2Heights(short xHeight, short capHeight) {
        print("OS/2 heights: xHeight=%d capHeight=%d", xHeight, capHeight);
    }

    // name table

    @Override
    public void startNameTable(int format, int count) {
        print("startNameTable(format=%d, count=%d)", format, count);
        indent++;
    }

    @Override
    public void nameRecord(int platformID, int encodingID, int languageID, int nameID, String value) {
        String nameType = getNameIdString(nameID);
        // Truncate long values
        String displayValue = value.length() > 60 ? value.substring(0, 57) + "..." : value;
        print("name[%d] %s = \"%s\" (platform=%d, encoding=%d, lang=%d)",
                nameID, nameType, displayValue, platformID, encodingID, languageID);
    }

    @Override
    public void endNameTable() {
        indent--;
        print("endNameTable");
    }

    private String getNameIdString(int nameID) {
        switch (nameID) {
            case NameId.COPYRIGHT: return "copyright";
            case NameId.FONT_FAMILY: return "family";
            case NameId.FONT_SUBFAMILY: return "subfamily";
            case NameId.UNIQUE_ID: return "uniqueID";
            case NameId.FULL_NAME: return "fullName";
            case NameId.VERSION: return "version";
            case NameId.POSTSCRIPT_NAME: return "psName";
            case NameId.TRADEMARK: return "trademark";
            case NameId.MANUFACTURER: return "manufacturer";
            case NameId.DESIGNER: return "designer";
            case NameId.DESCRIPTION: return "description";
            default: return "name" + nameID;
        }
    }

    // cmap table

    @Override
    public void startCmapTable(int version, int numTables) {
        print("startCmapTable(version=%d, subtables=%d)", version, numTables);
        indent++;
    }

    @Override
    public void startCmapSubtable(int platformID, int encodingID, int format) {
        String platform = platformID == 0 ? "Unicode" : platformID == 1 ? "Mac" :
                platformID == 3 ? "Windows" : "Platform" + platformID;
        print("startCmapSubtable(%s, encoding=%d, format=%d)", platform, encodingID, format);
        indent++;
    }

    private int cmapMappingCount = 0;

    @Override
    public void cmapMapping(int charCode, int glyphId) {
        // Only print first few and summary
        if (cmapMappingCount < 5) {
            print("cmap: U+%04X -> glyph %d", charCode, glyphId);
        } else if (cmapMappingCount == 5) {
            print("cmap: ... (more mappings)");
        }
        cmapMappingCount++;
    }

    @Override
    public void endCmapSubtable() {
        if (cmapMappingCount > 5) {
            print("cmap: total %d mappings", cmapMappingCount);
        }
        cmapMappingCount = 0;
        indent--;
        print("endCmapSubtable");
    }

    @Override
    public void endCmapTable() {
        indent--;
        print("endCmapTable");
    }

    // hmtx table

    private int hmtxCount = 0;

    @Override
    public void startHmtxTable(int numberOfHMetrics, int numGlyphs) {
        print("startHmtxTable(hMetrics=%d, glyphs=%d)", numberOfHMetrics, numGlyphs);
        indent++;
        hmtxCount = 0;
    }

    @Override
    public void horizontalMetric(int glyphId, int advanceWidth, short lsb) {
        if (hmtxCount < 5) {
            print("hmtx[%d]: width=%d lsb=%d", glyphId, advanceWidth, lsb);
        } else if (hmtxCount == 5) {
            print("hmtx: ... (more metrics)");
        }
        hmtxCount++;
    }

    @Override
    public void endHmtxTable() {
        indent--;
        print("endHmtxTable (total %d)", hmtxCount);
    }

    // vmtx table

    @Override
    public void startVmtxTable(int numberOfVMetrics, int numGlyphs) {
        print("startVmtxTable(vMetrics=%d, glyphs=%d)", numberOfVMetrics, numGlyphs);
        indent++;
    }

    @Override
    public void verticalMetric(int glyphId, int advanceHeight, short tsb) {
        // Usually skip these
    }

    @Override
    public void endVmtxTable() {
        indent--;
        print("endVmtxTable");
    }

    // post table

    @Override
    public void postTable(float format, float italicAngle, short underlinePosition,
                          short underlineThickness, boolean isFixedPitch) {
        print("post: format=%.1f italic=%.1f° underline=%d/%d fixedPitch=%b",
                format, italicAngle, underlinePosition, underlineThickness, isFixedPitch);
    }

    private int postGlyphCount = 0;

    @Override
    public void postGlyphName(int glyphId, String name) {
        if (postGlyphCount < 5) {
            print("post glyph[%d] = '%s'", glyphId, name);
        } else if (postGlyphCount == 5) {
            print("post: ... (more glyph names)");
        }
        postGlyphCount++;
    }

    // glyf table

    @Override
    public void startGlyphTable(int numGlyphs) {
        print("startGlyphTable(numGlyphs=%d)", numGlyphs);
        indent++;
    }

    private int simpleGlyphCount = 0;
    private int compositeGlyphCount = 0;
    private int emptyGlyphCount = 0;

    @Override
    public void startSimpleGlyph(int glyphId, int numberOfContours,
                                 short xMin, short yMin, short xMax, short yMax) {
        if (simpleGlyphCount < 3) {
            print("simpleGlyph[%d]: contours=%d bbox=[%d,%d,%d,%d]",
                    glyphId, numberOfContours, xMin, yMin, xMax, yMax);
        }
        simpleGlyphCount++;
    }

    @Override
    public void startContour(int contourIndex) {
        // Skip verbose contour output
    }

    @Override
    public void contourPoint(short x, short y, boolean onCurve) {
        // Skip verbose point output
    }

    @Override
    public void endContour() {
    }

    @Override
    public void endSimpleGlyph() {
    }

    @Override
    public void startCompositeGlyph(int glyphId, short xMin, short yMin, short xMax, short yMax) {
        if (compositeGlyphCount < 3) {
            print("compositeGlyph[%d]: bbox=[%d,%d,%d,%d]", glyphId, xMin, yMin, xMax, yMax);
        }
        compositeGlyphCount++;
    }

    @Override
    public void compositeComponent(int componentGlyphId, int flags, int arg1, int arg2, float[] transform) {
        // Skip verbose output
    }

    @Override
    public void endCompositeGlyph() {
    }

    @Override
    public void emptyGlyph(int glyphId) {
        emptyGlyphCount++;
    }

    @Override
    public void endGlyphTable() {
        print("endGlyphTable: %d simple, %d composite, %d empty",
                simpleGlyphCount, compositeGlyphCount, emptyGlyphCount);
        simpleGlyphCount = 0;
        compositeGlyphCount = 0;
        emptyGlyphCount = 0;
        indent--;
    }

    // CFF table

    @Override
    public void startCFF(int major, int minor) {
        print("startCFF(version=%d.%d)", major, minor);
        indent++;
    }

    @Override
    public void cffFontName(String name) {
        print("CFF fontName: '%s'", name);
    }

    @Override
    public void cffTopDictOperator(int operator, Number[] operands) {
        print("CFF TopDict: op=%d operands=%s", operator, Arrays.toString(operands));
    }

    @Override
    public void cffString(int sid, String value) {
        // Skip verbose string output
    }

    @Override
    public void startCFFCharStrings(int numGlyphs) {
        print("startCFFCharStrings(numGlyphs=%d)", numGlyphs);
    }

    @Override
    public void cffCharString(int glyphId, ByteBuffer charString) {
        // Skip verbose charstring output
    }

    @Override
    public void endCFFCharStrings() {
        print("endCFFCharStrings");
    }

    @Override
    public void endCFF() {
        indent--;
        print("endCFF");
    }

    // kern table

    @Override
    public void startKernTable(int version, int nTables) {
        print("startKernTable(version=%d, subtables=%d)", version, nTables);
        indent++;
    }

    private int kernPairCount = 0;

    @Override
    public void kernPair(int leftGlyph, int rightGlyph, short value) {
        if (kernPairCount < 5) {
            print("kern: %d + %d = %d", leftGlyph, rightGlyph, value);
        } else if (kernPairCount == 5) {
            print("kern: ... (more pairs)");
        }
        kernPairCount++;
    }

    @Override
    public void endKernTable() {
        if (kernPairCount > 0) {
            print("endKernTable (total %d pairs)", kernPairCount);
        } else {
            print("endKernTable");
        }
        kernPairCount = 0;
        indent--;
    }

    // Layout tables

    @Override
    public void layoutScript(String table, String scriptTag) {
        print("%s script: '%s'", table, scriptTag);
    }

    @Override
    public void layoutFeature(String table, String featureTag) {
        print("%s feature: '%s'", table, featureTag);
    }

    // Raw tables

    @Override
    public void rawTable(String tag, ByteBuffer data) {
        print("rawTable '%s' (%d bytes)", tag, data.remaining());
    }

}

