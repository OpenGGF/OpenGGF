package com.openggf.tools.modsdk;

import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.objects.PlayableSheetReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds GGFP v2 playable art from the existing exact-palette sheet input. */
public final class PlayableArtConverter {
    public Result convert(Path image, Path sheetManifest, Path output) throws IOException {
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IllegalArgumentException("playable output requires a parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".ggfmod-playable-base-", ".ggfs");
        Files.deleteIfExists(temporary);
        try {
            ArtConverter.Result baseResult = new ArtConverter().convert(image, sheetManifest, temporary);
            byte[] ggfs = Files.readAllBytes(temporary);
            BakedSheetReader.BakedSheet base = BakedSheetReader.read(ggfs);
            int bankSize = 1;
            List<PlayableSheetReader.Frame> frames = new ArrayList<>();
            for (var frame : base.frames()) {
                int first = Integer.MAX_VALUE, end = 0;
                for (var piece : frame.mapping().pieces()) {
                    first = Math.min(first, piece.tileIndex());
                    end = Math.max(end, piece.tileIndex() + piece.widthTiles() * piece.heightTiles());
                }
                if (first == Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Playable frames require at least one mapping piece");
                }
                int count = end - first;
                bankSize = Math.max(bankSize, end);
                frames.add(new PlayableSheetReader.Frame(0, 0, 1, 1,
                        List.of(new PlayableSheetReader.DplcRun(first, count, first))));
            }
            Map<String, PlayableSheetReader.Animation> animations = new LinkedHashMap<>();
            List<PlayableSheetReader.AnimationStep> steps = new ArrayList<>();
            for (int i = 0; i < base.frames().size(); i++) {
                steps.add(new PlayableSheetReader.AnimationStep(i,
                        Math.max(1, base.frames().get(i).delay()), i + 1 == base.frames().size()));
            }
            animations.put("idle", new PlayableSheetReader.Animation(steps));
            int paletteLine = base.palette().map(BakedSheetReader.Palette::line).orElseThrow(
                    () -> new IllegalArgumentException("Playable art requires a 16-color palette"));
            byte[] encoded = PlayableSheetWriter.write(new PlayableSheetReader.PlayableSheet(
                    ggfs, new PlayableSheetReader.Meta(0, bankSize, paletteLine),
                    frames, animations, Map.of()));
            if (Files.exists(output)) throw new IOException("Playable art output already exists: " + output);
            Path staging = Files.createTempFile(parent, ".ggfmod-playable-", ".ggfp");
            boolean published = false;
            try {
                Files.write(staging, encoded);
                NoClobberPublisher.publish(staging, output.toAbsolutePath().normalize(), () -> {});
                published = true;
            } finally {
                if (!published) Files.deleteIfExists(staging);
            }
            return new Result(output, baseResult.patternCount(), baseResult.frameCount(), bankSize);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record Result(Path output, int patternCount, int frameCount, int bankSize) { }
}
