package com.openggf.game.mode;

import com.openggf.configuration.SonicConfiguration;
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
    CourseControl(GameplayModeContext context) { this(context, null); }
    CourseControl(GameplayModeContext context, com.openggf.control.InputHandler input) {
        this.context = context; this.input = input; this.rewindHeld = input != null && input.isRewindHeld();
    }

    /** Current configured rewind key/primary-pad bumper, independent of developer rewind enablement.
     * Controllers own edge detection. Movie inputs should use their controller menu command instead. */
    public boolean rewindHeld() { return rewindHeld; }

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
        if (!profile.isLevelMusicPublicationPending()
                && (currentMusic == null || currentMusic.musicId() != musicId)) GameServices.audio().playMusic(musicId);
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
