package com.openggf.game.mode;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.audio.AudioReplay;
import com.openggf.game.*;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-owned operations injected into a level controller at stable frame boundaries. */
@ModApi
public final class CourseControl {
    private final GameplayModeContext context;
    private final com.openggf.control.InputHandler input;
    private final boolean rewindHeld;
    private boolean frameClosed;
    CourseControl(GameplayModeContext context) { this(context, null); }
    CourseControl(GameplayModeContext context, com.openggf.control.InputHandler input) {
        this.context = context; this.input = input; this.rewindHeld = input != null && input.isRewindHeld();
    }

    /** Current configured rewind key/primary-pad bumper, independent of developer rewind enablement.
     * Controllers own edge detection. Movie inputs should use their controller menu command instead. */
    public boolean rewindHeld() { return rewindHeld; }

    /** Injected services for this row's current level visit. Resolve objects anew after rewind or load.
     * The handle is revoked after afterTick, on callback failure, on level replacement or session teardown.
     * Object creation retains the callback's verified owner through the existing injected service path. */
    public com.openggf.level.objects.ObjectServices objectServices() {
        requireFrame();
        var manager = context.getLevelManager().getObjectManager();
        var delegate = manager.getObjectServices();
        return (com.openggf.level.objects.ObjectServices) java.lang.reflect.Proxy.newProxyInstance(
                CourseControl.class.getClassLoader(), new Class<?>[]{com.openggf.level.objects.ObjectServices.class},
                (proxy, method, args) -> {
                    requireFrame();
                    if (context.getLevelManager().getObjectManager() != manager)
                        throw new IllegalStateException("Controlled services belong to a replaced level visit");
                    try { return method.invoke(delegate, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    private void requireFrame() {
        if (frameClosed || !context.isGameplayRuntimeReady())
            throw new IllegalStateException("Controlled frame capability is closed");
    }
    void closeFrame() { frameClosed = true; }

    /** Characters typed this row through the operating system's text input (layout- and
     * Shift-aware, control characters omitted); consuming empties the row's text. Empty when the
     * row has no live input handler. Pair with {@link GameplayFrameController#capturesKeyboardText()}
     * so letter shortcuts do not also fire. */
    public String typedText() { return input == null ? "" : input.consumeTypedText(); }

    /** Held state of a GLFW key, including keys claimed by keyboard text capture. */
    public boolean keyDown(int glfwKey) { return input != null && input.isCapturedKeyDown(glfwKey); }

    /** Press edge of a GLFW key this row, including keys claimed by keyboard text capture. */
    public boolean keyPressed(int glfwKey) { return input != null && input.isCapturedKeyPressed(glfwKey); }

    /** Records final presented PCM for a bounded reverse view (1..120 seconds; at most four live
     * recordings per session). Start immediately before its first forward row and close on
     * settlement, replay completion or cancellation. Paused/silent rows are not recorded.
     * The session closes abandoned recordings, including after a creator callback failure. */
    public AudioReplay recordAudioReplay(int maxSeconds) { return context.recordAudioReplay(maxSeconds); }

    /** Prompt label for a local player's button (0 = P1, 1 = P2); see {@link com.openggf.control.ButtonPrompts}.
     * Empty when the button is unbound or this row has no live input handler. */
    public java.util.Optional<String> buttonLabel(int player, com.openggf.control.ButtonPrompts.Button button) {
        return input == null ? java.util.Optional.empty()
                : com.openggf.control.ButtonPrompts.label(input, player, button);
    }

    @ModApi
    public record PlayerState(String character, int x, int y, int xSpeed, int ySpeed, int groundSpeed,
                       int angle, boolean airborne, boolean floorSupport, boolean hurt,
                       boolean dead, boolean rolling, int radius, int supportDx, int supportDy,
                       int cameraX, int cameraY) { }

    private AbstractPlayableSprite player() {
        return java.util.Objects.requireNonNull(context.getCamera().getFocusedSprite(), "controlled player");
    }

    public PlayerState playerState() {
        var p = player();
        var support = context.getLevelManager().getObjectManager().getRidingObject(p);
        int supportDx = 0, supportDy = 0;
        if (support instanceof com.openggf.level.objects.AbstractObjectInstance object) {
            supportDx = object.getX() - object.getPreUpdateX();
            supportDy = object.getY() - object.getPreUpdateY();
        }
        return new PlayerState(p.getCode(), p.getCentreX(), p.getCentreY(), p.getXSpeed(), p.getYSpeed(),
                p.getGSpeed(), p.getAngle() & 255, p.getAir(),
                !p.getAir() && p.getGroundMode() == GroundMode.GROUND
                        && (!p.isOnObject() || (support != null && !support.isDestroyed())),
                p.isHurt() || p.getHurtAtFrameStart(), p.getDead(), p.getRolling(), p.getYRadius(), supportDx, supportDy,
                context.getCamera().getX(), context.getCamera().getY());
    }

    /**
     * Opaque main-player/camera entry state for directed presentation. Unlike a course
     * checkpoint this owns no world, object-manager, campaign, timer-manager or audio snapshot.
     * It retains an identity tag and immutable values, never a live player or frame capability.
     */
    @ModApi
    public static final class DirectedPlayerState {
        private final Object owner;
        private final long loadGeneration;
        private final String gameId;
        private final String character;
        private final com.openggf.level.objects.PerObjectRewindSnapshot player;
        private final com.openggf.game.rewind.snapshot.CameraSnapshot camera;

        private DirectedPlayerState(Object owner, long loadGeneration, String gameId, String character,
                com.openggf.level.objects.PerObjectRewindSnapshot player,
                com.openggf.game.rewind.snapshot.CameraSnapshot camera) {
            this.owner = owner;
            this.loadGeneration = loadGeneration;
            this.gameId = gameId;
            this.character = character;
            this.player = player;
            this.camera = camera;
        }

        public String gameId() { return gameId; }
        public String character() { return character; }
    }

    /**
     * Captures a freely controlled stock main player. Object custody, abilities, super art,
     * entry locks and forced controls must finish before directing the native player.
     * The controller must hold native gameplay while this entry state is in use.
     */
    public DirectedPlayerState captureDirectedPlayer() {
        requireFrame();
        var p = player();
        var snapshot = p.captureRewindState();
        var extra = snapshot.playerExtra();
        String game = context.getWorldSession().getGameModule().getGameCode();
        boolean stock = switch (game) {
            case "s1" -> p.getCode().equals("sonic");
            case "s2" -> p.getCode().equals("sonic") || p.getCode().equals("tails");
            case "s3k" -> p.getCode().equals("sonic") || p.getCode().equals("tails") || p.getCode().equals("knuckles");
            default -> false;
        };
        if (!stock || p.getDead() || p.isHurt() || p.isControlLocked() || p.isObjectControlled()
                || p.isOnObject() || context.getLevelManager().getObjectManager().getRidingObject(p) != null
                || p.isObjectMappingFrameControl() || p.isSuperSonic() || p.isDebugMode()
                || p.getDoubleJumpFlag() != 0 || p.getMoveLockTimer() != 0 || p.getPinballMode()
                || p.getForcedInputMask() != 0 || extra.forcedJumpPress() || extra.deferredObjectControlRelease()
                || extra.hasQueuedControlLockedState() || extra.hasQueuedForceInputRightState()
                || p.getRenderCentreX() != p.getCentreX() || p.getRenderCentreY() != p.getCentreY()) {
            throw new IllegalStateException("Native direction requires a freely controlled stock main player");
        }
        var state = new DirectedPlayerState(context.courseCheckpointIdentity(),
                context.getLevelManager().getCompletedProductionLoadGeneration(), game, p.getCode(),
                snapshot, context.getCamera().capture());
        context.directedPlayerLease().begin(state);
        return state;
    }

    /** A reload of the same zone/act is a new visit and cannot consume an older entry state. */
    public boolean isDirectedPlayerCurrent(DirectedPlayerState state) {
        requireFrame();
        return context.directedPlayerLease().owns(state) && directedPlayerMatchesVisit(state);
    }

    private boolean directedPlayerMatchesVisit(DirectedPlayerState state) {
        return state != null && state.owner == context.courseCheckpointIdentity()
                && state.loadGeneration == context.getLevelManager().getCompletedProductionLoadGeneration()
                && state.gameId.equals(context.getWorldSession().getGameModule().getGameCode())
                && state.character.equals(player().getCode());
    }

    /**
     * Applies an authored pixel pose to the actual native main player and camera, without a
     * physics step. The caller resolves the mapping frame from the matching game's ROM art.
     * No position, pose or camera value is sourced from trace comparison data.
     */
    public void directPlayer(DirectedPlayerState state, int x, int y, int animation, int mappingFrame,
            boolean flipX, boolean visible, int cameraX, int cameraY) {
        directPlayer(state, x, y, animation, mappingFrame, flipX, false, visible, cameraX, cameraY);
    }

    /** Native sampled poses can also retain vertical render orientation while under direction. */
    public void directPlayer(DirectedPlayerState state, int x, int y, int animation, int mappingFrame,
            boolean flipX, boolean flipY, boolean visible, int cameraX, int cameraY) {
        requireDirectedPlayer(state);
        requireNativeWord(x, "x");
        requireNativeWord(y, "y");
        requireNativeWord(cameraX, "cameraX");
        requireNativeWord(cameraY, "cameraY");
        var p = player();
        if (animation < 0 || animation > 65535 || mappingFrame < 0
                || mappingFrame >= p.getAnimationFrameCount()) {
            throw new IllegalArgumentException("Directed animation or mapping frame is unavailable in native player art");
        }
        // Entry rejects object custody. Native HOLD keeps that world and all other players still;
        // leave the object manager's unrelated contact bookkeeping unchanged during preview.
        p.setOnObject(false);
        p.setControlLocked(true);
        p.setGSpeed((short) 0);
        p.setXSpeed((short) 0);
        p.setYSpeed((short) 0);
        NativePositionOps.writeXPosResetSubpixel(p, x);
        NativePositionOps.writeYPosResetSubpixel(p, y);
        p.setDirection(flipX ? Direction.LEFT : Direction.RIGHT);
        p.setRenderFlips(flipX, flipY);
        p.setAnimationId(animation);
        p.setMappingFrame(mappingFrame);
        p.setObjectMappingFrameControl(true);
        p.setHidden(!visible);
        p.setRenderFlagOnScreen(true);
        p.updateSensors(p.getX(), p.getY());
        context.getCamera().setX((short) cameraX);
        context.getCamera().setY((short) cameraY);
    }

    /**
     * Restores only entry player/camera state. When keeping the final position, entry control
     * policy is restored and the main player is released airborne, upright and motionless at
     * that position; native collision establishes support on the next ordinary step. The final
     * camera origin is kept with its entry boundary/freeze policy. Audio and other actors are
     * untouched. Pending gameplay input is cleared for either outcome.
     */
    public void releaseDirectedPlayer(DirectedPlayerState state, boolean keepFinalPosition) {
        requireDirectedPlayer(state);
        restoreDirectedPlayer(state, keepFinalPosition);
        context.directedPlayerLease().complete(state);
    }

    static void restoreDirectedPlayerAfterFailure(GameplayModeContext context, DirectedPlayerState state) {
        if (!context.isGameplayRuntimeReady()) return;
        var cleanup = new CourseControl(context);
        if (cleanup.directedPlayerMatchesVisit(state)) cleanup.restoreDirectedPlayer(state, false);
    }

    static boolean isDirectedPlayerVisit(GameplayModeContext context, DirectedPlayerState state) {
        return context.isGameplayRuntimeReady() && new CourseControl(context).directedPlayerMatchesVisit(state);
    }

    private void restoreDirectedPlayer(DirectedPlayerState state, boolean keepFinalPosition) {
        var p = player();
        int x = p.getCentreX(), y = p.getCentreY();
        int cameraX = context.getCamera().getX(), cameraY = context.getCamera().getY();
        Direction direction = p.getDirection();
        boolean flip = p.getRenderHFlip();
        p.restoreRewindState(state.player);
        context.getCamera().restore(state.camera);
        if (keepFinalPosition) {
            context.getLevelManager().getObjectManager().clearRidingObject(p);
            p.setRolling(false);
            p.setJumping(false);
            p.setRollingJump(false);
            p.setSpindash(false);
            p.setPushing(false);
            p.setSliding(false);
            p.setCrouching(false);
            p.setLookingUp(false);
            p.setStickToConvex(false);
            p.setAngle((byte) 0);
            p.setGroundMode(GroundMode.GROUND);
            p.setAir(true);
            p.setOnObject(false);
            p.setLatchedSolidObject(0, null);
            p.setGSpeed((short) 0);
            p.setXSpeed((short) 0);
            p.setYSpeed((short) 0);
            p.setDirection(direction);
            p.setRenderFlips(flip, false);
            NativePositionOps.writeXPosResetSubpixel(p, x);
            NativePositionOps.writeYPosResetSubpixel(p, y);
            p.resetPositionAndStatTableHistory();
            p.forceAnimationRestart();
            context.getCamera().setX((short) cameraX);
            context.getCamera().setY((short) cameraY);
        }
        p.clearForcedInputMask();
        p.clearQueuedControlState();
        p.clearLogicalInputState();
        p.setJumpInputPressed(false, false);
        p.setDirectionalInputPressed(false, false, false, false);
        p.setMovementInputActive(false);
        p.suppressNextJumpPress();
        p.updateSensors(p.getX(), p.getY());
    }

    private void requireDirectedPlayer(DirectedPlayerState state) {
        if (!isDirectedPlayerCurrent(state)) {
            throw new IllegalArgumentException("Directed player entry belongs to another session, level visit or character");
        }
    }

    private static void requireNativeWord(int value, String field) {
        if (value < Short.MIN_VALUE || value > 0xffff) {
            throw new IllegalArgumentException("Directed " + field + " is outside native 16-bit coordinates");
        }
    }

    /** Consumes a pending initial card request; an already active native overlay finishes through controlled rows. */
    public void finishInitialPresentation() { context.getLevelManager().skipPendingInitialTitleCardPresentation(); }
    /** Advances an active entry fade; returns whether this row serviced that fade.
     * Finish this setup before capturing a reusable course checkpoint. */
    public boolean advanceEntryPresentation() {
        boolean active = context.getFadeManager().isActive();
        if (active) context.getFadeManager().update();
        return active;
    }
    /** Entry fade and native released title text must finish before capturing a reusable lie. */
    public boolean presentationReady() {
        var title = context.getWorldSession().getGameModule().getTitleCardProvider();
        return !context.getFadeManager().isActive() && (title == null || !title.isOverlayActive());
    }
    public String engineIdentity() { return com.openggf.version.AppVersion.get(); }
    public String apiIdentity() { return com.openggf.ModSubsystem.current().apiIdentity(); }
    /** Engine-frozen content identities of every enabled mod, in deterministic activation order. */
    public String modContentSha256() { return com.openggf.ModSubsystem.current().modContentSha256(); }
    /** Identifies the actual immutable logical ROM; paths and header checksums are insufficient. */
    public String romSha1() {
        try {
            var rom = GameServices.rom().getRom();
            var digest = java.security.MessageDigest.getInstance("SHA-1");
            for (long at = 0, size = rom.getSize(); at < size; at += 65536)
                digest.update(rom.readBytes(at, (int) Math.min(65536, size - at)));
            return java.util.HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Cannot identify course ROM", failure);
        }
    }
    public int actIndex() { return context.getLevelManager().getCurrentAct(); }
    public int viewportWidth() { return context.getCamera().getWidth(); }
    public int courseMaxX() { return context.getLevelManager().getCurrentLevel().getMaxX(); }
    /** Upright world bottom: decoded maxY is the camera origin limit, not the floor extent.
     * Native S2 Sonic_Boundary_CheckBottom compares centreY with maxY + $E0. */
    public int courseMaxY() { return context.getLevelManager().getCurrentLevel().getMaxY() + 224; }

    public CourseCheckpoint capture() {
        var level = context.getLevelManager();
        return new CourseCheckpoint(context.courseCheckpointIdentity(), level.getCurrentLevel(),
                level.getCurrentZone(), level.getCurrentAct(), player().getCode(),
                context.getRewindRegistry().captureCourse(), context.getRewindRegistry().courseLayoutVersion());
    }

    public void restore(CourseCheckpoint checkpoint) {
        java.util.Objects.requireNonNull(checkpoint, "checkpoint");
        var level = context.getLevelManager();
        if (checkpoint.owner != context.courseCheckpointIdentity() || checkpoint.level != level.getCurrentLevel()
                || checkpoint.zone != level.getCurrentZone() || checkpoint.act != level.getCurrentAct())
            throw new IllegalArgumentException("Course checkpoint belongs to another session or hole");
        if (checkpoint.layoutVersion != context.getRewindRegistry().courseLayoutVersion())
            throw new IllegalArgumentException("Course checkpoint registry adapters have changed");
        context.getRewindRegistry().requireCourseLayout(checkpoint.snapshot);
        // A frame controller restores from inside its own callback. Engine objects the restore
        // rebuilds must not inherit that creator as their owner (an inherited owner made adoption
        // reject the HCZ fan platform, and the restore dropped it), so no creator owner is in scope.
        com.openggf.ModSubsystem.withoutCreatorOwner(() -> {
            selectCharacter(checkpoint.character);
            var profile = context.getWorldSession().getGameModule().getLevelInitProfile();
            boolean entryMusicPublished = !profile.isLevelMusicPublicationPending();
            context.getRewindRegistry().restoreCourse(checkpoint.snapshot);
            // A checkpoint taken during level entry still holds its ROM-timed Level_PlayBgm countdown
            // (Sonic 2). Once that request has been published, restoring the course must not arm it
            // again: the zone music would restart from its first bar after the countdown.
            if (entryMusicPublished && profile.isLevelMusicPublicationPending()) profile.cancelPendingLevelLoadWork();
            if (context.getRewindController() != null) context.getRewindController().invalidateCourseFuture();
            // Course replacement retains mode time and the sound driver keeps running, as the Z80 does
            // across 68000 state changes: sounds in flight finish on their own. A driver-wide SFX stop
            // here gated the Sonic 2 presentation output (music included) until the next SFX admission.
            int musicId = context.getWorldSession().getGameModule().getZoneRegistry()
                    .getMusicId(level.getCurrentZone(), level.getCurrentAct());
            var currentMusic = GameServices.audio().captureLogicalSnapshot().presentation().activeMusic();
            // While the entry countdown is still pending, its own publication starts the zone music.
            if (musicId >= 0 && !profile.isLevelMusicPublicationPending()
                    && (currentMusic == null || currentMusic.musicId() != musicId)) GameServices.audio().playMusic(musicId);
            return null;
        });
    }

    public void selectCharacter(String character) {
        var registry = context.getWorldSession().getGameModule().getPlayableCharacterRegistry();
        var key = CharacterKey.parsePersisted(java.util.Objects.requireNonNull(character, "character"));
        var definition = registry.find(key).orElseThrow(
                () -> new IllegalArgumentException("Unavailable controlled character: " + character));
        if (player().getCode().equals(character)) return;
        var previous = player();
        int x = previous.getCentreX(), y = previous.getCentreY();
        var camera = context.getCamera();
        var view = camera.capture();
        var config = GameServices.configuration();
        // Construct first: a creator factory failure must leave the live roster intact.
        var p = java.util.Objects.requireNonNull(definition.spriteFactory().create(character,
                previous.getX(), previous.getY()), "controlled character factory");
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        context.getSpriteManager().clearAllSprites();
        context.getSpriteManager().addSprite(p);
        p.setAir(previous.getAir()); p.setOnObject(previous.isOnObject());
        p.setAngle(previous.getAngle()); p.setGroundMode(previous.getGroundMode());
        p.setRolling(previous.getRolling()); p.setGSpeed(previous.getGSpeed());
        p.setXSpeed(previous.getXSpeed()); p.setYSpeed(previous.getYSpeed());
        NativePositionOps.writeXPosResetSubpixel(p, x);
        double angle = (previous.getAngle() & 255) * Math.PI / 128.0;
        int radiusDelta = previous.getYRadius() - p.getYRadius();
        NativePositionOps.writeXPosResetSubpixel(p, x - (int) Math.round(Math.sin(angle) * radiusDelta));
        NativePositionOps.writeYPosResetSubpixel(p, y + (int) Math.round(Math.cos(angle) * radiusDelta));
        p.updateSensors(p.getX(), p.getY());
        // Native setFocusedSprite initializes a new view at sprite top-left.
        // Replacing a controlled roster must retain its held/checkpoint view.
        camera.setFocusedSprite(p);
        camera.restore(view);
        context.getLevelManager().refreshPlayableSpriteArt();
    }

    /** Reconstitutes the controlled mode's single main roster before a full debug restore. */
    static void prepareRoster(GameplayModeContext context,
                                      com.openggf.game.rewind.snapshot.SpriteManagerSnapshot snapshot) {
        if (ControlledFrameRuntime.controller(context) == null || snapshot.sprites().length != 1) return;
        new CourseControl(context).selectCharacter(snapshot.sprites()[0].code());
    }

    /**
     * Applies a creator-calculated rolling impulse. Speeds use native signed 8.8 units
     * (256 = one pixel per physics step). Corrects the centre for rolling radii and,
     * when airborne, releases native object support. Does not choose power, angle,
     * trajectory, sound or a subsequent gameplay step.
     */
    public void launchRolling(int facing, int xSpeed, int ySpeed, int groundSpeed, boolean airborne) {
        if ((facing != -1 && facing != 1) || xSpeed < Short.MIN_VALUE || xSpeed > Short.MAX_VALUE
                || ySpeed < Short.MIN_VALUE || ySpeed > Short.MAX_VALUE
                || groundSpeed < Short.MIN_VALUE || groundSpeed > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid native rolling impulse");
        }
        var p = player();
        int oldRadius = p.getYRadius(), oldX = p.getCentreX(), oldY = p.getCentreY();
        double angle = (p.getAngle() & 255) * Math.PI / 128.0;
        p.setDirection(facing < 0 ? Direction.LEFT : Direction.RIGHT);
        p.setSpindash(false);
        // An external impulse has no held-button variable-height jump latch.
        p.setJumping(false); p.setRollingJump(false); p.setPushing(false); p.setStickToConvex(false);
        p.setRolling(true);
        // Keep the support contact fixed when replacing standing radii with ball radii.
        int radiusDelta = oldRadius - p.getYRadius();
        NativePositionOps.writeXPosResetSubpixel(p, oldX - (int) Math.round(Math.sin(angle) * radiusDelta));
        NativePositionOps.writeYPosResetSubpixel(p, oldY + (int) Math.round(Math.cos(angle) * radiusDelta));
        p.setGSpeed((short) groundSpeed);
        p.setXSpeed((short) xSpeed);
        p.setYSpeed((short) ySpeed);
        if (airborne) {
            context.getLevelManager().getObjectManager().clearRidingObjectForJump(p);
            p.setAir(true); p.setOnObject(false);
        }
        p.updateSensors(p.getX(), p.getY());
    }

    /** Pins the supported player coherently after dwell; ceiling/wall/apex callers are rejected. */
    public void settle() {
        if (!playerState().floorSupport()) throw new IllegalStateException("Player has no floor support");
        var p = player();
        p.setGSpeed((short) 0); p.setXSpeed((short) 0); p.setYSpeed((short) 0);
        p.setSubpixelRaw(0, 0); p.setRolling(true); p.updateSensors(p.getX(), p.getY());
    }
    /** Loads an explicit native zone/act. Destination availability is owned by the active module. */
    public void loadLevel(int zone, int act) {
        if (zone < 0 || act < 0) throw new IllegalArgumentException("Negative level destination");
        try { context.getLevelManager().loadZoneAndAct(zone, act); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load controlled level", failure); }
    }
}
