package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR31aAssets;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Pure R11A response for one selected MONITOR, ENEMY, HAZARD or owned SPECIAL flag handler.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Palmtree Panic/Player Object Collision.asm:102-395 (Player_TouchMonitor, Player_TouchEnemy, .DamageEnemy,
 * .KillEnemy, Player_TouchHazard, HurtPlayer, KillPlayer and the seven Player_TouchSpecial flag branches),
 * Level/_Objects/HUD and Points/Main.asm:157-180
 * (AddPoints), Level/Object Spawner.asm:329-340 (FindObjSlot) and Player_ResetOnFloor through
 * {@link CdPlayerMovement#resetOnFloor(CdPlayerMotionState, int)}. Shipped FixBugs=0 behavior.
 *
 * <p>The caller owns the Player_ObjCollide gates and scan (Sonic/Main.asm:340-347) and passes the
 * scanner's selection with the SAME call-cut snapshot. The kind only names the native entry
 * (.CheckColType:81-88); every outcome follows the source tests below, never the kind. FindObjSlot
 * runs at its call over a working map seeded from that snapshot, so an ID-zero selected slot can be
 * its answer, and .NoWater then reads that slot's overwritten oX.
 *
 * <p>All writes stage in a per-call cursor. A Result keeps final actor/global values, distinct final
 * slot images in first-write order and ordered call observations; never the snapshot, scanner or a
 * pool. The caller commits images against the same snapshot only after its whole object call
 * succeeds. This owner writes no pool, plays no sound and sends no sub-CPU command. Exceptions
 * publish nothing.
 */
public final class CdPlayerTouchResponse {

    // BEGIN direct native HurtPlayer value entry.
    /**
     * Pure direct HurtPlayer entry (PP Player Object Collision.asm:229-298, source pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff), below every scanner/Hazard/Body gate.
     * The caller owns reached guards and live freshness of the complete pool. The complete a2
     * image must agree with this cut, including its actual pool owner and physical identity.
     * No asset table, selected handler, ID/group rule, pool publication or sound execution is involved.
     * ID-zero a2 remains the same address if FindObjSlot allocates it before the unsigned X compare.
     */
    public static Result hurtPlayer(Actor actor, PlayerGlobals player, WorldGlobals world,
                                    CdObjectState sourceA2, CdObjectPool.Snapshot callCut) {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(player, "player globals");
        Objects.requireNonNull(world, "world globals"); Objects.requireNonNull(sourceA2, "HurtPlayer source a2");
        Objects.requireNonNull(callCut, "call-cut snapshot");
        if (!sourceA2.equals(callCut.slots().get(sourceA2.identity().dynamicIndex()))) {
            throw new IllegalArgumentException("HurtPlayer source is not the complete same-owner call-cut image");
        }
        var c = new Cursor(actor, player, world, callCut);
        hurtPlayer(c, sourceA2.identity().wholeIndex());
        return c.finish();
    }
    // END direct native HurtPlayer value entry.
    /** Complete kernel pair; values alone authenticate no Frame, Environment or invocation. */
    public record HurtPair(Actor actor, PlayerGlobals player, WorldGlobals world) {
        public HurtPair { Objects.requireNonNull(actor); Objects.requireNonNull(player); Objects.requireNonNull(world); }
    }
    public enum HurtCut { SOUND, RETURN }
    public record HurtPrefix(HurtPair pair, List<CdObjectState> changedSlots, List<Call> calls,
                             Optional<Exit> exit) {
        public HurtPrefix { Objects.requireNonNull(pair); changedSlots=List.copyOf(changedSlots);
            calls=List.copyOf(calls); Objects.requireNonNull(exit); }
    }
    /** Current original kernel cut. Its private constructor/current visit are not World authority.
     * Retained data or an equal caller key cannot resume or publish any operation. */
    public static final class HurtStep {
        private final HurtVisit visit;
        private final HurtPrefix prefix;
        private final HurtCut cut;
        private final FmSound sound;
        private final int target;
        private boolean claimed;
        private HurtStep(HurtVisit visit,HurtCut cut,FmSound sound) {
            this.visit=visit;this.cut=cut;this.sound=sound;
            prefix=visit.cursor.hurtPrefix();target=visit.cursor.bossContinuationWhole;
        }
        public HurtPrefix prefix() { return prefix; }
        public HurtCut cut() { return cut; }
        public Optional<FmSound> sound() { return Optional.ofNullable(sound); }
        public int returnedTargetWholeIndex() { return target; }
        public boolean isCurrent() { return visit.current==this; }
        public void claim(Object originalInvocation) {
            if(!isCurrent()||claimed||visit.originalInvocation!=originalInvocation)
                throw new IllegalStateException("Stale, foreign or repeated original Hurt cut");
            claimed=true;
        }
    }
    @FunctionalInterface public interface HurtObserver { void reached(HurtStep step); }
    /** Terminal calculated prefix only. Source/owner publication happened solely at original live cuts. */
    public static final class HurtOutcome {
        private final HurtPrefix prefix;
        private final Throwable failure;
        private HurtOutcome(HurtPrefix prefix,Throwable failure) { this.prefix=prefix;this.failure=failure; }
        public HurtPrefix prefix() { return prefix; }
        public Optional<Throwable> failure() { return Optional.ofNullable(failure); }
    }
    /**
     * Source-equivalent guarded Hurt entry: R4 sub_20730C (player_object_collide.asm:208-265),
     * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Uses exactly the existing touchHazard/Hurt/kill/reset
     * algorithms, never PPZ's SPECIAL/Enemy tail or its handler admission. Source-family guards on
     * existing entries are unchanged. The caller supplies the original synchronous invocation key,
     * actual complete same-owner pool/source and actual kernel pair. Root authenticates its private
     * World/Frame/source/full State+Environment separately. Observer cuts occur at original FM calls
     * and the actual return, not by replaying completed calls/results. Failed callbacks retain the
     * calculated reached prefix and identical fault, and expire every cut; caller retains its actual
     * already published prefix and rethrows that same fault. No pool or external state is owned here.
     */
    public static HurtOutcome visitGuardedHurt(HurtPair pair,CdObjectState source,
            CdObjectPool.Snapshot cut,Object originalInvocation,HurtObserver observer) {
        Objects.requireNonNull(pair);Objects.requireNonNull(source);Objects.requireNonNull(cut);
        Objects.requireNonNull(originalInvocation);Objects.requireNonNull(observer);
        if(!source.equals(cut.slots().get(source.identity().dynamicIndex())))
            throw new IllegalArgumentException("Original Hurt needs complete same-owner source/cut");
        var c=new Cursor(pair.actor(),pair.player(),pair.world(),cut);
        c.bossContinuationWhole=source.identity().wholeIndex();
        c.hurtVisit=new HurtVisit(originalInvocation,c,observer);
        try {
            touchHazard(c,source.identity().wholeIndex());
            c.hurtVisit.emit(HurtCut.RETURN,null);
            return new HurtOutcome(c.hurtPrefix(),null);
        } catch(RuntimeException|Error fault) {
            return new HurtOutcome(c.hurtPrefix(),fault);
        } finally { c.hurtVisit.current=null; }
    }
    private static final class HurtVisit {
        final Object originalInvocation;final Cursor cursor;final HurtObserver observer;
        HurtStep current;
        HurtVisit(Object key,Cursor c,HurtObserver observer) {originalInvocation=key;cursor=c;this.observer=observer;}
        void emit(HurtCut cut,FmSound sound) {
            var step=new HurtStep(this,cut,sound);current=step;
            try { observer.reached(step);if(!step.claimed)throw new IllegalStateException("Unclaimed original Hurt cut"); }
            finally {current=null;}
        }
    }
    /** Projection only: native reset's last jump store is after its original sound call. */
    private static CdPlayerMotionState resetBeforeSound(CdPlayerMotionState m,int originalJump) {
        return new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
            m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
            originalJump,m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
    }

    /** EnemyPoints: four native words (Player Object Collision.asm:200-204). */
    public static final int ENEMY_POINTS_BYTES = 8;

    /**
     * Player-slot projection at the touch call cut, after Display decremented the hurt word.
     * tile is oTile ($02 word), priority oPriority ($18), hurt oPlayerHurt ($30 word) and
     * rotationDistance oPlayerRotDist ($39), the low byte of KillPlayer's oPlayerStick word write;
     * motion.stick() is the high byte at $38.
     */
    public record Actor(CdPlayerMotionState motion, int tile, int priority, int hurt, int rotationDistance) {
        public Actor {
            Objects.requireNonNull(motion);
            wordValue(tile); byteValue(priority); wordValue(hurt); byteValue(rotationDistance);
        }
    }

    /** Native player-scope globals: bytes, except the debugCheat/debugMode/scoreChain words. */
    public record PlayerGlobals(int timeWarp, int invincible, int shield, int combineRing,
                                int debugCheat, int debugMode, int scoreChain, int miniSonic) {
        public PlayerGlobals {
            byteValue(timeWarp); byteValue(invincible); byteValue(shield); byteValue(combineRing);
            wordValue(debugCheat); wordValue(debugMode); wordValue(scoreChain); byteValue(miniSonic);
        }
    }

    /**
     * World globals the cascade reads or writes. rings is a word; lives and the HUD flags are bytes;
     * score and nextLifeScore are raw 32-bit longs whose comparisons are unsigned.
     */
    public record WorldGlobals(int rings, int lives, int updateHudLives, int score, int nextLifeScore,
                               int updateHudScore) {
        public WorldGlobals {
            wordValue(rings); byteValue(lives); byteValue(updateHudLives); byteValue(updateHudScore);
        }
    }

    /** Complete pure AddPoints output; the optional native command is an observation, never execution. */
    public record PointsResult(WorldGlobals world, Optional<SubCpuCommand> command) {
        public PointsResult {
            Objects.requireNonNull(world); Objects.requireNonNull(command);
        }
    }

    /**
     * Selected AddPoints entry (HUD and Points/Main.asm:157-180, scddisasm
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff). rawPoints is the complete d0.l:
     * ADD.L wraps before the unsigned cap and the one unsigned extra-life test. The caller owns
     * reaching this entry, publication and its optional SubCPUCmd; no player or pool is involved.
     */
    public static PointsResult addPoints(WorldGlobals before, int rawPoints) {
        Objects.requireNonNull(before, "world globals");
        int score = before.score() + rawPoints;
        if (Integer.compareUnsigned(999999, score) <= 0) score = 999999;
        boolean extraLife = Integer.compareUnsigned(score, before.nextLifeScore()) >= 0;
        int nextLifeScore = before.nextLifeScore();
        int lives = before.lives();
        int updateHudLives = before.updateHudLives();
        if (extraLife) {
            nextLifeScore += 5000;
            lives = (lives + 1) & 0xFF;
            updateHudLives = (updateHudLives + 1) & 0xFF;
        }
        return new PointsResult(new WorldGlobals(before.rings(), lives, updateHudLives, score, nextLifeScore, 1),
                extraLife ? Optional.of(SubCpuCommand.SCMD_YESSFX) : Optional.empty());
    }

    /** USA IDs: FM_HURT $93, FM_DESTROY $96, FM_CHARGESTOP $AB. */
    public enum FmSound { FM_HURT, FM_DESTROY, FM_CHARGESTOP }
    /** USA ID: SCMD_YESSFX $7A. */
    public enum SubCpuCommand { SCMD_YESSFX }

    /** Ordered native call observations, listed at entry; a nested call follows the call that makes it. */
    public sealed interface Call permits FindObjSlot, ResetOnFloor, AddPoints, SubCpu, PlayFm { }
    /** FindObjSlot's whole-slot answer, or empty for NE (a1 = $FFF000, never written here). */
    public record FindObjSlot(OptionalInt wholeIndex) implements Call {
        public FindObjSlot {
            Objects.requireNonNull(wholeIndex);
            if (wholeIndex.isPresent() && (wholeIndex.getAsInt() < CdObjectPool.FIRST_WHOLE_INDEX
                    || wholeIndex.getAsInt() >= CdObjectPool.END_WHOLE_INDEX)) {
                throw new IllegalArgumentException("FindObjSlot answers only dynamic whole slots");
            }
        }
    }
    public record ResetOnFloor() implements Call { }
    /** AddPoints with d0.l = points (upper word zero on every KillEnemy path); extraLife = its one 1-up branch ran. */
    public record AddPoints(int points, boolean extraLife) implements Call {
        public AddPoints { wordValue(points); }
    }
    public record SubCpu(SubCpuCommand command) implements Call {
        public SubCpu { Objects.requireNonNull(command); }
    }
    public record PlayFm(FmSound sound) implements Call {
        public PlayFm { Objects.requireNonNull(sound); }
    }

    /** The native return site reached. explicitMinusOne exits end with the handler's own MOVEQ #-1,d0. */
    public enum Exit {
        /** .DamageEnemy .End (:150-151). */
        DAMAGE_ENEMY(false),
        /** .KillEnemy's bounce returns (:179-197). */
        KILL_ENEMY(false),
        /** Player_TouchHazard .NoHurt (:217-219): no writes. */
        NO_HURT(true),
        /** HurtPlayer .GotXVel (:263-268). */
        HURT(true),
        /** KillPlayer after its writes (:282-298). */
        KILL_PLAYER(true),
        /** KillPlayer with a nonzero debugMode word (:280-281, 296-298): no writes. */
        KILL_PLAYER_DEBUG_NO_WRITE(true),
        /** Player_TouchMonitor .End2 (:103-108): upward, but unsigned (oY-$10).w is below object Y. */
        MONITOR_UPWARD_NO_WRITE(false),
        /** Player_TouchMonitor (:109-114): upward hit; NEG.W, object YVel and optional fall-byte write. */
        MONITOR_UPWARD_BOUNCE(false),
        /** .GoingDown/.End2 (:118-125): nonnegative YVel, animation is not 2. */
        MONITOR_DOWNWARD_NO_WRITE(false),
        /** .GoingDown (:121-122): NEG.W then ADDQ.B #2 to object routine. */
        MONITOR_DOWNWARD_BOUNCE(false),
        /** Player_TouchSpecial .FlagCollision or rolling .CheckIfRoll (:383-395): one byte increment. */
        SPECIAL_FLAG_INCREMENT(false),
        /** .CheckIfRoll/.End4 (:389-395): animation is not 2; no writes. */
        SPECIAL_ROLL_NO_WRITE(false),
        // BEGIN R31 source SPECIAL no-action RTS; append preserves every old ordinal.
        SPECIAL_NO_WRITE(false);
        // END R31 source SPECIAL no-action RTS.

        private final boolean explicitMinusOne;
        Exit(boolean explicitMinusOne) { this.explicitMinusOne = explicitMinusOne; }
        public boolean explicitMinusOne() { return explicitMinusOne; }
    }

    /**
     * Complete staged response. d0 is present (-1) only where the handler writes it; elsewhere it
     * holds whatever the scanner or callees left, which ObjSonic_Main never reads (:340-351).
     */
    public record Result(Actor actor, PlayerGlobals player, WorldGlobals world, List<CdObjectState> changedSlots,
                         List<Call> calls, OptionalInt d0, Exit exit) {
        public Result {
            Objects.requireNonNull(actor); Objects.requireNonNull(player); Objects.requireNonNull(world);
            changedSlots = List.copyOf(changedSlots); calls = List.copyOf(calls);
            Objects.requireNonNull(d0); Objects.requireNonNull(exit);
            var identities = new HashSet<Integer>();
            for (var slot : changedSlots) {
                if (!identities.add(slot.identity().wholeIndex())) {
                    throw new IllegalArgumentException("A changed slot is published once, as its final image");
                }
            }
            if (d0.isPresent() != exit.explicitMinusOne() || (d0.isPresent() && d0.getAsInt() != -1)) {
                throw new IllegalArgumentException("d0 is published only for MOVEQ #-1 exits");
            }
        }
    }

    public enum UnsupportedBoundary { ODD_ENEMY_POINTS_ADDRESS,
        // BEGIN R31 precise SPECIAL ownership cuts; old ordinal0 remains unchanged.
        R3_BOSS_STARTED_OWNER, R3_BOSS_SPECIAL_RESPONSE_OWNER
        // END R31 precise SPECIAL ownership cuts.
    }
    /** A native stop with no partial result: nothing staged before it is published. */
    public static final class UnsupportedResponse extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private UnsupportedResponse(UnsupportedBoundary boundary) { super(boundary.name()); this.boundary = boundary; }
        public UnsupportedBoundary boundary() { return boundary; }
    }

    private final CdAssetBytes enemyPoints;
    // BEGIN R31 exact immutable semantic source; public values grant no execution authority.
    private final CdR31aAssets r31aAssets;
    // END R31 exact immutable semantic source.

    /** Pure format consumer for the complete EnemyPoints table, not an authenticated asset constructor. */
    public CdPlayerTouchResponse(CdAssetBytes enemyPoints) {
        Objects.requireNonNull(enemyPoints, "EnemyPoints");
        if (enemyPoints.length() != ENEMY_POINTS_BYTES) {
            throw new IllegalArgumentException("EnemyPoints requires exactly 8 bytes");
        }
        this.enemyPoints = enemyPoints;
        this.r31aAssets = null;
    }

    /** Runtime bytes come only from the authenticated R11A object-asset view; no literal fallback. */
    public static CdPlayerTouchResponse fromAssets(CdR11aObjectAssets assets) {
        return new CdPlayerTouchResponse(Objects.requireNonNull(assets, "assets")
                .view(CdR11aObjectAssets.Table.ENEMY_POINTS).bytes());
    }

    // BEGIN R31 authenticated ordinary touch response metadata/profile.
    public static CdPlayerTouchResponse fromR31aAssets(CdR31aAssets assets) {
        var source = Objects.requireNonNull(assets, "actual original R31A assets");
        return new CdPlayerTouchResponse(source, source.view(CdR31aAssets.Asset.ENEMY_POINTS));
    }
    private CdPlayerTouchResponse(CdR31aAssets assets, CdAssetBytes points) {
        r31aAssets = assets; enemyPoints = points;
        if (enemyPoints.length() != ENEMY_POINTS_BYTES) throw new IllegalArgumentException("EnemyPoints requires exactly 8 bytes");
    }
    public boolean ownsR31aAssets(CdR31aAssets assets) { return r31aAssets != null && r31aAssets == assets; }
    /** r3/player_object_collide.asm:300-314,358-360,390-396: flag27, not PPZ21/2F. */
    private static boolean ownsR3SpecialFlagCollision(int rawCollisionTypeByte) {
        byteValue(rawCollisionTypeByte);
        if ((rawCollisionTypeByte & 0xC0) != 0xC0) return false;
        return switch (rawCollisionTypeByte & 0x3F) {
            case 0x17, 0x1F, 0x23, 0x27, 0x3A, 0x3B -> true;
            default -> false;
        };
    }
    /**
     * Explicit R3 SPECIAL call with the actual raw boss_started byte. Value input is not a global owner.
     * Owns the source no-action branches only; reached R3 boss responses remain a precise boundary.
     * No boss_started read is inferred from Bounds, pool IDs or a PPZ bossFight projection.
     */
    public Result respondR3Special(CdPlayerObjectTouch.HandlerBoundary selected, Actor actor, PlayerGlobals player,
                                  WorldGlobals world, int bossStartedByte, CdObjectPool.Snapshot callCut) {
        Objects.requireNonNull(selected, "selected R3 SPECIAL"); Objects.requireNonNull(actor); Objects.requireNonNull(player);
        Objects.requireNonNull(world); Objects.requireNonNull(callCut); byteValue(bossStartedByte);
        if (r31aAssets == null || selected.r31aAssets() != r31aAssets)
            throw new IllegalArgumentException("R3 SPECIAL requires the exact original selector/source owner");
        var source = selected.selectedSlot().orElseThrow();
        if (selected.kind() != CdPlayerObjectTouch.Kind.SPECIAL || (source.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE) & 0xC0) != 0xC0)
            throw new IllegalArgumentException("Only the selected R3 SPECIAL entry belongs here");
        if (!source.equals(callCut.slots().get(source.identity().dynamicIndex())))
            throw new IllegalArgumentException("R3 SPECIAL source is not the complete same-owner call-cut image");
        int raw = source.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE);
        if (ownsR3SpecialFlagCollision(raw)) return respond(selected, actor, player, world, callCut);
        int selector = raw & 0x3F;
        // Source315-354: other nonzero boss_started bytes FALL THROUGH to case1; never normalize to boolean1/0.
        boolean action = bossStartedByte == 0 ? false : bossStartedByte == 4 ? selector == 0x3F || selector == 0x3E
                : bossStartedByte == 5 ? selector == 0x3D : selector >= 0x3C && selector <= 0x3F;
        if (action) throw new UnsupportedResponse(UnsupportedBoundary.R3_BOSS_SPECIAL_RESPONSE_OWNER);
        return new Result(actor, player, world, List.of(), List.of(), OptionalInt.empty(), Exit.SPECIAL_NO_WRITE);
    }
    // END R31 authenticated ordinary touch response metadata/profile.

    // BEGIN original R13 boss SPECIAL continuation; no legacy handler admission change.
    /** Authenticated original C/D EnemyPoints only; runtime invocation/source authority stays with the caller. */
    public static CdPlayerTouchResponse fromAct3FutureAssets(
            com.openggf.game.soniccd.level.CdR13PlayerCollisionAssets assets) {
        return new CdPlayerTouchResponse(Objects.requireNonNull(assets, "actual R13 collision metadata").enemyPoints());
    }
    public static boolean ownsBossSpecialCollision(int rawCollisionTypeByte) {
        byteValue(rawCollisionTypeByte);
        return (rawCollisionTypeByte & 0xC0) == 0xC0 && (rawCollisionTypeByte & 0x3F) >= 0x3C;
    }
    /** Immutable completed/prefixed values; neither an invocation receipt nor a publication/restore command. */
    public record BossSpecialValues(Actor actor, PlayerGlobals player, WorldGlobals world,
                                    List<CdObjectState> changedSlots, List<Call> calls) {
        public BossSpecialValues {
            Objects.requireNonNull(actor); Objects.requireNonNull(player); Objects.requireNonNull(world);
            changedSlots = List.copyOf(changedSlots); calls = List.copyOf(calls);
        }
    }
    public sealed interface BossSpecialOutcome permits BossSpecialCompleted, BossSpecialStopped {
        BossSpecialValues values();
        /** Original common Enemy result BEFORE the SPECIAL tail, absent when inactive or stopped within it. */
        Optional<Result> commonResult();
    }
    public record BossSpecialCompleted(BossSpecialValues values, Optional<Result> commonResult)
            implements BossSpecialOutcome {
        public BossSpecialCompleted { Objects.requireNonNull(values); Objects.requireNonNull(commonResult); }
    }
    /** Caller prebuilds/adopts exactly this prefix against its actual full pairs/cut, then rethrows this same fault. */
    public record BossSpecialStopped(BossSpecialValues values, Optional<Result> commonResult,
                                    RuntimeException originalException) implements BossSpecialOutcome {
        public BossSpecialStopped {
            Objects.requireNonNull(values); Objects.requireNonNull(commonResult); Objects.requireNonNull(originalException);
            if (!(originalException instanceof UnsupportedResponse)
                    && !(originalException instanceof UnownedBossSpecialTarget))
                throw new IllegalArgumentException("Not a reached native boss SPECIAL stop");
        }
    }
    public static final class UnownedBossSpecialTarget extends UnsupportedOperationException {
        private final int wholeIndex;
        private UnownedBossSpecialTarget(int whole) {
            super("Boss SPECIAL continuation needs the returned object outside the dynamic pool: " + whole);
            wholeIndex = whole;
        }
        public int wholeIndex() { return wholeIndex; }
    }
    /**
     * PP Player Object Collision.asm:323-336,383-385 (N1ecc5f3a), FixBugs=0.
     * Caller owns actual bossFight read, scanner/Body gates and complete source/full-pair freshness.
     * Only SPECIAL FC..FF is admitted; legacy respond/flag ownership is unchanged. bossFight !=1 is
     * a complete no-write return, represented without inventing an Enemy exit or default state.
     *
     * The original touchEnemy/Hurt/ResetOnFloor/AddPoints algorithms run once. Hurt's actual FindObjSlot
     * leaves the continuation target at its real result while recoil still reads original selected a2
     * through the working map. That target is observable in the three SPECIAL tail stores; it is not a
     * generic register/bus model and is not reconstructed from returned/public call observations.
     * Exhaustion stops BEFORE its first consumed colType read, retaining the completed common prefix.
     * Odd EnemyPoints preserves its real earlier flags/chain/frame stores without fabricating an exit.
     * Results contain values only. Actual Frame/World/score/pool/sound adoption is caller-owned.
     */
    public BossSpecialOutcome respondBossSpecial(CdPlayerObjectTouch.HandlerBoundary selected,
            Actor actor, PlayerGlobals player, WorldGlobals world, int bossFight,
            CdObjectPool.Snapshot callCut) {
        Objects.requireNonNull(selected, "selected boss handler"); Objects.requireNonNull(actor);
        Objects.requireNonNull(player); Objects.requireNonNull(world); Objects.requireNonNull(callCut);
        byteValue(bossFight);
        // BEGIN R31 cannot borrow the PPZ/R13 bossFight continuation contract.
        if (r31aAssets != null || selected.r31aAssets() != null || selected.programmeSource() != null)
            throw new IllegalArgumentException("Legacy boss SPECIAL requires its PPZ source rules");
        // END R31 cannot borrow the PPZ/R13 bossFight continuation contract.
        var source = selected.selectedSlot().orElseThrow();
        int originalWhole = source.identity().wholeIndex();
        if (selected.kind() != CdPlayerObjectTouch.Kind.SPECIAL
                || !ownsBossSpecialCollision(source.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE)))
            throw new IllegalArgumentException("Only selected SPECIAL FC..FF belongs to this boss continuation");
        if (!source.equals(callCut.slots().get(source.identity().dynamicIndex())))
            throw new IllegalArgumentException("Boss SPECIAL source is not the complete same-owner call-cut image");
        var c = new Cursor(actor, player, world, callCut);
        if (bossFight != 1) return new BossSpecialCompleted(c.bossValues(), Optional.empty());
        c.bossContinuationWhole = originalWhole;
        try { touchEnemy(c, originalWhole); }
        catch (UnsupportedResponse original) {
            return new BossSpecialStopped(c.bossValues(), Optional.empty(), original);
        }
        var common = c.finish();
        int target = c.bossContinuationWhole;
        if (target == CdObjectPool.END_WHOLE_INDEX)
            return new BossSpecialStopped(c.bossValues(), Optional.of(common), new UnownedBossSpecialTarget(target));
        if (c.slot(target).unsignedByte(CdObjectState.ByteField.COLLISION_TYPE) == 0)
            c.write(c.slot(target).addByte(CdObjectState.ByteField.COLLISION_STATUS, 3));
        c.write(c.slot(target).moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0));
        c.write(c.slot(target).addByte(CdObjectState.ByteField.COLLISION_STATUS, 1));
        return new BossSpecialCompleted(c.bossValues(), Optional.of(common));
    }
    // END original R13 boss SPECIAL continuation.

    /**
     * Exact selected Player_TouchSpecial flag ownership (:302-322), including the $C0 group.
     * Raw native byte only; $0B hazard, $0C MechaBlu, boss and .End3 paths remain unowned.
     * This is handler admission, not an object-ID rule or an outer scan/movement gate.
     */
    public static boolean ownsSpecialFlagCollision(int rawCollisionTypeByte) {
        byteValue(rawCollisionTypeByte);
        if ((rawCollisionTypeByte & 0xC0) != 0xC0) return false;
        return switch (rawCollisionTypeByte & 0x3F) {
            case 0x1F, 0x17, 0x21, 0x23, 0x2F, 0x3A, 0x3B -> true;
            default -> false;
        };
    }

    /** One selected handler call. The snapshot must be the scanner's own call-cut input. */
    public Result respond(CdPlayerObjectTouch.HandlerBoundary selected, Actor actor, PlayerGlobals player,
                          WorldGlobals world, CdObjectPool.Snapshot callCut) {
        Objects.requireNonNull(selected, "selected handler"); Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(player, "player globals"); Objects.requireNonNull(world, "world globals");
        Objects.requireNonNull(callCut, "call-cut snapshot");
        // BEGIN R31 exact original selector identity; equal values grant no source-profile admission.
        if (r31aAssets != selected.r31aAssets() || selected.programmeSource() != null)
            throw new IllegalArgumentException("Touch response requires the exact selector/source owner");
        // END R31 exact original selector identity.
        var slot = selected.selectedSlot().orElseThrow();
        int a1 = slot.identity().wholeIndex();
        if (!callCut.slots().get(a1 - CdObjectPool.FIRST_WHOLE_INDEX).equals(slot)) {
            throw new IllegalArgumentException("Selected slot is not the call-cut image at whole slot " + a1);
        }
        int group = slot.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE) & 0xC0;
        var c = new Cursor(actor, player, world, callCut);
        switch (selected.kind()) {
            case ENEMY -> { requireGroup(group, 0); touchEnemy(c, a1); }
            case HAZARD -> { requireGroup(group, 0x80); touchHazard(c, a1); }
            case MONITOR -> { requireGroup(group, 0x40); touchMonitor(c, a1); }
            case SPECIAL -> {
                requireGroup(group, 0xC0);
                // BEGIN R31 differing native flag selectors and first missing global read.
                if (r31aAssets != null) {
                    if (!ownsR3SpecialFlagCollision(slot.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE)))
                        throw new UnsupportedResponse(UnsupportedBoundary.R3_BOSS_STARTED_OWNER);
                } else {
                if (!ownsSpecialFlagCollision(slot.unsignedByte(CdObjectState.ByteField.COLLISION_TYPE))) {
                    throw new IllegalArgumentException("Unowned Player_TouchSpecial selector");
                }
                }
                // END R31 differing native flag selectors and first missing global read.
                touchSpecialFlag(c, a1);
            }
            default -> throw new IllegalArgumentException("Only selected ENEMY/HAZARD/MONITOR and SPECIAL flag handlers are owned here: " + selected.kind());
        }
        return c.finish();
    }

    /**
     * Player_TouchSpecial .FlagCollision/.CheckIfRoll (:383-395). Shipped FixBugs=0:
     * only animation 2 gates the three roll selectors; hurt, invincibility, shield, time-warp
     * and debug globals are not tested here. Both RTS paths leave d0 and all player/world values alone.
     * respond has already rejected every unowned SPECIAL selector before this call.
     */
    private static void touchSpecialFlag(Cursor c, int a1) {
        int selector = c.slot(a1).unsignedByte(CdObjectState.ByteField.COLLISION_TYPE) & 0x3F;
        if ((selector == 0x2F || selector == 0x3A || selector == 0x3B) && c.anim != 2) {
            c.exit = Exit.SPECIAL_ROLL_NO_WRITE;
            return;
        }
        c.write(c.slot(a1).addByte(CdObjectState.ByteField.COLLISION_STATUS, 1));
        c.exit = Exit.SPECIAL_FLAG_INCREMENT;
    }

    /**
     * Player_TouchMonitor/.GoingDown/.End2 (:102-125). Shipped FixBugs=0: no hurt, shield,
     * invincibility or time-warp gate; the selected handler ends the scan even without writes.
     * oMonitorFall aliases oRoutine2 ($25, Monitor and Time Post/Main.asm:265).
     */
    private static void touchMonitor(Cursor c, int a1) {
        if (c.yv < 0) { // TST.W/BPL: $8000 is upward; NEG.W below retains its overflow bits.
            if (uw(c.y() - 0x10) < c.slot(a1).centreY()) { // SUBI.W then CMP.W/BCS: unsigned words.
                c.exit = Exit.MONITOR_UPWARD_NO_WRITE;
                return;
            }
            c.yv = sw(-c.yv);
            c.write(c.slot(a1).moveWord(CdObjectState.WordField.Y_VELOCITY, 0xFE80));
            if (c.slot(a1).unsignedByte(CdObjectState.ByteField.ROUTINE_2) == 0) {
                c.write(c.slot(a1).addByte(CdObjectState.ByteField.ROUTINE_2, 4));
            }
            c.exit = Exit.MONITOR_UPWARD_BOUNCE;
            return;
        }
        if (c.anim != 2) {
            c.exit = Exit.MONITOR_DOWNWARD_NO_WRITE;
            return;
        }
        c.yv = sw(-c.yv);
        c.write(c.slot(a1).addByte(CdObjectState.ByteField.ROUTINE, 2));
        c.exit = Exit.MONITOR_DOWNWARD_BOUNCE;
    }

    /** Player_TouchEnemy (:130-136). */
    private void touchEnemy(Cursor c, int a1) {
        if (c.timeWarp != 0 || c.invincible != 0 || c.anim == 2) damageEnemy(c, a1);
        else touchHazard(c, a1);
    }

    /** .DamageEnemy (:138-151): NEG.W then memory ASR #1 on each velocity; no points, sound or bounce. */
    private void damageEnemy(Cursor c, int a1) {
        if (c.slot(a1).unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) == 0) { killEnemy(c, a1); return; }
        c.xv = sw(-c.xv); c.yv = sw(-c.yv);
        c.xv >>= 1; c.yv >>= 1;
        c.write(c.slot(a1).moveByte(CdObjectState.ByteField.COLLISION_TYPE, 0));
        c.write(c.slot(a1).addByte(CdObjectState.ByteField.COLLISION_STATUS, -1));
        if (c.slot(a1).unsignedByte(CdObjectState.ByteField.COLLISION_STATUS) == 0) {
            c.write(c.slot(a1).moveByte(CdObjectState.ByteField.FLAGS, c.slot(a1).flags() | 0x80));
        }
        c.exit = Exit.DAMAGE_ENEMY;
    }

    /** .KillEnemy/.CappedChain/.GivePoints and bounces (:155-197). */
    private void killEnemy(Cursor c, int a1) {
        c.write(c.slot(a1).moveByte(CdObjectState.ByteField.FLAGS, c.slot(a1).flags() | 0x80));
        int old = c.scoreChain; // MOVEQ #0 then MOVE.W: the upper word stays zero.
        c.scoreChain = uw(old + 2);
        int index = old < 6 ? old : 6; // CMPI.W/BCS: unsigned old word.
        c.write(c.slot(a1).moveWord(CdObjectState.WordField.EXPLOSION_POINTS_FRAME, index));
        // MOVE.W EnemyPoints(pc,d0.w): odd 1/3/5 is a 68000 odd-address word read; never index bytes there.
        if ((index & 1) != 0) throw new UnsupportedResponse(UnsupportedBoundary.ODD_ENEMY_POINTS_ADDRESS);
        int points = enemyPoints.unsignedWord(index);
        if (c.scoreChain >= 0x20) { // The NEW chain word, CMPI.W/BCS unsigned.
            points = 0x3E8;
            c.write(c.slot(a1).moveWord(CdObjectState.WordField.EXPLOSION_POINTS_FRAME, 0xA));
        }
        addPoints(c, points);
        c.calls.add(new PlayFm(FmSound.FM_DESTROY));
        c.write(c.slot(a1).moveByte(CdObjectState.ByteField.ID, 0x18));
        c.write(c.slot(a1).moveByte(CdObjectState.ByteField.ROUTINE, 0));
        c.write(c.slot(a1).moveByte(CdObjectState.ByteField.SUBTYPE, 1));
        if (c.yv < 0) c.yv = sw(c.yv + 0x100); // .BounceDown
        else if (c.y() >= c.slot(a1).centreY()) c.yv = sw(c.yv - 0x100); // BCC: unsigned words, .BounceUp
        else c.yv = sw(-c.yv);
        c.exit = Exit.KILL_ENEMY;
    }

    /** AddPoints (HUD and Points/Main.asm:157-180): ADD.L wraps, then the unsigned cap, then at most one 1-up. */
    private static void addPoints(Cursor c, int points) {
        var result = addPoints(new WorldGlobals(c.rings, c.lives, c.updateHudLives, c.score,
                c.nextLifeScore, c.updateHudScore), points);
        var world = result.world();
        c.rings = world.rings(); c.lives = world.lives(); c.updateHudLives = world.updateHudLives();
        c.score = world.score(); c.nextLifeScore = world.nextLifeScore(); c.updateHudScore = world.updateHudScore();
        c.calls.add(new AddPoints(points, result.command().isPresent()));
        result.command().ifPresent(command -> c.calls.add(new SubCpu(command))); // Tail JMP SubCPUCmd.
    }

    /** Player_TouchHazard/.ChkHurt (:211-227). The post-Display hurt WORD gates. */
    private static void touchHazard(Cursor c, int a1) {
        if (c.timeWarp != 0 || c.invincible != 0 || c.hurt != 0) { c.minusOne(Exit.NO_HURT); return; }
        hurtPlayer(c, a1); // MOVEA.L a1,a2: a2 keeps addressing this whole slot.
    }

    /** HurtPlayer (:229-238) and .CheckKill (:272-274). */
    private static void hurtPlayer(Cursor c, int a2) {
        if (c.shield == 0) {
            if (c.rings == 0) {
                if (c.debugCheat != 0) clearCharge(c, a2);
                else killPlayer(c);
                return;
            }
            var found = c.findObjSlot();
            if (found.isPresent()) {
                int a1 = found.getAsInt();
                c.write(c.slot(a1).moveByte(CdObjectState.ByteField.ID, 0x11));
                c.write(c.slot(a1).moveWord(CdObjectState.WordField.X, c.x())); // The PLAYER's position.
                c.write(c.slot(a1).moveWord(CdObjectState.WordField.Y, c.y()));
            }
        }
        clearCharge(c, a2);
    }

    /** .ClearCharge/.SetHurt/.NoWater/.GotXVel (:240-268). */
    private static void clearCharge(Cursor c, int a2) {
        c.charge = 0;
        boolean shielded = (c.shield & 1) != 0; // BCLR #0 sets Z from the old bit.
        c.shield &= ~1;
        if (!shielded) c.combineRing = 0;
        c.routine = 4;
        c.resetOnFloor();
        c.flags |= 2;
        c.yv = -0x400; c.xv = -0x200;
        if ((c.flags & 0x40) != 0) { c.yv = -0x200; c.xv = -0x100; }
        // CMP.W/BCS: unsigned; a2 is read from the working map AFTER any $11 write to the same slot.
        if (c.x() >= c.slot(a2).centreX()) c.xv = sw(-c.xv);
        c.gv = 0; c.anim = 0x1A; c.hurt = 0x78;
        c.minusOne(Exit.HURT);
    }

    /** Complete pure KillPlayer call cut; no world, pool, selected object or asset table is involved. */
    public record KillResult(Actor actor, int invincible, int scoreChain, List<Call> calls, Exit exit) {
        public KillResult {
            Objects.requireNonNull(actor); byteValue(invincible); wordValue(scoreChain);
            calls = List.copyOf(calls); Objects.requireNonNull(exit);
            if (exit != Exit.KILL_PLAYER && exit != Exit.KILL_PLAYER_DEBUG_NO_WRITE) {
                throw new IllegalArgumentException("KillPlayer requires its native return site");
            }
        }
    }

    /**
     * KillPlayer (Player Object Collision.asm:279-298), also called by the two Hurt boundary checks.
     * debugMode is the complete word. The caller commits this result at the actual call position;
     * this helper performs no routine dispatch, integration, camera freeze or continuation.
     */
    public static KillResult killPlayer(Actor actor, int invincible, int debugMode, int scoreChain, int miniSonic) {
        return killPlayer(actor,invincible,debugMode,scoreChain,miniSonic,null);
    }
    /** Source-issued sound cut from the SAME KillPlayer kernel. Values describe the reached
     * prefix; claiming it alone confers no Frame/World/source or sound authority. */
    public static final class KillStep {
        private final KillVisit visit;private final KillPrefix prefix;private boolean claimed;
        private KillStep(KillVisit visit,KillPrefix prefix){this.visit=visit;this.prefix=prefix;}
        private void require(){if(!visit.open||visit.current!=this||visit.thread!=Thread.currentThread())throw new IllegalStateException("Expired/off-thread KillPlayer source cut");}
        public void claim(Object originalInvocation){require();if(claimed||visit.invocation!=originalInvocation)throw new IllegalStateException("Foreign or repeated KillPlayer cut");claimed=true;}
        public Actor actor(){require();return prefix.actor();}
        public int invincible(){require();return prefix.invincible();}
        public int scoreChain(){require();return prefix.scoreChain();}
        public FmSound sound(){require();return prefix.sound();}
    }
    @FunctionalInterface public interface KillObserver {void reached(KillStep step);}
    private static final class KillVisit {
        final Object invocation;final Thread thread=Thread.currentThread();final KillObserver observer;
        boolean open=true;KillStep current;
        KillVisit(Object invocation,KillObserver observer){this.invocation=invocation;this.observer=observer;}
        void emit(KillPrefix prefix){
            var step=new KillStep(this,prefix);current=step;
            try{observer.reached(step);if(!step.claimed)throw new IllegalStateException("Unclaimed KillPlayer source cut");}
            finally{current=null;}
        }
    }
    /** Synchronous source-position hook, not iteration over a completed calls/effects list.
     * Debug's no-write return emits nothing. First callback failure escapes unchanged and expires
     * every step; the caller retains only its actually published prefix. Pure APIs stay unchanged. */
    public static KillResult visitKillPlayer(Actor actor,int invincible,int debugMode,int scoreChain,int miniSonic,
                                             Object originalInvocation,KillObserver observer){
        var visit=new KillVisit(Objects.requireNonNull(originalInvocation),Objects.requireNonNull(observer));
        try{return killPlayer(actor,invincible,debugMode,scoreChain,miniSonic,visit::emit);}
        finally{visit.open=false;visit.current=null;}
    }
    private record KillPrefix(Actor actor,int invincible,int scoreChain,FmSound sound) { }
    private static KillResult killPlayer(Actor actor,int invincible,int debugMode,int scoreChain,int miniSonic,
                                        java.util.function.Consumer<KillPrefix> observer) {

        Objects.requireNonNull(actor); byteValue(invincible); wordValue(debugMode);
        wordValue(scoreChain); byteValue(miniSonic);
        if (debugMode != 0) {
            return new KillResult(actor, invincible, scoreChain, List.of(), Exit.KILL_PLAYER_DEBUG_NO_WRITE);
        }
        var m = actor.motion();
        // Routine is 6 BEFORE Player_ResetOnFloor, exactly as at the native BSR call cut.
        var dying = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), 6, m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(),
                m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var reset = CdPlayerMovement.resetOnFloor(dying, miniSonic);
        var calls = new ArrayList<Call>(); calls.add(new ResetOnFloor());
        for (var sound : reset.sounds()) {
            if (sound.sound() != CdPlayerMovement.Sound.FM_CHARGESTOP) {
                throw new IllegalStateException("ResetOnFloor requests only FM_CHARGESTOP");
            }
            calls.add(new PlayFm(FmSound.FM_CHARGESTOP));
            if(observer!=null)observer.accept(new KillPrefix(new Actor(resetBeforeSound(reset.motion(),actor.motion().playerJump()),
                actor.tile(),actor.priority(),actor.hurt(),actor.rotationDistance()),0,scoreChain,FmSound.FM_CHARGESTOP));
        }
        m = reset.motion();
        int y = m.centreY16_16() >>> 16;
        var killed = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), 0, -0x700, 0, m.routine(),
                m.angle(), m.flags() | 2, m.spriteFlags(), m.xRadius(), m.yRadius(), 0x18, m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), y >>> 8, m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        var result = new Actor(killed, actor.tile() | 0x8000, 0, actor.hurt(), y & 0xFF);
        calls.add(new PlayFm(FmSound.FM_HURT));
        if(observer!=null)observer.accept(new KillPrefix(result,0,reset.scoreChain(),FmSound.FM_HURT));
        return new KillResult(result, 0, reset.scoreChain(), calls, Exit.KILL_PLAYER);
    }

    /** Selected touch calls the SAME KillPlayer owner; no duplicated kill algorithm. */
    private static void killPlayer(Cursor c) {
        boolean observed=c.hurtVisit!=null;
        if(observed&&c.debugMode==0)c.calls.add(new ResetOnFloor());
        var result = killPlayer(new Actor(c.motion(), c.tile, c.priority, c.hurt, c.rotationDistance),
                c.invincible, c.debugMode, c.scoreChain, c.miniSonic,observed?prefix->{
                    c.motion(prefix.actor().motion());c.tile=prefix.actor().tile();c.priority=prefix.actor().priority();
                    c.hurt=prefix.actor().hurt();c.rotationDistance=prefix.actor().rotationDistance();
                    c.invincible=prefix.invincible();c.scoreChain=prefix.scoreChain();
                    c.calls.add(new PlayFm(prefix.sound()));c.hurtVisit.emit(HurtCut.SOUND,prefix.sound());
                }:null);
        c.motion(result.actor().motion());
        c.tile = result.actor().tile(); c.priority = result.actor().priority();
        c.hurt = result.actor().hurt(); c.rotationDistance = result.actor().rotationDistance();
        c.invincible = result.invincible(); c.scoreChain = result.scoreChain();
        if(!observed)c.calls.addAll(result.calls()); c.minusOne(result.exit());
    }

    private static void requireGroup(int group, int expected) {
        if (group != expected) throw new IllegalArgumentException("Handler kind disagrees with the selected oColType group");
    }

    /** Detached per-call staging. Never retained or exposed on failure. */
    private static final class Cursor {
        final CdObjectState[] slots = new CdObjectState[CdObjectPool.DYNAMIC_COUNT];
        final Map<Integer, CdObjectState> changed = new LinkedHashMap<>();
        final List<Call> calls = new ArrayList<>();
        int xpos, ypos, xv, yv, gv, routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl;
        int pri, sec, tile, priority, hurt, rotationDistance;
        int timeWarp, invincible, shield, combineRing, debugCheat, debugMode, scoreChain;
        final int miniSonic;
        int rings, lives, updateHudLives, score, nextLifeScore, updateHudScore;
        OptionalInt d0 = OptionalInt.empty();
        Exit exit;
        // BEGIN boss-only semantic continuation target; legacy calls leave this disabled.
        int bossContinuationWhole = -1;
        HurtVisit hurtVisit;
        // END boss-only semantic continuation target.

        Cursor(Actor actor, PlayerGlobals g, WorldGlobals w, CdObjectPool.Snapshot callCut) {
            callCut.slots().toArray(slots);
            motion(actor.motion());
            tile = actor.tile(); priority = actor.priority(); hurt = actor.hurt(); rotationDistance = actor.rotationDistance();
            timeWarp = g.timeWarp(); invincible = g.invincible(); shield = g.shield(); combineRing = g.combineRing();
            debugCheat = g.debugCheat(); debugMode = g.debugMode(); scoreChain = g.scoreChain(); miniSonic = g.miniSonic();
            rings = w.rings(); lives = w.lives(); updateHudLives = w.updateHudLives(); score = w.score();
            nextLifeScore = w.nextLifeScore(); updateHudScore = w.updateHudScore();
        }
        int x() { return xpos >>> 16; }
        int y() { return ypos >>> 16; }
        CdObjectState slot(int whole) { return slots[whole - CdObjectPool.FIRST_WHOLE_INDEX]; }
        /** One native slot write over the working map; a re-put keeps the first-write order. */
        void write(CdObjectState image) {
            int whole = image.identity().wholeIndex();
            slots[whole - CdObjectPool.FIRST_WHOLE_INDEX] = image;
            changed.put(whole, image);
        }
        /** FindObjSlot (Object Spawner.asm:329-340): first oID-zero byte at the call, no clear or reservation. */
        OptionalInt findObjSlot() {
            var found = OptionalInt.empty();
            for (int whole = CdObjectPool.FIRST_WHOLE_INDEX; whole < CdObjectPool.END_WHOLE_INDEX; whole++) {
                if (slot(whole).id() == 0) { found = OptionalInt.of(whole); break; }
            }
            calls.add(new FindObjSlot(found));
            // BEGIN actual reached return target, never reconstructed from public effects.
            if (bossContinuationWhole >= 0) bossContinuationWhole = found.orElse(CdObjectPool.END_WHOLE_INDEX);
            // END actual reached return target.
            return found;
        }
        /** BSR Player_ResetOnFloor through the single shared implementation. */
        void resetOnFloor() {
            calls.add(new ResetOnFloor());
            int originalJump=jump;
            var reset = CdPlayerMovement.resetOnFloor(motion(), miniSonic);
            if(hurtVisit==null) {
                motion(reset.motion());scoreChain=reset.scoreChain();
            } else motion(resetBeforeSound(reset.motion(),originalJump));
            for (var request : reset.sounds()) {
                if (request.sound() != CdPlayerMovement.Sound.FM_CHARGESTOP) {
                    throw new IllegalStateException("ResetOnFloor requests only FM_CHARGESTOP");
                }
                calls.add(new PlayFm(FmSound.FM_CHARGESTOP));
                if(hurtVisit!=null)hurtVisit.emit(HurtCut.SOUND,FmSound.FM_CHARGESTOP);
            }
            if(hurtVisit!=null) {motion(reset.motion());scoreChain=reset.scoreChain();}
        }
        HurtPrefix hurtPrefix() {
            return new HurtPrefix(new HurtPair(new Actor(motion(),tile,priority,hurt,rotationDistance),
                new PlayerGlobals(timeWarp,invincible,shield,combineRing,debugCheat,debugMode,scoreChain,miniSonic),
                new WorldGlobals(rings,lives,updateHudLives,score,nextLifeScore,updateHudScore)),
                List.copyOf(changed.values()),calls,Optional.ofNullable(exit));
        }
        void minusOne(Exit value) { d0 = OptionalInt.of(-1); exit = value; }
        CdPlayerMotionState motion() {
            return new CdPlayerMotionState(xpos, ypos, xv, yv, gv, routine, angle, flags, spr, xr, yr, anim, prev,
                    jump, charge, lock, stick, ctrl, pri, sec);
        }
        void motion(CdPlayerMotionState m) {
            xpos = m.centreX16_16(); ypos = m.centreY16_16(); xv = m.xVelocity(); yv = m.yVelocity();
            gv = m.groundVelocity(); routine = m.routine(); angle = m.angle(); flags = m.flags(); spr = m.spriteFlags();
            xr = m.xRadius(); yr = m.yRadius(); anim = m.animation(); prev = m.previousAnimation();
            jump = m.playerJump(); charge = m.charge(); lock = m.moveLock(); stick = m.stick();
            ctrl = m.playerControl(); pri = m.previousPrimaryAngle(); sec = m.previousSecondaryAngle();
        }
        // BEGIN boss-only immutable prefix packing; no Exit/d0 invented before common completion.
        BossSpecialValues bossValues() {
            return new BossSpecialValues(new Actor(motion(), tile, priority, hurt, rotationDistance),
                    new PlayerGlobals(timeWarp, invincible, shield, combineRing, debugCheat, debugMode, scoreChain, miniSonic),
                    new WorldGlobals(rings, lives, updateHudLives, score, nextLifeScore, updateHudScore),
                    List.copyOf(changed.values()), calls);
        }
        // END boss-only immutable prefix packing.
        Result finish() {
            return new Result(new Actor(motion(), tile, priority, hurt, rotationDistance),
                    new PlayerGlobals(timeWarp, invincible, shield, combineRing, debugCheat, debugMode, scoreChain, miniSonic),
                    new WorldGlobals(rings, lives, updateHudLives, score, nextLifeScore, updateHudScore),
                    List.copyOf(changed.values()), calls, d0, exit);
        }
    }

    private static int sw(int value) { return (short) value; }
    private static int uw(int value) { return value & 0xFFFF; }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
