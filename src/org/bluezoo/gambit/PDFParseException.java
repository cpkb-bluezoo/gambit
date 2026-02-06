/*
 * PDFParseException.java
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

/**
 * Exception thrown when a PDF document is malformed or cannot be parsed.
 * <p>
 * This exception indicates a problem with the PDF content itself, such as
 * syntax errors, invalid object references, or unsupported features.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class PDFParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long offset;

    /**
     * Creates a new exception with the specified message.
     *
     * @param message the error message
     */
    public PDFParseException(String message) {
        super(message);
        this.offset = -1;
    }

    /**
     * Creates a new exception with the specified message and byte offset.
     *
     * @param message the error message
     * @param offset the byte offset in the PDF where the error occurred
     */
    public PDFParseException(String message, long offset) {
        super(message + " at offset " + offset);
        this.offset = offset;
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public PDFParseException(String message, Throwable cause) {
        super(message, cause);
        this.offset = -1;
    }

    /**
     * Creates a new exception with the specified message, offset, and cause.
     *
     * @param message the error message
     * @param offset the byte offset in the PDF where the error occurred
     * @param cause the underlying cause
     */
    public PDFParseException(String message, long offset, Throwable cause) {
        super(message + " at offset " + offset, cause);
        this.offset = offset;
    }

    /**
     * Returns the byte offset in the PDF where the error occurred.
     *
     * @return the offset, or -1 if not available
     */
    public long getOffset() {
        return offset;
    }

}

