package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Laser, object $1D in the R82x index ({@code LaserObject}, objects/r8/laser.asm:3-251; R83x's $1D is the shared bumper): the
 * emitter (routine 0/2), the beam it fires every $1E frames (routine 4 down to the floor, routine 6 back) and the
 * floor splash (routine 8). A beam that touches a player flings them back (the knock-back state without damage) and
 * toggles {@code miniSonic}: a subtype-0 laser only catches a full-size player, any other subtype only a mini one.
 * The sounds are FM $C5 (subtype 0) and $BC.
 */
public final class MmzLaser extends MmzObject implements ObjectVisualFacing.NonDirectional {
    /** byte_20B6DE (objects/r8/laser.asm:243-250): the beam's lower reach by animation frame. */
    private static final int[] REACH = {-12, -28, -44, -60, -76, -92, -108, -124};

    private int routine;
    private int timer = 1;                       // oVar2A
    private int anim;
    private int mapFrame;
    private int collisionHeight;
    private CdAnimator animator;
    private MmzLaser child;                      // oVar2C: the splash
    private boolean split;                       // oVar2A of the splash set by its beam
    private int subtype;
    private boolean sheetTwo;
    private boolean initialised;

    public MmzLaser(ObjectSpawn spawn) {
        super(spawn, "Laser");
        this.subtype = spawn.subtype() & 0xFF;
        this.sheetTwo = subtype != 0;
    }

    private MmzLaser(ObjectSpawn spawn, MmzLaser emitter, int routine) {
        super(spawn, "Laser");
        this.subtype = emitter.subtype;
        this.sheetTwo = emitter.sheetTwo;
        this.routine = routine;
        this.initialised = true;
        this.priority = routine == 4 ? 2 : 0;
        this.tileWord = MmzObjectArt.LASER_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!hasLaserArt()) return;                                    // R83x's $1D is the shared bumper
        switch (routine) {
            case 0 -> { init(); emit(); }
            case 2 -> emit();
            case 4 -> down(player);
            case 6 -> up(player);
            case 8 -> splash();
            default -> { }
        }
    }

    /** LaserObject_0_Routine0 (objects/r8/laser.asm:23-36). */
    private void init() {
        routine = 2;
        initialised = true;
        tileWord = MmzObjectArt.LASER_TILE;
        mapFrame = subtype != 0 ? 2 : 1;
        timer = 1;
    }

    /** LaserObject_0_Routine2 (objects/r8/laser.asm:37-70): fire a beam. */
    private void emit() {
        timer = (timer - 1) & 0xFF;
        if (timer != 0) return;
        var objects = services().objectManager();
        if (objects == null) return;
        ObjectSpawn beamSpawn = new ObjectSpawn(x, y & 0xFFF0, spawn.objectId(), subtype, 0, false, 0);
        MmzLaser beam = new MmzLaser(beamSpawn, this, 4);
        beam.yRadius = 4;
        beam.width = 0;
        objects.addDynamicObjectAfterCurrent(beam);                    // jsr SpawnObjectAfter
        timer = 0x1E;
        if (renderFlagOnScreen()) services().playSfx(subtype != 0 ? 0xBC : 0xC5);
    }

    /** LaserObject_0_Routine4 (objects/r8/laser.asm:76-115): the beam extends. */
    private void down(PlayableEntity player) {
        y = (y + 4) & 0xFFFF;
        if ((y & 0xF) == 0) anim = Math.min(anim + 1, 7);
        animate();
        if (floorDistance() < 0) {
            routine += 2;
            var objects = services().objectManager();
            if (objects != null) {
                MmzLaser cap = new MmzLaser(new ObjectSpawn(x, y, spawn.objectId(), subtype, 0, false, 0), this, 8);
                cap.anim = 8;
                child = cap;
                objects.addDynamicObjectAfterCurrent(cap);
            }
        }
        hit(player);
    }

    /** LaserObject_0_Routine6 (objects/r8/laser.asm:116-142): the beam retracts. */
    private void up(PlayableEntity player) {
        int d0 = (y + 4) & 0xFFFF;
        if ((d0 & 0xF) == 0) {
            d0 = (d0 - 0x10) & 0xFFFF;
            anim--;
            if (anim < 0) {
                if (child != null) child.split = true;                // move.b #1,$2A(a1)
                setDestroyed(true);
                return;
            }
        }
        y = d0;
        animate();
        hit(player);
    }

    /** LaserObject_0_Routine8 (objects/r8/laser.asm:143-155): the splash plays until its beam is gone. */
    private void splash() {
        if (split) {
            setDestroyed(true);
            return;
        }
        animate();
    }

    /**
     * The stages whose ObjectIndex starts the laser at $1D (r8/object_index_2.asm:32): those with laser data, R82x.
     * R83x's $1D is the bumper (r8/object_index_3.asm:32).
     */
    static boolean indexedIn(com.openggf.game.soniccd.CdStagePayload payload) {
        return MmzObjectCatalogue.offset(payload, "laserMappings1") >= 0;
    }

    /** R83x indexes $1D as the bumper (r8/object_index_3.asm), so the Laser stands down where the stage has no laser art. */
    private boolean hasLaserArt() {
        var provider = artProvider();
        return provider == null || MmzObjectCatalogue.offset(provider.payload(), "laserMappings1") >= 0;
    }

    private void animate() {
        if (animator == null) animator = mmzAnimator("laserAnimations", anim);
        if (animator == null) return;
        animator.anim = anim;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    private int floorDistance() {
        return ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
    }

    /** loc_20B60A (objects/r8/laser.asm:156-241): the beam catches the player. */
    private void hit(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (sprite.getAnimationId() == 0x2B) return;                  // cmpi.b #$2B,oAnim(a6): giving up
        MmzStageSignals signals = signals();
        boolean shrunk = signals != null && signals.shrunk();
        if (subtype == 0 ? shrunk : !shrunk) return;                  // loc_20B60A / loc_20B622
        if (sprite.isDebugMode()) return;
        int reach = sprite.getXRadius() + 4;
        int dx = ((x - sprite.getCentreX()) + reach) & 0xFFFF;
        if ((dx & 0x8000) != 0 || dx >= reach * 2) return;
        int radiusY = sprite.getYRadius();
        int playerBottom = (sprite.getCentreY() + radiusY) & 0xFFFF;
        int tip = (y + REACH[Math.min(anim, 7)]) & 0xFFFF;
        if (tip >= playerBottom) return;
        int playerTop = (sprite.getCentreY() - radiusY) & 0xFFFF;
        if (playerTop >= ((y + 4) & 0xFFFF)) return;
        // Hit: routine 4 (knock-back), airborne, flung back and up, anim $38, radii of the new size.
        sprite.setHurt(true);
        sprite.setAir(true);
        sprite.setYSpeed((short) -0x400);
        sprite.setXSpeed((short) (sprite.getCentreX() < x ? -0x200 : 0x200));
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(0x38);
        if (signals == null) return;
        signals.flipShrunk();
        if (signals.shrunk()) sprite.applyCustomRadii(5, 0xA); else sprite.applyCustomRadii(9, 0x13);
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised || !hasLaserArt()) return;
        drawFrame(sheetTwo ? MmzObjectArt.LASER_TWO : MmzObjectArt.LASER_ONE, mapFrame, false, false);
    }
}
