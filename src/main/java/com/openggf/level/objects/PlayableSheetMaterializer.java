package com.openggf.level.objects;

import com.openggf.game.ModApi;
import com.openggf.level.Palette;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Converts a strict GGFP v2 container into the complete runtime playable-art view. */
@ModApi
public final class PlayableSheetMaterializer {
    private PlayableSheetMaterializer() { }

    public static MaterializedArt read(byte[] encoded) throws IOException {
        PlayableSheetReader.PlayableSheet sheet = PlayableSheetReader.read(encoded);
        BakedSheetReader.BakedSheet base = BakedSheetReader.read(sheet.baseSheetV1());
        List<SpriteDplcFrame> dplcs = sheet.frames().stream()
                .map(frame -> new SpriteDplcFrame(frame.runs().stream()
                        .map(run -> new TileLoadRequest(
                                run.sourceTile(), run.tileCount(), run.bankOffset()))
                        .toList()))
                .toList();
        SpriteAnimationSet animations = new SpriteAnimationSet();
        var mappings = new ArrayList<>(base.frames().stream().map(BakedSheetReader.Frame::mapping).toList());
        dplcs = new ArrayList<>(dplcs);
        var strides = new java.util.EnumMap<com.openggf.game.CanonicalAnimation, Integer>(com.openggf.game.CanonicalAnimation.class);
        for (var entry : sheet.animations().entrySet()) {
            var canonical = com.openggf.game.CanonicalAnimation.valueOf(entry.getKey());
            var animation = entry.getValue();
            var script = materializeAnimation(animation);
            if (animation.speedDriven()) {
                int flag = switch (canonical) {
                    case WALK, RUN -> 0xFF; case ROLL, ROLL2 -> 0xFE; case PUSH -> 0xFD;
                    default -> throw new IOException("Unsupported speed-driven animation");
                };
                List<Integer> cycle = script.frames();
                if (canonical == com.openggf.game.CanonicalAnimation.WALK || canonical == com.openggf.game.CanonicalAnimation.RUN) {
                    // Native slope selector yields 0,2,4,6; pad each set to an even stride.
                    int width = (cycle.size() + 1) & ~1;
                    int first = mappings.size();
                    for (int angle = 0; angle < 4; angle++) {
                        var set = animation.slopes().isEmpty() ? cycle : animation.slopes().get(angle);
                        for (int f = 0; f < width; f++) {
                            int index = set.get(f % set.size());
                            mappings.add(base.frames().get(index).mapping());
                            dplcs.add(dplcs.get(index));
                        }
                    }
                    strides.put(canonical, width / 2);
                    cycle = java.util.stream.IntStream.range(0, cycle.size()).map(i -> first + i).boxed().toList();
                }
                script = new SpriteAnimationScript(flag, cycle, script.endAction(), script.endParam());
            }
            animations.addScript(canonical.ordinal(), script);
        }
        SpriteAnimationProfile profile = new PlayableSheetAnimationBinding.CanonicalProfile(strides);
        int delay = base.frames().isEmpty() ? 0 : base.frames().getFirst().delay();
        SpriteArtSet art = new SpriteArtSet(base.patterns(),
                mappings, dplcs,
                sheet.meta().paletteLine(), sheet.meta().basePatternIndex(), delay,
                sheet.meta().bankSize(), profile, animations);
        return new MaterializedArt(art, materializePalette(base));
    }

    private static SpriteAnimationScript materializeAnimation(
            PlayableSheetReader.Animation animation) throws IOException {
        for (int i = 0; i + 1 < animation.steps().size(); i++) {
            if (animation.steps().get(i).loop()) {
                throw new IOException("Invalid playable sheet: loop marker must be on final animation step");
            }
        }
        int quantum = 0;
        for (var step : animation.steps()) quantum = gcd(quantum, step.duration());
        if (quantum > 128) {
            for (int candidate = 128; candidate >= 1; candidate--) {
                if (quantum % candidate == 0) {
                    quantum = candidate;
                    break;
                }
            }
        }
        ArrayList<Integer> frames = new ArrayList<>();
        for (var step : animation.steps()) {
            int repeats = step.duration() / quantum;
            if ((long) frames.size() + repeats > 65_536) {
                throw new IOException("Invalid playable sheet: expanded animation exceeds limit");
            }
            for (int i = 0; i < repeats; i++) frames.add(step.frameIndex());
        }
        return new SpriteAnimationScript(Math.max(0, quantum - 1), List.copyOf(frames),
                animation.endAction(), com.openggf.game.CanonicalAnimation.valueOf(animation.nextAnimation()).ordinal());
    }

    private static Palette materializePalette(BakedSheetReader.BakedSheet base) throws IOException {
        var source = base.palette().orElseThrow(
                () -> new IOException("Invalid playable sheet: BASE palette is required"));
        byte[] encoded = new byte[Palette.PALETTE_SIZE_IN_ROM];
        int[] colors = source.colors();
        for (int i = 0; i < colors.length; i++) {
            encoded[i * 2] = (byte) (colors[i] >>> 8);
            encoded[i * 2 + 1] = (byte) colors[i];
        }
        Palette palette = new Palette();
        palette.fromSegaFormat(encoded);
        return palette;
    }

    private static int gcd(int left, int right) {
        while (right != 0) { int next = left % right; left = right; right = next; }
        return left;
    }

    @ModApi
    public record MaterializedArt(SpriteArtSet art, Palette palette) {
        public MaterializedArt {
            java.util.Objects.requireNonNull(art, "art");
            palette = java.util.Objects.requireNonNull(palette, "palette").deepCopy();
        }
        @Override public Palette palette() { return palette.deepCopy(); }
    }
}
