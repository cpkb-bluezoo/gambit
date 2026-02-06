/*
 * TeeConsumer.java
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

/**
 * Stream consumer that writes the same data to two downstream consumers.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class TeeConsumer implements StreamConsumer {

    private final StreamConsumer first;
    private final StreamConsumer second;
    private boolean open = true;

    /**
     * Creates a tee that writes to both consumers.
     *
     * @param first first consumer
     * @param second second consumer
     */
    public TeeConsumer(StreamConsumer first, StreamConsumer second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int remaining = src.remaining();
        if (remaining == 0) {
            return 0;
        }
        int pos = src.position();
        first.write(src);
        src.position(pos);
        second.write(src);
        return remaining;
    }

    @Override
    public void close() throws IOException {
        try {
            first.close();
        } finally {
            second.close();
        }
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        first.reset();
        second.reset();
        open = true;
    }
}
