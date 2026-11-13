package com.openggf.game.sonic2.competition;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2RomAddresses;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.Mapping;
import com.openggf.game.sonic2.competition.view.Sonic2CompetitionSpriteArtTable;
import com.openggf.game.sonic2.competition.view.Sonic2NativeDisplaySource;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractMonitorObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/** Native competition Obj2E; contents are latched on the first actual SST execution. */
public final class Sonic2CompetitionMonitorContents extends AbstractMonitorObjectInstance
        implements RewindRecreatable, Sonic2NativeDisplaySource,
        ObjectVisualFacing.NonDirectional {
    // Obj26_MapUnc_12D36 (s2.asm:26287): reuse the ROM-address catalogue.
    private static final int OBJ26_MAP_UNC_12D36 =
            Sonic2Constants.MAP_UNC_MONITOR_ADDR;
    // ArtTile_ArtNem_Powerups (s2.constants.asm:2612).
    private static final int ART_TILE_ART_NEM_POWERUPS = 0x0680;
    // Obj2E_Init (s2.asm:25745-25752): make_art_tile(ArtTile_ArtNem_Powerups,0,1).
    private static final int OBJ2E_ART_TILE_1P = ART_TILE_ART_NEM_POWERUPS | (0 << 13) | (1 << 15);

    private int breakerSlot;
    private int content;
    private boolean contentsInitialized;
    private int vIntRunCount;
    private int initializationFrame = -1;

    /** Read-only Obj2E observation for route outcome goals. */
    public int initializedContent() { return contentsInitialized ? content : 0; }
    public int initializationFrame() { return initializationFrame; }
    public int breakerSlot() { return breakerSlot; }

    public Sonic2CompetitionMonitorContents(int x, int y, int breakerSlot, PlayableEntity breaker) {
        this(new ObjectSpawn(x, y, Sonic2ObjectIds.MONITOR_CONTENTS, 9, 0, false, 0), breakerSlot, breaker);
    }

    private Sonic2CompetitionMonitorContents(ObjectSpawn spawn, int breakerSlot, PlayableEntity breaker) {
        super(spawn, "CompetitionMonitorContents");
        this.breakerSlot = breakerSlot;
        startIconRise(spawn.y(), breaker);
    }

    private Sonic2CompetitionMonitorContents(ObjectSpawn spawn) { this(spawn, 0, null); }

    public void delayFirstIconUpdateForPassedSlot() { iconPendingInit = true; }

    @Override public void update(int vIntRunCount, PlayableEntity ignoredObjectPassPlayer) {
        this.vIntRunCount = vIntRunCount;
        if (iconPendingInit) {
            iconPendingInit = false;
            return;
        }
        var session = Sonic2CompetitionObjectAccess.session(services());
        if (session == null) throw new IllegalStateException("Native monitor session is unavailable");
        if (!contentsInitialized) {
            // Obj2E_Init (s2.asm:25755-25776) reads Level_frame_counter, never V_int_run_count.
            initializationFrame = services().levelManager().getFrameCounter();
            content = session.monitorContents(services().levelManager().getFrameCounter(),
                    Sonic2CompetitionObjectAccess.host(services()).teleportOnly());
            contentsInitialized = true;
        }
        // The stable human slot survives removal/recreation of the original parent actor.
        if (!effectApplied) effectTarget = session.player(breakerSlot);
        updateDynamicSpawn(spawn.x(), iconSubY >> 8);
        updateIcon();
    }

    @Override protected void applyPowerup(PlayableEntity player) {
        Sonic2CompetitionObjectAccess.awardMonitor(services(), breakerSlot, content, spawn.x(), vIntRunCount);
    }

    @Override protected void onIconDeactivated() { ObjectLifetimeOps.expireDynamic(this); }
    @Override public int getPriorityBucket() { return RenderPriority.bucket(3); }
    @Override public boolean isHighPriority() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!isDestroyed() && iconActive && contentsInitialized)
            drawMonitorIcon(content + 1, spawn.x(), iconSubY >> 8);
    }

    @Override public ObjectSlot nativeDisplaySlot() {
        // Obj26_SpawnIcon (s2.asm:25690-25700) only creates the SST owner; its
        // mapping/display becomes valid when Obj2E_Init reaches loc_128C6.
        // Obj2E_Wait (s2.asm:26227-26230) deletes before DisplaySprite on expiry.
        if (isDestroyed() || !iconActive || !contentsInitialized) return null;
        try {
            var rom=services().romReader();
            int table=Sonic2RomAddresses.address(rom,OBJ26_MAP_UNC_12D36);
            // loc_128C6 (s2.asm:25776-25783): selected frame's first piece, static mappings.
            int pointer=table+rom.readS16BE(table+(content+1)*2)+2;
            var init=Sonic2CompetitionSpriteArtTable.row("monitor-icon").display();
            // Adjust2PArtPointer (s2.asm:31456-31464) halves only tile bits:
            // make_art_tile($680,0,1) -> $8000 | ($680 / 2) = $8340.
            int art=Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(OBJ2E_ART_TILE_1P);
            return new ObjectSlot(
                    Sonic2ObjectIds.MONITOR_CONTENTS,init.renderFlagsInit(),spawn.x()&0xFFFF,(iconSubY>>8)&0xFFFF,
                    init.widthPixels(),init.yRadius(),
                    new Mapping(pointer,0),art,null);
        } catch(java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    @Override public Sonic2CompetitionMonitorContents recreateForRewind(RewindRecreateContext ctx) {
        return new Sonic2CompetitionMonitorContents(ctx.spawn(), 0, null);
    }
}
