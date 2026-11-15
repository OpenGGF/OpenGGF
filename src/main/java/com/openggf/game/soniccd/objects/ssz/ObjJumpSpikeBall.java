package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;

/**
 * Jump spike ball, object $26 ({@code JumpSpikeBallObject}, objects/r7/jump_spike_ball.asm:3-134), two objects in
 * one routine selected by {@code oSubtype2}.
 *
 * <p>The placed launcher (secondary subtype 0, :4-33) draws {@code SpikeLaunchSprites} frame = its subtype
 * (tile $38E, priority 1) and every $96 frames spawns a ball: the same ID and subtype, secondary subtype $FF,
 * at its position ({@code SpawnObject}, :19-30). It ends in {@code CheckObjectDespawn}.
 *
 * <p>The ball (secondary subtype nonzero, :37-129; tile $386, priority 4, collision type $B5) moves along one
 * axis, Y for subtypes 0 and 1 and X for 2 and 3: routine 2 creeps $8000 (16.16) per frame for $20 frames away
 * from the launcher (toward negative for subtype bit 0 clear), routine 4 waits $1E frames, routine 6 sweeps at
 * {@code +-$60000} with an acceleration of {@code -+$4000} per frame until the axis coordinate has crossed back
 * over its starting value, then deletes. It is drawn every frame and never range-checked.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjJumpSpikeBall extends SszObject implements ObjectVisualFacing.NonDirectional, TouchResponseProvider {
    /** oColType of the ball (:58): category $80 (hurt), size $35. */
    private static final int BALL_COLLISION = 0xB5;
    private static final int LAUNCH_PERIOD = 0x96;

    private final boolean ball;
    private int timer;
    /** oVar30 and oVar34: the ball's axis velocity and acceleration, 16.16. */
    private int velocity;
    private int acceleration;
    /** oVar2A, oVar2C, oVar3F: the axis (true: X), the starting coordinate and the "keep going while negative" sign. */
    private boolean axisIsX;
    private int start;
    private boolean sweepNegative;
    private int axisSub;

    /** A launcher placed in the layout. */
    public ObjJumpSpikeBall(ObjectSpawn spawn) {
        this(spawn, false);
    }

    private ObjJumpSpikeBall(ObjectSpawn spawn, boolean ball) {
        super(spawn, "Jump spike ball");
        this.ball = ball;
    }

    @Override protected String sheetKind() { return ball ? SszObjectArt.JUMP_SPIKE_BALL : SszObjectArt.SPIKE_LAUNCH; }

    @Override protected boolean despawnsByCheckObjDespawn() { return !ball; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (ball) updateBall(); else updateLauncher();
    }

    /** The launcher (:4-33). */
    private void updateLauncher() {
        if (routine == 0) {
            routine += 2;
            priority = 1;
            tileWord = SszObjectArt.SPIKE_LAUNCH_TILE;
            mapFrame = spawn.subtype();
            timer = LAUNCH_PERIOD;                           // move.w #$96,obj.var_2a(a0)
            width = 0x10;
            yRadius = 0x10;
            initialised = true;
        }
        if (--timer != 0) return;                            // subq.w #1,obj.var_2a(a0); bne.s
        timer = LAUNCH_PERIOD;
        var objects = services().objectManager();
        if (objects == null) return;
        objects.addDynamicObject(new ObjJumpSpikeBall(                 // jsr SpawnObject; same id, subtype, position
                new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), 0, false, 0), true));
    }

    /** The ball (:37-129). */
    private void updateBall() {
        switch (routine) {
            case 0 -> { init(); creep(); }                   // falls through into Routine2
            case 2 -> creep();
            case 4 -> {                                      // Routine4 (:98-114)
                if (--timer == 0) {
                    routine += 2;
                    boolean up = (spawn.subtype() & 1) != 0;
                    velocity = up ? 0x60000 : -0x60000;
                    acceleration = up ? -0x4000 : 0x4000;
                }
            }
            case 6 -> sweep();
            default -> { }
        }
    }

    /** JumpSpikeBallObject_0_Routine0 (:54-82). */
    private void init() {
        routine += 2;
        priority = 4;
        tileWord = SszObjectArt.JUMP_SPIKE_BALL_TILE;
        width = 0x10;
        yRadius = 0x10;
        timer = 0x20;
        boolean positive = (spawn.subtype() & 1) != 0;       // btst #0,obj.subtype(a0)
        sweepNegative = !positive;                           // seq obj.var_3f(a0)
        velocity = positive ? 0x8000 : -0x8000;
        axisIsX = spawn.subtype() >= 2;                      // cmpi.b #2,obj.subtype(a0); bge
        start = axisIsX ? x : y;
        initialised = true;
    }

    /** JumpSpikeBallObject_0_Routine2 (:84-94). */
    private void creep() {
        addToAxis(velocity);
        if (--timer == 0) {
            routine += 2;
            timer = 0x1E;
        }
    }

    /** JumpSpikeBallObject_0_Routine6 (:118-129). */
    private void sweep() {
        velocity += acceleration;                            // add.l d0,obj.var_30(a0)
        addToAxis(velocity);
        boolean negative = (short) ((axisIsX ? x : y) - start) < 0;     // sub.w obj.var_2c(a0),d0; smi d0
        if (negative != sweepNegative) setDestroyed(true);   // cmp.b obj.var_3f(a0),d0; beq.s locret; DeleteObject
    }

    /** {@code add.l d0,(a1)}: the 16.16 coordinate of the object's X or Y. */
    private void addToAxis(int amount) {
        int coordinate = axisIsX ? x : y;
        long position = (((long) (coordinate & 0xFFFF) << 16) | axisSub) + amount;
        axisSub = (int) position & 0xFFFF;
        int integer = (int) (position >>> 16) & 0xFFFF;
        if (axisIsX) x = integer; else y = integer;
    }

    @Override public int getCollisionFlags() { return ball ? BALL_COLLISION : 0; }
    @Override public int getCollisionProperty() { return 0; }
}
