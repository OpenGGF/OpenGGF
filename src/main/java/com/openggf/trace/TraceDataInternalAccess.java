package com.openggf.trace;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Path;

/** Engine-only cross-package access to trace reader construction. */
public final class TraceDataInternalAccess {
    private TraceDataInternalAccess() {
    }

    public static BufferedReader openTraceReader(Path path) throws IOException {
        return TraceData.openTraceReader(path);
    }
}
