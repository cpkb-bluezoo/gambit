/*
 * ObjectReference.java
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
 * An object reference with associated stream type context.
 * <p>
 * When traversing the document, we track not just which objects to process
 * but also the context in which they were referenced. This allows us to
 * determine the correct stream type when the object contains a stream.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
final class ObjectReference {

    private final ObjectId id;
    private final StreamType streamType;

    /**
     * Creates a reference with default stream type.
     *
     * @param id the object identifier
     */
    ObjectReference(ObjectId id) {
        this(id, StreamType.DEFAULT);
    }

    /**
     * Creates a reference with specified stream type.
     *
     * @param id the object identifier
     * @param streamType the expected stream type, or null for default
     */
    ObjectReference(ObjectId id, StreamType streamType) {
        this.id = id;
        this.streamType = streamType != null ? streamType : StreamType.DEFAULT;
    }

    /**
     * Returns the object identifier.
     *
     * @return the object ID
     */
    ObjectId getId() {
        return id;
    }

    /**
     * Returns the expected stream type.
     *
     * @return the stream type
     */
    StreamType getStreamType() {
        return streamType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ObjectReference)) {
            return false;
        }
        ObjectReference other = (ObjectReference) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString() + "[" + streamType + "]";
    }

}

