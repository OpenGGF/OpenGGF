package com.openggf.game.soniccd;

import com.openggf.game.GameServices;

/**
 * The one owner of what the level load runs in place of the ROM's level start and first main-loop
 * iteration. The loaded state stands for {@code levelFrames} 1 ({@code LoadedLevelFrameCounter}), so every
 * presentation owner must have run exactly as often as native's has when iteration 1 ends. Native order
 * (USA, scddisasm pin {@code 1ecc5f3a}; {@code Level/Main.asm} assembles Palmtree Panic and Collision Chaos,
 * {@code r4/main.asm} Tidal Tempest, {@code r5/main.asm} Quartz Quadrant, {@code common/main.asm} Wacky
 * Workbench, Stardust Speedway and Metallic Madness, the same sequence in each):
 * <ol>
 *   <li>{@code LevelStart}: {@code bsr.w LevelScroll} once after {@code LevelSizeLoad} (Level/Main.asm:245-246;
 *       r4/main.asm:219; r5/main.asm:208; common/main.asm:248), then {@code jsr UpdateAnimTiles}
 *       ({@code AnimateStageGfx}) once (Level/Main.asm:312; r4/main.asm:280; r5/main.asm:269;
 *       common/main.asm:309). The palette fade's VSyncs take {@code VInt_PalFade}, which runs neither
 *       (Palette Fade.asm:25-30; Level/Main.asm:894-906).</li>
 *   <li>Iteration 1, {@code Level_MainLoop}: {@code vintRoutine} 8 and {@code VSync}, so {@code VInt_Level}
 *       runs {@code UpdateAnimTiles} and {@code UpdateHUD} (Level/Main.asm:333-335, 837, 847; r4/main.asm:309,
 *       1136-1138; r5/main.asm:298, 850-856; common/main.asm:329-331, 891-892); then {@code addq.w
 *       #1,levelFrames}, {@code SpawnObjects}, {@code RunObjects}, {@code LevelScroll} (unless the player is
 *       dead), {@code DrawObjects}, {@code PaletteCycle} (unless {@code timeStopTimer}), {@code
 *       UpdateSectionArt}, {@code ProcessPLCs} and the global animations, all after {@code .NotPaused}
 *       (Level/Main.asm:397-430; r4/main.asm:381-411; r5/main.asm:355-390; common/main.asm:406-452).</li>
 * </ol>
 * {@code LevelScroll} begins with {@code RunLevelEvents} (Palmtree Panic/Scroll (Act 1 Present).asm:189;
 * {@code UpdateScroll}'s {@code StageEvents} in the other dialect, e.g. r7/scroll_3c.asm:124-138), so each of
 * the load's two calls runs the level events first: the zone's routine and the bottom boundary's 4-pixel step
 * toward its target (Level/_Events.asm:7-53).
 * So after iteration 1 native has run {@code LevelScroll} twice, {@code PaletteCycle} once and
 * {@code AnimateStageGfx} twice. The engine's frame runs the zone scroll, the tile animator and the palette
 * cycle once each after the object pass. Its tile update stands for the next frame's {@code VInt_Level}
 * call: that call reads only the animator's own timers (and the boss and time-stop flags the previous
 * iteration left), runs before the next iteration's objects, and is on screen with this iteration's sprite
 * table and palette (both DMA'd by the same V-INT, Level/Main.asm:829-831). The load therefore also runs
 * iteration 2's V-INT call: three tile updates in all, so that iteration n's objects read
 * {@code AnimateStageGfx} after n+1 calls, as native's do.
 *
 * <p>{@code UpdateGlobalAnims} keeps no engine state (the ring frame is derived from the object pass counter,
 * {@code CdGlobalAnimations}); {@code UpdateSectionArt} and the PLC queue are owned by the objects that
 * read them. The zone scroll, palette cycle and tile animator are each zone's fragment hooks
 * ({@code CdZoneFragment}); this class names no zone.
 */
final class CdFirstMainLoopIteration {
    private CdFirstMainLoopIteration() { }

    /** {@code LevelStart}'s scroll and tile calls, iteration 1's V-INT, its object pass and its presentation. */
    static void run() {
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var camera = GameServices.camera();
        if (objects == null || camera == null) return;
        levelScroll(0);                                  // LevelStart: bsr.w LevelScroll (Level/Main.asm:246)
        animateTiles();                                  // the level start's tile call (Level/Main.asm:312)
        animateTiles();                                  // iteration 1's VInt_Level (Level/Main.asm:837)
        // The same V-INT moves the water in the zones that have it: VBlankStage's jsr UpdateWater
        // (r4/main.asm:1078-1082), so native water_angle is 2 on row 0 of every Tidal Tempest stage (LAG lane
        // probe). A no-op without water; the engine's frame moves it once per later V-INT.
        level.advanceDynamicWaterLevel();
        var player = camera.getFocusedSprite();
        // VInt_Level's UpdateHUD ticks the clock unless the title card's ctrlLocked holds it
        // (Level/Main.asm:847; HUD and Points/Main.asm:255-267): a warp arrival or a checkpoint restart has no
        // card, so the clock moves on this first frame.
        if (level.getLevelGamestate() instanceof com.openggf.game.LevelGamestate clock) clock.update();
        // SpawnObjects and RunObjects: every object's routine 0 (spawned pieces included) in the same pass as
        // ObjSonic_Init, so the loaded state is levelFrames 1 for objects as for Sonic (Level/Main.asm:400-403).
        com.openggf.game.soniccd.objects.common.CdFixedObjects.run(objects, camera.getX(), player);
        objects.update(camera.getX(), player, java.util.List.of(), 0);
        levelScroll(1);                                  // iteration 1: bsr.w LevelScroll (Level/Main.asm:418)
        var palette = level.getAnimatedPaletteManager();
        if (palette != null && palette != level.getAnimatedPatternManager()) {
            palette.update();                            // iteration 1: bsr.w PaletteCycle (Level/Main.asm:423-425)
        }
        animateTiles();                                  // iteration 2's VInt_Level, as the engine's frame runs it
    }

    /**
     * One {@code LevelScroll}: {@code RunLevelEvents}, then the stage scroll through the shared scroll owner (the
     * first call builds the stage's handler and runs its {@code InitLevelScroll}). {@code frame} names the native iteration (0 for the level start), so each call
     * is a distinct update for a handler that runs once per frame; the engine's first frame is 2.
     */
    private static void levelScroll(int frame) {
        CdLevelEventProvider.runLevelEvents();           // LevelScroll: bsr.w RunLevelEvents first
        var level = GameServices.level();
        var parallax = GameServices.parallaxOrNull();
        var camera = GameServices.camera();
        if (parallax == null || camera == null) return;
        parallax.update(level.getCurrentZone(), level.getCurrentAct(), camera, frame, level.getCurrentLevel());
    }

    private static void animateTiles() {
        var tiles = GameServices.level().getAnimatedPatternManager();
        if (tiles != null) tiles.update();
    }
}
