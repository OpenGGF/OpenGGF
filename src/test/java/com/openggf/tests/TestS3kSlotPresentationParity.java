package com.openggf.tests;

import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Level;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slot Machine presentation phase and content against the ROM's frame order, from the
 * 2026-10-15 native-still spot check (bonus_slots rows 300-1036).
 *
 * <p>LevelLoop (sonic3k.asm:7911-7915) runs {@code Render_Sprites} and then
 * {@code Slots_RenderLayout}, which appends the rotated layout pieces to the same
 * {@code Sprite_table} after every Render_Sprites entry (sonic3k.asm:98158-98269,
 * {@code Sprites_drawn}). The following VBlank DMAs that table, so the layout is displayed
 * with the player and objects of its own iteration and behind all of them.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSlotPresentationParity {

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void layoutIsPublishedWithTheSpriteTableOfItsIterationBehindRenderSprites() {
        HeadlessTestFixture fixture = enterSlots();
        S3kSlotBonusStageRuntime runtime = activeRuntime();
        var graphics = GameServices.graphics();
        var camera = GameServices.camera();

        int compared = 0;
        boolean geometryChanged = false;
        List<Key> previousLayout = null;
        for (int frame = 0; frame < 48; frame++) {
            fixture.stepIdleFrames(1);
            List<Key> published = keys(LevelSpritePresentation.published(GameServices.level()).tiles());
            if (previousLayout != null && !previousLayout.isEmpty()) {
                // The table published by this frame's VBlank was produced by the previous
                // iteration: its Render_Sprites entries followed by that iteration's layout.
                assertTrue(published.size() >= previousLayout.size(),
                        "published table holds the previous iteration's layout at frame " + frame);
                assertEquals(previousLayout, published.subList(0, previousLayout.size()),
                        "previous iteration's layout is published first (painter order: behind"
                                + " every Render_Sprites entry) at frame " + frame);
                compared++;
            }
            List<Key> layout = keys(SpritePresentationRenderer.prepare(graphics,
                    camera.getXWithShake(), camera.getYWithShake(),
                    () -> runtime.renderSlotLayout(camera)).tiles());
            if (previousLayout != null && !layout.equals(previousLayout)) {
                geometryChanged = true;
            }
            previousLayout = layout;
        }
        assertTrue(compared > 40, "layout pieces were visible and compared");
        assertTrue(geometryChanged, "the stage rotated during the comparison, so latency is observable");

        // The live draw pass must not draw this iteration's layout on top of the published table.
        var provider = GameServices.level().getZoneFeatureProvider();
        assertNotNull(provider);
        var live = SpritePresentationRenderer.prepare(graphics, camera.getXWithShake(), camera.getYWithShake(),
                () -> provider.render(camera, 0));
        assertTrue(live.tiles().isEmpty(), "zone feature render draws no unlatched layout pieces");
    }

    @Test
    void anPalSlotsSeesTheCageModeOnTheFollowingIteration() {
        HeadlessTestFixture fixture = enterSlots();
        S3kSlotBonusStageRuntime runtime = activeRuntime();
        fixture.stepIdleFrames(2);
        assertEquals(0, runtime.paletteCycleMode());
        // The cage writes Palette_cycle_counters+0 during Process_Sprites; LevelLoop's
        // Animate_Palette (sonic3k.asm:7891) already ran for that iteration.
        runtime.setPaletteCycleEnabledForTest(true);
        assertEquals(0, runtime.paletteCycleMode(), "AnPal_Slots of the writing iteration keeps the old mode");
        fixture.stepIdleFrames(1);
        assertEquals(1, runtime.paletteCycleMode(), "the next Animate_Palette sees the new mode");
    }

    @Test
    void goalIterationRunsRoutineTwoAndTheWindDownStartsNextIteration() {
        enterSlots();
        S3kSlotBonusStageRuntime runtime = activeRuntime();
        var controller = runtime.stageController();
        int scalar = controller.scalarIndex();
        // sub_4BE3A dispatches the goal tile ($30(a0) = 4) inside this iteration's movement:
        // loc_4BED0 only advances routine(a0) (sonic3k.asm:99247-99253); loc_4BC1E's first
        // +$40 belongs to the next iteration.
        controller.setLastCollision(4, 0);
        runtime.runPreMovePlayerInteractionsForTest();
        assertNotNull(runtime.activeExitSequenceForTest(), "goal touched");
        runtime.update(1000);
        assertEquals(scalar, controller.scalarIndex(), "no wind-down in the goal iteration");
        runtime.update(1001);
        assertEquals(scalar + 0x40, controller.scalarIndex(), "first loc_4BC1E iteration");
    }

    @Test
    void bonusHudKeepsTheLivesIconRow() {
        HeadlessTestFixture fixture = enterSlots();
        fixture.stepIdleFrames(2);
        var tiles = LevelSpritePresentation.published(GameServices.level()).tiles();
        // Render_HUD (sonic3k.asm:17580-17589) selects Map_HUD frame 4/5 for zones $13-$15
        // (word_DC96 / word_DCB6): RINGS plus the two y=+$40 lives pieces, which land at
        // screen Y $108+$40-$80 = 200 like the ordinary HUD's lives row.
        boolean rings = false, lives = false;
        for (var tile : tiles) {
            if (!tile.layer().isHud()) {
                continue;
            }
            rings |= tile.y() >= 8 && tile.y() < 24;
            lives |= tile.y() >= 200 && tile.y() < 216;
        }
        assertTrue(rings, "bonus HUD shows RINGS");
        assertTrue(lives, "bonus HUD shows the lives icon row");
    }

    @Test
    void goalFadeRunsPalToBlackEveryThirdLoc4BC54IterationAndLeavesTheRestToLevel() {
        HeadlessTestFixture fixture = enterSlots();
        S3kSlotBonusStageRuntime runtime = activeRuntime();
        fixture.stepIdleFrames(4);
        runtime.startGoalExitForTest();

        // loc_4BC1E (sonic3k.asm:98964-98979): +$40 per iteration until $1800, then
        // Palette_fade_info=$003F, Pal_fade_delay=0, $38=60, Palette_cycle_counters+0=$FF.
        int guard = 0;
        while (!runtime.activeExitSequenceForTest().isFading()) {
            fixture.stepIdleFrames(1);
            assertTrue(++guard < 200, "goal wind-down reaches $1800");
        }
        Level level = GameServices.level().getCurrentLevel();
        int[][] start = snapshot(level);
        int calls = 0;
        int delay = 0;
        for (int iteration = 1; iteration <= 60; iteration++) {
            fixture.stepIdleFrames(1);
            // loc_4BC54 (sonic3k.asm:98981-98987): subq #1,Pal_fade_delay / bpl / #2 / Pal_ToBlack.
            if (--delay < 0) {
                delay = 2;
                calls++;
            }
            int[][] actual = snapshot(level);
            for (int line = 0; line < 4; line++) {
                for (int color = 0; color < 16; color++) {
                    assertEquals(palToBlack(start[line][color], calls), actual[line][color],
                            "line " + line + " colour " + color + " after loc_4BC54 iteration " + iteration);
                }
            }
        }
        assertEquals(20, calls);
        assertTrue(runtime.isExitTriggered(), "$38 reached zero: Restart_level_flag");
        assertFalse(runtime.activeExitSequenceForTest().isFading());
        BonusStageProvider provider = GameServices.module().getBonusStageProvider();
        assertFalse(provider.hasCompletedExitFadeToBlack(),
                "Level: still runs Pal_FadeToBlack (sonic3k.asm:7504, 7523, 5042-5051) after the 20 goal passes");
    }

    @Test
    void goalWindDownKeepsTheCageOrbitingThePlayerForTheMachinePlane() {
        HeadlessTestFixture fixture = enterSlots();
        S3kSlotBonusStageRuntime runtime = activeRuntime();
        fixture.stepIdleFrames(4);
        // A goal far from the stage centre, as in the 2026-10-15 native still (player $572,$338).
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        player.setCentreX((short) 0x572);
        player.setCentreY((short) 0x338);
        runtime.slotPlayerRuntimeForTest().resetSlotOrigin(player);
        runtime.startGoalExitForTest();
        var controller = runtime.stageController();
        var playerRuntime = runtime.slotPlayerRuntimeForTest();
        for (int iteration = 1; iteration <= 40; iteration++) {
            fixture.stepIdleFrames(1);
            // Obj_SlotBonus loc_4BF9A (sonic3k.asm:99324-99348) runs in every Process_Sprites
            // pass after the player's loc_4BC1E rotation step: it rotates ($460,$430) about
            // Player_1 by Stat_table&$FC and writes Events_bg+$00/$02, which Slots_ScreenEvent
            // (sonic3k.asm:119061-119133) follows with the machine plane.
            int angle = controller.angle() & 0xFC;
            int px = playerRuntime.slotOriginX() >> 16;
            int py = playerRuntime.slotOriginY() >> 16;
            int dx = 0x460 - px;
            int dy = 0x430 - py;
            int sin = com.openggf.physics.TrigLookupTable.sinHex(angle);
            int cos = com.openggf.physics.TrigLookupTable.cosHex(angle);
            var visual = runtime.slotVisualState();
            assertEquals((short) ((((dx * cos) - (dy * sin)) >> 8) + px), (short) visual.eventsBgX(),
                    "Events_bg+$00 at wind-down iteration " + iteration);
            assertEquals((short) ((((dx * sin) + (dy * cos)) >> 8) + py), (short) visual.eventsBgY(),
                    "Events_bg+$02 at wind-down iteration " + iteration);
        }
    }

    private static int[][] snapshot(Level level) {
        int[][] words = new int[4][16];
        for (int line = 0; line < 4; line++) {
            for (int color = 0; color < 16; color++) {
                words[line][color] = PaletteWriteSupport.segaWordFromColor(level.getPalette(line).getColor(color));
            }
        }
        return words;
    }

    /** Pal_DecColor (sonic3k.asm:5085-5113): red, then green, then blue, one 2-step per call. */
    private static int palToBlack(int word, int calls) {
        for (int i = 0; i < calls; i++) {
            if ((word & 0x00E) != 0) word -= 0x002;
            else if ((word & 0x0E0) != 0) word -= 0x020;
            else if ((word & 0xE00) != 0) word -= 0x200;
        }
        return word;
    }

    private record Key(int patternId, int palette, boolean hFlip, boolean vFlip, boolean priority, float x, float y) { }

    private static List<Key> keys(List<SpritePresentation.Tile> tiles) {
        List<Key> keys = new ArrayList<>();
        for (var tile : tiles) {
            if (tile.layer().isHud()) {
                continue;
            }
            keys.add(new Key(tile.patternId(), tile.palette(), tile.hFlip(), tile.vFlip(), tile.priority(),
                    tile.x(), tile.y()));
        }
        return keys;
    }

    private static S3kSlotBonusStageRuntime activeRuntime() {
        var provider = (Sonic3kBonusStageCoordinator) GameServices.module().getBonusStageProvider();
        S3kSlotBonusStageRuntime runtime = provider.activeSlotRuntime();
        assertNotNull(runtime);
        assertTrue(runtime.isInitialized());
        return runtime;
    }

    /** {@link TestS3kBonusStageHeadlessBoot}'s production-ordered bonus entry. */
    private static HeadlessTestFixture enterSlots() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SLOT_MACHINE, 0)
                .build();
        BonusStageProvider provider = GameServices.module().getBonusStageProvider();
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        gameplayMode.setActiveBonusStageProvider(provider);
        provider.onEnter(BonusStageType.SLOT_MACHINE, new BonusStageState(
                0, 0, 25, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                (byte) 0x0C, (byte) 0x0D, 0, 0L));
        gameplayMode.registerBonusStageAdapter(provider);
        GameServices.level().getLevelGamestate().setRings(25);
        GameServices.level().setBonusStageHudLayout(true);
        for (var sprite : GameServices.sprites().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable) {
                playable.setHidden(false);
                playable.setObjectControlled(false);
            }
        }
        provider.onDeferredSetupComplete();
        return fixture;
    }
}
