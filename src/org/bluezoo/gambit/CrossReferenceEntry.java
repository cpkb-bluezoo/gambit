/*
 * CrossReferenceEntry.java
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
 * Represents an entry in a PDF cross-reference table.
 * <p>
 * Each entry describes the location of an indirect object within the PDF file.
 * Entries can be one of three types:
 * <ul>
 *   <li><b>Free</b> - The object has been deleted or is not in use</li>
 *   <li><b>In-use</b> - The object exists at a specific byte offset</li>
 *   <li><b>Compressed</b> - The object is stored within an object stream (PDF 1.5+)</li>
 * </ul>
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class CrossReferenceEntry {

    /**
     * Entry type for free (deleted) objects.
     */
    public static final int TYPE_FREE = 0;

    /**
     * Entry type for in-use objects at a byte offset.
     */
    public static final int TYPE_IN_USE = 1;

    /**
     * Entry type for compressed objects in an object stream.
     */
    public static final int TYPE_COMPRESSED = 2;

    private final int type;
    private final long offset;
    private final int generationOrIndex;

    /**
     * Creates a free entry.
     *
     * @param nextFreeObject the object number of the next free object
     * @param generation the generation number
     * @return the free entry
     */
    public static CrossReferenceEntry free(int nextFreeObject, int generation) {
        return new CrossReferenceEntry(TYPE_FREE, nextFreeObject, generation);
    }

    /**
     * Creates an in-use entry.
     *
     * @param offset the byte offset of the object in the file
     * @param generation the generation number
     * @return the in-use entry
     */
    public static CrossReferenceEntry inUse(long offset, int generation) {
        return new CrossReferenceEntry(TYPE_IN_USE, offset, generation);
    }

    /**
     * Creates a compressed entry for an object in an object stream.
     *
     * @param objectStreamNumber the object number of the object stream
     * @param indexInStream the index of this object within the stream
     * @return the compressed entry
     */
    public static CrossReferenceEntry compressed(int objectStreamNumber, int indexInStream) {
        return new CrossReferenceEntry(TYPE_COMPRESSED, objectStreamNumber, indexInStream);
    }

    private CrossReferenceEntry(int type, long offsetOrStreamNumber, int generationOrIndex) {
        this.type = type;
        this.offset = offsetOrStreamNumber;
        this.generationOrIndex = generationOrIndex;
    }

    /**
     * Returns the entry type.
     *
     * @return TYPE_FREE, TYPE_IN_USE, or TYPE_COMPRESSED
     */
    public int getType() {
        return type;
    }

    /**
     * Returns whether this entry represents a free (deleted) object.
     *
     * @return true if free
     */
    public boolean isFree() {
        return type == TYPE_FREE;
    }

    /**
     * Returns whether this entry represents an in-use object.
     *
     * @return true if in use
     */
    public boolean isInUse() {
        return type == TYPE_IN_USE;
    }

    /**
     * Returns whether this entry represents a compressed object.
     *
     * @return true if compressed
     */
    public boolean isCompressed() {
        return type == TYPE_COMPRESSED;
    }

    /**
     * Returns the byte offset of the object.
     * <p>
     * Only valid for in-use entries.
     *
     * @return the byte offset
     * @throws IllegalStateException if not an in-use entry
     */
    public long getOffset() {
        if (type != TYPE_IN_USE) {
            throw new IllegalStateException("Not an in-use entry");
        }
        return offset;
    }

    /**
     * Returns the generation number.
     * <p>
     * Valid for free and in-use entries.
     *
     * @return the generation number
     * @throws IllegalStateException if a compressed entry
     */
    public int getGeneration() {
        if (type == TYPE_COMPRESSED) {
            throw new IllegalStateException("Compressed entries have no generation");
        }
        return generationOrIndex;
    }

    /**
     * Returns the object number of the object stream containing this object.
     * <p>
     * Only valid for compressed entries.
     *
     * @return the object stream number
     * @throws IllegalStateException if not a compressed entry
     */
    public int getObjectStreamNumber() {
        if (type != TYPE_COMPRESSED) {
            throw new IllegalStateException("Not a compressed entry");
        }
        return (int) offset;
    }

    /**
     * Returns the index of this object within the object stream.
     * <p>
     * Only valid for compressed entries.
     *
     * @return the index in the stream
     * @throws IllegalStateException if not a compressed entry
     */
    public int getIndexInStream() {
        if (type != TYPE_COMPRESSED) {
            throw new IllegalStateException("Not a compressed entry");
        }
        return generationOrIndex;
    }

    /**
     * Returns the object number of the next free object.
     * <p>
     * Only valid for free entries.
     *
     * @return the next free object number
     * @throws IllegalStateException if not a free entry
     */
    public int getNextFreeObject() {
        if (type != TYPE_FREE) {
            throw new IllegalStateException("Not a free entry");
        }
        return (int) offset;
    }

    @Override
    public String toString() {
        switch (type) {
            case TYPE_FREE:
                return "free(next=" + offset + ", gen=" + generationOrIndex + ")";
            case TYPE_IN_USE:
                return "inUse(offset=" + offset + ", gen=" + generationOrIndex + ")";
            case TYPE_COMPRESSED:
                return "compressed(stream=" + offset + ", index=" + generationOrIndex + ")";
            default:
                return "unknown";
        }
    }

}

