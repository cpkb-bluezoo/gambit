/*
 * XRefHandler.java
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal PDFHandler implementation for building xref and trailer structures.
 * <p>
 * This handler receives parsing events and constructs in-memory representations
 * of PDF objects. It is used internally by the parser when loading the
 * cross-reference table and trailer dictionary.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
class XRefHandler implements PDFHandler {

    /**
     * Stack for building nested structures (arrays, dictionaries).
     */
    private final Deque<Object> stack = new ArrayDeque<>();
    
    /**
     * Current key when building a dictionary.
     */
    private Name currentKey;
    
    /**
     * The result after parsing completes.
     */
    private Object result;

    /**
     * Returns the parsed result.
     *
     * @return the parsed object
     */
    Object getResult() {
        return result;
    }

    /**
     * Returns the result as a dictionary.
     *
     * @return the dictionary
     * @throws ClassCastException if result is not a dictionary
     */
    @SuppressWarnings("unchecked")
    Map<Name, Object> getDictionary() {
        return (Map<Name, Object>) result;
    }

    /**
     * Returns the result as an array.
     *
     * @return the array
     * @throws ClassCastException if result is not an array
     */
    Object[] getArray() {
        return ((List<?>) result).toArray();
    }

    /**
     * Resets the handler for reuse.
     */
    void reset() {
        stack.clear();
        currentKey = null;
        result = null;
    }

    @Override
    public void setLocator(PDFLocator locator) {
        // Not needed for internal parsing
    }

    @Override
    public void booleanValue(boolean value) {
        addValue(Boolean.valueOf(value));
    }

    @Override
    public void numberValue(Number value) {
        addValue(value);
    }

    @Override
    public void stringValue(String value) {
        addValue(value);
    }

    @Override
    public void nameValue(Name name) {
        addValue(name);
    }

    @Override
    public void startArray() {
        stack.push(new ArrayList<Object>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void endArray() {
        List<Object> array = (List<Object>) stack.pop();
        if (stack.isEmpty()) {
            result = array;
        } else {
            addValue(array);
        }
    }

    @Override
    public void startDictionary() {
        stack.push(new HashMap<Name, Object>());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void endDictionary() {
        Map<Name, Object> dict = (Map<Name, Object>) stack.pop();
        if (stack.isEmpty()) {
            result = dict;
        } else {
            addValue(dict);
        }
    }

    @Override
    public void key(Name name) {
        currentKey = name;
    }

    @Override
    public void nullValue() {
        addValue(null);
    }

    @Override
    public void objectReference(ObjectId id) {
        addValue(id);
    }

    @Override
    public void startObject(ObjectId id) {
        // Not used in xref parsing
    }

    @Override
    public void endObject() {
        // Not used in xref parsing
    }

    @Override
    public void startStream() {
        // Not used in xref parsing (stream data handled separately)
    }

    @Override
    public void endStream() {
        // Not used in xref parsing
    }

    @Override
    public void streamContent(ByteBuffer content) {
        // Not used in xref parsing
    }

    /**
     * Adds a value to the current context (array or dictionary).
     */
    @SuppressWarnings("unchecked")
    private void addValue(Object value) {
        if (stack.isEmpty()) {
            result = value;
        } else {
            Object container = stack.peek();
            if (container instanceof List) {
                ((List<Object>) container).add(value);
            } else if (container instanceof Map) {
                ((Map<Name, Object>) container).put(currentKey, value);
                currentKey = null;
            }
        }
    }

}

