/*
 * ObjectId.java
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
 * Represents a PDF indirect object identifier.
 * <p>
 * Every indirect object in a PDF document is uniquely identified by a
 * pair of integers: the object number and the generation number. The
 * object number is a positive integer, while the generation number is
 * a non-negative integer that is incremented when an object is modified
 * and its old version is freed.
 * <p>
 * In most PDF files, especially those that have not undergone incremental
 * updates, the generation number is 0 for all objects.
 * <p>
 * This class provides an immutable representation of object identifiers
 * with proper equality semantics.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class ObjectId {

    private final int objectNumber;
    private final int generationNumber;

    /**
     * Creates a new object identifier with generation number 0.
     *
     * @param objectNumber the object number (must be positive)
     * @throws IllegalArgumentException if objectNumber is not positive
     */
    public ObjectId(int objectNumber) {
        this(objectNumber, 0);
    }

    /**
     * Creates a new object identifier.
     *
     * @param objectNumber the object number (must be non-negative)
     * @param generationNumber the generation number (must be non-negative)
     * @throws IllegalArgumentException if objectNumber is negative
     *         or generationNumber is negative
     */
    public ObjectId(int objectNumber, int generationNumber) {
        if (objectNumber < 0) {
            throw new IllegalArgumentException(
                "Object number must be non-negative: " + objectNumber);
        }
        if (generationNumber < 0) {
            throw new IllegalArgumentException(
                "Generation number must be non-negative: " + generationNumber);
        }
        this.objectNumber = objectNumber;
        this.generationNumber = generationNumber;
    }

    /**
     * Returns the object number.
     *
     * @return the object number
     */
    public int getObjectNumber() {
        return objectNumber;
    }

    /**
     * Returns the generation number.
     *
     * @return the generation number
     */
    public int getGenerationNumber() {
        return generationNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ObjectId) {
            ObjectId other = (ObjectId) obj;
            return objectNumber == other.objectNumber
                && generationNumber == other.generationNumber;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * objectNumber + generationNumber;
    }

    /**
     * Returns the string representation of this object identifier in PDF syntax.
     * <p>
     * For an object reference, this returns the format "n g R" where n is the
     * object number and g is the generation number.
     *
     * @return the object reference in PDF syntax
     */
    @Override
    public String toString() {
        return objectNumber + " " + generationNumber + " R";
    }

}

