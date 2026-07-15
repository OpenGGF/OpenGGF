package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.data.RomManager;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.sonic3k.FbzPaletteFoundation;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.objects.FbzOutdoorBgMotionObjectInstance;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.StagedBackgroundPlaneRedrawController;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Canonical mutable FBZ event workspace.
 *
 * <p>This task intentionally establishes only the ROM-shaped state/ownership
 * skeleton. Act 1 layout mutation and Act 2 boss behavior are ported by their
 * later route tasks.
 */
public final class Sonic3kFBZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kFBZEvents.class.getName());
    public enum RedrawDirection { NONE, TOP_DOWN, BOTTOM_UP, LEFT_TO_RIGHT, RIGHT_TO_LEFT }
    /** ROM {@code _unkF7C1}: a single inactive/active bit. */
    public enum MagneticPolarity { INACTIVE, ACTIVE }
    public enum PlaneAssignmentMode { NORMAL, REVERSED }
    public enum CollisionMode { FOREGROUND_ONLY, FOREGROUND_AND_BACKGROUND }
    public enum DeformMode { INDOOR, OUTDOOR }
    public enum PaletteVariant { INDOOR, OUTDOOR }
    public enum PaletteTarget { NONE, TARGET, NORMAL }

    /** Plane-A chunk-layout copy. Coordinates are byte cells in Level_layout_main. */
    public record LayoutCopy(int sourceX, int sourceY, int destX, int destY, int width, int height) {}

    /**
     * Observable native initialization effects. The live adapter delegates tilemap work to
     * {@link com.openggf.level.LevelManager}; tests use a recording surface to pin instruction order.
     */
    public interface Act2InitializationEffects {
        Act2InitializationEffects NONE = new Act2InitializationEffects() { };
        default void resetActualTileOffsets() { }
        default void refreshPlaneFull() { }
        default void copyTopBackgroundLines() { }
        default void deformNormalBackground() { }
        default void resetEffectiveTileOffsets() { }
        default void applyDeformation() { }
    }

    /** Ordinary FBZ2 traversal words captured as one rewind value snapshot. */
    public record Act2TraversalState(int layoutRegion, boolean foregroundOutdoor,
                                     boolean backgroundOutdoor, int foregroundStage,
                                     int backgroundStage, RedrawDirection redrawDirection,
                                     int redrawProgress, int redrawPosition,
                                     int redrawRowCount, int redrawVerticalAnchor,
                                     DeformMode deformMode, PaletteVariant paletteVariant,
                                     PaletteTarget paletteTarget) {}

    private static final int FG_LAYER = 0;
    private static final int[][] ACT1_LAYOUT_RANGES = {
            {0x400, 0xF00, 0x880, 0xA80}, {0x880, 0x1100, 0x180, 0x300},
            {0x1400, 0x1B80, 0x900, 0xB00}, {0x1A80, 0x2100, 0x80, 0x200},
            {0x2080, 0x2680, 0x100, 0x280}, {0, 0x180, 0x580, 0x780}
    };
    private static final int[] ACT2_LAYOUT_RANGE = {0xD80, 0x1300, 0xA00, 0xB80};
    private static final int ACT2_BOSS_SETUP_CAMERA_X = 0x2B30;
    private static final int ACT2_BOSS_REENTRY_CAMERA_X = 0x2C40;
    private static final int FBZ_BG_INDOOR_PALETTE_ADDR = 0x52DC0;
    private static final int FBZ_BG_OUTDOOR_PALETTE_ADDR = 0x52DD0;
    private static final int ACT_TRANSITION_WORLD_OFFSET_X = -0x2E00;
    private static final int FIRST_ROM_WORLD_OFFSET_SLOT = 4;
    private static final int LAST_ROM_WORLD_OFFSET_SLOT_EXCLUSIVE = 94;
    /** Native ScreenShakeArray2; the 32-byte pattern is repeated over the 64-frame mask. */
    private static final int[] SCREEN_SHAKE_ARRAY_2 = {
            1,2,1,3,1,2,2,1,2,3,1,2,1,2,0,0,
            2,0,3,2,2,3,2,2,1,3,0,0,1,0,1,3
    };

    private int act;
    private int foregroundLayoutRegion;
    private boolean foregroundOutdoor;
    private boolean backgroundOutdoor;
    private int backgroundRedrawStage;
    private RedrawDirection backgroundRedrawDirection = RedrawDirection.NONE;
    private int backgroundRedrawProgress;
    private int backgroundRedrawPosition;
    private int backgroundRedrawRowCount = -1;
    private int backgroundRedrawVerticalAnchor;
    private int lastRoundedBackgroundY = Integer.MIN_VALUE;
    private boolean planeBFrameTouched;
    /** This event instance has authored or restored the retained Plane-B image. */
    private boolean retainedPlaneOwned;
    private DeformMode deformMode = DeformMode.INDOOR;
    private PaletteVariant paletteVariant = PaletteVariant.INDOOR;
    private PaletteTarget paletteTarget = PaletteTarget.NONE;
    private boolean act1ScreenInitialized;
    private boolean act1BackgroundInitialized;
    private boolean outdoorMotionAllocationAttempted;
    private boolean outdoorMotionSpawned;
    private byte[] retainedPlaneSnapshot = new byte[0];
    private int outdoorBobOffset;
    /** ROM HScroll_table+$1FC: one 32-bit accumulator shared by every FBZ deform mode. */
    private int hScrollAccumulator;
    private boolean hScrollAccumulatorSampled;
    private int hScrollAccumulatorLastFrame;
    private int hScrollAccumulatorLastRead;
    private MagneticPolarity magneticPolarity = MagneticPolarity.INACTIVE;
    private int magneticTimerPhase;
    private boolean magneticEdgeObserved;
    private int magneticLastEdgeFrame;
    /** FBZ-owned bytes in the S3K Object_respawn_table, keyed by layout index. */
    private final BitSet pendulumOrientationBits = new BitSet(512);
    private int act2ForegroundStage;
    private int bossBackgroundStage;
    private int bossBackgroundOffsetX;
    private int bossBackgroundOffsetY;
    private boolean bossLoadPositionAdjustmentPending;
    private final ObjectRefId[] cloudRewindIds = new ObjectRefId[Sonic3kConstants.FBZ_CLOUD_REWIND_SLOT_COUNT];
    private boolean cloudCleanupTerminal;
    private PlaneAssignmentMode planeAssignmentMode = PlaneAssignmentMode.NORMAL;
    private CollisionMode collisionMode = CollisionMode.FOREGROUND_ONLY;
    private int collisionCameraDiffX;
    private int collisionCameraDiffY;
    private boolean screenShakeActive;
    private int screenShakeOffset;
    private int screenShakePhase;
    private boolean eventsFg5;
    private final StagedBackgroundPlaneRedrawController planeBRedraw =
            new StagedBackgroundPlaneRedrawController(new StagedBackgroundPlaneRedrawController.Surface() {
                @Override public void copyRow(int sx, int sy, int dy) {
                    int dest = 0xE000 + ((dy >>> 3) & 0x1F) * 0x80;
                    retainedPlaneOwned = true;
                    planeBFrameTouched |= levelManager().copyBackgroundTileRowFromWorldToVdpPlane(sx, sy, dest, 0x21);
                }
                @Override public void copyColumn(int sx, int sy, int dx) {
                    retainedPlaneOwned = true;
                    planeBFrameTouched |= levelManager().copyBackgroundTileColumnsFromWorldToVdpPlane(sx, sy, dx, 1, 0x11);
                }
            });

    @Override
    public void init(int act) {
        if (act < 0 || act > 1) throw new IllegalArgumentException("FBZ act must be 0 or 1: " + act);
        super.init(act);
        this.act = act;
        foregroundLayoutRegion = 0;
        foregroundOutdoor = false;
        backgroundOutdoor = false;
        backgroundRedrawStage = 0;
        backgroundRedrawDirection = RedrawDirection.NONE;
        backgroundRedrawProgress = 0;
        backgroundRedrawPosition = 0;
        backgroundRedrawRowCount = -1;
        backgroundRedrawVerticalAnchor = 0;
        lastRoundedBackgroundY = Integer.MIN_VALUE;
        planeBFrameTouched = false;
        retainedPlaneOwned = false;
        deformMode = DeformMode.INDOOR;
        paletteVariant = PaletteVariant.INDOOR;
        paletteTarget = PaletteTarget.NONE;
        act1ScreenInitialized = false;
        act1BackgroundInitialized = false;
        outdoorMotionAllocationAttempted = false;
        outdoorMotionSpawned = false;
        retainedPlaneSnapshot = new byte[0];
        outdoorBobOffset = 0;
        hScrollAccumulator = 0;
        hScrollAccumulatorSampled = false;
        hScrollAccumulatorLastFrame = 0;
        hScrollAccumulatorLastRead = 0;
        magneticPolarity = MagneticPolarity.INACTIVE;
        magneticTimerPhase = 0;
        magneticEdgeObserved = false;
        magneticLastEdgeFrame = 0;
        pendulumOrientationBits.clear();
        act2ForegroundStage = 0;
        bossBackgroundStage = 0;
        bossBackgroundOffsetX = 0;
        bossBackgroundOffsetY = 0;
        bossLoadPositionAdjustmentPending = false;
        Arrays.fill(cloudRewindIds, null);
        cloudCleanupTerminal = false;
        planeAssignmentMode = PlaneAssignmentMode.NORMAL;
        collisionMode = CollisionMode.FOREGROUND_ONLY;
        collisionCameraDiffX = 0;
        collisionCameraDiffY = 0;
        screenShakeActive = false;
        screenShakeOffset = 0;
        screenShakePhase = 0;
        eventsFg5 = false;
    }

    /** Runs the ROM ScreenInit/BackgroundInit phase after the runtime owner is installed. */
    public void initializeAct1Runtime() {
        if (act != 0 || !hasRuntime()) return;
        AbstractPlayableSprite player = spriteManager().getMainPlayable();
        if (player == null) return;
        if (!act1ScreenInitialized) applyRuntimeCopies(initializeAct1Screen(player.getCentreX()));
        if (!act1BackgroundInitialized) {
            initializeAct1Background(player.getCentreX());
            retainedPlaneOwned = true;
            levelManager().seedBackgroundVdpPlaneFromWorld(backgroundOutdoor ? 0x200 : 0);
            submitAct1PaletteOwnership();
        }
        spawnOutdoorMotionController();
    }

    @Override public void update(int act, int frameCounter) {
        if (act != this.act) throw new IllegalArgumentException("FBZ handler act mismatch: " + act);
        if (!hasRuntime()) return;
        AbstractPlayableSprite player = spriteManager().getMainPlayable();
        if (player == null) return;
        if (act == 0) {
            initializeAct1Runtime();
            updateAct1Frame(player.getCentreX(), player.getCentreY(), player.getDead(), frameCounter);
            return;
        }
        initializeAct2Runtime();
        // FBZ2_ScreenEvent adds Screen_shake_offset to Camera_Y_pos_copy,
        // leaving the live Camera_Y_pos word untouched.
        if (screenShakeActive) {
            screenShakePhase = frameCounter & 0x3F;
            screenShakeOffset = SCREEN_SHAKE_ARRAY_2[screenShakePhase & 0x1F];
        } else {
            screenShakeOffset = 0;
        }
        camera().setYCopy((short) (camera().getYCopy() + screenShakeOffset));
        applyRuntimeCopies(updateAct2ScreenEvent(player.getCentreX(), player.getCentreY(),
                player.getDead(), camera().getX()));
        updateAct2BackgroundEvent(player.getCentreX(), player.getCentreY(), player.getDead());
    }

    /** Locked-on FBZ2_ScreenInit/BackgroundInit ordinary-route setup. */
    public void initializeAct2Runtime() {
        requireAct2("runtime initialization");
        if (!hasRuntime()) return;
        int cameraX = camera().getX() & 0xFFFF;
        Act2InitializationEffects effects = liveAct2InitializationEffects();
        if (!act1ScreenInitialized) initializeAct2Screen(cameraX, effects);
        if (!act1BackgroundInitialized) {
            initializeAct2Background(cameraX, effects);
        }
        spawnOutdoorMotionController();
    }

    /**
     * ROM {@code FBZ2_ScreenInit}. The >=$2C40 boss re-entry preparation is
     * deliberately represented by its exact routine/min-X words here; the
     * object/art setup executed by {@code SetUp_FBZ2BossEvent} belongs to the
     * Task 15 boss/event task.
     */
    public void initializeAct2Screen(int cameraX) {
        initializeAct2Screen(cameraX, Act2InitializationEffects.NONE);
    }

    void initializeAct2Screen(int cameraX, Act2InitializationEffects effects) {
        requireAct2("screen initialization");
        Objects.requireNonNull(effects, "effects");
        act1ScreenInitialized = true;
        if ((cameraX & 0xFFFF) >= ACT2_BOSS_REENTRY_CAMERA_X) {
            act2ForegroundStage = 4;
            if (hasRuntime()) camera().setMinX((short) ACT2_BOSS_REENTRY_CAMERA_X);
        }
        // loc_52E2C is unconditional, including boss re-entry.
        effects.resetActualTileOffsets();
        effects.refreshPlaneFull();
    }

    /** ROM {@code FBZ2_BackgroundInit}; stage zero falls through on first event tick. */
    public void initializeAct2Background(int cameraX) {
        initializeAct2Background(cameraX, Act2InitializationEffects.NONE);
    }

    void initializeAct2Background(int cameraX, Act2InitializationEffects effects) {
        requireAct2("background initialization");
        Objects.requireNonNull(effects, "effects");
        act1BackgroundInitialized = true;
        backgroundOutdoor = false;
        deformMode = DeformMode.INDOOR;
        paletteVariant = PaletteVariant.INDOOR;
        paletteTarget = PaletteTarget.NONE;
        lastRoundedBackgroundY = effectiveBackgroundY() & ~0x0F;
        if ((cameraX & 0xFFFF) >= ACT2_BOSS_REENTRY_CAMERA_X) {
            bossBackgroundStage = 16;
            // FBZ2_BackgroundInit copies the top BG line pointer after selecting stage $10.
            effects.copyTopBackgroundLines();
            screenShakeActive = true;
            return;
        }
        // loc_53002: FBZ_Deform, Reset_TileOffsetPositionEff,
        // Refresh_PlaneFull, then ApplyDeformation.
        effects.deformNormalBackground();
        effects.resetEffectiveTileOffsets();
        effects.refreshPlaneFull();
        effects.applyDeformation();
    }

    /** Locked-on {@code FBZ2SE_Normal}, through the $2B30 setup handoff. */
    public List<LayoutCopy> updateAct2ScreenEvent(int playerX, int playerY,
                                                   boolean dying, int cameraX) {
        requireAct2("screen event");
        if (act2ForegroundStage != 0 || dying) return List.of();
        if (foregroundLayoutRegion == 0) {
            if (contains(ACT2_LAYOUT_RANGE, playerX, playerY)) foregroundLayoutRegion = 4;
            // Only FBZ2_NoLayoutMod flows through the $2B30 check.
            if ((cameraX & 0xFFFF) >= ACT2_BOSS_SETUP_CAMERA_X) act2ForegroundStage = 4;
            return List.of();
        }
        if (!contains(ACT2_LAYOUT_RANGE, playerX, playerY)) {
            foregroundLayoutRegion = 0;
            return List.of();
        }
        // loc_52F10 is an explicit return path: the inclusive window neither copies
        // nor reaches FBZ2_NoLayoutMod's camera threshold.
        if (playerX >= 0xE00 && playerX <= 0x1280) return List.of();
        boolean nextOutdoor = foregroundOutdoor;
        if (!foregroundOutdoor && playerY > 0xB0E) nextOutdoor = true;
        else if (foregroundOutdoor && playerY <= 0xAF2) nextOutdoor = false;
        if (nextOutdoor == foregroundOutdoor) return List.of();
        foregroundOutdoor = nextOutdoor;
        return List.of(act2LayoutCopy(nextOutdoor));
    }

    /**
     * FBZ2's sole 14x4 foreground window. The disassembly's {@code $10/$50}
     * values are byte offsets into four-byte interleaved row-pointer entries,
     * so they select source row 4 and destination row 20 respectively.
     */
    public static LayoutCopy act2LayoutCopy(boolean outdoor) {
        return new LayoutCopy(112, outdoor ? 4 : 0, 26, 20, 14, 4);
    }

    /** Locked-on ordinary {@code FBZ2_BackgroundEvent} stages 0/4/8/12. */
    public void updateAct2BackgroundEvent(int playerX, int playerY, boolean dying) {
        updateAct2BackgroundEvent(playerX, playerY, dying,
                hasRuntime() ? liveAct2InitializationEffects() : Act2InitializationEffects.NONE);
    }

    void updateAct2BackgroundEvent(int playerX, int playerY, boolean dying,
                                   Act2InitializationEffects effects) {
        requireAct2("background event");
        Objects.requireNonNull(effects, "effects");
        if (bossBackgroundStage == 16 || act2ForegroundStage != 0) {
            bossBackgroundStage = 16;
            return;
        }
        if (bossBackgroundStage == 0) {
            // FBZ2BGE_Init copies (a3) into $70/$74/$78/$7C before addq #4.
            effects.copyTopBackgroundLines();
            bossBackgroundStage = 4;
        }
        if (bossBackgroundStage == 8 || bossBackgroundStage == 12) {
            checkAct2BackgroundChange(playerY);
            advanceBackgroundRedraw();
            if (backgroundRedrawDirection == RedrawDirection.NONE) bossBackgroundStage = 4;
            drawNormalBackgroundRowIfCrossed();
            finishPlaneBFrame();
            return;
        }
        if (dying) return;
        checkAct2BackgroundChange(playerY);
        if (backgroundRedrawDirection != RedrawDirection.NONE) advanceBackgroundRedraw();
        drawNormalBackgroundRowIfCrossed();
        finishPlaneBFrame();
    }

    private void checkAct2BackgroundChange(int playerY) {
        if (foregroundLayoutRegion != 4) return;
        if (!backgroundOutdoor && playerY >= 0xA40) {
            startAct2BackgroundChange(true, RedrawDirection.BOTTOM_UP, 12);
        } else if (backgroundOutdoor && playerY <= 0xA40) {
            startAct2BackgroundChange(false, RedrawDirection.TOP_DOWN, 8);
        }
    }

    private void startAct2BackgroundChange(boolean outdoor, RedrawDirection direction, int stage) {
        backgroundOutdoor = outdoor;
        deformMode = outdoor ? DeformMode.OUTDOOR : DeformMode.INDOOR;
        paletteVariant = outdoor ? PaletteVariant.OUTDOOR : PaletteVariant.INDOOR;
        paletteTarget = PaletteTarget.NORMAL;
        backgroundRedrawDirection = direction;
        backgroundRedrawStage = stage;
        bossBackgroundStage = stage;
        backgroundRedrawProgress = 0;
        backgroundRedrawRowCount = 0x0F;
        backgroundRedrawVerticalAnchor = effectiveBackgroundY() & 0xFF0;
        submitAct1PaletteOwnership();
    }

    public Act2TraversalState captureAct2TraversalState() {
        requireAct2("traversal capture");
        return new Act2TraversalState(foregroundLayoutRegion, foregroundOutdoor, backgroundOutdoor,
                act2ForegroundStage, bossBackgroundStage, backgroundRedrawDirection,
                backgroundRedrawProgress, backgroundRedrawPosition, backgroundRedrawRowCount,
                backgroundRedrawVerticalAnchor, deformMode, paletteVariant, paletteTarget);
    }

    public void restoreAct2TraversalState(Act2TraversalState state) {
        requireAct2("traversal restore");
        Objects.requireNonNull(state, "state");
        validateLayoutRegion(1, state.layoutRegion());
        validateFourStepStage("Act 2 foreground", state.foregroundStage(), 12);
        validateFourStepStage("Act 2 background", state.backgroundStage(), 16);
        foregroundLayoutRegion = state.layoutRegion();
        foregroundOutdoor = state.foregroundOutdoor();
        backgroundOutdoor = state.backgroundOutdoor();
        act2ForegroundStage = state.foregroundStage();
        bossBackgroundStage = state.backgroundStage();
        backgroundRedrawDirection = Objects.requireNonNull(state.redrawDirection(), "redraw direction");
        backgroundRedrawStage = state.backgroundStage() == 8 || state.backgroundStage() == 12
                ? state.backgroundStage() : 0;
        backgroundRedrawProgress = state.redrawProgress();
        backgroundRedrawPosition = state.redrawPosition();
        backgroundRedrawRowCount = state.redrawRowCount();
        backgroundRedrawVerticalAnchor = state.redrawVerticalAnchor();
        deformMode = Objects.requireNonNull(state.deformMode(), "deform mode");
        paletteVariant = Objects.requireNonNull(state.paletteVariant(), "palette variant");
        paletteTarget = Objects.requireNonNull(state.paletteTarget(), "palette target");
    }

    public static int[][] act1LayoutRanges() {
        return Arrays.stream(ACT1_LAYOUT_RANGES).map(int[]::clone).toArray(int[][]::new);
    }

    public List<LayoutCopy> initializeAct1Screen(int playerX) {
        requireAct1();
        act1ScreenInitialized = true;
        if ((playerX & 0xFFFF) < 0x180) {
            foregroundLayoutRegion = 0x18;
            foregroundOutdoor = true; // ROM st -> non-zero word ($FF00 from cleared RAM).
            return List.of();
        }
        foregroundLayoutRegion = 0;
        foregroundOutdoor = false;
        return List.of(new LayoutCopy(0, 18, 0, 13, 5, 3));
    }

    public PaletteTarget initializeAct1Background(int playerX) {
        requireAct1();
        act1BackgroundInitialized = true;
        if ((playerX & 0xFFFF) < 0x180) {
            backgroundOutdoor = true;
            deformMode = DeformMode.OUTDOOR;
            paletteVariant = PaletteVariant.OUTDOOR;
            paletteTarget = PaletteTarget.TARGET;
            lastRoundedBackgroundY = effectiveBackgroundY() & ~0x0F;
            return paletteTarget;
        }
        backgroundOutdoor = false;
        deformMode = DeformMode.INDOOR;
        paletteVariant = PaletteVariant.INDOOR;
        paletteTarget = PaletteTarget.NONE;
        lastRoundedBackgroundY = effectiveBackgroundY() & ~0x0F;
        return paletteTarget;
    }

    public void updateAct1Frame(int playerX, int playerY, boolean dying, int frameCounter) {
        requireAct1();
        if (!dying) applyRuntimeCopies(updateAct1ScreenEvent(playerX, playerY));
        updateAct1BackgroundEvent(playerX, playerY, dying);
    }

    public List<LayoutCopy> updateAct1ScreenEvent(int x, int y) {
        if (foregroundLayoutRegion == 0) {
            for (int i = 0; i < ACT1_LAYOUT_RANGES.length; i++) {
                if (contains(ACT1_LAYOUT_RANGES[i], x, y)) {
                    foregroundLayoutRegion = (i + 1) * 4;
                    break;
                }
            }
            return List.of();
        }
        int region = foregroundLayoutRegion / 4;
        if (region < 1 || region > 6 || !contains(ACT1_LAYOUT_RANGES[region - 1], x, y)) {
            foregroundLayoutRegion = 0;
            return List.of();
        }
        Boolean toOutdoor = foregroundTransition(region, x, y, foregroundOutdoor);
        if (toOutdoor == null || toOutdoor == foregroundOutdoor) return List.of();
        foregroundOutdoor = toOutdoor;
        return act1LayoutCopies(region, toOutdoor);
    }

    private static Boolean foregroundTransition(int region, int x, int y, boolean outdoor) {
        return switch (region) {
            case 1 -> !outdoor
                    ? (x < 0xB00 ? (x > 0x70A ? Boolean.TRUE : null) : (y > 0xA0E ? Boolean.TRUE : null))
                    : (x < 0xB00 ? (x <= 0x6F6 ? Boolean.FALSE : null) : (y <= 0x9F2 ? Boolean.FALSE : null));
            case 2 -> !outdoor
                    ? (x < 0xC80 ? (y < 0x1F2 ? Boolean.TRUE : null) : (x > 0x108A ? Boolean.TRUE : null))
                    : (x < 0xC80 ? (y >= 0x20E ? Boolean.FALSE : null) : (x <= 0x1076 ? Boolean.FALSE : null));
            case 3 -> !outdoor
                    ? (x < 0x1880 ? (x > 0x158A ? Boolean.TRUE : null) : (y > 0xA0E ? Boolean.TRUE : null))
                    : (x < 0x1880 ? (x <= 0x1576 ? Boolean.FALSE : null) : (y <= 0x9F2 ? Boolean.FALSE : null));
            case 4 -> !outdoor
                    ? ((x >= 0x208A || (x < 0x1D80 ? x > 0x1C0A : y < 0xF2)) ? Boolean.TRUE : null)
                    : (x < 0x1D80 ? (x <= 0x1BF6 ? Boolean.FALSE : null)
                    : (y > 0x10E && x < 0x2076 ? Boolean.FALSE : null));
            case 5 -> (x >= 0x2100 && x <= 0x2600) ? null
                    : (!outdoor ? (y < 0x172 ? Boolean.TRUE : null) : (y >= 0x18E ? Boolean.FALSE : null));
            case 6 -> !outdoor ? (y > 0x70E ? Boolean.TRUE : null) : (y <= 0x6F2 ? Boolean.FALSE : null);
            default -> null;
        };
    }

    public void updateAct1BackgroundEvent(int x, int y, boolean dying) {
        requireAct1();
        // FBZ1_BackgroundEvent dispatches by Events_routine_bg. Only
        // FBZ1BGE_Normal reads Events_fg_5; the four staged redraw routines
        // execute their own draw/deform tail and leave the signal pending.
        if (backgroundRedrawDirection != RedrawDirection.NONE) {
            checkBackgroundChange(x, y);
            advanceBackgroundRedraw();
            drawNormalBackgroundRowIfCrossed();
            finishPlaneBFrame();
            return;
        }

        if (eventsFg5) {
            eventsFg5 = false;
            if (hasRuntime()) {
                applyAct1ToAct2Reload();
                levelManager().recomputeParallaxOnlyForCurrentFrame();
            }

            // ROM continues unconditionally through Reset_TileOffsetPositionActual,
            // FBZ_Deform, Reset_TileOffsetPositionEff and FBZ1BGE_GoDeform after
            // Load_Level. In particular, the later dying check is not reached.
            drawNormalBackgroundRowIfCrossed();
            finishPlaneBFrame();
            if (hasRuntime()) {
                // This is the first legal post-transition rewind state: the
                // synchronous reload and the complete native event tail above
                // have both finished.
                levelManager().markSynchronousSeamlessTransitionBoundary();
            }
            return;
        }

        if (dying) return;
        checkBackgroundChange(x, y);
        if (backgroundRedrawDirection != RedrawDirection.NONE) advanceBackgroundRedraw();
        drawNormalBackgroundRowIfCrossed();
        finishPlaneBFrame();
    }

    private void applyAct1ToAct2Reload() {
        var cam = camera();
        SeamlessLevelTransitionRequest request = SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(0x04, 1)
                .preserveMusic(true)
                .preserveLevelGamestate(true)
                .preserveEndOfLevelState(true)
                .preserveOffsetCameraPosition(true)
                .playerOffset(ACT_TRANSITION_WORLD_OFFSET_X, 0)
                .cameraOffset(ACT_TRANSITION_WORLD_OFFSET_X, 0)
                .postTransitionMinX((int) cam.getMinX() + ACT_TRANSITION_WORLD_OFFSET_X)
                .postTransitionMaxX((int) cam.getMaxX() + ACT_TRANSITION_WORLD_OFFSET_X)
                .postTransitionMinY((int) cam.getMinY())
                .postTransitionMaxY((int) cam.getMaxY())
                .postTransitionMinXTarget((int) cam.getMinXTarget())
                .postTransitionMaxXTarget((int) cam.getMaxXTarget())
                .postTransitionMinYTarget((int) cam.getMinYTarget())
                .postTransitionMaxYTarget((int) cam.getMaxYTarget())
                .objectSurvivalPolicy(
                        SeamlessLevelTransitionRequest.ObjectSurvivalPolicy.ALL_LIVE_SST)
                .romWorldObjectOffsetRange(FIRST_ROM_WORLD_OFFSET_SLOT,
                        LAST_ROM_WORLD_OFFSET_SLOT_EXCLUSIVE)
                .preserveCheckpointUntilResults(true)
                .omitSecondaryLevelPlc(true)
                .suppressLevelLoadRewindBoundary(true)
                .deferRingInitializationToLevelUpdate(true)
                .mutationKey(S3kSeamlessMutationExecutor.MUTATION_FBZ1_POST_RELOAD_ACT2)
                .build();

        // ROM Clear_Switches occurs before Load_Level and clears both the
        // trigger bytes and the respawn-table switch ownership.
        Sonic3kLevelTriggerManager.reset();
        levelManager().applySynchronousScreenEventTransition(request);
    }

    private void checkBackgroundChange(int x, int y) {
        int region = foregroundLayoutRegion / 4;
        RedrawDirection direction = null;
        boolean goOutdoor = backgroundOutdoor;
        switch (region) {
            case 1 -> {
                int threshold = x < 0xB00 ? 0x9C0 : 0x900;
                if (!backgroundOutdoor && y >= threshold) { goOutdoor = true; direction = RedrawDirection.BOTTOM_UP; }
                else if (backgroundOutdoor && y <= threshold) { goOutdoor = false; direction = RedrawDirection.TOP_DOWN; }
            }
            case 2 -> {
                int threshold = x < 0xC80 ? 0x2C0 : 0x240;
                if (!backgroundOutdoor && y <= threshold) { goOutdoor = true; direction = RedrawDirection.TOP_DOWN; }
                else if (backgroundOutdoor && y >= threshold) { goOutdoor = false; direction = RedrawDirection.BOTTOM_UP; }
            }
            case 3 -> {
                int threshold = x < 0x1880 ? 0x9C0 : 0x940;
                if (!backgroundOutdoor && y >= threshold) { goOutdoor = true; direction = RedrawDirection.BOTTOM_UP; }
                else if (backgroundOutdoor && y <= threshold) { goOutdoor = false; direction = RedrawDirection.TOP_DOWN; }
            }
            case 4 -> {
                if (y >= 0x100) {
                    if (!backgroundOutdoor && y <= 0x1C0) { goOutdoor = true; direction = RedrawDirection.TOP_DOWN; }
                    else if (backgroundOutdoor && y >= 0x1C0) { goOutdoor = false; direction = RedrawDirection.BOTTOM_UP; }
                } else {
                    if (!backgroundOutdoor && x >= 0x1B00) { goOutdoor = true; direction = RedrawDirection.RIGHT_TO_LEFT; }
                    else if (backgroundOutdoor && x <= 0x1B00) { goOutdoor = false; direction = RedrawDirection.LEFT_TO_RIGHT; }
                }
            }
            case 5 -> {
                if (!backgroundOutdoor && y <= 0x240) { goOutdoor = true; direction = RedrawDirection.TOP_DOWN; }
                else if (backgroundOutdoor && y >= 0x240) { goOutdoor = false; direction = RedrawDirection.BOTTOM_UP; }
            }
            case 6 -> {
                if (!backgroundOutdoor && y >= 0x640) { goOutdoor = true; direction = RedrawDirection.BOTTOM_UP; }
                else if (backgroundOutdoor && y <= 0x640) { goOutdoor = false; direction = RedrawDirection.TOP_DOWN; }
            }
        }
        if (direction != null && goOutdoor != backgroundOutdoor) startBackgroundChange(goOutdoor, direction);
    }

    private void startBackgroundChange(boolean outdoor, RedrawDirection direction) {
        backgroundOutdoor = outdoor;
        deformMode = outdoor ? DeformMode.OUTDOOR : DeformMode.INDOOR;
        paletteVariant = outdoor ? PaletteVariant.OUTDOOR : PaletteVariant.INDOOR;
        paletteTarget = PaletteTarget.NORMAL;
        backgroundRedrawDirection = direction;
        backgroundRedrawStage = switch (direction) {
            case TOP_DOWN -> 4; case BOTTOM_UP -> 8; case LEFT_TO_RIGHT -> 12; case RIGHT_TO_LEFT -> 16; default -> 0;
        };
        backgroundRedrawProgress = 0;
        backgroundRedrawRowCount = direction == RedrawDirection.TOP_DOWN
                || direction == RedrawDirection.BOTTOM_UP ? 0x0F : 0x1F;
        backgroundRedrawVerticalAnchor = effectiveBackgroundY() & 0xFF0;
        submitAct1PaletteOwnership();
    }

    private void advanceBackgroundRedraw() {
        var direction = switch (backgroundRedrawDirection) {
            case TOP_DOWN -> StagedBackgroundPlaneRedrawController.Direction.TOP_DOWN;
            case BOTTOM_UP -> StagedBackgroundPlaneRedrawController.Direction.BOTTOM_UP;
            case LEFT_TO_RIGHT -> StagedBackgroundPlaneRedrawController.Direction.LEFT_TO_RIGHT;
            case RIGHT_TO_LEFT -> StagedBackgroundPlaneRedrawController.Direction.RIGHT_TO_LEFT;
            case NONE -> throw new IllegalStateException("missing FBZ redraw direction");
        };
        int sourceOffset = backgroundOutdoor ? 0x200 : 0;
        boolean horizontal = direction == StagedBackgroundPlaneRedrawController.Direction.LEFT_TO_RIGHT
                || direction == StagedBackgroundPlaneRedrawController.Direction.RIGHT_TO_LEFT;
        int redrawY = horizontal ? effectiveBackgroundY() : backgroundRedrawVerticalAnchor;
        if (hasRuntime()) {
            backgroundRedrawPosition = planeBRedraw.step(direction, backgroundRedrawProgress,
                    sourceOffset, redrawY);
        } else {
            backgroundRedrawPosition = redrawPosition(direction, backgroundRedrawProgress,
                    backgroundRedrawVerticalAnchor);
        }
        backgroundRedrawRowCount -= horizontal ? 2 : 1;
        backgroundRedrawProgress++;
        if (backgroundRedrawRowCount < 0) {
            backgroundRedrawProgress = 0;
            backgroundRedrawRowCount = -1;
            backgroundRedrawStage = 0;
            backgroundRedrawDirection = RedrawDirection.NONE;
        }
    }

    private static int redrawPosition(StagedBackgroundPlaneRedrawController.Direction direction,
                                      int progress, int anchor) {
        return switch (direction) {
            case TOP_DOWN -> (anchor + progress * 0x10) & 0xFF0;
            case BOTTOM_UP -> (anchor + 0xF0 - progress * 0x10) & 0xFF0;
            case LEFT_TO_RIGHT -> progress * 0x20;
            case RIGHT_TO_LEFT -> 0x3F0 - progress * 0x20;
        };
    }

    private int effectiveBackgroundY() {
        if (backgroundOutdoor) return 0x16 + outdoorBobOffset;
        if (!hasRuntime()) return 0;
        int y = (short) camera().getY();
        y >>= 1;
        return y - (y >> 5);
    }

    private void drawNormalBackgroundRowIfCrossed() {
        if (!hasRuntime()) return;
        int rounded = effectiveBackgroundY() & 0xFF0;
        if (lastRoundedBackgroundY == Integer.MIN_VALUE) {
            lastRoundedBackgroundY = rounded;
            return;
        }
        if (rounded == lastRoundedBackgroundY) return;
        for (int position : normalDrawRowPositions(lastRoundedBackgroundY, rounded)) {
            int dest = 0xE000 + ((position >>> 3) & 0x1F) * 0x80;
            retainedPlaneOwned = true;
            planeBFrameTouched |= levelManager().copyBackgroundTileRowFromWorldToVdpPlane(
                    backgroundOutdoor ? 0x200 : 0, position, dest, 0x21);
        }
        lastRoundedBackgroundY = rounded;
    }

    /** Exact Draw_TileRow position selection, including the ROM's optional second 16px update. */
    public static int[] normalDrawRowPositions(int oldRounded, int newRounded) {
        int signedDelta = (short) (oldRounded - newRounded);
        int position = signedDelta < 0 ? oldRounded + 0xF0 : newRounded;
        position &= 0xFF0;
        int maskedDistance = Math.abs(signedDelta) & 0x30;
        return maskedDistance == 0x10
                ? new int[]{position}
                : new int[]{position, (position + 0x10) & 0xFF0};
    }

    private void finishPlaneBFrame() {
        if (planeBFrameTouched && hasRuntime()) levelManager().uploadBackgroundTilemap();
        planeBFrameTouched = false;
    }

    /** Reinstalls the exact event-owned Plane-B image captured by the FBZ runtime codec. */
    public void reconcileRetainedPlaneState() {
        if (!hasRuntime()) return;
        if (retainedPlaneSnapshot.length != 0) {
            levelManager().restoreBackgroundVdpPlane(retainedPlaneSnapshot);
            retainedPlaneSnapshot = new byte[0];
            retainedPlaneOwned = true;
            if (act == 0) submitAct1PaletteOwnership();
            return;
        }
        // Act 1 predates exact retained-plane snapshotting and retains its
        // deterministic seed/replay fallback for compatibility with an empty payload.
        if (act != 0) return;
        int targetOffset = backgroundOutdoor ? 0x200 : 0;
        int seedOffset = backgroundRedrawDirection == RedrawDirection.NONE ? targetOffset : targetOffset ^ 0x200;
        retainedPlaneOwned = true;
        levelManager().seedBackgroundVdpPlaneFromWorld(seedOffset);
        if (backgroundRedrawDirection != RedrawDirection.NONE && backgroundRedrawProgress > 0) {
            var direction = switch (backgroundRedrawDirection) {
                case TOP_DOWN -> StagedBackgroundPlaneRedrawController.Direction.TOP_DOWN;
                case BOTTOM_UP -> StagedBackgroundPlaneRedrawController.Direction.BOTTOM_UP;
                case LEFT_TO_RIGHT -> StagedBackgroundPlaneRedrawController.Direction.LEFT_TO_RIGHT;
                case RIGHT_TO_LEFT -> StagedBackgroundPlaneRedrawController.Direction.RIGHT_TO_LEFT;
                case NONE -> throw new IllegalStateException();
            };
            int replayY = direction == StagedBackgroundPlaneRedrawController.Direction.LEFT_TO_RIGHT
                    || direction == StagedBackgroundPlaneRedrawController.Direction.RIGHT_TO_LEFT
                    ? effectiveBackgroundY() : backgroundRedrawVerticalAnchor;
            planeBRedraw.replay(direction, backgroundRedrawProgress, targetOffset, replayY);
            finishPlaneBFrame();
        }
        submitAct1PaletteOwnership();
    }

    /** Compatibility entry point retained for Act 1 callers and focused tests. */
    public void reconcileAct1State() {
        if (act == 0) reconcileRetainedPlaneState();
    }

    private void submitAct1PaletteOwnership() {
        if (paletteTarget == PaletteTarget.NONE || !hasRuntime()) return;
        try {
            byte[] patch = rom().readBytes(paletteVariant == PaletteVariant.OUTDOOR
                    ? FBZ_BG_OUTDOOR_PALETTE_ADDR : FBZ_BG_INDOOR_PALETTE_ADDR, 16);
            if (paletteTarget == PaletteTarget.TARGET) {
                if (paletteRegistryOrNull() != null) {
                    paletteRegistryOrNull().applyTargetPatch(FbzPaletteFoundation.OWNER,
                            FbzPaletteFoundation.PALETTE_LINE_INDEX,
                            FbzPaletteFoundation.BACKGROUND_FIRST_COLOR, patch);
                }
                // The engine's level-entry fade is a black overlay rather than the ROM's
                // per-color PaletteFadeFrom loop. Materialize the same target into Normal
                // while the overlay is opaque so the revealed post-fade frame is identical.
                Level level = levelManager().getCurrentLevel();
                S3kPaletteWriteSupport.applyContiguousPatch(paletteRegistryOrNull(), level, graphics(),
                        FbzPaletteFoundation.OWNER, S3kPaletteOwners.PRIORITY_ZONE_EVENT,
                        FbzPaletteFoundation.PALETTE_LINE_INDEX,
                        FbzPaletteFoundation.BACKGROUND_FIRST_COLOR, patch);
                S3kPaletteWriteSupport.resolvePendingWritesNow(
                        paletteRegistryOrNull(), level, graphics());
            } else {
                Level level = levelManager().getCurrentLevel();
                S3kPaletteWriteSupport.applyContiguousPatch(paletteRegistryOrNull(), level, graphics(),
                        FbzPaletteFoundation.OWNER, S3kPaletteOwners.PRIORITY_ZONE_EVENT,
                        FbzPaletteFoundation.PALETTE_LINE_INDEX,
                        FbzPaletteFoundation.BACKGROUND_FIRST_COLOR, patch);
            }
        } catch (Exception failure) {
            if (RomManager.isConfiguredRomMissing(failure)) {
                LOG.fine(() -> "Skipped FBZ palette patch: " + failure.getMessage());
            } else {
                throw new IllegalStateException("Failed FBZ palette patch", failure);
            }
        }
    }

    private void spawnOutdoorMotionController() {
        if (outdoorMotionAllocationAttempted) return;
        outdoorMotionAllocationAttempted = true;
        outdoorMotionSpawned = spawnObject(FbzOutdoorBgMotionObjectInstance::new) != null;
    }

    private Act2InitializationEffects liveAct2InitializationEffects() {
        return new Act2InitializationEffects() {
            @Override public void resetActualTileOffsets() {
                levelManager().resetTileOffsetPositionActualForFullRefresh();
            }
            @Override public void refreshPlaneFull() {
                retainedPlaneOwned = true;
                levelManager().refreshFullTilemapPlanesFromCurrentLayout(0);
            }
            @Override public void copyTopBackgroundLines() {
                applyRuntimeAct2TopBackgroundLineCopy();
            }
            @Override public void deformNormalBackground() {
                // The engine's scroll handler composes FBZ_Deform and ApplyDeformation
                // into one buffer publication; recomputing here materializes that output.
                levelManager().recomputeParallaxOnlyForCurrentFrame();
            }
            @Override public void resetEffectiveTileOffsets() {
                levelManager().resetTileOffsetPositionEffectiveForFullRefresh();
            }
            @Override public void applyDeformation() {
                // Already published by recomputeParallaxOnlyForCurrentFrame above.
            }
        };
    }

    private void applyRuntimeAct2TopBackgroundLineCopy() {
        if (!hasRuntime()) return;
        Level level = levelManager().getCurrentLevel();
        if (level == null || level.getMap() == null) return;
        LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                levelManager()::applyMutationEffects);
        zoneLayoutMutationPipeline().applyImmediately(
                ctx -> copyAct2TopBackgroundLine(level.getMap(), ctx.surface()), context);
    }

    /** ROM pointer copy {@code (a3) -> $70/$74/$78/$7C(a3)} represented in decoded map rows. */
    public static MutationEffects copyAct2TopBackgroundLine(
            int[][][] layout, LevelMutationSurface surface) {
        Objects.requireNonNull(layout, "layout");
        if (layout.length < 2 || layout[1].length < 32) {
            throw new IllegalArgumentException("FBZ2 background layout requires 32 rows");
        }
        int width = layout[1][0].length;
        return copyAct2TopBackgroundLine((x, y) -> layout[1][y][x], width, surface);
    }

    private static MutationEffects copyAct2TopBackgroundLine(Map map, LevelMutationSurface surface) {
        if (map.getLayerCount() < 2 || map.getHeight() < 32) {
            throw new IllegalArgumentException("FBZ2 background map requires 2 layers and 32 rows");
        }
        return copyAct2TopBackgroundLine((x, y) -> map.getValue(1, x, y) & 0xFF,
                map.getWidth(), surface);
    }

    private static MutationEffects copyAct2TopBackgroundLine(
            BackgroundCellReader source, int width, LevelMutationSurface surface) {
        int[] topLine = new int[width];
        for (int x = 0; x < width; x++) topLine[x] = source.read(x, 0);
        for (int y = 28; y <= 31; y++) {
            for (int x = 0; x < width; x++) {
                surface.setBlockInMap(1, x, y, topLine[x]);
            }
        }
        return width == 0 ? MutationEffects.NONE : MutationEffects.redrawAllTilemaps();
    }

    @FunctionalInterface private interface BackgroundCellReader { int read(int x, int y); }

    private void applyRuntimeCopies(List<LayoutCopy> copies) {
        if (copies.isEmpty() || !hasRuntime()) return;
        Level level = levelManager().getCurrentLevel();
        if (level == null || level.getMap() == null) return;
        Map map = level.getMap();
        LayoutMutationContext context = new LayoutMutationContext(LevelMutationSurface.forLevel(level),
                levelManager()::applyMutationEffects);
        zoneLayoutMutationPipeline().applyImmediately(ctx -> applyLayoutCopies(map, copies, ctx.surface()), context);
    }

    public static MutationEffects applyLayoutCopies(int[][] planeA, List<LayoutCopy> copies, LevelMutationSurface surface) {
        return applyLayoutCopies((layer, x, y) -> planeA[y][x], copies, surface);
    }

    private static MutationEffects applyLayoutCopies(Map map, List<LayoutCopy> copies, LevelMutationSurface surface) {
        return applyLayoutCopies((layer, x, y) -> map.getValue(layer, x, y) & 0xFF, copies, surface);
    }

    private static MutationEffects applyLayoutCopies(CellReader source, List<LayoutCopy> copies, LevelMutationSurface surface) {
        for (LayoutCopy copy : copies) {
            int[][] snapshot = new int[copy.height()][copy.width()];
            for (int row = 0; row < copy.height(); row++) for (int col = 0; col < copy.width(); col++)
                snapshot[row][col] = source.read(FG_LAYER, copy.sourceX() + col, copy.sourceY() + row);
            for (int row = 0; row < copy.height(); row++) for (int col = 0; col < copy.width(); col++)
                surface.setBlockInMap(FG_LAYER, copy.destX() + col, copy.destY() + row, snapshot[row][col]);
        }
        return copies.isEmpty() ? MutationEffects.NONE : MutationEffects.redraw();
    }

    @FunctionalInterface private interface CellReader { int read(int layer, int x, int y); }

    public static List<LayoutCopy> act1LayoutCopies(int region, boolean outdoor) {
        return switch (region) {
            case 1 -> outdoor ? List.of(new LayoutCopy(96, 6, 12, 18, 4, 6), new LayoutCopy(100, 5, 26, 18, 6, 4))
                    : List.of(new LayoutCopy(96, 0, 12, 18, 4, 6), new LayoutCopy(100, 0, 26, 18, 6, 4));
            case 2 -> outdoor ? List.of(new LayoutCopy(108, 5, 14, 2, 10, 4), new LayoutCopy(118, 4, 28, 3, 12, 3))
                    : List.of(new LayoutCopy(108, 0, 14, 2, 10, 4), new LayoutCopy(118, 0, 28, 3, 12, 3));
            case 3 -> outdoor ? List.of(new LayoutCopy(96, 17, 40, 18, 6, 4), new LayoutCopy(102, 15, 52, 18, 6, 4))
                    : List.of(new LayoutCopy(96, 13, 40, 18, 6, 4), new LayoutCopy(102, 10, 52, 18, 6, 4));
            case 4, 5 -> outdoor ? List.of(new LayoutCopy(110, 15, 54, 0, 8, 4), new LayoutCopy(118, 15, 62, 0, 18, 5))
                    : List.of(new LayoutCopy(110, 10, 54, 0, 8, 4), new LayoutCopy(118, 10, 62, 0, 18, 5));
            case 6 -> List.of(new LayoutCopy(0, outdoor ? 21 : 18, 0, 13, 5, 3));
            default -> throw new IllegalArgumentException("FBZ1 layout region: " + region);
        };
    }

    private static boolean contains(int[] range, int x, int y) {
        return x >= range[0] && x <= range[1] && y >= range[2] && y <= range[3];
    }

    private void requireAct1() { if (act != 0) throw new IllegalArgumentException("Act 1 operation in FBZ Act 2"); }

    public int getAct() { return act; }
    public int getForegroundLayoutRegion() { return foregroundLayoutRegion; }
    public void setForegroundLayoutRegion(int value) { validateLayoutRegion(act, value); foregroundLayoutRegion = value; }
    public boolean isForegroundOutdoor() { return foregroundOutdoor; }
    public void setForegroundOutdoor(boolean value) { foregroundOutdoor = value; }
    public boolean isBackgroundOutdoor() { return backgroundOutdoor; }
    public void setBackgroundOutdoor(boolean value) { backgroundOutdoor = value; }
    public int getBackgroundRedrawStage() { return backgroundRedrawStage; }
    public RedrawDirection getBackgroundRedrawDirection() { return backgroundRedrawDirection; }
    public int getBackgroundRedrawProgress() { return backgroundRedrawProgress; }
    public int getBackgroundRedrawPosition() { return backgroundRedrawPosition; }
    public int getBackgroundRedrawRowCount() { return backgroundRedrawRowCount; }
    public int getBackgroundRedrawVerticalAnchor() { return backgroundRedrawVerticalAnchor; }
    public int getLastRoundedBackgroundY() { return lastRoundedBackgroundY; }
    public DeformMode getDeformMode() { return deformMode; }
    public PaletteVariant getPaletteVariant() { return paletteVariant; }
    public PaletteTarget getPaletteTarget() { return paletteTarget; }
    public boolean isAct1ScreenInitialized() { return act1ScreenInitialized; }
    public boolean isAct1BackgroundInitialized() { return act1BackgroundInitialized; }
    public boolean isOutdoorMotionAllocationAttempted() { return outdoorMotionAllocationAttempted; }
    public boolean isOutdoorMotionSpawned() { return outdoorMotionSpawned; }
    public byte[] captureRetainedPlaneSnapshot() {
        if (retainedPlaneSnapshot.length != 0) return retainedPlaneSnapshot.clone();
        if (!retainedPlaneOwned) return new byte[0];
        return hasRuntime() ? levelManager().captureBackgroundVdpPlane() : new byte[0];
    }
    public void restoreRetainedPlaneSnapshot(byte[] snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.length != 0 && snapshot.length != 64 * 32 * 4) {
            throw new IllegalArgumentException("retained Plane-B snapshot must be empty or 8192 bytes");
        }
        retainedPlaneSnapshot = snapshot.clone();
        retainedPlaneOwned = snapshot.length != 0;
    }
    public void restoreOutdoorMotionAllocationState(boolean attempted, boolean spawned) {
        if (spawned && !attempted) throw new IllegalArgumentException("spawned outdoor motion requires allocation attempt");
        outdoorMotionAllocationAttempted = attempted;
        outdoorMotionSpawned = spawned;
    }
    public void setBackgroundRedraw(int stage, RedrawDirection direction) {
        validateFourStepStage("background redraw", stage, 16);
        backgroundRedrawStage = stage;
        backgroundRedrawDirection = Objects.requireNonNull(direction, "direction");
    }
    public void restoreAct1EventState(int redrawProgress, int redrawPosition, int redrawRowCount,
                                     int redrawVerticalAnchor, int lastRoundedBackgroundY, DeformMode deformMode,
                                     PaletteVariant paletteVariant, PaletteTarget paletteTarget,
                                     boolean screenInitialized, boolean backgroundInitialized,
                                     boolean motionAllocationAttempted, boolean motionSpawned) {
        if (redrawProgress < 0 || redrawProgress > 15) throw new IllegalArgumentException("redraw progress: " + redrawProgress);
        this.backgroundRedrawProgress = redrawProgress;
        this.backgroundRedrawPosition = redrawPosition;
        this.backgroundRedrawRowCount = redrawRowCount;
        this.backgroundRedrawVerticalAnchor = redrawVerticalAnchor;
        this.lastRoundedBackgroundY = lastRoundedBackgroundY;
        this.deformMode = Objects.requireNonNull(deformMode, "deformMode");
        this.paletteVariant = Objects.requireNonNull(paletteVariant, "paletteVariant");
        this.paletteTarget = Objects.requireNonNull(paletteTarget, "paletteTarget");
        this.act1ScreenInitialized = screenInitialized;
        this.act1BackgroundInitialized = backgroundInitialized;
        restoreOutdoorMotionAllocationState(motionAllocationAttempted, motionSpawned);
    }
    public int getOutdoorBobOffset() { return outdoorBobOffset; }
    public void setOutdoorBobOffset(int value) { outdoorBobOffset = value; }
    public int getHScrollAccumulator() { return hScrollAccumulator; }
    public boolean isHScrollAccumulatorSampled() { return hScrollAccumulatorSampled; }
    public int getHScrollAccumulatorLastFrame() { return hScrollAccumulatorLastFrame; }
    public int getHScrollAccumulatorLastRead() { return hScrollAccumulatorLastRead; }

    /** ROM FBZ_Deform outdoor path: read old HScroll+$1FC, then add $E00 once. */
    public int sampleOutdoorHScrollAccumulator(int frameCounter) {
        return sampleHScrollAccumulator(frameCounter, 0xE00);
    }

    /** ROM FBZ2_CloudDeform: read old HScroll+$1FC >> 3, then add $8000 once. */
    public int sampleBossHScrollAccumulator(int frameCounter) {
        return sampleHScrollAccumulator(frameCounter, 0x8000) >> 3;
    }

    private int sampleHScrollAccumulator(int frameCounter, int increment) {
        if (hScrollAccumulatorSampled && hScrollAccumulatorLastFrame == frameCounter) {
            return hScrollAccumulatorLastRead;
        }
        hScrollAccumulatorLastRead = hScrollAccumulator;
        hScrollAccumulator += increment; // Java int overflow is the ROM's 32-bit wrap.
        hScrollAccumulatorLastFrame = frameCounter;
        hScrollAccumulatorSampled = true;
        return hScrollAccumulatorLastRead;
    }

    public void restoreHScrollAccumulatorState(int value, boolean sampled, int lastFrame, int lastRead) {
        hScrollAccumulator = value;
        hScrollAccumulatorSampled = sampled;
        hScrollAccumulatorLastFrame = lastFrame;
        hScrollAccumulatorLastRead = lastRead;
    }
    public MagneticPolarity getMagneticPolarity() { return magneticPolarity; }
    public int getMagneticTimerPhase() { return magneticTimerPhase; }
    public boolean isMagneticEdgeObserved() { return magneticEdgeObserved; }
    public int getMagneticLastEdgeFrame() { return magneticLastEdgeFrame; }
    public void setMagneticState(MagneticPolarity polarity, int phase) {
        if (phase < 0 || phase > 0xFF) throw new IllegalArgumentException("magnetic timer phase: " + phase);
        magneticPolarity = Objects.requireNonNull(polarity, "polarity");
        magneticTimerPhase = phase;
    }

    /** ROM AnPal_FBZ, guarded so a recompute of one frame cannot toggle twice. */
    public void advanceMagneticPhase(int frameCounter) {
        advanceMagneticPhase(frameCounter, false);
    }

    /**
     * ROM {@code Animate_Palette}: a nonzero {@code Palette_fade_timer} skips
     * AnPal entirely, so a qualifying edge is lost rather than deferred.
     */
    public void advanceMagneticPhase(int frameCounter, boolean paletteFadeActive) {
        int phase = frameCounter & 0xFF;
        if (!paletteFadeActive && phase == 0
                && (!magneticEdgeObserved || magneticLastEdgeFrame != frameCounter)) {
            magneticPolarity = magneticPolarity == MagneticPolarity.ACTIVE
                    ? MagneticPolarity.INACTIVE : MagneticPolarity.ACTIVE;
            magneticEdgeObserved = true;
            magneticLastEdgeFrame = frameCounter;
        }
        magneticTimerPhase = phase;
    }

    public void restoreMagneticState(MagneticPolarity polarity, int phase,
                                     boolean edgeObserved, int lastEdgeFrame) {
        setMagneticState(polarity, phase);
        magneticEdgeObserved = edgeObserved;
        magneticLastEdgeFrame = lastEdgeFrame;
    }
    public boolean getPendulumOrientationBit(int layoutIndex) {
        return layoutIndex >= 0 && pendulumOrientationBits.get(layoutIndex);
    }
    public void setPendulumOrientationBit(int layoutIndex, boolean value) {
        if (layoutIndex < 0 || layoutIndex >= 512) return;
        pendulumOrientationBits.set(layoutIndex, value);
    }
    public long[] capturePendulumOrientationBits() {
        return Arrays.copyOf(pendulumOrientationBits.toLongArray(), 8);
    }
    public void restorePendulumOrientationBits(long[] words) {
        if (words == null || words.length != 8) throw new IllegalArgumentException("FBZ pendulum orientation words");
        pendulumOrientationBits.clear();
        pendulumOrientationBits.or(BitSet.valueOf(words));
    }
    public int getAct2ForegroundStage() { return act2ForegroundStage; }
    public void setAct2ForegroundStage(int stage) {
        requireAct2("foreground stage"); validateFourStepStage("Act 2 foreground", stage, 12); act2ForegroundStage = stage;
    }
    public int getBossBackgroundStage() { return bossBackgroundStage; }
    public int getBossBackgroundOffsetX() { return bossBackgroundOffsetX; }
    public int getBossBackgroundOffsetY() { return bossBackgroundOffsetY; }
    public void setBossBackgroundState(int stage, int x, int y) {
        requireAct2("boss background state"); validateFourStepStage("boss background", stage, 16);
        bossBackgroundStage = stage; bossBackgroundOffsetX = x; bossBackgroundOffsetY = y;
    }
    public void setBossBackgroundOffsets(int x, int y) {
        requireAct2("boss background offsets"); bossBackgroundOffsetX = x; bossBackgroundOffsetY = y;
    }
    public boolean isBossLoadPositionAdjustmentPending() { return bossLoadPositionAdjustmentPending; }
    public void setBossLoadPositionAdjustmentPending(boolean value) {
        requireAct2("boss position adjustment"); bossLoadPositionAdjustmentPending = value;
    }
    public ObjectRefId getCloudRewindId(int index) { return cloudRewindIds[checkedCloudIndex(index)]; }
    public List<ObjectRefId> getCloudRewindIds() {
        return Collections.unmodifiableList(Arrays.asList(cloudRewindIds.clone()));
    }
    public void setCloudRewindId(int index, ObjectRefId id) {
        requireAct2("cloud identity");
        if (cloudCleanupTerminal) throw new IllegalStateException("FBZ clouds are terminally cleaned up");
        cloudRewindIds[checkedCloudIndex(index)] = id;
    }
    public boolean isCloudCleanupTerminal() { return cloudCleanupTerminal; }
    public void setCloudCleanupTerminal(boolean value) {
        requireAct2("cloud cleanup terminal");
        if (cloudCleanupTerminal && !value) {
            throw new IllegalStateException("FBZ terminal cloud cleanup is monotonic outside rewind restore");
        }
        if (value) Arrays.fill(cloudRewindIds, null);
        cloudCleanupTerminal = value;
    }

    public void restoreCloudState(List<ObjectRefId> ids, boolean terminal) {
        requireAct2("cloud restore");
        Objects.requireNonNull(ids, "ids");
        if (ids.size() != cloudRewindIds.length) throw new IllegalArgumentException("FBZ cloud restore requires ten IDs");
        if (terminal && ids.stream().anyMatch(Objects::nonNull))
            throw new IllegalArgumentException("terminal FBZ cloud restore contains identities");
        ids.toArray(cloudRewindIds);
        cloudCleanupTerminal = terminal;
    }

    public void reconcileCloudsAfterObjectRestore(FbzCloudIdentityResolver resolver,
                                                   FbzCloudRecreationBatchFactory batchFactory) {
        Objects.requireNonNull(resolver, "resolver");
        if (cloudCleanupTerminal) return;
        ObjectRefId[] stagedIds = cloudRewindIds.clone();
        List<FbzCloudRecreationRequest> missing = new java.util.ArrayList<>();
        for (int i = 0; i < stagedIds.length; i++) {
            ObjectRefId id = stagedIds[i];
            if (id != null && !resolver.isLive(id)) missing.add(new FbzCloudRecreationRequest(i, id));
        }
        if (missing.isEmpty()) return;
        if (batchFactory == null) throw new IllegalStateException("Missing FBZ cloud recreation batch factory");
        FbzCloudRecreationBatch batch = Objects.requireNonNull(batchFactory.begin(List.copyOf(missing)), "batch");
        try {
            List<ObjectRefId> rebound = Objects.requireNonNull(batch.recreateAll(), "rebound IDs");
            if (rebound.size() != missing.size()) throw new IllegalStateException("FBZ cloud batch result count mismatch");
            for (int i = 0; i < missing.size(); i++) {
                FbzCloudRecreationRequest request = missing.get(i);
                ObjectRefId id = rebound.get(i);
                if (id == null) throw new IllegalStateException("FBZ cloud batch returned null identity");
                if (!request.stableId().equals(id)) throw new IllegalStateException("FBZ cloud stable identity changed");
            }
            batch.commit();
            resolver.refresh();
            for (int i = 0; i < missing.size(); i++) {
                FbzCloudRecreationRequest request = missing.get(i);
                ObjectRefId id = rebound.get(i);
                if (!resolver.isLive(id)) throw new IllegalStateException("Recreated FBZ cloud identity is not live: " + id);
                stagedIds[request.cloudIndex()] = id;
            }
        } catch (RuntimeException | Error failure) {
            try {
                batch.rollback();
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        System.arraycopy(stagedIds, 0, cloudRewindIds, 0, stagedIds.length);
    }
    public PlaneAssignmentMode getPlaneAssignmentMode() { return planeAssignmentMode; }
    public CollisionMode getCollisionMode() { return collisionMode; }
    public int getCollisionCameraDiffX() { return collisionCameraDiffX; }
    public int getCollisionCameraDiffY() { return collisionCameraDiffY; }
    public void setPlaneAssignmentMode(PlaneAssignmentMode plane) {
        requireAct2("plane assignment mode");
        planeAssignmentMode = Objects.requireNonNull(plane, "plane");
    }
    public void setCollisionMode(CollisionMode collision, int diffX, int diffY) {
        requireAct2("collision mode");
        collision = Objects.requireNonNull(collision, "collision");
        if (collision == CollisionMode.FOREGROUND_ONLY && (diffX != 0 || diffY != 0)) {
            throw new IllegalArgumentException("foreground-only FBZ collision cannot retain background differences");
        }
        collisionMode = collision;
        collisionCameraDiffX = diffX;
        collisionCameraDiffY = diffY;
    }
    public boolean isScreenShakeActive() { return screenShakeActive; }
    public int getScreenShakeOffset() { return screenShakeOffset; }
    public int getScreenShakePhase() { return screenShakePhase; }
    public void setScreenShakeState(boolean active, int offset, int phase) {
        requireAct2("screen shake");
        if (phase < 0) throw new IllegalArgumentException("screen shake phase: " + phase);
        screenShakeActive = active; screenShakeOffset = offset; screenShakePhase = phase;
    }
    public boolean isEventsFg5() { return eventsFg5; }
    public void setEventsFg5(boolean value) { eventsFg5 = value; }

    @Override public int getDynamicResizeRoutine() { return 0; }
    @Override public void setDynamicResizeRoutine(int routine) { /* FBZ has no Dynamic_resize_routine authority. */ }

    private void requireAct2(String field) {
        if (act != 1) throw new IllegalArgumentException(field + " is invalid in FBZ Act " + (act + 1));
    }
    private static void validateLayoutRegion(int act, int value) {
        int max = act == 0 ? 24 : 4;
        validateFourStepStage("foreground layout region", value, max);
    }
    private static void validateFourStepStage(String name, int value, int max) {
        if (value < 0 || value > max || (value & 3) != 0) throw new IllegalArgumentException(name + " stage: " + value);
    }
    private static int checkedCloudIndex(int index) {
        if (index < 0 || index >= Sonic3kConstants.FBZ_CLOUD_REWIND_SLOT_COUNT)
            throw new IndexOutOfBoundsException("FBZ cloud index: " + index);
        return index;
    }
}
