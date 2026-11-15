package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.rings.RingDrawInternalAccess;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Ring, object $10 ({@code ObjRing}, Level/_Objects/Ring/Main.asm:7-287). One instance per
 * ring slot. The layout entry's routine 0 turns itself into the first ring of its group and
 * places the others with {@code FindNextObjSlot} (the first free slot after its own,
 * Object Spawner.asm:352-373), not Sonic 1's FindFreeObj. Collected rings are skipped without
 * a slot; when the group's first ring is already collected the entry deletes itself after
 * placing the rest (.ChkDel, Ring/Main.asm:203-209). Collection, the ring count, the global
 * spin frame and the sparkle are the shared ring owner's, which match Sonic 1: CD's
 * CollectRing (Ring/Main.asm:290-312), UpdateGlobalAnims (Level/Main.asm:513-549) and the
 * sparkle script.
 *
 * <p>Differences from Sonic 1's Obj25: children use FindNextObjSlot; ObjRing_Main skips the
 * despawn check while the ring is on screen ({@code tst.b oSprFlags; bmi}, Ring/Main.asm:215-224);
 * the collected bits are per time zone (the current zone's byte is used here; the
 * cross-period reads at Ring/Main.asm:39-66,109-113 belong to time travel).
 *
 * <p>The group's other rings are dynamic objects in slots after the entry; a rewind restore rebuilds them through
 * {@link #recreateForRewind} (a shell from the captured spawn, whose ring, state and entry X the capture restores), so
 * the slots they hold, and with them every later allocation, are the captured ones.
 */
public final class CdRing extends CdObject
        implements TouchResponseProvider, TouchResponseListener, ObjectVisualFacing.NonDirectional,
        com.openggf.level.objects.RewindRecreatable {
    /** .InitSubObj: move.b #$47,oColType(a1) (Ring/Main.asm:170). */
    static final int COLLISION_FLAGS = 0x47;
    private static final TouchResponseProfile TOUCH = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL, true, true, false, TouchShieldDeflectCapability.NONE, 0,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL, TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    private enum State { INIT, MAIN, SPARKLE }

    private RingSpawn ring;
    private List<RingSpawn> group;
    /** oVar32: the entry's X, which every ring of the group despawns by (restored by the rewind capture). */
    private int anchorX;
    private State state;

    /** A layout entry; its ring group comes from the level's ring placement mapping at routine 0. */
    public CdRing(ObjectSpawn spawn) {
        this(spawn, null, List.of(), spawn.x(), State.INIT);
    }

    private CdRing(ObjectSpawn spawn, RingSpawn ring, List<RingSpawn> group, int anchorX, State state) {
        super(spawn, "Ring");
        this.ring = ring;
        this.group = List.copyOf(group);
        this.anchorX = anchorX;
        this.state = state;
        this.x = spawn.x();
        this.y = spawn.y();
        // .InitSubObj: oPriority 2, oWidth 8, oYRadius 8, tile $A7AE (Ring/Main.asm:157-172).
        this.priority = 2;
        this.width = 8;
        this.yRadius = 8;
        this.tileWord = com.openggf.game.soniccd.objects.art.CdObjectArtProvider.RING_TILE;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        RingManager rings = services().ringManager();
        switch (state) {
            case INIT -> init(rings);
            case MAIN -> {
                if (rings != null && rings.isCollected(ring)) state = State.SPARKLE;
            }
            case SPARKLE -> {
                // ObjRing_Collect: move.b #1,oPriority(a0) (Ring/Main.asm:240); Ani_Ring ends in $FC
                // and ObjRing_Destroy deletes (Ring/Main.asm:276-286).
                priority = 1;
                ObjectManager objects = services().objectManager();
                int frames = objects != null ? objects.getFrameCounter() : vIntRunCount;
                if (rings == null || rings.isCollectedAndSparkleDone(ring, frames)) setDestroyed(true);
            }
        }
    }

    private void init(RingManager rings) {
        resolveGroup(rings);
        // .InitSubObj: oPriority 2, but Metallic Madness rings draw at 0 unless the entry's second subtype is set
        // (cmpi.b #6,zone; move.b #0,oPriority(a1); tst.b oSubtype2(a1), Ring/Main.asm:158-166), every ring of the
        // group alike. A set subtype2 also clears the tile's priority bit (andi.b #$7F,oTile(a1)): not modelled.
        if (zone() == 6 && subtype2() == 0) priority = 0;
        ObjectManager objects = services().objectManager();
        boolean anyLive = false;
        for (int i = 0; i < group.size(); i++) {
            RingSpawn member = group.get(i);
            if (rings != null && rings.isCollected(member)) continue;      // lsr.b d1,d4; bcs.w .Next
            anyLive = true;
            if (i == 0 || objects == null) continue;                          // the entry itself is ring 1
            ObjectSpawn childSpawn = new ObjectSpawn(member.x(), member.y(), spawn.objectId(), spawn.subtype(),
                    spawn.renderFlags(), false, spawn.rawYWord());
            // .Loop: bsr.w FindNextObjSlot; bne.w .DidInit (Ring/Main.asm:147-148).
            CdRing child = new CdRing(childSpawn, member, List.of(member), anchorX, State.MAIN);
            child.priority = priority;
            objects.addDynamicObjectAfterCurrent(child);
            if (child.isDestroyed()) break;
        }
        // bclr #7,(a2) for every uncollected ring: the spawner may place the entry again
        // (Ring/Main.asm:114,146).
        if (anyLive && objects != null) objects.clearSpawnCounterActiveBit(getSpawn());
        // .ChkDel: btst #0 of the collected bits deletes the entry (Ring/Main.asm:203-209).
        if (rings != null && rings.isCollected(ring)) {
            setDestroyed(true);
            return;
        }
        state = State.MAIN;
    }

    /** The level's expansion of this entry (CdLevel.expandRings); the shared owner's own spawns. */
    private void resolveGroup(RingManager rings) {
        var level = services().levelManager() != null ? services().levelManager().getCurrentLevel() : null;
        List<RingSpawn> mapped = level instanceof com.openggf.level.objects.RingObjectPlacementMapping mapping
                ? mapping.ringObjectPlacementMapping().get(getSpawn()) : null;
        if (mapped == null || mapped.isEmpty()) {
            RingSpawn canonical = rings != null ? rings.resolveCanonicalSpawn(x, y) : null;
            mapped = List.of(canonical != null ? canonical : new RingSpawn(x, y, -1));
        }
        group = List.copyOf(mapped);
        ring = group.getFirst();
    }

    @Override
    protected boolean despawnsByCheckObjDespawn() {
        // ObjRing_Main's own check against oVar32, the entry's X (Ring/Main.asm:214-224); not while sparkling.
        return state == State.MAIN;
    }

    @Override protected int despawnX() { return anchorX; }

    @Override
    public int getCollisionFlags() {
        return state == State.MAIN
                && com.openggf.game.mutators.LevelMutatorPolicyAccess.ringsAllowed(tryServices())
                ? COLLISION_FLAGS : 0;
    }

    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }
    @Override public TouchResponseProfile getTouchResponseProfile() { return TOUCH; }
    @Override public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) { return TOUCH; }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        if (state != State.MAIN || result.category() != TouchCategory.SPECIAL
                || !(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        RingManager rings = services().ringManager();
        // The player's slot runs first: ObjSonic's Player_ObjCollide (Sonic/Main.asm:347) advances the ring's
        // routine (addq.b #2,oRoutine(a1), Palmtree Panic/Player Object Collision.asm:91-95), and RunObjects
        // reaches the ring later in the same pass (Object Functions.asm:11-33), where ObjRing_Collect falls into
        // ObjRing_Sparkle (Ring/Main.asm:236-276). So the sparkle's first frame is the touch's frame: Ani_Ring's
        // four frames of 6 then $FC (Ring/Data/Animations.asm) put ObjRing_Destroy's DeleteObject on its 26th
        // pass, 25 frames after the touch (R32A: collected levelFrames 371, slot 43 free natively in 396).
        if (rings != null && com.openggf.level.rings.RingManagerInternalAccess.collectPlacedRingBeforeItsPass(
                rings, ring, player, frameCounter)) {
            state = State.SPARKLE;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        RingManager rings = services().ringManager();
        if (rings == null || state == State.INIT || ring == null) return;
        // ObjRing_Main .DoAnim: oMapFrame = ringAnimFrame (Ring/Main.asm:226-229), read through the
        // shared ring owner's retained clock (CdGlobalAnimations).
        var level = services().levelManager();
        RingDrawInternalAccess.drawPlacedObject(rings, ring, level != null ? level.getFrameCounter() : 0);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.bucket(priority);
    }

    /** A shell from the captured spawn; the generic capture restores its ring, group, state and entry X. */
    @Override
    public com.openggf.level.objects.AbstractObjectInstance recreateForRewind(
            com.openggf.level.objects.RewindRecreateContext ctx) {
        return new CdRing(ctx.spawn(), null, List.of(), ctx.spawn().x(), State.MAIN);
    }

    @Override
    public String traceDebugDetails() {
        return String.format("state=%s ring=@%04X,%04X group=%d anchor=%04X", state, ring.x(), ring.y(),
                group.size(), anchorX);
    }

}
