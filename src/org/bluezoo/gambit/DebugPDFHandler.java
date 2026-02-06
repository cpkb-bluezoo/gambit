/*
 * DebugPDFHandler.java
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

/**
 * A PDFHandler implementation that prints all events to a PrintStream.
 * <p>
 * Useful for debugging and understanding the structure of PDF documents.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class DebugPDFHandler implements PDFHandler {

    private final PrintStream out;
    private int indent = 0;

    /**
     * Creates a debug handler that prints to System.out.
     */
    public DebugPDFHandler() {
        this(System.out);
    }

    /**
     * Creates a debug handler that prints to the specified stream.
     *
     * @param out the output stream
     */
    public DebugPDFHandler(PrintStream out) {
        this.out = out;
    }

    private void print(String method, Object... args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        sb.append(method);
        if (args.length > 0) {
            sb.append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatArg(args[i]));
            }
            sb.append(")");
        }
        out.println(sb.toString());
    }

    private String formatArg(Object arg) {
        if (arg == null) {
            return "null";
        } else if (arg instanceof String) {
            return "\"" + escapeString((String) arg) + "\"";
        } else if (arg instanceof ByteBuffer) {
            ByteBuffer buf = (ByteBuffer) arg;
            return "ByteBuffer[" + buf.remaining() + " bytes]";
        } else {
            return arg.toString();
        }
    }

    private String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && i < 50; i++) {
            char c = s.charAt(i);
            if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c < 32 || c > 126) sb.append(String.format("\\x%02x", (int) c));
            else sb.append(c);
        }
        if (s.length() > 50) {
            sb.append("...");
        }
        return sb.toString();
    }

    @Override
    public void setLocator(PDFLocator locator) {
        print("setLocator", locator);
    }

    @Override
    public void booleanValue(boolean value) {
        print("booleanValue", value);
    }

    @Override
    public void numberValue(Number value) {
        print("numberValue", value);
    }

    @Override
    public void stringValue(String value) {
        print("stringValue", value);
    }

    @Override
    public void nameValue(Name name) {
        print("nameValue", name);
    }

    @Override
    public void startArray() {
        print("startArray");
        indent++;
    }

    @Override
    public void endArray() {
        indent--;
        print("endArray");
    }

    @Override
    public void startDictionary() {
        print("startDictionary");
        indent++;
    }

    @Override
    public void endDictionary() {
        indent--;
        print("endDictionary");
    }

    @Override
    public void key(Name name) {
        print("key", name);
    }

    @Override
    public void nullValue() {
        print("nullValue");
    }

    @Override
    public void objectReference(ObjectId id) {
        print("objectReference", id);
    }

    @Override
    public void startObject(ObjectId id) {
        print("startObject", id);
        indent++;
    }

    @Override
    public void endObject() {
        indent--;
        print("endObject");
    }

    @Override
    public void startStream() {
        print("startStream");
        indent++;
    }

    @Override
    public void endStream() {
        indent--;
        print("endStream");
    }

    @Override
    public void streamContent(ByteBuffer content) {
        print("streamContent", content);
    }

}

