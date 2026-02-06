/*
 * PDFHandler.java
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
 * SAX-like callback handler for receiving PDF parsing events.
 * <p>
 * This interface is implemented by applications that wish to receive
 * notifications of PDF parsing events as the parser processes a PDF document.
 * The parser invokes methods on this interface to report the structure and
 * content of the PDF as it is parsed.
 * <p>
 * PDF documents contain a variety of object types including booleans, numbers,
 * strings, names, arrays, dictionaries, streams, and indirect object references.
 * This handler receives callbacks for each of these as they are encountered
 * during parsing.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public interface PDFHandler {

    /**
     * Sets the locator for reporting position information.
     * <p>
     * This method is called by the parser before any other events are
     * delivered to provide access to byte offset information. The handler
     * can use the locator during subsequent callbacks to determine the
     * position of parsing events within the PDF.
     *
     * @param locator the document locator
     */
    void setLocator(PDFLocator locator);

    /**
     * Receives a boolean value.
     *
     * @param value the boolean value (true or false)
     */
    void booleanValue(boolean value);

    /**
     * Receives a numeric value.
     * <p>
     * PDF supports both integer and real (floating-point) numbers.
     * The Number type allows the parser to provide the most appropriate
     * Java type (Integer, Long, Float, or Double) based on the parsed value.
     *
     * @param value the numeric value
     */
    void numberValue(Number value);

    /**
     * Receives a string value.
     * <p>
     * PDF strings can be literal strings (enclosed in parentheses) or
     * hexadecimal strings (enclosed in angle brackets). This method
     * receives the decoded string content regardless of the encoding
     * used in the PDF.
     *
     * @param value the string value
     */
    void stringValue(String value);

    /**
     * Receives a name value.
     * <p>
     * PDF names are atomic symbols uniquely defined by a sequence of characters.
     * They are used as keys in dictionaries and to identify operators and other
     * PDF constructs. Names begin with a solidus (/) in PDF syntax.
     *
     * @param name the name value
     */
    void nameValue(Name name);

    /**
     * Signals the start of an array.
     * <p>
     * PDF arrays are heterogeneous ordered collections of objects.
     * After this callback, the handler will receive callbacks for each
     * element in the array, followed by {@link #endArray()}.
     */
    void startArray();

    /**
     * Signals the end of an array.
     * <p>
     * This callback indicates that all elements of the current array
     * have been reported.
     */
    void endArray();

    /**
     * Signals the start of a dictionary.
     * <p>
     * PDF dictionaries are associative collections of key-value pairs.
     * Keys are always names, and values can be any PDF object type.
     * After this callback, the handler will receive alternating
     * {@link #key(Name)} and value callbacks, followed by {@link #endDictionary()}.
     */
    void startDictionary();

    /**
     * Signals the end of a dictionary.
     * <p>
     * This callback indicates that all key-value pairs of the current
     * dictionary have been reported.
     */
    void endDictionary();

    /**
     * Receives a dictionary key.
     * <p>
     * This callback is invoked for each key in a dictionary, followed
     * by a value callback for the corresponding value.
     *
     * @param name the key name
     */
    void key(Name name);

    /**
     * Receives a null value.
     * <p>
     * The null object in PDF has a type and value that are unequal to
     * any other object.
     */
    void nullValue();

    /**
     * Receives an indirect object reference.
     * <p>
     * Indirect object references allow PDF objects to reference other
     * objects by their object identifier rather than containing the
     * object directly. This enables object sharing and is essential
     * for the PDF file structure.
     *
     * @param id the object identifier being referenced
     */
    void objectReference(ObjectId id);

    /**
     * Signals the start of an indirect object definition.
     * <p>
     * This callback is invoked when the parser encounters an indirect
     * object definition (e.g., "1 0 obj"). After this callback, the
     * handler will receive callbacks for the object's content, followed
     * by {@link #endObject()}.
     *
     * @param id the object identifier
     */
    void startObject(ObjectId id);

    /**
     * Signals the end of an indirect object definition.
     * <p>
     * This callback indicates that the current indirect object definition
     * has ended (the "endobj" keyword was encountered).
     */
    void endObject();

    /**
     * Signals the start of a stream.
     * <p>
     * PDF streams are sequences of bytes that can represent various
     * types of data including page content, images, fonts, and more.
     * A stream is always associated with a dictionary that describes
     * its properties (length, filters, etc.).
     * <p>
     * After this callback, the handler will receive one or more
     * {@link #streamContent(ByteBuffer)} callbacks containing the
     * stream data, followed by {@link #endStream()}.
     */
    void startStream();

    /**
     * Signals the end of a stream.
     * <p>
     * This callback indicates that all content of the current stream
     * has been reported.
     */
    void endStream();

    /**
     * Receives stream content.
     * <p>
     * This method may be called multiple times between {@link #startStream()}
     * and {@link #endStream()} to deliver stream content in chunks.
     * The ByteBuffer is in read mode with position set to the beginning
     * of the data and limit set to the end.
     * <p>
     * The handler should process the data immediately or copy it, as the
     * buffer may be reused by the parser after this method returns.
     *
     * @param content a buffer containing stream content
     */
    void streamContent(ByteBuffer content);

}

