/*
 * CMapParser.java
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses PDF CMap streams (e.g. ToUnicode) and dispatches events to a {@link CMapHandler}.
 * <p>
 * Supports begincodespacerange/endcodespacerange, beginbfchar/endbfchar,
 * and beginbfrange/endbfrange (both single-dest and array form). Implements
 * {@link StreamParser} for incremental parsing; handles partial tokens at
 * buffer boundaries.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class CMapParser implements StreamParser {

    private static final int TOKEN_KEYWORD = 0;
    private static final int TOKEN_HEX = 1;
    private static final int TOKEN_ARRAY = 2;

    private final CMapHandler handler;
    private boolean started;
    private boolean open = true;
    private int section; // 0=none, 1=codespacerange, 2=bfchar, 3=bfrange

    public CMapParser(CMapHandler handler) {
        this.handler = handler;
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        int startPosition = buffer.position();
        if (!started) {
            handler.startCMap();
            started = true;
        }
        while (buffer.hasRemaining()) {
            int mark = buffer.position();
            skipWhitespaceAndComments(buffer);
            if (!buffer.hasRemaining()) {
                break;
            }
            int tok = peekToken(buffer);
            if (tok == TOKEN_KEYWORD) {
                String kw = readKeyword(buffer);
                if (kw == null) {
                    buffer.position(mark);
                    break;
                }
                if (!dispatchKeyword(kw, buffer)) {
                    buffer.position(mark);
                    break;
                }
            } else if (tok == TOKEN_HEX) {
                ByteBuffer hex = readHexString(buffer);
                if (hex == null) {
                    buffer.position(mark);
                    break;
                }
                if (!handleHexInSection(hex, buffer)) {
                    buffer.position(mark);
                    break;
                }
            } else if (tok == TOKEN_ARRAY) {
                List<ByteBuffer> arr = readHexArray(buffer);
                if (arr == null) {
                    buffer.position(mark);
                    break;
                }
                if (!handleBfrangeArray(arr, buffer)) {
                    buffer.position(mark);
                    break;
                }
            } else {
                buffer.get();
            }
        }
        return buffer.position() - startPosition;
    }

    @Override
    public void close() throws IOException {
        if (started) {
            handler.endCMap();
            started = false;
        }
        section = 0;
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        started = false;
        section = 0;
        open = true;
    }

    private int peekToken(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        int b = buffer.get(buffer.position()) & 0xFF;
        if (b == '<') {
            if (buffer.remaining() > 1 && buffer.get(buffer.position() + 1) != '<') {
                return TOKEN_HEX;
            }
        } else if (b == '[') {
            return TOKEN_ARRAY;
        } else if (isAlpha(b)) {
            return TOKEN_KEYWORD;
        }
        return -1;
    }

    private boolean dispatchKeyword(String kw, ByteBuffer buffer) {
        switch (kw) {
            case "begincodespacerange":
                section = 1;
                return true;
            case "endcodespacerange":
                section = 0;
                return true;
            case "beginbfchar":
                section = 2;
                return true;
            case "endbfchar":
                section = 0;
                return true;
            case "beginbfrange":
                section = 3;
                return true;
            case "endbfrange":
                section = 0;
                return true;
            case "begincmap":
            case "endcmap":
                return true;
            default:
                return true;
        }
    }

    private ByteBuffer bfcharFrom;
    private ByteBuffer bfrangeFrom;
    private ByteBuffer bfrangeTo;

    private boolean handleHexInSection(ByteBuffer hex, ByteBuffer buffer) {
        switch (section) {
            case 1: {
                int mark = buffer.position();
                skipWhitespaceAndComments(buffer);
                if (!buffer.hasRemaining()) {
                    return false;
                }
                ByteBuffer high = readHexString(buffer);
                if (high == null) {
                    return false;
                }
                handler.codeSpaceRange(hex, high);
                return true;
            }
            case 2: {
                int mark = buffer.position();
                skipWhitespaceAndComments(buffer);
                if (!buffer.hasRemaining()) {
                    return false;
                }
                ByteBuffer to = readHexString(buffer);
                if (to == null) {
                    return false;
                }
                handler.bfchar(hex, to);
                return true;
            }
            case 3:
                if (bfrangeFrom == null) {
                    bfrangeFrom = hex;
                    return true;
                }
                if (bfrangeTo == null) {
                    bfrangeTo = hex;
                    return true;
                }
                handler.bfrange(bfrangeFrom, bfrangeTo, hex);
                bfrangeFrom = null;
                bfrangeTo = null;
                return true;
            default:
                return true;
        }
    }

    private boolean handleBfrangeArray(List<ByteBuffer> dests, ByteBuffer buffer) {
        if (section != 3 || bfrangeFrom == null || bfrangeTo == null) {
            return true;
        }
        handler.bfrange(bfrangeFrom, bfrangeTo, dests);
        bfrangeFrom = null;
        bfrangeTo = null;
        return true;
    }

    private void skipWhitespaceAndComments(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            int b = buffer.get(buffer.position()) & 0xFF;
            if (b == '%') {
                while (buffer.hasRemaining() && buffer.get() != '\n') {}
                continue;
            }
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '\f' || b == 0) {
                buffer.get();
                continue;
            }
            break;
        }
    }

    private String readKeyword(ByteBuffer buffer) {
        if (!buffer.hasRemaining() || !isAlpha(buffer.get(buffer.position()) & 0xFF)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            int b = buffer.get(buffer.position()) & 0xFF;
            if (!isAlpha(b) && b != '-') {
                break;
            }
            sb.append((char) buffer.get());
        }
        return sb.toString();
    }

    private ByteBuffer readHexString(ByteBuffer buffer) {
        if (buffer.remaining() < 2 || buffer.get(buffer.position()) != '<') {
            return null;
        }
        int start = buffer.position();
        buffer.get(); // '<'
        StringBuilder hex = new StringBuilder();
        while (buffer.hasRemaining()) {
            int b = buffer.get() & 0xFF;
            if (b == '>') {
                byte[] out = decodeHex(hex);
                return ByteBuffer.wrap(out);
            }
            if (isWhitespace(b)) {
                continue;
            }
            int h = hexValue(b);
            if (h < 0) {
                buffer.position(start);
                return null;
            }
            hex.append((char) b);
        }
        buffer.position(start);
        return null;
    }

    private static byte[] decodeHex(StringBuilder hex) {
        String s = hex.toString();
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (hexValue(s.charAt(i)) >= 0) {
                n++;
            }
        }
        int byteLen = (n + 1) / 2;
        byte[] out = new byte[byteLen];
        int first = -1;
        int idx = 0;
        for (int i = 0; i < s.length() && idx < byteLen; i++) {
            int h = hexValue(s.charAt(i));
            if (h < 0) {
                continue;
            }
            if (first < 0) {
                first = h;
            } else {
                out[idx++] = (byte) ((first << 4) | h);
                first = -1;
            }
        }
        if (first >= 0 && idx < byteLen) {
            out[idx++] = (byte) (first << 4);
        }
        return idx < byteLen ? java.util.Arrays.copyOf(out, idx) : out;
    }

    private List<ByteBuffer> readHexArray(ByteBuffer buffer) {
        if (!buffer.hasRemaining() || buffer.get(buffer.position()) != '[') {
            return null;
        }
        buffer.get();
        List<ByteBuffer> list = new ArrayList<>();
        while (buffer.hasRemaining()) {
            skipWhitespaceAndComments(buffer);
            if (!buffer.hasRemaining()) {
                return null;
            }
            int b = buffer.get(buffer.position()) & 0xFF;
            if (b == ']') {
                buffer.get();
                return list;
            }
            if (b == '<') {
                ByteBuffer hex = readHexString(buffer);
                if (hex == null) {
                    return null;
                }
                list.add(hex);
            } else {
                buffer.get();
            }
        }
        return null;
    }

    private static boolean isAlpha(int b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z');
    }

    private static boolean isWhitespace(int b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '\f' || b == 0;
    }

    private static int hexValue(int b) {
        if (b >= '0' && b <= '9') {
            return b - '0';
        }
        if (b >= 'A' && b <= 'F') {
            return b - 'A' + 10;
        }
        if (b >= 'a' && b <= 'f') {
            return b - 'a' + 10;
        }
        return -1;
    }
}
