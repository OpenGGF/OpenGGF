package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageState;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Level;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slot Machine presentation phase and content against the ROM's frame order, from the
 * 2026-10-15 native-still spot check (bonus_slots rows 300-1036).
 *
 * <p>LevelLoop (sonic3k.asm:7943-7947) runs {@code Render_Sprites} and then
 * {@code Slots_RenderLayout}, which appends the rotated layout pieces to the same
 * {@code Sprite_table} after every Render_Sprites entry (sonic3k.asm:98204-98315,
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
    void layoutIsPublishedWithTheSpriteTableOfItsIterationBehindRenderSprites() throws ReflectiveOperationException {
        HeadlessTestFixture fixture = enterSlots();
        activeRuntime();
        // The first call also drains the real SETUP_ONLY pass. Prime one ordinary
        // Slot table before the 48 sampled iterations; bootstrap latency is separate.
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false),
                "prime initial setup and the first ordinary Slot table");
        var level = GameServices.level();
        var graphics = level.getObjectManager().getObjectServices().graphicsManager();
        var camera = GameServices.camera();
        assertSame(graphics, GameServices.graphics(), "loaded level and fixture share the graphics owner");
        var renderManager = level.getObjectRenderManager();
        var layoutFrames = Map.of(
                Sonic3kObjectArtKeys.SLOT_COLORED_WALL, 16,
                Sonic3kObjectArtKeys.SLOT_GOAL, 3,
                Sonic3kObjectArtKeys.SLOT_BUMPER, 3,
                Sonic3kObjectArtKeys.SLOT_R_LABEL, 1,
                Sonic3kObjectArtKeys.SLOT_PEPPERMINT, 4,
                Sonic3kObjectArtKeys.SLOT_RING_STAGE, 8);
        Set<Integer> layoutPatterns = new HashSet<>();
        for (var entry : layoutFrames.entrySet()) {
            addMappingPatterns(layoutPatterns, renderManager, entry.getKey(), entry.getValue());
            assertSame(graphics, field(renderManager.getRenderer(entry.getKey()), "graphicsManager"),
                    "the collection flag must come from each layout renderer's actual graphics owner");
        }
        Set<Integer> otherObjectPatterns = new HashSet<>();
        for (String key : renderManager.getArtProvider().getRendererKeys()) {
            if (!layoutFrames.containsKey(key)) {
                var sheet = renderManager.getSheet(key);
                assertNotNull(sheet, "registered object renderer has a ROM sheet: " + key);
                addMappingPatterns(otherObjectPatterns, renderManager, key, sheet.getFrameCount());
            }
        }
        assertFalse(layoutPatterns.isEmpty(), "actual emitted layout mappings own patterns");
        assertTrue(Collections.disjoint(layoutPatterns, otherObjectPatterns),
                "layout and nonlayout ROM mapping ownership must be unambiguous");

        int compared = 0;
        boolean geometryChanged = false;
        List<LayoutGeometry> previousGeometry = null;
        for (int frame = 0; frame < 48; frame++) {
            // Retain the actual immutable production result BEFORE ordinary VBlank.
            // No standalone layout render can reproduce native SAT admission.
            SpritePresentation.Frame precedingPrepared = preparedFrame();
            fixture.stepIdleFrames(1);
            SpritePresentation.Frame published = LevelSpritePresentation.published(level);
            assertSame(precedingPrepared, published,
                    "exact preceding CPU table reaches the next VBlank at frame " + frame);
            assertEquals(precedingPrepared, published,
                    "all admitted tiles, attributes, primitives and pattern versions at frame " + frame);
            if (!published.tiles().isEmpty()) {
                List<LayoutGeometry> geometry = layoutPrefixGeometry(published, layoutPatterns, otherObjectPatterns);
                if (previousGeometry != null && !geometry.equals(previousGeometry)) {
                    geometryChanged = true;
                }
                previousGeometry = geometry;
                compared++;
            }
        }
        assertTrue(compared > 40, "nonempty production tables with layout and Render_Sprites witnesses compared");
        assertTrue(geometryChanged, "layout geometry changed, so one-VBlank latency is observable");

        // The live draw pass must not draw this iteration's layout on top of the published table.
        var provider = level.getZoneFeatureProvider();
        assertNotNull(provider);
        var live = SpritePresentationRenderer.prepare(graphics, camera.getXWithShake(), camera.getYWithShake(),
                () -> provider.render(camera, 0));
        assertTrue(live.tiles().isEmpty(), "zone feature render draws no unlatched layout pieces");
    }

    private static SpritePresentation.Frame preparedFrame() throws ReflectiveOperationException {
        return (SpritePresentation.Frame) field(field(field(GameServices.level(), "levelRenderer"),
                "spriteTables"), "prepared");
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void addMappingPatterns(Set<Integer> patterns, ObjectRenderManager renderManager,
                                           String key, int frameCount) {
        var renderer = renderManager.getRenderer(key);
        var sheet = renderManager.getSheet(key);
        assertNotNull(renderer, "actual ROM renderer: " + key);
        assertNotNull(sheet, "actual ROM sheet: " + key);
        int base = renderer.getPatternBase();
        assertTrue(base >= 0, "actual cached ROM pattern base: " + key);
        assertTrue(frameCount <= sheet.getFrameCount(), "declared emitted frames exist: " + key);
        for (int frame = 0; frame < frameCount; frame++) {
            for (var piece : sheet.getFrame(frame).pieces()) {
                for (int tile = 0; tile < piece.widthTiles() * piece.heightTiles(); tile++) {
                    patterns.add(base + piece.tileIndex() + tile);
                }
            }
        }
    }

    private record LayoutGeometry(float x, float y, float width, float height, int rowStart, int rowEnd) { }

    private static List<LayoutGeometry> layoutPrefixGeometry(SpritePresentation.Frame frame,
            Set<Integer> layoutPatterns, Set<Integer> otherObjectPatterns) {
        List<LayoutGeometry> geometry = new ArrayList<>();
        boolean renderSpritesSeen = false, playerSeen = false, hudSeen = false;
        for (var tile : frame.tiles()) {
            boolean layout = layoutPatterns.contains(tile.patternId());
            if (layout) {
                assertFalse(otherObjectPatterns.contains(tile.patternId()), "ambiguous production pattern ownership");
                assertEquals(SpritePresentation.Layer.OBJECT, tile.layer(), "actual layout entry layer");
                assertFalse(renderSpritesSeen, "every layout entry is behind every Render_Sprites entry in painter order");
                geometry.add(new LayoutGeometry(tile.x(), tile.y(), tile.width(), tile.height(),
                        tile.rowStart(), tile.rowEnd()));
            } else {
                renderSpritesSeen = true;
                if (tile.layer() == SpritePresentation.Layer.OBJECT) {
                    assertTrue(otherObjectPatterns.contains(tile.patternId()),
                            "nonlayout production object has independently declared ROM mapping ownership");
                }
                playerSeen |= tile.layer() == SpritePresentation.Layer.PLAYER;
                hudSeen |= tile.layer().isHud();
            }
        }
        assertFalse(geometry.isEmpty(), "admitted layout prefix is nonempty");
        assertTrue(playerSeen, "actual Render_Sprites player entries follow the layout");
        // S3kSlotBonusCageObjectInstance deliberately emits no sprites: its face
        // belongs to the machine plane. Require actual nonlayout producers here.
        assertTrue(hudSeen, "actual nonlayout Render_Sprites HUD entries follow the layout");
        return List.copyOf(geometry);
    }

    @Test
    void anPalSlotsSeesTheCageModeOnTheFollowingIteration() {
        HeadlessTestFixture fixture = enterSlots();
        S3kSlotBonusStageRuntime runtime = activeRuntime();
        fixture.stepIdleFrames(2);
        assertEquals(0, runtime.paletteCycleMode());
        // The cage writes Palette_cycle_counters+0 during Process_Sprites; LevelLoop's
        // Animate_Palette (sonic3k.asm:7923) already ran for that iteration.
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
        // loc_4BED0 only advances routine(a0) (sonic3k.asm:99293-99299); loc_4BC1E's first
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
        // Render_HUD (sonic3k.asm:17616-17625) selects Map_HUD frame 4/5 for zones $13-$15
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

        // loc_4BC1E (sonic3k.asm:99010-99025): +$40 per iteration until $1800, then
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
            // loc_4BC54 (sonic3k.asm:99027-99033): subq #1,Pal_fade_delay / bpl / #2 / Pal_ToBlack.
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
            // Obj_SlotBonus loc_4BF9A (sonic3k.asm:99370-99394) runs in every Process_Sprites
            // pass after the player's loc_4BC1E rotation step: it rotates ($460,$430) about
            // Player_1 by Stat_table&$FC and writes Events_bg+$00/$02, which Slots_ScreenEvent
            // (sonic3k.asm:119107-119179) follows with the machine plane.
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

    /** Pal_DecColor (sonic3k.asm:5117-5145): red, then green, then blue, one 2-step per call. */
    private static int palToBlack(int word, int calls) {
        for (int i = 0; i < calls; i++) {
            if ((word & 0x00E) != 0) word -= 0x002;
            else if ((word & 0x0E0) != 0) word -= 0x020;
            else if ((word & 0xE00) != 0) word -= 0x200;
        }
        return word;
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
