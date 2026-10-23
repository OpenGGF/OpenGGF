package com.openggf.level.objects.authored;

import com.openggf.audio.StreamedMusicPort;
import com.openggf.game.MusicReference;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * Semantic authored-region behavior. All mutable fields are scalar and use the engine's
 * automatic subclass rewind capture; camera/player/audio/service state stays with its normal
 * registered owner. Spawn recreation retains the immutable typed placement properties.
 */
public final class AuthoredGizmoInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private final AuthoredObjectProperties properties;
    private boolean inside;
    private boolean fired;
    private boolean activeCamera;
    private boolean releasedCamera;
    private boolean buttonHeld;
    private boolean pendingStageplay;
    private int cooldown;
    private int oldMinX, oldMaxX, oldMinY, oldMaxY;
    private int oldMinXTarget, oldMaxXTarget, oldMinYTarget, oldMaxYTarget;

    public AuthoredGizmoInstance(ObjectSpawn spawn) {
        super(spawn, "AuthoredGizmo");
        if (!AuthoredGizmos.handlesKey(spawn.objectKey()) || spawn.authoredProperties() == null)
            throw new IllegalArgumentException("A matching authored gizmo placement is required");
        properties = spawn.authoredProperties();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (player == null || player.getDead() || isDestroyed()) return;
        int x = player.getCentreX() & 0xffff, y = player.getCentreY() & 0xffff;
        boolean nowInside = contains(x, y);
        boolean up = player instanceof AbstractPlayableSprite sprite && sprite.isUpPressed();
        if (cooldown > 0) cooldown--;
        switch (properties) {
            case AuthoredObjectProperties.CameraZone zone -> camera(zone, x, y, nowInside);
            case AuthoredObjectProperties.MusicZone zone -> {
                if (nowInside && !inside && (!zone.once() || !fired)) {
                    switch (zone.music()) {
                        case MusicReference.Stock stock -> services().playMusic(stock.musicId());
                        case MusicReference.Namespaced music -> services().audioManager().playNamespacedMusic(
                                new StreamedMusicPort.TrackRef(music.owner(), music.localName()));
                    }
                    fired = true;
                }
            }
            case AuthoredObjectProperties.Warp warp -> {
                if (nowInside && !inside && cooldown == 0) warp(warp, player);
            }
            case AuthoredObjectProperties.StageplayTrigger trigger -> {
                boolean activation = trigger.trigger() == AuthoredObjectProperties.Trigger.BUTTON
                        ? up && !buttonHeld : !inside;
                if (!nowInside) pendingStageplay = false;
                else if (activation) pendingStageplay = true;
                if (nowInside && pendingStageplay && (!trigger.once() || !fired)) {
                    var playback = services().gameService(AuthoredStageplayService.class);
                    if (playback == null) throw new IllegalStateException("Authored Stageplay playback is not registered");
                    if (playback.play(trigger.resource(), trigger.blocking())) {
                        pendingStageplay = false;
                        fired = true;
                        if (trigger.consume()) ObjectLifetimeOps.destroyLatched(this);
                    }
                }
            }
        }
        inside = nowInside;
        buttonHeld = up;
    }

    private boolean contains(int x, int y) {
        return x >= spawn.x() && x < spawn.x() + properties.width()
                && y >= spawn.y() && y < spawn.y() + properties.height();
    }

    private void camera(AuthoredObjectProperties.CameraZone zone, int x, int y, boolean nowInside) {
        var camera = services().camera();
        if (camera == null) throw new IllegalStateException("Authored camera region requires a camera");
        if (!activeCamera && !releasedCamera && nowInside) {
            // Existing camera regions finish before another region borrows the camera. No
            // process-global lock or object reference is retained across rewind/recreation.
            boolean occupied = services().objectQuery().activeObjectsOfType(AuthoredGizmoInstance.class).stream()
                    .anyMatch(other -> other != this && other.activeCamera && !other.isDestroyed());
            if (!occupied) {
                oldMinX = camera.getMinX(); oldMaxX = camera.getMaxX();
                oldMinY = camera.getMinY(); oldMaxY = camera.getMaxY();
                oldMinXTarget = camera.getMinXTarget(); oldMaxXTarget = camera.getMaxXTarget();
                oldMinYTarget = camera.getMinYTarget(); oldMaxYTarget = camera.getMaxYTarget();
                camera.setMinX((short) zone.minX()); camera.setMaxX((short) zone.maxX());
                camera.setMinY((short) zone.minY()); camera.setMaxY((short) zone.maxY());
                activeCamera = true;
            }
        }
        boolean release = switch (zone.releaseCondition()) {
            case NONE -> !nowInside;
            case CROSS_X -> x >= zone.releaseAt();
            case CROSS_Y -> y >= zone.releaseAt();
        };
        if (activeCamera && release) {
            camera.setMinXCurrent((short) oldMinX); camera.setMaxXCurrent((short) oldMaxX);
            camera.setMinYCurrent((short) oldMinY); camera.setMaxYCurrent((short) oldMaxY);
            camera.setMinXTarget((short) oldMinXTarget); camera.setMaxXTarget((short) oldMaxXTarget);
            camera.setMinYTarget((short) oldMinYTarget); camera.setMaxYTarget((short) oldMaxYTarget);
            activeCamera = false;
            releasedCamera = zone.releaseCondition() != AuthoredObjectProperties.ReleaseCondition.NONE;
        }
    }

    private void warp(AuthoredObjectProperties.Warp warp, PlayableEntity player) {
        var matches = services().currentLevel().getObjects().stream()
                .filter(other -> other.authoredProperties() instanceof AuthoredObjectProperties.Warp target
                        && target.link().equals(warp.link())).toList();
        if (matches.size() != 2) throw new IllegalStateException("Warp link needs exactly two endpoints: " + warp.link());
        var target = matches.stream().filter(other -> other.layoutIndex() != spawn.layoutIndex()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Warp endpoints need distinct placement IDs"));
        if (!warp.twoWay() && spawn.layoutIndex() > target.layoutIndex()) return;
        var targetProperties = (AuthoredObjectProperties.Warp) target.authoredProperties();
        int exitX = AuthoredGizmos.exitX(target, targetProperties);
        if (exitX > 0xffff) throw new IllegalStateException("Warp exit is outside native world coordinates");
        NativePositionOps.writeXPosPreserveSubpixel(player, exitX);
        NativePositionOps.writeYPosPreserveSubpixel(player, target.y() + targetProperties.height() / 2);
        player.setXSpeed((short) 0); player.setYSpeed((short) 0); player.setGSpeed((short) 0);
        player.setOnObject(false); player.setPushing(false); player.setAir(true);
        cooldown = 30;
    }

    @Override public boolean isPersistent() { return true; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return false; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
