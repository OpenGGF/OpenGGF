package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageResultsScreen;
import com.openggf.game.GameStateManager;
import com.openggf.game.SpecialStageViewport;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class TestSonic2SpecialStageResultsWidescreenCommands {
    @Test
    void resultsKeepIdentityAndTranslateTheNativeBlock() throws Exception {
        List<Command> nativeCommands = render(320);
        for (int width : new int[] {320, 400, 528}) {
            List<Command> wideCommands = render(width);
            int offset = (width - 320) / 2;
            assertEquals(nativeCommands.size(), wideCommands.size());
            for (int i = 0; i < nativeCommands.size(); i++) {
                Command expected = nativeCommands.get(i);
                Command actual = wideCommands.get(i);
                assertEquals(expected.patternId(), actual.patternId());
                assertEquals(expected.descriptor(), actual.descriptor());
                assertEquals(expected.x() + offset, actual.x());
                assertEquals(expected.y(), actual.y());
            }
        }
    }

    @Test
    void twoPlayerResultsEmitMilesRingsRowButOnePlayerLayoutDoesNot() throws Exception {
        List<Command> onePlayer = render(320, 0);
        List<Command> twoPlayer = render(320, 9);

        assertTrue(twoPlayer.size() > onePlayer.size(), "P2 must add a distinct result row");
        assertTrue(twoPlayer.stream().anyMatch(command -> command.y() == 168),
                "P2 Miles Rings row must use the ROM two-player Y position");
        assertFalse(onePlayer.stream().anyMatch(command -> command.y() == 168),
                "single-player layout must retain its existing three-line layout");
    }

    @Test
    void ringCountsUseTheNativeFourDigitColumnsWhileScoreKeepsItsFixedZero() throws Exception {
        List<Command> commands = render(320, 0);
        var sourceBase = Sonic2SpecialStageResultsScreen.class.getDeclaredField("SOURCE_DIGITS_PATTERN_BASE");
        sourceBase.setAccessible(true);
        int base = sourceBase.getInt(null);
        List<Command> ringDigits = commands.stream()
                .filter(command -> command.y() == 152 && command.patternId() >= base)
                .toList();
        assertEquals(2, ringDigits.size(), "17 rings displays two raw digits");
        // The card centres differ by $10 during slide-in. Ring count's two digits
        // occupy offsets $50,$58, matching Obj6F_024C's four-column block at $40.
        assertEquals(8, ringDigits.get(1).x() - ringDigits.get(0).x());
        assertEquals(160 + 544 + 0x50, ringDigits.get(0).x());
        assertEquals(160 + 544 + 0x58, ringDigits.get(1).x());
        assertEquals(base + 2, ringDigits.get(0).patternId());
        assertEquals(base + 14, ringDigits.get(1).patternId());
        assertFalse(commands.stream().anyMatch(command -> command.y() == 136 && command.patternId() >= base),
                "zero score leaves all variable digits blank and relies on the fixed HUD zero");
        assertTrue(commands.stream().anyMatch(command -> command.y() == 136
                        && command.patternId() == privateInt("PATTERN_BASE") + 0x6F0 - 2),
                "SCORE's mapping still submits the fixed zero pair");
    }

    private static int privateInt(String name) {
        try {
            Field field = Sonic2SpecialStageResultsScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static List<Command> render(int width) throws Exception {
        return render(width, 9);
    }

    private static List<Command> render(int width, int p2) throws Exception {
        RecordingGraphics graphics = new RecordingGraphics();
        TestObjectServices services = new TestObjectServices()
                .withGraphicsManager(graphics).withGameState(mock(GameStateManager.class));
        Sonic2SpecialStageResultsScreen results =
                new Sonic2SpecialStageResultsScreen(17, p2, false, 2, 0, services);
        setField(results, "artLoaded", true);
        setField(results, "artCached", true);
        Pattern[] patterns = new Pattern[0x0710 - 0x0002];
        for (int i = 0; i < patterns.length; i++) patterns[i] = new Pattern();
        setField(results, "combinedPatterns", patterns);
        setField(results, "sourceDigitPatterns", new Pattern[20]);
        results.setViewportWidth(width);
        results.appendRenderCommands(new ArrayList<>());
        return List.copyOf(graphics.commands);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Command(int patternId, int descriptor, int x, int y) {}

    private static final class RecordingGraphics extends GraphicsManager {
        private final List<Command> commands = new ArrayList<>();
        @Override public void cachePatternTexture(Pattern pattern, int patternId) {}
        @Override public void cachePaletteTexture(com.openggf.level.Palette palette, int index) {}
        @Override public void renderPatternWithId(int id, PatternDesc desc, int x, int y) {
            commands.add(new Command(id, desc.get(), x, y));
        }
        @Override public void beginPatternBatch() {}
        @Override public void flushPatternBatch() {}
    }
}
