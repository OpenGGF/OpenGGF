package com.openggf.game.timeattack;

import com.openggf.game.ghost.GhostFileCodec;
import com.openggf.game.ghost.GhostHeader;
import com.openggf.game.ghost.GhostRecording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

/** Best-run persistence: ghosts/<gameId>/<zone>-<act>-<character>.ggfghost, keep last 3 (spec §3). */
@com.openggf.game.ModApi
public final class GhostStore {
    private final Path root;

    public GhostStore(Path root) {
        this.root = root;
    }

    public Optional<GhostRecording> loadBest(String gameId, int zone, int act, String character)
            throws IOException {
        Path best = bestPath(gameId, zone, act, character);
        if (!Files.exists(best)) {
            return Optional.empty();
        }
        return Optional.of(GhostFileCodec.read(best));
    }

    public boolean saveIfBest(GhostRecording candidate, AttemptInputRecording inputs) throws IOException {
        GhostHeader h = candidate.header();
        if (!java.util.Arrays.equals(h.inputRecordingHash(), inputs.sha256())) {
            throw new IllegalArgumentException(
                    "ghost header inputRecordingHash does not match the supplied input recording");
        }
        Path best = bestPath(h.gameId(), h.zone(), h.act(), h.character());
        if (Files.exists(best)
                && GhostFileCodec.read(best).header().finalTimeFrames() <= h.finalTimeFrames()) {
            return false;
        }
        rotate(best, "-prev1", "-prev2");
        GhostFileCodec.write(candidate, best);
        Files.write(sibling(best, ".ggfinputs"), inputs.encode());
        return true;
    }

    public List<Path> listImports(String gameId) throws IOException {
        Path dir = root.resolve(gameId).resolve("import");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".ggfghost"))
                    .sorted().toList();
        }
    }

    /** Finds a persisted best/previous input sidecar by its content hash. */
    public Optional<byte[]> findInputRecording(String hashHex) throws IOException {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString()
                    .endsWith(".ggfinputs")).toList()) {
                byte[] encoded = Files.readAllBytes(file);
                AttemptInputRecording recording;
                try {
                    recording = AttemptInputRecording.decode(encoded);
                } catch (IllegalArgumentException invalid) {
                    continue;
                }
                if (HexFormat.of().formatHex(recording.sha256())
                        .equalsIgnoreCase(hashHex)) {
                    return Optional.of(encoded);
                }
            }
        }
        return Optional.empty();
    }

    private Path bestPath(String gameId, int zone, int act, String character) {
        return root.resolve(gameId).resolve(zone + "-" + act + "-" + character + ".ggfghost");
    }

    private void rotate(Path best, String prev1Suffix, String prev2Suffix) throws IOException {
        Path prev1 = stemSuffix(best, prev1Suffix);
        Path prev2 = stemSuffix(best, prev2Suffix);
        if (Files.exists(prev1)) {
            Files.move(prev1, prev2, StandardCopyOption.REPLACE_EXISTING);
            moveIfExists(sibling(prev1, ".ggfinputs"), sibling(prev2, ".ggfinputs"));
        }
        if (Files.exists(best)) {
            Files.move(best, prev1, StandardCopyOption.REPLACE_EXISTING);
            moveIfExists(sibling(best, ".ggfinputs"), sibling(prev1, ".ggfinputs"));
        }
    }

    private static void moveIfExists(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path stemSuffix(Path ghostFile, String suffix) {
        String name = ghostFile.getFileName().toString();
        String stem = name.substring(0, name.length() - ".ggfghost".length());
        return ghostFile.resolveSibling(stem + suffix + ".ggfghost");
    }

    private static Path sibling(Path ghostFile, String extension) {
        String name = ghostFile.getFileName().toString();
        String stem = name.substring(0, name.length() - ".ggfghost".length());
        return ghostFile.resolveSibling(stem + extension);
    }
}
