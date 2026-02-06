/*
 * CrossReferenceTable.java
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

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory representation of a PDF cross-reference table.
 * <p>
 * The cross-reference table maps object identifiers to their locations
 * within the PDF file. It can be populated from either a legacy xref
 * section or an XRef stream (PDF 1.5+).
 * <p>
 * For PDFs with incremental updates, multiple xref sections may exist.
 * This class handles merging entries, with later entries taking precedence
 * over earlier ones for the same object.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class CrossReferenceTable {

    private final Map<ObjectId, CrossReferenceEntry> entries;
    private int maxObjectNumber;

    /**
     * Creates an empty cross-reference table.
     */
    public CrossReferenceTable() {
        this.entries = new HashMap<>();
        this.maxObjectNumber = 0;
    }

    /**
     * Adds or updates an entry in the table.
     * <p>
     * If an entry for the same object already exists, it is replaced.
     *
     * @param objectNumber the object number
     * @param generation the generation number
     * @param entry the cross-reference entry
     */
    public void put(int objectNumber, int generation, CrossReferenceEntry entry) {
        ObjectId id = new ObjectId(objectNumber, generation);
        entries.put(id, entry);
        if (objectNumber > maxObjectNumber) {
            maxObjectNumber = objectNumber;
        }
    }

    /**
     * Adds or updates an entry in the table.
     *
     * @param id the object identifier
     * @param entry the cross-reference entry
     */
    public void put(ObjectId id, CrossReferenceEntry entry) {
        entries.put(id, entry);
        if (id.getObjectNumber() > maxObjectNumber) {
            maxObjectNumber = id.getObjectNumber();
        }
    }

    /**
     * Returns the entry for the specified object.
     *
     * @param objectNumber the object number
     * @param generation the generation number
     * @return the entry, or null if not found
     */
    public CrossReferenceEntry get(int objectNumber, int generation) {
        return entries.get(new ObjectId(objectNumber, generation));
    }

    /**
     * Returns the entry for the specified object.
     *
     * @param id the object identifier
     * @return the entry, or null if not found
     */
    public CrossReferenceEntry get(ObjectId id) {
        return entries.get(id);
    }

    /**
     * Returns whether an entry exists for the specified object.
     *
     * @param objectNumber the object number
     * @param generation the generation number
     * @return true if an entry exists
     */
    public boolean contains(int objectNumber, int generation) {
        return entries.containsKey(new ObjectId(objectNumber, generation));
    }

    /**
     * Returns whether an entry exists for the specified object.
     *
     * @param id the object identifier
     * @return true if an entry exists
     */
    public boolean contains(ObjectId id) {
        return entries.containsKey(id);
    }

    /**
     * Returns the number of entries in the table.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns the highest object number in the table.
     *
     * @return the maximum object number
     */
    public int getMaxObjectNumber() {
        return maxObjectNumber;
    }

    /**
     * Returns all object identifiers in the table.
     *
     * @return iterable of object IDs
     */
    public Iterable<ObjectId> getObjectIds() {
        return entries.keySet();
    }

    /**
     * Merges entries from another cross-reference table.
     * <p>
     * Entries from the other table take precedence over existing entries
     * for the same object, which is the correct behavior for incremental
     * updates where newer xref sections override older ones.
     *
     * @param other the table to merge from
     */
    public void merge(CrossReferenceTable other) {
        for (Map.Entry<ObjectId, CrossReferenceEntry> e : other.entries.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public String toString() {
        return "CrossReferenceTable[entries=" + entries.size() + 
               ", maxObject=" + maxObjectNumber + "]";
    }

}

