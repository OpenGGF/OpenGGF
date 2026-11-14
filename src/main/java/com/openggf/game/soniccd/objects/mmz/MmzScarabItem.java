package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdExplosion;
import com.openggf.game.soniccd.objects.common.CdMonitorItem;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The time monitor on the scarab's back ({@code ScarabObject} with {@code oSubtype} 2, ScarabObject_0_Routine0..8,
 * objects/r8/scarab.asm:492-570): a monitor with {@code MonitorTimeSprites} (tile $5A8, collision $C6), solid like one
 * ({@code sub_20E2F6} borrows ID $19), that the beetle moves along with itself. Jumped into from below it bounces
 * and falls back to the floor; rolled into from above it breaks (sound $96, a monitor item and an explosion) and
 * tells the beetle ({@code oColStatus} 1). The kind in {@code oSubtype2} (0-7) is the monitor's animation.
 */
final class MmzScarabItem extends MmzObject
        implements ReevaluatingSolidObject, TouchResponseProvider, TouchResponseListener, ObjectVisualFacing.NonDirectional {
    private final MmzScarab spawner;             // oVar2A
    /** The slot of {@code spawner} the native child stores and tests each turn ({@code cmpi.b #$2F,obj.id(a1)}). */
    private int spawnerSlot = -1;
    private final int kind;
    private int routine;
    private int phase;                           // oRoutine2: 0 resting, nonzero falling after a bounce
    private int yVel;
    private int xVel;
    private boolean gone;
    private boolean initialised;
    private int mapFrame;
    private CdAnimator animator;

    MmzScarabItem(ObjectSpawn spawn, MmzScarab spawner, int kind) {
        super(spawn, "ScarabItem");
        this.spawner = spawner;
        this.spawnerSlot = spawner == null ? -1 : spawner.getSlotIndex();
        this.kind = kind;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!slotHoldsId(spawnerSlot, 0x2F)) {                                 // cmpi.b #$2F,oID(a1); bne DeleteObject
            setDestroyed(true);
            gone = true;
            return;
        }
        switch (routine) {
            case 0 -> { init(); resting(player); }
            case 2 -> resting(player);
            case 4 -> broken(player);
            default -> { }
        }
    }

    /** ScarabObject_0_Routine0 (objects/r8/scarab.asm:507-529). */
    private void init() {
        routine = 2;
        initialised = true;
        yRadius = 0xE;
        width = 0xF;
        priority = 2;
        tileWord = CdObjectArtProvider.MONITOR_TILE;
        animator = animator(e -> e.monitorAnimations(), kind);
        y = (y + floorDistanceAt(y, yRadius)) & 0xFFFF;
    }

    /** ScarabObject_0_Routine2/6 (objects/r8/scarab.asm:531-565). */
    private void resting(PlayableEntity player) {
        if (!renderFlagOnScreen()) return;                           // tst.b oSprFlags; bpl Routine8
        if (phase != 0) {                                            // falling after a bounce
            addToY(yVel << 8);
            yVel += 0x38;                                            // MoveObjectFall
            int d1 = floorDistanceAt(y, yRadius);
            if (d1 < 0) {
                y = (y + d1) & 0xFFFF;
                yVel = 0;
                phase = 0;
            }
        } else if (player instanceof AbstractPlayableSprite) {
            solidObject(Entry.SOLID);                                // sub_20E2F6
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    /** ScarabObject_0_Routine4 (objects/r8/scarab.asm:567-604): the monitor breaks. */
    private void broken(PlayableEntity player) {
        MmzScarabBody beetle = spawner.body();
        if (beetle != null) beetle.touchedByMonitor();               // move.b #1,oColStatus(a6)
        if (player != null && solidObject(Entry.SOLID)) {            // sub_20E2F6; beq
            com.openggf.level.objects.ObjectManagerInternalAccess.releaseFromReevaluatingSolid(
                    services().objectManager(), player, this);       // jsr GetOffObject
        }
        services().playSfx(0x96);                                    // move.w #$96,d0; jsr PlayFmSound
        var objects = services().objectManager();
        if (objects != null) {
            objects.addDynamicObject(new CdMonitorItem(x, y, kind, 0));          // jsr SpawnObject: $1A
            objects.addDynamicObject(new CdExplosion(x, y, 1, false, 0, 0));    // jsr SpawnObject: $18, subtype 1
        }
        gone = true;
        setDestroyed(true);                                          // jmp DeleteObject
    }

    /** The beetle's {@code sub.w d6,oX(a1); sub.w d5,oY(a1)} and {@code oXVel} (objects/r8/scarab.asm:303-318). */
    void carry(int dx, int dy) {
        x = (x + dx) & 0xFFFF;
        y = (y + dy) & 0xFFFF;
        xVel = (short) (dx << 8);
    }

    /** {@code clr.w oXVel(a1)} (loc_20DDD0). */
    void stop() { xVel = 0; }

    boolean gone() { return gone; }

    void deleteOther() {
        setDestroyed(true);
    }

    @Override public int solidXVelocity() { return xVel; }
    @Override public Role solidRole() { return Role.MONITOR; }

    // ---- touch: loc_206BDE type 6 -> loc_206CEE (r8/player_object_collide.asm:loc_206CEE..loc_206D28) -------------

    @Override public int getCollisionFlags() { return routine == 2 ? 0xC6 : 0; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean usesS3kTouchSpecialPropertyResponse() { return routine == 2; }
    @Override public boolean requiresContinuousTouchCallbacks() { return true; }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        if (routine != 2 || !(playerEntity instanceof AbstractPlayableSprite player) || player.isCpuControlled()) return;
        if (player.getYSpeed() < 0) {
            if (player.getCentreY() - 0x10 < y) return;              // cmp.w oY(a1),d0; bcs
            player.setYSpeed((short) -player.getYSpeed());
            yVel = -0x180;
            if (phase == 0) phase += 4;                              // tst.b oRoutine2(a1); addq.b #4
            return;
        }
        if (player.getAnimationId() != CdSonicAnimation.ROLL.id()) return;   // cmpi.b #2,oAnim(a0); bne
        player.setYSpeed((short) -player.getYSpeed());
        routine = 4;                                                 // addq.b #2,oRoutine(a1) (Routine2 -> Routine4)
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    /** ScarabObject_0_Routine8 draws before its CheckObjectDespawn (objects/r8/scarab.asm:554-557). */
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    /** Despawned by its range check, after which it has cleared the spawner's {@code var_30} (:558-561). */
    private boolean despawned;

    boolean despawned() { return despawned; }

    @Override
    public void onUnload() {
        super.onUnload();
        despawned = true;
    }

    public int mapFrame() { return mapFrame; }
    public int kind() { return kind; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawFrame(CdObjectArtProvider.MONITOR, mapFrame, false, false);
    }
}
