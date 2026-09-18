package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * SKL {@code $5B}, {@code Obj_DEZGravitySwap} (sonic3k.asm:95472-95543).
 *
 * <p>An invisible Death Egg act 2 trigger: the eleven placements sit in open corridor and
 * write {@code Reverse_gravity_flag} when Player 1 crosses their X, provided the crossing
 * happens inside a {@code ±$20} band around the object's {@code y_pos}. Which crossing
 * direction turns gravity <em>on</em> is chosen by the placement's {@code render_flags}
 * bit 0 (the X-flip bit), so the level can put a "gravity starts here" trigger and a
 * "gravity ends here" trigger on the same corridor without any subtype.
 *
 * <p><strong>It writes, it never toggles.</strong> Both crossing bodies run
 * {@code move.b #0,(Reverse_gravity_flag).w} first and only then conditionally
 * {@code move.b #1}, so a crossing inside the band always leaves a definite value
 * (:95511-95514, :95536-95539). Crossing back out the way you came writes the opposite
 * value, which is how the corridor's gravity survives a player who changes their mind.
 *
 * <p><strong>Player 1 only.</strong> {@code Obj_DEZGravitySwap}'s body passes
 * {@code lea (Player_1).w,a1} and never runs for Player 2 (:95217-95219) — unlike
 * {@code $58}, which at least reads Player 2's contact bits. A sidekick crossing the
 * trigger does nothing at all.
 *
 * <p>Side state. {@code $32(a0)} is the latch the two bodies switch on: the init
 * (:95472-95478) seeds it from the player's position at spawn — 1 when the object's
 * {@code x_pos} is below the player's, meaning the player already stands to the right —
 * and each crossing flips it <em>before</em> the Y-band test, so a crossing outside the
 * band still consumes the latch without writing the flag. {@code $30(a0)} is the constant
 * {@code $20} half-height and is modelled as a constant rather than a field.
 */
public final class S3kDezGravitySwapObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {

    /** ROM {@code move.w #$20,$30(a0)} (sonic3k.asm:95473): half-height of the active Y band. */
    private static final int Y_BAND_HALF_HEIGHT = 0x20;

    /**
     * ROM {@code $32(a0)}: 0 while the player is on the object's left, 1 once it is on the
     * right. Seeded lazily on the first update rather than at construction, because the
     * ROM's init routine runs with the player where it is when the object comes into
     * range, and {@code Delete_Sprite_If_Not_In_Range} re-runs it on every respawn.
     */
    private boolean playerOnRight;
    private boolean sideLatchSeeded;

    public S3kDezGravitySwapObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZGravitySwap");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (player == null) {
            return;
        }
        int objectX = getX() & 0xFFFF;
        int playerX = player.getCentreX() & 0xFFFF;

        if (!sideLatchSeeded) {
            // ROM init (sonic3k.asm:95474-95478): cmp.w x_pos(a1),d1 / bhs.s loc_4920E
            // leaves $32 at 0 when the object is at or right of the player; the fallthrough
            // move.b #1,$32(a0) marks "player already on the right".
            playerOnRight = objectX < playerX;
            sideLatchSeeded = true;
            // No rts between `move.l #loc_49214,(a0)` and loc_49214 (:95483-95484): the
            // init falls straight into the first crossing check, in the same frame. That
            // is not cosmetic -- an object seeded at exactly the player's x keeps the
            // "player on the left" latch (bhs) and then fails the "still on the left"
            // test (bhi), so it fires on its own spawn frame.
        }

        if (!playerOnRight) {
            // sub_49228 (:95489-95491): cmp.w x_pos(a1),d1 / bhi.s locret — the player is
            // still on the left while the object's x is strictly greater.
            if (objectX > playerX) {
                return;
            }
            playerOnRight = true;
            // move.b #1,-1(a2) lands before the band test: the latch is consumed either way.
            applyCrossing(player, !xFlipped());
            return;
        }

        // loc_49270 (:95518-95520): cmp.w x_pos(a1),d1 / bls.s locret — still on the right
        // while the object's x is lower or the same.
        if (objectX <= playerX) {
            return;
        }
        playerOnRight = false;
        applyCrossing(player, xFlipped());
    }

    /**
     * The shared tail of both bodies (:95495-95517 and :95524-95542): the Y-band test, the
     * debug-placement test, then {@code move.b #0} followed by the conditional
     * {@code move.b #1}.
     *
     * @param setsFlag what {@code btst #0,render_flags(a0)} selects for this crossing
     *                 direction. Left-to-right sets gravity when bit 0 is <em>clear</em>
     *                 ({@code bne.s locret} skips the {@code move.b #1}); right-to-left
     *                 sets it when bit 0 is <em>set</em> ({@code beq.s locret}).
     */
    private void applyCrossing(PlayableEntity player, boolean setsFlag) {
        int bandCentre = (short) (getY() & 0xFFFF);
        int top = bandCentre - Y_BAND_HALF_HEIGHT;
        int bottom = bandCentre + Y_BAND_HALF_HEIGHT;
        int playerY = (short) (player.getCentreY() & 0xFFFF);
        // cmp.w d2,d4 / blt.s locret  then  cmp.w d3,d4 / bge.s locret: signed word
        // comparisons, so the band is [y_pos - $20, y_pos + $20).
        if (playerY < top || playerY >= bottom) {
            return;
        }
        // tst.w (Debug_placement_mode).w / bne.s locret (:95508, :95533). The engine has no
        // debug placement mode during gameplay, so this reads as zero -- the same treatment
        // the other Debug_placement_mode sites in the S3K objects carry.
        ObjectServices objectServices = tryServices();
        GameStateManager gameState = objectServices == null ? null : objectServices.gameState();
        if (gameState == null) {
            return;
        }
        gameState.setReverseGravityActive(setsFlag);
    }

    /** {@code btst #0,render_flags(a0)}: the placement's X-flip bit. */
    private boolean xFlipped() {
        return (spawn.renderFlags() & 1) != 0;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(playerOnRight, sideLatchSeeded));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                   RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            playerOnRight = extra.playerOnRight();
            sideLatchSeeded = extra.sideLatchSeeded();
        }
    }

    private record RewindExtra(boolean playerOnRight, boolean sideLatchSeeded)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    /** Invisible: the ROM object has no mappings and never reaches Draw_Sprite. */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) { }
}
