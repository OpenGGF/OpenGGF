package example.flappysample;

import com.openggf.audio.GameSound;
import com.openggf.game.DamageCause;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/**
 * Seizes the main player, drives flappy-bird-style gravity/flap physics via direct
 * position writes, force-scrolls the camera every frame, renders the Tails "bird" in
 * place of the (hidden) player, scores pipe passes as rings, and hands the player back
 * to engine-owned hurt/death/respawn on contact -- re-seizing once respawn settles.
 *
 * <p>All session state lives in non-final scalar fields so the default compact rewind
 * schema can capture/restore it (final scalar fields trip the FINAL_SCALAR_REWIND_GAP
 * validation); {@link #recreateForRewind} only needs to rebuild the object itself from
 * the (unchanged) spawn, matching {@link FlappyPipe}'s precedent.
 */
public final class FlappyController extends AbstractObjectInstance implements RewindRecreatable {
    /** ROM Tails mapping-frame indices for the flying pose (TailsAni_Fly: dc.b 1,$5E,$5F,$FF
     *  -- verified against docs/s2disasm/s2.asm:41625 and the two-piece spinning-tails
     *  mapping frames Map_Tails_0810/Map_Tails_0822, entries 94/95 of Map_unc_Tails). The
     *  ROM-baked "sample-flappy:bird" sheet preserves raw mapping-table order 1:1
     *  (RomArtMaterializer -> S2SpriteDataLoader.loadMappingFrames, DPLC remap keeps frame
     *  order), so these are also the sheet's own frame indices. */
    private static final int FLY_FRAME_A = 94;
    private static final int FLY_FRAME_B = 95;

    /** Forward speed: 0x200 subpixels/frame = exactly 2px/frame (no fractional carry). */
    private static final int FORWARD_SPEED = 0x200;
    /** Flap impulse: matches a snappy upward flick without a hard instant stop. */
    private static final int FLAP_VELOCITY = -0x400;
    /** Per-frame gravity accumulation, matching the shared S3K gravity constant used
     *  elsewhere in the engine for lightweight sprite-style falls. */
    private static final int GRAVITY = SubpixelMotion.S3K_GRAVITY;
    /** Terminal fall speed cap. */
    private static final int MAX_FALL_VELOCITY = 0x800;
    /** Half-width/half-height of the bird's contact box against a pipe body. */
    private static final int BIRD_HALF_SIZE = 8;
    /** Off-level kill bounds (world Y); the level itself is only 256px tall. */
    private static final int KILL_TOP_Y = 16;
    private static final int KILL_BOTTOM_Y = 240;

    // routine: 0 = waiting to seize, 1 = flying, 2 = released for hurt/death (waiting to
    // re-seize once the player is back to a normal, unowned state after respawn).
    private int routine;
    private int velY;          // subpixels/frame, 0x100 = 1px
    private int ySub;          // fractional y accumulator
    private int lastScoredX;   // rightmost pipe left-edge already scored
    private int bestScore;     // session best, rewind-captured
    private int animTick;      // bird flying-frame cycle

    public FlappyController(ObjectSpawn spawn) { super(spawn, "sample-flappy:controller"); }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        var player = (AbstractPlayableSprite) services().playerQuery().mainPlayerOrNull();
        if (player == null) return;
        // Layout objects despawn once they fall out of the camera's tracked range; track
        // the player's own position every frame so this controller never gets collected
        // while the level keeps scrolling out from under its original spawn point.
        updateDynamicSpawn(player.getCentreX(), player.getCentreY());

        switch (routine) {
            case 0 -> {
                player.applyObjectControlState(ObjectControlState.NATIVE_BIT_7_FULL_CONTROL);
                player.setHidden(true);
                velY = 0;
                ySub = 0;
                routine = 1;
            }
            case 1 -> {
                if (player.isJumpJustPressed()) {
                    velY = FLAP_VELOCITY;
                }
                velY = Math.min(velY + GRAVITY, MAX_FALL_VELOCITY);

                SubpixelMotion.State motion =
                        new SubpixelMotion.State(0, 0, 0, ySub, FORWARD_SPEED, velY);
                SubpixelMotion.moveSprite2(motion);
                ySub = motion.ySub;
                player.shiftX(motion.x);
                player.shiftY(motion.y);

                int px = player.getCentreX();
                int py = player.getCentreY();
                // requestForcedScroll takes the world FOCUS point the camera tracks (same
                // semantics as a focused sprite's centre -- Camera.java:1302-1319, MHZ
                // swing-vine precedent). Leading the focus 64px ahead of the bird keeps it
                // at screen x~=96 instead of dead-center, so oncoming pipes stay visible.
                services().camera().requestForcedScroll(px + 64, 112);

                scoreAndCollide(player, px, py, frameCounter);
                animTick++;
            }
            case 2 -> {
                // Engine-owned hurt/death/respawn is in flight. Re-seize once the player is
                // back to a normal, unowned state (checkpoint respawn resets hurt/dead via
                // LevelManager, matching neither flag being latched anymore).
                if (!player.isObjectControlled() && !player.isHurt() && !player.getDead()) {
                    routine = 0;
                }
            }
        }
    }

    private void scoreAndCollide(AbstractPlayableSprite player, int px, int py, int frame) {
        if (py < KILL_TOP_Y || py > KILL_BOTTOM_Y) {
            release(player, frame);
            player.applyHurtOrDeath(px, DamageCause.NORMAL, true);
            return;
        }
        for (FlappyPipe pipe : services().objectManager().activeObjectsOfType(FlappyPipe.class)) {
            if (px > pipe.rightEdge() && pipe.leftEdge() > lastScoredX) {
                lastScoredX = pipe.leftEdge();
                services().levelGamestate().addRings(1);
                services().playSfx(GameSound.RING);
                bestScore = Math.max(bestScore, services().levelGamestate().getRings());
            }
            boolean insideX = px + BIRD_HALF_SIZE > pipe.leftEdge() && px - BIRD_HALF_SIZE < pipe.rightEdge();
            boolean insideGap = py - BIRD_HALF_SIZE > pipe.gapTop() && py + BIRD_HALF_SIZE < pipe.gapBottom();
            if (insideX && !insideGap) {
                release(player, frame);
                player.applyHurtOrDeath(px, DamageCause.NORMAL, true);
                return;
            }
        }
    }

    private void release(AbstractPlayableSprite player, int frame) {
        player.setHidden(false);
        player.releaseFromObjectControl(frame);
        routine = 2;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 1) return;
        PatternSpriteRenderer renderer = getRenderer("sample-flappy:bird");
        if (renderer == null) return;
        var player = (AbstractPlayableSprite) services().playerQuery().mainPlayerOrNull();
        if (player == null) return;
        int frame = (animTick / 4) % 2 == 0 ? FLY_FRAME_A : FLY_FRAME_B;
        renderer.drawFrameIndex(frame, player.getCentreX(), player.getCentreY(), false, false);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new FlappyController(context.spawn());
    }
}
