package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Amy Rose in the Act 1 Present intro, object {@code $32} ({@code AmyRoseObject}, objects/r3/intro.asm:248-400; the USA build
 * assembles this recovered copy, not Level/Collision Chaos/Objects/Amy Rose/Main.asm). She waits (routine 0) until the player
 * passes her X, then runs (routine 2) along the stage away from him at up to $200 (acceleration $20, facing him), standing
 * still on the left edge ($80) while he approaches from the right. From X $3C0 she calls Metal Sonic ({@code $31} at
 * ($500,$3E8), linked both ways); once he has arrived ({@link CczMetalSonic#arrived}) she plays sub-CPU command {@code $7D}
 * and is carried ({@code AmyRoseCaptured}, routine 4) beside him ({@code $8} ahead, $4 below). Every 32 frames she emits a
 * heart ({@link Heart}) above her leading shoulder. She and the intro end when {@code amy_captured} is set or the player passes X $4C0.
 *
 * <p>Not modelled: {@code AddGfxQueue} of PLC {@code $B} (art is resident, see {@link CczObjectArt}), Amy's palette load
 * ({@code AmyRoseLoadPalette}, objects/r3/intro.asm:478-496: sixteen words into palette line 1) and Time Attack, which need a
 * palette-write and a mode service this lane does not own.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class CczAmyRose extends CczObject implements ObjectVisualFacing.Source {
    /** move.w #$7D,d0; jsr SubCpuCommand (objects/r3/intro.asm:312-313). */
    public static final int CAPTURE_COMMAND = 0x7D;

    private int xVel;
    private int anim;
    private int heartAccumulator;    // var_3b
    private CczMetalSonic metal;     // var_34
    private CczMetalSonic.IntroAnimator introAnimator;

    public CczAmyRose(ObjectSpawn spawn) {
        super(spawn, "Amy Rose");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && CczStage.standDownFromIntro(this, services())) return;
        var level = cdLevel();
        if (level != null && CczIntroState.of(level).amyCaptured()) {      // tst.b amy_captured; bne MetalSonicCaptureAmy
            CczIntroState.of(level).restoreStagePalette(services());
            setDestroyed(true);
            return;
        }
        switch (routine) {
            case 0 -> init(player);
            case 2 -> run(player);
            default -> captured();
        }
        if (isDestroyed()) return;
        if (player instanceof AbstractPlayableSprite sprite && (sprite.getCentreX() & 0xFFFF) >= 0x4C0) {
            if (level != null) {
                CczIntroState.of(level).captureAmy();                      // cmpi.w #$4C0,8(a1); bcc MetalSonicCaptureAmy
                CczIntroState.of(level).restoreStagePalette(services());
            }
            setDestroyed(true);
        }
    }

    /** AmyRoseInit (:271-296). */
    private void init(PlayableEntity player) {
        priority = 1;
        tileWord = CczObjectArt.AMY_TILE;
        var level = cdLevel();
        if (level != null) CczIntroState.of(level).loadAmyPalette(services(), level, provider());   // AmyRoseLoadPalette
        facePlayer(player);
        if (player instanceof AbstractPlayableSprite sprite && (sprite.getCentreX() & 0xFFFF) >= (x & 0xFFFF)) {
            routine += 2;                                                  // cmp.w obj.x(a0),d0; bcs.s .Animate
        }
        introAnimate();
    }

    /** AmyRoseRun (:303-368). */
    private void run(PlayableEntity player) {
        if (metal != null && !metal.isDestroyed() && metal.arrived()) {    // tst.b $3D(a2)
            routine = 4;
            services().playMusic(CAPTURE_COMMAND);
            animateAndHeart();
            return;
        }
        facePlayer(player);
        boolean facingRight = (statusFlip & 1) != 0;
        if (facingRight && (x & 0xFFFF) < 0x80) {                          // btst #0,obj.flags(a0); cmpi.w #$80,obj.x(a0)
            anim = 0;
            xVel = 0;
            animateAndHeart();
            return;
        }
        if ((x & 0xFFFF) < 0x3C0) {
            int d0 = facingRight ? -0x20 : 0x20;
            d0 = (short) (d0 + xVel);
            int limit = d0 < 0 ? -0x200 : 0x200;
            if (Math.abs(d0) >= 0x200) d0 = limit;                         // cmpi.w #$200,d1; bcs .SetVelocity
            xVel = d0;
            moveX(xVel);
            anim = 1;
            if ((x & 0xFFFF) < 0x3C0) {
                animateAndHeart();
                return;
            }
        }
        anim = 0;                                                          // .SpawnMetalSonic
        if (metal == null) spawnMetalSonic();
        animateAndHeart();
    }

    /** :.SpawnMetalSonic (:337-346): SpawnObject, id $31 at ($500,$3E8), linked both ways. */
    private void spawnMetalSonic() {
        var objects = services().objectManager();
        if (objects == null) return;
        metal = new CczMetalSonic(new ObjectSpawn(0x500, 0x3E8, 0x31, 0, 0, false, 0));
        metal.linkAmy(this);
        objects.addDynamicObject(metal);
    }

    /** AmyRoseCaptured (:377-399). */
    private void captured() {
        if (metal == null || metal.isDestroyed()) {                        // cmpi.b #$31,obj.id(a1); bne .Delete
            setDestroyed(true);
            return;
        }
        int d0 = 8;
        setFacingRight(true);
        if ((metal.spriteFlip() & 1) != 0) {                               // btst #0,obj.flags(a1)
            d0 = -d0;
            setFacingRight(false);
        }
        x = (metal.xPosition() + d0) & 0xFFFF;
        y = (metal.yPosition() + 4) & 0xFFFF;
        anim = 2;
        introAnimate();
    }

    /** IntroFacePlayer (:405-415). */
    private void facePlayer(PlayableEntity player) {
        setFacingRight(false);
        if (player instanceof AbstractPlayableSprite sprite && (x & 0xFFFF) >= (sprite.getCentreX() & 0xFFFF)) {
            setFacingRight(true);
        }
    }

    /** AmyRoseAnimateAndSpawnHeart (:370-376) and AmyRoseSpawnHeart (:506-531). */
    private void animateAndHeart() {
        introAnimate();
        heartAccumulator += 8;                                             // addq.b #8,obj.var_3b; bcc.s .End
        if (heartAccumulator < 0x100) return;
        heartAccumulator &= 0xFF;
        var objects = services().objectManager();
        if (objects == null) return;
        int dx = (statusFlip & 1) != 0 ? -0xE : 0xC;
        objects.addDynamicObject(new Heart(new ObjectSpawn((x + dx) & 0xFFFF, (y - 0xC) & 0xFFFF, 0x33, 0, 0, false, 0)));
    }

    private void introAnimate() {
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        if (introAnimator == null) {
            int table = CczObjectCatalogue.offset(provider.payload(), "amyRoseAnimations");
            if (table < 0) return;
            introAnimator = new CczMetalSonic.IntroAnimator(provider.image(), table);
        }
        introAnimator.step(anim, statusFlip);
        mapFrame = introAnimator.mapFrame;
        spriteFlip = introAnimator.spriteFlip;
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(CczObjectArt.AMY, mapFrame);
    }

    public int xVelocity() { return xVel; }

    /**
     * Amy's heart, object {@code $33} ({@code HeartObject}, objects/r3/intro.asm:758-820): rises at $60 (8.8) swaying by
     * {@code sin(3 * age) / 4}, changes frame at age $14 and $6E (where it stops and the sway ends) and deletes itself at $78;
     * CheckObjectDespawn applies. Tile {@code $35E}, priority 3, first frame $A.
     */
    @CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
    public static final class Heart extends CczObject implements ObjectVisualFacing.NonDirectional {
        private int age;             // var_3a
        private int xVel;
        private int yVel;
        private boolean stopped;     // var_3c

        public Heart(ObjectSpawn spawn) {
            super(spawn, "Amy heart");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (routine == 0) {                                            // AmyHeartInit (:774-781), falls into Main
                if (CczStage.standDownFromIntro(this, services())) return;
                routine = 2;
                tileWord = CczObjectArt.HEART_TILE;
                priority = 3;
                yVel = -0x60;
                mapFrame = 0xA;
            }
            if (!stopped) {
                int angle = (age * 3) & 0xFF;                              // add.b d0,d0; add.b var_3a,d0
                xVel = TrigLookupTable.sinHex(angle) >> 2;                 // asr.w #2,d0
            }
            moveX(xVel);
            moveY(yVel);
            age = (age + 1) & 0xFF;
            if (age == 0x14) mapFrame++;
            if (age == 0x6E) {
                mapFrame++;
                yVel = 0;
                xVel = 0;
                stopped = true;
            }
            if (age == 0x78) setDestroyed(true);
        }

        @Override protected boolean despawnsByCheckObjDespawn() { return true; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(CczObjectArt.HEART, mapFrame);
        }

        public int age() { return age; }
    }
}
