/*
 * DebugCMapHandler.java
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
import java.util.List;

/**
 * A CMapHandler implementation that prints all events for debugging.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class DebugCMapHandler implements CMapHandler {

    private final java.io.PrintStream out;

    public DebugCMapHandler() {
        this(System.out);
    }

    public DebugCMapHandler(java.io.PrintStream out) {
        this.out = out;
    }

    private static String hex(ByteBuffer b) {
        if (b == null || !b.hasRemaining()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        for (int i = b.position(); i < b.limit(); i++) {
            sb.append(String.format("%02X", b.get(i) & 0xFF));
        }
        sb.append('>');
        return sb.toString();
    }

    @Override
    public void startCMap() {
        out.println("startCMap()");
    }

    @Override
    public void endCMap() {
        out.println("endCMap()");
    }

    @Override
    public void codeSpaceRange(ByteBuffer low, ByteBuffer high) {
        out.println("codeSpaceRange(" + hex(low) + ", " + hex(high) + ")");
    }

    @Override
    public void bfchar(ByteBuffer fromCode, ByteBuffer toUnicode) {
        out.println("bfchar(" + hex(fromCode) + " -> " + hex(toUnicode) + ")");
    }

    @Override
    public void bfrange(ByteBuffer from, ByteBuffer to, ByteBuffer startUnicode) {
        out.println("bfrange(" + hex(from) + "-" + hex(to) + " -> " + hex(startUnicode) + ")");
    }

    @Override
    public void bfrange(ByteBuffer from, ByteBuffer to, List<ByteBuffer> dests) {
        StringBuilder sb = new StringBuilder();
        for (ByteBuffer d : dests) sb.append(hex(d)).append(" ");
        out.println("bfrange(" + hex(from) + "-" + hex(to) + " -> [ " + sb + "])");
    }
}
