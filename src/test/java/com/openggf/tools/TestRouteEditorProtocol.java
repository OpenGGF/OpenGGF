package com.openggf.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The editor/worker line protocol round-trips every field and bounds its records. */
class TestRouteEditorProtocol {
    @Test
    void requestsAndResponsesRoundTripIncludingSpacesAndNewlinesInText() throws IOException {
        var request = new RouteEditorProtocol.Request(RouteEditorProtocol.Kind.SAVE, Path.of("/a b/ehz.route.json"),
                "# rows\n3 R\n1 R+A / L\n", Path.of("/a b/ehz"), true, Map.of("s2", Path.of("/roms/s 2.gen")));
        var decoded = RouteEditorProtocol.decodeRequest(new RouteEditorProtocol.LineReader(
                new StringReader(RouteEditorProtocol.encode(request))));
        assertEquals(request, decoded);
        var result = new RouteEditorProtocol.Result(RouteEditorProtocol.Verdict.FAILED, 1366, "fell: off ledge\nx",
                "c".repeat(64));
        assertEquals(result, RouteEditorProtocol.decodeResponse(RouteEditorProtocol.result(result)).result());
        var point = new RouteEditorProtocol.Point(2, 7, 3, -12, 4000, true);
        assertEquals(point, RouteEditorProtocol.decodeResponse(RouteEditorProtocol.point(point)).point());
        assertThrows(IOException.class, () -> RouteEditorProtocol.decodeResponse("point 1 2"));
        assertThrows(IOException.class, () -> RouteEditorProtocol.decodeResponse("verdict PASSED"));
        assertThrows(IOException.class, () -> new RouteEditorProtocol.LineReader(new StringReader("x".repeat(100) + "\n"))
                .readLine(10));
        assertEquals(1, RouteEditorProtocol.pathStride(100_000));
        assertEquals(2, RouteEditorProtocol.pathStride(100_001));
    }
}
