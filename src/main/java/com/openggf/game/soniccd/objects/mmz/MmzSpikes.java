package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdSpring;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Trap block and spikes, object $36 in every Metallic Madness index ({@code SpikesObject} in
 * objects/r8/trap_1.asm:23-229 for R81x, trap_2.asm:472-742 for R82x, trap_3.asm:445-644 for R83x). One ID, three shapes by subtype:
 * <ul>
 *   <li><b>Block</b> (subtype 0-$7F): a $10 box that is solid while the player is within $20 vertically, carrying a
 *       spring on top (a spike block, subtype 1, carries solid spikes instead).</li>
 *   <li><b>Spikes</b> (subtype $80-$FF): the top spikes ride the block (subtype $FF, hurting a player who stands on
 *       them); the others are sideways spikes with collision $83. R82x makes some of them live on one {@code stage_layer}
 *       only (subtypes $FB and below on layer 1, $FD and $FC on layer 0).</li>
 *   <li><b>Layer platform</b> (R82x subtype $F8): solid only while {@code stage_layer} is nonzero.</li>
 * </ul>
 * The three builds differ in the details marked per method; the variant is the act (R81x, R82x, R83x).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzSpikes extends MmzObject
        implements ReevaluatingSolidObject, TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private enum Kind { BLOCK, SPIKES, LAYER_PLATFORM }

    private int variant;                         // 1: R81x, 2: R82x, 3: R83x
    private Kind kind;
    private final ObjectInstance parent;         // oVar34 of a spikes child
    /** The parent's slot: native keeps its address and tests that the slot still holds an object. */
    private final int parentSlot;
    private final int offsetY;                   // oVar39 of a spikes child
    private final int initialX;                  // oVar36
    private boolean initialised;
    private int routine;
    private int mapFrame;
    private int collision;
    private boolean activeLayer = true;

    public MmzSpikes(ObjectSpawn spawn) {
        this(spawn, null, 0);
    }

    private MmzSpikes(ObjectSpawn spawn, ObjectInstance parent, int offsetY) {
        super(spawn, "Spikes");
        this.parent = parent;
        this.parentSlot = parent instanceof com.openggf.level.objects.AbstractObjectInstance a ? a.getSlotIndex() : -1;
        this.offsetY = offsetY;
        this.initialX = spawn.x();
    }

    /** Variant and kind need the loaded stage, which the services reach only once the object is in the level. */
    private void resolve() {
        if (kind != null) return;
        variant = parent instanceof MmzSpikes block ? block.variant : stageVariant();
        int subtype = spawn.subtype() & 0xFF;
        if (variant == 2 && subtype == 0xF8) kind = Kind.LAYER_PLATFORM;     // cmpi.b #$F8 (trap_2.asm:472-476)
        else kind = (subtype & 0x80) != 0 ? Kind.SPIKES : Kind.BLOCK;        // tst.b oSubtype; bmi
    }

    /** The build: 1 for R81x, 2 for R82x, 3 for R83x. */
    private int stageVariant() {
        var level = cdLevel();
        return level == null ? 1 : level.payload().stage().actNumber();
    }

    private int subtype() {
        return spawn.subtype() & 0xFF;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        resolve();
        switch (kind) {
            case BLOCK -> updateBlock(player);
            case SPIKES -> updateSpikes(player);
            case LAYER_PLATFORM -> updateLayerPlatform(player);
        }
    }

    // ---- block ---------------------------------------------------------------------------------------------

    /** SpikesObject_1_Routine0/2 (trap_1.asm:42-99; trap_3.asm:464-516); SpikesObject_2_Routine0/2 (trap_2.asm:493-550). */
    private void updateBlock(PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            initialised = true;
            priority = 3;
            tileWord = MmzObjectArt.TRAP_BLOCK_TILE;
            width = 0x10;
            yRadius = 0x10;
            attachChild();
        }
        // SpikesObject_1_Routine2 (trap_1.asm:79-99): drawn last frame and within $20 vertically.
        if (!(player instanceof AbstractPlayableSprite sprite) || !renderFlagOnScreen()) return;
        if (Math.abs((short) (sprite.getCentreY() - y)) < 0x20) solidObject(Entry.SOLID);
    }

    /** loc_20BE20/loc_20BE4C (trap_1.asm:58-78): a spring on top, or for subtype 1 solid spikes. */
    private void attachChild() {
        var objects = services().objectManager();
        if (objects == null) return;
        if (subtype() == 1) {
            ObjectSpawn child = new ObjectSpawn(x, y, spawn.objectId(), 0xFF, 0, false, 0);
            objects.addDynamicObject(new MmzSpikes(child, this, -0x18));              // move.b #$E8,oVar39
        } else {
            ObjectSpawn child = new ObjectSpawn(x, y, 0x0A, subtype(), 0, false, 0);
            if ((subtype() & 0xC) == 0) {
                objects.addDynamicObject(new MmzAttachedSpring(child, this, 0, -0x18));
            } else {
                objects.addDynamicObject(new CdSpring(child));                         // sideways and diagonal: unattached
            }
        }
    }

    // ---- spikes --------------------------------------------------------------------------------------------

    private void updateSpikes(PlayableEntity player) {
        if (routine == 0) {
            routine = 2;
            initialised = true;
            initSpikes();
        }
        if (variant == 2) spikesRoutine2Layered(player); else spikesRoutine2(player);
        follow();
    }

    /**
     * SpikesObject_0_Routine0 (trap_1.asm:144-159; trap_3.asm:566-582) and SpikesObject_1_Routine0 (trap_2.asm:595-611):
     * tile $488; frame 1 (sideways spikes, collision $83, width $12) unless the subtype says top spikes.
     */
    private void initSpikes() {
        tileWord = MmzObjectArt.TRAP_SPIKES_TILE;
        width = 0x10;
        yRadius = 8;
        if (variant == 2) {
            priority = 1;
            if ((subtype() & 1) == 0) makeSideways();
        } else {
            priority = variant == 1 ? 4 : 3;
            if (subtype() != 0xFF) makeSideways();
        }
    }

    private void makeSideways() {
        mapFrame = 1;
        width = 0x12;
        yRadius = 8;
        collision = 0x83;
    }

    /** SpikesObject_0_Routine2 (trap_1.asm:160-223; trap_3.asm:583-639). */
    private void spikesRoutine2(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (mapFrame == 0) {
            if (Math.abs((short) (sprite.getCentreY() - y)) >= 0x20) return;
            standOnSpikes(sprite);
        } else if (renderFlagOnScreen() && Math.abs((short) (sprite.getCentreY() - y)) < 0x20) {
            solidObject(Entry.SOLID);                                                  // loc_20BFBC
        }
    }

    /** SpikesObject_1_Routine2 (trap_2.asm:612-693): the layer rules, then the same solid and hurt. */
    private void spikesRoutine2Layered(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        int d5 = (-subtype()) & 0xFF;
        boolean live = true;
        if (d5 >= 5) {                                                                 // loc_20D368
            priority = 3;
            collision = 0x83;
            if (stageLayer() == 0) {
                collision = 0;
                live = false;
            }
        } else if (d5 >= 3) {                                                          // loc_20D346
            tileWord = MmzObjectArt.TRAP_SPIKES_LAYER_TILE;
            collision = 0x83;
            if (stageLayer() != 0) {
                collision = 0;
                live = false;
            }
        }
        if (!live) {                                                                   // loc_20D35E: GetOffObject
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), sprite, this);
            return;
        }
        if (mapFrame != 0) {                                                           // loc_20D3E4
            if (renderFlagOnScreen()) solidObject(Entry.SOLID);
            return;
        }
        collision = 0;                                                                 // clr.b oColType
        standOnSpikes(sprite);
    }

    /**
     * SolidObject, then hurt a player who stands on the top spikes (trap_1.asm:166-198): not during a warp, not
     * invincible, not already hurt, and with the Y rewound by one frame of speed first, as Sonic 1's spikes do.
     */
    private void standOnSpikes(AbstractPlayableSprite sprite) {
        if (!solidObject(Entry.SOLID)) return;                                         // beq: d0 = 0, no top or bottom contact
        if (!stoodOn(sprite)) return;                                                  // btst #3,oFlags(a0)
        var tt = com.openggf.game.soniccd.timetravel.CdTimeTravel.of(services().gameModule());
        if ((tt != null && tt.warping()) || sprite.getInvincibleFrames() > 0) return;  // tst.b warping / invincible
        if (sprite.isHurt() || sprite.getDead() || sprite.getInvulnerableFrames() > 0) return;
        NativePositionOps.addYPos16_16(sprite, -(sprite.getYSpeed() << 8));
        boolean hadRings = sprite.getRingCount() > 0;
        if (hadRings && !sprite.hasShield()) services().spawnLostRings(sprite, 0);
        sprite.applyHurtOrDeath(x, true, hadRings);                                    // jsr HurtPlayer
    }

    /** loc_20BEA4 (trap_1.asm:106-137): after the draw, copy the parent's position plus the offsets. */
    private void follow() {
        if (parent == null) return;
        if (slotOccupant(parentSlot) == null) {
            setDestroyed(true);                                                        // tst.b oID(a1); beq DeleteObject
            return;
        }
        x = parent.getX() & 0xFFFF;
        y = (parent.getY() + offsetY) & 0xFFFF;
    }

    // ---- layer platform ------------------------------------------------------------------------------------

    /** loc_20D408 (trap_2.asm:700-737): solid while stage_layer is nonzero, otherwise it lets go. */
    private void updateLayerPlatform(PlayableEntity player) {
        initialised = true;
        priority = 3;
        tileWord = MmzObjectArt.LAYER_PLATFORM_TILE;
        width = 0x18;
        yRadius = 0x10;
        if (stageLayer() != 0) {
            solidObject(Entry.SOLID);
        } else if (player != null) {
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
    }

    // ---- touch, despawn, draw ------------------------------------------------------------------------------

    @Override public int getCollisionFlags() { return kind == Kind.SPIKES ? collision : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return kind == Kind.LAYER_PLATFORM ? x : initialX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised || kind == null) return;
        switch (kind) {
            case BLOCK -> drawSheet(MmzObjectArt.TRAP_BLOCK, 0, false, false);
            case SPIKES -> drawSheet(MmzObjectArt.trapSpikesKey(tileWord), mapFrame, hFlip(), vFlip());
            case LAYER_PLATFORM -> drawSheet(MmzObjectArt.LAYER_PLATFORM, 0, false, false);
        }
    }
}
