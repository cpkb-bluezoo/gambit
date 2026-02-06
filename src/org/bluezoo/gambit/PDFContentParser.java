package org.bluezoo.gambit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses PDF content streams and dispatches events to a {@link PDFContentHandler}.
 * <p>
 * Content streams use a postfix notation where operands precede the operator.
 * This parser accumulates operands on a stack and dispatches to the appropriate
 * handler method when an operator is encountered.
 * <p>
 * This parser implements {@link StreamParser} (and thus {@link java.nio.channels.WritableByteChannel})
 * for incremental parsing. Data may arrive in chunks via {@code write()}, and the parser
 * handles partial tokens at buffer boundaries by leaving them unconsumed for the next call.
 * <p>
 * Note: The {@code Do} operator (paintXObject) presents a challenge in an
 * event-driven model. The handler receives the event but must coordinate
 * XObject resolution and painting asynchronously, as we cannot resolve
 * the XObject inline during content stream parsing.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class PDFContentParser implements StreamParser {

    private final PDFContentHandler handler;
    private final PDFHandler pdfHandler; // For inline image dictionary parsing
    
    // Operand stack
    private final List<Object> operands = new ArrayList<>();
    
    // State tracking
    private boolean started = false;
    private boolean open = true;

    /**
     * Creates a new content stream parser.
     *
     * @param handler the content handler to receive events
     * @param pdfHandler the PDF handler for inline image dictionary events (may be null)
     */
    public PDFContentParser(PDFContentHandler handler, PDFHandler pdfHandler) {
        this.handler = handler;
        this.pdfHandler = pdfHandler;
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        int startPosition = buffer.position();
        
        if (!started) {
            handler.startContentStream();
            started = true;
        }
        
        while (buffer.hasRemaining()) {
            // Mark position for potential rollback on incomplete token
            int tokenStart = buffer.position();
            
            skipWhitespaceAndComments(buffer);
            if (!buffer.hasRemaining()) {
                break;
            }
            
            tokenStart = buffer.position();
            int b = peek(buffer);
            
            if (b == '(') {
                byte[] str = readLiteralString(buffer);
                if (str == null) {
                    // Incomplete - rollback
                    buffer.position(tokenStart);
                    break;
                }
                operands.add(str);
            } else if (b == '<') {
                if (peekAt(buffer, 1) == '<') {
                    Object dict = readDictionary(buffer);
                    if (dict == null) {
                        buffer.position(tokenStart);
                        break;
                    }
                    operands.add(dict);
                } else {
                    byte[] hex = readHexString(buffer);
                    if (hex == null) {
                        buffer.position(tokenStart);
                        break;
                    }
                    operands.add(hex);
                }
            } else if (b == '/') {
                Name name = readName(buffer);
                if (name == null) {
                    buffer.position(tokenStart);
                    break;
                }
                operands.add(name);
            } else if (b == '[') {
                Object arr = readArray(buffer);
                if (arr == null) {
                    buffer.position(tokenStart);
                    break;
                }
                operands.add(arr);
            } else if (b == '-' || b == '+' || b == '.' || (b >= '0' && b <= '9')) {
                Number num = readNumber(buffer);
                if (num == null) {
                    buffer.position(tokenStart);
                    break;
                }
                operands.add(num);
            } else if (isAlpha(b)) {
                String keyword = readKeyword(buffer);
                if (keyword == null || keyword.isEmpty()) {
                    buffer.position(tokenStart);
                    break;
                }
                if ("true".equals(keyword)) {
                    operands.add(Boolean.TRUE);
                } else if ("false".equals(keyword)) {
                    operands.add(Boolean.FALSE);
                } else if ("null".equals(keyword)) {
                    operands.add(null);
                } else {
                    // It's an operator
                    if (!dispatchOperator(keyword, buffer)) {
                        // Operator needs more data (e.g., inline image)
                        buffer.position(tokenStart);
                        break;
                    }
                    operands.clear();
                }
            } else {
                // Skip unknown byte
                buffer.get();
            }
        }
        
        return buffer.position() - startPosition;
    }

    @Override
    public void close() throws IOException {
        if (started) {
            handler.endContentStream();
            started = false;
        }
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        operands.clear();
        started = false;
        open = true;
    }

    // ========== Operator Dispatch ==========

    /**
     * Dispatches an operator to the handler.
     * 
     * @param op the operator name
     * @param buffer the data buffer (needed for inline images)
     * @return true if operator was fully processed, false if more data needed
     */
    private boolean dispatchOperator(String op, ByteBuffer buffer) {
        switch (op) {
            // Graphics State Operators
            case "q":
                handler.saveGraphicsState();
                break;
            case "Q":
                handler.restoreGraphicsState();
                break;
            case "cm":
                handler.concatenateMatrix(
                    getDouble(0), getDouble(1), getDouble(2),
                    getDouble(3), getDouble(4), getDouble(5));
                break;
            case "w":
                handler.setLineWidth(getDouble(0));
                break;
            case "J":
                handler.setLineCap(getInt(0));
                break;
            case "j":
                handler.setLineJoin(getInt(0));
                break;
            case "M":
                handler.setMiterLimit(getDouble(0));
                break;
            case "d":
                handler.setDashPattern(getDoubleArray(0), getDouble(1));
                break;
            case "ri":
                handler.setRenderingIntent(getName(0));
                break;
            case "i":
                handler.setFlatness(getDouble(0));
                break;
            case "gs":
                handler.setGraphicsStateParameters(getName(0));
                break;

            // Path Construction Operators
            case "m":
                handler.moveTo(getDouble(0), getDouble(1));
                break;
            case "l":
                handler.lineTo(getDouble(0), getDouble(1));
                break;
            case "c":
                handler.curveTo(
                    getDouble(0), getDouble(1),
                    getDouble(2), getDouble(3),
                    getDouble(4), getDouble(5));
                break;
            case "v":
                handler.curveToInitialPointReplicated(
                    getDouble(0), getDouble(1),
                    getDouble(2), getDouble(3));
                break;
            case "y":
                handler.curveToFinalPointReplicated(
                    getDouble(0), getDouble(1),
                    getDouble(2), getDouble(3));
                break;
            case "h":
                handler.closePath();
                break;
            case "re":
                handler.appendRectangle(
                    getDouble(0), getDouble(1),
                    getDouble(2), getDouble(3));
                break;

            // Path Painting Operators
            case "S":
                handler.stroke();
                break;
            case "s":
                handler.closeAndStroke();
                break;
            case "f":
            case "F":
                handler.fill();
                break;
            case "f*":
                handler.fillEvenOdd();
                break;
            case "B":
                handler.fillAndStroke();
                break;
            case "B*":
                handler.fillEvenOddAndStroke();
                break;
            case "b":
                handler.closeFillAndStroke();
                break;
            case "b*":
                handler.closeFillEvenOddAndStroke();
                break;
            case "n":
                handler.endPath();
                break;

            // Clipping Path Operators
            case "W":
                handler.clip();
                break;
            case "W*":
                handler.clipEvenOdd();
                break;

            // Text Object Operators
            case "BT":
                handler.beginText();
                break;
            case "ET":
                handler.endText();
                break;

            // Text State Operators
            case "Tc":
                handler.setCharacterSpacing(getDouble(0));
                break;
            case "Tw":
                handler.setWordSpacing(getDouble(0));
                break;
            case "Tz":
                handler.setHorizontalScaling(getDouble(0));
                break;
            case "TL":
                handler.setTextLeading(getDouble(0));
                break;
            case "Tf":
                handler.setTextFont(getName(0), getDouble(1));
                break;
            case "Tr":
                handler.setTextRenderingMode(getInt(0));
                break;
            case "Ts":
                handler.setTextRise(getDouble(0));
                break;

            // Text Positioning Operators
            case "Td":
                handler.moveTextPosition(getDouble(0), getDouble(1));
                break;
            case "TD":
                handler.moveTextPositionSetLeading(getDouble(0), getDouble(1));
                break;
            case "Tm":
                handler.setTextMatrix(
                    getDouble(0), getDouble(1),
                    getDouble(2), getDouble(3),
                    getDouble(4), getDouble(5));
                break;
            case "T*":
                handler.moveToNextLine();
                break;

            // Text Showing Operators
            case "Tj":
                handler.showText(getByteBuffer(0));
                break;
            case "'":
                handler.moveToNextLineAndShowText(getByteBuffer(0));
                break;
            case "\"":
                handler.setSpacingMoveToNextLineAndShowText(
                    getDouble(0), getDouble(1), getByteBuffer(2));
                break;
            case "TJ":
                dispatchTJ();
                break;

            // Color Operators
            case "CS":
                handler.setStrokingColorSpace(getName(0));
                break;
            case "cs":
                handler.setNonStrokingColorSpace(getName(0));
                break;
            case "SC":
                handler.setStrokingColor(getAllDoubles());
                break;
            case "SCN":
                dispatchSCN(true);
                break;
            case "sc":
                handler.setNonStrokingColor(getAllDoubles());
                break;
            case "scn":
                dispatchSCN(false);
                break;
            case "G":
                handler.setStrokingGray(getDouble(0));
                break;
            case "g":
                handler.setNonStrokingGray(getDouble(0));
                break;
            case "RG":
                handler.setStrokingRGB(getDouble(0), getDouble(1), getDouble(2));
                break;
            case "rg":
                handler.setNonStrokingRGB(getDouble(0), getDouble(1), getDouble(2));
                break;
            case "K":
                handler.setStrokingCMYK(
                    getDouble(0), getDouble(1), getDouble(2), getDouble(3));
                break;
            case "k":
                handler.setNonStrokingCMYK(
                    getDouble(0), getDouble(1), getDouble(2), getDouble(3));
                break;

            // Shading Operator
            case "sh":
                handler.paintShading(getName(0));
                break;

            // XObject Operator
            case "Do":
                handler.paintXObject(getName(0));
                break;

            // Inline Image Operators
            case "BI":
                return parseInlineImage(buffer);

            // Marked Content Operators
            case "MP":
                handler.markedContentPoint(getName(0));
                break;
            case "DP":
                handler.markedContentPointWithProperties(
                    getName(0), getNameOrDict(1));
                break;
            case "BMC":
                handler.beginMarkedContent(getName(0));
                break;
            case "BDC":
                handler.beginMarkedContentWithProperties(
                    getName(0), getNameOrDict(1));
                break;
            case "EMC":
                handler.endMarkedContent();
                break;

            // Compatibility Operators
            case "BX":
                handler.beginCompatibilitySection();
                break;
            case "EX":
                handler.endCompatibilitySection();
                break;

            // Type 3 Font Operators
            case "d0":
                handler.setGlyphWidth(getDouble(0), getDouble(1));
                break;
            case "d1":
                handler.setGlyphWidthAndBoundingBox(
                    getDouble(0), getDouble(1),
                    getDouble(2), getDouble(3),
                    getDouble(4), getDouble(5));
                break;

            // Legacy PostScript Operator
            case "PS":
                handler.paintPostScript(getName(0));
                break;

            default:
                // Unknown operator - ignore
                break;
        }
        return true;
    }

    /**
     * Dispatches TJ (show text with positioning) operator.
     */
    @SuppressWarnings("unchecked")
    private void dispatchTJ() {
        Object arg = operands.isEmpty() ? null : operands.get(0);
        if (!(arg instanceof List)) {
            return;
        }
        
        handler.startShowTextWithPositioning();
        
        List<Object> array = (List<Object>) arg;
        for (Object element : array) {
            if (element instanceof Number) {
                handler.showTextKerning((Number) element);
            } else if (element instanceof ByteBuffer) {
                handler.showText((ByteBuffer) element);
            } else if (element instanceof byte[]) {
                handler.showText(ByteBuffer.wrap((byte[]) element));
            }
        }
        
        handler.endShowTextWithPositioning();
    }

    /**
     * Dispatches SCN/scn (set color with optional pattern name).
     */
    private void dispatchSCN(boolean stroking) {
        Name patternName = null;
        List<Double> components = new ArrayList<>();
        
        for (Object op : operands) {
            if (op instanceof Name) {
                patternName = (Name) op;
            } else if (op instanceof Number) {
                components.add(((Number) op).doubleValue());
            }
        }
        
        double[] comps = new double[components.size()];
        for (int i = 0; i < components.size(); i++) {
            comps[i] = components.get(i);
        }
        
        if (stroking) {
            handler.setStrokingColor(comps, patternName);
        } else {
            handler.setNonStrokingColor(comps, patternName);
        }
    }

    /**
     * Parses an inline image (BI ... ID ... EI).
     * 
     * @param buffer the data buffer
     * @return true if fully parsed, false if more data needed
     */
    private boolean parseInlineImage(ByteBuffer buffer) {
        int startPos = buffer.position();
        
        handler.beginImage();
        
        // Parse image dictionary until ID
        skipWhitespaceAndComments(buffer);
        
        while (buffer.hasRemaining()) {
            skipWhitespaceAndComments(buffer);
            if (!buffer.hasRemaining()) {
                buffer.position(startPos);
                return false;
            }
            
            // Check for ID keyword
            if (peek(buffer) == 'I' && peekAt(buffer, 1) == 'D') {
                buffer.get(); // 'I'
                buffer.get(); // 'D'
                // Skip single whitespace after ID
                if (buffer.hasRemaining() && isWhitespace(peek(buffer))) {
                    buffer.get();
                }
                break;
            }
            
            // Read key-value pair
            if (peek(buffer) == '/') {
                Name key = readName(buffer);
                if (key == null) {
                    buffer.position(startPos);
                    return false;
                }
                if (pdfHandler != null) {
                    pdfHandler.key(key);
                }
                
                skipWhitespaceAndComments(buffer);
                Object value = readInlineImageValue(buffer);
                if (value == null && buffer.hasRemaining()) {
                    // Parse error, skip
                    buffer.position(startPos);
                    return false;
                }
                
                if (pdfHandler != null) {
                    emitValueToHandler(value);
                }
            } else {
                // Skip unknown
                buffer.get();
            }
        }
        
        if (!buffer.hasRemaining()) {
            buffer.position(startPos);
            return false;
        }
        
        // Find EI - it should be preceded by whitespace
        int dataStart = buffer.position();
        int eiPos = -1;
        
        while (buffer.hasRemaining()) {
            int pos = buffer.position();
            int b = buffer.get() & 0xFF;
            
            if (isWhitespace(b) && buffer.remaining() >= 2) {
                int e = buffer.get() & 0xFF;
                int i = buffer.get() & 0xFF;
                if (e == 'E' && i == 'I') {
                    // Check that EI is followed by whitespace or end
                    if (!buffer.hasRemaining() || isWhitespace(peek(buffer))) {
                        eiPos = pos;
                        break;
                    }
                }
                buffer.position(pos + 1); // Continue searching
            }
        }
        
        if (eiPos < 0) {
            // EI not found - need more data
            buffer.position(startPos);
            return false;
        }
        
        // Extract image data
        int dataEnd = eiPos;
        if (dataEnd > dataStart) {
            ByteBuffer imageData = buffer.duplicate();
            imageData.position(dataStart);
            imageData.limit(dataEnd);
            handler.imageData(imageData.slice());
        }
        
        handler.endImage();
        return true;
    }

    private void emitValueToHandler(Object value) {
        if (value == null) {
            pdfHandler.nullValue();
        } else if (value instanceof Boolean) {
            pdfHandler.booleanValue((Boolean) value);
        } else if (value instanceof Number) {
            pdfHandler.numberValue((Number) value);
        } else if (value instanceof String) {
            pdfHandler.stringValue((String) value);
        } else if (value instanceof Name) {
            pdfHandler.nameValue((Name) value);
        } else if (value instanceof byte[]) {
            pdfHandler.stringValue(new String((byte[]) value, StandardCharsets.ISO_8859_1));
        }
    }

    private Object readInlineImageValue(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) return null;
        int b = peek(buffer);
        
        if (b == '/') {
            return readName(buffer);
        } else if (b == '(') {
            return readLiteralString(buffer);
        } else if (b == '<') {
            return readHexString(buffer);
        } else if (b == '[') {
            return readArray(buffer);
        } else if (b == '-' || b == '+' || b == '.' || (b >= '0' && b <= '9')) {
            return readNumber(buffer);
        } else if (isAlpha(b)) {
            String keyword = readKeyword(buffer);
            if ("true".equals(keyword)) {
                return Boolean.TRUE;
            } else if ("false".equals(keyword)) {
                return Boolean.FALSE;
            } else if ("null".equals(keyword)) {
                return null;
            }
            // Abbreviated inline image key names
            return new Name(keyword);
        }
        
        return null;
    }

    // ========== Operand Accessors ==========

    private double getDouble(int index) {
        if (index < operands.size()) {
            Object op = operands.get(index);
            if (op instanceof Number) {
                return ((Number) op).doubleValue();
            }
        }
        return 0.0;
    }

    private int getInt(int index) {
        if (index < operands.size()) {
            Object op = operands.get(index);
            if (op instanceof Number) {
                return ((Number) op).intValue();
            }
        }
        return 0;
    }

    private Name getName(int index) {
        if (index < operands.size()) {
            Object op = operands.get(index);
            if (op instanceof Name) {
                return (Name) op;
            }
        }
        return null;
    }

    private ByteBuffer getByteBuffer(int index) {
        if (index < operands.size()) {
            Object op = operands.get(index);
            if (op instanceof ByteBuffer) {
                return (ByteBuffer) op;
            } else if (op instanceof byte[]) {
                return ByteBuffer.wrap((byte[]) op);
            }
        }
        return ByteBuffer.allocate(0);
    }

    private double[] getDoubleArray(int index) {
        if (index < operands.size()) {
            Object op = operands.get(index);
            if (op instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) op;
                double[] result = new double[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Object element = list.get(i);
                    if (element instanceof Number) {
                        result[i] = ((Number) element).doubleValue();
                    }
                }
                return result;
            }
        }
        return new double[0];
    }

    private double[] getAllDoubles() {
        List<Double> list = new ArrayList<>();
        for (Object op : operands) {
            if (op instanceof Number) {
                list.add(((Number) op).doubleValue());
            }
        }
        double[] result = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    private Object getNameOrDict(int index) {
        if (index < operands.size()) {
            return operands.get(index);
        }
        return null;
    }

    // ========== Lexer Methods ==========

    private int peek(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return buffer.get(buffer.position()) & 0xFF;
    }

    private int peekAt(ByteBuffer buffer, int offset) {
        int pos = buffer.position() + offset;
        if (pos >= buffer.limit()) {
            return -1;
        }
        return buffer.get(pos) & 0xFF;
    }

    private void skipWhitespaceAndComments(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            int b = peek(buffer);
            if (isWhitespace(b)) {
                buffer.get();
            } else if (b == '%') {
                // Skip comment until end of line
                while (buffer.hasRemaining()) {
                    int c = buffer.get() & 0xFF;
                    if (c == '\r' || c == '\n') {
                        break;
                    }
                }
            } else {
                break;
            }
        }
    }

    private boolean isWhitespace(int b) {
        return b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32;
    }

    private boolean isDelimiter(int b) {
        return b == '(' || b == ')' || b == '<' || b == '>' ||
               b == '[' || b == ']' || b == '{' || b == '}' ||
               b == '/' || b == '%';
    }

    private boolean isAlpha(int b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') ||
               b == '\'' || b == '"' || b == '*';
    }

    private Number readNumber(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) return null;
        
        StringBuilder sb = new StringBuilder();
        boolean isReal = false;
        
        int b = peek(buffer);
        if (b == '-' || b == '+') {
            sb.append((char) buffer.get());
        }
        
        while (buffer.hasRemaining()) {
            b = peek(buffer);
            if (b >= '0' && b <= '9') {
                sb.append((char) buffer.get());
            } else if (b == '.') {
                isReal = true;
                sb.append((char) buffer.get());
            } else {
                break;
            }
        }
        
        String str = sb.toString();
        if (str.isEmpty() || str.equals("-") || str.equals("+") || str.equals(".")) {
            return 0;
        }
        
        if (isReal) {
            return Double.parseDouble(str);
        } else {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return Long.parseLong(str);
            }
        }
    }

    private String readKeyword(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) return null;
        
        StringBuilder sb = new StringBuilder();
        
        while (buffer.hasRemaining()) {
            int b = peek(buffer);
            if (isWhitespace(b) || isDelimiter(b)) {
                break;
            }
            sb.append((char) buffer.get());
        }
        
        return sb.toString();
    }

    private Name readName(ByteBuffer buffer) {
        if (!buffer.hasRemaining() || buffer.get() != '/') {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        
        while (buffer.hasRemaining()) {
            int b = peek(buffer);
            if (isWhitespace(b) || isDelimiter(b)) {
                break;
            }
            buffer.get();
            if (b == '#' && buffer.remaining() >= 2) {
                int h1 = buffer.get() & 0xFF;
                int h2 = buffer.get() & 0xFF;
                int value = (hexValue(h1) << 4) | hexValue(h2);
                sb.append((char) value);
            } else {
                sb.append((char) b);
            }
        }
        
        return new Name(sb.toString());
    }

    private byte[] readLiteralString(ByteBuffer buffer) {
        if (!buffer.hasRemaining() || buffer.get() != '(') {
            return null;
        }
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int depth = 1;
        
        while (buffer.hasRemaining() && depth > 0) {
            int b = buffer.get() & 0xFF;
            
            if (b == '(') {
                depth++;
                baos.write(b);
            } else if (b == ')') {
                depth--;
                if (depth > 0) {
                    baos.write(b);
                }
            } else if (b == '\\') {
                if (!buffer.hasRemaining()) {
                    return null; // Incomplete escape
                }
                int next = buffer.get() & 0xFF;
                switch (next) {
                    case 'n': baos.write('\n'); break;
                    case 'r': baos.write('\r'); break;
                    case 't': baos.write('\t'); break;
                    case 'b': baos.write('\b'); break;
                    case 'f': baos.write('\f'); break;
                    case '(': baos.write('('); break;
                    case ')': baos.write(')'); break;
                    case '\\': baos.write('\\'); break;
                    case '\r':
                        if (buffer.hasRemaining() && peek(buffer) == '\n') {
                            buffer.get();
                        }
                        break;
                    case '\n':
                        break;
                    default:
                        if (next >= '0' && next <= '7') {
                            int octal = next - '0';
                            if (buffer.hasRemaining() && peek(buffer) >= '0' && peek(buffer) <= '7') {
                                octal = octal * 8 + (buffer.get() - '0');
                                if (buffer.hasRemaining() && peek(buffer) >= '0' && peek(buffer) <= '7') {
                                    octal = octal * 8 + (buffer.get() - '0');
                                }
                            }
                            baos.write(octal);
                        } else {
                            baos.write(next);
                        }
                        break;
                }
            } else {
                baos.write(b);
            }
        }
        
        if (depth > 0) {
            return null; // Incomplete string
        }
        
        return baos.toByteArray();
    }

    private byte[] readHexString(ByteBuffer buffer) {
        if (!buffer.hasRemaining() || buffer.get() != '<') {
            return null;
        }
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int first = -1;
        
        while (buffer.hasRemaining()) {
            int b = buffer.get() & 0xFF;
            
            if (b == '>') {
                break;
            }
            
            if (isWhitespace(b)) {
                continue;
            }
            
            int hex = hexValue(b);
            if (hex < 0) {
                continue;
            }
            
            if (first < 0) {
                first = hex;
            } else {
                baos.write((first << 4) | hex);
                first = -1;
            }
        }
        
        // Trailing nibble
        if (first >= 0) {
            baos.write(first << 4);
        }
        
        return baos.toByteArray();
    }

    private List<Object> readArray(ByteBuffer buffer) {
        if (!buffer.hasRemaining() || buffer.get() != '[') {
            return null;
        }
        
        List<Object> array = new ArrayList<>();
        
        while (buffer.hasRemaining()) {
            skipWhitespaceAndComments(buffer);
            if (!buffer.hasRemaining()) {
                return null; // Incomplete array
            }
            
            int b = peek(buffer);
            if (b == ']') {
                buffer.get();
                break;
            }
            
            Object element = null;
            if (b == '(') {
                element = readLiteralString(buffer);
            } else if (b == '<') {
                if (peekAt(buffer, 1) == '<') {
                    element = readDictionary(buffer);
                } else {
                    element = readHexString(buffer);
                }
            } else if (b == '/') {
                element = readName(buffer);
            } else if (b == '[') {
                element = readArray(buffer);
            } else if (b == '-' || b == '+' || b == '.' || (b >= '0' && b <= '9')) {
                element = readNumber(buffer);
            } else if (isAlpha(b)) {
                String keyword = readKeyword(buffer);
                if ("true".equals(keyword)) {
                    element = Boolean.TRUE;
                } else if ("false".equals(keyword)) {
                    element = Boolean.FALSE;
                } else if ("null".equals(keyword)) {
                    element = null;
                }
            } else {
                buffer.get(); // Skip unknown
                continue;
            }
            
            if (element != null || b == 'n') { // 'n' for null keyword
                array.add(element);
            }
        }
        
        return array;
    }

    private java.util.Map<Name, Object> readDictionary(ByteBuffer buffer) {
        if (buffer.remaining() < 2 || buffer.get() != '<' || buffer.get() != '<') {
            return null;
        }
        
        java.util.Map<Name, Object> dict = new java.util.HashMap<>();
        
        while (buffer.hasRemaining()) {
            skipWhitespaceAndComments(buffer);
            if (!buffer.hasRemaining()) {
                return null; // Incomplete
            }
            
            if (peek(buffer) == '>' && peekAt(buffer, 1) == '>') {
                buffer.get();
                buffer.get();
                break;
            }
            
            if (peek(buffer) == '/') {
                Name key = readName(buffer);
                if (key == null) {
                    return null;
                }
                skipWhitespaceAndComments(buffer);
                Object value = readInlineImageValue(buffer);
                dict.put(key, value);
            } else {
                buffer.get(); // Skip unknown
            }
        }
        
        return dict;
    }

    private int hexValue(int b) {
        if (b >= '0' && b <= '9') {
            return b - '0';
        } else if (b >= 'A' && b <= 'F') {
            return b - 'A' + 10;
        } else if (b >= 'a' && b <= 'f') {
            return b - 'a' + 10;
        }
        return -1;
    }
}
