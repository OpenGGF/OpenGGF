package com.openggf.game.sonic3k.objects;

import com.openggf.physics.Direction;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * SKL {@code $61}, {@code Obj_DEZGravityPuzzle} (sonic3k.asm:96087-96245).
 *
 * <p>One act 1 placement, {@code DEZ1_Sprites} record 301 at {@code $2690,$0840}, subtype
 * {@code $00} — inside the {@code $5F} turbine corridor's {@code $500} px reach, so it is the
 * obstacle in the turbine room rather than a feature of its own.
 *
 * <p><b>The shaft bobs and the panels are a shared RAM byte.</b> {@code angle(a0)} advances by
 * one an update and {@code GetSineCosine >> 2} of the previous value is added to the stored
 * centre in {@code $46(a0)} (:96104-96110), so the whole 32x48 block rides a ±64-subpixel sine.
 * Six 16x16 marker panels — three down each side at {@code ±$1C, -$20/0/+$20}
 * ({@code byte_49A5A}, :96236-96245) — live on a child sprite whose Y follows the same bob
 * (:96112-96120). Each panel's <em>pressed</em> state is one bit of
 * {@code MHZ_pollen_counter} ({@code bset d0,(MHZ_pollen_counter).w}, :96229): Mushroom Hill's
 * particle counter is reused here as the puzzle's panel bitfield, which is why this class keeps
 * it in {@link S3kDezZoneRuntimeState} rather than anywhere near the MHZ spawner.
 *
 * <p><b>The mapping frames read backwards from what the names suggest.</b>
 * {@code Map_DEZGravityPuzzle} frames 3 and 4 are <em>empty</em> (both point at
 * {@code word_49AAC}, zero pieces) and frames 1 and 2 are the single mirrored 16x16 marker. The
 * init loop hands each piece frame 3 (left column) or 4 (right), and a push runs
 * {@code subq.b #2} on it (:96234), so pressing a panel is what makes its marker appear.
 * {@code cmpi.b #3,(a2,d0.w) / blo} (:96232-96233) is the guard that stops a second push on the
 * same panel lowering the frame twice.
 *
 * <p><b>The panel a push marks is a clamped row, and a known ROM ordering bug picks the wrong
 * player for it.</b> {@code sub_49A0E} (:96198-96235) takes {@code y_pos(a1) - y_pos(a0) + $30},
 * floors it at 0, and replaces anything {@code >= $60} with {@code $40} — not {@code $60} —
 * before {@code lsr.w #5}, giving rows 0-2; {@code x_pos(a1) - x_pos(a0)} without a borrow adds
 * 3 for the right-hand column. Player 1's branch loads {@code a1} before calling it
 * (:96170-96171); <b>Player 2's branch calls it first and loads {@code a1} afterwards</b>
 * (:96177-96179). With {@code FixBugs = 0} that is kept: when both players push on the same
 * update, Player 2's push marks the panel under <em>Player 1</em>. When only Player 2 pushes,
 * {@code a1} still holds Player 2 from {@code SolidObjectFull2}'s own tail (:41062-41063) and
 * the panel is right. Fixing it would mark the panel under Player 2 in the two-player case.
 *
 * <p>The launch itself is {@code loc_49850} (:96049-96080), shared with
 * {@code Obj_DEZBumperWall}: {@code x_vel = ±$C00} away from the shaft, airborne,
 * {@code ground_vel = 1} negated for a left-facing player, and the endless tumble
 * ({@code flip_angle} 1 only when it was zero, {@code flips_remaining = -1},
 * {@code flip_speed = 4}, {@code anim = 0}).
 */
public final class S3kDezGravityPuzzleObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {

    /** {@code move.w #$23,d1 / #$30,d2 / #$31,d3} before {@code SolidObjectFull2} (:96121-96124). */
    private static final SolidObjectParams SOLID_PARAMS = SolidObjectParams.of(0x23, 0x30, 0x31);

    /** {@code asr.w #2,d0} (:96107): the bob is the sine quarter-scaled. */
    private static final int BOB_SHIFT = 2;

    /** {@code addi.w #$30,d0} (:96206) then {@code lsr.w #5} (:96217). */
    private static final int ROW_BIAS = 0x30;
    /** {@code cmpi.w #$60,d0 / blo} (:96212-96213). */
    private static final int ROW_LIMIT = 0x60;
    /** {@code moveq #$40,d0} (:96214): the replacement, which is not the limit. */
    private static final int ROW_CLAMP = 0x40;
    private static final int ROW_SHIFT = 5;
    /** {@code moveq #3,d1} (:96204): the right-hand column's first panel. */
    private static final int RIGHT_COLUMN = 3;

    /** {@code move.w #$C00,x_vel(a1)} (:96051). */
    private static final int LAUNCH_X_VEL = 0xC00;

    /** {@code byte_49A5A} (:96236-96245): six (dx, dy, frame) triples. */
    private static final int[] PIECE_DX = { -0x1C, -0x1C, -0x1C, 0x1C, 0x1C, 0x1C };
    private static final int[] PIECE_DY = { -0x20, 0x00, 0x20, -0x20, 0x00, 0x20 };
    private static final int[] PIECE_FRAME = { 3, 3, 3, 4, 4, 4 };
    /** {@code subq.b #2,-1(a2)} / {@code subq.b #2,(a2,d0.w)} (:96128, :96234). */
    private static final int PRESSED_FRAME_DROP = 2;

    /** {@code move.w #$280,priority(a0)} (:96093). */
    private static final int PRIORITY_WORD = 0x280;

    /** {@code angle(a0)}. */
    private int bobAngle;

    /**
     * The ROM's {@code a1} at {@code loc_499EC}: true once Player 1's branch has run this
     * update, which is what makes Player 2's push mark Player 1's panel.
     */
    private boolean playerOnePushedThisUpdate;

    public S3kDezGravityPuzzleObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZGravityPuzzle");
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        playerOnePushedThisUpdate = false;
        // loc_49986 :96101-96110. The sine of the *current* angle is used and the angle is
        // advanced afterwards, so the first update of the object's life sits at offset 0.
        int sine = TrigLookupTable.sinHex(bobAngle & 0xFF);
        int offset = sine >> BOB_SHIFT;
        bobAngle = (bobAngle + 1) & 0xFF;
        updateDynamicSpawn(getX(), (bobCentreY() + offset) & 0xFFFF);
    }

    @Override
    public boolean allowsObjectControlledSolidContacts() {
        // SolidObjectFull2 -> loc_1DFFE rejects only negative object_control.
        // The turbine room writes positive $01 and must still hit this solid.
        return true;
    }

    @Override
    public boolean rejectsBit7ObjectControlNewSolidContact(PlayableEntity player) {
        return true;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        // swap d6 / andi.w #1|2,d6 (:96126-96127) reads returned side-contact bits,
        // set by loc_1E094 even in air; status pushing bits are a separate value.
        if (player == null || !contact.touchSide()) {
            return;
        }
        AbstractPlayableSprite sprite = asSprite(player);
        if (sprite == null) {
            return;
        }
        if (isPlayerOne(player)) {
            markPanel(sprite);
            playerOnePushedThisUpdate = true;
            launch(sprite);
            return;
        }
        // loc_499EC (:96177-96179) with FixBugs = 0: sub_49A0E runs before a1 is reloaded, so
        // the panel marked belongs to whoever a1 already held -- Player 1 when Player 1 pushed
        // on this same update, and Player 2 otherwise because SolidObjectFull2 left a1 there.
        AbstractPlayableSprite panelOwner = sprite;
        if (playerOnePushedThisUpdate) {
            AbstractPlayableSprite playerOne = mainPlayerOrNull();
            if (playerOne != null) {
                panelOwner = playerOne;
            }
        }
        markPanel(panelOwner);
        launch(sprite);
    }

    /** {@code sub_49A0E} :96198-96235. */
    private void markPanel(AbstractPlayableSprite player) {
        // move.w x_pos(a1),d0 / sub.w x_pos(a0),d0 / bcs (:96201-96203): an unsigned borrow,
        // so a player at exactly the shaft's X counts as the right-hand column.
        int column = (player.getCentreX() & 0xFFFF) >= (getX() & 0xFFFF) ? RIGHT_COLUMN : 0;
        int row = (short) (player.getCentreY() - getY()) + ROW_BIAS;
        if (row < 0) {
            row = 0;
        }
        if (row >= ROW_LIMIT) {
            row = ROW_CLAMP;
        }
        setPanelPressed((row >> ROW_SHIFT) + column);
    }

    /**
     * {@code bset d0,(MHZ_pollen_counter).w} (:96229) plus the {@code cmpi.b #3 / blo} guard on
     * the piece's own frame (:96232-96233). A bit that is already set has a frame below 3, so
     * the guard and the bit are the same fact.
     */
    private void setPanelPressed(int index) {
        S3kDezZoneRuntimeState state = dezState();
        if (state == null || index < 0 || index >= PIECE_FRAME.length) {
            return;
        }
        state.setPanelBits(state.panelBits() | (1 << index));
    }

    /**
     * {@code loc_49850} :96049-96080, shared with {@code Obj_DEZBumperWall}'s
     * {@code sub_49848}.
     */
    private void launch(AbstractPlayableSprite player) {
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            // sub_49A02 (:96188-96191): the sound plays on the push, before the launch.
            objectServices.playSfx(Sonic3kSfx.TUNNEL_BOOSTER.id);
        }
        // bclr #Status_Facing / move.w #$C00,x_vel / sub.w x_pos(a0),d0 / bcc (:96050-96056).
        boolean toTheLeft = (player.getCentreX() & 0xFFFF) < (getX() & 0xFFFF);
        player.setDirection(toTheLeft ? Direction.LEFT : Direction.RIGHT);
        player.setXSpeed((short) (toTheLeft ? -LAUNCH_X_VEL : LAUNCH_X_VEL));
        player.setAir(true);
        player.setPushing(false);
        player.setDoubleJumpFlag(0);
        player.setRollingJump(false);
        player.setJumping(false);
        // move.w #1,ground_vel(a1) then neg.w for a left-facing player (:96064, :96078-96079).
        player.setGSpeed((short) (toTheLeft ? -1 : 1));
        // tst.b flip_angle(a1) / bne (:96065-96067): a player already tumbling keeps the angle
        // they had, so a second launch does not restart the spin from upright.
        if ((player.getFlipAngle() & 0xFF) == 0) {
            player.setFlipAngle(1);
        }
        player.setAnimationId(0);
        player.setFlipsRemaining(-1);
        player.setFlipSpeed(4);
    }

    // --- rendering ---

    /**
     * {@code Map_DEZGravityPuzzle} through {@code make_art_tile(ArtTile_DEZMisc2+$31,1,0)}
     * (:96088-96089). Frame 0 is the shaft itself, six 16x16 pieces; the marker panels are
     * frames 1 and 2 and only exist once pressed, because their unpressed frames 3 and 4 are
     * empty mapping entries.
     *
     * <p>The ROM draws the markers from a child object at priority {@code $200} against the
     * shaft's {@code $280} (:96099-96100, :96093). They are drawn here in the shaft's own
     * bucket instead: the markers sit at {@code ±$1C} either side of a {@code $20}-wide shaft,
     * so the two never overlap and the ordering is not observable.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer =
                renderManager.getRenderer(Sonic3kObjectArtKeys.DEZ_GRAVITY_PUZZLE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 1) != 0;
        boolean vFlip = (spawn.renderFlags() & 2) != 0;
        renderer.drawFrameIndex(0, getX(), getY(), hFlip, vFlip);
        for (int piece = 0; piece < PIECE_FRAME.length; piece++) {
            int frame = frameForPiece(piece);
            if (frame >= RIGHT_COLUMN) {
                // Frames 3 and 4 are word_49AAC, zero pieces: an unpressed panel draws nothing.
                continue;
            }
            renderer.drawFrameIndex(frame, getX() + PIECE_DX[piece], getY() + PIECE_DY[piece],
                    hFlip, vFlip);
        }
    }

    /** {@code move.b (a3)+,(a2)+} then {@code subq.b #2} per set bit (:96124-96128). */
    public int frameForPiece(int piece) {
        if (piece < 0 || piece >= PIECE_FRAME.length) {
            return 0;
        }
        boolean pressed = (panelBitsForTest() & (1 << piece)) != 0;
        return pressed ? PIECE_FRAME[piece] - PRESSED_FRAME_DROP : PIECE_FRAME[piece];
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(PRIORITY_WORD);
    }

    // --- helpers ---

    /**
     * {@code move.w y_pos(a0),$46(a0)} (:96094): the bob's centre is the placement's own Y,
     * written once at init and never changed. It is read from the immutable placement spawn
     * rather than stored, because {@code updateDynamicSpawn} moves the live spawn every update
     * and a stored copy would be object state rewind has to carry for no reason.
     */
    private int bobCentreY() {
        return spawn.y() & 0xFFFF;
    }

    private S3kDezZoneRuntimeState dezState() {
        ObjectServices objectServices = tryServices();
        if (objectServices == null || objectServices.zoneRuntimeRegistry() == null) {
            return null;
        }
        return S3kRuntimeStates.currentDez(objectServices.zoneRuntimeRegistry()).orElse(null);
    }

    private AbstractPlayableSprite mainPlayerOrNull() {
        ObjectServices objectServices = tryServices();
        if (objectServices == null || objectServices.playerQuery() == null) {
            return null;
        }
        return asSprite(objectServices.playerQuery().mainPlayerOrNull());
    }

    private static boolean isPlayerOne(PlayableEntity player) {
        return !player.isCpuControlled();
    }

    private static AbstractPlayableSprite asSprite(PlayableEntity entity) {
        return entity instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    /** Test accessors. */
    public int panelBitsForTest() {
        S3kDezZoneRuntimeState state = dezState();
        return state == null ? 0 : state.panelBits();
    }

    public int bobAngleForTest() {
        return bobAngle;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(bobAngle));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                   RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            bobAngle = extra.bobAngle();
        }
        playerOnePushedThisUpdate = false;
    }

    private record RewindExtra(int bobAngle)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

}
