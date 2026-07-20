package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x4A in zone 0x14 - Pachinko round bumper.
 *
 * <p>This shares the same core bumper physics as the CNZ bumper path in the ROM, but
 * uses the Pachinko-specific mappings and vertical off-screen despawn behavior.
 */
public class PachinkoBumperObjectInstance extends AbstractObjectInstance implements RewindRecreatable {

    private static final int BOUNCE_VELOCITY = 0x700;
    private static final int COLLISION_HALF_WIDTH = 8;
    private static final int COLLISION_HALF_HEIGHT = 8;
    private static final int ANIM_DURATION = 8;

    private int animFrame;
    private int animTimer;

    public PachinkoBumperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoBumper");
    }

    @Override
    public PachinkoBumperObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new PachinkoBumperObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (animTimer > 0) {
            animTimer--;
            if (animTimer == 0) {
                animFrame = 0;
            }
        }

        // ROM sub_32F34 (sonic3k.asm:68934-68949) has no cooldown timer: it clears
        // collision_property and re-bounces every frame the bit reads set, i.e. every
        // frame Add_SpriteToCollisionResponseList's touch-loop still finds the player
        // overlapping (matching the general S3K ENEMY-touch per-frame poll, not an
        // edge-triggered "already hit" latch). The player's hitbox can stay inside the
        // bumper's for 2+ consecutive frames after a bounce (observed: Pachinko trace
        // frame 1318 then 1319 both resolve a fresh bounce from the bumper at
        // (0x104,0x7E0), each using that frame's already-updated player position --
        // an invented per-object cooldown here suppressed the second resolve and froze
        // x_speed/y_speed at the frame-1318 value instead of the frame-1319 re-bounce).
        if (playerEntity instanceof AbstractPlayableSprite player
                && !player.isHurt()
                && !player.getDead()
                && checkCollision(player)) {
            applyBounce(player, frameCounter);
        }
    }

    private boolean checkCollision(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - spawn.x());
        int dy = Math.abs(player.getCentreY() - spawn.y());
        return dx < (COLLISION_HALF_WIDTH + 8)
                && dy < (COLLISION_HALF_HEIGHT + player.getYRadius());
    }

    private void applyBounce(AbstractPlayableSprite player, int frameCounter) {
        int dx = spawn.x() - player.getCentreX();
        int dy = spawn.y() - player.getCentreY();
        int angle = TrigLookupTable.calcAngle((short) dx, (short) dy);

        // ROM sub_32F34/sub_32F56 (sonic3k.asm:68953-68968): `move.b
        // (Level_frame_counter).w,d1` reads the HIGH byte of the big-endian
        // Level_frame_counter word (no +1 offset, unlike the many call sites that
        // read `(Level_frame_counter+1).w` for the low/fast-changing byte), then
        // `andi.w #3,d1` keeps only its bottom 2 bits -- i.e. bits 8-9 of the full
        // word counter, not the raw per-object update() frameCounter parameter
        // (which is ObjectManager's internal vblaCounter, offset from
        // Level_frame_counter by a large non-multiple-of-4 constant -- see
        // PachinkoItemOrbObjectInstance.resolveRomFrameCounter). Mirrors the S2
        // CNZ Obj_Bumper port (BumperObjectInstance.applyBounce, s2.asm:44675-44677)
        // which reads the same ROM-aligned counter for the identical bias term.
        int romFrameCounter = resolveRomFrameCounter(frameCounter);
        angle = (angle + ((romFrameCounter >> 8) & 3)) & 0xFF;

        int cosVal = TrigLookupTable.cosHex(angle);
        int sinVal = TrigLookupTable.sinHex(angle);
        player.setXSpeed((short) (cosVal * -BOUNCE_VELOCITY >> 8));
        player.setYSpeed((short) (sinVal * -BOUNCE_VELOCITY >> 8));
        player.setAir(true);
        player.setPushing(false);
        player.setJumping(false);
        // ROM sub_32F56 never writes ground_vel(a1) -- the pre-bounce inertia is
        // left untouched (observed: engine cleared g_speed to 0 while the ROM
        // trace kept g_speed=0x0800 constant across the bounce, Pachinko trace
        // frame 1318 onward).

        animFrame = 1;
        animTimer = ANIM_DURATION;

        try {
            services().playSfx(GameSound.BUMPER);
        } catch (Exception e) {
            // Keep gameplay logic independent from audio state.
        }
        services().gameState().addScore(10);
    }

    /**
     * Resolves the ROM-aligned {@code Level_frame_counter} equivalent for the bounce-angle
     * bias. Falls back to the raw {@code update()} parameter only when the object manager is
     * unavailable (e.g. bare unit-test construction without full services wiring). See
     * {@link PachinkoItemOrbObjectInstance#resolveRomFrameCounter} for why the per-object
     * {@code update()} frameCounter parameter is not ROM-aligned in this zone.
     */
    private int resolveRomFrameCounter(int updateParamFrameCounter) {
        try {
            return services().objectManager().getFrameCounter();
        } catch (Exception e) {
            return updateParamFrameCounter;
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(1);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_BUMPER);
        if (renderer == null) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(animFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }
}
