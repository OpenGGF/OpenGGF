package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;

/**
 * One of the four body segments {@code Obj_Fireworm}'s head creates:
 * {@code ChildObjDat_8FA16} -> {@code loc_8F8F0} (sonic3k.asm:196345-196380, ROM {@code $8F8F0}).
 *
 * <p>Its init ({@code loc_8F910}, :196362-196372) takes {@code ObjDat3_8F9FC}
 * ({@code Map_FirewormSegments}, {@code make_art_tile(ArtTile_FirewormSegments,1,1)},
 * {@code priority $200}, an {@code 8 x 8} box, {@code mapping_frame} 1,
 * {@code collision_flags $98}), copies the head's {@code x_vel} and {@code render_flags}, and
 * seeds {@code $2E(a0)} from {@code word_8F940} -- {@code $B, $16, $21, $2C} indexed by the
 * child's own subtype, which {@code CreateChild1_Normal} numbers {@code 0, 2, 4, 6}. So each
 * segment waits eleven frames longer than the one in front of it, and that stagger is the whole
 * trailing shape: while it waits it does nothing at all, so the head swims away from it.
 *
 * <p>When the wait expires, {@code loc_8F94E} (:196387-196390) gives the segment its own flame
 * child and then falls into the head's {@code loc_8F82E}, so from that frame on the segment runs
 * the identical swim-and-turn routine. See {@link FirewormMotion}.
 *
 * <p>{@code Child_DrawTouch_Sprite_FlickerMove} (:178136-178141) only publishes the segment to the
 * collision list while the parent's {@code status} bit 7 is clear, which is why a segment stops
 * hurting the moment the head is gone.
 */
