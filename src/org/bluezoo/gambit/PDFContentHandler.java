/*
 * PDFContentHandler.java
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
 * Callback handler for receiving PDF content stream operations.
 * <p>
 * This interface is implemented by applications that wish to receive
 * notifications of drawing operations as the parser processes PDF content
 * streams. Content streams define the visual appearance of pages and other
 * content through a sequence of operators that manipulate graphics state,
 * construct paths, render text, and paint images.
 * <p>
 * Each method in this interface corresponds to one or more PDF content
 * stream operators. Method names are descriptive rather than using the
 * terse PDF operator names.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public interface PDFContentHandler {

    // ========================================================================
    // Content Stream Delimiters
    // ========================================================================

    /**
     * Signals the start of a content stream.
     * <p>
     * Called when the parser begins processing a content stream, such as
     * a page content stream or form XObject.
     */
    void startContentStream();

    /**
     * Signals the end of a content stream.
     */
    void endContentStream();

    // ========================================================================
    // Graphics State Operators
    // ========================================================================

    /**
     * Save the current graphics state on the graphics state stack.
     * <p>
     * PDF operator: <b>q</b>
     */
    void saveGraphicsState();

    /**
     * Restore the graphics state from the graphics state stack.
     * <p>
     * PDF operator: <b>Q</b>
     */
    void restoreGraphicsState();

    /**
     * Modify the current transformation matrix (CTM) by concatenating
     * the specified matrix.
     * <p>
     * PDF operator: <b>cm</b>
     *
     * @param a scale X / rotate
     * @param b rotate / skew
     * @param c rotate / skew
     * @param d scale Y / rotate
     * @param e translate X
     * @param f translate Y
     */
    void concatenateMatrix(double a, double b, double c, double d,
                           double e, double f);

    /**
     * Set the line width in the graphics state.
     * <p>
     * PDF operator: <b>w</b>
     *
     * @param width the line width
     */
    void setLineWidth(double width);

    /**
     * Set the line cap style in the graphics state.
     * <p>
     * PDF operator: <b>J</b>
     *
     * @param cap the line cap style (0=butt, 1=round, 2=projecting square)
     */
    void setLineCap(int cap);

    /**
     * Set the line join style in the graphics state.
     * <p>
     * PDF operator: <b>j</b>
     *
     * @param join the line join style (0=miter, 1=round, 2=bevel)
     */
    void setLineJoin(int join);

    /**
     * Set the miter limit in the graphics state.
     * <p>
     * PDF operator: <b>M</b>
     *
     * @param limit the miter limit
     */
    void setMiterLimit(double limit);

    /**
     * Set the line dash pattern in the graphics state.
     * <p>
     * PDF operator: <b>d</b>
     *
     * @param dashArray the dash array
     * @param dashPhase the dash phase
     */
    void setDashPattern(double[] dashArray, double dashPhase);

    /**
     * Set the color rendering intent in the graphics state.
     * <p>
     * PDF operator: <b>ri</b>
     *
     * @param intent the rendering intent name
     */
    void setRenderingIntent(Name intent);

    /**
     * Set the flatness tolerance in the graphics state.
     * <p>
     * PDF operator: <b>i</b>
     *
     * @param flatness the flatness tolerance
     */
    void setFlatness(double flatness);

    /**
     * Set the graphics state from a named graphics state parameter dictionary.
     * <p>
     * PDF operator: <b>gs</b>
     *
     * @param name the name of the graphics state parameter dictionary
     */
    void setGraphicsStateParameters(Name name);

    // ========================================================================
    // Path Construction Operators
    // ========================================================================

    /**
     * Begin a new subpath by moving the current point.
     * <p>
     * PDF operator: <b>m</b>
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    void moveTo(double x, double y);

    /**
     * Append a straight line segment to the current path.
     * <p>
     * PDF operator: <b>l</b>
     *
     * @param x the x coordinate of the endpoint
     * @param y the y coordinate of the endpoint
     */
    void lineTo(double x, double y);

    /**
     * Append a cubic Bézier curve to the current path.
     * <p>
     * PDF operator: <b>c</b>
     *
     * @param x1 x coordinate of first control point
     * @param y1 y coordinate of first control point
     * @param x2 x coordinate of second control point
     * @param y2 y coordinate of second control point
     * @param x3 x coordinate of endpoint
     * @param y3 y coordinate of endpoint
     */
    void curveTo(double x1, double y1, double x2, double y2,
                 double x3, double y3);

    /**
     * Append a cubic Bézier curve to the current path with the first
     * control point at the current point.
     * <p>
     * PDF operator: <b>v</b>
     *
     * @param x2 x coordinate of second control point
     * @param y2 y coordinate of second control point
     * @param x3 x coordinate of endpoint
     * @param y3 y coordinate of endpoint
     */
    void curveToInitialPointReplicated(double x2, double y2,
                                       double x3, double y3);

    /**
     * Append a cubic Bézier curve to the current path with the second
     * control point at the endpoint.
     * <p>
     * PDF operator: <b>y</b>
     *
     * @param x1 x coordinate of first control point
     * @param y1 y coordinate of first control point
     * @param x3 x coordinate of endpoint
     * @param y3 y coordinate of endpoint
     */
    void curveToFinalPointReplicated(double x1, double y1,
                                     double x3, double y3);

    /**
     * Close the current subpath by appending a straight line segment
     * from the current point to the starting point.
     * <p>
     * PDF operator: <b>h</b>
     */
    void closePath();

    /**
     * Append a rectangle to the current path as a complete subpath.
     * <p>
     * PDF operator: <b>re</b>
     *
     * @param x x coordinate of lower-left corner
     * @param y y coordinate of lower-left corner
     * @param width width of the rectangle
     * @param height height of the rectangle
     */
    void appendRectangle(double x, double y, double width, double height);

    // ========================================================================
    // Path Painting Operators
    // ========================================================================

    /**
     * Stroke the current path.
     * <p>
     * PDF operator: <b>S</b>
     */
    void stroke();

    /**
     * Close and stroke the current path.
     * <p>
     * PDF operator: <b>s</b>
     */
    void closeAndStroke();

    /**
     * Fill the current path using the nonzero winding number rule.
     * <p>
     * PDF operators: <b>f</b>, <b>F</b>
     */
    void fill();

    /**
     * Fill the current path using the even-odd rule.
     * <p>
     * PDF operator: <b>f*</b>
     */
    void fillEvenOdd();

    /**
     * Fill and stroke the current path using the nonzero winding number rule.
     * <p>
     * PDF operator: <b>B</b>
     */
    void fillAndStroke();

    /**
     * Fill and stroke the current path using the even-odd rule.
     * <p>
     * PDF operator: <b>B*</b>
     */
    void fillEvenOddAndStroke();

    /**
     * Close, fill, and stroke the current path using nonzero winding number rule.
     * <p>
     * PDF operator: <b>b</b>
     */
    void closeFillAndStroke();

    /**
     * Close, fill, and stroke the current path using the even-odd rule.
     * <p>
     * PDF operator: <b>b*</b>
     */
    void closeFillEvenOddAndStroke();

    /**
     * End the current path without filling or stroking.
     * <p>
     * PDF operator: <b>n</b>
     */
    void endPath();

    // ========================================================================
    // Clipping Path Operators
    // ========================================================================

    /**
     * Modify the clipping path using the nonzero winding number rule.
     * <p>
     * PDF operator: <b>W</b>
     */
    void clip();

    /**
     * Modify the clipping path using the even-odd rule.
     * <p>
     * PDF operator: <b>W*</b>
     */
    void clipEvenOdd();

    // ========================================================================
    // Text Object Operators
    // ========================================================================

    /**
     * Begin a text object.
     * <p>
     * PDF operator: <b>BT</b>
     */
    void beginText();

    /**
     * End a text object.
     * <p>
     * PDF operator: <b>ET</b>
     */
    void endText();

    // ========================================================================
    // Text State Operators
    // ========================================================================

    /**
     * Set the character spacing.
     * <p>
     * PDF operator: <b>Tc</b>
     *
     * @param spacing the character spacing
     */
    void setCharacterSpacing(double spacing);

    /**
     * Set the word spacing.
     * <p>
     * PDF operator: <b>Tw</b>
     *
     * @param spacing the word spacing
     */
    void setWordSpacing(double spacing);

    /**
     * Set the horizontal text scaling.
     * <p>
     * PDF operator: <b>Tz</b>
     *
     * @param scale the horizontal scaling (percentage)
     */
    void setHorizontalScaling(double scale);

    /**
     * Set the text leading.
     * <p>
     * PDF operator: <b>TL</b>
     *
     * @param leading the text leading
     */
    void setTextLeading(double leading);

    /**
     * Set the text font and size.
     * <p>
     * PDF operator: <b>Tf</b>
     *
     * @param font the name of the font resource
     * @param size the font size
     */
    void setTextFont(Name font, double size);

    /**
     * Set the text rendering mode.
     * <p>
     * PDF operator: <b>Tr</b>
     *
     * @param mode the rendering mode (0-7)
     */
    void setTextRenderingMode(int mode);

    /**
     * Set the text rise.
     * <p>
     * PDF operator: <b>Ts</b>
     *
     * @param rise the text rise
     */
    void setTextRise(double rise);

    // ========================================================================
    // Text Positioning Operators
    // ========================================================================

    /**
     * Move the text position by the specified offset.
     * <p>
     * PDF operator: <b>Td</b>
     *
     * @param tx the horizontal offset
     * @param ty the vertical offset
     */
    void moveTextPosition(double tx, double ty);

    /**
     * Move the text position and set the text leading.
     * <p>
     * PDF operator: <b>TD</b>
     *
     * @param tx the horizontal offset
     * @param ty the vertical offset (also sets leading to -ty)
     */
    void moveTextPositionSetLeading(double tx, double ty);

    /**
     * Set the text matrix and text line matrix.
     * <p>
     * PDF operator: <b>Tm</b>
     *
     * @param a matrix element
     * @param b matrix element
     * @param c matrix element
     * @param d matrix element
     * @param e matrix element (x translation)
     * @param f matrix element (y translation)
     */
    void setTextMatrix(double a, double b, double c, double d,
                       double e, double f);

    /**
     * Move to the start of the next line.
     * <p>
     * PDF operator: <b>T*</b>
     */
    void moveToNextLine();

    // ========================================================================
    // Text Showing Operators
    // ========================================================================

    /**
     * Show a text string.
     * <p>
     * PDF operator: <b>Tj</b>
     *
     * @param text the text string (as raw bytes from the PDF)
     */
    void showText(ByteBuffer text);

    /**
     * Move to the next line and show a text string.
     * <p>
     * PDF operator: <b>'</b> (single quote)
     *
     * @param text the text string
     */
    void moveToNextLineAndShowText(ByteBuffer text);

    /**
     * Set word and character spacing, move to the next line, and show text.
     * <p>
     * PDF operator: <b>"</b> (double quote)
     *
     * @param wordSpacing the word spacing
     * @param charSpacing the character spacing
     * @param text the text string
     */
    void setSpacingMoveToNextLineAndShowText(double wordSpacing,
                                             double charSpacing,
                                             ByteBuffer text);

    /**
     * Begin showing text with individual glyph positioning.
     * <p>
     * PDF operator: <b>TJ</b> (start of array)
     * <p>
     * After this callback, the handler will receive a sequence of
     * {@link #showText(ByteBuffer)} and {@link #showTextKerning(Number)}
     * callbacks for the text strings and positioning adjustments,
     * followed by {@link #endShowTextWithPositioning()}.
     */
    void startShowTextWithPositioning();

    /**
     * Receive a kerning/positioning adjustment within a TJ array.
     * <p>
     * PDF operator: <b>TJ</b> (numeric element)
     * <p>
     * The number represents a position adjustment in thousandths of a unit
     * of text space. Positive values move the next glyph left (for horizontal
     * writing modes), negative values move it right.
     *
     * @param adjustment the positioning adjustment
     */
    void showTextKerning(Number adjustment);

    /**
     * End showing text with individual glyph positioning.
     * <p>
     * PDF operator: <b>TJ</b> (end of array)
     */
    void endShowTextWithPositioning();

    // ========================================================================
    // Color Operators
    // ========================================================================

    /**
     * Set the stroking color space.
     * <p>
     * PDF operator: <b>CS</b>
     *
     * @param name the color space name
     */
    void setStrokingColorSpace(Name name);

    /**
     * Set the non-stroking color space.
     * <p>
     * PDF operator: <b>cs</b>
     *
     * @param name the color space name
     */
    void setNonStrokingColorSpace(Name name);

    /**
     * Set the stroking color.
     * <p>
     * PDF operators: <b>SC</b>, <b>SCN</b>
     *
     * @param components the color component values
     */
    void setStrokingColor(double[] components);

    /**
     * Set the stroking color with an optional pattern name.
     * <p>
     * PDF operator: <b>SCN</b>
     *
     * @param components the color component values (may be empty for patterns)
     * @param pattern the pattern name, or null if not a pattern
     */
    void setStrokingColor(double[] components, Name pattern);

    /**
     * Set the non-stroking color.
     * <p>
     * PDF operators: <b>sc</b>, <b>scn</b>
     *
     * @param components the color component values
     */
    void setNonStrokingColor(double[] components);

    /**
     * Set the non-stroking color with an optional pattern name.
     * <p>
     * PDF operator: <b>scn</b>
     *
     * @param components the color component values (may be empty for patterns)
     * @param pattern the pattern name, or null if not a pattern
     */
    void setNonStrokingColor(double[] components, Name pattern);

    /**
     * Set the stroking color to a gray value.
     * <p>
     * PDF operator: <b>G</b>
     *
     * @param gray the gray value (0.0 = black, 1.0 = white)
     */
    void setStrokingGray(double gray);

    /**
     * Set the non-stroking color to a gray value.
     * <p>
     * PDF operator: <b>g</b>
     *
     * @param gray the gray value (0.0 = black, 1.0 = white)
     */
    void setNonStrokingGray(double gray);

    /**
     * Set the stroking color to an RGB value.
     * <p>
     * PDF operator: <b>RG</b>
     *
     * @param r the red component (0.0 to 1.0)
     * @param g the green component (0.0 to 1.0)
     * @param b the blue component (0.0 to 1.0)
     */
    void setStrokingRGB(double r, double g, double b);

    /**
     * Set the non-stroking color to an RGB value.
     * <p>
     * PDF operator: <b>rg</b>
     *
     * @param r the red component (0.0 to 1.0)
     * @param g the green component (0.0 to 1.0)
     * @param b the blue component (0.0 to 1.0)
     */
    void setNonStrokingRGB(double r, double g, double b);

    /**
     * Set the stroking color to a CMYK value.
     * <p>
     * PDF operator: <b>K</b>
     *
     * @param c the cyan component (0.0 to 1.0)
     * @param m the magenta component (0.0 to 1.0)
     * @param y the yellow component (0.0 to 1.0)
     * @param k the black component (0.0 to 1.0)
     */
    void setStrokingCMYK(double c, double m, double y, double k);

    /**
     * Set the non-stroking color to a CMYK value.
     * <p>
     * PDF operator: <b>k</b>
     *
     * @param c the cyan component (0.0 to 1.0)
     * @param m the magenta component (0.0 to 1.0)
     * @param y the yellow component (0.0 to 1.0)
     * @param k the black component (0.0 to 1.0)
     */
    void setNonStrokingCMYK(double c, double m, double y, double k);

    // ========================================================================
    // Shading Operator
    // ========================================================================

    /**
     * Paint the shape and color defined by a shading dictionary.
     * <p>
     * PDF operator: <b>sh</b>
     *
     * @param name the name of the shading resource
     */
    void paintShading(Name name);

    // ========================================================================
    // External Object Operator
    // ========================================================================

    /**
     * Paint an external object (XObject).
     * <p>
     * PDF operator: <b>Do</b>
     *
     * @param name the name of the XObject resource
     */
    void paintXObject(Name name);

    /**
     * Paint a PostScript XObject.
     * <p>
     * PDF operator: <b>PS</b>
     * <p>
     * <b>Note:</b> This is a legacy operator from PDF 1.1 that was deprecated
     * in PDF 1.2. It is rarely encountered in modern PDF files. PostScript
     * XObjects (Type 1 XObjects) allowed embedding arbitrary PostScript code,
     * which posed security and portability concerns.
     *
     * @param name the name of the PostScript XObject resource
     * @deprecated Legacy PDF 1.1 operator, deprecated since PDF 1.2
     */
    @Deprecated
    void paintPostScript(Name name);

    // ========================================================================
    // Inline Image Operators
    // ========================================================================

    /**
     * Begin an inline image.
     * <p>
     * PDF operator: <b>BI</b>
     * <p>
     * After this callback, the handler will receive key/value pairs for
     * the image dictionary via the standard {@link PDFHandler} callbacks
     * (the parser will deliver these through the PDFHandler), followed by
     * one or more {@link #imageData(ByteBuffer)} callbacks containing the
     * image data, and finally {@link #endImage()}.
     */
    void beginImage();

    /**
     * Receive inline image data.
     * <p>
     * PDF operator: <b>ID</b> (implicit)
     * <p>
     * This method may be called multiple times to deliver the image data
     * in chunks. The ByteBuffer is in read mode with position at the start
     * of the data and limit at the end.
     * <p>
     * The handler should process the data immediately or copy it, as the
     * buffer may be reused by the parser after this method returns.
     *
     * @param data the image data
     */
    void imageData(ByteBuffer data);

    /**
     * End an inline image.
     * <p>
     * PDF operator: <b>EI</b>
     */
    void endImage();

    // ========================================================================
    // Marked Content Operators
    // ========================================================================

    /**
     * Define a marked-content point.
     * <p>
     * PDF operator: <b>MP</b>
     *
     * @param tag the tag name
     */
    void markedContentPoint(Name tag);

    /**
     * Define a marked-content point with a property list.
     * <p>
     * PDF operator: <b>DP</b>
     *
     * @param tag the tag name
     * @param properties the properties dictionary name or inline dictionary
     */
    void markedContentPointWithProperties(Name tag, Object properties);

    /**
     * Begin a marked-content sequence.
     * <p>
     * PDF operator: <b>BMC</b>
     *
     * @param tag the tag name
     */
    void beginMarkedContent(Name tag);

    /**
     * Begin a marked-content sequence with a property list.
     * <p>
     * PDF operator: <b>BDC</b>
     *
     * @param tag the tag name
     * @param properties the properties dictionary name or inline dictionary
     */
    void beginMarkedContentWithProperties(Name tag, Object properties);

    /**
     * End a marked-content sequence.
     * <p>
     * PDF operator: <b>EMC</b>
     */
    void endMarkedContent();

    // ========================================================================
    // Compatibility Operators
    // ========================================================================

    /**
     * Begin a compatibility section.
     * <p>
     * PDF operator: <b>BX</b>
     */
    void beginCompatibilitySection();

    /**
     * End a compatibility section.
     * <p>
     * PDF operator: <b>EX</b>
     */
    void endCompatibilitySection();

    // ========================================================================
    // Type 3 Font Operators
    // ========================================================================

    /**
     * Set the glyph width for a Type 3 font glyph.
     * <p>
     * PDF operator: <b>d0</b>
     *
     * @param wx the horizontal displacement
     * @param wy the vertical displacement (typically 0)
     */
    void setGlyphWidth(double wx, double wy);

    /**
     * Set the glyph width and bounding box for a Type 3 font glyph.
     * <p>
     * PDF operator: <b>d1</b>
     *
     * @param wx the horizontal displacement
     * @param wy the vertical displacement (typically 0)
     * @param llx lower-left x of bounding box
     * @param lly lower-left y of bounding box
     * @param urx upper-right x of bounding box
     * @param ury upper-right y of bounding box
     */
    void setGlyphWidthAndBoundingBox(double wx, double wy,
                                     double llx, double lly,
                                     double urx, double ury);

}

