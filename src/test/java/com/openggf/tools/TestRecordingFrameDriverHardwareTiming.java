package com.openggf.tools;

import com.openggf.LevelFrameResult;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameModule;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TestRecordingFrameDriverHardwareTiming {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void pausedDriverRowTraversesOnlyVintService() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        List<String> events = observe(context);
        context.getGameStateManager().setGamePaused(true);
        RecordingFrameDriver driver =
                new RecordingFrameDriver(mock(AbstractPlayableSprite.class));

        LevelFrameResult result =
                driver.stepFrame(false, false, false, false, false);

        assertEquals(LevelFrameResult.PAUSED, result);
        assertEquals(List.of("VINT_SERVICE"), events);
    }

    @Test
    void setupOnlyDriverRowDoesNotInventABoundary() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        List<String> events = observe(context);
        RecordingFrameDriver driver =
                new RecordingFrameDriver(mock(AbstractPlayableSprite.class));
        LevelManager level = mock(LevelManager.class);
        when(level.consumePendingInitialProcessSpritesPass()).thenReturn(true);
        setField(driver, "levelManager", level);

        LevelFrameResult result =
                driver.stepFrame(false, false, false, false, false);

        assertEquals(LevelFrameResult.SETUP_ONLY, result);
        assertEquals(List.of(), events);
    }

    @Test
    void inputOnlyAdvanceConsumesNoProductionBoundary() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        List<String> events = observe(context);
        RecordingFrameDriver driver =
                new RecordingFrameDriver(mock(AbstractPlayableSprite.class));
        driver.setBk2Movie(oneFrameMovie(), 0);

        driver.consumeRecordingFrameInputOnly();

        assertEquals(List.of(), events);
    }

    @Test
    void heldS3kTitleCardSkipSurroundsActualProviderScanWithBoundaries()
            throws Exception {
        TitleCardProvider provider = mock(TitleCardProvider.class);
        GameModule module = spy(new Sonic3kGameModule());
        doReturn(provider).when(module).getTitleCardProvider();
        doReturn(null).when(module).getLevelEventProvider();
        TestEnvironment.configureGameModuleFixture(module);
        GameplayModeContext context = SessionManager.getCurrentGameplayMode();
        List<String> events = observe(context);
        when(provider.advancesOnHeldLevelCounter()).thenReturn(true);
        doAnswer(ignored -> {
            events.add("TITLE_CARD_SCAN");
            return null;
        }).when(provider).update();
        RecordingFrameDriver driver =
                new RecordingFrameDriver(mock(AbstractPlayableSprite.class));
        LevelManager level = mock(LevelManager.class);
        when(level.getObjectManager()).thenReturn(mock(ObjectManager.class));
        setField(driver, "levelManager", level);
        driver.setBk2Movie(oneFrameMovie(), 0);

        driver.skipFrameFromRecording();

        assertEquals(List.of(
                "VINT_SERVICE",
                "PRE_MAIN_LOOP",
                "TITLE_CARD_SCAN",
                "POST_OBJECTS"), events);
    }

    private static List<String> observe(GameplayModeContext context) {
        List<String> events = new ArrayList<>();
        context.setHardwareTimingBoundaryObserver(
                boundary -> events.add(boundary.name()));
        return events;
    }

    private static Bk2Movie oneFrameMovie() {
        return new Bk2Movie(
                Path.of("hardware-timing-test.bk2"),
                "logkey",
                Map.of(),
                List.of(new Bk2FrameInput(0, 0, 0, false, "")),
                1);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