public final class FirewormSegmentInstance extends AbstractObjectInstance
        implements TouchResponseProvider, RewindRecreatable {

    /** {@code ObjDat3_8F9FC}: {@code dc.w $200} (sonic3k.asm:196444). */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x0200);
    /** {@code dc.b 8,8,1,$98} (:196445). */
    private static final int HALF_SIZE = 8;
    private static final int INITIAL_MAPPING_FRAME = 1;
    private static final int COLLISION_FLAGS = 0x98;
    /** {@code word_8F940}: {@code dc.w $B,$16,$21,$2C} (:196373-196374). */
    static final int[] WAIT_FRAMES = {0x0B, 0x16, 0x21, 0x2C};

    private enum Phase { WAITING, MOVING }

    /** ROM {@code subtype(a0)}: {@code CreateChild1_Normal}'s child index times two. */
    private int subtype;
    private Phase phase = Phase.WAITING;
    /** ROM {@code $2E(a0)} during the wait. */
    private int waitTimer;
    private int mappingFrame = INITIAL_MAPPING_FRAME;
    private boolean flipX;
    private boolean flipY;

    private final SubpixelMotion.State motion;
    private final FirewormMotion swim = new FirewormMotion();
    @RewindTransient(reason = "read-only ROM script window, reloaded lazily from the ROM reader")
    private S3kRawAnimation scripts;
    @RewindTransient(reason = "child link restored from an ObjectRefId sidecar in restoreRewindState")
    private FirewormFlameInstance flame;
    @RewindTransient(reason = "parent link reattached by FirewormHeadInstance.restoreRewindState")
    private FirewormHeadInstance head;

    FirewormSegmentInstance(int x, int y, int subtype, int parentXVel,
            boolean parentFlipX, boolean parentFlipY) {
        super(new ObjectSpawn(x & 0xFFFF, y & 0xFFFF, 0, subtype, 0, false, 0),
                "FirewormSegment");
        this.subtype = subtype & 0xFF;
        this.flipX = parentFlipX;
        this.flipY = parentFlipY;
        this.motion = new SubpixelMotion.State(x & 0xFFFF, y & 0xFFFF, 0, 0, parentXVel, 0);
        // move.w word_8F940(pc,d0.w),$2E(a0). SetUp_ObjAttributes already advanced the routine,
        // so loc_8F948's first decrement lands on the NEXT dispatch, as for the CaterKiller Jr
        // body: the init dispatch is its own frame.
        this.waitTimer = WAIT_FRAMES[(this.subtype >> 1) & 3] + 1;
    }

    /**
     * Probe constructor for {@code ObjectRewindDynamicCodecs.genericRecreate}, which builds an
     * instance from one of its known signatures before calling {@link #recreateForRewind}. The
     * six-argument creation constructor matches none of them, so without this the segments were
     * dropped on every composite restore.
     */
    private FirewormSegmentInstance(ObjectSpawn spawn) {
        this(spawn.x(), spawn.y(), spawn.subtype(), 0, false, false);
    }

    /** Rewind recreate: the generic field capturer reapplies every scalar afterwards. */
    public static FirewormSegmentInstance forRewindRecreate(ObjectSpawn spawn) {
        return new FirewormSegmentInstance(spawn.x(), spawn.y(), spawn.subtype(), 0, false, false);
    }

    @Override
    public FirewormSegmentInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                () -> forRewindRecreate(ctx.spawn()));
    }

    void attachHead(FirewormHeadInstance owner) {
        this.head = owner;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (phase == Phase.WAITING) {
            // loc_8F948: Obj_Wait, then loc_8F94E.
            waitTimer--;
            if (waitTimer < 0) {
                createFlame();
                swim.startSwim(motion, Sonic3kConstants.LRZ_FIREWORM_ANIM_SWIM_ADDR);
                swim.setMappingFrame(mappingFrame);
                phase = Phase.MOVING;
            }
            refreshFlame();
            return;
        }
        swim.update(motion, scripts(), Sonic3kConstants.LRZ_FIREWORM_ANIM_TURN_ADDR,
                () -> flipX = !flipX);
        mappingFrame = swim.mappingFrame();
        updateDynamicSpawn(motion.x, motion.y);
        refreshFlame();
    }

    /** {@code loc_8F94E}: {@code ChildObjDat_8FA30} at {@code (0,-$E)}. */
    private void createFlame() {
        int fx = (motion.x + (flipX ? -FirewormFlameInstance.CHILD_DX : FirewormFlameInstance.CHILD_DX))
                & 0xFFFF;
        int fy = (motion.y + (flipY ? -FirewormFlameInstance.CHILD_DY : FirewormFlameInstance.CHILD_DY))
                & 0xFFFF;
        flame = spawnChild(() -> new FirewormFlameInstance(fx, fy));
    }

    private record FlameLink(ObjectRefId flameId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    /**
     * The flame follows its segment through {@link #refreshFlame()} and never moves on its own,
     * so a lost link leaves it standing still after a restore while the segment swims away. The
     * reference therefore rides the snapshot as an identity id, the same way the head carries its
     * segments.
     */
    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId id = flame == null
                ? null
                : context.identityTable().map(table -> table.encodeObject(flame)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new FlameLink(id));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (!(snapshot.objectSubclassExtra() instanceof FlameLink link)) {
            return;
        }
        flame = null;
        if (link.flameId() == null) {
            return;
        }
        // Not required: loc_8F9EE deletes the flame with its segment's chain, so a missing link
        // must leave the segment flameless rather than throw.
        Object resolved = context.requireIdentityTable().resolveObject(link.flameId(), false);
        if (resolved instanceof FirewormFlameInstance restored) {
            flame = restored;
        }
    }

    private void refreshFlame() {
        if (flame != null && !flame.isDestroyed()) {
            flame.refreshFrom(motion.x & 0xFFFF, motion.y & 0xFFFF, flipX, flipY);
        }
    }

    private S3kRawAnimation scripts() {
        if (scripts == null) {
            try {
                scripts = S3kRawAnimation.load(services().romReader(),
                        Sonic3kConstants.LRZ_FIREWORM_RAW_ANIM_BASE_ADDR,
                        Sonic3kConstants.LRZ_FIREWORM_RAW_ANIM_SIZE);
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }
        return scripts;
    }

    /** ROM {@code $2E(a0)} while the segment still waits; {@code -1} once it moves. */
    public int waitTimer() {
        return phase == Phase.WAITING ? waitTimer : -1;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    /** ROM {@code x_vel(a0)}. */
    public int xVel() {
        return (short) motion.xVel;
    }

    /** ROM {@code y_vel(a0)}. */
    public int yVel() {
        return (short) motion.yVel;
    }

    public FirewormFlameInstance flame() {
        return flame;
    }

    public int getCentreX() {
        return motion.x & 0xFFFF;
    }

    public int getCentreY() {
        return motion.y & 0xFFFF;
    }

    @Override
    public int getCollisionFlags() {
        // Child_DrawTouch_Sprite_FlickerMove: no Add_SpriteToCollisionResponseList while the
        // parent's status bit 7 is set (sonic3k.asm:178136-178141).
        if (head != null && head.isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // make_art_tile(ArtTile_FirewormSegments,1,1) sets the priority bit.
        return true;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return HALF_SIZE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FIREWORM_SEGMENTS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), flipX, flipY);
    }
}
