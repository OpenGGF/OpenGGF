package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.camera.DeadzoneGeometry;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.game.sonic3k.objects.SszCloudOscillatorObjectInstance;
import com.openggf.game.sonic3k.objects.SszRoamingCloudObjectInstance;
import com.openggf.game.sonic3k.objects.SszSolidCloudObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Sky Sanctuary ({@code $A00}/{@code $A01}) screen events: {@code SSZ1_ScreenInit},
 * {@code SSZ2_ScreenInit} and the bounds machine {@code sub_575EA} that {@code SSZ1_ScreenEvent}
 * stage 0 runs every frame (sonic3k.asm:115851-115910, 116198-116290, 117724).
 *
 * <p>Persistent event words live in {@link SszZoneRuntimeState} so rewind captures them; the
 * arrival objects and the four bosses read the same state.
 */
public class Sonic3kSSZEvents extends Sonic3kZoneEvents {
    /** {@code SSZ1_ScreenInit}. */
    static final int ACT1_CONTROLLER_X = 0x100;
    static final int ACT1_CONTROLLER_RISE_FRAMES = 0x6C;
    static final int ACT1_CAMERA_MAX_X = 0x200;
    static final int ACT1_CAMERA_MAX_Y = 0xBC0;
    static final int ACT1_CAMERA_X = 0x60;
    static final int ACT1_CAMERA_Y = 0xF49;
    /** {@code SSZ2_ScreenInit}. */
    static final int ACT2_CONTROLLER_X = 0xA0;
    static final int ACT2_CONTROLLER_RISE_FRAMES = 0x44;
    static final int ACT2_CAMERA_X = 0;
    static final int ACT2_CAMERA_Y = 0x649;

    /** The act's full vertical range once an event releases the bounds; also the wrap period. */
    static final int LEVEL_MIN_Y = -0x100;
    static final int LEVEL_MAX_Y = 0x1000;
    /** {@code sub_575EA}: the dynamic-bounds band is only consulted inside this camera Y range. */
    private static final int DYNAMIC_BOUNDS_MIN_Y = 0x100;
    private static final int DYNAMIC_BOUNDS_MAX_Y = 0xE00;

    /** {@code word_5778A}: player X limit, then the {@code Camera_min_Y} for everything below it. */
    private static final int[][] MIN_Y_BAND = {
            {0x0EE0, 0x0D00}, {0x11E0, 0x0CC0}, {0x1340, 0x0B20}, {0x7FFF, 0x0000}
    };
    /** {@code word_5779A}: player X limit, then the {@code Camera_max_Y}. */
    private static final int[][] MAX_Y_BAND = {
            {0x0640, 0x0C60}, {0x0880, 0x0CA0}, {0x1200, 0x0C60}, {0x1380, 0x0A80},
            {0x13C0, 0x0660}, {0x7FFF, 0x03E0}
    };

    /** The ROM's own screen width, which the camera-X gates below are written against. */
    private static final int NATIVE_SCREEN_WIDTH = 320;
    /** {@code loc_57686}: the GHZ recreation band, {@code [$440,$880)} in Player 1's Y. */
    static final int GHZ_BAND_MIN_Y = 0x440;
    static final int GHZ_BAND_MAX_Y = 0x880;
    static final int GHZ_PRE_LOCK_MIN_X = 0x160;
    static final int GHZ_PRE_LOCK_MAX_X = 0x19A0;
    static final int GHZ_LOCK_PLAYER_Y = 0x7C0;
    static final int GHZ_LOCK_CAMERA_X = 0x160;
    static final int GHZ_ARENA_Y = 0x7C0;
    /** {@code move.w #$7F00,(Events_bg+$00).w}: fighting, and the lock byte cleared with it. */
    static final int GHZ_BOSS_FIGHTING_WORD = 0x7F00;
    /** {@code loc_5770C}: the MTZ recreation, below {@code $440}. */
    static final int MTZ_PRE_LOCK_MAX_X = 0x1660;
    static final int MTZ_LOCK_PLAYER_Y = 0x420;
    static final int MTZ_LOCK_CAMERA_X = 0x1660;
    static final int MTZ_ARENA_Y = 0x380;
    /** {@code move.w #$7F00,(Events_bg+$02).w}: fighting, and the lock byte cleared with it. */
    static final int MTZ_BOSS_FIGHTING_WORD = 0x7F00;
    /** The head of {@code sub_575EA}: the Mecha Sonic arena. */
    static final int FINAL_ARENA_CAMERA_X = 0x19A0;
    static final int FINAL_ARENA_PLAYER_Y = 0x680;
    static final int FINAL_ARENA_Y = 0x5C0;
    /** {@code loc_5777E}: a beaten boss opens the act back up. */
    static final int BEATEN_MIN_X = 0;
    static final int BEATEN_MAX_X = 0x19A0;

