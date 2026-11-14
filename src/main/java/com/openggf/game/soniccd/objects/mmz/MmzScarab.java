package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Scarab spawner, the layout entry of object $2F in the R81x index ({@code ScarabObject} with {@code oSubtype} above 2,
 * ScarabObject_6_Routine0..4, objects/r8/scarab.asm:3-193; $2F is a null object in R82x and R83x). It is never drawn. When the
 * player comes within $140 pixels on both axes it makes the beetle ({@link MmzScarabBody}, with its shell) unless the
 * stage's {@code object_states} entry says the beetle is gone (bit 0), and a time monitor ({@link MmzScarabItem}) on
 * its back unless that is gone (bit 1) or {@code oSubtype2} is not 0-7. Afterwards it keeps the two states in step
 * (loc_20DD08..loc_20DE66). The layout subtype is the beetle's kind (bit 0: 0 fast, 1 slow) and, in bit 4, its direction.
 */
public final class MmzScarab extends MmzObject implements ObjectVisualFacing.NonDirectional {
    /** CheckObjDespawn2: cmpi.w #$80+(320+$40)+$80,d0; bls (Level/Object Spawner.asm:385-399). */
    private static final int DESPAWN_RANGE = 0x80 + (320 + 0x40) + 0x80;

    private int routine;                         // oRoutine
    private MmzScarabBody body;                  // oVar2E
    private MmzScarabItem item;                  // oVar30
    private int sub2;
    private boolean sub2Resolved;
    private int stateKey;

    public MmzScarab(ObjectSpawn spawn) {
        super(spawn, "Scarab");
    }

    private int sub2() {
        if (!sub2Resolved) {
            sub2 = subtype2();
            sub2Resolved = true;
        }
        return sub2;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!hasArt()) return;
        switch (routine) {
            case 0 -> {                                                    // ScarabObject_6_Routine0 (:21-35)
                routine = 2;
                stateKey = (spawn.layoutIndex() << 2) | liveTimeZone();
                waitForPlayer(player);
            }
            case 2 -> waitForPlayer(player);
            case 4 -> maintain(player);
            default -> { }
        }
    }

    private boolean hasArt() {
        var provider = artProvider();
        return provider == null || MmzObjectCatalogue.offset(provider.payload(), "scarabMappings") >= 0;
    }

    /** {@code timeZone} without bit 7, read live from the campaign owner. */
    private int liveTimeZone() {
        var tt = CdTimeTravel.of(services().gameModule());
        return tt != null ? tt.timeZone().nativeValue() & 0x7F : timeZone();
    }

    private int states() {
        MmzStageSignals signals = signals();
        return signals == null ? 0 : signals.objectState(stateKey);
    }

    private void setStates(int value) {
        MmzStageSignals signals = signals();
        if (signals != null) signals.setObjectState(stateKey, value);
    }

    // ---- routine 2 ---------------------------------------------------------------------------------------

    /** sub_20DCE4 (objects/r8/scarab.asm:44-55): the player within $140 of the spawner on both axes. */
    private boolean playerInRange(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return false;
        if (((sprite.getCentreY() - y + 0x140) & 0xFFFF) >= 0x280) return false;      // subi.w #-$140; subi.w #$280; bcc
        return ((sprite.getCentreX() - x + 0x140) & 0xFFFF) < 0x280;
    }

    private void waitForPlayer(PlayableEntity player) {
        if (playerInRange(player)) activate();                              // bcs loc_20DD08; else CheckObjectDespawn
    }

    /** loc_20DD08..loc_20DD9C (objects/r8/scarab.asm:60-116). */
    private void activate() {
        routine = 4;
        var objects = services().objectManager();
        int bits = states();
        if (objects == null) return;
        if ((bits & 1) == 0) {                                              // btst #0,(a6): the beetle is not gone
            ObjectSpawn s = new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype() & 1, 0, false, 0);
            body = new MmzScarabBody(s, this, (spawn.subtype() & 0x10) != 0 ? 0xFF : 0);    // sne.b d0 on bit 4
            objects.addDynamicObjectAfterCurrent(body);                      // jsr SpawnObjectAfter
        }
        if ((bits & 2) != 0) return;                                        // btst #1,(a6): the monitor is gone
        int kind = sub2();
        if ((kind & 0x80) != 0 || kind > 7) {                               // bmi / bhi loc_20DD9C
            setStates(bits | 2);
            return;
        }
        boolean right = (spawn.subtype() & 0x10) != 0;
        ObjectSpawn s = new ObjectSpawn((x + (right ? 0x18 : -0x18)) & 0xFFFF, (y - 4) & 0xFFFF, spawn.objectId(), 2, 0, false, 0);
        item = new MmzScarabItem(s, this, kind);
        objects.addDynamicObjectAfterCurrent(item);
    }

    // ---- routine 4 ---------------------------------------------------------------------------------------

    /** ScarabObject_6_Routine4 (objects/r8/scarab.asm:118-193): keep the two parts and the stored states consistent. */
    private void maintain(PlayableEntity player) {
        int bits = states();
        if ((bits & 1) == 0 && body != null && (body.isDestroyed() || body.despawned())) {
            if (body.killed()) {                                            // the beetle's ID changed: it is gone for good
                setStates(bits |= 1);
                if (item != null) {                                         // clr.w oXVel(a1) of the monitor
                    item.stop();
                } else if (body.grabbed()) {                                // move.w oVar32(a1),d6 of the dead beetle
                    body.releaseGrip();
                }
            }
            body = null;                                                    // clr.w oVar2E (a despawn clears it as well)
        }
        if ((bits & 2) == 0 && item != null && (item.isDestroyed() || item.despawned())) {
            if (item.gone()) setStates(bits |= 2);                          // loc_20DDDE: the monitor's ID changed
            item = null;
        }
        if ((bits & 3) == 3) {                                              // loc_20DDFC: both gone
            setDestroyed(true);                                             // loc_20E318: DeleteObject
            return;
        }
        if (bits == 0 && (body != null) != (item != null)) {               // one half has despawned without its state bit
            if (body != null) {                                             // loc_20DE3E
                if (body.grabbed()) body.releaseGrip();
                body.deleteOther();
                body = null;
            } else {                                                        // loc_20DE4A
                item.deleteOther();
                item = null;
            }
        }
        if (body == null && item == null) {                                 // loc_20DE58
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);            // loc_20E312: DespawnObject
        }
    }

    MmzScarabItem item() { return item; }
    MmzScarabBody body() { return body; }

    // ---- despawn: never drawn, so only the chunk distance decides (ScarabObject_6_Routine2, :36-43) ---------

    @Override protected boolean despawnsByCheckObjDespawn() { return routine != 4; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        if (!despawnsByCheckObjDespawn()) return false;
        int chunk = x & 0xFF80;
        int cameraChunk = (cameraX - 0x80) & 0xFF80;
        return ((chunk - cameraChunk) & 0xFFFF) > DESPAWN_RANGE;
    }

    public int routine() { return routine; }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
