package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Fireworm's head: {@code ChildObjDat_8FA0E} -> {@code loc_8F7A4}
 * (sonic3k.asm:196233-196275, ROM {@code $8F7A4}), created at {@code (0,-8)} from the placement
 * object {@link FirewormBadnikInstance}.
 *
 * <p>The head is the only attackable part. Its init ({@code loc_8F7EA}, :196259) takes
 * {@code ObjSlot_Fireworm} through {@code SetUp_ObjAttributesSlotted} -- {@code Map_Fireworm},
 * {@code make_art_tile(ArtTile_Fireworm,1,1)}, {@code priority $180}, a {@code $C x $C} box,
 * {@code mapping_frame} 0, {@code collision_flags $1A} -- and reserves two VRAM slots, because
 * this is the one part of the object with dynamic art: {@code loc_8F7A4} runs
 * {@code Perform_DPLC} from {@code DPLCPtr_Fireworm} every frame (:196238-196239).
 *
 * <p>{@code loc_8F7EA} falls straight through into {@code loc_8F7F4} (:196261-196267), which
 * enters routine 4, sets {@code $2E(a0) = 3}, points {@code $34(a0)} at {@code loc_8F81E} and
 * calls {@code Set_VelocityXTrackSonic} with {@code d4 = -$100}, so the head sets off at one pixel
 * a frame toward Player 1 and faces that way. Routine 4 ({@code loc_8F812}, :196269-196271) is
 * {@code MoveSprite2} plus {@code Obj_Wait}, i.e. four frames of straight travel, and then
 * {@code loc_8F81E} (:196277-196280) puts {@code mapping_frame} on 1 and creates the four segments
 * of {@code ChildObjDat_8FA16}, all at {@code (0,0)}.
 *
 * <p>From there the head runs the shared {@link FirewormMotion} swim-and-turn pair, which the
 * segments join one by one as their own delays expire.
 */
