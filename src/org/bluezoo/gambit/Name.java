/*
 * Name.java
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
 * Represents a PDF name object.
 * <p>
 * A name object is an atomic symbol uniquely defined by a sequence of any
 * characters (8-bit values) except null (character code 0). Uniquely among
 * PDF object types, name objects are case-sensitive.
 * <p>
 * In PDF syntax, a name begins with a solidus (/) followed by the name
 * characters. The solidus is not part of the name itself. Any character
 * in a name may be represented by a number sign (#) followed by two
 * hexadecimal digits representing the character code.
 * <p>
 * This class provides an immutable representation of PDF names with
 * proper equality semantics. Names are interned where possible to
 * reduce memory usage for commonly occurring names.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class Name {

    private final String value;
    private final int hashCode;

    /**
     * Creates a new name with the specified value.
     *
     * @param value the name value (without the leading solidus)
     * @throws NullPointerException if value is null
     * @throws IllegalArgumentException if value contains null characters
     */
    public Name(String value) {
        if (value == null) {
            throw new NullPointerException("Name value cannot be null");
        }
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Name cannot contain null character");
        }
        this.value = value;
        this.hashCode = value.hashCode();
    }

    /**
     * Returns the string value of this name.
     *
     * @return the name value
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Name) {
            Name other = (Name) obj;
            return value.equals(other.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Returns the string representation of this name in PDF syntax.
     *
     * @return the name prefixed with a solidus
     */
    @Override
    public String toString() {
        return "/" + value;
    }

}

