/*
 * PDFLocator.java
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
 * Interface for locating the position of parsing events within a PDF document.
 * <p>
 * This interface is similar in concept to SAX's {@code Locator}, but since
 * PDF documents are binary and not line/column based, it provides byte offset
 * information instead.
 * <p>
 * If a {@code PDFLocator} is provided via {@link PDFHandler#setLocator(PDFLocator)},
 * the handler can call {@link #getOffset()} at any time during parsing to
 * determine the byte position in the PDF that corresponds to the current
 * parsing event.
 * <p>
 * The locator information is useful for error reporting and for applications
 * that need to track the physical location of PDF objects.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public interface PDFLocator {

    /**
     * Returns the byte offset of the current parsing event.
     * <p>
     * This method returns the position in the PDF document (as a byte offset
     * from the beginning of the file) where the current parsing event began.
     * <p>
     * The offset is only meaningful during a parsing callback. If called
     * outside of a parsing context, the return value is undefined.
     *
     * @return the byte offset of the current event, or -1 if not available
     */
    long getOffset();

}

