package com.openggf.level.objects;

import com.openggf.tools.modsdk.PlayableSheetWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestPlayableSheetMaterializer {
    @Test
    void materializesEveryRuntimeArtComponentAndEmbeddedPalette() throws Exception {
        var sheet = new PlayableSheetReader.PlayableSheet(base(),
                new PlayableSheetReader.Meta(0x120000, 4, 0),
                List.of(new PlayableSheetReader.Frame(-3, 4, 16, 20,
                        List.of(new PlayableSheetReader.DplcRun(0, 1, 2)))),
                Map.of("idle", new PlayableSheetReader.Animation(List.of(
                        new PlayableSheetReader.AnimationStep(0, 2, true)))), Map.of());

        PlayableSheetMaterializer.MaterializedArt materialized =
                PlayableSheetMaterializer.read(PlayableSheetWriter.write(sheet));

        assertEquals(1, materialized.art().artTiles().length);
        assertEquals(1, materialized.art().mappingFrames().size());
        assertEquals(1, materialized.art().dplcFrames().size());
        assertEquals(0x120000, materialized.art().basePatternIndex());
        assertEquals(4, materialized.art().bankSize());
        assertNotNull(materialized.art().animationProfile());
        assertNotNull(materialized.art().animationSet());
        assertEquals(1, materialized.art().animationSet().getScriptCount());
        assertEquals(0, materialized.palette().getColor(0).r & 0xff);
        assertEquals(255, materialized.palette().getColor(1).r & 0xff);
    }

    @Test
    void rejectsLoopMarkerBeforeFinalAnimationStep() throws Exception {
        var sheet = new PlayableSheetReader.PlayableSheet(base(),
                new PlayableSheetReader.Meta(0, 1, 0),
                List.of(new PlayableSheetReader.Frame(0, 0, 16, 16,
                        List.of(new PlayableSheetReader.DplcRun(0, 1, 0)))),
                Map.of("idle", new PlayableSheetReader.Animation(List.of(
                        new PlayableSheetReader.AnimationStep(0, 1, true),
                        new PlayableSheetReader.AnimationStep(0, 1, false)))), Map.of());

        assertThrows(java.io.IOException.class,
                () -> PlayableSheetMaterializer.read(PlayableSheetWriter.write(sheet)));
    }

    @Test
    void longAnimationDurationsPreserveExactTimingWithoutFlagByteDelays() throws Exception {
        var sheet = new PlayableSheetReader.PlayableSheet(base(),
                new PlayableSheetReader.Meta(0, 1, 0),
                List.of(new PlayableSheetReader.Frame(0, 0, 16, 16,
                        List.of(new PlayableSheetReader.DplcRun(0, 1, 0)))),
                new java.util.TreeMap<>(Map.of(
                        "duration129", new PlayableSheetReader.Animation(List.of(
                                new PlayableSheetReader.AnimationStep(0, 129, true))),
                        "duration300", new PlayableSheetReader.Animation(List.of(
                                new PlayableSheetReader.AnimationStep(0, 300, true))),
                        "large_divisor", new PlayableSheetReader.Animation(List.of(
                                new PlayableSheetReader.AnimationStep(0, 65535, false),
                                new PlayableSheetReader.AnimationStep(0, 65535, true))),
                        "mixed_hold", new PlayableSheetReader.Animation(List.of(
                                new PlayableSheetReader.AnimationStep(0, 129, false),
                                new PlayableSheetReader.AnimationStep(0, 300, false))))), Map.of());

        var animations = PlayableSheetMaterializer.read(PlayableSheetWriter.write(sheet))
                .art().animationSet();

        assertExactOrdinaryTiming(animations.getScript(0), 129);
        assertExactOrdinaryTiming(animations.getScript(1), 300);
        assertExactOrdinaryTiming(animations.getScript(2), 131070);
        assertExactOrdinaryTiming(animations.getScript(3), 429);
        assertEquals(com.openggf.sprites.animation.SpriteAnimationEndAction.HOLD,
                animations.getScript(3).endAction());
    }

    @Test
    void dynamicBankHonorsMultipleNoncontiguousDestinationOffsets() {
        com.openggf.level.Pattern first = new com.openggf.level.Pattern();
        com.openggf.level.Pattern second = new com.openggf.level.Pattern();
        first.setPixel(0, 0, (byte) 3);
        second.setPixel(0, 0, (byte) 7);
        var bank = new com.openggf.level.render.DynamicPatternBank(0, 5);

        bank.applyRequests(List.of(
                new com.openggf.level.render.TileLoadRequest(0, 1, 1),
                new com.openggf.level.render.TileLoadRequest(1, 1, 3)),
                new com.openggf.level.Pattern[]{first, second});

        assertEquals(0, bank.getPatterns()[0].getPixel(0, 0));
        assertEquals(3, bank.getPatterns()[1].getPixel(0, 0));
        assertEquals(0, bank.getPatterns()[2].getPixel(0, 0));
        assertEquals(7, bank.getPatterns()[3].getPixel(0, 0));
    }

    private static void assertExactOrdinaryTiming(
            com.openggf.sprites.animation.SpriteAnimationScript script, int duration) {
        assertTrue(script.delay() >= 0 && script.delay() < 0x80);
        assertEquals(duration, (script.delay() + 1) * script.frames().size());
    }

    private static byte[] base() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes("GGFS"); out.writeShort(1); out.writeInt(1); out.write(new byte[32]);
            out.writeShort(1); out.writeShort(5); out.writeShort(0);
            out.writeByte(1); out.writeByte(0);
            for (int i = 0; i < 16; i++) out.writeShort(i == 1 ? 0x0EEE : 0);
        }
        return bytes.toByteArray();
    }
}
