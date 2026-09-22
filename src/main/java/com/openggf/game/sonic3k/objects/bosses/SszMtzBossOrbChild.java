package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Tails;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM {@code loc_7AD8A} (sonic3k.asm:163663-163830): the seven orbs the Metropolis recreation
 * carries, and the one object that does the fight's actual damage.
 *
 * <p><b>There is no separate controller.</b> {@code loc_7A72C} allocates one slot with
 * {@code loc_7AD8A} and {@code $34 = a0}; that slot's routine 0 ({@code loc_7ADA2}) opens
 * {@code movea.l a0,a1}, so the <em>first</em> orb it sets up is itself and it allocates the other
 * six. The native rows agree: in the {@code hpz} segment of
 * {@code s3k-sonic-tails-complete-emeralds} slots 25-32 all appear on frame 6190, one frame after
 * the ship's first dispatch — the Mecha Sonic head and seven orbs, in a single object pass.
 *
 * <p><b>The orbit</b> ({@code sub_7AEB0}). {@code $2F(a0)} is a fixed {@code $40}, so the first
 * {@code GetSineCosine} is always {@code $100}; the radii are the ship's own {@code $38}/{@code
 * $3A} arm lengths. {@code $2E(a0)} — the horizontal angle, seeded per orb from
 * {@code byte_7AE14} — advances four a frame and gives {@code x_pos}; its cosine gives
 * {@code $3A(a0)}, the depth, which {@code sub_7AF5A} turns into one of four priorities and one of
 * three mapping frames. That sort is the front/back ordering of the ring around the ship.
 * {@code $41(a0)} is the vertical angle and normally advances eight a frame; while the ship's
 * {@code $3B} is non-zero it is replaced by {@code $42(a0)}, which walks to {@code $40} and back
 * so the ring flattens and re-opens around a hit.
 *
 * <p><b>The launch.</b> {@code loc_7AE22} watches the ship's {@code $39(a0)} — set by
 * {@code sub_7AC06} on the frame the hit window reaches {@code $1F} — and the first orb to see it
 * takes it, so which orb flies is decided by slot order, not by position. It leaves with
 * {@code y_vel -$400} and a {@code +/-$80} horizontal term chosen from the player's side and then
 * clamped to keep it inside {@code [$16A0,$1760)}. Its collision byte is <em>not</em> cleared on the
 * way out: {@code loc_7ADB2}'s {@code $87} stands for the whole {@code $3C}-frame delay, so a
 * launched orb is plain {@code Touch_ChkHurt} harm until {@code loc_7AFA4} writes {@code $C6} and
 * makes it poppable.
 * {@code loc_7AFA4} then runs {@code MoveSprite} — which applies the standard {@code $38} gravity
 * — with an extra {@code -$20} of its own and a {@code $180} terminal speed, lands on the arena
 * floor at {@code $42C} and hands over to {@code loc_7B02A}'s bounce.
 *
 * <p><b>What pops it.</b> {@code sub_7B0C2} reads {@code collision_property(a0)} — the shared
 * touch response stores which player hit it there — checks that player is attacking, hurts them if
 * not, and either way sets {@code routine 8}. It also pops with no touch at all once
 * {@code _unkFA88} is set, which is the ship's own defeat. {@code loc_7B116} plays
 * {@code sfx_Balloon}, decrements the ship's {@code $30(a0)} and deletes.
 */
