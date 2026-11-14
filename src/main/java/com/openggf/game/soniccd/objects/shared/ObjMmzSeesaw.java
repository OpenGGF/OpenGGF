package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/**
 * Metallic Madness's seesaw, object $35 in the R8 indexes ({@code SeesawObject}, objects/r8/seesaw.asm:3-289; Wacky Workbench's
 * {@code SeesawObject} is another routine, {@link ObjWwzSeesaw}). The placed object is the body (sprite layer 5, frame 0);
 * routine 0 (:32-82) spawns two copies of the ID after itself ({@code SpawnObjectAfter}): a ball ({@code oVar3E} bit 0, layer 6,
 * frame 5) on the body and a platform ({@code oVar3E} bit 1, layer 3, frame 8, $2C above it, $50 wide, $18 high). Each
 * copy runs {@code SeesawObject_0_Routine2} (:88-154) by its role bit:
 * <ul>
 *   <li><b>platform</b> (:224-262): full-solid; once the player has stood on it (a {@code SolidObject} top contact sets bit 5 of
 *       the body, :224-239) it rides the body's frame - its Y is the placed Y plus {@code word_20B850}[frame] and its rider
 *       velocity {@code byte_20B84A}[frame] times $100 ({@code sub_20B834}, :265-274) - and calls {@code SolidObject}
 *       once more after moving, setting bit 2 of the body on a top contact;</li>
 *   <li><b>body</b> (:96-153): idle until bit 5; then steps its frame 1, 2, 3, 4 (every 6 frames after the first), rocks
 *       through {@code byte_20B712} (3, 2, 1, 2 every 11 frames) while the player is off the platform and creeps to frame 4
 *       while on it;</li>
 *   <li><b>ball</b> (:167-221): from bit 5 launches upward ({@code move.w #object_pool,obj.y_speed}: $D000, -$3000), gains $100 a
 *       frame (the Y moves by y_speed &lt;&lt; 6 in 16.16), turns hurtful ($A4, layer 4) at the apex and lands within 8 pixels of
 *       the placed Y, freezing itself and the body at frame 4 (bit 4).</li>
 * </ul>
 * Every copy deletes itself outside {@code chunk(x) - chunk(scroll_fg_x - $80) <= $280} (:9-21), on-screen or not.
 *
 * <p>Not modelled: {@code time_stop} (every motion routine returns while it is nonzero, :145-147, :168-169, :225-226); the engine
 * has no such flag yet.
 */
