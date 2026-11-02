package com.openggf.editor.route;

import com.openggf.route.InitialState;
import com.openggf.route.Inlet;
import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.LevelId;
import com.openggf.route.Provenance;
import com.openggf.route.RomIdentity;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteFiles;
import com.openggf.route.Roster;
import com.openggf.route.Seeds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Route files written in the published manifest format, as a route author would commit them. */
public final class RouteTapeFixtures {
    private RouteTapeFixtures() {
    }

    public static LaunchContract ehz() {
        return new LaunchContract(RomIdentity.standard("s2"), new Inlet.ColdLevel(new LevelId("s2", 0, 0), false),
                new Roster("sonic", "tails"), 320, "off", new InitialState(0, Seeds.NONE));
    }

    /**
     * Writes {@code <stem>.route.json} and {@code <stem>.script} for {@code script} rows.
     *
     * @param stamps raw qualification stamp objects ({@code "%CONTENT%"} becomes the route's content hash)
     * @param nativeJson raw {@code native} object
     */
    public static Path write(Path directory, String stem, String script, List<String> stamps, String nativeJson)
            throws IOException {
        InputTape tape = RouteFiles.tapeFromScript(script);
        String content = RouteArtifact.of(ehz(), tape, new Provenance("test", "", "", List.of())).content().toString();
        Files.writeString(directory.resolve(stem + ".script"), script, StandardCharsets.UTF_8);
        String manifest = """
                {
                  "format": "openggf-route/1",
                  "launch": {
                    "rom": {"game": "s2", "crc32": "7B905383", "sha1": "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9"},
                    "inlet": {"kind": "cold-level", "zone": 0, "act": 0, "showTitleCard": false},
                    "roster": {"main": "sonic", "sidekick": "tails"},
                    "width": 320,
                    "donor": "off",
                    "initial": {"vIntStart": 0}
                  },
                  "tape": {"script": "%SCRIPT%", "rows": %ROWS%, "sha256": "%SHA%"},
                  "provenance": {"producer": "test", "engine": "", "source": "", "notes": []},
                  "qualification": [%STAMPS%],
                  "native": %NATIVE%
                }
                """.replace("%SCRIPT%", stem + ".script").replace("%ROWS%", String.valueOf(tape.size()))
                .replace("%SHA%", tape.hash().sha256()).replace("%STAMPS%", String.join(",", stamps))
                .replace("%NATIVE%", nativeJson).replace("%CONTENT%", content);
        Path path = directory.resolve(stem + RouteFiles.MANIFEST_SUFFIX);
        Files.writeString(path, manifest, StandardCharsets.UTF_8);
        return path;
    }

    public static Path write(Path directory, String stem, String script) throws IOException {
        return write(directory, stem, script, List.of(), "{\"lag\": []}");
    }

    public static String engineStamp(String verdict) {
        return "{\"scope\": \"ENGINE\", \"verdict\": \"" + verdict + "\", \"content\": \"%CONTENT%\", "
                + "\"environment\": \"test\", \"rows\": 1}";
    }
}
