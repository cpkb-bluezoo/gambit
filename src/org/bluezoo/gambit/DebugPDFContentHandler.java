/*
 * DebugPDFContentHandler.java
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A {@link PDFContentHandler} implementation that prints all received events
 * and their arguments to a {@link PrintStream} for debugging purposes.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class DebugPDFContentHandler implements PDFContentHandler {

    private final PrintStream out;
    private int indentLevel = 0;

    /**
     * Creates a new DebugPDFContentHandler that prints to {@code System.out}.
     */
    public DebugPDFContentHandler() {
        this(System.out);
    }

    /**
     * Creates a new DebugPDFContentHandler that prints to the specified {@link PrintStream}.
     *
     * @param out the PrintStream to print to
     */
    public DebugPDFContentHandler(PrintStream out) {
        this.out = out;
    }

    private void indent() {
        for (int i = 0; i < indentLevel; i++) {
            out.print("  ");
        }
    }

    private void print(String method) {
        indent();
        out.println(method);
    }

    private void print(String method, Object... args) {
        indent();
        out.print(method + "(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) out.print(", ");
            out.print(formatArg(args[i]));
        }
        out.println(")");
    }

    private String formatArg(Object arg) {
        if (arg == null) {
            return "null";
        } else if (arg instanceof ByteBuffer) {
            ByteBuffer buf = (ByteBuffer) arg;
            byte[] bytes = new byte[Math.min(buf.remaining(), 20)];
            buf.duplicate().get(bytes);
            String s = new String(bytes, StandardCharsets.ISO_8859_1);
            if (buf.remaining() > 20) {
                return "\"" + escape(s) + "...\" (" + buf.remaining() + " bytes)";
            }
            return "\"" + escape(s) + "\"";
        } else if (arg instanceof double[]) {
            return Arrays.toString((double[]) arg);
        } else if (arg instanceof Name) {
            return arg.toString();
        } else {
            return String.valueOf(arg);
        }
    }

    private String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) {
                sb.append("\\x").append(String.format("%02x", (int) c));
            } else if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Content Stream Lifecycle

    @Override
    public void startContentStream() {
        print("startContentStream");
        indentLevel++;
    }

    @Override
    public void endContentStream() {
        indentLevel--;
        print("endContentStream");
    }

    // Graphics State Operators

    @Override
    public void saveGraphicsState() {
        print("q");
    }

    @Override
    public void restoreGraphicsState() {
        print("Q");
    }

    @Override
    public void concatenateMatrix(double a, double b, double c, double d, double e, double f) {
        print("cm", a, b, c, d, e, f);
    }

    @Override
    public void setLineWidth(double width) {
        print("w", width);
    }

    @Override
    public void setLineCap(int cap) {
        print("J", cap);
    }

    @Override
    public void setLineJoin(int join) {
        print("j", join);
    }

    @Override
    public void setMiterLimit(double limit) {
        print("M", limit);
    }

    @Override
    public void setDashPattern(double[] dashArray, double dashPhase) {
        print("d", dashArray, dashPhase);
    }

    @Override
    public void setRenderingIntent(Name intent) {
        print("ri", intent);
    }

    @Override
    public void setFlatness(double flatness) {
        print("i", flatness);
    }

    @Override
    public void setGraphicsStateParameters(Name name) {
        print("gs", name);
    }

    // Path Construction Operators

    @Override
    public void moveTo(double x, double y) {
        print("m", x, y);
    }

    @Override
    public void lineTo(double x, double y) {
        print("l", x, y);
    }

    @Override
    public void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        print("c", x1, y1, x2, y2, x3, y3);
    }

    @Override
    public void curveToInitialPointReplicated(double x2, double y2, double x3, double y3) {
        print("v", x2, y2, x3, y3);
    }

    @Override
    public void curveToFinalPointReplicated(double x1, double y1, double x3, double y3) {
        print("y", x1, y1, x3, y3);
    }

    @Override
    public void closePath() {
        print("h");
    }

    @Override
    public void appendRectangle(double x, double y, double width, double height) {
        print("re", x, y, width, height);
    }

    // Path Painting Operators

    @Override
    public void stroke() {
        print("S");
    }

    @Override
    public void closeAndStroke() {
        print("s");
    }

    @Override
    public void fill() {
        print("f");
    }

    @Override
    public void fillEvenOdd() {
        print("f*");
    }

    @Override
    public void fillAndStroke() {
        print("B");
    }

    @Override
    public void fillEvenOddAndStroke() {
        print("B*");
    }

    @Override
    public void closeFillAndStroke() {
        print("b");
    }

    @Override
    public void closeFillEvenOddAndStroke() {
        print("b*");
    }

    @Override
    public void endPath() {
        print("n");
    }

    // Clipping Path Operators

    @Override
    public void clip() {
        print("W");
    }

    @Override
    public void clipEvenOdd() {
        print("W*");
    }

    // Text Object Operators

    @Override
    public void beginText() {
        print("BT");
        indentLevel++;
    }

    @Override
    public void endText() {
        indentLevel--;
        print("ET");
    }

    // Text State Operators

    @Override
    public void setCharacterSpacing(double spacing) {
        print("Tc", spacing);
    }

    @Override
    public void setWordSpacing(double spacing) {
        print("Tw", spacing);
    }

    @Override
    public void setHorizontalScaling(double scale) {
        print("Tz", scale);
    }

    @Override
    public void setTextLeading(double leading) {
        print("TL", leading);
    }

    @Override
    public void setTextFont(Name font, double size) {
        print("Tf", font, size);
    }

    @Override
    public void setTextRenderingMode(int mode) {
        print("Tr", mode);
    }

    @Override
    public void setTextRise(double rise) {
        print("Ts", rise);
    }

    // Text Positioning Operators

    @Override
    public void moveTextPosition(double tx, double ty) {
        print("Td", tx, ty);
    }

    @Override
    public void moveTextPositionSetLeading(double tx, double ty) {
        print("TD", tx, ty);
    }

    @Override
    public void setTextMatrix(double a, double b, double c, double d, double e, double f) {
        print("Tm", a, b, c, d, e, f);
    }

    @Override
    public void moveToNextLine() {
        print("T*");
    }

    // Text Showing Operators

    @Override
    public void showText(ByteBuffer text) {
        print("Tj", text);
    }

    @Override
    public void moveToNextLineAndShowText(ByteBuffer text) {
        print("'", text);
    }

    @Override
    public void setSpacingMoveToNextLineAndShowText(double wordSpacing, double charSpacing, ByteBuffer text) {
        print("\"", wordSpacing, charSpacing, text);
    }

    @Override
    public void startShowTextWithPositioning() {
        print("TJ [");
        indentLevel++;
    }

    @Override
    public void showTextKerning(Number adjustment) {
        print("  kerning", adjustment);
    }

    @Override
    public void endShowTextWithPositioning() {
        indentLevel--;
        print("]");
    }

    // Color Operators

    @Override
    public void setStrokingColorSpace(Name name) {
        print("CS", name);
    }

    @Override
    public void setNonStrokingColorSpace(Name name) {
        print("cs", name);
    }

    @Override
    public void setStrokingColor(double[] components) {
        print("SC", components);
    }

    @Override
    public void setStrokingColor(double[] components, Name pattern) {
        print("SCN", components, pattern);
    }

    @Override
    public void setNonStrokingColor(double[] components) {
        print("sc", components);
    }

    @Override
    public void setNonStrokingColor(double[] components, Name pattern) {
        print("scn", components, pattern);
    }

    @Override
    public void setStrokingGray(double gray) {
        print("G", gray);
    }

    @Override
    public void setNonStrokingGray(double gray) {
        print("g", gray);
    }

    @Override
    public void setStrokingRGB(double r, double g, double b) {
        print("RG", r, g, b);
    }

    @Override
    public void setNonStrokingRGB(double r, double g, double b) {
        print("rg", r, g, b);
    }

    @Override
    public void setStrokingCMYK(double c, double m, double y, double k) {
        print("K", c, m, y, k);
    }

    @Override
    public void setNonStrokingCMYK(double c, double m, double y, double k) {
        print("k", c, m, y, k);
    }

    // Shading Operator

    @Override
    public void paintShading(Name name) {
        print("sh", name);
    }

    // XObject Operator

    @Override
    public void paintXObject(Name name) {
        print("Do", name);
    }

    // PostScript Operator (deprecated)

    @Override
    @Deprecated
    public void paintPostScript(Name name) {
        print("PS", name);
    }

    // Inline Image Operators

    @Override
    public void beginImage() {
        print("BI");
        indentLevel++;
    }

    @Override
    public void imageData(ByteBuffer data) {
        print("ID", data);
    }

    @Override
    public void endImage() {
        indentLevel--;
        print("EI");
    }

    // Marked Content Operators

    @Override
    public void markedContentPoint(Name tag) {
        print("MP", tag);
    }

    @Override
    public void markedContentPointWithProperties(Name tag, Object properties) {
        print("DP", tag, properties);
    }

    @Override
    public void beginMarkedContent(Name tag) {
        print("BMC", tag);
        indentLevel++;
    }

    @Override
    public void beginMarkedContentWithProperties(Name tag, Object properties) {
        print("BDC", tag, properties);
        indentLevel++;
    }

    @Override
    public void endMarkedContent() {
        indentLevel--;
        print("EMC");
    }

    // Compatibility Operators

    @Override
    public void beginCompatibilitySection() {
        print("BX");
        indentLevel++;
    }

    @Override
    public void endCompatibilitySection() {
        indentLevel--;
        print("EX");
    }

    // Type 3 Font Operators

    @Override
    public void setGlyphWidth(double wx, double wy) {
        print("d0", wx, wy);
    }

    @Override
    public void setGlyphWidthAndBoundingBox(double wx, double wy, double llx, double lly, double urx, double ury) {
        print("d1", wx, wy, llx, lly, urx, ury);
    }
}

