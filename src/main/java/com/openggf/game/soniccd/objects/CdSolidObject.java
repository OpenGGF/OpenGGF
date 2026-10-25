package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdTerrainCollision;
import com.openggf.game.soniccd.objects.CdObjectState.ByteField;
import com.openggf.game.soniccd.objects.CdObjectState.WordField;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Pure R11A port of Level/Object Solid.asm at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff (included by
 * Palmtree Panic/Act 1 Present.asm:52, oSize = $40): GetOffObject (:15-55), StandOnObject (:65-146),
 * BtmSolidObject (:159-161), TopSolidObject (:174-175), SolidObject (:188-479), CheckWallCrush (:489-514),
 * PushObject (:524-581) and StopObjPush (:591-607). Shipped FixBugs = 0 behavior throughout.
 *
 * <p>One {@link #run} is one native call of the selected entry with a0 = the calling dynamic object and a1 =
 * objPlayerSlot (whole 0). Its own callees run in source order: the three Collision Check.asm player probes
 * through the shared {@link CdPlayerMovement} ports (Player_CalcRoomInFront :14-65, Player_CheckFloor :79-125,
 * Player_GetCeilDist :298-314) and KillPlayer (PP Player Object Collision.asm:279-298) through the one shared
 * {@link CdPlayerTouchResponse#killPlayer}. No probe, kill or reset arithmetic is duplicated here.
 *
 * <p>Inputs are the object's working image, the post-player slot-0 projection, two read-only gates, four
 * read/write globals and the live call-cut capture, read only where the source dereferences a2 (StandOnObject
 * :117-118, PushObject :542-561); its own a0 entry is never read. This owner retains no pool, capture, Frame or
 * Runtime, invokes no callback, and commits nothing: it plays no sound, writes no pool slot, angle buffer or
 * player state. A {@link Result} carries final values, the native write sets (same-value stores included) and
 * ordered call observations that must never be replayed over those values. Exceptions publish nothing.
 *
 * <p>Source quirks kept: oSolidType is oRoutine2 ($25, _Definitions.inc:59-61), so plain SolidObject reads and
 * clears routine2. The X range uses the constant player width 9 (:202-204), never oXRadius. The side depth test
 * is CMPI.B (:275-276), so a $100 depth is shallow. debugMode is TST.B (its high byte) here but TST.W inside
 * KillPlayer. GetOffObject's charge test requests FM_CHARGESTOP without clearing the charge. A stand byte of 0
 * in StandOnObject's a2 path addresses the player slot itself. KillPlayer's stick word is later cleared by
 * GetOffObject when its standing guards pass on the .GetOff path. .HitBottom overwrites the crushed player's -$700.
 */
public final class CdSolidObject {
    /** objPlayerSlot is whole slot 0 of the object pool (Level/_Definitions.inc:129-131). */
    public static final int PLAYER_WHOLE_INDEX = 0;
    public static final int SPINNING_DISC_ID = 0x09, SPRING_ID = 0x0A, FLOWER_CAPSULE_ID = 0x15, MONITOR_ID = 0x19;
    private static final int DROWNING_ANIMATION = 0x17, BORED_ANIMATION = 0x2B;

    /** The five Object Solid.asm routines that objects call. */
    public enum Entry {
        SOLID_OBJECT, TOP_SOLID_OBJECT, BTM_SOLID_OBJECT, GET_OFF_OBJECT, STAND_ON_OBJECT;

        /** The three solidity entries end in MOVEQ #0/#1,d0; the two subroutines define no d0. */
        public boolean solidity() { return this == SOLID_OBJECT || this == TOP_SOLID_OBJECT || this == BTM_SOLID_OBJECT; }
    }

    /**
     * objPlayerSlot at the call cut, after its own dispatch. actor is the shared player projection (motion, oTile,
     * oPriority, oPlayerHurt word and byte39); slotId is oID ($00), pushObject oPlayerPushObj ($20, oVar20) and
     * standObject oPlayerStandObj ($3D, oVar3D). The two object bytes hold WHOLE pool indices,
     * ((a0 - objects) >> 6) & $7F, so a dynamic object is 32..127. Every byte is explicit ingress.
     */
    public record Player(CdPlayerTouchResponse.Actor actor, int slotId, int pushObject, int standObject) {
        public Player {
            Objects.requireNonNull(actor, "actor");
            byteValue(slotId); byteValue(pushObject); byteValue(standObject);
        }
    }

    /** Read-only gates: the debugMode WORD ($FF1906) and the miniSonic byte ($FF195B). */
    public record Gates(int debugMode, int miniSonic) {
        public Gates { wordValue(debugMode); byteValue(miniSonic); }
    }

    /**
     * Read/write globals: invincible ($FF151F) and scoreChain ($FFF7D0, word) through KillPlayer, and the
     * primaryAngle/secondaryAngle collision buffers through the probes.
     */
    public record Globals(int invincible, int scoreChain, int primaryAngle, int secondaryAngle) {
        public Globals { byteValue(invincible); wordValue(scoreChain); byteValue(primaryAngle); byteValue(secondaryAngle); }
    }

    /** Player-slot fields with native offsets. TILE is BSET #7 on oTile's high byte; HURT is the $30 word. */
    public enum PlayerField {
        TILE(0x02), CENTRE_X(0x08), CENTRE_X_FRACTION(0x0A), CENTRE_Y(0x0C), CENTRE_Y_FRACTION(0x0E),
        X_VELOCITY(0x10), Y_VELOCITY(0x12), GROUND_VELOCITY(0x14), Y_RADIUS(0x16), X_RADIUS(0x17), PRIORITY(0x18),
        ANIMATION(0x1C), PUSH_OBJECT(0x20), FLAGS(0x22), ROUTINE(0x24), ANGLE(0x26), PLAYER_CONTROL(0x2C),
        HURT(0x30), STICK(0x38), ROTATION_DISTANCE(0x39), PLAYER_JUMP(0x3C), STAND_OBJECT(0x3D);
        private final int offset;
        PlayerField(int offset) { this.offset = offset; }
        public int byteOffset() { return offset; }
    }

    public enum GlobalField { INVINCIBLE, SCORE_CHAIN, PRIMARY_ANGLE, SECONDARY_ANGLE }

    /** Object Solid.asm routines entered by BSR/BRA from inside this call. */
    public enum Routine { STOP_OBJ_PUSH, GET_OFF_OBJECT, STAND_ON_OBJECT, PUSH_OBJECT, CHECK_WALL_CRUSH }

    /** Ordered native call observations, listed at entry; a nested call follows the call that makes it. */
    public sealed interface Call permits RoutineCall, PlayFm, RoomInFront, FloorDistance, CeilingDistance, KillPlayerCall { }
    public record RoutineCall(Routine routine) implements Call {
        public RoutineCall { Objects.requireNonNull(routine); }
    }
    /** GetOffObject's MOVE.W #FM_CHARGESTOP,d0 / JSR PlayFMSound (:32-35): a request for the caller's sound owner. */
    public record PlayFm(CdPlayerTouchResponse.FmSound sound) implements Call {
        public PlayFm {
            if (Objects.requireNonNull(sound) != CdPlayerTouchResponse.FmSound.FM_CHARGESTOP) {
                throw new IllegalArgumentException("GetOffObject requests only FM_CHARGESTOP");
            }
        }
    }
    /** Player_CalcRoomInFront with d0.b = angle: d1 and this call's stores to the two angle buffers. */
    public record RoomInFront(int angle, int distance, OptionalInt primaryAngleWrite, OptionalInt secondaryAngleWrite)
            implements Call {
        public RoomInFront { byteValue(angle); probe(distance, primaryAngleWrite, secondaryAngleWrite); }
    }
    /** Player_CheckFloor: d1 chosen by ChooseAngle and this call's stores to the two angle buffers. */
    public record FloorDistance(int distance, OptionalInt primaryAngleWrite, OptionalInt secondaryAngleWrite) implements Call {
        public FloorDistance { probe(distance, primaryAngleWrite, secondaryAngleWrite); }
    }
    /** Player_GetCeilDist: d1 and this call's store to primaryAngle (secondary is never stored). */
    public record CeilingDistance(int distance, OptionalInt primaryAngleWrite, OptionalInt secondaryAngleWrite)
            implements Call {
        public CeilingDistance { probe(distance, primaryAngleWrite, secondaryAngleWrite); }
    }
    /** One executed shared KillPlayer, already adopted ONCE into the final values; its calls are nested here. */
    public record KillPlayerCall(CdPlayerTouchResponse.KillResult result) implements Call {
        public KillPlayerCall { Objects.requireNonNull(result); }
    }

    /** The native return site reached, with its MOVEQ d0 for the solidity entries. */
    public enum Path {
        /** .GetOff (:473-479). */
        GET_OFF(0),
        /** .CollideHoriz .HaltOnX/.ClearXSpd (:303-315). */
        HALT_ON_X(0),
        /** .CollideHoriz .StopPush (:317-324). */
        STOP_PUSH(0),
        /** .StandOn (:426-430). */
        STAND_ON(1),
        /** .HitBottom (:462-471). */
        HIT_BOTTOM(1),
        /** The RTS of a direct GetOffObject/StandOnObject entry; no source d0. */
        ROUTINE_RETURN(-1);
        private final int d0;
        Path(int d0) { this.d0 = d0; }
        public int d0() { return d0; }
    }

    /** The source branch that reached .GetOff, in SolidObject order. */
    public enum GetOffReason {
        /** CMPI.B #$17,oAnim(a1) (:189-190). */
        PLAYER_DROWNING,
        /** BTST #6,oPlayerCtrl(a1) (:191-192). */
        PLAYER_INVISIBLE,
        /** CMPI.B #6,oRoutine(a1) / BCC, unsigned (:193-194). */
        PLAYER_DEAD,
        /** TST.B oID(a1) (:195-196). */
        PLAYER_ABSENT,
        /** TST.B oSprFlags(a0) / BPL: DrawObjects never set bit 7 (:197-198). */
        OBJECT_OFFSCREEN,
        /** TST.B debugMode: the word's high byte (:199-200). */
        DEBUG_MODE,
        /** BMI on the left overlap word (:206-209). */
        X_RANGE_LEFT,
        /** CMP.W / BCC, unsigned (:210-213). */
        X_RANGE_RIGHT,
        /** A bored player is solid only while bit 3 of our oFlags is set (:215-219). */
        BORED_NOT_STOOD_ON,
        /** Top solidity with a rising player (:221-226). */
        TOP_SOLID_RISING,
        /** BMI on the vertical overlap word (:236-239). */
        Y_RANGE_ABOVE,
        /** CMP.W / BCC, unsigned (:240-243). */
        Y_RANGE_BELOW,
        /** Top solidity on a side contact (:266-267). */
        TOP_SOLID_SIDE,
        /** A spring is not solid sideways to an airborne player (:269-272). */
        SPRING_SIDE_AIRBORNE,
        /** CMPI.B #4,d5 / BLS on the depth's low byte (:275-276). */
        SHALLOW_SIDE,
        /** A monitor is not solid vertically to a rolling player (:329-332). */
        MONITOR_ROLLING,
        /** A non-bored player moving up from above (:345-349). */
        RISING_ABOVE,
        /** Player_CheckFloor d1 < 0 after .MoveY (:400-407). */
        FLOOR_UNDER_OBJECT,
        /** Player_GetCeilDist d1 < 0 for a rising object: KillPlayer first (:413-424). */
        CEILING_CRUSH,
        /** Top solidity from below (:435-436). */
        TOP_SOLID_BELOW,
        /** The spinning disc is not solid from below (:437-438). */
        SPINNING_DISC_BELOW,
        /** A spring neither bottom solid nor vertically flipped (:440-447). */
        SPRING_BELOW
    }

    /**
     * Final values after the call. object is the final a0 image; otherSlots are the distinct final images of other
     * dynamic slots written through a2 (only StandOnObject's BCLR #3,oFlags), in first-write order; the player
     * slot's own a2 write is part of player. The write sets name every native store, including same-value
     * stores: every native storage offset outside them equals its input. Object SOLID_TYPE denotes byte $25,
     * also aliased by ROUTINE_2; an alias name is not another store. getOffReason is present exactly for {@link Path#GET_OFF};
     * d0 is the MOVEQ long for the solidity entries and absent for the two subroutine entries.
     */
    public record Result(Entry entry, CdObjectState object, Player player, Globals globals, List<CdObjectState> otherSlots,
                         Set<ByteField> objectWrites, Set<PlayerField> playerWrites, Set<GlobalField> globalWrites,
                         List<Call> calls, Path path, Optional<GetOffReason> getOffReason, OptionalInt d0) {
        public Result {
            Objects.requireNonNull(entry); Objects.requireNonNull(object); Objects.requireNonNull(player);
            Objects.requireNonNull(globals); Objects.requireNonNull(path); Objects.requireNonNull(getOffReason);
            Objects.requireNonNull(d0);
            otherSlots = List.copyOf(otherSlots); objectWrites = Set.copyOf(objectWrites);
            playerWrites = Set.copyOf(playerWrites); globalWrites = Set.copyOf(globalWrites); calls = List.copyOf(calls);
            var identities = new HashSet<Integer>();
            identities.add(object.identity().wholeIndex());
            for (var slot : otherSlots) {
                if (!identities.add(slot.identity().wholeIndex())) {
                    throw new IllegalArgumentException("Each slot is published once, as its final image, apart from a0");
                }
            }
            if (entry.solidity() == (path == Path.ROUTINE_RETURN) || getOffReason.isPresent() != (path == Path.GET_OFF)
                    || d0.isPresent() != entry.solidity() || (d0.isPresent() && d0.getAsInt() != path.d0())) {
                throw new IllegalArgumentException("Inconsistent native return site " + path + " for " + entry);
            }
        }

        /** Every FM request in source order, including the nested KillPlayer/ResetOnFloor requests. */
        public List<CdPlayerTouchResponse.FmSound> fmRequests() {
            var sounds = new ArrayList<CdPlayerTouchResponse.FmSound>();
            for (var call : calls) {
                if (call instanceof PlayFm request) {
                    sounds.add(request.sound());
                } else if (call instanceof KillPlayerCall killed) {
                    for (var inner : killed.result().calls()) {
                        if (inner instanceof CdPlayerTouchResponse.PlayFm nested) sounds.add(nested.sound());
                    }
                }
            }
            return List.copyOf(sounds);
        }
    }

    public enum UnsupportedBoundary {
        /** A stand/push byte 1..31 addresses a reserved slot (HUD, shield, stars...) that has no owner here. */
        RESERVED_SLOT_ADDRESS,
        /**
         * A stand/push byte 128..255 does not address an object slot: 128..191 reach $FFF000..$FFFFC0,
         * while 192..255 wrap on the 24-bit bus to $000000..$000FC0.
         */
        OUT_OF_POOL_ADDRESS
    }

    /** Thrown at the dereference itself; nothing staged before it is published. */
    public static final class UnsupportedSolidObject extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int slotByte;
        private final Routine routine;

        private UnsupportedSolidObject(UnsupportedBoundary boundary, int slotByte, Routine routine) {
            super(boundary + " in " + routine + ": slot byte $" + Integer.toHexString(slotByte) + " addresses $"
                    + Integer.toHexString((0xFFD000 + slotByte * CdObjectState.BYTE_SIZE) & 0xFFFFFF));
            this.boundary = boundary; this.slotByte = slotByte; this.routine = routine;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int slotByte() { return slotByte; }
        public Routine routine() { return routine; }
    }

    /**
     * Stores of an executed KillPlayer (PP Player Object Collision.asm:282-292: oRoutine, oFlags, oYVel, oXVel,
     * oPlayerGVel, the oPlayerStick word $38-$39, oAnim, oTile's high byte, oPriority) and of its
     * Player_ResetOnFloor (Sonic/Main.asm:2301-2303, 2328: oFlags, oPlayerJump), plus invincible and scoreChain.
     */
    private static final Set<PlayerField> KILL_PLAYER_WRITES = Set.of(PlayerField.ROUTINE, PlayerField.FLAGS,
            PlayerField.PLAYER_JUMP, PlayerField.Y_VELOCITY, PlayerField.X_VELOCITY, PlayerField.GROUND_VELOCITY,
            PlayerField.STICK, PlayerField.ROTATION_DISTANCE, PlayerField.ANIMATION, PlayerField.TILE, PlayerField.PRIORITY);
    /** Player_ResetOnFloor's rolled branch (Sonic/Main.asm:2305-2323): both radii and SUBQ.W on oY. */
    private static final Set<PlayerField> KILL_PLAYER_RESIZE_WRITES = Set.of(PlayerField.Y_RADIUS, PlayerField.X_RADIUS,
            PlayerField.CENTRE_Y);

    private final CdPlayerMovement probes;

    /**
     * Pure consumer of the shared player probe owner; it authenticates nothing. Production binds the owner built
     * from the authenticated R11A terrain and player assets through {@link #fromAssets}.
     */
    public CdSolidObject(CdPlayerMovement probes) {
        this.probes = Objects.requireNonNull(probes, "probes");
    }

    /** The same terrain and player assets the Body's own movement owner uses; no literal or fallback data. */
    public static CdSolidObject fromAssets(CdR11aAssets assets) {
        Objects.requireNonNull(assets, "assets");
        return new CdSolidObject(new CdPlayerMovement(assets.terrain(), assets.player()));
    }

    /**
     * One native call. object is a0's working image (its pre-call writes included); callCut is the live pool
     * capture at this call, consulted only for other slots that StandOnObject/PushObject dereference. A caller
     * running two calls in one body (ObjFloatBlock_TopSolid runs TopSolidObject twice, Floating Block/Main.asm:
     * 156-165) threads the first Result's object, player and globals into the second, and commits or recaptures
     * its otherSlots before that second call.
     */
    public Result run(Entry entry, CdObjectState object, Player player, Gates gates, Globals globals,
                      CdObjectPool.Snapshot callCut) {
        Objects.requireNonNull(entry, "entry"); Objects.requireNonNull(object, "object");
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(gates, "gates");
        Objects.requireNonNull(globals, "globals"); Objects.requireNonNull(callCut, "call-cut capture");
        var c = new Cursor(entry, object, player, gates, globals, callCut);
        switch (entry) {
            case SOLID_OBJECT -> solidObject(c);
            case TOP_SOLID_OBJECT -> { c.objectByte(ByteField.SOLID_TYPE, 1); solidObject(c); } // :175
            case BTM_SOLID_OBJECT -> { c.objectByte(ByteField.SOLID_TYPE, 2); solidObject(c); } // :160-161
            case GET_OFF_OBJECT -> { getOffObject(c); c.routineReturn(); }
            case STAND_ON_OBJECT -> { standOnObject(c); c.routineReturn(); }
        }
        return c.finish();
    }

    /** SolidObject (:188-276): gates, range words and the vertical/horizontal decision. */
    private void solidObject(Cursor c) {
        if (c.anim == DROWNING_ANIMATION) { getOff(c, GetOffReason.PLAYER_DROWNING); return; }
        if ((c.ctrl & 0x40) != 0) { getOff(c, GetOffReason.PLAYER_INVISIBLE); return; }
        if (c.routine >= 6) { getOff(c, GetOffReason.PLAYER_DEAD); return; }
        if (c.slotId == 0) { getOff(c, GetOffReason.PLAYER_ABSENT); return; }
        if ((c.object.spriteFlags() & 0x80) == 0) { getOff(c, GetOffReason.OBJECT_OFFSCREEN); return; }
        if ((c.gates.debugMode() >>> 8) != 0) { getOff(c, GetOffReason.DEBUG_MODE); return; }
        int d1 = uw(c.object.signedByte(ByteField.WIDTH) + 9 + 1);        // EXT.W oWidth; ADDI.W #9+1 (:202-204)
        int d0 = uw(c.x() - c.object.centreX() + d1);
        if (negative(d0)) { getOff(c, GetOffReason.X_RANGE_LEFT); return; }
        int d2 = uw(d1 + d1);
        if (d0 >= d2) { getOff(c, GetOffReason.X_RANGE_RIGHT); return; }
        if (c.anim == BORED_ANIMATION) {
            if ((c.object.flags() & 8) == 0) { getOff(c, GetOffReason.BORED_NOT_STOOD_ON); return; }
        } else if (c.solidType() == 1 && c.yv < 0) {                       // TST.W: zero continues, BMI gets off
            getOff(c, GetOffReason.TOP_SOLID_RISING); return;
        }
        int d3 = uw(c.object.signedByte(ByteField.Y_RADIUS) + (byte) c.yr + 2); // both radii EXT.W (:229-234)
        d2 = uw(c.y() - c.object.centreY() + d3);
        if (negative(d2)) { getOff(c, GetOffReason.Y_RANGE_ABOVE); return; }
        if (d2 >= uw(d3 + d3)) { getOff(c, GetOffReason.Y_RANGE_BELOW); return; }
        int d4 = d0;                                                        // .GotDistToHEdge (:245-251)
        if (d1 < d0) { d1 = uw(d1 + d1); d0 = uw(d0 - d1); d4 = uw(-d0); }
        int d5 = d2;                                                        // .GotDistToVEdge (:254-260)
        if (d3 < d2) { d3 = uw(d3 + d3); d2 = uw(d2 - d3); d5 = uw(-d2); }
        if (d5 < d4) { collideVertically(c, d2); return; }                  // CMP.W d4,d5 / BCS (:263-264)
        if (c.solidType() == 1) { getOff(c, GetOffReason.TOP_SOLID_SIDE); return; }
        if (c.object.id() == SPRING_ID && (c.flags & 2) != 0) { getOff(c, GetOffReason.SPRING_SIDE_AIRBORNE); return; }
        if ((d5 & 0xFF) <= 4) { getOff(c, GetOffReason.SHALLOW_SIDE); return; }
        collideHorizontally(c, d0);
    }

    /** .CollideHoriz (:280-324). d0 is the signed edge word; PushObject's KillPlayer and GetOffObject keep it. */
    private void collideHorizontally(Cursor c, int d0) {
        pushObject(c);
        c.call(Routine.GET_OFF_OBJECT); getOffObject(c);
        c.objectByte(ByteField.SOLID_TYPE, 0);
        c.setXWord(c.x() - d0);                                             // SUB.W d0,oX(a1): oXSub kept
        boolean halt = negative(d0) ? c.xv < 0 : c.xv > 0;                  // .PlayerRight/.PlayerLeft (:289-301)
        if (halt) {
            checkWallCrush(c);
            if ((c.flags & 2) == 0) {                                       // :305-309
                c.setFlags(c.flags | 0x20);
                c.objectByte(ByteField.FLAGS, c.object.flags() | 0x20);
                c.setGroundVelocity(0);
            }
            c.setXVelocity(0);
            c.returnAt(Path.HALT_ON_X);
        } else {
            stopObjPush(c);
            checkWallCrush(c);
            c.setFlags(c.flags & ~0x20);
            c.objectByte(ByteField.FLAGS, c.object.flags() & ~0x20);
            c.returnAt(Path.STOP_PUSH);
        }
    }

    /** .CollideVert/.NotMonitor (:328-342): TST.W d2, where zero counts as above. */
    private void collideVertically(Cursor c, int d2) {
        if (c.object.id() == MONITOR_ID && (c.flags & 4) != 0) { getOff(c, GetOffReason.MONITOR_ROLLING); return; }
        int d1 = uw(c.object.signedByte(ByteField.Y_RADIUS) + (byte) c.yr);
        if (negative(d2)) playerBelow(c, d2); else playerAbove(c, d1);
    }

    /** .PlayerAbove, .MoveOnObject, .MoveY, .CheckFloor, .CheckCeiling and .StandOn (:344-430). */
    private void playerAbove(Cursor c, int d1) {
        if (c.anim != BORED_ANIMATION && c.yv < 0) { getOff(c, GetOffReason.RISING_ABOVE); return; }
        c.setYWord(c.object.centreY());                                     // MOVE.W oY(a0),oY(a1)
        c.setYWord(c.y() - d1);                                             // SUB.W d1,oY(a1)
        int objectXVelocity = c.object.signedWord(WordField.X_VELOCITY);
        c.setXLong(c.xpos + (objectXVelocity << 8));                        // EXT.L/ASL.L #8/ADD.L/MOVE.L (:355-361)
        if (objectXVelocity != 0) {                                         // $C0 right, NEG.B -> $40 left (:363-367)
            int distance = roomInFront(c, objectXVelocity > 0 ? 0xC0 : 0x40);
            if (distance < 0) c.setXWord(c.x() + (objectXVelocity > 0 ? distance : -distance)); // :374-382
        }
        int objectYVelocity = c.object.signedWord(WordField.Y_VELOCITY);
        c.setYLong(c.ypos + (objectYVelocity << 8));                        // :385-391
        if (c.object.id() != SPRING_ID) {                                   // A spring skips both probes (:393-394)
            if (objectYVelocity >= 0) {
                int distance = checkFloor(c);
                if (distance < 0) {                                         // ADD.W d1,oY(a1); BRA .GetOff (:404-407)
                    c.setYWord(c.y() + distance);
                    getOff(c, GetOffReason.FLOOR_UNDER_OBJECT); return;
                }
            } else {
                int distance = ceilingDistance(c);
                if (distance < 0) {                                         // KillPlayer, then .GetOff (:417-424)
                    killPlayer(c);
                    getOff(c, GetOffReason.CEILING_CRUSH); return;
                }
            }
        }
        c.call(Routine.STAND_ON_OBJECT); standOnObject(c);
        c.returnAt(Path.STAND_ON);
    }

    /** .PlayerBelow, .CheckCrushFloor and .HitBottom (:434-471). d2 is the negative edge word. */
    private void playerBelow(Cursor c, int d2) {
        if (c.solidType() == 1) { getOff(c, GetOffReason.TOP_SOLID_BELOW); return; }
        if (c.object.id() == SPINNING_DISC_ID) { getOff(c, GetOffReason.SPINNING_DISC_BELOW); return; }
        if (c.object.id() == SPRING_ID) {
            if (c.solidType() != 2 && (c.object.spriteFlags() & 2) == 0) { getOff(c, GetOffReason.SPRING_BELOW); return; }
        } else if ((c.flags & 2) == 0 && c.object.signedWord(WordField.Y_VELOCITY) > 0) {
            killPlayer(c);                                                  // A grounded player under a descent
        }
        c.setYWord(c.y() - d2);                                             // SUB.W d2,oY(a1)
        c.setYVelocity(0);
        stopObjPush(c);
        c.call(Routine.GET_OFF_OBJECT); getOffObject(c);
        c.objectByte(ByteField.SOLID_TYPE, 0);
        c.returnAt(Path.HIT_BOTTOM);
    }

    /** .GetOff (:473-479). */
    private void getOff(Cursor c, GetOffReason reason) {
        stopObjPush(c);
        c.call(Routine.GET_OFF_OBJECT); getOffObject(c);
        c.objectByte(ByteField.SOLID_TYPE, 0);
        c.getOff(reason);
    }

    /** CheckWallCrush (:489-514): the OBJECT's X velocity selects the probe; the player's motion predicts it. */
    private void checkWallCrush(Cursor c) {
        c.call(Routine.CHECK_WALL_CRUSH);
        int objectXVelocity = c.object.signedWord(WordField.X_VELOCITY);
        if (objectXVelocity == 0 || c.object.id() == SPRING_ID) return;
        if (roomInFront(c, objectXVelocity > 0 ? 0xC0 : 0x40) < 0) killPlayer(c);
    }

    /** PushObject (:524-581): MOVEM keeps d0 across its KillPlayer; the crush RTS skips .SetPushObj. */
    private void pushObject(Cursor c) {
        c.call(Routine.PUSH_OBJECT);
        if (c.object.id() == SPRING_ID) { c.setPushObject(0); return; }
        int pushed = c.push;
        if (pushed == 0) { c.setPushObject(c.wholeIndex & 0x7F); return; } // .SetPushObj: LSR.W #6, ANDI.W #$7F
        if (pushed == c.wholeIndex) return;                                 // CMPA.W: byte == our whole index
        var other = c.dynamic(pushed, Routine.PUSH_OBJECT);                 // MOVEA.L d1,a2 (:542)
        if (c.object.signedWord(WordField.X_VELOCITY) == 0 && other.signedWord(WordField.X_VELOCITY) == 0) return;
        int playerX = c.x();                                                // CMP.W/BCC/BCS: unsigned words
        boolean crush = playerX >= c.object.centreX() ? playerX < other.centreX() : playerX >= other.centreX();
        if (!crush || c.object.id() == FLOWER_CAPSULE_ID) return;
        killPlayer(c);
    }

    /** StopObjPush (:591-607): compares only. */
    private static void stopObjPush(Cursor c) {
        c.call(Routine.STOP_OBJ_PUSH);
        if (c.push != 0 && c.push == c.wholeIndex) c.setPushObject(0);
    }

    /** GetOffObject (:15-55): compares the stand byte with our physical slot and never dereferences it. */
    private static void getOffObject(Cursor c) {
        if ((c.object.flags() & 8) == 0 || (c.flags & 8) == 0 || c.stand != c.wholeIndex) return;
        if (c.charge != 0) c.calls.add(new PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP));
        c.setStick(0);
        c.setFlags(c.flags | 2);
        c.setFlags(c.flags & ~8);
        c.objectByte(ByteField.FLAGS, c.object.flags() & ~8);
        if ((c.ctrl & 0x40) == 0 && c.anim != DROWNING_ANIMATION) c.setControl(c.ctrl & ~1);
        c.setStandObject(0);                                                // .ReleasePlayer
        if (c.anim == BORED_ANIMATION) c.setFlags(c.flags & ~2);
    }

    /** StandOnObject (:65-146). */
    private static void standOnObject(Cursor c) {
        if (c.routine == 4) { c.setRoutine(2); c.setHurt(120); }           // SUBQ.B #2 / MOVE.W #120 (:66-69)
        c.objectByte(ByteField.SOLID_TYPE, 0);
        c.setJump(0);
        boolean objectWasStoodOn = (c.object.flags() & 8) != 0;
        c.objectByte(ByteField.FLAGS, c.object.flags() | 8);               // BSET #3 / BNE (:75-76)
        if (!objectWasStoodOn) {
            if (c.anim == BORED_ANIMATION) {                                // :78-81, BRA.W returns for us
                c.objectByte(ByteField.FLAGS, c.object.flags() & ~8);
                c.call(Routine.GET_OFF_OBJECT); getOffObject(c);
                return;
            }
            boolean rolled = (c.flags & 4) != 0;
            c.setFlags(c.flags & ~0x10);
            c.setFlags(c.flags & ~4);                                       // BCLR #2 / BEQ (:85-86)
            if (rolled) {                                                   // No sound, unlike Player_ResetOnFloor
                if (c.gates.miniSonic() != 0) { c.setRadii(5, 0x0A); c.setYWord(c.y() - 2); }
                else { c.setRadii(9, 0x13); c.setYWord(c.y() - 5); }
                c.setAnimation(0);
            }
        }
        boolean playerWasStanding = (c.flags & 8) != 0;
        c.setFlags(c.flags | 8);                                            // BSET #3 / BEQ (:104-105)
        if (playerWasStanding) {
            if (c.stand == c.wholeIndex) return;                            // Already us: no .SetInteract (:107-116)
            releaseStoodOn(c);                                             // MOVEA.L d0,a2 / BCLR #3 (:117-118)
        }
        c.setStandObject(c.wholeIndex & 0x7F);                              // .SetInteract (:121-129)
        c.setAngle(0);
        c.setYVelocity(0);
        if (c.object.id() != SPRING_ID || c.object.routine() != 2) c.setGroundVelocity(c.xv); // :134-140
        c.setFlags(c.flags & ~2);
    }

    /** KillPlayer through the one shared owner with a0 := a1; the debugMode WORD is its own gate (:280-281). */
    private static void killPlayer(Cursor c) {
        boolean rolled = (c.flags & 4) != 0;
        var result = CdPlayerTouchResponse.killPlayer(c.actor(), c.invincible, c.gates.debugMode(), c.scoreChain,
                c.gates.miniSonic());
        c.adopt(result.actor());
        c.invincible = result.invincible(); c.scoreChain = result.scoreChain();
        c.calls.add(new KillPlayerCall(result));
        if (result.exit() == CdPlayerTouchResponse.Exit.KILL_PLAYER) {
            c.playerWrites.addAll(KILL_PLAYER_WRITES);
            if (rolled) c.playerWrites.addAll(KILL_PLAYER_RESIZE_WRITES);
            c.globalWrites.add(GlobalField.INVINCIBLE); c.globalWrites.add(GlobalField.SCORE_CHAIN);
        }
    }

    /** Player_CalcRoomInFront: both buffers := d0 (:27-28) before its single probe. */
    private int roomInFront(Cursor c, int angle) {
        if (c.prefixReads) return roomInFrontWithPrefix(c, angle);
        var probe = probes.calcRoomInFront(c.motion(), angle);
        if (probe.primaryAngleWrite().isEmpty() || probe.secondaryAngleWrite().isEmpty()) {
            throw new IllegalStateException("Player_CalcRoomInFront stores d0 to both angle buffers");
        }
        c.angleWrites(probe.primaryAngleWrite(), probe.secondaryAngleWrite());
        c.calls.add(new RoomInFront(angle, probe.distance(), probe.primaryAngleWrite(), probe.secondaryAngleWrite()));
        return probe.distance();
    }

    private int checkFloor(Cursor c) {
        if (c.prefixReads) return checkFloorWithPrefix(c);
        var probe = probes.checkFloor(c.motion());
        c.angleWrites(probe.primaryAngleWrite(), probe.secondaryAngleWrite());
        c.calls.add(new FloorDistance(probe.distance(), probe.primaryAngleWrite(), probe.secondaryAngleWrite()));
        return probe.distance();
    }

    private int ceilingDistance(Cursor c) {
        if (c.prefixReads) return ceilingDistanceWithPrefix(c);
        var probe = probes.getCeilDist(c.motion());
        if (probe.secondaryAngleWrite().isPresent()) {
            throw new IllegalStateException("Player_GetCeilDist never stores secondaryAngle");
        }
        c.angleWrites(probe.primaryAngleWrite(), probe.secondaryAngleWrite());
        c.calls.add(new CeilingDistance(probe.distance(), probe.primaryAngleWrite(), probe.secondaryAngleWrite()));
        return probe.distance();
    }

    /** Detached per-call staging. Never retained, exposed or reused after a failure. */
    private static final class Cursor {
        final Entry entry;
        final int wholeIndex;
        final Gates gates;
        final int slotId;
        final CdObjectState[] slots = new CdObjectState[CdObjectPool.DYNAMIC_COUNT];
        final Map<Integer, CdObjectState> changed = new LinkedHashMap<>();
        final List<Call> calls = new ArrayList<>();
        final Set<ByteField> objectWrites = EnumSet.noneOf(ByteField.class);
        final Set<PlayerField> playerWrites = EnumSet.noneOf(PlayerField.class);
        final Set<GlobalField> globalWrites = EnumSet.noneOf(GlobalField.class);
        CdObjectState object;
        int xpos, ypos, xv, yv, gv, routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl;
        int previousPrimary, previousSecondary, tile, priority, hurt, rotationDistance, push, stand;
        int invincible, scoreChain, primaryAngle, secondaryAngle;
        Path path;
        GetOffReason reason;
        boolean prefixReads; // Invocation-local opt-in only; legacy run leaves it false.

        Cursor(Entry entry, CdObjectState object, Player player, Gates gates, Globals globals, CdObjectPool.Snapshot callCut) {
            this.entry = entry; this.object = object; this.gates = gates;
            wholeIndex = object.identity().wholeIndex();
            callCut.slots().toArray(slots);
            adopt(player.actor());
            slotId = player.slotId(); push = player.pushObject(); stand = player.standObject();
            invincible = globals.invincible(); scoreChain = globals.scoreChain();
            primaryAngle = globals.primaryAngle(); secondaryAngle = globals.secondaryAngle();
        }

        int x() { return xpos >>> 16; }
        int y() { return ypos >>> 16; }
        int solidType() { return object.unsignedByte(ByteField.SOLID_TYPE); }
        void call(Routine routine) { calls.add(new RoutineCall(routine)); }

        void objectByte(ByteField field, int value) { object = object.moveByte(field, value); objectWrites.add(field); }
        void setXWord(int value) { xpos = (uw(value) << 16) | (xpos & 0xFFFF); playerWrites.add(PlayerField.CENTRE_X); }
        void setYWord(int value) { ypos = (uw(value) << 16) | (ypos & 0xFFFF); playerWrites.add(PlayerField.CENTRE_Y); }
        void setXLong(int value) {
            xpos = value; playerWrites.add(PlayerField.CENTRE_X); playerWrites.add(PlayerField.CENTRE_X_FRACTION);
        }
        void setYLong(int value) {
            ypos = value; playerWrites.add(PlayerField.CENTRE_Y); playerWrites.add(PlayerField.CENTRE_Y_FRACTION);
        }
        void setXVelocity(int value) { xv = (short) value; playerWrites.add(PlayerField.X_VELOCITY); }
        void setYVelocity(int value) { yv = (short) value; playerWrites.add(PlayerField.Y_VELOCITY); }
        void setGroundVelocity(int value) { gv = (short) value; playerWrites.add(PlayerField.GROUND_VELOCITY); }
        void setFlags(int value) { flags = value & 0xFF; playerWrites.add(PlayerField.FLAGS); }
        void setRoutine(int value) { routine = value & 0xFF; playerWrites.add(PlayerField.ROUTINE); }
        void setHurt(int value) { hurt = uw(value); playerWrites.add(PlayerField.HURT); }
        void setJump(int value) { jump = value & 0xFF; playerWrites.add(PlayerField.PLAYER_JUMP); }
        void setStick(int value) { stick = value & 0xFF; playerWrites.add(PlayerField.STICK); }
        void setControl(int value) { ctrl = value & 0xFF; playerWrites.add(PlayerField.PLAYER_CONTROL); }
        void setAngle(int value) { angle = value & 0xFF; playerWrites.add(PlayerField.ANGLE); }
        void setAnimation(int value) { anim = value & 0xFF; playerWrites.add(PlayerField.ANIMATION); }
        void setPushObject(int value) { push = value & 0xFF; playerWrites.add(PlayerField.PUSH_OBJECT); }
        void setStandObject(int value) { stand = value & 0xFF; playerWrites.add(PlayerField.STAND_OBJECT); }
        void setRadii(int xRadius, int yRadius) {
            yr = yRadius; playerWrites.add(PlayerField.Y_RADIUS);
            xr = xRadius; playerWrites.add(PlayerField.X_RADIUS);
        }

        void angleWrites(OptionalInt primary, OptionalInt secondary) {
            if (primary.isPresent()) { primaryAngle = primary.getAsInt(); globalWrites.add(GlobalField.PRIMARY_ANGLE); }
            if (secondary.isPresent()) { secondaryAngle = secondary.getAsInt(); globalWrites.add(GlobalField.SECONDARY_ANGLE); }
        }

        /** The physical slot a stand/push byte addresses: byte * $40 + $FFD000. */
        CdObjectState dynamic(int slotByte, Routine routine) {
            if (slotByte >= CdObjectPool.END_WHOLE_INDEX) {
                throw new UnsupportedSolidObject(UnsupportedBoundary.OUT_OF_POOL_ADDRESS, slotByte, routine);
            }
            if (slotByte < CdObjectPool.FIRST_WHOLE_INDEX) {
                throw new UnsupportedSolidObject(UnsupportedBoundary.RESERVED_SLOT_ADDRESS, slotByte, routine);
            }
            return slots[slotByte - CdObjectPool.FIRST_WHOLE_INDEX];
        }

        /** BCLR #3,oFlags(a2) for the previously stood-on byte; byte 0 is objPlayerSlot itself. */
        void releaseStoodOn(int slotByte) {
            if (slotByte == PLAYER_WHOLE_INDEX) { setFlags(flags & ~8); return; }
            var slot = dynamic(slotByte, Routine.STAND_ON_OBJECT);
            var released = slot.moveByte(ByteField.FLAGS, slot.flags() & ~8);
            slots[slotByte - CdObjectPool.FIRST_WHOLE_INDEX] = released;
            changed.put(slotByte, released);
        }

        CdPlayerMotionState motion() {
            return new CdPlayerMotionState(xpos, ypos, xv, yv, gv, routine, angle, flags, spr, xr, yr, anim, prev, jump,
                    charge, lock, stick, ctrl, previousPrimary, previousSecondary);
        }
        CdPlayerTouchResponse.Actor actor() {
            return new CdPlayerTouchResponse.Actor(motion(), tile, priority, hurt, rotationDistance);
        }
        void adopt(CdPlayerTouchResponse.Actor actor) {
            var m = actor.motion();
            xpos = m.centreX16_16(); ypos = m.centreY16_16(); xv = m.xVelocity(); yv = m.yVelocity(); gv = m.groundVelocity();
            routine = m.routine(); angle = m.angle(); flags = m.flags(); spr = m.spriteFlags(); xr = m.xRadius();
            yr = m.yRadius(); anim = m.animation(); prev = m.previousAnimation(); jump = m.playerJump(); charge = m.charge();
            lock = m.moveLock(); stick = m.stick(); ctrl = m.playerControl();
            previousPrimary = m.previousPrimaryAngle(); previousSecondary = m.previousSecondaryAngle();
            tile = actor.tile(); priority = actor.priority(); hurt = actor.hurt(); rotationDistance = actor.rotationDistance();
        }

        void returnAt(Path value) { path = value; }
        void getOff(GetOffReason value) { path = Path.GET_OFF; reason = value; }
        void routineReturn() { path = Path.ROUTINE_RETURN; }

        Prefix prefix() {
            return new Prefix(entry, object, new Player(actor(), slotId, push, stand),
                    new Globals(invincible, scoreChain, primaryAngle, secondaryAngle), List.copyOf(changed.values()),
                    objectWrites, playerWrites, globalWrites, calls);
        }

        /** The probe writer updates these same two globals at the reached native store, never on replay. */
        void probeAngleWrite(CdPlayerMovement.AngleBuffer buffer, int rawByte) {
            byteValue(rawByte);
            switch (Objects.requireNonNull(buffer)) {
                case PRIMARY -> { primaryAngle = rawByte; globalWrites.add(GlobalField.PRIMARY_ANGLE); }
                case SECONDARY -> { secondaryAngle = rawByte; globalWrites.add(GlobalField.SECONDARY_ANGLE); }
            }
        }

        Result finish() {
            OptionalInt d0 = entry.solidity() ? OptionalInt.of(path.d0()) : OptionalInt.empty();
            return new Result(entry, object, new Player(actor(), slotId, push, stand),
                    new Globals(invincible, scoreChain, primaryAngle, secondaryAngle), List.copyOf(changed.values()),
                    objectWrites, playerWrites, globalWrites, calls, path, Optional.ofNullable(reason), d0);
        }
    }

    private static void probe(int distance, OptionalInt primary, OptionalInt secondary) {
        if (distance != (short) distance) throw new IllegalArgumentException("Probe distance must be a signed native word");
        Objects.requireNonNull(primary).ifPresent(CdSolidObject::byteValue);
        Objects.requireNonNull(secondary).ifPresent(CdSolidObject::byteValue);
    }
    private static boolean negative(int word) { return (word & 0x8000) != 0; }
    private static int uw(int value) { return value & 0xFFFF; }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }

    /** One complete native return, or an exact reached unavailable native read without a return value. */
    public sealed interface Outcome permits Completed, NativeStopped { }

    public record Completed(Result completedResult) implements Outcome {
        public Completed { Objects.requireNonNull(completedResult, "completed result"); }
    }

    public record NativeStopped(Prefix prefix, NativeStop stop) implements Outcome {
        public NativeStopped { Objects.requireNonNull(prefix, "native prefix"); Objects.requireNonNull(stop, "native stop"); }
    }

    /**
     * Current cursor values and reached native store masks, with only completed probe/kill observations.
     * RoutineCall still records entry, not return. These immutable values grant no pool/Frame/phase authority,
     * contain no d0 or native return site, and are never a resumable call or instructions for replay.
     */
    public record Prefix(Entry entry, CdObjectState object, Player player, Globals globals, List<CdObjectState> otherSlots,
                         Set<ByteField> objectWrites, Set<PlayerField> playerWrites, Set<GlobalField> globalWrites,
                         List<Call> calls) {
        public Prefix {
            Objects.requireNonNull(entry); Objects.requireNonNull(object); Objects.requireNonNull(player);
            Objects.requireNonNull(globals);
            otherSlots = List.copyOf(otherSlots); objectWrites = Set.copyOf(objectWrites);
            playerWrites = Set.copyOf(playerWrites); globalWrites = Set.copyOf(globalWrites); calls = List.copyOf(calls);
            var identities = new HashSet<Integer>();
            identities.add(object.identity().wholeIndex());
            for (var slot : otherSlots) {
                if (!identities.add(slot.identity().wholeIndex())) {
                    throw new IllegalArgumentException("Each slot is published once, as its final image, apart from a0");
                }
            }
        }

        /** Extract completed ordered requests only; this method performs no execution or publication. */
        public List<CdPlayerTouchResponse.FmSound> fmRequests() {
            var sounds = new ArrayList<CdPlayerTouchResponse.FmSound>();
            for (var call : calls) {
                if (call instanceof PlayFm request) {
                    sounds.add(request.sound());
                } else if (call instanceof KillPlayerCall killed) {
                    for (var inner : killed.result().calls()) {
                        if (inner instanceof CdPlayerTouchResponse.PlayFm nested) sounds.add(nested.sound());
                    }
                }
            }
            return List.copyOf(sounds);
        }
    }

    public enum StopSite { ROOM_PRIMARY, FLOOR_PRIMARY, FLOOR_SECONDARY, CEILING_PRIMARY, STAND_RELEASE }

    public sealed interface StopDetail permits TerrainWord, CollisionId, SlotAddress { }

    public record TerrainWord(CdTerrainCollision.Address address,
                              CdTerrainCollision.UnavailableReason reason) implements StopDetail {
        public TerrainWord { Objects.requireNonNull(address); Objects.requireNonNull(reason); }
    }

    public record CollisionId(int blockIndex) implements StopDetail { }

    public record SlotAddress(UnsupportedBoundary boundary, int slotByte, Routine routine) implements StopDetail {
        public SlotAddress { Objects.requireNonNull(boundary); byteValue(slotByte); Objects.requireNonNull(routine); }
    }

    /** The actual legacy exception instance, retained unchanged for the caller's terminal rethrow. */
    public record NativeStop(StopSite site, StopDetail detail, RuntimeException originalException) {
        public NativeStop {
            Objects.requireNonNull(site); Objects.requireNonNull(detail); Objects.requireNonNull(originalException);
        }
    }

    /**
     * Opt-in TopSolidObject/GetOffObject call. Uses the same native algorithms and one detached Cursor as run.
     * Collision Check.asm:27-28,92-108,305-314 raw stores reach that cursor synchronously, including stores inside
     * an unfinished terrain leaf. Only origin-tagged terrain reads and StandOnObject:117-118's unowned a2 address
     * can stop natively. Host exceptions and Errors propagate; this entry has no caller callback or publication.
     */
    public Outcome runWithPrefix(Entry entry, CdObjectState object, Player player, Gates gates, Globals globals,
                                 CdObjectPool.Snapshot callCut) {
        Objects.requireNonNull(entry, "entry");
        if (entry != Entry.TOP_SOLID_OBJECT && entry != Entry.GET_OFF_OBJECT) {
            throw new IllegalArgumentException("Native prefixes support only TopSolidObject and GetOffObject");
        }
        Objects.requireNonNull(object, "object"); Objects.requireNonNull(player, "player");
        Objects.requireNonNull(gates, "gates"); Objects.requireNonNull(globals, "globals");
        Objects.requireNonNull(callCut, "call-cut capture");
        var c = new Cursor(entry, object, player, gates, globals, callCut);
        c.prefixReads = true;
        try {
            if (entry == Entry.TOP_SOLID_OBJECT) {
                c.objectByte(ByteField.SOLID_TYPE, 1); solidObject(c);
            } else {
                getOffObject(c); c.routineReturn();
            }
            return new Completed(c.finish());
        } catch (NativeStopReached stopped) {
            return new NativeStopped(c.prefix(), stopped.stop);
        }
    }

    private int roomInFrontWithPrefix(Cursor c, int angle) {
        CdPlayerMovement.ProbeResult probe;
        try {
            probe = probes.calcRoomInFrontWithPrefix(c.motion(), angle, c::probeAngleWrite);
        } catch (CdPlayerMovement.UnownedProbeRead missing) {
            if (missing.buffer() != CdPlayerMovement.AngleBuffer.PRIMARY) {
                throw new IllegalStateException("Player_CalcRoomInFront probes only primaryAngle");
            }
            throw terrainStop(StopSite.ROOM_PRIMARY, missing);
        }
        if (probe.primaryAngleWrite().isEmpty() || probe.secondaryAngleWrite().isEmpty()) {
            throw new IllegalStateException("Player_CalcRoomInFront stores d0 to both angle buffers");
        }
        // Already adopted at each raw write; do not replay ProbeResult's final angle writes.
        c.calls.add(new RoomInFront(angle, probe.distance(), probe.primaryAngleWrite(), probe.secondaryAngleWrite()));
        return probe.distance();
    }

    private int checkFloorWithPrefix(Cursor c) {
        CdPlayerMovement.ProbeResult probe;
        try {
            probe = probes.checkFloorWithPrefix(c.motion(), c::probeAngleWrite);
        } catch (CdPlayerMovement.UnownedProbeRead missing) {
            var site = switch (missing.buffer()) {
                case PRIMARY -> StopSite.FLOOR_PRIMARY;
                case SECONDARY -> StopSite.FLOOR_SECONDARY;
            };
            throw terrainStop(site, missing);
        }
        // Both leaves completed; only now is the aggregate Player_CheckFloor return known.
        c.calls.add(new FloorDistance(probe.distance(), probe.primaryAngleWrite(), probe.secondaryAngleWrite()));
        return probe.distance();
    }

    private int ceilingDistanceWithPrefix(Cursor c) {
        CdPlayerMovement.ProbeResult probe;
        try {
            probe = probes.getCeilDistWithPrefix(c.motion(), c::probeAngleWrite);
        } catch (CdPlayerMovement.UnownedProbeRead missing) {
            if (missing.buffer() != CdPlayerMovement.AngleBuffer.PRIMARY) {
                throw new IllegalStateException("Player_GetCeilDist probes only primaryAngle");
            }
            throw terrainStop(StopSite.CEILING_PRIMARY, missing);
        }
        if (probe.secondaryAngleWrite().isPresent()) {
            throw new IllegalStateException("Player_GetCeilDist never stores secondaryAngle");
        }
        c.calls.add(new CeilingDistance(probe.distance(), probe.primaryAngleWrite(), probe.secondaryAngleWrite()));
        return probe.distance();
    }

    /** Scope the existing exception to the reached StandOnObject dereference, never PushObject or host code. */
    private static void releaseStoodOn(Cursor c) {
        if (!c.prefixReads) { c.releaseStoodOn(c.stand); return; }
        try {
            c.releaseStoodOn(c.stand); // Stand0 is the real player alias; physical dynamic IDs need not be live.
        } catch (UnsupportedSolidObject missing) {
            throw new NativeStopReached(new NativeStop(StopSite.STAND_RELEASE,
                    new SlotAddress(missing.boundary(), missing.slotByte(), missing.routine()), missing));
        }
    }

    private static NativeStopReached terrainStop(StopSite site, CdPlayerMovement.UnownedProbeRead missing) {
        var read = missing.terrainRead();
        StopDetail detail = switch (read.detail()) {
            case CdPlayerTerrain.TerrainWord word -> new TerrainWord(word.address(), word.reason());
            case CdPlayerTerrain.CollisionId id -> new CollisionId(id.blockIndex());
        };
        return new NativeStopReached(new NativeStop(site, detail, read.originalException()));
    }

    /** Invocation-local unwind only. Neither the cursor nor this private signal is retained or returned. */
    private static final class NativeStopReached extends RuntimeException {
        final NativeStop stop;
        NativeStopReached(NativeStop stop) { this.stop = stop; }
    }
}