    /** {@code Events_bg} byte offsets. */
    static final int EV_GHZ_BOSS = 0x00;
    static final int EV_GHZ_LOCK = 0x01;
    static final int EV_MTZ_BOSS = 0x02;
    static final int EV_MTZ_LOCK = 0x03;
    static final int EV_ARRIVAL_CONTROL = 0x04;
    static final int EV_EVENT_OWNS_BOUNDS = 0x05;
    static final int EV_FINAL_ARENA = 0x06;
    static final int EV_CUTSCENE_BUTTON = 0x08;

    @Override
    public void update(int act, int frameCounter) {
        if (!hasRuntime()) {
            return;
        }
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(zoneRuntimeRegistry()).orElse(null);
        if (state == null) {
            return;
        }
        if (!state.screenInitApplied()) {
            // The load-time call is the owner; this is the fallback for a runtime installed
            // without one (partial harnesses).
            state.markScreenInitApplied();
            applyScreenInit(act, state);
        }
        if (act == 1 && state.foregroundRoutine() == 0
                && camera().getMinY() == camera().getMaxY()) {
            // loc_58ACC falls through to stage4 after the arrival closes the bounds.
            state.setSpecialVIntRoutine(state.specialVIntRoutine() + 4);
            state.setForegroundRoutine(4);
        }
        if (act == 1) {
            var services = levelManager().getObjectManager().getObjectServices();
            // SSZ2_ScreenEvent reads the previous shake offset; loc_58D1E's
            // background tail prepares the next one. advanceScreenShake retains
            // both values, so render/re-render consumes only the captured applied word.
            updateAct2Foreground(services, state);
            updateAct2WaterPalette(services, state);
            if (state.foregroundRoutine() == 0x18)
                state.endingPlane().streamRows(camera().getYCopy(),
                        services.levelManager()::getForegroundTileDescriptorAtWorld);
            state.advanceScreenShake(frameCounter,
                    services.playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p && p.getDead());
            camera().setYCopy((short) (camera().getYCopy() + state.appliedScreenShakeOffset()));
        }
        if (act == 0) {
            var services = levelManager().getObjectManager().getObjectServices();
            state.launch().advanceShake(frameCounter,
                    services.playerQuery().mainPlayerOrNull() instanceof AbstractPlayableSprite p && p.getDead());
            camera().setYCopy((short) (camera().getYCopy() + state.launch().appliedShake()));
            if (state.foregroundRoutine() == 0) {
                if (gameState().isEndOfLevelFlag()) SszLaunchWorld.begin(services, state);
                else dynamicResize(state);
            } else {
                SszLaunchWorld.update(services, state);
            }
        }
        // User-confirmed widescreen exceptions: GHZ and MTZ replica arenas only.
        // ROM has no mask; preserve its gameplay bounds and derive release from
        // the event owner, not player damage or boss HP. Native width renders no mask.
        if (act == 0 && state.centerNativeArenaCamera()) state.arenaMask().activate(320);
        else state.arenaMask().release();
        state.arenaMask().advance();
    }