public final class SszMtzBossOrbChild extends AbstractObjectInstance
        implements RewindRecreatable, TouchResponseProvider, TouchResponseListener {

    /** {@code moveq #7-1,d3}. */
    public static final int ORB_COUNT = 7;
    /** {@code byte_7AE14}: the horizontal angle each orb starts on. */
    public static final int[] START_ANGLE = {0x24, 0x6C, 0xB4, 0xFC, 0x48, 0x90, 0xD8};
    /** {@code byte_7AE1B}: {@code $40(a0)}, which nothing in this fight ever reads back. */
    static final int[] START_GROUP = {0, 1, 1, 0, 1, 1, 0};
    /** {@code move.b #$40,$2F(a1)}: the constant the first {@code GetSineCosine} runs on. */
    private static final int RADIUS_ANGLE = 0x40;
    /** {@code move.b #$20,width_pixels(a1)}. */
    private static final int HALF_SIZE = 0x20;
    /** {@code move.w #$180,priority(a1)} until the first {@code sub_7AF5A}. */
    private static final int PRIORITY_INITIAL = 0x180;
    /** {@code sub_7AF5A}'s four buckets. */
    public static final int PRIORITY_FRONT_NEAR = 0x100;
    public static final int PRIORITY_FRONT_FAR = 0x080;
    public static final int PRIORITY_BACK_NEAR = 0x300;
    public static final int PRIORITY_BACK_FAR = 0x380;
    /** {@code cmpi.w #$C,d0} / {@code cmpi.w #-$C,d0}: the depth band the near frames cover. */
    public static final int DEPTH_BAND = 0x0C;

    /** {@code move.b #$87,collision_flags(a1)} at setup. */
    public static final int COLLISION_ORBITING = 0x87;
    /** {@code move.b #$C6,collision_flags(a0)} once the launch's {@code $3C} runs out. */
    public static final int COLLISION_LAUNCHED = 0xC6;
    /**
     * {@code move.b #$DA,collision_flags(a0)} once the bounce's {@code $3C} runs out.
     *
     * <p>In practice it never does. {@code $3C} is a byte counted down with {@code subq.b} and
     * tested with {@code bmi}, and it is not reloaded at the landing — so a flight longer than
     * {@code $3C} frames leaves it already negative and the orb keeps the launch's {@code $C6}
     * for the whole bounce. The gravity arithmetic makes every normal flight that long. The
     * branch is modelled as written rather than removed.
     */
    public static final int COLLISION_BOUNCING = 0xDA;

    /** {@code move.b #$3C,$3C(a0)} on launch. */
    public static final int ARM_DELAY_FRAMES = 0x3C;
    /** {@code move.w #-$400,y_vel(a0)}. */
    private static final int LAUNCH_Y_VEL = -0x400;
    /** {@code move.w #$80,d1} / {@code #-$80}. */
    private static final int LAUNCH_X_VEL = 0x80;
    /** {@code cmpi.w #$16A0,x_pos(a0)} / {@code #$1760}: the launch's horizontal clamp. */
    private static final int LAUNCH_CLAMP_LEFT = 0x16A0;
    private static final int LAUNCH_CLAMP_RIGHT = 0x1760;
    /** {@code addi.w #$38,y_vel(a0)} inside {@code MoveSprite}. */
    private static final int GRAVITY = 0x38;
    /** {@code subi.w #$20,y_vel(a0)}: the orb's own term, on top of the gravity above. */
    private static final int LIFT = 0x20;
    /** {@code cmpi.w #$180,y_vel(a0)}: terminal downward speed. */
    private static final int TERMINAL_Y_VEL = 0x180;
    /** {@code cmpi.w #$42C,y_pos(a0)}: the arena floor, and where the bounce sits. */
    public static final int FLOOR_Y = 0x42C;
    /** {@code subi.w #4,$30(a0)}: the orbit centre is four pixels above the ship. */
    private static final int ORBIT_CENTRE_DY = -4;
    /** {@code addq.b #4,$2E(a0)} and {@code addq.b #8,$41(a0)}. */
    private static final int HORIZONTAL_STEP = 4;
    private static final int VERTICAL_STEP = 8;
    /** {@code cmpi.b #$40,$42(a0)} / {@code subq.b #2}: the flatten-and-reopen walk. */
    private static final int FLATTEN_LIMIT = 0x40;
    private static final int FLATTEN_STEP = 2;
    /** {@code move.w #$10,d2} while the ship's {@code $3B} is set. */
    private static final int FLATTENED_RADIUS = 0x10;
    /** {@code move.b #$E,mapping_frame(a0)}: the pop's own frame. */
    public static final int POP_FRAME = 0x0E;
    /** {@code byte_7B233}: the launch animation, three frames a step, ending looped on 8. */
    private static final int[] LAUNCH_ANIM = {2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 3, 4, 3, 4, 5, 6, 7, 8};
    private static final int LAUNCH_ANIM_DELAY = 3;
    /** The gate {@code loc_7B02A} bounces on: only once the animation has settled on frame 8. */
    public static final int BOUNCE_GATE_FRAME = 8;
    /** {@code asr.w #2} on the negated sine, added to {@code $38(a0)}. */
    private static final int BOUNCE_SHIFT = 2;

    public static final int ROUTINE_SETUP = 0;
    public static final int ROUTINE_ORBITING = 2;
    public static final int ROUTINE_LAUNCHED = 4;
    public static final int ROUTINE_BOUNCING = 6;
    public static final int ROUTINE_POPPING = 8;

    private SszMtzBossObjectInstance parent;
    /** Which {@code byte_7AE14} row this orb was set up from; slot order in one value. */
    private int index;

    private int routine = ROUTINE_ORBITING;
    /** 16.16 position, as {@code MoveSprite} reads it. */
    private int xPos;
    private int yPos;
    private int xVel;
    private int yVel;
    /** {@code $2E(a0)}, {@code $41(a0)} and {@code $42(a0)}: the three angles. */
    private int horizontalAngle;
    private int verticalAngle;
    private int flattenAngle;
    /** {@code $3E(a0)} and {@code $30(a0)}: this frame's orbit centre. */
    private int centreX;
    private int centreY;
    /** {@code $3A(a0)}: the depth the sort reads. */
    private int depth;
    /** {@code $3C(a0)}: the collision delay after a launch or a landing. */
    private int collisionDelay = -1;
    /** {@code $32(a0)}: the bounce angle, and {@code $38(a0)}: the floor it bounces from. */
    private int bounceAngle;
    private int bounceFloor = FLOOR_Y;
    private int collisionFlags = COLLISION_ORBITING;
    private int priorityWord = PRIORITY_INITIAL;
    private int mappingFrame;
    private boolean renderFlipped;
    private int animStep = -1;
    private int animTimer;
    /** {@code anim(a0)} against {@code next_anim(a0)}: true from the frame the script is selected. */
    private boolean animating;
    /** {@code collision_property(a0)}: which player {@code loc_103FA} credited the touch to. */
    private int collisionProperty;

    public SszMtzBossOrbChild(ObjectSpawn spawn, SszMtzBossObjectInstance parent, int index) {
        super(spawn, "SSZMTZBossOrb");
        this.parent = parent;
        this.index = Math.floorMod(index, ORB_COUNT);
        this.xPos = spawn.x() << 16;
        this.yPos = spawn.y() << 16;
        // loc_7ADB2 is the shared setup block: routine 2, the seeded angles and $87 collision.
        this.horizontalAngle = START_ANGLE[this.index];
        this.verticalAngle = START_ANGLE[this.index];
    }

    /** Probe/rewind constructor: the spawn alone, with the parent re-resolved on restore. */
    public SszMtzBossOrbChild(ObjectSpawn spawn) {
        this(spawn, null, 0);
    }

    @Override
    public SszMtzBossOrbChild recreateForRewind(RewindRecreateContext ctx) {
        return new SszMtzBossOrbChild(ctx.spawn());
    }

    private record RewindExtra(ObjectRefId parentId, int index, int routine, int xPos, int yPos,
                               int xVel, int yVel, int horizontalAngle, int verticalAngle,
                               int flattenAngle, int centreX, int centreY, int depth,
                               int collisionDelay, int bounceAngle, int bounceFloor,
                               int collisionFlags, int priorityWord, int mappingFrame,
                               boolean renderFlipped, int animStep, int animTimer,
                               boolean animating, int collisionProperty)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                parentId, index, routine, xPos, yPos, xVel, yVel, horizontalAngle, verticalAngle,
                flattenAngle, centreX, centreY, depth, collisionDelay, bounceAngle, bounceFloor,
                collisionFlags, priorityWord, mappingFrame, renderFlipped, animStep, animTimer,
                animating, collisionProperty));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (!(snapshot.objectSubclassExtra() instanceof RewindExtra extra)) {
            return;
        }
        routine = extra.routine();
        xPos = extra.xPos();
        yPos = extra.yPos();
        xVel = extra.xVel();
        yVel = extra.yVel();
        horizontalAngle = extra.horizontalAngle();
        verticalAngle = extra.verticalAngle();
        flattenAngle = extra.flattenAngle();
        centreX = extra.centreX();
        centreY = extra.centreY();
        depth = extra.depth();
        collisionDelay = extra.collisionDelay();
        bounceAngle = extra.bounceAngle();
        bounceFloor = extra.bounceFloor();
        collisionFlags = extra.collisionFlags();
        priorityWord = extra.priorityWord();
        mappingFrame = extra.mappingFrame();
        renderFlipped = extra.renderFlipped();
        animStep = extra.animStep();
        animTimer = extra.animTimer();
        animating = extra.animating();
        index = extra.index();
        collisionProperty = extra.collisionProperty();
        parent = extra.parentId() == null ? null
                : (SszMtzBossObjectInstance) context.requireIdentityTable()
                .resolveObject(extra.parentId(), true);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent == null || parent.isDestroyed()) {
            // Divergence, recorded in docs/status/s3k-known-bugs.md: an orb still in loc_7AE22 is
            // never popped by _unkFA88, because sub_7B0C2 is only reached from loc_7AFA4 and
            // loc_7B02A. On the cartridge those orbs keep orbiting a slot loc_7ACA4 has freed,
            // reading whatever the next object writes into it. Deleting with the ship is the
            // engine's choice, not the ROM's.
            if (parent != null) {
                parent.releaseOrb(this);
            }
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        switch (routine) {
            case ROUTINE_ORBITING -> updateOrbiting();
            case ROUTINE_LAUNCHED -> updateLaunched(vIntRunCount);
            case ROUTINE_BOUNCING -> updateBouncing(vIntRunCount);
            case ROUTINE_POPPING -> pop();
            default -> { }
        }
    }

    /** {@code loc_7AE22}. */
    private void updateOrbiting() {
        centreY = (parent.getY() + ORBIT_CENTRE_DY) & 0xFFFF;
        centreX = parent.getX() & 0xFFFF;
        if (parent.takeLaunchRequest()) {
            parent.addLiveOrb();
            routine = ROUTINE_LAUNCHED;
            collisionDelay = ARM_DELAY_FRAMES;
            // move.b #2,anim(a0) only selects the script. loc_7AE22 ends at loc_7AE9C without
            // calling Animate_Sprite, so this frame's mapping frame is still sub_7AF5A's, and
            // byte_7B233's first entry is not loaded until the frame after.
            animating = true;
            animStep = -1;
            animTimer = 0;
            yVel = LAUNCH_Y_VEL;
            // move.w #-$80,d1 / sub.w x_pos(a0),d0 / bpl: the player's side picks the sign, and
            // the two clamps then keep the launch inside the arena's laser band.
            int d1 = -LAUNCH_X_VEL;
            if (Integer.compareUnsigned(playerX(), getX()) < 0) {
                d1 = LAUNCH_X_VEL;
            }
            if (Integer.compareUnsigned(getX(), LAUNCH_CLAMP_LEFT) < 0) {
                d1 = LAUNCH_X_VEL;
            }
            if (Integer.compareUnsigned(getX(), LAUNCH_CLAMP_RIGHT) >= 0) {
                d1 = -LAUNCH_X_VEL;
            }
            renderFlipped = d1 >= 0;
            xVel = d1;
        }
        orbit();
        sortByDepth();
    }

    /** {@code sub_7AEB0}. */
    private void orbit() {
        int armX = parent.armXForTest();
        int armY = parent.armYForTest();
        int signal = parent.armSignal();
        // move.b $2F(a0),d0 / GetSineCosine: $2F never changes, so this is sin($40) = $100.
        int unit = TrigLookupTable.sinHex(RADIUS_ANGLE);
        int radius = (short) (unit * armX);
        int verticalRadius = (short) ((signal != 0 ? FLATTENED_RADIUS : armY) * unit);
        int sin = TrigLookupTable.sinHex(horizontalAngle & 0xFF);
        int cos = TrigLookupTable.cosHex(horizontalAngle & 0xFF);
        xPos = (((((radius * sin) >> 16) + centreX) & 0xFFFF) << 16);
        // muls.w d1,d4 / swap d4: the cosine of the same angle, scaled by the same radius.
        depth = (short) ((radius * cos) >> 16);
        int verticalSource = signal != 0 ? flattenAngle : verticalAngle;
        int verticalSin = TrigLookupTable.sinHex(verticalSource & 0xFF);
        yPos = (((((verticalRadius * verticalSin) >> 16) + centreY) & 0xFFFF) << 16);
        horizontalAngle = (horizontalAngle + HORIZONTAL_STEP) & 0xFF;
        if (signal == 0) {
            verticalAngle = (verticalAngle + VERTICAL_STEP) & 0xFF;
            return;
        }
        if (signal == -1) {
            // loc_7AF4C: open the ring out, and leave $3B alone until it is told to close.
            if (flattenAngle < FLATTEN_LIMIT) {
                flattenAngle += FLATTEN_STEP;
            }
            return;
        }
        if (signal == 0x80) {
            flattenAngle -= FLATTEN_STEP;
            if (flattenAngle >= 0) {
                return;
            }
            flattenAngle = 0;
        }
        // loc_7AF44: the first orb whose walk finishes clears the ship's flag for the rest.
        parent.clearArmSignal();
    }

    /** {@code sub_7AF5A}: the depth's sign and magnitude pick the frame and the priority. */
    private void sortByDepth() {
        if (depth >= 0) {
            if (depth < DEPTH_BAND) {
                mappingFrame = 1;
                priorityWord = PRIORITY_FRONT_NEAR;
            } else {
                mappingFrame = 0;
                priorityWord = PRIORITY_FRONT_FAR;
            }
            return;
        }
        if (depth >= -DEPTH_BAND) {
            mappingFrame = 1;
            priorityWord = PRIORITY_BACK_NEAR;
        } else {
            mappingFrame = 2;
            priorityWord = PRIORITY_BACK_FAR;
        }
    }

    /** {@code loc_7AFA4}. */
    private void updateLaunched(int vIntRunCount) {
        if (collisionDelay >= 0 && --collisionDelay < 0) {
            collisionFlags = COLLISION_LAUNCHED;
        }
        // MoveSprite applies the standard $38 gravity; the -$20 below is the orb's own term.
        xPos += xVel << 8;
        yPos += yVel << 8;
        yVel = (short) (yVel + GRAVITY);
        yVel = (short) (yVel - LIFT);
        if (yVel >= TERMINAL_Y_VEL) {
            yVel = TERMINAL_Y_VEL;
        }
        if (yVel >= 0 && Integer.compareUnsigned(getY(), FLOOR_Y) >= 0) {
            yPos = FLOOR_Y << 16;
            bounceFloor = FLOOR_Y;
            bounceAngle = 1;
            routine = ROUTINE_BOUNCING;
            faceThePlayer();
        }
        touchOrPop(vIntRunCount);
        animateLaunch();
    }

    /** {@code loc_7B02A}. */
    private void updateBouncing(int vIntRunCount) {
        if (collisionDelay >= 0 && --collisionDelay < 0) {
            collisionFlags = COLLISION_BOUNCING;
        }
        touchOrPop(vIntRunCount);
        if (mappingFrame != BOUNCE_GATE_FRAME) {
            // bne.s loc_7AFFC: the animation only runs while the orb is still squashing.
            animateLaunch();
            return;
        }
        // -sin($32) >> 2 above the floor, one pixel of drift per odd step.
        // neg.w d0 THEN asr.w #2: negating first is not the same as negating the shift, because
        // an arithmetic shift of a negative value rounds towards minus infinity.
        int hop = bounceFloor + ((-TrigLookupTable.sinHex(bounceAngle & 0xFF)) >> BOUNCE_SHIFT);
        if (Integer.compareUnsigned(hop & 0xFFFF, FLOOR_Y) >= 0) {
            yPos = FLOOR_Y << 16;
            faceThePlayer();
            bounceAngle = 1;
            return;
        }
        yPos = (hop & 0xFFFF) << 16;
        bounceAngle = (bounceAngle + 1) & 0xFF;
        if ((bounceAngle & 1) == 0) {
            return;
        }
        xPos += (renderFlipped ? 1 : -1) << 16;
    }

    /** {@code sub_7B0A8}: the bounce always leans towards the player. */
    private void faceThePlayer() {
        renderFlipped = Integer.compareUnsigned(playerX(), getX()) >= 0;
    }

    /**
     * {@code sub_7B0C2}. {@code collision_property(a0)} is what {@code loc_103FA} left behind —
     * {@code 1} for the main character, {@code 2} for the sidekick, which {@code word_7B10E}
     * indexes with {@code andi.b #3,d0}. An attacking player just pops the orb; anyone else takes
     * {@code HurtCharacter_Directly}, which spills their rings. With no touch at all the orb still
     * pops once the ship has written {@code _unkFA88}, which is its defeat.
     */
    private void touchOrPop(int vIntRunCount) {
        int property = collisionProperty & 0xFF;
        if (property == 0) {
            if (parent.allOrbsShouldPop()) {
                beginPop();
            }
            return;
        }
        collisionProperty = 0;
        PlayableEntity target = (property & 3) < 2
                ? services().playerQuery().mainPlayerOrNull()
                : services().playerQuery().nativeP2OrNull();
        if (!(target instanceof AbstractPlayableSprite hit)) {
            beginPop();
            return;
        }
        // tst.b $34(a1) / bne.w locret_7A718: a player already owned by an object is left alone,
        // orb and all.
        if (hit.isObjectControlled()) {
            return;
        }
        if (!isPlayerAttacking(hit)) {
            hurtDirectly(hit, vIntRunCount);
        }
        beginPop();
    }

    /** {@code Check_PlayerAttack}, as {@code BlastoidBadnikInstance} reads it. */
    private boolean isPlayerAttacking(AbstractPlayableSprite hit) {
        if (hit.isSuperSonic() || hit.getInvincibleFrames() > 0
                || hit.getAnimationId() == 9 || hit.getAnimationId() == 2) {
            return true;
        }
        if (hit instanceof Knuckles) {
            int ability = hit.getDoubleJumpFlag();
            return ability == 1 || ability == 3;
        }
        if (hit instanceof Tails tails) {
            if (hit.getDoubleJumpFlag() == 0 || tails.isInWater()) {
                return false;
            }
            int dx = (short) (hit.getCentreX() - getX());
            int dy = (short) (hit.getCentreY() - getY());
            int angle = (int) Math.round(Math.atan2(dy, dx) * 128.0 / Math.PI) & 0xFF;
            return ((angle - 0x20) & 0xFF) < 0x40;
        }
        return false;
    }

    /** {@code HurtCharacter_Directly}: {@code HurtCharacter} with no invulnerability test. */
    private void hurtDirectly(AbstractPlayableSprite hit, int vIntRunCount) {
        if (hit.isCpuControlled()) {
            hit.applyHurt(getX(), com.openggf.game.DamageCause.NORMAL);
            return;
        }
        boolean hadRings = hit.getRingCount() > 0;
        if (hadRings && !hit.hasShield()) {
            services().spawnLostRings(hit, vIntRunCount);
        }
        hit.applyHurtOrDeath(getX(), com.openggf.game.DamageCause.NORMAL, hadRings);
    }

    /** {@code loc_103FA}: the touch pass is what writes {@code collision_property}. */
    @Override
    public void onTouchResponse(PlayableEntity hit, TouchResponseResult result, int vIntRunCount) {
        if (hit == services().playerQuery().mainPlayerOrNull()) {
            collisionProperty = (collisionProperty + 1) & 0xFF;
        } else if (hit == services().playerQuery().nativeP2OrNull()) {
            collisionProperty = (collisionProperty + 2) & 0xFF;
        }
    }

    @Override public int getCollisionProperty() { return collisionProperty; }

    /**
     * The decode mode is declared, but the category is <b>not</b> pinned: this object's collision
     * byte changes category during its own life — {@code $87} while it orbits and through the
     * launch delay, {@code $C6} once {@code loc_7AFA4} arms it — so the profile has to keep
     * reading the byte. {@code fromProvider} is what the default does; declaring it says that is
     * deliberate rather than unconsidered.
     */
    @Override
    public com.openggf.level.objects.TouchResponseProfile getTouchResponseProfile() {
        return getTouchResponseProfile(getMultiTouchRegions() != null);
    }

    @Override
    public com.openggf.level.objects.TouchResponseProfile getTouchResponseProfile(
            boolean multiRegionSource) {
        return com.openggf.level.objects.TouchResponseProfile.fromProvider(this, multiRegionSource);
    }

    /** {@code $C6} and {@code $DA} are {@code $C0} category bytes: property, not damage. */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return true; }

    /** {@code Draw_And_Touch_Sprite} polls the property on every overlapping frame. */
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }

    private void beginPop() {
        collisionFlags = 0;
        mappingFrame = POP_FRAME;
        animating = false;
        animStep = -1;
        routine = ROUTINE_POPPING;
    }

    /** {@code loc_7B116}. */
    private void pop() {
        services().playSfx(Sonic3kSfx.BALLOON.id);
        if (parent != null) {
            parent.removeLiveOrb();
            parent.releaseOrb(this);
        }
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /**
     * {@code Animate_Sprite} over {@code byte_7B233}, which ends {@code $FE 1} — a one-entry step
     * back, so the script settles on frame 8 and stays there.
     *
     * <p>The first call after {@code anim} changes takes the {@code .newanim} arm: it zeroes
     * {@code anim_frame} and {@code anim_frame_duration} and then falls into the load, so entry 0
     * is installed with a full {@code dc.b 3} duration on that call. Every entry therefore holds
     * four frames — the load plus three decrements — starting one frame after the launch.
     */
    private void animateLaunch() {
        if (!animating) {
            return;
        }
        if (animStep < 0) {
            animStep = 0;
            animTimer = LAUNCH_ANIM_DELAY;
            mappingFrame = LAUNCH_ANIM[0];
            return;
        }
        if (--animTimer >= 0) {
            return;
        }
        animTimer = LAUNCH_ANIM_DELAY;
        if (animStep < LAUNCH_ANIM.length - 1) {
            animStep++;
        }
        mappingFrame = LAUNCH_ANIM[animStep];
    }

    private int playerX() {
        var sprite = services().spriteManager().getMainPlayable();
        return sprite == null ? 0 : sprite.getCentreX() & 0xFFFF;
    }

    @Override public int getCollisionFlags() { return collisionFlags; }

    /** No {@code Obj_WaitOffscreen}: the fight's own defeat is what clears the orbs. */
    @Override public boolean isPersistent() { return true; }

    @Override public int getX() { return (xPos >> 16) & 0xFFFF; }

    @Override public int getY() { return (yPos >> 16) & 0xFFFF; }

    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(priorityWord); }

    @Override public int getOnScreenHalfWidth() { return HALF_SIZE; }

    @Override public int getOnScreenHalfHeight() { return HALF_SIZE; }

    public int routineForTest() { return routine; }

    public int depthForTest() { return depth; }

    public int priorityWordForTest() { return priorityWord; }

    public int mappingFrameForTest() { return mappingFrame; }

    public int horizontalAngleForTest() { return horizontalAngle; }

    public int flattenAngleForTest() { return flattenAngle; }

    /** {@code byte_7AE14}'s row, which is this orb's position in the allocation order. */
    public int indexForTest() { return index; }

    public int collisionFlagsForTest() { return collisionFlags; }

    public boolean isRenderFlippedForTest() { return renderFlipped; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_MTZ_ORBS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), renderFlipped, false, 0);
        }
    }
}
