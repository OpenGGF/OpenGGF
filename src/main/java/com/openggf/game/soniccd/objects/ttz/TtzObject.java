package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Base of the Tidal Tempest objects: {@link CdObject} plus the zone's sheet lookup and the
 * object-own chunk despawn test ({@code loc_20F03E}-style routines that do not check the
 * on-screen bit, unlike CheckObjectDespawn).
 */
abstract class TtzObject extends CdObject {
    protected TtzObject(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /**
     * CheckObjDespawn / CheckObjDespawn2 end in {@code DeleteObject} (Object Spawner.asm:388-399), which clears the object's
     * whole record, so its ID is no longer the routine's and the children that test {@code cmpi.b #id,obj.id(a1)} against
     * their parent delete themselves in the same pass (circle spike ball, objects/r4/circle_spike_ball.asm:3-9; mace
     * parts). The engine's out-of-range removal ends in {@code onUnload}: the retired parent reads as deleted from then on.
     * The circle spike ball now tests its parent's slot with {@code slotHoldsId}; the mace parts and the twin move block still
     * read {@code isDestroyed()} (no native row separates them from the rule: R41D's slots and every frontier are equal with this
     * override removed). Scoped to Tidal Tempest: in the other zones the same rule moved the end windows of R51A, R52A and R61B.
     */
    @Override
    public void onUnload() {
        setDestroyed(true);
    }

    /**
     * An own despawn test without the on-screen exception: the object's chunk against the camera's,
     * {@code cmpi.w #$280,d0; bls} (loc_20F03E, objects/r4/move_wall.asm:91-102).
     */
    protected static boolean outsideChunkRange(int objX, int cameraX) {
        int chunk = objX & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > 0x280;
    }

    /**
     * {@code switch_flags[index]} is non-zero (Level/Tidal Tempest/R4 Compatibility.asm:163): the array the Switch
     * objects write, the module's {@link com.openggf.game.soniccd.objects.shared.SharedSwitchFlags} service.
     */
    protected boolean switchFlag(int index) {
        var flags = services().gameService(com.openggf.game.soniccd.objects.shared.SharedSwitchFlags.class);
        return flags != null && flags.get(index) != 0;
    }

    /** {@code btst #7,switch_flags[index]}: the switch is held this frame (objects/r4/platform_chain.asm:171-172). */
    protected boolean switchHeld(int index) {
        var flags = services().gameService(com.openggf.game.soniccd.objects.shared.SharedSwitchFlags.class);
        return flags != null && (flags.get(index) & 0x80) != 0;
    }

    /** {@code WobbleTable[index]}, a signed byte (ext.w), from the stage file. */
    protected int wobble(int index) {
        var provider = artProvider();
        if (provider == null) return 0;
        int table = TtzObjectCatalogue.offset(provider.payload(), "wobbleTable");
        return table < 0 ? 0 : (byte) provider.image().u8(table + (index & 0xFF));
    }

    /**
     * The distance FindLevelFloor returns for the point ({@code x}, {@code y}) with {@code a3 = $10}; over two blank blocks it
     * is {@code $1F - (y & $F)} (Level/Collision Floor.asm:697-702, 788-793), Sonic CD's object floor rule.
     */
    protected static int floorDistance(int x, int y) {
        return com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y).distance();
    }

    /** CheckObjectDespawn2 (Level/Object Spawner.asm:388-399): on screen keeps; else chunk distance > $280. */
    protected static boolean outsideDespawnRange(int objX, int cameraX, boolean onScreen) {
        return !onScreen && outsideChunkRange(objX, cameraX);
    }

    /** {@code water_y}: the gameplay water line, base level plus bob (TtzWaterDataProvider). */
    protected int waterY() {
        var water = services().waterSystem();
        return water == null ? 0 : water.getGameplayWaterLevelY(services().featureZoneId(), services().featureActId());
    }

    /** {@code static_water_y}: the base level without the bob. */
    protected int staticWaterY() {
        var water = services().waterSystem();
        return water == null ? 0 : water.getWaterLevelY(services().featureZoneId(), services().featureActId());
    }

    /**
     * {@code SpawnObject} (FindObjSlot): the child takes the first free dynamic slot; false when none is free
     * ({@code bne}). The object runs this frame if its slot follows the current object's.
     */
    protected boolean spawnChild(CdObject child) {
        var objects = services().objectManager();
        if (objects == null) return false;
        objects.addDynamicObject(child);
        return child.getSlotIndex() >= 0 && !child.isDestroyed();
    }

    /** {@code SpawnObjectAfter} (FindNextObjSlot): the first free slot after the current object's. */
    protected boolean spawnChildAfter(CdObject child) {
        var objects = services().objectManager();
        if (objects == null) return false;
        objects.addDynamicObjectAfterCurrent(child);
        return child.getSlotIndex() >= 0 && !child.isDestroyed();
    }

    /** A child spawn record: the layout entry fields the child routine reads (id, subtype), no layout row. */
    protected static ObjectSpawn childSpawn(int x, int y, int objectId, int subtype, int renderFlags) {
        return new ObjectSpawn(x, y, objectId, subtype, renderFlags, false, 0);
    }

    /** The first player (the focused sprite), {@code player_object}. */
    protected com.openggf.game.PlayableEntity mainPlayer() {
        var camera = services().camera();
        return camera == null ? null : camera.getFocusedSprite();
    }

    /** Draws mapping frame {@code frame} of the stage's {@code kind} sheet at the object's position. */
    protected void drawSheet(String kind, int frame, boolean hFlip, boolean vFlip) {
        var provider = artProvider();
        if (provider == null) return;
        var camera = services().camera();
        String key = TtzObjectArt.key(provider, kind, tileWord, camera == null ? 0 : camera.getX());
        if (key != null) drawFrame(key, frame, hFlip, vFlip);
    }
}
