package com.openggf.game.soniccd.objects.projector;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

import java.util.List;

/**
 * The Metal Sonic projector, {@code ObjProjector} (Palmtree Panic/Objects/Projector/Main.asm:36-224; the same routine in
 * Palmtree Panic act 1 Present's copy, Collision Chaos's legacy-ABI link, Wacky Workbench's own
 * {@code Wacky Workbench/Objects/Projector/Main.asm} and {@code objects/r4..r7/projector.asm}; the zones differ only in
 * {@link ProjectorVariant}'s immediates). A Past act 1 or 2 object that the Present of the same act omits.
 *
 * <p>Init (routine 0) deletes itself when {@code projDestroyed} is set ({@link CdTimeTravel#projectorDestroyed()}, the
 * campaign owner), requests the pattern load, sets priority 4, radii and width $C, collision $FB and the zone's tile, and
 * creates four children in slots from {@code FindObjSlot}: the beam hologram (subtype $FF), the Metal Sonic hologram
 * (subtype 1) and two hologram animals ({@code $80}, {@code $81}); a full object table deletes the projector. It then falls
 * into routine 2 in the same turn: collision status raised by the player's touch clears the collision and moves to routine 4;
 * either way {@code SolidObject} runs against the player.
 *
 * <p>The touch is {@code Player_TouchSpecial}'s {@code .CheckIfRoll} for the $3B type: it raises the collision status only
 * while the player's animation is rolling (Player Object Collision.asm:302-322, 389-394), on every frame of overlap.
 *
 * <p>Routine 4 shows frame 1, raises {@code oProjectorChildrenInvalid} (the hologram children then delete themselves),
 * requests the pattern load that restores the level art, runs {@code SolidObject} and lets go of a standing player
 * ({@code GetOffObject}). Routine 6 walks {@code ObjProjector_ExplosionLocs} (read from the stage file): a tick counter
 * (a byte) is raised each turn and, when it equals a record's tick, an explosion ({@code $18}, routine2 1: no points, no
 * flower) appears at the record's offset with {@code FM_EXPLODE} ($9E); the $FF terminator starts a 60-frame delay
 * (routine 8) after which {@code projDestroyed} is set and the projector deletes itself. DrawObject runs every turn;
 * CheckObjDespawn only in routines 0 and 2.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CdProjector extends CdObject implements ReevaluatingSolidObject, TouchResponseProvider,
        TouchResponseListener, ObjectVisualFacing.NonDirectional, RewindRecreatable {
    /** move.b #$FB,oColType(a0) (Palmtree Panic/Objects/Projector/Main.asm:84). */
    private static final int COLLISION_FLAGS = 0xFB;
    /** move.w #FM_EXPLODE,d0 (Main.asm:199): $9E. */
    private static final int FM_EXPLODE = 0x9E;
    /** move.w #60,oProjectorTimer(a0) (Main.asm:209). */
    private static final int FINAL_DELAY = 60;

    private int routine;
    private int collisionFlags;
    private boolean touched;
    private int mapFrame;
    private boolean childrenInvalid;
    /** oProjectorTimer ({@code oVar2A}): a byte while the schedule runs, a word for the final delay. */
    private int timer;
    /** oProjectorSchedule ({@code oVar2C}): the file offset of the next record. */
    private int scheduleAt;

    public CdProjector(ObjectSpawn spawn) {
        super(spawn, "Projector");
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CdProjector(ctx.spawn());
    }

    /** {@code oProjectorChildrenInvalid} ({@code oVar3F}): the children delete themselves once it is set. */
    public boolean childrenInvalid() {
        return childrenInvalid;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                init();
                if (routine == 2) solid(player);                    // ObjProjector_Init falls into ObjProjector_Solid
            }
            case 2 -> solid(player);
            case 4 -> startExploding(player);
            case 6 -> exploding();
            default -> finalDelay();
        }
    }

    private ProjectorVariant variant() {
        var level = cdLevel();
        return level == null ? null : ProjectorVariant.of(level.payload().stage().zone());
    }

    /** ObjProjector_Init (Main.asm:73-153). */
    private void init() {
        CdTimeTravel time = CdTimeTravel.of(services().gameModule());
        ProjectorVariant variant = variant();
        if ((time != null && time.projectorDestroyed()) || variant == null) {
            setDestroyed(true);                                      // tst.b projDestroyed; bne ObjProjector_Destroy
            return;
        }
        routine = 2;
        priority = 4;
        width = 0xC;
        yRadius = 0xC;
        collisionFlags = COLLISION_FLAGS;
        tileWord = variant.tile(cdLevel().payload().stage().actNumber() - 1);
        scheduleAt = ProjectorObjectCatalogue.schedule(cdLevel().payload());
        // Four FindObjSlot children; a full table (bne.w ObjProjector_Destroy) deletes the projector.
        int slot = getSlotIndex();
        if (!spawnChild(new CdProjectorHologram(child(spawn.objectId(), x - 0x15, y - 7, 0xFF), slot))) return;
        if (!spawnChild(new CdProjectorHologram(child(spawn.objectId(), x + variant.metalDx(), y - 4, 1), slot))) return;
        if (!spawnAnimal(variant, variant.animal1(), 0x80, slot)) return;
        spawnAnimal(variant, variant.animal2(), 0x81, slot);
    }

    private ObjectSpawn child(int id, int cx, int cy, int subtype) {
        return new ObjectSpawn(cx & 0xFFFF, cy & 0xFFFF, id, subtype, 0, false, 0);
    }

    private boolean spawnChild(AbstractObjectInstance child) {
        if (findObjSlot(child)) return true;
        setDestroyed(true);                                          // bne.w ObjProjector_Destroy
        return false;
    }

    /**
     * A hologram animal: the zone's animal object at the offset with subtype $80/$81 and this projector's slot in
     * {@code oVar3E}. The animal is created through the stage's own registry; a zone whose animal routine does not
     * host the hologram branch (Tidal Tempest's walker, Wacky Workbench's unhosted animal) creates none.
     */
    private boolean spawnAnimal(ProjectorVariant variant, int[] offset, int subtype, int slot) {
        var registry = services().gameModule().createObjectRegistry();
        var instance = registry.create(child(variant.animalId(), x + offset[0], y + offset[1], subtype));
        if (!(instance instanceof ProjectorChild link) || instance instanceof PlaceholderObjectInstance
                || !(instance instanceof AbstractObjectInstance animal)) {
            return true;
        }
        link.linkProjector(slot, variant.parentId());
        return spawnChild(animal);
    }

    /** ObjProjector_Solid (Main.asm:155-163). */
    private void solid(PlayableEntity player) {
        if (touched) {                                               // tst.b oColStatus(a0); beq .Solid
            touched = false;
            collisionFlags = 0;                                      // clr.w oColType(a0): type and status
            routine = 4;
        }
        solidObject(Entry.SOLID);
    }

    /** ObjProjector_StartExploding (Main.asm:168-177), which falls into ObjProjector_Exploding. */
    private void startExploding(PlayableEntity player) {
        routine = 6;
        mapFrame = 1;
        childrenInvalid = true;                                      // st oProjectorChildrenInvalid(a0)
        if (solidObject(Entry.SOLID) && player != null) {
            ObjectManagerInternalAccess.releaseFromReevaluatingSolid(services().objectManager(), player, this);
        }
        exploding();
    }

    /** ObjProjector_Exploding (Main.asm:179-210). */
    private void exploding() {
        CdObjectArtProvider provider = artProvider();
        if (provider == null) return;
        int tick = provider.image().u8(scheduleAt);
        if ((byte) tick < 0) {                                       // bmi.s .Finished
            routine = 8;
            timer = FINAL_DELAY;
            return;
        }
        timer = (timer + 1) & 0xFF;                                  // addq.b #1,oProjectorTimer(a0)
        if (timer != tick) return;
        int dx = (byte) provider.image().u8(scheduleAt + 1);
        int dy = (byte) provider.image().u8(scheduleAt + 2);
        scheduleAt += 3;                                             // move.l a6,oProjectorSchedule(a0)
        var objects = services().objectManager();
        if (objects == null) return;
        CdExplosion explosion = new CdExplosion(x + dx, y + dy, 0, false, 0, 0);   // routine2 1: no points, no flower
        objects.addDynamicObject(explosion);
        if (explosion.isDestroyed()) return;                         // bne.s .End: no free slot, no sound
        services().playSfx(FM_EXPLODE);
    }

    /** ObjProjector_FinalDelay (Main.asm:215-219). */
    private void finalDelay() {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return;
        CdTimeTravel time = CdTimeTravel.of(services().gameModule());
        if (time != null) time.destroyProjector();                   // st projDestroyed
        setDestroyed(true);
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        // Player_TouchSpecial .CheckIfRoll: cmpi.b #2,oAnim(a0); addq.b #1,oColStatus(a1).
        if (collisionFlags != 0 && player.getAnimationId() == CdSonicAnimation.ROLL.id()) touched = true;
    }

    @Override public int getCollisionFlags() { return collisionFlags; }
    @Override public int getCollisionProperty() { return 0; }
    /** A $C0-class type is the native special touch (Player Object Collision.asm:84-86, 302). */
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return (collisionFlags & 0xC0) == 0xC0; }
    /** Native tests the special touch on every frame of overlap. */
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TouchResponseProfile.fromProvider(this); }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multi) { return TouchResponseProfile.fromProvider(this, multi); }

    /** {@code cmpi.b #2,oRoutine(a0); bgt.s .End; jsr CheckObjDespawn}. */
    @Override protected boolean despawnsByCheckObjDespawn() { return routine <= 2; }

    @Override
    public String traceDebugDetails() {
        return String.format("routine=%d timer=%d schedule=%X invalid=%b frame=%d col=%02X", routine, timer, scheduleAt,
                childrenInvalid, mapFrame, collisionFlags);
    }

    int mapFrame() { return mapFrame; }
    int routine() { return routine; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        String key = routine == 0 ? null : ProjectorArt.key(artProvider(), services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }
}
