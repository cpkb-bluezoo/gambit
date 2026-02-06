/*
 * FilterPipeline.java
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
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds and manages a filter pipeline for decoding stream content.
 * <p>
 * The pipeline consists of zero or more filters followed by a final consumer.
 * Data is pushed through the pipeline in a feedforward manner:
 * <pre>
 *   Input → Filter1 → Filter2 → ... → Consumer
 * </pre>
 * <p>
 * Implements {@link WritableByteChannel} for standard NIO semantics.
 * <p>
 * Usage:
 * <pre>
 *   FilterPipeline pipeline = FilterPipeline.create(streamDict, finalConsumer);
 *   pipeline.write(data);
 *   pipeline.close();
 * </pre>
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class FilterPipeline implements StreamConsumer {

    private final WritableByteChannel head;
    private final List<StreamFilter> filters;
    private boolean open = true;

    private FilterPipeline(WritableByteChannel head, List<StreamFilter> filters) {
        this.head = head;
        this.filters = filters;
    }

    /**
     * Creates a filter pipeline based on the stream dictionary.
     *
     * @param streamDict the stream dictionary (may contain /Filter and /DecodeParms)
     * @param finalConsumer the consumer to receive decoded data
     * @return a new pipeline
     */
    public static FilterPipeline create(Map<Name, Object> streamDict, WritableByteChannel finalConsumer) {
        List<StreamFilter> filters = new ArrayList<>();
        WritableByteChannel current = finalConsumer;
        
        if (streamDict == null) {
            return new FilterPipeline(finalConsumer, filters);
        }
        
        // Get filter(s)
        Object filterObj = streamDict.get(new Name("Filter"));
        if (filterObj == null) {
            return new FilterPipeline(finalConsumer, filters);
        }
        
        // Get decode parameters (parallel array or single dict)
        Object paramsObj = streamDict.get(new Name("DecodeParms"));
        if (paramsObj == null) {
            paramsObj = streamDict.get(new Name("DP")); // Abbreviation
        }
        
        // Build filter list
        List<String> filterNames = new ArrayList<>();
        List<Map<Name, Object>> paramsList = new ArrayList<>();
        
        if (filterObj instanceof Name) {
            filterNames.add(((Name) filterObj).getValue());
            paramsList.add(extractParams(paramsObj, 0));
        } else if (filterObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> filterList = (List<Object>) filterObj;
            for (int i = 0; i < filterList.size(); i++) {
                Object f = filterList.get(i);
                if (f instanceof Name) {
                    filterNames.add(((Name) f).getValue());
                    paramsList.add(extractParams(paramsObj, i));
                }
            }
        } else if (filterObj instanceof Object[]) {
            Object[] filterArray = (Object[]) filterObj;
            for (int i = 0; i < filterArray.length; i++) {
                Object f = filterArray[i];
                if (f instanceof Name) {
                    filterNames.add(((Name) f).getValue());
                    paramsList.add(extractParams(paramsObj, i));
                }
            }
        }
        
        // Build pipeline in reverse order (last filter connects to consumer first)
        for (int i = filterNames.size() - 1; i >= 0; i--) {
            String filterName = filterNames.get(i);
            StreamFilter filter = StreamFilter.create(filterName);
            
            if (filter != null) {
                filter.setParams(paramsList.get(i));
                filter.setNext(current);
                filters.add(0, filter); // Add to front of list
                current = filter;
            }
            // If filter is not supported, skip it (data passes through unchanged)
        }
        
        return new FilterPipeline(current, filters);
    }

    /**
     * Extracts decode parameters for a specific filter index.
     */
    @SuppressWarnings("unchecked")
    private static Map<Name, Object> extractParams(Object paramsObj, int index) {
        if (paramsObj == null) {
            return null;
        }
        
        if (paramsObj instanceof Map) {
            // Single params dict - applies to single filter or first in chain
            return index == 0 ? (Map<Name, Object>) paramsObj : null;
        }
        
        if (paramsObj instanceof List) {
            List<Object> paramsList = (List<Object>) paramsObj;
            if (index < paramsList.size()) {
                Object p = paramsList.get(index);
                if (p instanceof Map) {
                    return (Map<Name, Object>) p;
                }
            }
        }
        
        if (paramsObj instanceof Object[]) {
            Object[] paramsArray = (Object[]) paramsObj;
            if (index < paramsArray.length) {
                Object p = paramsArray[index];
                if (p instanceof Map) {
                    return (Map<Name, Object>) p;
                }
            }
        }
        
        return null;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return head.write(src);
    }

    @Override
    public void close() throws IOException {
        head.close();
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void reset() {
        for (StreamFilter filter : filters) {
            filter.reset();
        }
        open = true;
    }

    /**
     * Returns true if this pipeline has any filters.
     *
     * @return true if there are filters in the pipeline
     */
    public boolean hasFilters() {
        return !filters.isEmpty();
    }

}