public final class ObjMmzSeesaw extends CdObject
        implements ReevaluatingSolidObject, TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private enum Role { BODY, BALL, PLATFORM }

    /** {@code oVar3E} bits (:42, :61, :77 and the routines). */
    private static final int BIT_PLATFORM_STOOD = 0x04;
    private static final int BIT_LANDED = 0x10;
    private static final int BIT_ACTIVE = 0x20;
    private static final int BIT_BODY_STEPPED = 0x40;
    private static final int BIT_BODY_STARTED = 0x80;
    private static final int[] ROCK_FRAMES = {3, 2, 1, 2};                 // byte_20B712 (:159-164)
    private static final int[] RIDER_VELOCITY = {0, 8, 0x10, 8, 0, 0};     // byte_20B84A (:276-282)
    private static final int[] PLATFORM_OFFSET = {-0x2C, -0x1C, -0xC, 0xC, 0x1C};   // word_20B850 (:284-289)

    private final Role role;
    private final ObjMmzSeesaw body;
    private final int originY;                    // oVar36
    private int routine;
    private int bits;                             // oVar3E
    private int var30;
    private int var31;
    private int mapFrame;
    private int ySpeed;
    private int ySub;
    private int colType;
    private boolean initialised;

    public ObjMmzSeesaw(ObjectSpawn spawn) {
        super(spawn, "Seesaw");
        this.role = Role.BODY;
        this.body = null;
        this.originY = spawn.y();
    }

    private ObjMmzSeesaw(ObjMmzSeesaw body, Role role, int x, int y, int originY) {
        super(new ObjectSpawn(x, y, body.getSpawn().objectId(), 0, 0, false, 0), "Seesaw");
        this.role = role;
        this.body = body;
        this.originY = originY;
        this.routine = 2;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            init();
            return;
        }
        if ((bits & BIT_LANDED) != 0) return;                           // btst #4,obj.var_3e(a0); bne locret (:89-90)
        switch (role) {
            case BALL -> ball();
            case PLATFORM -> platform(player);
            default -> body();
        }
    }

    /** SeesawObject_0_Routine0 (:32-82). */
    private void init() {
        var level = cdLevel();
        var objects = services().objectManager();
        if (level == null || objects == null) return;
        routine = 2;
        priority = 5;
        tileWord = SharedObjectCatalogue.offset(level.payload(), "mmzSeesawTile");
        mapFrame = 0;
        width = 0x28;
        yRadius = 0x38;
        var30 = 1;
        bits = 0;
        ObjMmzSeesaw ball = new ObjMmzSeesaw(this, Role.BALL, x, y, y);                    // :45-64
        ball.priority = 6;
        ball.tileWord = SharedObjectCatalogue.offset(level.payload(), "mmzSeesawBallTile");
        ball.mapFrame = 5;
        ball.width = 0x10;
        ball.yRadius = 0x10;
        ball.bits = 1;
        objects.addDynamicObjectAfterCurrent(ball);
        ObjMmzSeesaw platform = new ObjMmzSeesaw(this, Role.PLATFORM, x, y - 0x2C, y);     // :66-82
        platform.priority = 3;
        platform.tileWord = tileWord;
        platform.mapFrame = 8;
        platform.width = 0x28;
        platform.yRadius = 0xC;
        platform.bits = 2;
        objects.addDynamicObjectAfterCurrent(platform);
        initialised = true;
    }

    // ---- Body (:96-153) ---------------------------------------------------------------------------

    private void body() {
        if ((bits & BIT_ACTIVE) == 0) return;                           // btst #5; beq locret_20B710
        ySpeed = 0;
        if ((bits & BIT_BODY_STARTED) == 0) {                           // loc_20B67x: subq.b #1,obj.var_30
            boolean borrow = var30 == 0;
            var30 = (var30 - 1) & 0xFF;
            if (!borrow) return;                                        // bcc.w locret
            var31 = 5;
            bits |= BIT_BODY_STARTED;
            mapFrame = (mapFrame + 1) & 0xFF;
            return;
        }
        if ((bits & BIT_BODY_STEPPED) == 0) {                           // loc_20B68C
            boolean borrow = var31 == 0;
            var31 = (var31 - 1) & 0xFF;
            if (!borrow) return;
            mapFrame = (mapFrame + 1) & 0xFF;
            var31 = 5;
            if ((byte) mapFrame < 4) return;                            // cmpi.b #4,obj.sprite_frame(a0); blt
            bits |= BIT_BODY_STEPPED;
            var30 = 0;
            var31 = 0xA;
            return;
        }
        if ((bits & BIT_PLATFORM_STOOD) != 0) {                         // loc_20B6C6, then loc_20B6FE
            if (mapFrame == 4) return;
            mapFrame = (mapFrame + 1) & 0xFF;
            var31 = 0xA;
            return;
        }
        boolean borrow = var31 == 0;
        var31 = (var31 - 1) & 0xFF;
        if (!borrow) return;                                            // subq.b #1,obj.var_31(a0); bcc.s locret
        var30 = (var30 + 1) & 0xFF;                                     // addq.b #1,obj.var_30(a0)
        int index = (byte) var30;
        if (index == 4) {
            var30 = 0;
            index = 0;
        }
        mapFrame = ROCK_FRAMES[index];                                  // loc_20B6EE
        var31 = 0xA;
    }

    // ---- Ball (:167-221) --------------------------------------------------------------------------

    private void ball() {
        if ((body.bits & BIT_ACTIVE) != 0) {                            // btst #5,obj.var_3e(a1)
            if ((bits & BIT_BODY_STARTED) == 0) {
                ySpeed = (short) 0xD000;                                // move.w #object_pool,obj.y_speed(a0)
                bits |= BIT_BODY_STARTED;
            } else {
                ySpeed = (short) (ySpeed + 0x100);                      // loc_20B742
                boolean turn = ySpeed < 0;
                if (!turn) {
                    int distance = (y - originY) & 0xFFFF;              // move.w obj.y(a0),d1; sub.w obj.var_36(a0),d1
                    if (distance <= 8) {                                // cmpi.w #8,d1; bhi.s loc_20B784
                        y = (originY + 4) & 0xFFFF;
                        body.bits |= BIT_LANDED;
                        bits |= BIT_LANDED;
                        body.mapFrame = 4;
                        ySpeed = 0;
                        moveBall();
                        return;
                    }
                }
                if (ySpeed == 0) {                                      // loc_20B784
                    mapFrame++;
                    colType = 0xA4;
                    priority = 4;
                } else if (ySpeed == -0x2000) {                         // loc_20B79E
                    mapFrame++;
                }
            }
        }
        moveBall();
    }

    /** loc_20B7AA: {@code oY += y_speed << 6} as a 16.16 long. */
    private void moveBall() {
        int position = ((y & 0xFFFF) << 16 | ySub) + (ySpeed << 6);
        y = position >>> 16;
        ySub = position & 0xFFFF;
    }

    // ---- Platform (:224-262) ----------------------------------------------------------------------

    private void platform(PlayableEntity player) {
        if ((body.bits & BIT_ACTIVE) != 0 || solidObject(ReevaluatingSolidObject.Entry.SOLID)) {   // loc_20B7B8 / loc_20B7E2
            body.bits |= BIT_ACTIVE;
        }
        y = (originY + PLATFORM_OFFSET[(byte) body.mapFrame]) & 0xFFFF;              // loc_20B7EC
        ySpeed = RIDER_VELOCITY[body.mapFrame & 0xFF] << 8;                          // sub_20B834
        body.bits &= ~BIT_PLATFORM_STOOD;
        if (solidObject(ReevaluatingSolidObject.Entry.SOLID)) body.bits |= BIT_PLATFORM_STOOD;
    }

    @Override public int solidYVelocity() { return role == Role.PLATFORM ? (short) ySpeed : 0; }

    // ---- touch, despawn, draw ---------------------------------------------------------------------

    @Override public int getCollisionFlags() { return role == Role.BALL ? colType : 0; }
    @Override public int getCollisionProperty() { return 0; }

    /** SeesawObject's own despawn (:9-21): chunk distance above $280, on screen or not. */
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        int chunk = x & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x280;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 && !initialised) return;
        boolean flipped = role == Role.BODY && hFlip();
        drawFrame(SharedObjectArt.MMZ_SEESAW, mapFrame, flipped, false);
    }
}
