package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Metal Sonic in the Act 1 Present intro, object $31 ({@code MetalSonicObject}, objects/r3/intro.asm:13-210).
 * Amy Rose's lane spawns it and links itself through {@link #linkAmy}; it waits $3C frames, flies in from the right to
 * $20 right of Amy (or X $3E0 without her), hovers (bob around the base Y with a sine of {@code var_3a}),
 * rises and falls once ({@code var_3b} frames of motion), watches the player, then flies off to the right. It
 * ends the scene ({@code MetalSonicCaptureAmy}: {@code amy_captured}, palette reload, delete) once the player
 * passes X $4C0 or it passes X $528. The exhaust child ($34) is spawned on arrival.
 *
 * <p>Its animation uses the intro's own animator, {@code IntroAnimateObject} (:431-476): word offsets, two-byte
 * (frame/flip, delay) records, restart on any negative record.
 *
 * <p>Not modelled: {@code MetalSonicBob} adds the long {@code var_32} to the sine term, and {@code var_34}
 * (the low word of that long) holds Amy's slot address, which perturbs only the sub-pixel of Y
 * ({@code FixBugs = 0} quirk; no slot addresses are modelled).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczMetalSonic extends CczObject implements ObjectVisualFacing.Source {
    /** move.w #FM_CA,d0 (objects/r3/intro.asm:58). */
    public static final int ENTRY_SOUND = 0xCA;

    private int timer = 0x3C;            // var_3a: wait timer, then bob phase
    private int yTimer;                  // var_3b
    private int acceleration;            // var_3e
    private int playerTimer;             // var_30
    private int baseY;                   // var_32 (integer part)
    private int xVel;
    private int yVel;
    private int anim;
    private boolean arrived;             // var_3d
    private CczObject amy;               // var_34, Amy's slot
    private IntroAnimator introAnimator;
    private CczMetalSonicExhaust exhaust;

    public CczMetalSonic(ObjectSpawn spawn) {
        super(spawn, "Metal Sonic");
    }

    /** Amy's lane links its object here ({@code move.w a0,obj.var_34(a1)}). */
    public void linkAmy(CczObject amyObject) {
        this.amy = amyObject;
    }

    /** {@code obj.var_3d}: set on arrival; Amy reads it (:303-312). */
    public boolean arrived() { return arrived; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && CczStage.standDownFromIntro(this, services())) return;
        switch (routine) {
            case 0 -> init();
            case 2 -> wait(player);
            case 4 -> approach();
            case 6 -> hover(player);
            case 8 -> capture(player);
            default -> escape();
        }
        if (isDestroyed()) return;
        // bsr.w IntroLoadPlayer; cmpi.w #$4C0,obj.x(a1); bcc MetalSonicCaptureAmy (:204-210)
        if (player instanceof AbstractPlayableSprite sprite && (sprite.getCentreX() & 0xFFFF) >= 0x4C0) captureAmy();
    }

    /** MetalSonicInit (:35-44); the routine falls through into MetalSonicWait. */
    private void init() {
        routine = 2;
        tileWord = CczObjectArt.METAL_SONIC_TILE;
        priority = 2;
        mapFrame = 0xE;
        yRadius = 0x20;
        timer = 0x3C;
        colType = 0;
        wait(null);
    }

    /** MetalSonicWait (:46-61). */
    private void wait(PlayableEntity player) {
        if (timer != 0) {
            timer = (timer - 1) & 0xFF;
            if (timer != 0) return;
        }
        setFacingRight(true);                                              // IntroFaceRight
        acceleration = -0x10;
        routine = 4;
        if (renderFlagOnScreen()) services().playSfx(ENTRY_SOUND);         // btst #7,obj.sprite_flags; FM_CA
    }

    /** MetalSonicApproach (:66-99). */
    private void approach() {
        moveX(xVel);
        int v = (short) (acceleration + xVel);
        if (v <= -0x300) v = -0x300;                                       // cmpi.w #-$300,d0; bgt
        xVel = v;
        int target = amy != null ? ((amy.xPosition() + 0x20) & 0xFFFF) : 0x3E0;
        if ((x & 0xFFFF) <= target) {                                      // cmp.w obj.x(a0),d0; bcs .Animate: flying on while target < X
            xVel = 0;
            acceleration = 0;
            arrived = true;                                                // st obj.var_3d
            anim = 2;
            spawnExhaust();
            colType = 0;
            baseY = y;
            routine = 6;
        }
        introAnimate();
    }

    /** MetalSonicHover (:101-119). */
    private void hover(PlayableEntity player) {
        facePlayer(player);
        bob();
        timer = (timer + 4) & 0xFF;                                        // addq.b #4,var_3a
        if (timer >= 4) {                                                  // bcc (no carry)
            introAnimate();
            return;
        }
        routine = 8;
        yVel = -0x2C0;
        acceleration = 0xB;
        yTimer = 0x40;
        playerTimer = 0x50;
        introAnimate();
    }

    /** MetalSonicCapture (:135-172). */
    private void capture(PlayableEntity player) {
        facePlayer(player);
        if (yTimer != 0) {
            moveX(xVel);
            moveY(yVel);
            yVel = (short) (yVel + acceleration);
            yTimer = (yTimer - 1) & 0xFF;
            if (yTimer != 0) {
                introAnimate();
                return;
            }
            acceleration = 0;
            yVel = 0;
            baseY = y;
        }
        bob();
        timer = (timer + 4) & 0xFF;
        int px = player instanceof AbstractPlayableSprite sprite ? sprite.getCentreX() & 0xFFFF : 0;
        // sub.w obj.x(a1),d0; bcs .NearPlayer; cmpi.b #$A0,d0; bcc .Animate (:151-158): the compare is on the low byte.
        boolean near = (x & 0xFFFF) < px || ((x - px) & 0xFF) < 0xA0;
        if (!near) {
            introAnimate();
            return;
        }
        playerTimer = (playerTimer - 1) & 0xFF;
        if (playerTimer == 0) {
            setFacingRight(false);                                         // IntroFaceLeft
            xVel = 0;
            acceleration = 0x60;
            routine = 10;
        }
        introAnimate();
    }

    /** MetalSonicEscape (:185-202). */
    private void escape() {
        moveX(xVel);
        int v = (short) (acceleration + xVel);
        if ((v & 0xFFFF) >= 0x400) v = 0x400;                              // cmpi.w #$400,d0; bcs
        xVel = v;
        if ((x & 0xFFFF) >= 0x528) {
            captureAmy();
            return;
        }
        introAnimate();
    }

    /** MetalSonicSpawnExhaust (:174-183): SpawnObject, id $34, parent link. */
    private void spawnExhaust() {
        var objects = services().objectManager();
        if (objects == null) return;
        exhaust = new CczMetalSonicExhaust(new ObjectSpawn(x, y, 0x34, 0, 0, false, 0), this);
        objects.addDynamicObject(exhaust);
    }

    /** MetalSonicBob (:121-133): y = base + sin(var_3a) * 4 (the sub-pixel is not modelled, see the class comment). */
    private void bob() {
        int d0 = TrigLookupTable.sinHex(timer & 0xFF) * 4;
        y = (baseY + d0) & 0xFFFF;
        ySub = 0;
    }

    /** IntroFacePlayer (:405-415): left, then right when {@code obj.x - player.x} does not borrow. */
    private void facePlayer(PlayableEntity player) {
        setFacingRight(false);
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if ((x & 0xFFFF) >= (sprite.getCentreX() & 0xFFFF)) setFacingRight(true);
    }

    /** MetalSonicCaptureAmy (:204-209). */
    private void captureAmy() {
        var level = cdLevel();
        if (level != null) {
            CczIntroState.of(level).captureAmy();
            CczIntroState.of(level).restoreStagePalette(services());   // lea StagePalette,a3; bsr.w IntroLoadPalette
        }
        setDestroyed(true);
    }

    private void introAnimate() {
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        if (introAnimator == null) {
            int table = CczObjectCatalogue.offset(provider.payload(), "metalSonicAnimations");
            if (table < 0) return;
            introAnimator = new IntroAnimator(provider.image(), table);
        }
        introAnimator.step(anim, statusFlip);
        mapFrame = introAnimator.mapFrame;
        spriteFlip = introAnimator.spriteFlip;
    }

    /** {@code IntroAnimateObject} (objects/r3/intro.asm:431-476). */
    static final class IntroAnimator {
        private final CdStageImage image;
        private final int table;
        private int prevAnim = -1;
        private int index;
        private int animTimer;
        int mapFrame;
        int spriteFlip;

        IntroAnimator(CdStageImage image, int table) {
            this.image = image;
            this.table = table;
        }

        void step(int anim, int statusFlags) {
            if (anim != prevAnim) {                                        // cmp.b obj.prev_anim_id(a0),d0; beq
                prevAnim = anim;
                index = 0;
                animTimer = 0;
            }
            animTimer = (animTimer - 1) & 0xFF;                            // subq.b #1,obj.anim_timer; bpl .End
            if ((animTimer & 0x80) == 0) return;
            int script = table + (short) image.u16(table + anim * 2);
            int value = image.u8(script + index);
            while ((value & 0x80) != 0) {                                  // bpl .SetFrame; clr.b anim_index
                index = 0;
                value = image.u8(script);
            }
            mapFrame = value & 0x1F;
            int rotated = ((value << 3) | (value >> 5)) & 0xFF;            // rol.b #3,d1
            spriteFlip = (rotated ^ statusFlags) & 3;
            animTimer = image.u8(script + index + 1);
            index = (index + 2) & 0xFF;
        }
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.METAL_SONIC, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }
}
