package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Object 0x53 - MGZ Swinging Platform.
 *
 * <p>ROM: Obj_MGZSwingingPlatform (sonic3k.asm:70504-70602).
 * A platform on a chain of 4 links that rotates continuously around its pivot
 * at a constant angular velocity of 1 angle unit per frame. The player can
 * stand on the platform piece at the end of the chain (SolidObjectTop).
 *
 * <p>Subtype = initial angle byte (0-255).
 * Status bit 0 (x-flip) reverses rotation direction.
 * Status bit 1 (y-flip) selects chain link visual (frame 0 vs frame 1).
 */
public class MGZSwingingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider, ObjectVisualFacing.NonDirectional {

    // Native spin/swing state positions geometric platforms or radial balls and chain parts without facing.

    /**
     * Word 0 of this object's S3K SST holds its live ROM code pointer.
     * ROM {@code Obj_MGZSwingingPlatform} is installed from the S3K object pointer table at
     * {@code $00033F84} (table read from the user-supplied ROM; the
     * label is defined at docs/skdisasm/sonic3k.asm:70504).
     * Its whole code block lies in one bank, so the HIGH word that
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} and compares
     * on the next off-screen on-object frame is {@code $0003}
     * (docs/skdisasm/sonic3k.asm:26856-26883).
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0003;
    }

    private static final String ART_KEY = Sonic3kObjectArtKeys.MGZ_SWINGING_PLATFORM;

    // ROM: move.w #$200,priority(a0)
    private static final int PRIORITY_BUCKET = 4;

    // Mapping frames from Map_MGZSwingingPlatform
    private static final int FRAME_LINK = 0;     // Chain link (child mapframe default = 0)
    private static final int FRAME_PIVOT = 1;    // Pivot/anchor piece (child mapping_frame default)
    private static final int FRAME_PLATFORM = 2; // Platform piece (2 mirrored 24x24 halves)

    // ROM: mainspr_childsprites = 4
    private static final int LINK_COUNT = 4;
    private static final int ROM_CHILD_SLOT_COUNT = 1;

    // ROM: move.b #$18,width_pixels(a0)
    private static final int SOLID_HALF_WIDTH = 0x18;
    // ROM: move.b height_pixels(a0),d3; addq.w #1,d3 -> $0C + 1 = $0D
    private static final int SOLID_HALF_HEIGHT = 0x0D;

    private int pivotX;
    private int pivotY;
    private int pivotFrame; // ROM: child mapping_frame (1 default, 0 if y-flip)
    private int angleStep;  // +1 or -1

    private final int[] linkX = new int[LINK_COUNT];
    private final int[] linkY = new int[LINK_COUNT];

    private int platformX;
    private int platformY;
    private int angleByte;
    private boolean childSlotReserved;

    public MGZSwingingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MGZSwingingPlatform");
        this.pivotX = spawn.x();
        this.pivotY = spawn.y();

        // ROM: move.b subtype(a0),$34(a0) -- initial angle
        this.angleByte = spawn.subtype() & 0xFF;

        // ROM: btst #0,status(a0); neg.b $36(a0)
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.angleStep = xFlip ? -1 : 1;

        // ROM: status bit 1 selects pivot frame (set = frame 0, clear = frame 1).
        // Chain links always use frame 0 (zeroed mapframe from AllocateObjectAfterCurrent).
        boolean yFlip = (spawn.renderFlags() & 0x02) != 0;
        this.pivotFrame = yFlip ? FRAME_LINK : FRAME_PIVOT;

        updateChainPositions();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        reserveRomChildSlot();
        updateChainPositionsFromNativeInput();

        // ROM: move.b $36(a0),d0; add.b d0,$34(a0) -- constant angular velocity
        angleByte = (angleByte + angleStep) & 0xFF;
    }

    @Override
    public int getReservedChildSlotCount() {
        return ROM_CHILD_SLOT_COUNT;
    }

    private void reserveRomChildSlot() {
        if (childSlotReserved || getSlotIndex() < 0) {
            return;
        }
        childSlotReserved = true;
        ObjectServices svc = tryServices();
        if (svc == null || svc.objectManager() == null) {
            return;
        }
        // ROM Obj_MGZSwingingPlatform allocates a visual child immediately
        // after the parent with AllocateObjectAfterCurrent before entering
        // loc_3403A (docs/skdisasm/sonic3k.asm:70504-70546). The engine draws
        // that child inline, but its SST slot must remain reserved so later
        // ObjPosLoad/AllocateObject calls see the same low-slot pressure.
        svc.objectManager().allocateChildSlotsAfter(spawn, ROM_CHILD_SLOT_COUNT, getSlotIndex());
    }

    /**
     * ROM: sub_34074 -- computes chain link positions and platform endpoint.
     *
     * <p>GetSineCosine returns sin/cos for the current angle, each scaled to
     * 16.16 fixed-point by {@code swap; asr.l #4}. The accumulator starts at
     * the pivot and adds one step per link, with the platform at the 5th step.
     */
    private void updateChainPositions() {
        computeChainPositions(0);
    }

