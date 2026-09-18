package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * SKL {@code $58}, {@code Obj_DEZGravitySwitch} (sonic3k.asm:94800-94910).
 *
 * <p>The five Death Egg act 2 pressure pads. Unlike {@code $5B}, which <em>writes</em>
 * {@code Reverse_gravity_flag} from a crossing direction, this one <strong>toggles</strong>
 * it — {@code eori.b #1,(Reverse_gravity_flag).w} at {@code loc_48B7E} (:94879) — so the
 * same pad turns gravity on and off on alternate presses and the level needs only one of
 * them per room.
 *
 * <p>Three routines, in ROM order:
 *
 * <ol>
 *   <li><b>Armed</b> ({@code loc_48AD6}, :94809-94838). {@code SolidObjectFull} with
 *       {@code d1 = $1B, d2 = 8, d3 = 9}. A press is {@code d6 & $14} — Player 1's top
 *       <em>and</em> bottom contact bits, which is why the pad works from either face and
 *       therefore under either gravity. Player 2's pair is {@code $28} and only ever
 *       reaches the rider release, never the toggle. On a press the pad sinks 8 px
 *       (negated by its own {@code render_flags} bit 1), plays {@code sfx_Transporter},
 *       releases both players through {@code sub_48B40}, and sets {@code $30 = 3}.</li>
 *   <li><b>Counting</b> ({@code loc_48B7E}, :94876-94883). {@code subq.w #1,$30} each
 *       frame; on the frame the counter goes negative — the <em>fourth</em> update after
 *       the press — it toggles the flag, sets {@code $30 = 19} and moves on.</li>
 *   <li><b>Rearming</b> ({@code loc_48B9C}, :94886-94910). Still solid. While anything is
 *       standing on it ({@code btst #Status_OnObj,status(a0)}) the counter is reset to 0
 *       every frame, so the pad cannot rearm underneath a player who never left. Once
 *       clear, twenty frames later it rises back by the same 8 px and re-arms.</li>
 * </ol>
 *
 * <p>{@code sub_48B40} (:94845-94874) is the rider release and it is deliberately
 * <em>asymmetric</em>: the whole velocity/state wipe (anim, flip_type, double_jump_flag,
 * jumping, spin_dash_flag, ground_vel, x_vel, y_vel, {@code Status_InAir} set,
 * {@code Status_OnObj} cleared) runs for a player unconditionally, and only the final
 * {@code add.w d0,y_pos(a1)} nudge is masked by that player's own contact bits. It returns
 * immediately when {@code object_control(a1)} is non-zero.
 */
public final class S3kDezGravitySwitchObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SpawnRewindRecreatable {

    /** ROM {@code SolidObjectFull} arguments at :94810-94813: {@code d1 = $1B, d2 = 8, d3 = 9}. */
    private static final SolidObjectParams SOLID_PARAMS = SolidObjectParams.of(0x1B, 8, 9);

    /** ROM {@code move.w #3,$30(a0)} (:94822): the toggle lands on the fourth update after it. */
    private static final int TOGGLE_DELAY = 3;

    /** ROM {@code move.w #20-1,$30(a0)} (:94880). */
    private static final int REARM_DELAY = 19;

    /** ROM {@code moveq #8,d0} (:94823, :94903), negated by {@code render_flags} bit 1. */
    private static final int SINK_PIXELS = 8;

    private enum Routine { ARMED, COUNTING, REARMING }

    private Routine routine = Routine.ARMED;
    private int timer;
    private int mappingFrame;
    private boolean sunk;
    private boolean pressedThisFrame;
    private boolean occupiedThisFrame;

    public S3kDezGravitySwitchObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZGravitySwitch");
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }
        // ROM: andi.w #$14,d0 (:94817). $14 is Player 1's top and bottom contact bits --
        // the pad answers to being stood on and to being hit from underneath, which is the
        // whole point: under reverse gravity the player arrives at its other face. Player 2's
        // pair is $28 and reaches sub_48B40 only, never this test (:94826-94833).
        boolean topOrBottom = contact.touchTop() || contact.touchBottom();
        if (!isPlayerOne(player)) {
            return;
        }
        if (contact.standing() || topOrBottom) {
            occupiedThisFrame = true;
            if (routine == Routine.ARMED) {
                pressedThisFrame = true;
            }
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case ARMED -> {
                if (pressedThisFrame) {
                    press(player);
                }
            }
            case COUNTING -> {
                // loc_48B7E (:94876-94883): subq.w #1,$30 / bpl.s loc_48B96.
                if (--timer < 0) {
                    timer = REARM_DELAY;
                    toggleFlag();
                    routine = Routine.REARMING;
                }
            }
            case REARMING -> {
                // loc_48B9C (:94886-94910): while something is still on the pad the counter
                // is reset to 0 every frame, so it never rearms under a standing player.
                if (occupiedThisFrame) {
                    timer = 0;
                } else if (--timer < 0) {
                    mappingFrame = 0;
                    applySink(false);
                    routine = Routine.ARMED;
                }
            }
            default -> { }
        }
        pressedThisFrame = false;
        occupiedThisFrame = false;
    }

    private void press(PlayableEntity player) {
        mappingFrame = 1;
        timer = TOGGLE_DELAY;
        routine = Routine.COUNTING;
        applySink(true);
        releaseRider(player);
        // moveq #signextendB(sfx_Transporter),d0 / jsr (Play_SFX).l (:94832-94833), inside
        // the press branch: the toggle four updates later and the rearm are both silent.
        ObjectServices objectServices = tryServices();
        if (objectServices != null) {
            objectServices.playSfx(Sonic3kSfx.TRANSPORTER.id);
        }
    }

    /**
     * {@code sub_48B40} (:94845-94874). The state wipe is unconditional for a player whose
     * {@code object_control} is clear; only the 8 px nudge is masked by the contact bits,
     * and this pad is only ever pressed by the player it then releases.
     */
    private void releaseRider(PlayableEntity player) {
        if (player == null || player.isObjectControlled()) {
            return;
        }
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setAir(true);
        player.setOnObject(false);
    }

    /** {@code eori.b #1,(Reverse_gravity_flag).w} (:94879): a toggle, not a write. */
    private void toggleFlag() {
        ObjectServices objectServices = tryServices();
        GameStateManager gameState = objectServices == null ? null : objectServices.gameState();
        if (gameState == null) {
            return;
        }
        gameState.setReverseGravityActive(!gameState.isReverseGravityActive());
    }

    /**
     * The pad's own 8 px travel. {@code btst #1,render_flags(a0)} (:94824, :94904) negates
     * it for a Y-flipped placement, so a pad mounted on a ceiling sinks upward.
     */
    private void applySink(boolean down) {
        if (sunk == down) {
            return;
        }
        int delta = (spawn.renderFlags() & 2) != 0 ? -SINK_PIXELS : SINK_PIXELS;
        updateDynamicSpawn(getX(), (getY() + (down ? delta : -delta)) & 0xFFFF);
        sunk = down;
    }

    private boolean isPlayerOne(PlayableEntity player) {
        return !player.isCpuControlled();
    }

    /** Test/diagnostic accessors for the routine the pad is in. */
    public boolean isPressed() {
        return routine != Routine.ARMED;
    }

    public int mappingFrameForTest() {
        return mappingFrame;
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(routine, timer, mappingFrame, sunk));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot,
                                   RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            routine = extra.routine();
            timer = extra.timer();
            mappingFrame = extra.mappingFrame();
            sunk = extra.sunk();
        }
        pressedThisFrame = false;
        occupiedThisFrame = false;
    }

    private record RewindExtra(Routine routine, int timer, int mappingFrame, boolean sunk)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra { }

    /**
     * {@code Map_DEZGravitySwitch} through {@code make_art_tile(ArtTile_DEZMisc+$143,1,0)}
     * (:94801-94802): two frames from the level's own {@code ArtTile_DEZMisc} block, frame 0
     * the armed 32x16 pad and frame 1 the two-piece pressed pose. The placement's own
     * {@code render_flags} flip bits are kept -- the header only ever ORs bit 2 (:94803) --
     * so a pad mounted on a ceiling draws upside down as well as sinking upward.
     */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer =
                renderManager.getRenderer(Sonic3kObjectArtKeys.DEZ_GRAVITY_SWITCH);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }

    /** ROM {@code move.w #$280,priority(a0)} (:94805). */
    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x280);
    }
}
