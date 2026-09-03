package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.control.InputHandler;
import com.openggf.game.GameModule;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic2.timing.Sonic2LevelMusicScheduler;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class TestGameLoopTitleCardLevelLoadVBlank {
    @Test
    void presentedPrePlayerTitleCardRowsServiceLevelLoadWorkOnceEach() {
        AtomicInteger serviced = new AtomicInteger();
        List<Integer> published = new ArrayList<>();
        Sonic2LevelMusicScheduler scheduler = new Sonic2LevelMusicScheduler();
        scheduler.arm(0x81, 11);
        LevelInitProfile profile = mock(LevelInitProfile.class);
        doAnswer(ignored -> {
            serviced.incrementAndGet();
            scheduler.serviceVBlank().ifPresent(published::add);
            return null;
        })
                .when(profile).serviceLevelLoadVBlank();
        GameModule module = mock(GameModule.class);
        when(module.getLevelInitProfile()).thenReturn(profile);
        WorldSession world = mock(WorldSession.class);
        when(world.getGameModule()).thenReturn(module);
        GameplayModeContext gameplay = mock(GameplayModeContext.class);
        when(gameplay.getWorldSession()).thenReturn(world);
        TitleCardProvider title = mock(TitleCardProvider.class);
        when(title.shouldReleaseControl()).thenReturn(false);
        when(title.shouldAdvanceVblankClockDuringLockedPhase()).thenReturn(false);
        when(title.shouldRunPlayerPhysics()).thenReturn(false);
        when(title.shouldRunLevelObjectsDuringLockedPhase()).thenReturn(false);

        for (int row = 0; row < 10; row++) {
            runPresentedTitleCardVBlank(title, gameplay);
            assertEquals(List.of(), published);
        }
        runPresentedTitleCardVBlank(title, gameplay);

        assertEquals(11, serviced.get());
        assertEquals(List.of(0x81), published,
                "normal title-card VBlanks release once, in request order");
    }

    private static void runPresentedTitleCardVBlank(
            TitleCardProvider title, GameplayModeContext gameplay) {
        GameLoopTitleCardLifecycle.update(true, title, mock(PlcLifecycleFrame.class),
                gameplay, mock(LevelManager.class), mock(SpriteManager.class),
                mock(Camera.class), mock(InputHandler.class), () -> { },
                mock(PostTitleCardDestination.class), () -> { }, ignored -> { },
                () -> { }, () -> { }, ignored -> { }, LevelFrameStep.DIRECT_WRAPPER);
    }
}
