package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.ZeroArgRewindRecreatable;

import java.util.List;

/**
 * HCZ2 Moving Wall — invisible solid collision object for the Act 2 wall-chase sequence.
 *
 * <p>ROM: Obj_HCZ2Wall (sonic3k.asm:106277-106291).
 * This is a pure collision rectangle with no art — status bit 7 is set (invisible).
 *
 * <p>Collision rectangle: half-width $4B (75px), height $100 (256px).
 * ROM passes d1=$4B, d2=$100, d3=$100 to SolidObjectFull2.
 * Width_pixels field set to $40 (64px).
 *
 * <p>Y position is fixed at $700 (1792). X position is calculated from the wall offset:
 * {@code x = -wallOffset + $5BE} (negated offset + 1470).
 *
 * <p>Its own object slot reads the preceding completed background event. It
 * latches that event's wall offset and deletes itself when the background
 * routine is no longer 4; screen events never push a pose or a delete command.
 */
public class HCZ2WallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, ZeroArgRewindRecreatable, RomObjectCodePointerProvider {

    /**
     * Word 0 of this object's S3K SST holds its live ROM code pointer.
     * ROM {@code Obj_HCZ2Wall} (docs/skdisasm/sonic3k.asm:106277) is spawned by its parent rather than from the
     * object pointer table; every routine in its code block lies in the
     * {@code $0005xxxx} bank.
     * Its whole code block lies in one bank, so the HIGH word that
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} and compares
     * on the next off-screen on-object frame is {@code $0005}
     * (docs/skdisasm/sonic3k.asm:26856-26883).
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0005;
    }


    /** ROM: d1 = $4B (75 pixels half-width for SolidObjectFull2). */
    private static final int HALF_WIDTH = 0x4B;

    /** ROM: d2 = $100, d3 = $100 (256 pixels height for both air and ground). */
    private static final int HALF_HEIGHT = 0x100;

    /** ROM: y_pos set to $700 (1792 decimal). */
    private static final int FIXED_Y = 0x700;

    /** ROM: X base offset ($5BE = 1470 decimal). x_pos = -wallOffset + $5BE. */
    private static final int X_BASE_OFFSET = 0x5BE;

    /** Dummy spawn so SolidContacts can safely read objectId/subtype. */
    private static final ObjectSpawn DUMMY_SPAWN =
            new ObjectSpawn(X_BASE_OFFSET, FIXED_Y, 0xFF, 0, 0, false, 0);

    private int wallOffsetSupplierValue;

    /**
     * Create the wall object.
     * ROM: AllocateObject → move.l #Obj_HCZ2Wall,address(a1)
     */
    public HCZ2WallObjectInstance() {
        super(DUMMY_SPAWN, "HCZ2Wall");
    }

    @Override
    public int getX() {
        // ROM: move.w (Events_bg).w,d0 / neg.w d0 / addi.w #$5BE,d0 / move.w d0,x_pos(a0)
        return (short) (-wallOffsetSupplierValue + X_BASE_OFFSET);
    }

    @Override
    public int getY() {
        return FIXED_Y;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // Obj_HCZ2Wall reads Events_routine_bg and the accumulator in its own
        // slot, before the next screen-event publication: Obj_HCZ2Wall (sonic3k.asm:106277-106288).
        var registry = services().zoneRuntimeRegistry();
        var hcz = registry != null ? S3kRuntimeStates.currentHcz(registry).orElse(null) : null;
        if (hcz == null || hcz.backgroundRoutine() != 4) {
            setDestroyed(true);
            return;
        }
        wallOffsetSupplierValue = hcz.wallOffsetPixels();
        // SolidObjectFull2 collision is handled by the engine's SolidContacts system
        // automatically via the SolidObjectProvider interface.
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (player == null || player.getDead() || contact == null || !contact.touchSide()) {
            return;
        }

        // ROM FindFreeObj gives this wall an earlier SST slot than the placed
        // vertical hurt blocks in the chase corridor. The engine can load those
        // placements before the background event allocates the wall, reversing
        // just these solid checkpoints even though both objects retain valid
        // native slots. Re-run only earlier engine-slot hurt blocks after the
        // wall has applied its side separation, restoring the ROM's wall-then-
        // hazard order (Obj_HCZ2Wall / loc_1F66C, sonic3k.asm:106272-106290,
        // 43507-43535). The block's own face selector remains the authority for
        // whether the corrected position is lethal.
        int wallSlot = getSlotIndex();
        var objectManager = services().objectManager();
        for (Sonic3kInvisibleHurtBlockVObjectInstance block :
                objectManager.activeObjectsOfType(Sonic3kInvisibleHurtBlockVObjectInstance.class)) {
            if (block.getSlotIndex() >= wallSlot) {
                break;
            }
            objectManager.processImmediateInlineSolidCheckpoint(block, player, List.of());
            if (player.getDead()) {
                break;
            }
        }
    }

    @Override
    public boolean isTopSolidOnly() {
        // SolidObjectFull2: full solidity on all sides
        return false;
    }

    @Override
    public boolean bypassesOffscreenSolidGate() {
        // Obj_HCZ2Wall jumps directly to SolidObjectFull2, whose _1P entry
        // bypasses loc_1DF88's render_flags bit-7 gate. Its $4B collision box
        // can therefore push a player before the smaller $40 render bounds
        // enter the viewport (sonic3k.asm:106272-106290,41105-41107).
        return true;
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(
                usesStickyContactBuffer(),
                usesInclusiveRightEdge(),
                bypassesOffscreenSolidGate());
    }

    @Override
    public boolean isPersistent() {
        // Wall must stay active regardless of spawn position / camera window
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> cmds) {
        // ROM: bset #7,status(a0) — invisible, no rendering
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }
}
