package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerMath;

/**
 * Finite synchronous joins for the historical R4 object families. No loader, walker or lifetime.
 * Root must bind an actual programme and private invocation, validate full State/Environment at
 * every Guard cut, and invalidate operations on return/restore. Public values grant no authority.
 * No result is a command to perform an operation later. Native source pin:
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff (historical R4 Shared/Legacy Shared graphs).
 */
public interface CdR4ObjectPorts {
    enum Data {
        TONBO_MAP1, TONBO_MAP2, TONBO_ANIM,
        AMENBO_RETAINED_ANIM, AMENBO_MAP1, AMENBO_MAP2, AMENBO_ANIM1, AMENBO_ANIM2,
        TAGA_MAP1, TAGA_MAP2, TAGA_ANIM, TAGA_MISSILE_MAP,
        YAGO_MAP1, YAGO_MAP2, YAGO_ANIM1, YAGO_ANIM2,
        ANIMAL_MAP1, ANIMAL_MAP2, ANIMAL_ANIM,
        SPIKES_MAP, FALL_SPIKES_MAP, MACE_MAP, MACE_CHAIN1_MAP, MACE_CHAIN2_MAP,
        SPIKE_BALL_MAP, CIRCLE_SPIKE_BALL_MAP
    }
    enum Read {
        GOOD_FUTURE, TIME_ZONE, PROJECTOR_DESTROYED, ACT, WATER_Y, STATIC_WATER_Y,
        PLAYER_X, PLAYER_Y, WARPING, INVINCIBLE, PLAYER_ROUTINE, PLAYER_HURT_TIMER,
        PLAYER_Y_SPEED
    }
    enum Kind { STORE, DELETE, READ, POINTER, ANIMAL_TILE, SINE, ANIMATE, DRAW,
        DESPAWN_CURRENT, DESPAWN_ANCHOR, FLOOR, WALL_LEFT, WALL_RIGHT, SOLID,
        HURT_WITH_Y_REWIND, FM, TEST_SPAWN_POINTS }
    record AnimationOperand(Data table, int rawAddress) { }
    /** Constructible diagnostic data, accepted only by identity while producer.isCurrent(operation). */
    record Operation(Kind kind, int actor, int target, CdObjectPlacementRuntime.Snapshot cut,
                     CdObjectState output, Object operand) { }
    interface Guard {
        void before(CdR4ObjectScope producer, Operation operation);
        void after(CdR4ObjectScope producer, Operation operation);
    }
    int read(Read field);
    int pointer(Data field);
    /** Literal WORD read at the native byte-computed index; do not mask time_zone or invent a tile. */
    int animalTile(int byteOffset);
    CdPlayerMath.SineCosine sine(int angleByte);
    /** Existing animation algorithm, with reached stores retained if a selected read faults. */
    void animate(int wholeIndex, Data table, int rawTableAddress);
    void draw(int wholeIndex);
    /** Actual native despawn owner, including lazy time-warp and saved-bank reads/stores. */
    void despawn(int wholeIndex, Integer anchorWord);
    /** Actual CheckBlockDown / CheckBlockLeft/Right, including reached angle-global stores. */
    int floor(int wholeIndex);
    int wall(int wholeIndex, boolean right, int widthByte);
    /** Return the native nonzero SolidObject condition, preserving all actor-pair changes. */
    boolean solid(int wholeIndex);
    /** SUB.L signed y_speed<<8 from player.y, then native R4 HurtPlayer, synchronously. */
    void hurtWithYRewind(int wholeIndex);
    void fm(int soundWord);
    /** TestObject falls through into ExplosionSpawnPoints before ExplosionObject, unchanged ID06. */
    void testSpawnPoints(int wholeIndex);
}
