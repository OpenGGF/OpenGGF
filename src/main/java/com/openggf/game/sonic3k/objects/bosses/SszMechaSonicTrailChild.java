package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;

/**
 * ROM {@code loc_7C902} (and {@code loc_7C8FE}, which is the same routine with {@code addq.b #4}
 * on the subtype first): the after-image Mecha Sonic leaves behind while it is dashing.
 *
 * <p>{@code ObjDat3_7D402} gives it {@code Map_MechaSonicExtra} on
 * {@code make_art_tile(ArtTile_MechaSonicExtra,0,1)}, priority {@code $200}, {@code $C} by 4,
 * frame 0 and no collision; {@code sub_7D236} then overwrites the child offset and the priority
 * from {@code byte_7D24C}, and the init plays {@code sfx_Roll}.
 *
 * <p><b>The subtypes step by two, which is what makes the table line up.</b> {@code sub_7D236}
 * does {@code add.w d0,d0} on the subtype against rows that are four bytes wide, so it only
 * addresses whole rows because {@code CreateChild6_Simple} numbers its children with
 * {@code addq.w #2,d2} — 0, 2, 4 — and not 0, 1, 2. {@code loc_7C8FE} then adds four to the
 * subtype before falling into this routine, so {@code ChildObjDat_7D486}'s pair lands on rows 2
 * and 3 where {@code ChildObjDat_7D47A}'s lands on rows 0 and 1, and
 * {@code ChildObjDat_7D480}'s {@code move.b #8,subtype(a1)} takes row 4. Five rows, each used
 * exactly once.
 *
 * <p>Reading the stride as one-per-child instead gives subtype 1 a priority word of {@code $81C},
 * which is not a sprite-table offset at all; that is how this was caught.
 *
 * <p>{@code loc_7C91C} reads the parent every frame and deletes itself the moment the parent's
 * {@code status} bit 7 is set or its {@code $38} bit 2 is clear, so the trail is not something
 * the boss cleans up: it goes when the dash goes.
 */
public final class SszMechaSonicTrailChild extends AbstractObjectInstance
        implements RewindRecreatable {

    /** {@code ObjDat3_7D402}: {@code dc.w $200} / {@code dc.b $C,4,0,0}. */
    private static final int DEFAULT_PRIORITY_WORD = 0x200;
    private static final int HALF_WIDTH = 0x0C;
    private static final int HALF_HEIGHT = 4;

    /**
     * {@code byte_7D24C} as bytes, addressed the way {@code sub_7D236} addresses it —
     * {@code subtype * 2} — so the even subtypes select whole rows:
     * {@code dc.b -4,$1C / dc.w $300}, and four more.
     */
    private static final int[] CHILD_TABLE = {
            0xFC, 0x1C, 0x03, 0x00,
            0x08, 0x1C, 0x02, 0x00,
            0xF8, 0x1C, 0x02, 0x00,
            0x04, 0x1C, 0x03, 0x00,
            0x0C, 0x0C, 0x02, 0x00,
    };

    private final SszMechaSonicObjectInstance parent;
    private final int subtype;
    private final int childDx;
    private final int childDy;
    private final int priorityWord;

    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();
    /** A lazily sliced read-only window over the ROM's script block; nothing to restore. */
    private transient S3kRawAnimation animator;
    private int x;
    private int y;
    /** {@code loc_7C902} is the object's first dispatch, not its allocation. */
    private boolean initialised;

    public SszMechaSonicTrailChild(ObjectSpawn spawn, SszMechaSonicObjectInstance parent) {
        super(spawn, "SSZMechaSonicTrail");
        this.parent = parent;
        this.subtype = spawn.subtype() & 0xFF;
        int offset = (subtype * 2) & 0xFF;
        this.childDx = offset < CHILD_TABLE.length ? (byte) CHILD_TABLE[offset] : 0;
        this.childDy = offset + 1 < CHILD_TABLE.length ? (byte) CHILD_TABLE[offset + 1] : 0;
        this.priorityWord = offset + 3 < CHILD_TABLE.length
                ? (CHILD_TABLE[offset + 2] << 8) | CHILD_TABLE[offset + 3]
                : DEFAULT_PRIORITY_WORD;
        this.x = spawn.x();
        this.y = spawn.y();
        this.anim.script = Sonic3kConstants.SSZ_MECHA_TRAIL_ANIM_ADDR;
    }

    /** Probe/rewind constructor: the spawn alone, with the parent re-resolved on restore. */
    public SszMechaSonicTrailChild(ObjectSpawn spawn) {
        this(spawn, null);
    }

    private record RewindExtra(int animFrame, int animFrameTimer, int mappingFrame, int x, int y,
                               boolean initialised)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(anim.animFrame, anim.animFrameTimer, anim.mappingFrame, x, y,
                        initialised));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            anim.animFrame = extra.animFrame();
            anim.animFrameTimer = extra.animFrameTimer();
            anim.mappingFrame = extra.mappingFrame();
            x = extra.x();
            y = extra.y();
            initialised = extra.initialised();
        }
    }

    /**
     * The captured spawn carries the subtype, and the subtype is what {@code sub_7D236} turns
     * into this child's offset and priority, so the recreate has to take the context's spawn and
     * not this instance's.
     */
    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszMechaSonicTrailChild(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) {
            return;
        }
        if (!initialised) {
            // loc_7C902's own init: SetUp_ObjAttributes, sub_7D236 and then sfx_Roll. It is a
            // dispatch, not an allocation, so a rewind-recreated child does not replay it.
            initialised = true;
            services().playSfx(Sonic3kSfx.ROLL.id);
        }
        animate();
        if (parent == null || parent.isDestroyed() || !parent.trailVisible()) {
            // loc_7C942: Go_Delete_Sprite the moment the parent stops dashing.
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        // Refresh_ChildPositionAdjusted: the offset is applied against the parent's facing.
        boolean flipped = parent.renderFlippedForTest();
        x = (parent.getX() + (flipped ? -childDx : childDx)) & 0xFFFF;
        y = (parent.getY() + childDy) & 0xFFFF;
    }

    private void animate() {
        if (animator == null) {
            try {
                animator = S3kRawAnimation.load(services().romReader(),
                        Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_ADDR,
                        Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_SIZE);
            } catch (IOException | RuntimeException ignored) {
                return;
            }
        }
        // Animate_RawNoSSTMultiDelay is Animate_RawMultiDelay with the script supplied by the
        // caller rather than read from $30(a0); byte_7D65F ends in $FC and never reaches $F4.
        animator.animateMultiDelay(anim, () -> { });
    }

    @Override public int getX() { return x; }

    @Override public int getY() { return y; }

    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }

    @Override public int getOnScreenHalfHeight() { return HALF_HEIGHT; }

    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(priorityWord); }

    public int subtypeForTest() { return subtype; }
    public int childDxForTest() { return childDx; }
    public int childDyForTest() { return childDy; }
    public int priorityWordForTest() { return priorityWord; }
    public int mappingFrameForTest() { return anim.mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_EXTRA);
        if (renderer != null && renderer.isReady()) {
            // ObjDat3_7D402 is make_art_tile(ArtTile_MechaSonicExtra,0,1) -- line 0, where the
            // spark object's ObjDat_MechaSonic_Sparks uses line 1 off the same sheet, so this
            // one passes its line explicitly rather than taking the sheet's.
            renderer.drawFrameIndex(anim.mappingFrame, x, y,
                    parent != null && parent.renderFlippedForTest(), false, 0);
        }
    }
}