public final class FirewormHeadInstance extends AbstractS3kBadnikInstance
        implements SpawnRewindRecreatable {

    /** {@code ObjSlot_Fireworm}: {@code dc.b $C,$C,0,$1A} (sonic3k.asm:196441). */
    private static final int COLLISION_FLAGS = 0x1A;
    /** {@code dc.w $180} (:196440). */
    private static final int PRIORITY_BUCKET = 3;
    /** {@code move.w #-$100,d4} before {@code Set_VelocityXTrackSonic} (loc_8F7F4). */
    private static final int TRACK_SPEED = 0x100;
    /** {@code move.w #3,$2E(a0)} (loc_8F7F4). */
    private static final int TRAVEL_FRAMES = 3;
    /** {@code move.b #1,mapping_frame(a0)} (loc_8F81E). */
    private static final int SWIM_MAPPING_FRAME = 1;
    /** {@code ChildObjDat_8FA16}: {@code dc.w 4-1}, four segments, each at {@code (0,0)}. */
    private static final int SEGMENT_COUNT = 4;
    /** {@code ChildObjDat_8FA0E}: {@code dc.b 0,-8}. */
    static final int CHILD_DX = 0;
    static final int CHILD_DY = -8;

    private enum Phase { TRAVEL, SWIM }

    private Phase phase = Phase.TRAVEL;
    /** ROM {@code $2E(a0)} during routine 4. */
    private int travelTimer = TRAVEL_FRAMES;
    private final SubpixelMotion.State motion;
    private final FirewormMotion swim = new FirewormMotion();
    @RewindTransient(reason = "child links restored from ObjectRefId sidecars in restoreRewindState")
    private final List<FirewormSegmentInstance> segments = new ArrayList<>();
    @RewindTransient(reason = "read-only ROM script window, reloaded lazily from the ROM reader")
    private S3kRawAnimation scripts;
    private boolean velocityInitialised;

    public FirewormHeadInstance(ObjectSpawn spawn) {
        super(spawn, "FirewormHead", Sonic3kObjectArtKeys.FIREWORM,
                COLLISION_FLAGS, PRIORITY_BUCKET, true);
        this.mappingFrame = 0;
        this.currentX = spawn.x() & 0xFFFF;
        this.currentY = spawn.y() & 0xFFFF;
        this.motion = new SubpixelMotion.State(currentX, currentY, 0, 0, 0, 0);
    }

    @Override
    public FirewormHeadInstance recreateForRewind(RewindRecreateContext ctx) {
        return new FirewormHeadInstance(ctx.spawn());
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        if (!velocityInitialised) {
            // Set_VelocityXTrackSonic (sonic3k.asm:179327-179339): Find_OtherObject against
            // PLAYER 1 only -- not the nearer of the two -- then x_vel = -d4 and render_flags
            // bit 0 set when Player 1 is to the right.
            trackPlayerOne(playerEntity);
            velocityInitialised = true;
        }
        if (phase == Phase.TRAVEL) {
            SubpixelMotion.moveSprite2(motion);
            travelTimer--;
            if (travelTimer < 0) {
                // loc_8F81E.
                mappingFrame = SWIM_MAPPING_FRAME;
                createSegments();
                swim.startSwim(motion, Sonic3kConstants.LRZ_FIREWORM_ANIM_SWIM_ADDR);
                swim.setMappingFrame(mappingFrame);
                phase = Phase.SWIM;
            }
            publishPosition();
            return;
        }
        swim.update(motion, scripts(), Sonic3kConstants.LRZ_FIREWORM_ANIM_TURN_ADDR,
                () -> facingLeft = !facingLeft);
        mappingFrame = swim.mappingFrame();
        publishPosition();
    }

    private void publishPosition() {
        currentX = motion.x & 0xFFFF;
        currentY = motion.y & 0xFFFF;
    }

    private void trackPlayerOne(PlayableEntity playerEntity) {
        PlayableEntity p1 = playerEntity;
        if (p1 == null) {
            try {
                p1 = services().playerQuery().mainPlayerOrNull();
            } catch (Exception e) {
                p1 = null;
            }
        }
        boolean playerIsRight = p1 != null && p1.getCentreX() > currentX;
        facingLeft = !playerIsRight;
        motion.xVel = playerIsRight ? TRACK_SPEED : -TRACK_SPEED;
    }

    /** {@code loc_8F81E}: {@code CreateChild1_Normal} over {@code ChildObjDat_8FA16}. */
    private void createSegments() {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            final int subtype = i * 2;
            final int x = motion.x & 0xFFFF;
            final int y = motion.y & 0xFFFF;
            final int xVel = (short) motion.xVel;
            final boolean flip = !facingLeft;
            FirewormSegmentInstance segment = spawnChild(
                    () -> new FirewormSegmentInstance(x, y, subtype, xVel, flip, false));
            if (segment != null && !segment.isDestroyed()) {
                segment.attachHead(this);
                segments.add(segment);
            }
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

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        super.onPlayerAttack((AbstractPlayableSprite) playerEntity, result);
    }

    private record SegmentLinks(List<ObjectRefId> segmentIds)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        List<ObjectRefId> ids = context.identityTable()
                .map(table -> segments.stream().map(table::encodeObject).toList())
                .orElse(List.of());
        return super.captureRewindState(context).withObjectSubclassExtra(new SegmentLinks(ids));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (!(snapshot.objectSubclassExtra() instanceof SegmentLinks links)) {
            return;
        }
        segments.clear();
        for (ObjectRefId id : links.segmentIds()) {
            if (id == null) {
                continue;
            }
            Object resolved = context.requireIdentityTable().resolveObject(id, true);
            if (resolved instanceof FirewormSegmentInstance segment) {
                segment.attachHead(this);
                segments.add(segment);
            }
        }
    }

    /** ROM {@code x_vel(a0)}. */
    public int xVel() {
        return (short) motion.xVel;
    }

    /** ROM {@code y_vel(a0)}. */
    public int yVel() {
        return (short) motion.yVel;
    }

    /** ROM {@code mapping_frame(a0)}. */
    public int mappingFrame() {
        return mappingFrame;
    }

    /** ROM {@code $2E(a0)} during routine 4; {@code -1} once the head is swimming. */
    public int travelTimer() {
        return phase == Phase.TRAVEL ? travelTimer : -1;
    }

    /** ROM {@code $39(a0)}. */
    public int swingHalfCycles() {
        return swim.halfCycles();
    }

    /** {@code true} once the head has entered routine 8. */
    public boolean turning() {
        return swim.phase() == FirewormMotion.Phase.TURN;
    }

    public List<FirewormSegmentInstance> segments() {
        return List.copyOf(segments);
    }

    public int getCentreX() {
        return currentX;
    }

    public int getCentreY() {
        return currentY;
    }
}
