package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdPoints;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The pinball bumper on Metallic Madness act 3, object $1D of {@code r8/object_index_3.asm:32} ({@code BumperObject},
 * objects/r3/bumper_flipper.asm:3-117, assembled when {@code R8_VARIANT >= 9}, R8 Shared.asm:175-177). It is the routine
 * Collision Chaos hosts as {@code CczBumper} (there from {@code Level/Collision Chaos/Objects/Bumper and Flipper/Main.asm});
 * that class draws Collision Chaos's sheet, so this one takes its mappings and animation script from the stage file.
 *
 * <p>A $10 square, touch type $D7 (tile $4CE, priority 1). The subtype gives a slide range: bit 1 adds $40, bit 0 adds $20
 * (zero stands still); bit 7 slides along X instead of Y, bit 6 picks the first direction. Each touch counts down five
 * prizes (100 points and a 100-point sprite), plays sub-CPU command $7F on screen and throws the player away from the
 * bumper at -$700 along the angle from the player to the bumper, in the air with the push bit and jump flag cleared,
 * and the object runs animation 1. Not modelled: the per-object saved-flags counter ({@code object_states}+2, :104-110),
 * which no hosted object reads. The despawn is CheckObjectDespawn2 on the starting X (:116-117).
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzBumper extends MmzObject implements TouchResponseProvider, TouchResponseListener,
        ObjectVisualFacing.NonDirectional {
    public static final int ID = 0x1D;
    /** move.w #$7F,d0; jsr SubCpuCommand (objects/r3/bumper_flipper.asm:81-82). */
    public static final int BUMP_COMMAND = 0x7F;
    /** move.b #$D7,obj.collide_type (:26). */
    private static final int COLLIDE_TYPE = 0xD7;

    private int routine;
    private int baseX;               // obj.var_32
    private int range;               // obj.var_30
    private int steps;               // obj.var_2e
    private int velocity;            // obj.var_2a (long)
    private int prizes;              // obj.var_34
    private boolean touched;         // obj.collide_status
    private int mapFrame;
    private int flip;
    private int colType;
    private CdAnimator animator;

    /**
     * The stages whose ObjectIndex starts the bumper at $1D: R83x, which select {@code r8/object_index_3.asm}
     * ({@code R8_VARIANT >= 9}, R8 Shared.asm:114-122; the bumper is its entry 29, :32). The R81x binaries carry
     * the bumper's bytes too, but their index ({@code object_index_1.asm:32}) starts a null object at $1D.
     */
    static boolean indexedIn(com.openggf.game.soniccd.CdStagePayload payload) {
        return payload.stage().actNumber() == 3 && MmzObjectCatalogue.offset(payload, "bumperMappings") >= 0;
    }

    /** {@link #indexedIn} for the loaded stage. */
    static boolean indexedInLoadedStage() {
        var levels = com.openggf.game.GameServices.levelOrNull();
        return levels != null
                && levels.getCurrentLevel() instanceof com.openggf.game.soniccd.level.CdLevel level
                && indexedIn(level.payload());
    }

    public MmzBumper(ObjectSpawn spawn) {
        super(spawn, "Bumper");
        flip = spawn.renderFlags() & 3;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        main(player);
    }

    /** BumperObject_0_Routine0 (objects/r3/bumper_flipper.asm:17-50); the routine falls into Routine2. */
    private void init() {
        routine = 2;
        tileWord = MmzObjectArt.BUMPER_TILE;
        width = 0x10;
        yRadius = 0x10;
        priority = 1;
        colType = COLLIDE_TYPE;
        baseX = x;
        prizes = 5;
        int subtype = spawn.subtype();
        int d0 = 0;
        if ((subtype & 2) != 0) d0 += 0x40;
        if ((subtype & 1) != 0) d0 += 0x20;
        range = d0;
        steps = d0 >> 1;
        if (steps == 0) return;                                            // beq.s Routine2
        velocity = 0x10000;
        if ((subtype & 0x40) == 0) velocity = -velocity;
    }

    /** BumperObject_0_Routine2 (objects/r3/bumper_flipper.asm:52-117). */
    private void main(PlayableEntity player) {
        if (velocity != 0) {
            if ((spawn.subtype() & 0x80) != 0) addToX(velocity);           // tst.w obj.subtype; bmi.s .Move
            else addToY(velocity);
            steps = (steps - 1) & 0xFFFF;
            if (steps == 0) {
                steps = range;
                velocity = -velocity;
            }
        }
        if (touched) {
            touched = false;                                               // clr.b obj.collide_status
            if (prizes != 0) {
                prizes--;
                var game = services().gameState();
                if (game != null) game.addScore(0xA * 10);                 // moveq #$A,d0; bsr.w AddPoints
                var objects = services().objectManager();
                if (objects != null) objects.addDynamicObject(new CdPoints(new ObjectSpawn(x, y, 0x1C, 0x80, 0, false, 0)));
            }
            if (renderFlagOnScreen()) services().playMusic(BUMP_COMMAND);  // tst.b obj.sprite_flags; bpl .NoSound
            if (player instanceof AbstractPlayableSprite sprite) throwPlayer(sprite);
            if (animator == null) animator = mmzAnimator("bumperAnimations", 0);
            if (animator != null) animator.anim = 1;                       // move.b #1,obj.anim_id
        }
        if (animator == null) animator = mmzAnimator("bumperAnimations", 0);
        if (animator != null) {                                            // .Animate: AnimateObject
            animator.step();
            mapFrame = animator.mapFrame();
            flip = (spawn.renderFlags() ^ animator.flipBits()) & 3;
        }
    }

    /** :85-102: Atan2 of (bumper - player), SineCosine, speed $F900 (-$700) times each, {@code asr.l #8}. */
    private void throwPlayer(AbstractPlayableSprite sprite) {
        int d1 = (short) (x - sprite.getCentreX());
        int d2 = (short) (y - sprite.getCentreY());
        int angle = TrigLookupTable.calcAngle((short) d1, (short) d2) & 0xFF;
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);
        sprite.setXSpeed((short) ((cos * -0x700) >> 8));
        sprite.setYSpeed((short) ((sin * -0x700) >> 8));
        sprite.setAir(true);                                               // bset #1,obj.flags(a1)
        sprite.setPushing(false);                                          // bclr #4 / #5,obj.flags(a1)
        sprite.setJumping(false);                                          // clr.b obj.var_3c(a1)
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        touched = true;                                                    // loc_206C76 (r8/player_object_collide.asm:407-409): addq.b #1,collide_status
    }

    @Override public int getCollisionFlags() { return colType; }
    @Override public int getCollisionProperty() { return 0; }

    /** A {@code $C0}-class type only adds to the collision status (loc_206C76, r8/player_object_collide.asm:407-409). */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (getCollisionFlags() & 0xC0) == 0xC0; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        String key = MmzObjectArt.key(artProvider(), MmzObjectArt.BUMPER, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (flip & 1) != 0, (flip & 2) != 0);
    }

    public int prizes() { return prizes; }
    public int steps() { return steps; }
    public int velocity() { return velocity; }
    public int routine() { return routine; }
}
