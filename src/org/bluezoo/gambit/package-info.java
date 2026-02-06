/*
 * package-info.java
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

/**
 * Gambit PDF Parser.
 * <p>
 * A streaming PDF parser that delivers parsing events via a SAX-like
 * callback handler. The parser is designed to support efficient parsing
 * of both local files and remote resources via HTTP byte ranges.
 * <p>
 * The main entry points are:
 * <ul>
 *   <li>{@link org.bluezoo.gambit.PDFParser} - The PDF parser</li>
 *   <li>{@link org.bluezoo.gambit.PDFHandler} - The callback handler interface</li>
 * </ul>
 * <p>
 * Core PDF object types represented in this package:
 * <ul>
 *   <li>{@link org.bluezoo.gambit.Name} - PDF name objects</li>
 *   <li>{@link org.bluezoo.gambit.ObjectId} - Indirect object identifiers</li>
 * </ul>
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
package org.bluezoo.gambit;

