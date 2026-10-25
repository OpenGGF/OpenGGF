package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.PlayableEntity;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.SonicGame;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** Bounded native request/fade/load/title/first-input proof, with no trace hydration. */
final class RootTitleCardPilot {
    private RootTitleCardPilot() { }
    static void run(SonicGame game, boolean wideTeam) throws Exception {
        String property = game == SonicGame.SONIC_1 ? "sonic1.rom.path" : "sonic2.rom.path";
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(
                wideTeam ? 400 : 320, "sonic", wideTeam ? "tails" : "", "off", null, null, null);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.boot(java.nio.file.Path.of(System.getProperty(property)), 0, 0, settings);
            var config = SonicConfigurationService.getInstance();
            var context = SessionManager.getCurrentGameplayMode();
            var level = context.getLevelManager();
            var loop = session.loop(); var input = loop.getInputHandler();
            loop.changeGameModeWithoutRewindBoundary(GameMode.LEVEL);
            var nativeTitle = context.getWorldSession().getGameModule().getTitleCardProvider();
            assertSame(nativeTitle, loop.getTitleCardProvider());
            assertEquals(wideTeam ? 2 : 1, context.getSpriteManager().getAllSprites().stream()
                    .filter(AbstractPlayableSprite.class::isInstance).count());
            var passes = new ArrayList<Pass>();
            boolean[] titleSeen = {false};
            loop.setGameModeChangeListener((old, now) -> {
                if (now == GameMode.TITLE_CARD) {
                    titleSeen[0] = true;
                    assertEquals(1, level.getCurrentAct());
                    assertNotNull(level.getObjectManager().createDynamicObjectAtSlot(
                            () -> new Observer(passes, loop), 90));
                    context.getSpriteManager().getAllSprites().stream().filter(AbstractPlayableSprite.class::isInstance)
                            .map(AbstractPlayableSprite.class::cast).forEach(p -> assertTrue(p.isControlLocked()));
                }
            });
            level.requestZoneAndAct(0, 1, true);
            tick(session);
            assertEquals(0, level.getCurrentAct(), "request consumption starts the native fade before load");
            assertEquals(FadeManager.FadeState.FADING_TO_BLACK, context.getFadeManager().getState());
            boolean loaded = false;
            for (int frame = 0; frame < 600 && (!titleSeen[0] || loop.getCurrentGameMode() == GameMode.TITLE_CARD); frame++) {
                tick(session);
                loaded |= level.getCurrentAct() == 1;
            }
            assertTrue(loaded, "destination loaded"); assertTrue(titleSeen[0], "native title was presented"); assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());
            assertSame(nativeTitle, loop.getTitleCardProvider());
            // Current S2 locked physics uses LevelFrameStep (26 RunObjects passes);
            // the release resets the sprite clock alone. S1 has one uncounted prelude.
            int titleCounter = game == SonicGame.SONIC_1 ? 0 : 26;
            assertEquals(titleCounter, level.getFrameCounter(), "existing locked-pass counter ownership");
            assertEquals(0, context.getSpriteManager().getFrameCounter(), "release resets the player clock after locked passes");
            assertEquals(LevelFrameResult.SETUP_ONLY, TitleCardLifecycleTestAccess.result(loop));
            assertFalse(passes.isEmpty(), "native title/prelude executes real object scans");
            assertTrue(passes.stream().allMatch(p -> p.mode == GameMode.TITLE_CARD),
                    "release adds no ordinary destination pass");
            int before = passes.size();
            assertEquals(game == SonicGame.SONIC_1 ? 1 : 26, before, "native prelude/locked pass count");
            for (int i = 0; i < before; i++) assertEquals(game == SonicGame.SONIC_1 ? 0 : i + 1,
                    passes.get(i).levelCounter, "title object pass counter at " + i);
            int right = config.getInt(SonicConfiguration.RIGHT);
            input.handleKeyEvent(right, GLFW_PRESS);
            tick(session);
            assertEquals(before + 1, passes.size(), "first controller input gets exactly one ordinary object pass");
            var first = passes.getLast(); assertEquals(GameMode.LEVEL, first.mode);
            assertEquals(titleCounter + 1, first.levelCounter); assertEquals(1, first.spriteCounter);
            assertTrue((first.input & AbstractPlayableSprite.INPUT_RIGHT) != 0, "the first destination scan observes right input");
            assertFalse(session.player().isControlLocked());
            input.handleKeyEvent(right, GLFW_RELEASE); tick(session);
            assertEquals(before + 2, passes.size()); assertEquals(titleCounter + 2, level.getFrameCounter());
        }
    }
    private static void tick(com.openggf.tools.GameplayCaptureSession session) {
        session.loop().step(); session.renderFrame();
    }
    private record Pass(GameMode mode, int levelCounter, int spriteCounter, int input) { }
    private static final class Observer extends AbstractObjectInstance {
        private final List<Pass> passes;
        private final GameLoop loop;
        Observer(List<Pass> passes, GameLoop loop) {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "RootTitlePilot");
            this.passes = passes; this.loop = loop; setRomWorldPositioned(false);
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            var context = SessionManager.getCurrentGameplayMode();
            passes.add(new Pass(loop.getCurrentGameMode(), context.getLevelManager().getFrameCounter(),
                    context.getSpriteManager().getFrameCounter(), ((AbstractPlayableSprite)player).getLogicalInputState()));
        }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }
}
