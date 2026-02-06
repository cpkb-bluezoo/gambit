/*
 * PDFParser.java
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
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A streaming PDF parser that delivers parsing events via callbacks.
 * <p>
 * This parser reads PDF documents from a {@link SeekableByteChannel} and
 * delivers parsing events to a {@link PDFHandler}. The channel-based design
 * supports both local files (via {@link FileChannel}) and remote resources
 * accessed via HTTP byte ranges (for linearized PDF support).
 * <p>
 * The parser uses a SAX-like callback model where the handler receives
 * events as PDF objects are parsed. This streaming approach allows for
 * memory-efficient processing of large PDF documents.
 * <p>
 * Example usage:
 * <pre>
 * PDFHandler handler = new MyPDFHandler();
 * PDFParser parser = new PDFParser(handler);
 * 
 * try (FileChannel channel = FileChannel.open(Paths.get("document.pdf"))) {
 *     parser.parse(channel);
 * }
 * </pre>
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class PDFParser {

    private static final int BUFFER_SIZE = 8192;
    private static final byte[] STARTXREF = "startxref".getBytes();
    private static final byte[] XREF = "xref".getBytes();
    private static final byte[] TRAILER = "trailer".getBytes();
    private static final byte[] OBJ = "obj".getBytes();
    private static final byte[] ENDOBJ = "endobj".getBytes();
    private static final byte[] STREAM = "stream".getBytes();
    private static final byte[] ENDSTREAM = "endstream".getBytes();
    private static final byte[] TRUE = "true".getBytes();
    private static final byte[] FALSE = "false".getBytes();
    private static final byte[] NULL = "null".getBytes();

    private final PDFHandler handler;
    private PDFContentHandler contentHandler;
    private OpenTypeHandler openTypeHandler;
    
    private SeekableByteChannel channel;
    private ByteBuffer buffer;
    private long bufferOffset; // file offset of buffer start
    
    // Internal handler for xref parsing
    private final XRefHandler xrefHandler = new XRefHandler();
    
    // Current active handler (switches between user handler and xrefHandler)
    private PDFHandler activeHandler;
    
    private CrossReferenceTable xref;
    private Map<Name, Object> trailer;
    private ObjectId rootDictionaryId;  // 0 0 R for traditional xref, actual ID for XRef stream
    
    // Document traversal state
    private Set<ObjectId> visitedObjects;
    private Deque<ObjectReference> objectQueue;
    private Map<ObjectId, StreamType> pendingReferences;
    
    // Context tracking for stream type inference
    private Name currentObjectType;  // /Type value of current object being parsed
    private Name currentKey;         // Current dictionary key being processed
    private StreamType currentStreamType;  // Stream type for current object

    /**
     * Creates a new PDF parser with the specified handler.
     *
     * @param handler the handler to receive parsing events
     * @throws NullPointerException if handler is null
     */
    public PDFParser(PDFHandler handler) {
        if (handler == null) {
            throw new NullPointerException("Handler cannot be null");
        }
        this.handler = handler;
        this.activeHandler = handler;
    }

    /**
     * Creates a new PDF parser with the specified handlers.
     *
     * @param handler the handler to receive parsing events
     * @param contentHandler the handler to receive content stream events
     * @throws NullPointerException if handler is null
     */
    public PDFParser(PDFHandler handler, PDFContentHandler contentHandler) {
        this(handler, contentHandler, null);
    }

    /**
     * Creates a new PDF parser with all handlers.
     *
     * @param handler the handler to receive parsing events
     * @param contentHandler the handler to receive content stream events (may be null)
     * @param openTypeHandler the handler to receive OpenType font parsing events (may be null)
     * @throws NullPointerException if handler is null
     */
    public PDFParser(PDFHandler handler, PDFContentHandler contentHandler, OpenTypeHandler openTypeHandler) {
        if (handler == null) {
            throw new NullPointerException("Handler cannot be null");
        }
        this.handler = handler;
        this.activeHandler = handler;
        this.contentHandler = contentHandler;
        this.openTypeHandler = openTypeHandler;
    }

    /**
     * Parses a PDF document from the specified channel.
     * <p>
     * The channel must support seeking to allow random access to PDF
     * structures such as the cross-reference table and indirect objects.
     * <p>
     * Parsing starts from the document root (Catalog) and traverses
     * the document structure by following object references.
     *
     * @param channel the channel to read from
     * @throws IOException if an I/O error occurs
     * @throws PDFParseException if the PDF is malformed
     */
    public void parse(SeekableByteChannel channel) throws IOException {
        this.channel = channel;
        this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.xref = new CrossReferenceTable();
        this.trailer = null;
        this.visitedObjects = new HashSet<>();
        this.objectQueue = new ArrayDeque<>();
        this.pendingReferences = new HashMap<>();
        
        // Find startxref at end of file
        long startxrefOffset = findStartxref();
        
        // Read the xref offset value
        long xrefOffset = readStartxrefValue(startxrefOffset);
        
        // Load the cross-reference table (handles incremental updates)
        loadXref(xrefOffset);
        
        // First, emit the root dictionary as an object
        // (0 0 R for traditional xref, actual ID for XRef stream)
        emitRootDictionary();
        
        // Start from the document catalog
        Object root = trailer.get(new Name("Root"));
        if (!(root instanceof ObjectId)) {
            throw new PDFParseException("Root dictionary missing /Root entry");
        }
        
        // Queue the catalog object (no special stream type for catalog)
        objectQueue.add(new ObjectReference((ObjectId) root));
        
        // Also queue /Info if present (already collected as reference from root dict)
        Object info = trailer.get(new Name("Info"));
        if (info instanceof ObjectId && !visitedObjects.contains(info)) {
            objectQueue.add(new ObjectReference((ObjectId) info));
        }
        
        // Process objects in document order
        while (!objectQueue.isEmpty()) {
            ObjectReference ref = objectQueue.poll();
            ObjectId id = ref.getId();
            
            if (visitedObjects.contains(id)) {
                continue;
            }
            
            CrossReferenceEntry entry = xref.get(id);
            if (entry == null) {
                continue; // Object not in xref (might be in object stream)
            }
            
            if (entry.isInUse()) {
                visitedObjects.add(id);
                pendingReferences.clear();
                currentStreamType = ref.getStreamType();
                currentObjectType = null;
                currentKey = null;
                
                parseIndirectObject(id);
                
                // Queue any new references found during parsing
                for (Map.Entry<ObjectId, StreamType> pending : pendingReferences.entrySet()) {
                    if (!visitedObjects.contains(pending.getKey())) {
                        objectQueue.add(new ObjectReference(pending.getKey(), pending.getValue()));
                    }
                }
            }
            // TODO: Handle compressed objects (in object streams)
        }
    }

    /**
     * Emits the root dictionary (trailer/XRef stream dict) as an object.
     * This is called before processing the document catalog.
     */
    private void emitRootDictionary() {
        // Mark as visited to avoid re-processing
        visitedObjects.add(rootDictionaryId);
        pendingReferences.clear();
        
        handler.startObject(rootDictionaryId);
        handler.startDictionary();
        
        // Emit all entries in the root dictionary
        for (Map.Entry<Name, Object> entry : trailer.entrySet()) {
            handler.key(entry.getKey());
            emitValue(entry.getValue());
        }
        
        handler.endDictionary();
        handler.endObject();
        
        // Queue any references found in the root dictionary
        for (Map.Entry<ObjectId, StreamType> entry : pendingReferences.entrySet()) {
            ObjectId ref = entry.getKey();
            if (!visitedObjects.contains(ref)) {
                objectQueue.add(new ObjectReference(ref, entry.getValue()));
            }
        }
    }

    /**
     * Emits a value to the handler (used for root dictionary emission).
     */
    private void emitValue(Object value) {
        if (value == null) {
            handler.nullValue();
        } else if (value instanceof Boolean) {
            handler.booleanValue((Boolean) value);
        } else if (value instanceof Number) {
            handler.numberValue((Number) value);
        } else if (value instanceof String) {
            handler.stringValue((String) value);
        } else if (value instanceof Name) {
            handler.nameValue((Name) value);
        } else if (value instanceof ObjectId) {
            ObjectId ref = (ObjectId) value;
            if (pendingReferences != null) {
                pendingReferences.put(ref, StreamType.DEFAULT);
            }
            handler.objectReference(ref);
        } else if (value instanceof Object[]) {
            handler.startArray();
            for (Object element : (Object[]) value) {
                emitValue(element);
            }
            handler.endArray();
        } else if (value instanceof java.util.List) {
            handler.startArray();
            for (Object element : (java.util.List<?>) value) {
                emitValue(element);
            }
            handler.endArray();
        } else if (value instanceof Map) {
            handler.startDictionary();
            @SuppressWarnings("unchecked")
            Map<Name, Object> dict = (Map<Name, Object>) value;
            for (Map.Entry<Name, Object> entry : dict.entrySet()) {
                handler.key(entry.getKey());
                emitValue(entry.getValue());
            }
            handler.endDictionary();
        }
    }

    // ========================================================================
    // Event-driven parsing methods - fire events to activeHandler
    // ========================================================================

    /**
     * Parses a PDF object at the current position and fires handler events.
     *
     * @throws IOException if an I/O error occurs
     */
    void parseObject() throws IOException {
        skipWhitespace();
        int b = peek();
        
        if (b == '/') {
            parseName();
        } else if (b == '(') {
            parseLiteralString();
        } else if (b == '<') {
            int next = peekAt(1);
            if (next == '<') {
                parseDictionary();
            } else {
                parseHexString();
            }
        } else if (b == '[') {
            parseArray();
        } else if (b == 't') {
            parseTrue();
        } else if (b == 'f') {
            parseFalse();
        } else if (b == 'n') {
            parseNull();
        } else if (b == '-' || b == '+' || b == '.' || (b >= '0' && b <= '9')) {
            parseNumberOrReference();
        } else {
            throw new PDFParseException("Unexpected character: " + (char) b, getPosition());
        }
    }

    /**
     * Parses a name and fires nameValue event.
     */
    private void parseName() throws IOException {
        if (readByte() != '/') {
            throw new PDFParseException("Expected '/'", getPosition());
        }
        
        StringBuilder sb = new StringBuilder();
        while (true) {
            int b = peek();
            if (isWhitespace(b) || isDelimiter(b) || b == -1) {
                break;
            }
            readByte();
            if (b == '#') {
                int h1 = readByte();
                int h2 = readByte();
                int value = (hexValue(h1) << 4) | hexValue(h2);
                sb.append((char) value);
            } else {
                sb.append((char) b);
            }
        }
        
        activeHandler.nameValue(new Name(sb.toString()));
    }

    /**
     * Parses a literal string and fires stringValue event.
     */
    private void parseLiteralString() throws IOException {
        if (readByte() != '(') {
            throw new PDFParseException("Expected '('", getPosition());
        }
        
        StringBuilder sb = new StringBuilder();
        int parenDepth = 1;
        
        while (parenDepth > 0) {
            int b = readByte();
            if (b == -1) {
                throw new PDFParseException("Unterminated string", getPosition());
            }
            
            if (b == '(') {
                parenDepth++;
                sb.append((char) b);
            } else if (b == ')') {
                parenDepth--;
                if (parenDepth > 0) {
                    sb.append((char) b);
                }
            } else if (b == '\\') {
                int escaped = readByte();
                switch (escaped) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '(': sb.append('('); break;
                    case ')': sb.append(')'); break;
                    case '\\': sb.append('\\'); break;
                    case '\r':
                        if (peek() == '\n') readByte();
                        break;
                    case '\n':
                        break;
                    default:
                        if (escaped >= '0' && escaped <= '7') {
                            int octal = escaped - '0';
                            for (int i = 0; i < 2; i++) {
                                int next = peek();
                                if (next >= '0' && next <= '7') {
                                    readByte();
                                    octal = (octal << 3) | (next - '0');
                                } else {
                                    break;
                                }
                            }
                            sb.append((char) octal);
                        } else {
                            sb.append((char) escaped);
                        }
                }
            } else {
                sb.append((char) b);
            }
        }
        
        activeHandler.stringValue(sb.toString());
    }

    /**
     * Parses a hex string and fires stringValue event.
     */
    private void parseHexString() throws IOException {
        if (readByte() != '<') {
            throw new PDFParseException("Expected '<'", getPosition());
        }
        
        StringBuilder sb = new StringBuilder();
        StringBuilder hex = new StringBuilder();
        
        while (true) {
            int b = readByte();
            if (b == '>') {
                break;
            }
            if (b == -1) {
                throw new PDFParseException("Unterminated hex string", getPosition());
            }
            if (isWhitespace(b)) {
                continue;
            }
            hex.append((char) b);
            if (hex.length() == 2) {
                int value = Integer.parseInt(hex.toString(), 16);
                sb.append((char) value);
                hex.setLength(0);
            }
        }
        
        if (hex.length() == 1) {
            int value = Integer.parseInt(hex.toString() + "0", 16);
            sb.append((char) value);
        }
        
        activeHandler.stringValue(sb.toString());
    }

    /**
     * Parses a dictionary and fires startDictionary, key, value, endDictionary events.
     */
    private void parseDictionary() throws IOException {
        if (readByte() != '<' || readByte() != '<') {
            throw new PDFParseException("Expected '<<'", getPosition());
        }
        
        activeHandler.startDictionary();
        skipWhitespace();
        
        while (true) {
            int b = peek();
            if (b == '>') {
                readByte();
                if (readByte() != '>') {
                    throw new PDFParseException("Expected '>>'", getPosition());
                }
                break;
            }
            
            // Parse key (must be a name) and fire key event
            parseKey();
            skipWhitespace();
            
            // Parse value (fires appropriate value event)
            parseObject();
            skipWhitespace();
        }
        
        activeHandler.endDictionary();
    }

    /**
     * Parses a dictionary key and fires key event.
     */
    private void parseKey() throws IOException {
        if (readByte() != '/') {
            throw new PDFParseException("Expected '/'", getPosition());
        }
        
        StringBuilder sb = new StringBuilder();
        while (true) {
            int b = peek();
            if (isWhitespace(b) || isDelimiter(b) || b == -1) {
                break;
            }
            readByte();
            if (b == '#') {
                int h1 = readByte();
                int h2 = readByte();
                int value = (hexValue(h1) << 4) | hexValue(h2);
                sb.append((char) value);
            } else {
                sb.append((char) b);
            }
        }
        
        Name key = new Name(sb.toString());
        currentKey = key;  // Track for stream type inference
        activeHandler.key(key);
    }

    /**
     * Parses an array and fires startArray, element values, endArray events.
     */
    private void parseArray() throws IOException {
        if (readByte() != '[') {
            throw new PDFParseException("Expected '['", getPosition());
        }
        
        activeHandler.startArray();
        skipWhitespace();
        
        while (true) {
            int b = peek();
            if (b == ']') {
                readByte();
                break;
            }
            parseObject();
            skipWhitespace();
        }
        
        activeHandler.endArray();
    }

    /**
     * Parses 'true' and fires booleanValue event.
     */
    private void parseTrue() throws IOException {
        if (!skipKeyword(TRUE)) {
            throw new PDFParseException("Expected 'true'", getPosition());
        }
        activeHandler.booleanValue(true);
    }

    /**
     * Parses 'false' and fires booleanValue event.
     */
    private void parseFalse() throws IOException {
        if (!skipKeyword(FALSE)) {
            throw new PDFParseException("Expected 'false'", getPosition());
        }
        activeHandler.booleanValue(false);
    }

    /**
     * Parses 'null' and fires nullValue event.
     */
    private void parseNull() throws IOException {
        if (!skipKeyword(NULL)) {
            throw new PDFParseException("Expected 'null'", getPosition());
        }
        activeHandler.nullValue();
    }

    /**
     * Parses a number or indirect reference and fires appropriate event.
     */
    private void parseNumberOrReference() throws IOException {
        Number num1 = readNumber();
        
        long savedPos = getPosition();
        skipWhitespace();
        
        // Check if this might be an indirect reference (objNum genNum R)
        int b = peek();
        if (b >= '0' && b <= '9') {
            Number num2 = readNumber();
            skipWhitespace();
            if (peek() == 'R') {
                readByte();
                ObjectId refId = new ObjectId(num1.intValue(), num2.intValue());
                
                // Collect reference for document traversal with inferred stream type
                if (pendingReferences != null) {
                    StreamType inferredType = inferStreamType();
                    pendingReferences.put(refId, inferredType);
                }
                
                activeHandler.objectReference(refId);
                return;
            }
            // Not a reference, restore position
            seek(savedPos);
        }
        
        activeHandler.numberValue(num1);
    }

    /**
     * Infers the stream type for a reference based on current context.
     *
     * @return the inferred stream type
     */
    private StreamType inferStreamType() {
        if (currentKey == null) {
            return StreamType.DEFAULT;
        }
        
        String key = currentKey.getValue();
        
        // Content streams from Page or Form XObject
        if ("Contents".equals(key)) {
            if (currentObjectType != null) {
                String type = currentObjectType.getValue();
                if ("Page".equals(type) || "XObject".equals(type)) {
                    return StreamType.CONTENT;
                }
            }
        }
        
        // ToUnicode CMap
        if ("ToUnicode".equals(key)) {
            return StreamType.CMAP;
        }
        
        // Metadata stream
        if ("Metadata".equals(key)) {
            return StreamType.METADATA;
        }
        
        // Font programs
        if ("FontFile".equals(key)) {
            return StreamType.FONT_TYPE1;
        }
        if ("FontFile2".equals(key)) {
            return StreamType.FONT_TRUETYPE;
        }
        if ("FontFile3".equals(key)) {
            return StreamType.FONT_CFF;
        }
        
        return StreamType.DEFAULT;
    }

    /**
     * Parses an indirect object and fires startObject, content, endObject events.
     *
     * @param id the object identifier
     * @throws IOException if an I/O error occurs
     */
    void parseIndirectObject(ObjectId id) throws IOException {
        CrossReferenceEntry entry = xref.get(id);
        if (entry == null || !entry.isInUse()) {
            throw new PDFParseException("Object not found: " + id);
        }
        
        seek(entry.getOffset());
        
        // Parse object header: objNum genNum obj
        int objNum = (int) readLong();
        skipWhitespace();
        int genNum = (int) readLong();
        skipWhitespace();
        
        if (objNum != id.getObjectNumber() || genNum != id.getGenerationNumber()) {
            throw new PDFParseException("Object number mismatch at offset " + entry.getOffset());
        }
        
        if (!skipKeyword(OBJ)) {
            throw new PDFParseException("Expected 'obj'", getPosition());
        }
        skipWhitespace();
        
        // First, parse using xrefHandler to capture the value (for stream Length)
        long contentStart = getPosition();
        xrefHandler.reset();
        activeHandler = xrefHandler;
        parseObject();
        Object capturedValue = xrefHandler.getResult();
        activeHandler = handler;
        
        skipWhitespace();
        
        // Check for stream and extract object type
        boolean hasStream = (peek() == 's');
        int streamLength = 0;
        if (capturedValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Name, Object> dict = (Map<Name, Object>) capturedValue;
            
            // Extract /Type for context tracking
            Object typeObj = dict.get(new Name("Type"));
            if (typeObj instanceof Name) {
                currentObjectType = (Name) typeObj;
            }
            
            if (hasStream) {
                Object lengthObj = dict.get(new Name("Length"));
                if (lengthObj instanceof Number) {
                    streamLength = ((Number) lengthObj).intValue();
                } else if (lengthObj instanceof ObjectId) {
                    // Length is an indirect reference - need to resolve it
                    streamLength = resolveLength((ObjectId) lengthObj);
                }
            }
        }
        
        // Now fire events to user handler
        handler.startObject(id);
        
        // Re-parse from content start to fire events
        seek(contentStart);
        parseObject();
        skipWhitespace();
        
        // Handle stream if present
        if (hasStream && capturedValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Name, Object> streamDict = (Map<Name, Object>) capturedValue;
            parseStream(streamLength, streamDict);
        }
        
        // Expect 'endobj'
        skipWhitespace();
        if (!skipKeyword(ENDOBJ)) {
            throw new PDFParseException("Expected 'endobj'", getPosition());
        }
        
        handler.endObject();
    }

    /**
     * Resolves an indirect reference to get a Length value.
     */
    private int resolveLength(ObjectId lengthId) throws IOException {
        CrossReferenceEntry entry = xref.get(lengthId);
        if (entry == null || !entry.isInUse()) {
            throw new PDFParseException("Length object not found: " + lengthId);
        }
        
        long savedPos = getPosition();
        seek(entry.getOffset());
        
        // Skip object header
        readLong(); // objNum
        skipWhitespace();
        readLong(); // genNum
        skipWhitespace();
        skipKeyword(OBJ);
        skipWhitespace();
        
        // Read the number
        Number length = readNumber();
        
        seek(savedPos);
        return length.intValue();
    }

    /**
     * Parses a stream and fires stream events.
     *
     * @param length the stream length in bytes
     */
    private void parseStream(int length, Map<Name, Object> streamDict) throws IOException {
        if (!skipKeyword(STREAM)) {
            return;
        }
        
        // Skip single EOL after "stream"
        int b = readByte();
        if (b == '\r') {
            if (peek() == '\n') {
                readByte();
            }
        }
        
        activeHandler.startStream();
        
        // Build the filter pipeline
        // The dispatcher sends decoded data to handler.streamContent() and optionally a specialized parser
        StreamDispatcher dispatcher = new StreamDispatcher(activeHandler);
        
        // Set up specialized stream parser based on stream type
        StreamParser streamParser = createStreamParser(currentStreamType);
        if (streamParser != null) {
            dispatcher.setParser(streamParser);
        }
        
        // Create the filter pipeline (filters -> dispatcher)
        FilterPipeline pipeline = FilterPipeline.create(streamDict, dispatcher);
        
        // Read and push stream content through the pipeline
        if (length > 0) {
            // Read in chunks for memory efficiency
            int chunkSize = 8192;
            int remaining = length;
            
            while (remaining > 0) {
                int toRead = Math.min(chunkSize, remaining);
                byte[] chunk = readBytes(toRead);
                pipeline.write(ByteBuffer.wrap(chunk));
                remaining -= toRead;
            }
        }
        
        // Signal end of stream to flush buffers
        pipeline.close();
        
        activeHandler.endStream();
        
        skipWhitespace();
        skipKeyword(ENDSTREAM);
    }
    // ========================================================================
    // XRef loading - uses xrefHandler to build internal structures
    // ========================================================================

    /**
     * Finds the startxref keyword near the end of the file.
     */
    private long findStartxref() throws IOException {
        long fileSize = channel.size();
        int searchSize = (int) Math.min(1024, fileSize);
        long searchStart = fileSize - searchSize;
        
        buffer.clear();
        buffer.limit(searchSize);
        channel.position(searchStart);
        int bytesRead = channel.read(buffer);
        if (bytesRead < searchSize) {
            throw new PDFParseException("Failed to read end of file");
        }
        buffer.flip();
        bufferOffset = searchStart;
        
        for (int i = searchSize - STARTXREF.length; i >= 0; i--) {
            if (matches(buffer, i, STARTXREF)) {
                return searchStart + i;
            }
        }
        
        throw new PDFParseException("startxref not found");
    }

    /**
     * Reads the xref offset value following startxref.
     */
    private long readStartxrefValue(long startxrefOffset) throws IOException {
        seek(startxrefOffset);
        skipBytes(STARTXREF.length);
        skipWhitespace();
        return readLong();
    }

    /**
     * Loads the cross-reference table starting at the given offset.
     */
    private void loadXref(long offset) throws IOException {
        seek(offset);
        skipWhitespace();
        
        int b = peek();
        
        if (b == 'x') {
            loadLegacyXref(offset);
        } else if (b >= '0' && b <= '9') {
            loadXrefStream(offset);
        } else {
            throw new PDFParseException("Invalid xref at offset " + offset);
        }
        
        if (trailer != null) {
            Object prev = trailer.get(new Name("Prev"));
            if (prev instanceof Number) {
                long prevOffset = ((Number) prev).longValue();
                loadXref(prevOffset);
            }
        }
    }

    /**
     * Loads a legacy xref section and trailer.
     */
    private void loadLegacyXref(long offset) throws IOException {
        seek(offset);
        
        if (!skipKeyword(XREF)) {
            throw new PDFParseException("Expected 'xref' at offset " + offset);
        }
        skipWhitespace();
        
        // Read subsections
        while (true) {
            int b = peek();
            if (b == 't') {
                break;
            }
            if (b < '0' || b > '9') {
                throw new PDFParseException("Expected subsection or trailer", getPosition());
            }
            
            int startObject = (int) readLong();
            skipWhitespace();
            int count = (int) readLong();
            skipWhitespace();
            
            for (int i = 0; i < count; i++) {
                int objectNumber = startObject + i;
                
                long entryOffset = readLong();
                skipWhitespace();
                int generation = (int) readLong();
                skipWhitespace();
                int type = readByte();
                skipWhitespace();
                
                CrossReferenceEntry entry;
                if (type == 'n') {
                    entry = CrossReferenceEntry.inUse(entryOffset, generation);
                } else if (type == 'f') {
                    entry = CrossReferenceEntry.free((int) entryOffset, generation);
                } else {
                    throw new PDFParseException("Invalid xref entry type: " + (char) type, getPosition());
                }
                
                if (!xref.contains(objectNumber, generation)) {
                    xref.put(objectNumber, generation, entry);
                }
            }
        }
        
        // Parse trailer dictionary using xrefHandler
        if (!skipKeyword(TRAILER)) {
            throw new PDFParseException("Expected 'trailer'", getPosition());
        }
        skipWhitespace();
        
        xrefHandler.reset();
        activeHandler = xrefHandler;
        parseDictionary();
        activeHandler = handler;
        
        Map<Name, Object> trailerDict = xrefHandler.getDictionary();
        if (this.trailer == null) {
            this.trailer = trailerDict;
            // Traditional xref trailer uses synthetic ObjectId 0 0
            this.rootDictionaryId = new ObjectId(0, 0);
        }
    }

    /**
     * Loads an XRef stream.
     */
    private void loadXrefStream(long offset) throws IOException {
        seek(offset);
        
        int objNum = (int) readLong();
        skipWhitespace();
        int genNum = (int) readLong();
        skipWhitespace();
        
        if (!skipKeyword(OBJ)) {
            throw new PDFParseException("Expected 'obj' at offset " + offset);
        }
        skipWhitespace();
        
        // Parse stream dictionary using xrefHandler
        xrefHandler.reset();
        activeHandler = xrefHandler;
        parseDictionary();
        activeHandler = handler;
        
        Map<Name, Object> streamDict = xrefHandler.getDictionary();
        skipWhitespace();
        
        if (this.trailer == null) {
            this.trailer = streamDict;
            // XRef stream uses its actual ObjectId
            this.rootDictionaryId = new ObjectId(objNum, genNum);
        }
        
        if (!skipKeyword(STREAM)) {
            throw new PDFParseException("Expected 'stream'", getPosition());
        }
        int b = readByte();
        if (b == '\r') {
            if (peek() == '\n') {
                readByte();
            }
        }
        
        Number lengthNum = (Number) streamDict.get(new Name("Length"));
        if (lengthNum == null) {
            throw new PDFParseException("XRef stream missing Length");
        }
        int length = lengthNum.intValue();
        
        byte[] streamData = readBytes(length);
        byte[] decodedData = decodeStream(streamData, streamDict);
        
        parseXrefStreamData(decodedData, streamDict);
    }

    /**
     * Parses the binary data of an XRef stream.
     */
    private void parseXrefStreamData(byte[] data, Map<Name, Object> dict) {
        Object wValue = dict.get(new Name("W"));
        Object[] wArray;
        if (wValue instanceof Object[]) {
            wArray = (Object[]) wValue;
        } else {
            throw new PDFParseException("XRef stream missing or invalid W array");
        }
        if (wArray.length != 3) {
            throw new PDFParseException("XRef stream W array must have 3 elements");
        }
        int w0 = ((Number) wArray[0]).intValue();
        int w1 = ((Number) wArray[1]).intValue();
        int w2 = ((Number) wArray[2]).intValue();
        int entrySize = w0 + w1 + w2;
        
        Object indexValue = dict.get(new Name("Index"));
        int[] index;
        if (indexValue instanceof Object[]) {
            Object[] indexArray = (Object[]) indexValue;
            index = new int[indexArray.length];
            for (int i = 0; i < indexArray.length; i++) {
                index[i] = ((Number) indexArray[i]).intValue();
            }
        } else {
            Number sizeNum = (Number) dict.get(new Name("Size"));
            int size = sizeNum != null ? sizeNum.intValue() : data.length / entrySize;
            index = new int[] { 0, size };
        }
        
        int dataOffset = 0;
        for (int i = 0; i < index.length; i += 2) {
            int startObj = index[i];
            int count = index[i + 1];
            
            for (int j = 0; j < count; j++) {
                int objNumEntry = startObj + j;
                
                int type = (w0 > 0) ? readIntFromBytes(data, dataOffset, w0) : 1;
                dataOffset += w0;
                
                int field2 = readIntFromBytes(data, dataOffset, w1);
                dataOffset += w1;
                
                int field3 = readIntFromBytes(data, dataOffset, w2);
                dataOffset += w2;
                
                CrossReferenceEntry entry;
                switch (type) {
                    case 0:
                        entry = CrossReferenceEntry.free(field2, field3);
                        break;
                    case 1:
                        entry = CrossReferenceEntry.inUse(field2, field3);
                        break;
                    case 2:
                        entry = CrossReferenceEntry.compressed(field2, field3);
                        break;
                    default:
                        continue;
                }
                
                int gen = (type == 2) ? 0 : field3;
                if (!xref.contains(objNumEntry, gen)) {
                    xref.put(objNumEntry, gen, entry);
                }
            }
        }
    }

    /**
     * Reads an integer from a byte array with the specified width.
     */
    private int readIntFromBytes(byte[] data, int offset, int width) {
        int value = 0;
        for (int i = 0; i < width; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    /**
     * Decodes stream data by applying filters.
     */
    private byte[] decodeStream(byte[] data, Map<Name, Object> dict) {
        Object filter = dict.get(new Name("Filter"));
        if (filter == null) {
            return data;
        }
        
        // TODO: Implement filter decoding (FlateDecode, etc.)
        return data;
    }

    // ========================================================================
    // Low-level I/O helpers
    // ========================================================================

    /**
     * Seeks to a position in the file and refills the buffer.
     */
    private void seek(long position) throws IOException {
        channel.position(position);
        buffer.clear();
        channel.read(buffer);
        buffer.flip();
        bufferOffset = position;
    }

    /**
     * Returns the current position in the file.
     */
    private long getPosition() {
        return bufferOffset + buffer.position();
    }

    /**
     * Reads a single byte.
     */
    private int readByte() throws IOException {
        if (!buffer.hasRemaining()) {
            refillBuffer();
            if (!buffer.hasRemaining()) {
                return -1;
            }
        }
        return buffer.get() & 0xFF;
    }

    /**
     * Peeks at the next byte without consuming it.
     */
    private int peek() throws IOException {
        if (!buffer.hasRemaining()) {
            refillBuffer();
            if (!buffer.hasRemaining()) {
                return -1;
            }
        }
        return buffer.get(buffer.position()) & 0xFF;
    }

    /**
     * Peeks at a byte at a relative offset.
     */
    private int peekAt(int offset) throws IOException {
        if (buffer.position() + offset >= buffer.limit()) {
            return -1;
        }
        return buffer.get(buffer.position() + offset) & 0xFF;
    }

    /**
     * Refills the buffer from the channel.
     */
    private void refillBuffer() throws IOException {
        bufferOffset += buffer.position();
        buffer.clear();
        channel.read(buffer);
        buffer.flip();
    }

    /**
     * Skips whitespace characters.
     */
    private void skipWhitespace() throws IOException {
        while (true) {
            int b = peek();
            if (b == -1 || !isWhitespace(b)) {
                if (b == '%') {
                    skipComment();
                } else {
                    break;
                }
            } else {
                readByte();
            }
        }
    }

    /**
     * Skips a comment (% to end of line).
     */
    private void skipComment() throws IOException {
        while (true) {
            int b = readByte();
            if (b == -1 || b == '\r' || b == '\n') {
                break;
            }
        }
    }

    /**
     * Skips a specific number of bytes.
     */
    private void skipBytes(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            readByte();
        }
    }

    /**
     * Attempts to skip a keyword.
     */
    private boolean skipKeyword(byte[] keyword) throws IOException {
        for (byte b : keyword) {
            if (readByte() != (b & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a long integer from the current position.
     */
    private long readLong() throws IOException {
        long value = 0;
        boolean negative = false;
        
        int b = peek();
        if (b == '-') {
            negative = true;
            readByte();
        } else if (b == '+') {
            readByte();
        }
        
        while (true) {
            b = peek();
            if (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
                readByte();
            } else {
                break;
            }
        }
        
        return negative ? -value : value;
    }

    /**
     * Reads a number and returns it.
     */
    private Number readNumber() throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean isReal = false;
        
        int b = peek();
        if (b == '+' || b == '-') {
            sb.append((char) readByte());
        }
        
        while (true) {
            b = peek();
            if (b >= '0' && b <= '9') {
                sb.append((char) readByte());
            } else if (b == '.') {
                isReal = true;
                sb.append((char) readByte());
            } else {
                break;
            }
        }
        
        String str = sb.toString();
        if (isReal) {
            return Double.parseDouble(str);
        } else {
            long value = Long.parseLong(str);
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        }
    }

    /**
     * Reads a specified number of bytes.
     */
    private byte[] readBytes(int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            if (!buffer.hasRemaining()) {
                refillBuffer();
            }
            int toRead = Math.min(buffer.remaining(), length - offset);
            buffer.get(result, offset, toRead);
            offset += toRead;
        }
        return result;
    }

    /**
     * Checks if buffer matches a byte sequence at the given position.
     */
    private boolean matches(ByteBuffer buf, int pos, byte[] sequence) {
        for (int i = 0; i < sequence.length; i++) {
            if (buf.get(pos + i) != sequence[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a byte is PDF whitespace.
     */
    private boolean isWhitespace(int b) {
        return b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32;
    }

    /**
     * Checks if a byte is a PDF delimiter.
     */
    private boolean isDelimiter(int b) {
        return b == '(' || b == ')' || b == '<' || b == '>' ||
               b == '[' || b == ']' || b == '{' || b == '}' ||
               b == '/' || b == '%';
    }

    /**
     * Returns the hex value of a character.
     */
    private int hexValue(int c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        throw new PDFParseException("Invalid hex character: " + (char) c, getPosition());
    }

    // ========================================================================
    // Public accessors
    // ========================================================================

    /**
     * Returns the handler receiving parsing events.
     *
     * @return the PDF handler
     */
    public PDFHandler getHandler() {
        return handler;
    }

    /**
     * Returns the content handler receiving content stream events.
     *
     * @return the content handler, or null if none is set
     */
    public PDFContentHandler getContentHandler() {
        return contentHandler;
    }

    /**
     * Sets the content handler to receive content stream events.
     *
     * @param contentHandler the content handler, or null to disable
     *        content stream parsing
     */
    public void setContentHandler(PDFContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    /**
     * Returns the OpenType handler receiving font parsing events.
     *
     * @return the OpenType handler, or null if none is set
     */
    public OpenTypeHandler getOpenTypeHandler() {
        return openTypeHandler;
    }

    /**
     * Sets the OpenType handler to receive font parsing events.
     *
     * @param openTypeHandler the handler, or null to disable font parsing
     */
    public void setOpenTypeHandler(OpenTypeHandler openTypeHandler) {
        this.openTypeHandler = openTypeHandler;
    }

    /**
     * Creates a specialized stream parser for the given stream type.
     *
     * @param streamType the type of stream
     * @return a stream parser, or null if no specialized parsing is needed
     */
    private StreamParser createStreamParser(StreamType streamType) {
        if (streamType == null) {
            return null;
        }
        
        switch (streamType) {
            case CONTENT:
                if (contentHandler != null) {
                    return new PDFContentParser(contentHandler, handler);
                }
                break;
                
            case FONT_TRUETYPE:
            case FONT_OPENTYPE_CFF:
                if (openTypeHandler != null) {
                    return new OpenTypeParser(openTypeHandler);
                }
                break;
                
            // TODO: Add other stream type parsers:
            // case CMAP: return new CMapParser(...);
            // case METADATA: return new XMPParser(...);
            // case FONT_CFF: return new CFFParser(...);
            // case FONT_TYPE1: return new Type1Parser(...);
            
            default:
                break;
        }
        
        return null;
    }

    /**
     * Returns the cross-reference table.
     * <p>
     * Only available after parsing has begun.
     *
     * @return the xref table, or null if not yet loaded
     */
    public CrossReferenceTable getCrossReferenceTable() {
        return xref;
    }

    /**
     * Returns the document trailer dictionary.
     * <p>
     * Only available after parsing has begun.
     *
     * @return the trailer dictionary, or null if not yet loaded
     */
    public Map<Name, Object> getTrailer() {
        return trailer;
    }

    /**
     * Main method for testing. Parses a PDF file and prints events.
     *
     * @param args command line arguments (args[0] = PDF file path)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java org.bluezoo.gambit.PDFParser <pdf-file>");
            System.exit(1);
        }
        
        try (FileChannel channel = FileChannel.open(Paths.get(args[0]), StandardOpenOption.READ)) {
            PDFParser parser = new PDFParser(new DebugPDFHandler());
            parser.setContentHandler(new DebugPDFContentHandler());
            parser.setOpenTypeHandler(new DebugOpenTypeHandler());
            parser.parse(channel);
        } catch (IOException e) {
            System.err.println("Error parsing PDF: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

}
