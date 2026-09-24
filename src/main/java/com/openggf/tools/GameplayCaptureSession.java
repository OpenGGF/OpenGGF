package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.game.GameMode;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.LevelBackdropResultsScreen;
import com.openggf.game.ResultsScreen;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.level.LevelManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * A booted, steppable, renderable gameplay session for arbitrary captures.
 *
 * <p>This is the production boot path ({@link HeadlessGameBoot} + {@link GameLoop#step()})
 * rather than the test fixtures: the fixture forces headless graphics, and the
 * {@code LevelFrameTestStep} render recipe used by the visual tests draws tiles and
 * objects but never the playable sprite. Input arrives per frame as a
 * {@link Bk2FrameInput} through the {@code InputHandler} logical override, so an
 * authored input log and a recorded BK2 drive the session identically.
 *
 * <p>Cross-game donation: the boot's {@code ResetCrossGameFeatures} step deactivates
 * the donor provider, so when a donor is requested the session re-initialises it after
 * boot, re-registers the team and refreshes playable art, then verifies the provider
 * reports active. Originating task: FBZ2 {@code $1DC0} squeeze capture, 2026-09-13.
 */
public final class GameplayCaptureSession implements AutoCloseable {

    /** Height of the logical framebuffer; every supported width pairs with it. */
    public static final int HEIGHT = 224;

    private final HeadlessGameBoot boot;
    private final int width;
    private GameLoop loop;
    private AbstractPlayableSprite player;
    private Bk2FrameInput previousInput;
    private boolean closed;

    public GameplayCaptureSession(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        this.width = settings.width();
        WidescreenAspect aspect = Arrays.stream(WidescreenAspect.values())
                .filter(candidate -> candidate.pixelWidth() == settings.width())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported capture width "
                        + settings.width() + "; supported: " + Arrays.toString(WidescreenAspect.values())));
        this.boot = new HeadlessGameBoot(settings.width(), HEIGHT);
        SonicConfigurationService config = GameServices.configuration();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.setSessionOverride(SonicConfiguration.DISPLAY_WINDOW_AUTOSIZE, false);
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH, settings.width());
        config.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT, HEIGHT);
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setSessionOverride(SonicConfiguration.DEBUG_VIEW_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.EDITOR_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, settings.mainCharacter());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, settings.sidekickCharacter());
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, settings.donorActive());
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, settings.donor());
        if (settings.donorRom() != null) {
            SonicConfiguration donorKey = switch (settings.donor()) {
                case "s1" -> SonicConfiguration.SONIC_1_ROM;
                case "s2" -> SonicConfiguration.SONIC_2_ROM;
                case "s3k" -> SonicConfiguration.SONIC_3K_ROM;
                default -> throw new IllegalArgumentException("Unknown donor game: " + settings.donor());
            };
            config.setSessionOverride(donorKey, settings.donorRom().toAbsolutePath().toString());
        }
        config.resolveDisplayAspect();
    }

    private boolean showTitleCard;
    private boolean completeSpecialStage;

    /** Boots the level, consumes the title card, and optionally teleports the leader. */
    public void boot(Path romPath, int zone, int act, Settings settings) throws IOException {
        if (loop != null) {
            throw new IllegalStateException("session is already booted");
        }
        loop = boot.boot(romPath, zone, act);
        LevelManager level = GameServices.level();
        showTitleCard = settings.showTitleCard();
        completeSpecialStage = settings.completeSpecialStage();
        // Omit only presentation. The native title owner must still retire its
        // children/admission lease and publish any title-owned level objects.
        if (!showTitleCard) {
            level.skipPendingInitialTitleCardPresentation();
        }
        if (loop.getCurrentGameMode() != GameMode.LEVEL
                && !(showTitleCard && loop.getCurrentGameMode() == GameMode.TITLE_CARD)) {
            throw new IllegalStateException("capture did not boot into LEVEL mode: " + loop.getCurrentGameMode());
        }
        if (settings.donorActive()) {
            CrossGameFeatureProvider provider = GameServices.crossGameFeatures();
            if (!CrossGameFeatureProvider.isActive()) {
                provider.initialize(settings.donor());
                GameplayTeamBootstrap.BootstrappedTeam team = GameplayTeamBootstrap.registerActiveTeam(
                        GameServices.module(), GameServices.sprites(), GameServices.configuration());
                GameServices.camera().setFocusedSprite(team.mainSprite());
                level.refreshPlayableSpriteArt();
            }
            if (!CrossGameFeatureProvider.isActive()) {
                throw new IllegalStateException("donor '" + settings.donor()
                        + "' did not activate; check the donor ROM configuration");
            }
        }
        if (settings.emeraldStates() != null) {
            // Declared capture setup: seven ROM Collected_emeralds_array values (0-3).
            java.util.List<Integer> states = settings.emeraldStates().chars()
                    .map(c -> c - '0').boxed().toList();
            if (states.size() != 7 || states.stream().anyMatch(v -> v < 0 || v > 3)) {
                throw new IllegalArgumentException("--emeralds needs seven digits 0-3");
            }
            GameServices.gameState().restoreS3kEmeraldProgress(states,
                    states.stream().anyMatch(v -> v >= 2));
        }
        if (settings.vIntRunCount() != null) {
            // Declared capture setup: an inherited V_int_run_count (for example a movie's
            // level entry after a long run), read by objects that gate on its low bits.
            level.getObjectManager().initVblaCounter(settings.vIntRunCount());
        }
        if (settings.cameraXSub() != null) {
            // Declared capture setup: the inherited Camera_X_pos low word. Only zones that keep
            // a camera fraction honour it (S3K Doomsday autoscroll, sub_82920).
            com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "--camera-x-sub needs a zone that keeps a camera fraction"))
                    .setCameraXFraction(settings.cameraXSub());
        }
        if (settings.starPost()) {
            // Declared capture setup: a star post already hit, with Saved_X/Y at the requested
            // start. Several acts run a scripted intro on the no-star-post path that overrides
            // --x/--y outright — Sky Sanctuary act 1's SSZ1_ScreenInit forces the arrival camera
            // and Obj_57C1E then writes Player 1 to Camera_Y + $65 — so positioned captures of
            // anything past the intro need the star-post branch the ROM itself provides.
            if (GameServices.level().getCheckpointState() instanceof CheckpointState checkpoint) {
                checkpoint.saveCheckpoint(1,
                        settings.startX() != null ? settings.startX() : 0,
                        settings.startY() != null ? settings.startY() : 0,
                        false);
            }
            level.initLevelEventsForLevel();
        }
        player = GameServices.camera().getFocusedSprite();
        if (player == null) {
            throw new IllegalStateException("no focused playable sprite after boot");
        }
        if (settings.startX() != null || settings.startY() != null) {
            if (settings.startX() != null) {
                NativePositionOps.writeXPosPreserveSubpixel(player, settings.startX());
            }
            if (settings.startY() != null) {
                NativePositionOps.writeYPosPreserveSubpixel(player, settings.startY());
            }
            Camera camera = GameServices.camera();
            camera.updatePosition(true);
            level.initCameraForLevel();
            level.initLevelEventsForLevel();
            level.updateObjectPositions();
        }
        if (settings.rings() != null) {
            // Declared capture setup: the ring count the route carried in. A boss filmed from a
            // positioned start otherwise begins on zero rings, where the first touch is fatal and
            // the fight cannot be filmed at all. Applied last: --star-post saves a checkpoint with
            // restoreRings false and the reposition re-runs the level events, either of which can
            // zero a count written earlier. Ported from the Lava Reef campaign's b35f59d33.
            player.setRingCount(settings.rings());
        }
        if (settings.endOfLevel()) {
            // Declared capture setup for production post-boss event owners such as SSZ1's
            // Death Egg launch. This is the ROM's End_of_level_flag, not a direct event jump.
            GameServices.gameState().setEndOfLevelFlag(true);
        }
    }

    /** Steps one gameplay frame with the given held input ({@code null} = neutral). */
    public void step(Bk2FrameInput input) {
        requireBooted();
        Bk2FrameInput current = input != null ? input : neutral(previousInput);
        loop.getInputHandler().setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previousInput));
        previousInput = current;
        // A later level load has the same native omitted-title boundary as
        // boot; consuming only its request would discard the owner's teardown.
        if (!showTitleCard) {
            GameServices.level().skipPendingInitialTitleCardPresentation();
        }
        UiRenderPipeline ui = GameServices.graphics().getUiRenderPipeline();
        if (ui != null) {
            ui.updateFade();
        }
        // Declared setup: the special-stage debug completion, requested on alternate frames
        // until the stage hands over to its results screen.
        boolean pressComplete = completeSpecialStage
                && loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE
                && (previousInput == null || (previousInput.frameIndex() & 1) == 0);
        if (pressComplete) {
            loop.debugCompleteSpecialStageWithEmerald();
        }
        loop.step();
    }

    /** Renders the current frame through the gameplay renderer and reads it back. */
    public RgbaImage render() {
        return render(true);
    }

    /** Renders with or without the sprite pass; the tiles-only image is a baseline for pixel checks. */
    public RgbaImage render(boolean includeSprites) {
        requireBooted();
        GraphicsManager graphics = GameServices.graphics();
        LevelManager level = GameServices.level();
        graphics.runPendingRenderThreadTasks();
        if (loop.getCurrentGameMode() == GameMode.SPECIAL_STAGE_RESULTS) {
            return renderSpecialStageResults(graphics, level);
        }
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithSpritePriority(GameServices.sprites(), includeSprites);
        graphics.flush();
        var titleCard = showTitleCard ? loop.getTitleCardProvider() : null;
        if (titleCard != null && (loop.getCurrentGameMode() == GameMode.TITLE_CARD
                || titleCard.isOverlayActive())) {
            // Engine.drawTitleCardMode / drawActiveLevelTitleCardOverlay.
            graphics.resetForFixedFunction();
            titleCard.draw();
            graphics.flushScreenSpace();
        }
        UiRenderPipeline ui = graphics.getUiRenderPipeline();
        if (ui != null) {
            ui.renderFadePass();
        }
        glFinish();
        return ScreenshotCapture.captureFramebuffer(width, HEIGHT);
    }

    /** Engine.drawSpecialStageResults: optional level backdrop, then the results sprites. */
    private RgbaImage renderSpecialStageResults(GraphicsManager graphics, LevelManager level) {
        org.lwjgl.opengl.GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        ResultsScreen results = loop.getResultsScreen();
        if (results != null) {
            if (results instanceof LevelBackdropResultsScreen backdrop && backdrop.drawsLevelBackdrop()) {
                backdrop.prepareLevelBackdropDraw();
                level.drawWithRenderOptions(GameServices.sprites(),
                        LevelManager.LevelRenderOptions.previewCapture());
                graphics.flush();
            }
            results.setViewportWidth(width);
            graphics.beginPatternBatch();
            java.util.List<com.openggf.graphics.GLCommand> commands = new java.util.ArrayList<>();
            results.appendRenderCommands(commands);
            graphics.flushPatternBatch();
            graphics.flushScreenSpace();
        }
        UiRenderPipeline ui = graphics.getUiRenderPipeline();
        if (ui != null) {
            ui.renderFadePass();
        }
        glFinish();
        return ScreenshotCapture.captureFramebuffer(width, HEIGHT);
    }

    public AbstractPlayableSprite player() {
        requireBooted();
        return player;
    }

    public GameLoop loop() {
        requireBooted();
        return loop;
    }

    public int width() {
        return width;
    }

    /** One CSV-friendly line of leader/camera state for the frame just stepped. */
    public String stateLine(int frame, Bk2FrameInput input) {
        requireBooted();
        Camera camera = GameServices.camera();
        return frame
                + "," + (player.getCentreX() & 0xFFFF)
                + "," + (player.getCentreY() & 0xFFFF)
                + "," + player.getXSpeed()
                + "," + player.getYSpeed()
                + "," + player.getGSpeed()
                + "," + (player.getAir() ? 1 : 0)
                + "," + (player.getRolling() ? 1 : 0)
                + "," + (player.getSpindash() ? 1 : 0)
                + "," + (player.isHurt() ? 1 : 0)
                + "," + (player.getDead() ? 1 : 0)
                + "," + player.getRingCount()
                + "," + player.getMappingFrame()
                + "," + (camera.getX() & 0xFFFF)
                + "," + (camera.getY() & 0xFFFF)
                + "," + loop.getCurrentGameMode()
                + "," + (input == null ? "" : input.rawLine());
    }

    public static String stateHeader() {
        return "frame,x,y,xvel,yvel,gspeed,air,rolling,spindash,hurt,dead,rings,mapping_frame,cam_x,cam_y,mode,input";
    }

    private static Bk2FrameInput neutral(Bk2FrameInput previous) {
        int index = previous == null ? 0 : previous.frameIndex() + 1;
        return new Bk2FrameInput(index, 0, 0, false, 0, 0, false, "|..|........|........|");
    }

    private void requireBooted() {
        if (loop == null || closed) {
            throw new IllegalStateException("capture session is not active");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            // The graphics singleton caches shader, atlas and palette GL objects that
            // belong to this window's context. Release them while the context is still
            // current, or a second session in the same process renders black frames.
            GraphicsManager.destroyForReinit();
            boot.close();
        } finally {
            GameServices.configuration().clearSessionOverrides();
        }
    }

    /**
     * Capture configuration. {@code donor} is {@code off} for native play or a donor
     * game code ({@code s1}, {@code s2}); {@code donorRom} overrides the configured donor
     * ROM path when non-null; {@code sidekickCharacter} is blank for solo.
     */
    public record Settings(int width, String mainCharacter, String sidekickCharacter, String donor,
                           Path donorRom, Integer startX, Integer startY, String emeraldStates,
                           boolean showTitleCard, boolean completeSpecialStage, Integer vIntRunCount,
                           Integer cameraXSub, boolean starPost, Integer rings, boolean endOfLevel) {
        public Settings(int width, String mainCharacter, String sidekickCharacter, String donor,
                        Path donorRom, Integer startX, Integer startY) {
            this(width, mainCharacter, sidekickCharacter, donor, donorRom, startX, startY, null, false,
                    false);
        }

        public Settings(int width, String mainCharacter, String sidekickCharacter, String donor,
                        Path donorRom, Integer startX, Integer startY, String emeraldStates,
                        boolean showTitleCard, boolean completeSpecialStage) {
            this(width, mainCharacter, sidekickCharacter, donor, donorRom, startX, startY, emeraldStates,
                    showTitleCard, completeSpecialStage, null, null, false, null, false);
        }

        public Settings(int width, String mainCharacter, String sidekickCharacter, String donor,
                        Path donorRom, Integer startX, Integer startY, String emeraldStates,
                        boolean showTitleCard, boolean completeSpecialStage, Integer vIntRunCount,
                        Integer cameraXSub) {
            this(width, mainCharacter, sidekickCharacter, donor, donorRom, startX, startY, emeraldStates,
                    showTitleCard, completeSpecialStage, vIntRunCount, cameraXSub, false, null, false);
        }

        public Settings(int width, String mainCharacter, String sidekickCharacter, String donor,
                        Path donorRom, Integer startX, Integer startY, String emeraldStates,
                        boolean showTitleCard, boolean completeSpecialStage, Integer vIntRunCount,
                        Integer cameraXSub, boolean starPost) {
            this(width, mainCharacter, sidekickCharacter, donor, donorRom, startX, startY, emeraldStates,
                    showTitleCard, completeSpecialStage, vIntRunCount, cameraXSub, starPost, null, false);
        }

        public Settings {
            Objects.requireNonNull(mainCharacter, "mainCharacter");
            sidekickCharacter = sidekickCharacter == null ? "" : sidekickCharacter;
            donor = donor == null || donor.isBlank() ? "off" : donor.trim();
        }

        public boolean donorActive() {
            return !"off".equals(donor);
        }
    }
}
