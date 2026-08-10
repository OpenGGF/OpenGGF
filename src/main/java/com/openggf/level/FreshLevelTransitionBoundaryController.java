package com.openggf.level;

import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Owns the transient player/camera boundary around a fresh level title card. */
final class FreshLevelTransitionBoundaryController {
    private Boundary pending;

    void load(LevelManager level, int zone, int act) throws IOException {
        if (pending != null) {
            throw new IllegalStateException("a fresh title-card transition boundary is already pending");
        }
        short previousCameraX = level.camera.getX();
        short previousCameraY = level.camera.getY();
        int previousRings = level.levelGamestate != null ? level.levelGamestate.getRings() : 0;

        level.loadZoneAndActWithTitleCard(zone, act);

        List<PlayableState> playableStates = new ArrayList<>();
        for (Sprite sprite : level.spriteManager.getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable) {
                playableStates.add(new PlayableState(
                        playable.getCode(), playable.captureRewindState(false)));
            }
        }
        AbstractPlayableSprite destinationPlayer = mainPlayable(level);
        if (destinationPlayer != null) {
            level.camera.setFocusedSprite(destinationPlayer);
            level.camera.updatePosition(true);
        }
        short destinationCameraX = level.camera.getX();
        short destinationCameraY = level.camera.getY();

        level.camera.setX(previousCameraX);
        level.camera.setY(previousCameraY);
        if (level.levelGamestate != null) {
            level.levelGamestate.setRings(previousRings);
        }
        AbstractPlayableSprite player = mainPlayable(level);
        if (player != null) {
            player.setRolling(false);
            player.setCentreX((short) 0);
            player.setCentreY((short) 0);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setAir(false);
            player.setJumping(false);
            player.setAnimationId(0);
            player.setMappingFrame(0);
            player.setObjectRoutineOverride(0);
            player.setNativeSlotPresent(true);
        }
        for (AbstractPlayableSprite sidekick : level.spriteManager.getSidekicks()) {
            sidekick.setCentreX((short) 0);
            sidekick.setCentreY((short) 0);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setAir(false);
            sidekick.setNativeSlotPresent(false);
        }
        level.discardPendingInitialProcessSpritesForStateRestoration();
        pending = new Boundary(destinationCameraX, destinationCameraY, playableStates);
    }

    void complete(LevelManager level) {
        if (pending == null) {
            return;
        }
        restorePlayables(level);
        applyDestinationCamera(level);
        pending = null;
    }

    void publishInitial(LevelManager level) {
        if (pending == null) {
            return;
        }
        restorePlayables(level);
        AbstractPlayableSprite player = mainPlayable(level);
        if (player != null) {
            player.clearAirForNativeControlRestore();
            short centreY = player.getCentreY();
            player.setRolling(false);
            player.setCentreYPreserveSubpixel(centreY);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setJumping(false);
            player.setAnimationId(0);
            player.setMappingFrame(0);
            player.setAnimationFrameIndex(0);
            player.setAnimationTick(0);
            player.setForcedAnimationId(-1);
            player.setObjectMappingFrameControl(false);
            player.setTopSolidBit((byte) 0);
            player.setLrbSolidBit((byte) 0);
            player.setObjectRoutineOverride(0);
        }
        for (AbstractPlayableSprite sidekick : level.spriteManager.getSidekicks()) {
            sidekick.setNativeSlotPresent(false);
        }
        applyDestinationCamera(level);
    }

    void publishCamera(LevelManager level) {
        if (pending != null) {
            applyDestinationCamera(level);
        }
    }

    boolean isPending() {
        return pending != null;
    }

    private void restorePlayables(LevelManager level) {
        for (PlayableState playableState : pending.playableStates()) {
            Sprite sprite = level.spriteManager.getSprite(playableState.code());
            if (sprite instanceof AbstractPlayableSprite playable) {
                playable.restoreRewindState(playableState.state());
                playable.setObjectRoutineOverride(null);
                playable.setNativeSlotPresent(true);
            }
        }
    }

    private void applyDestinationCamera(LevelManager level) {
        AbstractPlayableSprite player = mainPlayable(level);
        if (player != null) {
            level.camera.setFocusedSprite(player);
        }
        level.camera.setX(pending.destinationCameraX());
        level.camera.setY(pending.destinationCameraY());
    }

    private static AbstractPlayableSprite mainPlayable(LevelManager level) {
        Sprite player = level.spriteManager.getSprite(level.transitionMainCharacterCode());
        return player instanceof AbstractPlayableSprite playable ? playable : null;
    }

    private record Boundary(
            short destinationCameraX,
            short destinationCameraY,
            List<PlayableState> playableStates) {
        private Boundary {
            playableStates = List.copyOf(playableStates);
        }
    }

    private record PlayableState(String code, PerObjectRewindSnapshot state) {}
}
