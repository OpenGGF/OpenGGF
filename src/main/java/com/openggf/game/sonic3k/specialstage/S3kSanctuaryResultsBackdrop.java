package com.openggf.game.sonic3k.specialstage;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.sonic3k.Sonic3kLevelResourceProfile;
import com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance;
import com.openggf.game.sonic3k.objects.HPZSuperEmeraldReturnEffectObjectInstance;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.level.objects.ObjectManager;
import com.openggf.game.LevelLoadMode;

import java.util.List;
import java.util.logging.Logger;

/**
 * The Hidden Palace sanctuary rebuilt behind a Super Emerald results screen.
 *
 * <p>ROM {@code SpecialStage_Results} (sonic3k.asm:63121-63201): with
 * {@code SK_special_stage_flag} set it decompresses {@code Layout_HPZ} and the HPZ blocks
 * and art, sets {@code Current_zone_and_act} to $1701 and the camera to ($15A0,$240), runs
 * {@code Load_Sprites}, moves the camera to {@code word_2E398[Current_special_stage_2]} and
 * calls {@code LevelSetup}. The results loop then runs {@code Process_Sprites} and
 * {@code ScreenEvents} each frame without a player (loc_2E24C, :63203-63231) and never
 * reloads object placement.
 *
 * <p>The engine reloads the current $1701 level for the same effect, with its music change
 * suppressed and no title card, then marks the controller so its first pass takes the
 * {@code HPZ_special_stage_completed} branch.
 */
final class S3kSanctuaryResultsBackdrop {
    private static final Logger LOG = Logger.getLogger(S3kSanctuaryResultsBackdrop.class.getName());

    /** {@code move.w #$240,(Camera_Y_pos).w} before {@code Load_Sprites}. */
    static final int START_CAMERA_Y = 0x240;
    /** {@code word_2E398}: results camera X by {@code Current_special_stage_2}. */
    static final int[] STAGE_CAMERA_X = {0x15A0, 0x1540, 0x1600, 0x1500, 0x1640, 0x14B0, 0x1690};

    private final LevelManager level;
    private final HPZSSEntryControlObjectInstance controller;
    private int frameCounter;

    private S3kSanctuaryResultsBackdrop(LevelManager level, HPZSSEntryControlObjectInstance controller) {
        this.level = level;
        this.controller = controller;
    }

    /**
     * Rebuilds the sanctuary when the finished stage was launched from it, or returns
     * {@code null} when no $1701 level is loaded (a stage started without the sanctuary).
     */
    static S3kSanctuaryResultsBackdrop host(int stageIndex, boolean succeeded) {
        LevelManager level = GameServices.levelOrNull();
        if (level == null || level.getCurrentLevel() == null
                || stageIndex < 0 || stageIndex >= STAGE_CAMERA_X.length
                || !Sonic3kLevelResourceProfile.isHpzSanctuary(
                        level.getCurrentZone(), level.getCurrentAct())) {
            return null;
        }
        // SpecialStage_Results plays mus_GotThroughAct itself; the rebuilt level has no music.
        level.setSuppressNextMusicChange(true);
        level.loadCurrentLevel(LevelLoadMode.FULL, false);
        ObjectManager objects = level.getObjectManager();
        HPZSSEntryControlObjectInstance controller = objects == null ? null
                : objects.activeObjectsOfType(HPZSSEntryControlObjectInstance.class)
                .stream().findFirst().orElse(null);
        if (controller == null) {
            LOG.warning("HPZ results backdrop loaded without its $B5 controller");
            return null;
        }
        controller.hostSpecialStageResults(stageIndex, succeeded);
        Camera camera = GameServices.camera();
        camera.setX((short) STAGE_CAMERA_X[stageIndex]);
        camera.setY((short) START_CAMERA_Y);
        S3kSanctuaryResultsBackdrop backdrop = new S3kSanctuaryResultsBackdrop(level, controller);
        backdrop.publishFrame();
        return backdrop;
    }

    /** loc_2E24C's {@code Process_Sprites} for the level objects, with no player slots. */
    void runObjects(S3kSanctuaryResultsPalette palette) {
        PaletteOwnershipRegistry registry = GameServices.paletteOwnershipRegistryOrNull();
        if (registry != null) {
            registry.beginFrame();
        }
        ObjectManager objects = level.getObjectManager();
        if (objects != null) {
            objects.update(GameServices.camera().getX(), null, List.of(), ++frameCounter, false);
        }
        if (palette != null) {
            palette.resolveObjectWrites(registry);
        }
    }

    /**
     * loc_2E24C's {@code ScreenEvents}, {@code Render_Sprites} and the VInt 8 upload after the
     * results object has moved the camera: the level renderer draws the published table and scroll.
     */
    void publishFrame() {
        // ScreenEvents: publish the camera copy and this frame's plane scroll.
        GameServices.camera().captureRenderCopy();
        level.recomputeParallaxOnlyForCurrentFrame();
        LevelSpritePresentation.prepare(level, GameServices.sprites());
        LevelSpritePresentation.publish(level, PlcLifecyclePhase.SPECIAL_STAGE_RESULTS);
    }

    HPZSuperEmeraldReturnEffectObjectInstance spawnStars(int stageIndex, boolean expanding) {
        HPZSuperEmeraldReturnEffectObjectInstance stars =
                new HPZSuperEmeraldReturnEffectObjectInstance(controller, stageIndex, expanding);
        ObjectManager objects = level.getObjectManager();
        if (objects != null) {
            // AllocateObject from the results SST: the stars first run on the next pass.
            objects.addDynamicObject(stars);
        }
        return stars;
    }

    HPZSSEntryControlObjectInstance controller() {
        return controller;
    }

    int cameraX() {
        return GameServices.camera().getX() & 0xFFFF;
    }

    int cameraY() {
        return GameServices.camera().getY() & 0xFFFF;
    }

    void setCameraX(int x) {
        GameServices.camera().setX((short) x);
    }

    void setCameraY(int y) {
        GameServices.camera().setY((short) y);
    }
}