    private void updateChainPositionsFromNativeInput() {
        var manager = tryServices() != null ? services().objectManager() : null;
        int fraction = manager != null ? ObjectManagerInternalAccess.sineCosineInputFraction(manager) : 0;
        computeChainPositions(fraction);
        // loc_3403A (sonic3k.asm:70552-70554) zeroes the width accumulator
        // before SolidObjectTop; subsequent platforms inherit that zero.
        if (manager != null) ObjectManagerInternalAccess.publishSineCosineInputFraction(manager, 0);
    }

    private void computeChainPositions(int fraction) {
        int sinStep = TrigLookupTable.sinHex(angleByte) << 12;
        // GetSineCosine (sonic3k.asm:3053-3060) replaces the cosine word;
        // sub_34074 (sonic3k.asm:70569-70602) retains the incoming unsigned
        // fraction through the swap and arithmetic shift, for EACH chain step.
        int cosStep = cosineStep(angleByte, fraction);

        // ROM: d3 = pivotX << 16, d2 = pivotY << 16
        int accumX = pivotX << 16;
        int accumY = pivotY << 16;

        // ROM: loop over mainspr_childsprites, accumulating and writing positions
        for (int i = 0; i < LINK_COUNT; i++) {
            accumX += cosStep;
            accumY += sinStep;
            linkX[i] = accumX >> 16;
            linkY[i] = accumY >> 16;
        }

        // ROM: one more step for the platform position (x_pos/y_pos)
        accumX += cosStep;
        accumY += sinStep;
        platformX = accumX >> 16;
        platformY = accumY >> 16;
    }

    static int cosineStep(int angle, int inputFraction) {
        return (TrigLookupTable.cosHex(angle) << 12) + ((inputFraction & 0xFFFF) >>> 4);
    }

    // ===== SolidObjectProvider (SolidObjectTop) =====

    @Override
    public SolidObjectParams getSolidParams() {
        return SolidObjectParams.of(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean rejectsZeroDistanceTopSolidLanding() {
        // ROM SolidObjectTop reaches loc_1E45A, where d0 == 0 is rejected by
        // cmpi.w #-$10,d0 / blo (sonic3k.asm:42044-42045). Only the negative
        // overlap window [-$10, -1] proceeds to RideObject_SetRide.
        return true;
    }

    // ===== SolidObjectListener =====

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer == null) {
            return;
        }

        // Draw pivot and chain links first (behind platform, ROM child priority $280)
        renderer.drawFrameIndex(pivotFrame, pivotX, pivotY, false, false);
        for (int i = 0; i < LINK_COUNT; i++) {
            renderer.drawFrameIndex(FRAME_LINK, linkX[i], linkY[i], false, false);
        }

        // Draw platform last (in front, ROM parent priority $200)
        renderer.drawFrameIndex(FRAME_PLATFORM, platformX, platformY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx == null) {
            return;
        }
        // Pivot marker
        ctx.drawCross(pivotX, pivotY, 4, 1.0f, 1.0f, 0.0f);

        // Chain link connections
        int prevX = pivotX;
        int prevY = pivotY;
        for (int i = 0; i < LINK_COUNT; i++) {
            ctx.drawLine(prevX, prevY, linkX[i], linkY[i], 0.6f, 0.6f, 0.6f);
            prevX = linkX[i];
            prevY = linkY[i];
        }
        ctx.drawLine(prevX, prevY, platformX, platformY, 0.3f, 1.0f, 0.3f);

        // Platform collision box
        ctx.drawRect(platformX, platformY, SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT,
                0.3f, 1.0f, 0.3f);
    }

    // ===== Position (platform endpoint, not pivot) =====

    @Override
    public int getX() {
        return platformX;
    }

    @Override
    public int getY() {
        return platformY;
    }

    @Override
    public int getOutOfRangeReferenceX() {
        // ROM: move.w $30(a0),d0 before Sprite_OnScreen_Test2.
        return pivotX;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }

    /**
     * ROM {@code Obj_MGZSwingingPlatform} init stores
     * {@code move.b #$18,width_pixels(a0)} / {@code move.b #$C,height_pixels(a0)}
     * (docs/skdisasm/sonic3k.asm:70508-70509). Render_Sprites builds the
     * render_flags bit-7 box from those bytes, and that bit is the gate
     * SolidObjectTop tests before doing any solid work
     * (sonic3k.asm:41430-41432). The AbstractObjectInstance default of 16 is
     * narrower than $18 and taller than $C, so the platform stopped being
     * solid before the ROM's box left the screen. See pitfall P60.
     */
    @Override
    public int getOnScreenHalfWidth() {
        return 0x18;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return 0x0C;
    }
}
