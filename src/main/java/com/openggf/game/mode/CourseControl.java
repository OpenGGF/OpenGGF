package com.openggf.game.mode;

import com.openggf.audio.GameSound;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.*;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-owned operations injected into a level controller at stable frame boundaries. */
@ModApi
public final class CourseControl {
    private final GameplayModeContext context;
    CourseControl(GameplayModeContext context) { this.context = context; }

    @ModApi
    public record Ball(String character, int x, int y, int xSpeed, int ySpeed, int groundSpeed,
                       int angle, boolean airborne, boolean floorSupport, boolean hurt,
                       boolean dead, boolean rolling, int radius, int supportDx, int supportDy,
                       int cameraX, int cameraY) { }

    private AbstractPlayableSprite player() {
        return java.util.Objects.requireNonNull(context.getCamera().getFocusedSprite(), "active ball");
    }

    public Ball ball() {
        var p = player();
        var support = context.getLevelManager().getObjectManager().getRidingObject(p);
        int supportDx = 0, supportDy = 0;
        if (support instanceof com.openggf.level.objects.AbstractObjectInstance object) {
            supportDx = object.getX() - object.getPreUpdateX();
            supportDy = object.getY() - object.getPreUpdateY();
        }
        return new Ball(p.getCode(), p.getCentreX(), p.getCentreY(), p.getXSpeed(), p.getYSpeed(),
                p.getGSpeed(), p.getAngle() & 255, p.getAir(),
                !p.getAir() && p.getGroundMode() == GroundMode.GROUND
                        && (!p.isOnObject() || (support != null && !support.isDestroyed())),
                p.isHurt() || p.getHurtAtFrameStart(), p.getDead(), p.getRolling(), p.getYRadius(), supportDx, supportDy,
                context.getCamera().getX(), context.getCamera().getY());
    }
    /** Native entry art may finish while the initial lie settles, before HOLD begins. */
    public void finishInitialPresentation() { context.getLevelManager().skipPendingInitialTitleCardPresentation(); }
    /** Entry fade is presentation setup, completed before a neutral course checkpoint exists. */
    public void advanceEntryPresentation() { if (context.getFadeManager().isActive()) context.getFadeManager().update(); }
    public boolean presentationReady() { return !context.getFadeManager().isActive(); }
    public String engineIdentity() { return com.openggf.version.AppVersion.get(); }
    public String apiIdentity() { return com.openggf.mods.ModApiVersion.CURRENT.toString(); }
    /** Engine-frozen content identities of every enabled mod, in deterministic activation order. */
    public String modContentSha256() {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (var descriptor : com.openggf.ModSubsystem.current().processCatalog().effective().orderedEnabled()) {
                digest.update(descriptor.manifest().id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(descriptor.sha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                digest.update((byte) '\n');
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
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
        context.getRewindRegistry().restoreCourse(checkpoint.snapshot);
        if (context.getRewindController() != null) context.getRewindController().invalidateCourseFuture();
        // Course replacement retains mode time; abandoned transients cannot outlive the shot.
        GameServices.audio().stopAllSfx();
        int musicId = context.getWorldSession().getGameModule().getZoneRegistry()
                .getMusicId(level.getCurrentZone(), level.getCurrentAct());
        var currentMusic = GameServices.audio().captureLogicalSnapshot().presentation().activeMusic();
        if (currentMusic == null || currentMusic.musicId() != musicId) GameServices.audio().playMusic(musicId);
    }

    public void selectCharacter(String character) {
        if (!character.equals("sonic") && !character.equals("tails"))
            throw new IllegalArgumentException("Unsupported course character");
        if (player().getCode().equals(character)) return;
        var previous = player();
        int x = previous.getCentreX(), y = previous.getCentreY();
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        context.getSpriteManager().clearAllSprites();
        var p = GameplayTeamBootstrap.registerActiveTeam(context.getWorldSession().getGameModule(),
                context.getSpriteManager(), config).mainSprite();
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
        context.getCamera().setFocusedSprite(p);
        context.getLevelManager().refreshPlayableSpriteArt();
    }

    /** Reconstitutes the controlled mode's single main roster before a full debug restore. */
    static void prepareRoster(GameplayModeContext context,
                                      com.openggf.game.rewind.snapshot.SpriteManagerSnapshot snapshot) {
        if (ControlledFrameRuntime.controller(context) == null || snapshot.sprites().length != 1) return;
        new CourseControl(context).selectCharacter(snapshot.sprites()[0].code());
    }

    /** Launches relative to the supported surface without running native charge physics. */
    public void launch(int facing, int elevationDegrees, int speedFixed) {
        if ((facing != -1 && facing != 1) || elevationDegrees < 0 || elevationDegrees > 75
                || speedFixed < 0x80 || speedFixed > 0x2000) throw new IllegalArgumentException("Invalid shot");
        var p = player();
        int oldRadius = p.getYRadius(), oldX = p.getCentreX(), oldY = p.getCentreY();
        double angle = (p.getAngle() & 255) * Math.PI / 128.0;
        p.setDirection(facing < 0 ? Direction.LEFT : Direction.RIGHT);
        p.setSpindash(false);
        // A golf chip is an external impulse, not a held-button variable-height jump.
        p.setJumping(false); p.setRollingJump(false); p.setPushing(false); p.setStickToConvex(false);
        p.setRolling(true);
        // Keep the support contact fixed when replacing standing radii with ball radii.
        int radiusDelta = oldRadius - p.getYRadius();
        NativePositionOps.writeXPosResetSubpixel(p, oldX - (int) Math.round(Math.sin(angle) * radiusDelta));
        NativePositionOps.writeYPosResetSubpixel(p, oldY + (int) Math.round(Math.cos(angle) * radiusDelta));
        double elevation = Math.toRadians(elevationDegrees);
        double tangent = facing * Math.cos(elevation) * speedFixed;
        double normal = Math.sin(elevation) * speedFixed;
        p.setGSpeed((short) Math.round(tangent));
        p.setXSpeed((short) Math.round(tangent * Math.cos(angle) + normal * Math.sin(angle)));
        p.setYSpeed((short) Math.round(tangent * Math.sin(angle) - normal * Math.cos(angle)));
        if (elevationDegrees > 0) {
            context.getLevelManager().getObjectManager().clearRidingObjectForJump(p);
            p.setAir(true); p.setOnObject(false);
        }
        p.updateSensors(p.getX(), p.getY());
        GameServices.audio().playSfx(GameSound.SPINDASH_RELEASE);
    }

    /** Pins the supported ball coherently after dwell; ceiling/wall/apex callers are rejected. */
    public void settle() {
        if (!ball().floorSupport()) throw new IllegalStateException("Ball has no floor support");
        var p = player();
        p.setGSpeed((short) 0); p.setXSpeed((short) 0); p.setYSpeed((short) 0);
        p.setSubpixelRaw(0, 0); p.setRolling(true); p.updateSensors(p.getX(), p.getY());
    }
    /** Preserves the game's native charge request and its innate sound-driver pitch ladder. */
    public void chargeSound() { GameServices.audio().playSfx(GameSound.SPINDASH_CHARGE); }

    public void loadAct(int act) {
        if (act < 0 || act > 1) throw new IllegalArgumentException("Invalid hole");
        try { context.getLevelManager().loadZoneAndAct(0, act); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load course", failure); }
    }
}
