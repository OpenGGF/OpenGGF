package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.List;

/**
 * Launch Base Zone Act 1 startup controller.
 *
 * <p>ROM: {@code Obj_LevelIntro_PlayerLaunchFromGround} at
 * {@code docs/skdisasm/sonic3k.asm:77207}. The object locks player input for
 * 30 frames while the player sits at the LBZ1 start position inside the terrain,
 * then applies the upward spring launch and releases control once y_pos rises
 * above {@code $05C0}.
 */
public final class Lbz1GroundLaunchIntroInstance extends AbstractObjectInstance {
    private static final int PRE_LAUNCH_DELAY_FRAMES = 30;
    private static final int RELEASE_Y = 0x05C0;
    private static final short LAUNCH_Y_SPEED = (short) -0x0B00;

    private static final int STATE_PRE_LAUNCH = 0;
    private static final int STATE_LAUNCHING = 1;

    private int state = STATE_PRE_LAUNCH;
    private int timer = PRE_LAUNCH_DELAY_FRAMES;
    private boolean armed;
    private boolean initialized;

    public Lbz1GroundLaunchIntroInstance(ObjectSpawn spawn) {
        super(spawn, "LBZ1GroundLaunchIntro");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!armed) {
            return;
        }
        if (!initialized) {
            initialized = true;
            holdPlayersBeforeLaunch();
        }

        if (state == STATE_PRE_LAUNCH) {
            updatePreLaunch();
        } else {
            updateLaunching();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Controller object only; ROM object has no visible sprite.
    }

    public void applyInitialHoldForLevelStart() {
        armed = true;
        initialized = true;
        holdPlayersBeforeLaunch();
    }

    private void updatePreLaunch() {
        holdPlayersBeforeLaunch();
        if (--timer > 0) {
            return;
        }
        beginLaunch();
        state = STATE_LAUNCHING;
    }

    private void updateLaunching() {
        holdInputLocked();
        List<PlayableEntity> players = players();
        PlayableEntity mainPlayer = players.isEmpty() ? null : players.getFirst();
        if (mainPlayer == null || (mainPlayer.getCentreY() & 0xFFFF) < RELEASE_Y) {
            for (PlayableEntity player : players) {
                if (player instanceof AbstractPlayableSprite sprite) {
                    releasePlayer(sprite);
                }
            }
            // ROM (loc_39AD2): on emergence each player's Dust object plays anim 4
            // (the splash) snapped to y=$5C0 / x=player x, then sfx_SandSplash fires.
            triggerSurfaceSplashes(players);
            services().playSfx(Sonic3kSfx.SAND_SPLASH.id);
            setDestroyed(true);
            return;
        }

        for (PlayableEntity player : players) {
            movePlayerWithCurrentVelocity(player);
        }
    }

    private void holdPlayersBeforeLaunch() {
        holdInputLocked();
        for (PlayableEntity player : players()) {
            if (player instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(true);
                sprite.clearForcedInputMask();
                ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            }
        }
    }

    private void beginLaunch() {
        for (PlayableEntity player : players()) {
            player.setYSpeed(LAUNCH_Y_SPEED);
            player.setAir(true);
            if (player instanceof AbstractPlayableSprite sprite) {
                sprite.setJumping(false);
                applySpringLaunchAnimation(sprite);
                sprite.setControlLocked(true);
                sprite.clearForcedInputMask();
                ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            }
        }
    }

    private void applySpringLaunchAnimation(AbstractPlayableSprite sprite) {
        sprite.setAnimationId(Sonic3kAnimationIds.SPRING);
        sprite.forceAnimationRestart();
        sprite.setAnimationFrameIndex(0);
        sprite.setAnimationTick(0);

        var animationSet = sprite.getAnimationSet();
        if (animationSet == null) {
            return;
        }
        var springScript = animationSet.getScript(Sonic3kAnimationIds.SPRING.id());
        if (springScript == null || springScript.frames().isEmpty()) {
            return;
        }
        sprite.setMappingFrame(springScript.frames().getFirst());
    }

    private void holdInputLocked() {
        for (PlayableEntity player : players()) {
            if (player instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(true);
                sprite.clearForcedInputMask();
            }
        }
    }

    private void movePlayerWithCurrentVelocity(PlayableEntity player) {
        player.move(player.getXSpeed(), player.getYSpeed());
        player.setYSpeed((short) (player.getYSpeed() + 0x38));
    }

    /**
     * Plays the surface splash on each player's dust controller. The ROM mutates the
     * pre-existing fixed Dust object (no allocation); the engine models that object as
     * the per-player {@link SpindashDustController}, so the splash is driven there
     * rather than spawned as an ObjectManager/SST object. This keeps engine object
     * slots identical to the ROM and out of trace nearby-object snapshots.
     */
    private void triggerSurfaceSplashes(List<PlayableEntity> players) {
        PlayerSpriteRenderer splashRenderer = surfaceSplashRenderer();
        if (splashRenderer == null) {
            return;
        }
        for (PlayableEntity player : players) {
            if (player instanceof AbstractPlayableSprite sprite) {
                SpindashDustController dust = sprite.getSpindashDustController();
                if (dust != null) {
                    // ROM loc_18BEC snaps x to the parent player's x_pos; y is the
                    // surface line ($5C0). status is cleared (no flip) -> faces right.
                    dust.triggerSurfaceSplash(splashRenderer, sprite.getCentreX(), RELEASE_Y);
                }
            }
        }
    }

    private PlayerSpriteRenderer surfaceSplashRenderer() {
        GameModule module = services().gameModule();
        if (module == null) {
            return null;
        }
        ObjectArtProvider provider = module.getObjectArtProvider();
        return (provider instanceof Sonic3kObjectArtProvider s3k) ? s3k.getSurfaceSplashRenderer() : null;
    }

    private void releasePlayer(AbstractPlayableSprite player) {
        player.setControlLocked(false);
        ObjectControlState.none().applyTo(player);
        player.clearForcedInputMask();
    }

    private List<PlayableEntity> players() {
        return services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS);
    }
}
