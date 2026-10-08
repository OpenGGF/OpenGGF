package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;

import java.util.List;

/**
 * {@code Obj_56EA0} (sonic3k.asm:115568-115631, ROM {@code $56EA0}): the lava surface inside a
 * Lava Reef act 1 dome region. It is allocated by {@code loc_56E40} the frame the region locks
 * and deletes itself at {@code loc_56EC2} the frame {@code Events_bg+$00} clears, so it exists
 * exactly while the background is locked.
 *
 * <p><b>The rise and fall.</b> {@code $2E(a0)} is a 32-bit position and {@code $32(a0)} a 32-bit
 * velocity, with {@code $36(a0)} the direction flag. Each frame the velocity is added to the
 * position and then stepped by {@code $100}; when the position crosses zero the routine parks it
 * at zero, reloads the velocity with {@code ±$C000} and flips the flag. The position's <em>high
 * word</em> is published as {@code _unkEE9C}, which is both the platform's own offset -- its
 * {@code y_pos} is {@code $988 - _unkEE9C} -- and the term {@code sub_56DAC} adds to
 * {@code Camera_Y_pos_BG_copy}, so the dome background rides the lava exactly.
 *
 * <p>Init clears {@code _unkEE9C} ({@code clr.w (_unkEE9C).w}), so the phase restarts at zero on
 * every entry to a region, however long the player was away. {@code $34(a0) = -$4000} is written
 * at init and never read anywhere in the routine; it is kept here only as the ROM field it is.
 *
 * <p><b>The burn is asymmetric, and that is the shipped behaviour.</b> After
 * {@code SolidObjectTop}, {@code loc_56F3E} tests Player 1's standing bit and skips
 * {@code sub_24280} when {@code Status_FireShield} is set; {@code loc_56F54} does the same test
 * for Player 2 <em>without</em> any shield check, so a fire-shielded sidekick still burns. There
 * is no {@code FixBugs} switch near it: this is what the ROM does, and the engine reproduces it.
 */
public final class LrzDomeLavaPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RewindRecreatable {

    /** {@code move.w #$1E80,x_pos(a0)}: the object ignores where it was allocated. */
    public static final int FIXED_X = 0x1E80;
    /** {@code addi.w #$988,d0} after negating the phase. */
    public static final int SURFACE_BASE_Y = 0x0988;
    /** {@code move.b #$80,height_pixels(a0)}. */
    private static final int HEIGHT_PIXELS = 0x80;
    /** {@code move.l #-$4000,$34(a0)}: written at init, never read. */
    private static final int UNUSED_34 = -0x4000;
    /** {@code move.l #-$100,d2} / {@code neg.l d2}: the per-frame velocity step. */
    private static final int VELOCITY_STEP = 0x100;
    /** {@code move.l #$C000,d1} / {@code #-$C000,d1}: the velocity reloaded at each turn. */
    private static final int VELOCITY_RELOAD = 0xC000;
    /** {@code move.w #$280,d1 / #$80,d2 / #$6C,d3} before {@code SolidObjectTop}. */
    private static final int SOLID_HALF_WIDTH = 0x280;
    private static final int SOLID_HEIGHT_AIR = 0x80;
    private static final int SOLID_HEIGHT_GROUND = 0x6C;

    /** ROM {@code $2E(a0)}, a signed long. */
    private int position;
    /** ROM {@code $32(a0)}, a signed long. */
    private int velocity;
    /** ROM {@code $36(a0)}: zero while the surface is falling, {@code $FF} while it rises. */
    private boolean rising;

    public LrzDomeLavaPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "LRZDomeLavaPlatform");
        publishPhase(0);
    }

    @Override
    public LrzDomeLavaPlatformObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> new LrzDomeLavaPlatformObjectInstance(ctx.spawn()));
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        LrzZoneRuntimeState state = stateOrNull();
        // loc_56EC2: the object's whole life is the locked background's.
        if (state == null || !state.domeRegionLocked()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        step();
        publishPhase(phase());
        updateDynamicSpawn(FIXED_X, getCentreY());
    }

    /** {@code loc_56ECE} through {@code loc_56F0C}. */
    private void step() {
        int step = rising ? VELOCITY_STEP : -VELOCITY_STEP;
        int next = position + velocity;
        if (rising) {
            if (next < 0) {
                velocity += step;
                position = next;
                return;
            }
            position = 0;
            velocity = VELOCITY_RELOAD;
            rising = false;
            return;
        }
        if (next > 0) {
            velocity += step;
            position = next;
            return;
        }
        // loc_56EFC covers both the zero and the positive case (beq then bpl).
        position = 0;
        velocity = -VELOCITY_RELOAD;
        rising = true;
    }

    /** {@code swap d0 / move.w d0,(_unkEE9C).w}: the position's high word. */
    public int phase() {
        return (position >> 16) & 0xFFFF;
    }

    private void publishPhase(int value) {
        LrzZoneRuntimeState state = stateOrNull();
        if (state != null) {
            state.setDomePlatformPhase(value);
        }
    }

    private LrzZoneRuntimeState stateOrNull() {
        try {
            return S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.standing()) {
            return;
        }
        // loc_56F3E / loc_56F54: Player 1's burn is skipped for a fire shield, Player 2's is not.
        boolean isSidekick = player == nativeP2OrNull();
        if (!isSidekick && hasFireShield(player)) {
            return;
        }
        burn(player, frameCounter);
    }

    /** {@code sub_24280} (sonic3k.asm:36000-36020 region, ROM {@code $24280}). */
    private void burn(PlayableEntity player, int frameCounter) {
        if (player.getDead() || player.getInvulnerable()) {
            return;
        }
        boolean rings = player.getRingCount() > 0;
        if (player.isCpuControlled()) {
            player.applyHurt(FIXED_X, com.openggf.game.DamageCause.NORMAL);
            return;
        }
        if (rings && !player.hasShield()) {
            services().spawnLostRings(player, frameCounter);
        }
        player.applyHurtOrDeath(FIXED_X, com.openggf.game.DamageCause.NORMAL, rings);
    }

    private boolean hasFireShield(PlayableEntity player) {
        return player.getShieldType() == com.openggf.game.ShieldType.FIRE;
    }

    private PlayableEntity nativeP2OrNull() {
        try {
            return services().playerQuery().nativeP2OrNull();
        } catch (Exception e) {
            return null;
        }
    }

    /** ROM {@code $32(a0)}. */
    public int velocity() {
        return velocity;
    }

    /** ROM {@code $36(a0)}. */
    public boolean rising() {
        return rising;
    }

    /** ROM {@code $34(a0)}, written once and never read. */
    public static int unusedField34() {
        return UNUSED_34;
    }

    public int getCentreX() {
        return FIXED_X;
    }

    /** {@code neg.w d0 / addi.w #$988,d0 / move.w d0,y_pos(a0)}. */
    public int getCentreY() {
        return (SURFACE_BASE_Y - (short) phase()) & 0xFFFF;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HEIGHT_AIR, SOLID_HEIGHT_GROUND);
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.topSolid(false);
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() {
        // There is no MarkObjGone or delete-if-out-of-range anywhere in Obj_56EA0: it lives and
        // dies on Events_bg+$00 alone.
        return true;
    }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return false;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return SOLID_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HEIGHT_PIXELS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Obj_56EA0 never reaches Draw_Sprite: the lava the player sees is the dome background
        // and the animated tiles, not a sprite.
    }
}
