package com.openggf.game.soniccd.objects.qqz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Kumo Kumo, object $23 ({@code KumoKumoObject}, objects/r5/kumo_kumo.asm:3-13): a spider badnik. It falls
 * to the floor, then hops and waits; when the player is inside a {@code [-$50, $50)} square it turns to face
 * the player (subtype 0 only), pauses 60 frames and spits a web ({@link ObjKumoWeb}); then it hops again.
 * Collision {@code $35}, {@code $36} once landed (both enemy). Subtype 1 uses the second sprite and animation
 * tables, a lower hop ({@code -$50000} against {@code -$80000}) and never looks for the player.
 * {@code DestroyInGoodFuture} runs first; CheckObjectDespawn runs on its X after every routine (:12).
 *
 * <p>Routines (off_20D866, :16-24): 0 init, which flows into 4; 2 hop (Y up 8, impulse in {@code oVar34});
 * 4 fall ({@code $4000} gravity) to the floor; 6 settle (collision {@code $36}, 180 frames); 8 watch
 * and animate; A pause 60 frames; C spit. Shipped {@code FixBugs = 0}.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjKumoKumo extends QqzBadnik {
    private int hopImpulse;        // oVar30
    private int fallVelocity;      // oVar34
    private int timer;             // oVar2A

    public ObjKumoKumo(ObjectSpawn spawn) {
        super(spawn, "Kumo Kumo");
    }

    @Override
    protected String sheetKind() {
        return spawn.subtype() == 0 ? QqzObjectArt.KUMO1 : QqzObjectArt.KUMO2;
    }

    @Override
    protected void run(PlayableEntity player) {
        if (standDownInBossAct()) return;
        if (destroyInGoodFuture()) return;
        switch (routine) {
            case 0 -> init();
            case 2 -> hop();
            case 4 -> fall();
            case 6 -> settle();
            case 8 -> watch(player);
            case 0xA -> { if (--timer == 0) routine += 2; }
            case 0xC -> spit();
            default -> { }
        }
    }

    /** KumoKumoObject_0_Routine0 (objects/r5/kumo_kumo.asm:27-58). */
    private void init() {
        routine += 2;
        priority = 3;
        tileWord = QqzObjectArt.KUMO_TILE;
        xRadius = 0x18;
        width = 0x18;
        yRadius = 0x18;
        colType = 0x35;
        boolean first = spawn.subtype() == 0;
        animColumn = first ? "kumoAnimations1" : "kumoAnimations2";
        hopImpulse = first ? -0x80000 : -0x50000;
        if (sub2() != 0) {
            spriteFlip |= 1;                                 // bset #0,obj.sprite_flags(a0)
            statusFlip |= 1;                                 // bset #0,obj.flags(a0)
        }
        initialised = true;
        routine += 2;                                        // loc_20D8E2: addq.b #2; bra.s Routine4
        fall();
    }

    /** KumoKumoObject_0_Routine2 (objects/r5/kumo_kumo.asm:60-65). */
    private void hop() {
        routine += 2;
        mapFrame = 0;
        y = (y - 8) & 0xFFFF;
        fallVelocity = hopImpulse;
        fall();
    }

    /** KumoKumoObject_0_Routine4 (objects/r5/kumo_kumo.asm:66-79). */
    private void fall() {
        fallVelocity += 0x4000;
        addToY(fallVelocity);
        int d1 = floorDistance();
        if (d1 >= 0) return;                                 // tst.w d1; bpl.s
        y = (y + d1) & 0xFFFF;
        routine += 2;
        mapFrame = 1;
        y = (y + 8) & 0xFFFF;
    }

    /** KumoKumoObject_0_Routine6 (objects/r5/kumo_kumo.asm:83-87). */
    private void settle() {
        routine += 2;
        colType = 0x36;
        timer = 0xB4;
    }

    /** KumoKumoObject_0_Routine8 (objects/r5/kumo_kumo.asm:88-110). */
    private void watch(PlayableEntity player) {
        if (spawn.subtype() == 0 && player != null && lookAtPlayer(player)) {
            routine += 2;
            mapFrame = 1;
            timer = 0x3C;
            return;
        }
        if (--timer == 0) routine -= 6;                      // loc_20D95C
        animate();                                           // loc_20D966: AnimateObject over oVar2C's table
    }

    /**
     * sub_20D970 (objects/r5/kumo_kumo.asm:113-137): d6 is set when the player is within {@code [-$50, $50)} on both
     * axes and the spider faces away; it then flips to face the player.
     */
    private boolean lookAtPlayer(PlayableEntity player) {
        if (!inWindow(centreY(player) - y, 0x50, 0xA0)) return false;
        int dx = (centreX(player) - x) & 0xFFFF;
        if (!inWindow(dx, 0x50, 0xA0)) return false;
        boolean playerLeft = (short) dx < 0;                 // tst.w d0; smi d0
        boolean bitClear = (spriteFlip & 1) == 0;            // btst #0,obj.sprite_flags(a0); seq d1
        if (playerLeft != bitClear) return false;            // eor.b d1,d0; bne.s locret_20D9B6
        flipHorizontally();
        return true;
    }

    /** KumoKumoObject_0_RoutineC (objects/r5/kumo_kumo.asm:151-183). */
    private void spit() {
        var objects = services().objectManager();
        if (objects != null) {
            boolean facingFlag = (spriteFlip & 1) != 0;
            int dx = facingFlag ? -0x18 : 0x18;
            int velocity = facingFlag ? -0x30000 : 0x30000;
            ObjKumoWeb web = new ObjKumoWeb((x + dx) & 0xFFFF, y, spriteFlip, tileWord, velocity);
            objects.addDynamicObject(web);                   // jsr SpawnObject
            if (!web.isDestroyed() && onScreen()) services().playSfx(0xA0);
        }
        routine -= 0xA;                                      // loc_20DA44: subi.b #$A,obj.routine(a0)
    }
}
