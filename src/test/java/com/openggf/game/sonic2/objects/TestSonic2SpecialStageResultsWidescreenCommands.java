package com.openggf.game.sonic2.objects;

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

    private static List<Command> render(int width) throws Exception {
        RecordingGraphics graphics = new RecordingGraphics();
        TestObjectServices services = new TestObjectServices()
                .withGraphicsManager(graphics).withGameState(mock(GameStateManager.class));
        SpecialStageResultsScreenObjectInstance results =
                new SpecialStageResultsScreenObjectInstance(17, 9, false, 2, 0, services);
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