    /** loc_58C68 calls sub_5928C; redraw stages return without ticking it. */
    private static void updateAct2WaterPalette(com.openggf.level.objects.ObjectServices services,
                                               SszZoneRuntimeState state) {
        int routine = state.foregroundRoutine();
        if (routine != 0 && routine != 4 && routine != 8 && routine != 0x18) return;
        int offset = state.endingPlane().advanceWaterPalette();
        if (offset < 0) return;
        try {
            com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyContiguousPatch(
                    services.paletteOwnershipRegistryOrNull(), services.currentLevel(), services.graphicsManager(),
                    "ssz-ending-water", com.openggf.game.sonic3k.S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    3, 13, services.rom().readBytes(0x592BE + offset, 6));
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** SSZ2_ScreenEvent stage4, loc_58AE0; stage0 falls through on the same pass. */
    private static void updateAct2Foreground(com.openggf.level.objects.ObjectServices services,
                                             SszZoneRuntimeState state) {
        if (state.foregroundRoutine() == 0x10) {
            // loc_58B9E also calls sub_5B18E, the excluded actual ending owner.
            // Retain only its independently seedable island presentation tail;
            // no fabricated ending progress supplies Events_fg_4 or _unkFAAE.
            if (services.gameState().hasAllSuperEmeralds() || services.gameState().hasAllEmeralds()) {
                int offset = state.endingPlane().advanceEmeraldPalette();
                if (offset >= 0) {
                    try {
                        com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyContiguousPatch(
                                services.paletteOwnershipRegistryOrNull(), services.currentLevel(), services.graphicsManager(),
                                "ssz-ending-emerald", com.openggf.game.sonic3k.S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                                3, 11, services.rom().readBytes(0x5931A + offset, 4));
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }
                return;
            }
            if (state.eventsFg4() == 0) return;
            try {
                // loc_58C1A copies Pal_Ending1 into lines2..4 before redrawing.
                for (int line = 1; line < 4; line++)
                    com.openggf.game.sonic3k.S3kPaletteWriteSupport.applyLine(
                            services.paletteOwnershipRegistryOrNull(), services.currentLevel(), services.graphicsManager(),
                            "ssz-ending-island", com.openggf.game.sonic3k.S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                            line, services.rom().readBytes(0xA97FC + (line - 1) * 32, 32), true);
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            state.endingPlane().beginSecondRedraw();
            state.setForegroundRoutine(0x14); // native fallthrough draws its first two rows now
        }
        if (state.foregroundRoutine() == 0x14) {
            if (state.endingPlane().advanceSecondRedraw(services.levelManager()::getForegroundTileDescriptorAtWorld)) {
                services.camera().setY((short) 0x720); services.camera().setYCopy((short) 0x720);
                state.endingPlane().finishSecondRedraw();
                state.setForegroundRoutine(0x18);
                // The outer palette call implements loc_58C42's fallthrough to sub_5928C.
            }
            return;
        }
        if (state.foregroundRoutine() == 8) {
            if ((short) state.eventsFg4() <= 0) return;
            state.setEventsFg4(0);
            com.openggf.game.sonic3k.objects.SszEndingIslandMask.fillTiles(services);
            services.objectManager().createDynamicObject(() ->
                    new com.openggf.game.sonic3k.objects.SszEndingIslandMask(
                            new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
            state.endingPlane().begin(services.camera().getYCopy(),
                    services.levelManager()::getForegroundTileDescriptorAtWorld);
            state.setForegroundRoutine(0xC);
            return; // loc_58B64 does not execute the first redraw until the next pass.
        }
        if (state.foregroundRoutine() == 0xC) {
            if (state.endingPlane().advance(services.levelManager()::getForegroundTileDescriptorAtWorld)) {
                services.camera().setY((short) 0); services.camera().setYCopy((short) 0);
                services.camera().setX((short) 0);
                state.setCenterNativeArenaCamera(false); // arena projection ends with native plane/camera reset
                // Next native dispatch calls sub_5B18E, the separately scoped ending.
                state.setForegroundRoutine(0x10);
            }
            return;
        }
        if (state.foregroundRoutine() != 4 || state.eventsFg4() == 0) return;
        var level = services.currentLevel();
        // ScreenEvents sets a3=Level_layout_main (after the eight-byte header).
        // Its $24/$28 pointer words select FG rows9/10, not rows7/8. The ROM
        // writes nine chunks then Refresh_PlaneFullDirect; publish that same map
        // change through the engine's rewind-aware mutation/redraw owner.
        services.zoneLayoutMutationPipeline().applyImmediately(context -> {
            for (int x = 0; x < 9; x++) {
                context.surface().setBlockInMap(0, x, 9, (x & 1) == 0 ? 0x17 : 0x18);
                context.surface().setBlockInMap(0, x, 10, 0x19);
            }
            return com.openggf.game.mutation.MutationEffects.redrawAllTilemaps();
        }, new com.openggf.game.mutation.LayoutMutationContext(
                com.openggf.game.mutation.LevelMutationSurface.forLevel(level),
                services.levelManager()::applyMutationEffects));
        state.setEventsFg4(0);
        state.setEndingRunning(true);
        state.setForegroundRoutine(8);
    }

    /**
     * {@code SSZ1_ScreenInit} / {@code SSZ2_ScreenInit} run inside the level load, before the
     * first {@code Load_Sprites}/{@code Process_Sprites} pass, so the arrival controller's own
     * init pass is frame 1 and its first {@code loc_57D50} rise step is frame 2. The event
     * manager calls this while preparing that initial pass, after host camera positioning.
     * Ordinary pre-physics retains an idempotent fallback for positioned entries.
     */
    public void applyScreenInitAtLoad(int act) {
        if (!hasRuntime()) {
            return;
        }
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(zoneRuntimeRegistry()).orElse(null);
        if (state == null || state.screenInitApplied()) {
            return;
        }
        state.markScreenInitApplied();
        applyScreenInit(act, state);
    }

    /**
     * {@code SSZ1_ScreenInit} / {@code SSZ2_ScreenInit}. The controller, forced camera and
     * {@code Scroll_lock} are on the no-starpost path only ({@code tst.b (Last_star_post_hit).w});
     * clearing {@code _unkEE98}/{@code _unkEE9C} happens either way.
     */
    private void applyScreenInit(int act, SszZoneRuntimeState state) {
        Camera camera = camera();
        if (act == 1) {
            applyAct2ScreenInit(state);
            return;
        }
        // Levels_1000_High: SSZ1 wraps vertically over $1000 and the dynamic bounds reach -$100.
        camera.setVerticalWrapEnabled(true, LEVEL_MAX_Y);
        if (!startsAtStarPost()) {
            int controllerX = act == 0 ? ACT1_CONTROLLER_X : ACT2_CONTROLLER_X;
            int riseFrames = act == 0 ? ACT1_CONTROLLER_RISE_FRAMES : ACT2_CONTROLLER_RISE_FRAMES;
            spawnObject(() -> new SszArrivalControllerObjectInstance(
                    new ObjectSpawn(controllerX, 0x1000, 0, riseFrames, 0, false, 0)));
            state.setEventsBgByte(EV_EVENT_OWNS_BOUNDS, 0xFF);
            if (act == 0) {
                camera.setMaxX((short) ACT1_CAMERA_MAX_X);
                camera.setMaxY((short) ACT1_CAMERA_MAX_Y);
                camera.setMaxYTarget((short) ACT1_CAMERA_MAX_Y);
                camera.setX((short) ACT1_CAMERA_X);
                camera.setXCopy((short) ACT1_CAMERA_X);
                camera.setY((short) ACT1_CAMERA_Y);
                camera.setYCopy((short) ACT1_CAMERA_Y);
            } else {
                camera.setX((short) ACT2_CAMERA_X);
                camera.setXCopy((short) ACT2_CAMERA_X);
                camera.setY((short) ACT2_CAMERA_Y);
                camera.setYCopy((short) ACT2_CAMERA_Y);
            }
            camera.setScrollLocked(true);
        }
        state.setUnkEE98(0);
        state.setCloudOscillator(0);
        if (act == 0) {
            spawnBackgroundClouds();
        }
    }

    /** SSZ2_ScreenInit: camera writes are inside the first successful allocation. */
    private void applyAct2ScreenInit(SszZoneRuntimeState state) {
        var arrival = spawnObject(() -> new SszArrivalControllerObjectInstance(
                new ObjectSpawn(ACT2_CONTROLLER_X, 0x1000, 0, ACT2_CONTROLLER_RISE_FRAMES, 0, false, 0)));
        if (arrival != null) {
            var camera = camera();
            camera.setX((short) ACT2_CAMERA_X); camera.setXCopy((short) ACT2_CAMERA_X);
            camera.setY((short) ACT2_CAMERA_Y); camera.setYCopy((short) ACT2_CAMERA_Y);
            camera.setScrollLocked(true);
            // AllocateObject found the first free slot. With no intervening frees,
            // the next lowest-free allocation is CreateNewSprite4's forward scan.
            spawnObject(() -> new com.openggf.game.sonic3k.objects.SszAct2CameraController(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        }
        // SSZ2_BackgroundInit clears the whole longword, not just its integer half.
        state.setCloudOffsetFixed(0);
    }

    /**
     * {@code SSZ1_ScreenInit}'s always-run tail (sonic3k.asm:115846-115890) followed by
     * {@code SSZ1_BackgroundInit} (116385-116404), in the order the pointer table runs them:
     * five roaming clouds from {@code word_58758}, each drawing one {@code Random_Number} word
     * for its bob phase; then the {@code _unkEE9C} oscillator; then ten invisible sloped
     * platforms from {@code word_5853E}.
     *
     * <p>The ROM records the roaming clouds' object slots in {@code HScroll_table+$1F6} so
     * {@code sub_5758A} can reposition them as a batch. The engine's clouds do that arithmetic
     * per instance instead, so no slot table is kept; the allocation order, which decides the
     * RNG draw order, is preserved.
     */
    private void spawnBackgroundClouds() {
        for (int index = 0; index < SszRoamingCloudObjectInstance.CLOUD_COUNT; index++) {
            int phaseSeed = nextRandomWord();
            final int row = index;
            spawnObject(() -> SszRoamingCloudObjectInstance.forRow(row, phaseSeed));
        }
        spawnObject(() -> new SszCloudOscillatorObjectInstance(
                new ObjectSpawn(0, 0, 0, 0, 0, false, 0)));
        for (int index = 0; index < SszSolidCloudObjectInstance.CLOUD_COUNT; index++) {
            final int row = index;
            spawnObject(() -> SszSolidCloudObjectInstance.forRow(row));
        }
    }

    /** {@code jsr (Random_Number).l} in {@code loc_57BB2}'s init pass. */
    private int nextRandomWord() {
        var rng = rngOrNull();
        return rng == null ? 0 : rng.nextWord();
    }

    /**
     * {@code loc_13AB4} (sonic3k.asm:26439-26449): {@code Current_zone_and_act == $A00} with
     * {@code Tails_CPU_star_post_flag} clear runs {@code sub_13ECA}, then writes
     * {@code Tails_CPU_routine = $A} and {@code object_control = $83} — the same branch AIZ1's
     * intro takes, so Player 2 waits parked at {@code ($7F00,0)} until
     * {@code Obj_57DCC} beams her in and writes routine 6.
     *
     * <p>This is the branch the engine models as the sidekick dormant marker. The ROM's separate
     * {@code loc_13B18} branch (SOZ1, zone {@code $17}) is a different question and belongs to
     * {@code SidekickCpuInitializationPolicy}; SSZ does not use it.
     */
    public boolean shouldEnterArrivalSidekickDormantMarker(AbstractPlayableSprite sidekick) {
        return sidekick != null && !startsAtStarPost();
    }

    /** {@code tst.b (Last_star_post_hit).w}. */
    private boolean startsAtStarPost() {
        var levelManager = levelManager();
        if (levelManager == null) {
            return false;
        }
        var checkpoint = levelManager.getCheckpointState();
        // ScreenInit tests Last_star_post_hit, restored by Load_Starpost_Settings.
        // The activation mark models the persistent post/respawn history instead;
        // a death reload restores the checkpoint index before any post executes,
        // while that separate activation record can still be empty.
        return checkpoint != null && checkpoint.getLastCheckpointIndex() > 0;
    }

    /**
     * {@code sub_575EA} (sonic3k.asm:116198-116290): the whole act-1 bounds machine. Boss
     * allocation at {@code loc_576E8}, {@code loc_5775C} and the final arena belongs to the boss
     * slices; this reproduces every bounds write and flag test around them.
     */
    void dynamicResize(SszZoneRuntimeState state) {
        if (state.eventsBgByte(EV_FINAL_ARENA) != 0) {
            return;
        }
        Camera camera = camera();
        AbstractPlayableSprite player = spriteManager().getMainPlayable();
        if (player == null) {
            return;
        }
        int cameraX = nativeFramedCameraX(camera);
        int playerY = player.getCentreY() & 0xFFFF;
        if (cameraX >= FINAL_ARENA_CAMERA_X && playerY < FINAL_ARENA_PLAYER_Y) {
            camera.setMinX((short) FINAL_ARENA_CAMERA_X);
            camera.setMinY((short) FINAL_ARENA_Y);
            camera.setMaxYTarget((short) FINAL_ARENA_Y);
            state.setEventsBgByte(EV_FINAL_ARENA, 0xFF);
            return;
        }
        // loc_5761C: while an event owns the bounds (the arrival, a live boss) nothing below runs.
        if (state.eventsBgByte(EV_EVENT_OWNS_BOUNDS) != 0) {
            return;
        }
        if ((state.eventsBgByte(EV_GHZ_LOCK) | state.eventsBgByte(EV_MTZ_LOCK)) == 0) {
            applyDynamicYBands(camera, player);
        }
        if (playerY >= GHZ_BAND_MIN_Y) {
            if (playerY >= GHZ_BAND_MAX_Y) {
                openBounds(camera);
                return;
            }
            ghzBand(state, camera, player, playerY);
            return;
        }
        mtzBand(state, camera, player, playerY);
    }

    /**
     * {@code sub_575EA} tests {@code Camera_X_pos} against three literals — {@code $19A0} for the
     * Mecha Sonic arena, {@code $160} for Green Hill and {@code $1660} for Metropolis — and two of
     * the three are equality tests. On the cartridge the camera trails the leader by 160 px, half
     * of a 320-pixel screen; a wider viewport trails by half of its own width, so the same world
     * position gives a smaller {@code Camera_X_pos} and the equality can never hold. This reframes
     * the value the gates read into the ROM's own 320-wide framing and leaves every bounds
     * <em>write</em> in world coordinates, because moving the arena would move its terrain.
     * Same treatment, same reason, as {@code HczMinibossInstance.nativeFramedCameraX}.
     */
    private int nativeFramedCameraX(Camera camera) {
        int focusExcess = Math.max(0, DeadzoneGeometry.rightEdge(camera.getWidth())
                - DeadzoneGeometry.rightEdge(NATIVE_SCREEN_WIDTH));
        return (camera.getX() + focusExcess) & 0xFFFF;
    }

    /** {@code loc_5761C}-{@code loc_57674}: the {@code word_5778A}/{@code word_5779A} bands. */
    private void applyDynamicYBands(Camera camera, AbstractPlayableSprite player) {
        int cameraY = camera.getY() & 0xFFFF;
        if (cameraY < DYNAMIC_BOUNDS_MIN_Y || cameraY >= DYNAMIC_BOUNDS_MAX_Y) {
            // loc_57674: outside the band range the act uses its full height.
            camera.setMinY((short) LEVEL_MIN_Y);
            camera.setMaxY((short) LEVEL_MAX_Y);
            camera.setMaxYTarget((short) LEVEL_MAX_Y);
            return;
        }
        int playerX = player.getCentreX() & 0xFFFF;
        int minY = bandValue(MIN_Y_BAND, playerX);
        // cmp.w d0,d2 / bhi: the band only pulls Camera_min_Y down to or below the camera.
        if (minY <= cameraY) {
            camera.setMinY((short) minY);
        }
        int maxY = bandValue(MAX_Y_BAND, playerX);
        if (maxY >= cameraY) {
            camera.setMaxY((short) maxY);
            camera.setMaxYTarget((short) maxY);
        }
    }

    /** The table walk: the first row whose X limit is above the player's X. */
    private static int bandValue(int[][] table, int playerX) {
        for (int[] row : table) {
            if (playerX < row[0]) {
                return row[1];
            }
        }
        return table[table.length - 1][1];
    }

    /** {@code loc_57686}-{@code loc_576E8}. */
    private void ghzBand(SszZoneRuntimeState state, Camera camera,
                         AbstractPlayableSprite player, int playerY) {
        int flag = state.eventsBgByte(EV_GHZ_BOSS);
        if (flag < 0) {
            openBounds(camera);
            return;
        }
        if (flag != 0) {
            return;
        }
        if (state.eventsBgByte(EV_GHZ_LOCK) == 0) {
            camera.setMinX((short) GHZ_PRE_LOCK_MIN_X);
            camera.setMaxX((short) GHZ_PRE_LOCK_MAX_X);
            if (playerY < GHZ_LOCK_PLAYER_Y
                    || nativeFramedCameraX(camera) != GHZ_LOCK_CAMERA_X
                    || player.getAir()) {
                return;
            }
            camera.setMaxX((short) GHZ_LOCK_CAMERA_X);
            camera.setMinY((short) GHZ_ARENA_Y);
            camera.setMaxYTarget((short) GHZ_ARENA_Y);
            state.setEventsBgByte(EV_GHZ_LOCK, 0xFF);
        }
        allocateGhzBoss(state, camera);
    }

    /**
     * {@code loc_576E8}. The allocation is a second gate, not part of the lock: the lock only
     * publishes {@code Camera_target_max_Y_pos = $7C0}, and the camera then eases down to it at
     * two pixels a frame, so the fight starts when the arena has actually framed itself. The word
     * write is {@code move.w #$7F00,(Events_bg+$00).w}, which sets the fighting byte and clears
     * the lock byte beside it in the same instruction — {@code Events_bg+$01} is the low half of
     * that word, which is why nothing clears it separately.
     */
    private void allocateGhzBoss(SszZoneRuntimeState state, Camera camera) {
        if ((camera.getY() & 0xFFFF) != GHZ_ARENA_Y) {
            return;
        }
        SszGhzBossObjectInstance boss = spawnObject(() -> new SszGhzBossObjectInstance(
                new ObjectSpawn(
                        // addi.w #$110,d0 is an offset from the ROM's 320-pixel left edge; the
                        // lock fixes Camera_max_X_pos, not the wider viewport's left edge.
                        (nativeFramedCameraX(camera)
                                + SszGhzBossObjectInstance.SPAWN_CAMERA_X_OFFSET) & 0xFFFF,
                        (camera.getY() + SszGhzBossObjectInstance.SPAWN_CAMERA_Y_OFFSET) & 0xFFFF,
                        0, 0, 0, false, 0)));
        if (boss == null) {
            // jsr (AllocateObject).l / bne.s loc_5770A: a failed allocation writes no flags and
            // the gate is simply retried on the next frame the camera is still at $7C0.
            return;
        }
        state.setEventsBgByte(EV_EVENT_OWNS_BOUNDS, 0xFF);
        state.setEventsBgWord(EV_GHZ_BOSS, GHZ_BOSS_FIGHTING_WORD);
    }

    /** {@code loc_5770C}-{@code loc_5775C}. */
    private void mtzBand(SszZoneRuntimeState state, Camera camera,
                         AbstractPlayableSprite player, int playerY) {
        int flag = state.eventsBgByte(EV_MTZ_BOSS);
        if (flag < 0) {
            openBounds(camera);
            return;
        }
        if (flag != 0) {
            return;
        }
        if (state.eventsBgByte(EV_MTZ_LOCK) == 0) {
            camera.setMaxX((short) MTZ_PRE_LOCK_MAX_X);
            // tst.w (Events_bg+$00).w: once the GHZ word is non-zero the left limit opens to 0.
            int minX = state.eventsBgWord(EV_GHZ_BOSS) != 0 ? 0 : GHZ_PRE_LOCK_MIN_X;
            camera.setMinX((short) minX);
            if (playerY < MTZ_LOCK_PLAYER_Y
                    || nativeFramedCameraX(camera) != MTZ_LOCK_CAMERA_X
                    || player.getAir()) {
                return;
            }
            camera.setMinX((short) MTZ_LOCK_CAMERA_X);
            camera.setMinY((short) MTZ_ARENA_Y);
            camera.setMaxYTarget((short) MTZ_ARENA_Y);
            state.setEventsBgByte(EV_MTZ_LOCK, 0xFF);
        }
        allocateMtzBoss(state, camera);
    }

    /**
     * {@code loc_5775C}. As with Green Hill the allocation is a second gate, not part of the
     * lock: the lock only publishes {@code Camera_target_max_Y_pos = $380} and the camera then
     * eases up to it two pixels a frame. {@code move.w #$7F00,(Events_bg+$02).w} sets the
     * fighting byte and clears {@code +$03} beside it in the same instruction.
     *
     * <p>Unlike {@code loc_576E8} this one passes no position: {@code loc_7A72C} writes
     * {@code ($1700,$300)} as absolute world coordinates, so nothing here needs the camera's
     * 320-pixel framing.
     */
    private void allocateMtzBoss(SszZoneRuntimeState state, Camera camera) {
        if ((camera.getY() & 0xFFFF) != MTZ_ARENA_Y) {
            return;
        }
        SszMtzBossObjectInstance boss = spawnObject(() -> new SszMtzBossObjectInstance(
                new ObjectSpawn(SszMtzBossObjectInstance.SPAWN_X,
                        SszMtzBossObjectInstance.SPAWN_Y, 0, 0, 0, false, 0)));
        if (boss == null) {
            // jsr (AllocateObject).l / bne.s loc_7777C: a failed allocation writes no flags.
            return;
        }
        state.setEventsBgByte(EV_EVENT_OWNS_BOUNDS, 0xFF);
        state.setEventsBgWord(EV_MTZ_BOSS, MTZ_BOSS_FIGHTING_WORD);
    }

    /** {@code loc_5777E}. */
    private void openBounds(Camera camera) {
        camera.setMinX((short) BEATEN_MIN_X);
        camera.setMaxX((short) BEATEN_MAX_X);
    }
}
