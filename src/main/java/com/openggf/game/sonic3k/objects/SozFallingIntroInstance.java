package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.Tails;
import java.util.List;

/** Obj_LevelIntro_PlayerFallIntoGround, loc_41FEE–loc_42248. */
public final class SozFallingIntroInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, FreshLevelTitleOwnerReplacement {
    private boolean armed;
    private boolean initialized;
    private int routine;

    public SozFallingIntroInstance(ObjectSpawn spawn) { super(spawn, "SOZFallingIntro"); }

    /** The initial native object pass writes control bytes; it does not move players. */
    public void arm() {
        armed = true;
        for (var player : players()) {
            player.setControlLocked(true);
            player.clearForcedInputMask();
            // Native clr.w Ctrl_1/2_logical bypasses the input publisher's
            // lock-latching rule; physical pad edges remain available.
            player.clearLogicalInputState();
            if (player.getCpuController() != null) player.getCpuController().clearController2LogicalLatch();
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        }
    }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (!armed || !(leader instanceof AbstractPlayableSprite main)) return;
        if (!initialized) {
            initialized = true;
            arm(); // Obj_41FEE init-only setup dispatch, no movement until loc_42028.
            return;
        }
        var players = players();
        for (var player : players) {
            player.setControlLocked(true);
            player.clearForcedInputMask();
            // Native clr.w Ctrl_1/2_logical bypasses the input publisher's
            // lock-latching rule; physical pad edges remain available.
            player.clearLogicalInputState();
            if (player.getCpuController() != null) player.getCpuController().clearController2LogicalLatch();
        }
        switch (routine) {
            case 0 -> {
                for (var player : players) move(player, false);
                if ((main.getCentreY() & 0xFFFF) == (main instanceof Tails ? 0x694 : 0x690)) splash(players);
                if ((main.getCentreY() & 0xFFFF) >= 0x6C0) routine = 1;
            }
            case 1 -> {
                // loc_420A6 reads physical Ctrl_1_pressed, not cleared logical input.
                if (main.isRawControllerJumpJustPressed()) {
                    routine = 2;
                    for (var player : players) {
                        player.setYSpeed((short) -0x800);
                        player.setAir(true);
                        player.setJumping(false);
                        player.setAnimationId(0x10);
                        player.setObjectMappingFrameControl(false);
                        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
                    }
                }
            }
            case 2 -> {
                if ((main.getCentreY() & 0xFFFF) < 0x6A8) {
                    for (var player : players) {
                        ObjectControlState.none().applyTo(player);
                        player.setControlLocked(false);
                    }
                    splash(players);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                } else for (var player : players) move(player, true);
            }
            default -> throw new IllegalStateException("SOZ intro routine " + routine);
        }
    }

    private static void move(AbstractPlayableSprite player, boolean horizontal) {
        // sub_42092/sub_42160 use old velocity for position, then add word gravity.
        player.move((short) (horizontal ? player.getXSpeed() : 0), player.getYSpeed());
        player.setYSpeed((short) (player.getYSpeed() + 0x38));
    }
    private List<AbstractPlayableSprite> players() {
        return services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS)
                .stream().filter(AbstractPlayableSprite.class::isInstance)
                .map(AbstractPlayableSprite.class::cast).toList();
    }
    private void splash(List<AbstractPlayableSprite> players) {
        for (var player : players) {
            var spawn = new ObjectSpawn(player.getCentreX(), 0x66C, 0, 0, 0, false, 0);
            spawnChild(() -> new Splash(spawn));
        }
        services().playSfx(Sonic3kSfx.SAND_SPLASH.id);
    }
    public int routine() { return routine; }
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    /** One SST with two native display entries, loc_4219E. */
    public static final class Splash extends AbstractObjectInstance implements SpawnRewindRecreatable {
        private int timer = 0x13;
        private int animationTimer = 3;
        private int upperFrame = 8;
        private int lowerFrame = 0xD;
        private int upperY;
        private boolean flipped;
        public Splash(ObjectSpawn spawn) { super(spawn, "SOZIntroSplash"); upperY = spawn.y(); }
        @Override public void update(int vIntRunCount, PlayableEntity leader) {
            if (--animationTimer < 0) {
                animationTimer = 3;
                flipped = !flipped;
                upperY++;
                if (++lowerFrame >= 0xD) lowerFrame = 9;
            }
            if (timer == 0xC) upperFrame = 0xD;
            if (--timer < 0) ObjectLifetimeOps.deleteNoRespawn(this);
        }
        @Override public int getPriorityBucket() { return 1; }
        @Override public int getOnScreenHalfWidth() { return 0xC; }
        @Override public int getOnScreenHalfHeight() { return 0x34; }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_RISING_SAND_WALL);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(upperFrame, spawn.x(), upperY, flipped, false);
                renderer.drawFrameIndex(lowerFrame, spawn.x(), spawn.y() + 0x68, flipped, false);
            }
        }
    }
}
