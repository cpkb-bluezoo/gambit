/*
 * OpenTypeParser.java
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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for OpenType/TrueType font files.
 * <p>
 * Implements {@link StreamParser} to receive font data incrementally.
 * Since OpenType fonts are not inherently streamable (tables reference
 * each other by offset), this parser buffers all data before parsing.
 * <p>
 * Dispatches parsing events to an {@link OpenTypeHandler}.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class OpenTypeParser implements StreamParser {

    /** TrueType sfnt version (0x00010000). */
    private static final int SFNT_VERSION_TRUETYPE = 0x00010000;

    /** OpenType/CFF sfnt version ('OTTO'). */
    private static final int SFNT_VERSION_CFF = 0x4F54544F;

    /** TrueType collection version ('ttcf'). */
    private static final int TTC_TAG = 0x74746366;

    private final OpenTypeHandler handler;
    private final ByteArrayOutputStream buffer;
    private boolean open = true;

    // Table directory (populated during parsing)
    private final List<TableRecord> tables = new ArrayList<>();
    private final Map<String, TableRecord> tablesByTag = new HashMap<>();

    /**
     * Creates a new OpenType parser.
     *
     * @param handler the handler to receive parsing events
     */
    public OpenTypeParser(OpenTypeHandler handler) {
        this.handler = handler;
        this.buffer = new ByteArrayOutputStream();
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        // Buffer all data - OpenType requires random access
        int count = src.remaining();
        if (src.hasArray()) {
            buffer.write(src.array(), src.arrayOffset() + src.position(), count);
            src.position(src.position() + count);
        } else {
            byte[] temp = new byte[count];
            src.get(temp);
            buffer.write(temp);
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        if (open) {
            open = false;
            parse();
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        buffer.reset();
        tables.clear();
        tablesByTag.clear();
        open = true;
    }

    /**
     * Parses the buffered font data.
     */
    private void parse() {
        byte[] data = buffer.toByteArray();
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.BIG_ENDIAN);

        try {
            int sfntVersion = buf.getInt();

            // Check for TrueType collection
            if (sfntVersion == TTC_TAG) {
                parseTTC(buf);
                return;
            }

            parseFont(buf, sfntVersion);

        } catch (Exception e) {
            throw new PDFParseException("Error parsing OpenType font: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a TrueType Collection.
     */
    private void parseTTC(ByteBuffer buf) {
        // TTC Header
        int majorVersion = buf.getShort() & 0xFFFF;
        int minorVersion = buf.getShort() & 0xFFFF;
        int numFonts = buf.getInt();

        // Parse offsets
        int[] offsets = new int[numFonts];
        for (int i = 0; i < numFonts; i++) {
            offsets[i] = buf.getInt();
        }

        // For now, just parse the first font
        // TODO: Allow handler to select which font to parse
        if (numFonts > 0) {
            buf.position(offsets[0]);
            int sfntVersion = buf.getInt();
            parseFont(buf, sfntVersion);
        }
    }

    /**
     * Parses a single font.
     */
    private void parseFont(ByteBuffer buf, int sfntVersion) {
        int numTables = buf.getShort() & 0xFFFF;
        int searchRange = buf.getShort() & 0xFFFF;
        int entrySelector = buf.getShort() & 0xFFFF;
        int rangeShift = buf.getShort() & 0xFFFF;

        handler.startFont(sfntVersion, numTables);

        // Read table directory
        for (int i = 0; i < numTables; i++) {
            String tag = readTag(buf);
            int checksum = buf.getInt();
            int offset = buf.getInt();
            int length = buf.getInt();

            TableRecord record = new TableRecord(tag, checksum, offset, length);
            tables.add(record);
            tablesByTag.put(tag, record);

            handler.tableDirectory(tag, checksum, offset, length);
        }

        // Parse tables in dependency order
        parseHeadTable(buf);
        parseMaxpTable(buf);
        parseHheaTable(buf);
        parseVheaTable(buf);
        parseOs2Table(buf);
        parseNameTable(buf);
        parsePostTable(buf);
        parseCmapTable(buf);
        parseHmtxTable(buf);
        parseVmtxTable(buf);
        parseKernTable(buf);

        // Parse glyph outlines
        if (sfntVersion == SFNT_VERSION_CFF) {
            parseCFFTable(buf);
        } else {
            parseLocaTable(buf);
            parseGlyfTable(buf);
        }

        // Parse OpenType layout tables (simplified)
        parseGPOSTable(buf);
        parseGSUBTable(buf);

        // Pass any remaining tables as raw data
        for (TableRecord record : tables) {
            if (!record.parsed) {
                buf.position(record.offset);
                ByteBuffer tableData = buf.slice();
                tableData.limit(record.length);
                handler.rawTable(record.tag, tableData.asReadOnlyBuffer());
            }
        }

        handler.endFont();
    }

    // ========================================================================
    // Table Parsing Methods
    // ========================================================================

    private void parseHeadTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("head");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);

        int majorVersion = buf.getShort() & 0xFFFF;
        int minorVersion = buf.getShort() & 0xFFFF;
        int fontRevision = buf.getInt();
        int checksumAdjustment = buf.getInt();
        int magicNumber = buf.getInt();
        int flags = buf.getShort() & 0xFFFF;
        int unitsPerEm = buf.getShort() & 0xFFFF;
        long created = buf.getLong();
        long modified = buf.getLong();
        short xMin = buf.getShort();
        short yMin = buf.getShort();
        short xMax = buf.getShort();
        short yMax = buf.getShort();
        int macStyle = buf.getShort() & 0xFFFF;
        int lowestRecPPEM = buf.getShort() & 0xFFFF;
        short fontDirectionHint = buf.getShort();
        int indexToLocFormat = buf.getShort() & 0xFFFF;
        int glyphDataFormat = buf.getShort() & 0xFFFF;

        handler.headTable(unitsPerEm, xMin, yMin, xMax, yMax, macStyle, indexToLocFormat);
    }

    private void parseMaxpTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("maxp");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);

        int version = buf.getInt();
        int numGlyphs = buf.getShort() & 0xFFFF;

        handler.maxpTable(numGlyphs);
    }

    private void parseHheaTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("hhea");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);

        int majorVersion = buf.getShort() & 0xFFFF;
        int minorVersion = buf.getShort() & 0xFFFF;
        short ascender = buf.getShort();
        short descender = buf.getShort();
        short lineGap = buf.getShort();
        int advanceWidthMax = buf.getShort() & 0xFFFF;
        short minLeftSideBearing = buf.getShort();
        short minRightSideBearing = buf.getShort();
        short xMaxExtent = buf.getShort();
        short caretSlopeRise = buf.getShort();
        short caretSlopeRun = buf.getShort();
        short caretOffset = buf.getShort();
        buf.getShort(); // reserved
        buf.getShort(); // reserved
        buf.getShort(); // reserved
        buf.getShort(); // reserved
        short metricDataFormat = buf.getShort();
        int numberOfHMetrics = buf.getShort() & 0xFFFF;

        handler.hheaTable(ascender, descender, lineGap, advanceWidthMax, numberOfHMetrics);
    }

    private void parseVheaTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("vhea");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);

        int majorVersion = buf.getShort() & 0xFFFF;
        int minorVersion = buf.getShort() & 0xFFFF;
        short ascender = buf.getShort();
        short descender = buf.getShort();
        short lineGap = buf.getShort();
        int advanceHeightMax = buf.getShort() & 0xFFFF;
        buf.getShort(); // minTopSideBearing
        buf.getShort(); // minBottomSideBearing
        buf.getShort(); // yMaxExtent
        buf.getShort(); // caretSlopeRise
        buf.getShort(); // caretSlopeRun
        buf.getShort(); // caretOffset
        buf.getShort(); // reserved
        buf.getShort(); // reserved
        buf.getShort(); // reserved
        buf.getShort(); // reserved
        buf.getShort(); // metricDataFormat
        int numberOfVMetrics = buf.getShort() & 0xFFFF;

        handler.vheaTable(ascender, descender, lineGap, advanceHeightMax, numberOfVMetrics);
    }

    private void parseOs2Table(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("OS/2");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);

        int version = buf.getShort() & 0xFFFF;
        short avgCharWidth = buf.getShort();
        int weightClass = buf.getShort() & 0xFFFF;
        int widthClass = buf.getShort() & 0xFFFF;
        int fsType = buf.getShort() & 0xFFFF;
        short subscriptXSize = buf.getShort();
        short subscriptYSize = buf.getShort();
        short subscriptXOffset = buf.getShort();
        short subscriptYOffset = buf.getShort();
        short superscriptXSize = buf.getShort();
        short superscriptYSize = buf.getShort();
        short superscriptXOffset = buf.getShort();
        short superscriptYOffset = buf.getShort();
        short strikeoutSize = buf.getShort();
        short strikeoutPosition = buf.getShort();
        short familyClass = buf.getShort();

        handler.os2Table(version, avgCharWidth, weightClass, widthClass, fsType,
                subscriptXSize, subscriptYSize, subscriptXOffset, subscriptYOffset,
                superscriptXSize, superscriptYSize, superscriptXOffset, superscriptYOffset,
                strikeoutSize, strikeoutPosition, familyClass);

        // PANOSE
        byte[] panose = new byte[10];
        buf.get(panose);
        handler.os2Panose(panose);

        // Unicode ranges
        int ulUnicodeRange1 = buf.getInt();
        int ulUnicodeRange2 = buf.getInt();
        int ulUnicodeRange3 = buf.getInt();
        int ulUnicodeRange4 = buf.getInt();
        handler.os2UnicodeRanges(ulUnicodeRange1, ulUnicodeRange2, ulUnicodeRange3, ulUnicodeRange4);

        // Vendor ID
        byte[] vendorIdBytes = new byte[4];
        buf.get(vendorIdBytes);
        String vendorID = new String(vendorIdBytes, StandardCharsets.US_ASCII);

        int fsSelection = buf.getShort() & 0xFFFF;
        int firstCharIndex = buf.getShort() & 0xFFFF;
        int lastCharIndex = buf.getShort() & 0xFFFF;
        handler.os2Selection(vendorID, fsSelection, firstCharIndex, lastCharIndex);

        // Version 0 ends here
        if (version >= 1) {
            short typoAscender = buf.getShort();
            short typoDescender = buf.getShort();
            short typoLineGap = buf.getShort();
            int winAscent = buf.getShort() & 0xFFFF;
            int winDescent = buf.getShort() & 0xFFFF;
            handler.os2TypoMetrics(typoAscender, typoDescender, typoLineGap, winAscent, winDescent);
        }

        if (version >= 2) {
            buf.getInt(); // ulCodePageRange1
            buf.getInt(); // ulCodePageRange2
            short xHeight = buf.getShort();
            short capHeight = buf.getShort();
            handler.os2Heights(xHeight, capHeight);
        }
    }

    private void parseNameTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("name");
        if (record == null) return;
        record.parsed = true;

        int tableStart = record.offset;
        buf.position(tableStart);

        int format = buf.getShort() & 0xFFFF;
        int count = buf.getShort() & 0xFFFF;
        int stringOffset = buf.getShort() & 0xFFFF;

        handler.startNameTable(format, count);

        for (int i = 0; i < count; i++) {
            int platformID = buf.getShort() & 0xFFFF;
            int encodingID = buf.getShort() & 0xFFFF;
            int languageID = buf.getShort() & 0xFFFF;
            int nameID = buf.getShort() & 0xFFFF;
            int length = buf.getShort() & 0xFFFF;
            int offset = buf.getShort() & 0xFFFF;

            // Save position
            int savedPosition = buf.position();

            // Read the string
            buf.position(tableStart + stringOffset + offset);
            byte[] stringData = new byte[length];
            buf.get(stringData);

            String value = decodeNameString(platformID, encodingID, stringData);

            handler.nameRecord(platformID, encodingID, languageID, nameID, value);

            // Restore position
            buf.position(savedPosition);
        }

        handler.endNameTable();
    }

    private String decodeNameString(int platformID, int encodingID, byte[] data) {
        // Platform 0 (Unicode) and Platform 3 (Windows) use UTF-16BE
        if (platformID == 0 || platformID == 3) {
            return new String(data, StandardCharsets.UTF_16BE);
        }
        // Platform 1 (Mac) - various encodings, default to MacRoman
        return new String(data, StandardCharsets.ISO_8859_1);
    }

    private void parsePostTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("post");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);

        int version = buf.getInt();
        float format = (version >> 16) + ((version & 0xFFFF) / 65536.0f);
        int italicAngleFixed = buf.getInt();
        float italicAngle = (italicAngleFixed >> 16) + ((italicAngleFixed & 0xFFFF) / 65536.0f);
        short underlinePosition = buf.getShort();
        short underlineThickness = buf.getShort();
        int isFixedPitch = buf.getInt();
        buf.getInt(); // minMemType42
        buf.getInt(); // maxMemType42
        buf.getInt(); // minMemType1
        buf.getInt(); // maxMemType1

        handler.postTable(format, italicAngle, underlinePosition, underlineThickness,
                isFixedPitch != 0);

        // Parse glyph names for format 2.0
        if (version == 0x00020000) {
            int numGlyphs = buf.getShort() & 0xFFFF;
            int[] glyphNameIndex = new int[numGlyphs];
            for (int i = 0; i < numGlyphs; i++) {
                glyphNameIndex[i] = buf.getShort() & 0xFFFF;
            }

            // Read Pascal strings
            List<String> extraNames = new ArrayList<>();
            while (buf.hasRemaining() && buf.position() < record.offset + record.length) {
                int nameLength = buf.get() & 0xFF;
                if (nameLength == 0 || buf.remaining() < nameLength) break;
                byte[] nameBytes = new byte[nameLength];
                buf.get(nameBytes);
                extraNames.add(new String(nameBytes, StandardCharsets.US_ASCII));
            }

            for (int i = 0; i < numGlyphs; i++) {
                int index = glyphNameIndex[i];
                String name;
                if (index < 258) {
                    name = STANDARD_GLYPH_NAMES[index];
                } else {
                    int extraIndex = index - 258;
                    name = extraIndex < extraNames.size() ? extraNames.get(extraIndex) : ".notdef";
                }
                handler.postGlyphName(i, name);
            }
        }
    }

    private void parseCmapTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("cmap");
        if (record == null) return;
        record.parsed = true;

        int tableStart = record.offset;
        buf.position(tableStart);

        int version = buf.getShort() & 0xFFFF;
        int numTables = buf.getShort() & 0xFFFF;

        handler.startCmapTable(version, numTables);

        // Read encoding records
        List<int[]> encodings = new ArrayList<>();
        for (int i = 0; i < numTables; i++) {
            int platformID = buf.getShort() & 0xFFFF;
            int encodingID = buf.getShort() & 0xFFFF;
            int subtableOffset = buf.getInt();
            encodings.add(new int[] { platformID, encodingID, subtableOffset });
        }

        // Parse each subtable
        for (int[] encoding : encodings) {
            int platformID = encoding[0];
            int encodingID = encoding[1];
            int subtableOffset = encoding[2];

            buf.position(tableStart + subtableOffset);
            parseCmapSubtable(buf, platformID, encodingID);
        }

        handler.endCmapTable();
    }

    private void parseCmapSubtable(ByteBuffer buf, int platformID, int encodingID) {
        int format = buf.getShort() & 0xFFFF;
        handler.startCmapSubtable(platformID, encodingID, format);

        switch (format) {
            case 0:
                parseCmapFormat0(buf);
                break;
            case 4:
                parseCmapFormat4(buf);
                break;
            case 6:
                parseCmapFormat6(buf);
                break;
            case 12:
                parseCmapFormat12(buf);
                break;
            // Add more formats as needed
        }

        handler.endCmapSubtable();
    }

    private void parseCmapFormat0(ByteBuffer buf) {
        int length = buf.getShort() & 0xFFFF;
        int language = buf.getShort() & 0xFFFF;
        for (int charCode = 0; charCode < 256; charCode++) {
            int glyphId = buf.get() & 0xFF;
            if (glyphId != 0) {
                handler.cmapMapping(charCode, glyphId);
            }
        }
    }

    private void parseCmapFormat4(ByteBuffer buf) {
        int length = buf.getShort() & 0xFFFF;
        int language = buf.getShort() & 0xFFFF;
        int segCountX2 = buf.getShort() & 0xFFFF;
        int segCount = segCountX2 / 2;
        buf.getShort(); // searchRange
        buf.getShort(); // entrySelector
        buf.getShort(); // rangeShift

        int[] endCode = new int[segCount];
        int[] startCode = new int[segCount];
        int[] idDelta = new int[segCount];
        int[] idRangeOffset = new int[segCount];

        for (int i = 0; i < segCount; i++) {
            endCode[i] = buf.getShort() & 0xFFFF;
        }
        buf.getShort(); // reservedPad

        for (int i = 0; i < segCount; i++) {
            startCode[i] = buf.getShort() & 0xFFFF;
        }
        for (int i = 0; i < segCount; i++) {
            idDelta[i] = buf.getShort();
        }

        int idRangeOffsetStart = buf.position();
        for (int i = 0; i < segCount; i++) {
            idRangeOffset[i] = buf.getShort() & 0xFFFF;
        }

        // Emit mappings
        for (int i = 0; i < segCount; i++) {
            if (endCode[i] == 0xFFFF) continue; // Skip sentinel

            for (int charCode = startCode[i]; charCode <= endCode[i]; charCode++) {
                int glyphId;
                if (idRangeOffset[i] == 0) {
                    glyphId = (charCode + idDelta[i]) & 0xFFFF;
                } else {
                    int glyphIdOffset = idRangeOffsetStart + (i * 2) + idRangeOffset[i]
                            + (charCode - startCode[i]) * 2;
                    int savedPos = buf.position();
                    buf.position(glyphIdOffset);
                    glyphId = buf.getShort() & 0xFFFF;
                    buf.position(savedPos);
                    if (glyphId != 0) {
                        glyphId = (glyphId + idDelta[i]) & 0xFFFF;
                    }
                }
                if (glyphId != 0) {
                    handler.cmapMapping(charCode, glyphId);
                }
            }
        }
    }

    private void parseCmapFormat6(ByteBuffer buf) {
        int length = buf.getShort() & 0xFFFF;
        int language = buf.getShort() & 0xFFFF;
        int firstCode = buf.getShort() & 0xFFFF;
        int entryCount = buf.getShort() & 0xFFFF;

        for (int i = 0; i < entryCount; i++) {
            int glyphId = buf.getShort() & 0xFFFF;
            if (glyphId != 0) {
                handler.cmapMapping(firstCode + i, glyphId);
            }
        }
    }

    private void parseCmapFormat12(ByteBuffer buf) {
        buf.getShort(); // reserved
        int length = buf.getInt();
        int language = buf.getInt();
        int numGroups = buf.getInt();

        for (int i = 0; i < numGroups; i++) {
            int startCharCode = buf.getInt();
            int endCharCode = buf.getInt();
            int startGlyphID = buf.getInt();

            for (int charCode = startCharCode; charCode <= endCharCode; charCode++) {
                int glyphId = startGlyphID + (charCode - startCharCode);
                handler.cmapMapping(charCode, glyphId);
            }
        }
    }

    private void parseHmtxTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("hmtx");
        TableRecord hheaRecord = tablesByTag.get("hhea");
        TableRecord maxpRecord = tablesByTag.get("maxp");
        if (record == null || hheaRecord == null || maxpRecord == null) return;
        record.parsed = true;

        // We need numberOfHMetrics from hhea and numGlyphs from maxp
        buf.position(hheaRecord.offset + 34);
        int numberOfHMetrics = buf.getShort() & 0xFFFF;

        buf.position(maxpRecord.offset + 4);
        int numGlyphs = buf.getShort() & 0xFFFF;

        handler.startHmtxTable(numberOfHMetrics, numGlyphs);

        buf.position(record.offset);
        int lastAdvanceWidth = 0;

        for (int i = 0; i < numberOfHMetrics; i++) {
            int advanceWidth = buf.getShort() & 0xFFFF;
            short lsb = buf.getShort();
            lastAdvanceWidth = advanceWidth;
            handler.horizontalMetric(i, advanceWidth, lsb);
        }

        // Remaining glyphs use the last advance width
        for (int i = numberOfHMetrics; i < numGlyphs; i++) {
            short lsb = buf.getShort();
            handler.horizontalMetric(i, lastAdvanceWidth, lsb);
        }

        handler.endHmtxTable();
    }

    private void parseVmtxTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("vmtx");
        TableRecord vheaRecord = tablesByTag.get("vhea");
        TableRecord maxpRecord = tablesByTag.get("maxp");
        if (record == null || vheaRecord == null || maxpRecord == null) return;
        record.parsed = true;

        buf.position(vheaRecord.offset + 34);
        int numberOfVMetrics = buf.getShort() & 0xFFFF;

        buf.position(maxpRecord.offset + 4);
        int numGlyphs = buf.getShort() & 0xFFFF;

        handler.startVmtxTable(numberOfVMetrics, numGlyphs);

        buf.position(record.offset);
        int lastAdvanceHeight = 0;

        for (int i = 0; i < numberOfVMetrics; i++) {
            int advanceHeight = buf.getShort() & 0xFFFF;
            short tsb = buf.getShort();
            lastAdvanceHeight = advanceHeight;
            handler.verticalMetric(i, advanceHeight, tsb);
        }

        for (int i = numberOfVMetrics; i < numGlyphs; i++) {
            short tsb = buf.getShort();
            handler.verticalMetric(i, lastAdvanceHeight, tsb);
        }

        handler.endVmtxTable();
    }

    private void parseKernTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("kern");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);
        int version = buf.getShort() & 0xFFFF;
        int nTables = buf.getShort() & 0xFFFF;

        handler.startKernTable(version, nTables);

        for (int t = 0; t < nTables; t++) {
            int subVersion = buf.getShort() & 0xFFFF;
            int length = buf.getShort() & 0xFFFF;
            int coverage = buf.getShort() & 0xFFFF;
            int format = coverage >> 8;

            if (format == 0) {
                int nPairs = buf.getShort() & 0xFFFF;
                buf.getShort(); // searchRange
                buf.getShort(); // entrySelector
                buf.getShort(); // rangeShift

                for (int i = 0; i < nPairs; i++) {
                    int left = buf.getShort() & 0xFFFF;
                    int right = buf.getShort() & 0xFFFF;
                    short value = buf.getShort();
                    handler.kernPair(left, right, value);
                }
            }
        }

        handler.endKernTable();
    }

    private int[] locaTable;
    private int locaFormat;

    private void parseLocaTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("loca");
        TableRecord headRecord = tablesByTag.get("head");
        TableRecord maxpRecord = tablesByTag.get("maxp");
        if (record == null || headRecord == null || maxpRecord == null) return;
        record.parsed = true;

        // Get indexToLocFormat from head
        buf.position(headRecord.offset + 50);
        locaFormat = buf.getShort() & 0xFFFF;

        // Get numGlyphs from maxp
        buf.position(maxpRecord.offset + 4);
        int numGlyphs = buf.getShort() & 0xFFFF;

        buf.position(record.offset);
        locaTable = new int[numGlyphs + 1];

        if (locaFormat == 0) {
            for (int i = 0; i <= numGlyphs; i++) {
                locaTable[i] = (buf.getShort() & 0xFFFF) * 2;
            }
        } else {
            for (int i = 0; i <= numGlyphs; i++) {
                locaTable[i] = buf.getInt();
            }
        }
    }

    private void parseGlyfTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("glyf");
        if (record == null || locaTable == null) return;
        record.parsed = true;

        int numGlyphs = locaTable.length - 1;
        handler.startGlyphTable(numGlyphs);

        for (int glyphId = 0; glyphId < numGlyphs; glyphId++) {
            int offset = locaTable[glyphId];
            int nextOffset = locaTable[glyphId + 1];
            int glyphLength = nextOffset - offset;

            if (glyphLength == 0) {
                handler.emptyGlyph(glyphId);
                continue;
            }

            buf.position(record.offset + offset);
            parseGlyph(buf, glyphId);
        }

        handler.endGlyphTable();
    }

    private void parseGlyph(ByteBuffer buf, int glyphId) {
        short numberOfContours = buf.getShort();
        short xMin = buf.getShort();
        short yMin = buf.getShort();
        short xMax = buf.getShort();
        short yMax = buf.getShort();

        if (numberOfContours >= 0) {
            // Simple glyph
            handler.startSimpleGlyph(glyphId, numberOfContours, xMin, yMin, xMax, yMax);
            parseSimpleGlyph(buf, numberOfContours);
            handler.endSimpleGlyph();
        } else {
            // Composite glyph
            handler.startCompositeGlyph(glyphId, xMin, yMin, xMax, yMax);
            parseCompositeGlyph(buf);
            handler.endCompositeGlyph();
        }
    }

    private void parseSimpleGlyph(ByteBuffer buf, int numberOfContours) {
        if (numberOfContours == 0) return;

        int[] endPtsOfContours = new int[numberOfContours];
        for (int i = 0; i < numberOfContours; i++) {
            endPtsOfContours[i] = buf.getShort() & 0xFFFF;
        }

        int instructionLength = buf.getShort() & 0xFFFF;
        buf.position(buf.position() + instructionLength); // Skip instructions

        int numPoints = endPtsOfContours[numberOfContours - 1] + 1;

        // Read flags
        byte[] flags = new byte[numPoints];
        for (int i = 0; i < numPoints; ) {
            byte flag = buf.get();
            flags[i++] = flag;
            if ((flag & 0x08) != 0) { // Repeat flag
                int repeatCount = buf.get() & 0xFF;
                for (int j = 0; j < repeatCount && i < numPoints; j++) {
                    flags[i++] = flag;
                }
            }
        }

        // Read x coordinates
        short[] xCoordinates = new short[numPoints];
        short x = 0;
        for (int i = 0; i < numPoints; i++) {
            int flag = flags[i] & 0xFF;
            if ((flag & 0x02) != 0) { // x-Short Vector
                int dx = buf.get() & 0xFF;
                x += ((flag & 0x10) != 0) ? dx : -dx;
            } else if ((flag & 0x10) == 0) { // Not same
                x += buf.getShort();
            }
            xCoordinates[i] = x;
        }

        // Read y coordinates
        short[] yCoordinates = new short[numPoints];
        short y = 0;
        for (int i = 0; i < numPoints; i++) {
            int flag = flags[i] & 0xFF;
            if ((flag & 0x04) != 0) { // y-Short Vector
                int dy = buf.get() & 0xFF;
                y += ((flag & 0x20) != 0) ? dy : -dy;
            } else if ((flag & 0x20) == 0) { // Not same
                y += buf.getShort();
            }
            yCoordinates[i] = y;
        }

        // Emit contours
        int pointIndex = 0;
        for (int contour = 0; contour < numberOfContours; contour++) {
            handler.startContour(contour);
            int endPoint = endPtsOfContours[contour];
            while (pointIndex <= endPoint) {
                boolean onCurve = (flags[pointIndex] & 0x01) != 0;
                handler.contourPoint(xCoordinates[pointIndex], yCoordinates[pointIndex], onCurve);
                pointIndex++;
            }
            handler.endContour();
        }
    }

    private void parseCompositeGlyph(ByteBuffer buf) {
        int flags;
        do {
            flags = buf.getShort() & 0xFFFF;
            int glyphIndex = buf.getShort() & 0xFFFF;

            int arg1, arg2;
            if ((flags & 0x0001) != 0) { // ARG_1_AND_2_ARE_WORDS
                arg1 = buf.getShort();
                arg2 = buf.getShort();
            } else {
                arg1 = buf.get();
                arg2 = buf.get();
            }

            float[] transform = null;
            if ((flags & 0x0008) != 0) { // WE_HAVE_A_SCALE
                float scale = buf.getShort() / 16384.0f;
                transform = new float[] { scale, 0, 0, scale };
            } else if ((flags & 0x0040) != 0) { // WE_HAVE_AN_X_AND_Y_SCALE
                float scaleX = buf.getShort() / 16384.0f;
                float scaleY = buf.getShort() / 16384.0f;
                transform = new float[] { scaleX, 0, 0, scaleY };
            } else if ((flags & 0x0080) != 0) { // WE_HAVE_A_TWO_BY_TWO
                float xx = buf.getShort() / 16384.0f;
                float yx = buf.getShort() / 16384.0f;
                float xy = buf.getShort() / 16384.0f;
                float yy = buf.getShort() / 16384.0f;
                transform = new float[] { xx, yx, xy, yy };
            }

            handler.compositeComponent(glyphIndex, flags, arg1, arg2, transform);

        } while ((flags & 0x0020) != 0); // MORE_COMPONENTS
    }

    private void parseCFFTable(ByteBuffer buf) {
        TableRecord record = tablesByTag.get("CFF ");
        if (record == null) return;
        record.parsed = true;

        buf.position(record.offset);

        int major = buf.get() & 0xFF;
        int minor = buf.get() & 0xFF;
        int hdrSize = buf.get() & 0xFF;
        int offSize = buf.get() & 0xFF;

        handler.startCFF(major, minor);

        // Skip to after header
        buf.position(record.offset + hdrSize);

        // Parse Name INDEX
        List<byte[]> names = parseIndex(buf);
        if (!names.isEmpty()) {
            handler.cffFontName(new String(names.get(0), StandardCharsets.US_ASCII));
        }

        // Parse Top DICT INDEX
        List<byte[]> topDicts = parseIndex(buf);
        if (!topDicts.isEmpty()) {
            parseCFFDict(ByteBuffer.wrap(topDicts.get(0)));
        }

        // Parse String INDEX
        List<byte[]> strings = parseIndex(buf);
        for (int i = 0; i < strings.size(); i++) {
            handler.cffString(i + 391, new String(strings.get(i), StandardCharsets.US_ASCII));
        }

        // Parse Global Subr INDEX (skip for now)
        parseIndex(buf);

        // CharStrings parsing would go here - requires Top DICT CharStrings offset
        // For now, emit end
        handler.endCFF();
    }

    private List<byte[]> parseIndex(ByteBuffer buf) {
        List<byte[]> result = new ArrayList<>();
        int count = buf.getShort() & 0xFFFF;
        if (count == 0) return result;

        int offSize = buf.get() & 0xFF;
        int[] offsets = new int[count + 1];
        for (int i = 0; i <= count; i++) {
            offsets[i] = readOffset(buf, offSize);
        }

        int dataStart = buf.position() - 1;
        for (int i = 0; i < count; i++) {
            int length = offsets[i + 1] - offsets[i];
            byte[] data = new byte[length];
            buf.position(dataStart + offsets[i]);
            buf.get(data);
            result.add(data);
        }

        buf.position(dataStart + offsets[count]);
        return result;
    }

    private int readOffset(ByteBuffer buf, int offSize) {
        int value = 0;
        for (int i = 0; i < offSize; i++) {
            value = (value << 8) | (buf.get() & 0xFF);
        }
        return value;
    }

    private void parseCFFDict(ByteBuffer buf) {
        List<Number> operands = new ArrayList<>();
        while (buf.hasRemaining()) {
            int b0 = buf.get() & 0xFF;

            if (b0 <= 21) {
                // Operator
                int op = b0;
                if (b0 == 12) {
                    op = (12 << 8) | (buf.get() & 0xFF);
                }
                handler.cffTopDictOperator(op, operands.toArray(new Number[0]));
                operands.clear();
            } else if (b0 == 28) {
                operands.add((short) buf.getShort());
            } else if (b0 == 29) {
                operands.add(buf.getInt());
            } else if (b0 == 30) {
                operands.add(parseCFFReal(buf));
            } else if (b0 >= 32 && b0 <= 246) {
                operands.add(b0 - 139);
            } else if (b0 >= 247 && b0 <= 250) {
                int b1 = buf.get() & 0xFF;
                operands.add((b0 - 247) * 256 + b1 + 108);
            } else if (b0 >= 251 && b0 <= 254) {
                int b1 = buf.get() & 0xFF;
                operands.add(-(b0 - 251) * 256 - b1 - 108);
            }
        }
    }

    private double parseCFFReal(ByteBuffer buf) {
        StringBuilder sb = new StringBuilder();
        boolean done = false;
        while (!done && buf.hasRemaining()) {
            int b = buf.get() & 0xFF;
            for (int nibbleIndex = 0; nibbleIndex < 2 && !done; nibbleIndex++) {
                int nibble = (nibbleIndex == 0) ? (b >> 4) : (b & 0x0F);
                switch (nibble) {
                    case 0x0: case 0x1: case 0x2: case 0x3: case 0x4:
                    case 0x5: case 0x6: case 0x7: case 0x8: case 0x9:
                        sb.append((char) ('0' + nibble));
                        break;
                    case 0xa: sb.append('.'); break;
                    case 0xb: sb.append('E'); break;
                    case 0xc: sb.append("E-"); break;
                    case 0xe: sb.append('-'); break;
                    case 0xf: done = true; break;
                }
            }
        }
        return Double.parseDouble(sb.toString());
    }

    private void parseGPOSTable(ByteBuffer buf) {
        parseLayoutTable(buf, "GPOS");
    }

    private void parseGSUBTable(ByteBuffer buf) {
        parseLayoutTable(buf, "GSUB");
    }

    private void parseLayoutTable(ByteBuffer buf, String tableName) {
        TableRecord record = tablesByTag.get(tableName);
        if (record == null) return;
        record.parsed = true;

        int tableStart = record.offset;
        buf.position(tableStart);

        int majorVersion = buf.getShort() & 0xFFFF;
        int minorVersion = buf.getShort() & 0xFFFF;
        int scriptListOffset = buf.getShort() & 0xFFFF;
        int featureListOffset = buf.getShort() & 0xFFFF;
        int lookupListOffset = buf.getShort() & 0xFFFF;

        // Parse script list
        buf.position(tableStart + scriptListOffset);
        int scriptCount = buf.getShort() & 0xFFFF;
        for (int i = 0; i < scriptCount; i++) {
            String tag = readTag(buf);
            buf.getShort(); // offset
            handler.layoutScript(tableName, tag);
        }

        // Parse feature list
        buf.position(tableStart + featureListOffset);
        int featureCount = buf.getShort() & 0xFFFF;
        for (int i = 0; i < featureCount; i++) {
            String tag = readTag(buf);
            buf.getShort(); // offset
            handler.layoutFeature(tableName, tag);
        }
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    private String readTag(ByteBuffer buf) {
        byte[] tag = new byte[4];
        buf.get(tag);
        return new String(tag, StandardCharsets.US_ASCII);
    }

    /**
     * Table record from the table directory.
     */
    private static class TableRecord {
        final String tag;
        final int checksum;
        final int offset;
        final int length;
        boolean parsed;

        TableRecord(String tag, int checksum, int offset, int length) {
            this.tag = tag;
            this.checksum = checksum;
            this.offset = offset;
            this.length = length;
        }
    }

    /**
     * Command-line entry point for testing.
     *
     * @param args font file path
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: OpenTypeParser <font-file>");
            System.exit(1);
        }

        try (java.nio.channels.FileChannel channel =
                java.nio.channels.FileChannel.open(
                        java.nio.file.Paths.get(args[0]),
                        java.nio.file.StandardOpenOption.READ)) {

            OpenTypeParser parser = new OpenTypeParser(new DebugOpenTypeHandler());
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.flip();
            parser.write(buffer);
            parser.close();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Standard glyph names for post format 2.0
    private static final String[] STANDARD_GLYPH_NAMES = {
        ".notdef", ".null", "nonmarkingreturn", "space", "exclam", "quotedbl", "numbersign",
        "dollar", "percent", "ampersand", "quotesingle", "parenleft", "parenright", "asterisk",
        "plus", "comma", "hyphen", "period", "slash", "zero", "one", "two", "three", "four",
        "five", "six", "seven", "eight", "nine", "colon", "semicolon", "less", "equal", "greater",
        "question", "at", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N",
        "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "bracketleft", "backslash",
        "bracketright", "asciicircum", "underscore", "grave", "a", "b", "c", "d", "e", "f", "g",
        "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y",
        "z", "braceleft", "bar", "braceright", "asciitilde", "Adieresis", "Aring", "Ccedilla",
        "Eacute", "Ntilde", "Odieresis", "Udieresis", "aacute", "agrave", "acircumflex",
        "adieresis", "atilde", "aring", "ccedilla", "eacute", "egrave", "ecircumflex", "edieresis",
        "iacute", "igrave", "icircumflex", "idieresis", "ntilde", "oacute", "ograve", "ocircumflex",
        "odieresis", "otilde", "uacute", "ugrave", "ucircumflex", "udieresis", "dagger", "degree",
        "cent", "sterling", "section", "bullet", "paragraph", "germandbls", "registered",
        "copyright", "trademark", "acute", "dieresis", "notequal", "AE", "Oslash", "infinity",
        "plusminus", "lessequal", "greaterequal", "yen", "mu", "partialdiff", "summation",
        "product", "pi", "integral", "ordfeminine", "ordmasculine", "Omega", "ae", "oslash",
        "questiondown", "exclamdown", "logicalnot", "radical", "florin", "approxequal", "Delta",
        "guillemotleft", "guillemotright", "ellipsis", "nonbreakingspace", "Agrave", "Atilde",
        "Otilde", "OE", "oe", "endash", "emdash", "quotedblleft", "quotedblright", "quoteleft",
        "quoteright", "divide", "lozenge", "ydieresis", "Ydieresis", "fraction", "currency",
        "guilsinglleft", "guilsinglright", "fi", "fl", "daggerdbl", "periodcentered",
        "quotesinglbase", "quotedblbase", "perthousand", "Acircumflex", "Ecircumflex", "Aacute",
        "Edieresis", "Egrave", "Iacute", "Icircumflex", "Idieresis", "Igrave", "Oacute",
        "Ocircumflex", "apple", "Ograve", "Uacute", "Ucircumflex", "Ugrave", "dotlessi",
        "circumflex", "tilde", "macron", "breve", "dotaccent", "ring", "cedilla", "hungarumlaut",
        "ogonek", "caron", "Lslash", "lslash", "Scaron", "scaron", "Zcaron", "zcaron",
        "brokenbar", "Eth", "eth", "Yacute", "yacute", "Thorn", "thorn", "minus", "multiply",
        "onesuperior", "twosuperior", "threesuperior", "onehalf", "onequarter", "threequarters",
        "franc", "Gbreve", "gbreve", "Idotaccent", "Scedilla", "scedilla", "Cacute", "cacute",
        "Ccaron", "ccaron", "dcroat"
    };

}

