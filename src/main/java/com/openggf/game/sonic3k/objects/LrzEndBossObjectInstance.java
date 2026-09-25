package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.LrzBossActState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.level.resources.PlcParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Obj_LRZEndBoss: damage comes exclusively from the floating mines' status-bit publication. */
public final class LrzEndBossObjectInstance extends AbstractObjectInstance
        implements ZeroArgRewindRecreatable, TouchResponseProvider, RomObjectCodePointerProvider {
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0,0,0,0,0,0);
    private int entry, routine, timer, callback, shots, shotInterval, hits, flash;
    private boolean hitPending, defeated, childrenReleased, flipped;
    private long bossArtOrdinal = -1, debrisArtOrdinal = -1;

    public LrzEndBossObjectInstance() {
        super(new ObjectSpawn(0,0,0,0,0,false,0), "LRZEndBoss");
    }
    private LrzBossActState state() {
        return S3kRuntimeStates.currentLrz(services().zoneRuntimeRegistry()).orElseThrow().bossAct();
    }
    @Override public void update(int vIntRunCount, PlayableEntity player) {
        bossArtOrdinal = claimReadyArt(bossArtOrdinal);
        debrisArtOrdinal = claimReadyArt(debrisArtOrdinal);
        if (entry == 0) {
            state().setBossSlot(getSlotIndex()); entry = 1;
            bossArtOrdinal = queueArt(0x1715F2, 0x3CC);
            debrisArtOrdinal = queueArt(0x1714C0, 0x487);
            loadRawPlc(0x83D64);
            loadPalette();
            return;
        }
        if (entry == 1) {
            if (state().entryPlatformReady()) {
                entry = 2; timer = 120; services().gameState().setCurrentBossId(1);
            }
            return;
        }
        if (entry == 2) {
            timer = (short)(timer - 1);
            if (timer < 0) { entry = 3; services().playMusic(Sonic3kMusic.BOSS.id); }
            return;
        }
        if (entry == 4) {
            timer = (short)(timer - 1);
            if (timer >= 0) { motion.y = (motion.y + 1) & 65535; return; }
            entry = 5; childrenReleased = true;
            services().gameState().setEndOfLevelActive(true);
            services().gameState().setCurrentBossId(0);
            spawnFreeChild(LrzEndBossEggCapsule::new);
            spawnFreeChild(LrzEndBossPaletteRestore::new);
            loadRawPlc(0x83D74);
            return;
        }
        if (entry == 5) {
            if (!services().gameState().isEndOfLevelActive()) {
                entry = 6;
                spawnFreeChild(() -> new SongFadeTransitionInstance(90, Sonic3kMusic.LRZ2.id));
                restorePlayers();
                spawnFreeChild(() -> new com.openggf.game.sonic3k.objects.bosses.HczEndBossGradualMaxXExtender(0,0,0xEC0));
                spawnFreeChild(() -> new S3kStartNewLevelObjectInstance(new ObjectSpawn(0xFE8,0x5E0,0xB3,0x2D,0,false,0)));
            }
            return;
        }
        if (entry == 6) return;
        switch (routine) {
            case 0 -> {
                routine = 2; motion.x = 0xB10; motion.y = 0x640; motion.yVel = -0x580; hits = 14;
                services().playSfx(Sonic3kSfx.BOSS_MAGMA.id);
                spawnTable(0x7A18C, 2);
            }
            case 2 -> {
                SubpixelMotion.moveSprite(motion,0x38);
                if (motion.yVel >= 0 && (getY() & 65535) >= 0x600) {
                    motion.y = 0x600; routine = 4; shots = 3; timer = shotInterval = 0x7F; callback = 0;
                    int direction = flipped ? 1 : 0xFFFF;
                    state().setStreamDirection(direction); state().setLavaDirection(direction);
                    spawnFreeChild(LrzEndBossPlatformStream::new);
                }
            }
            case 4 -> { state().setDriftClock(state().driftClock()+1); followLava(); waitCallback(); }
            case 6 -> {
                followLava();
                if (state().lavaAmplitude() == 0) {
                    routine = 8; state().setStreamDirection(state().streamDirection() & 0xFF00);
                    motion.yVel = 0x100; timer = 0x3F; callback = 2;
                }
            }
            case 8 -> { SubpixelMotion.moveSprite2(motion); waitCallback(); }
            case 10 -> waitCallback();
            default -> throw new IllegalStateException("LRZ end-boss routine " + routine);
        }
        consumeMineHit();
    }
    private void waitCallback() {
        timer = (short)(timer - 1);
        if (timer >= 0) return;
        switch (callback) {
            case 0 -> {
                shots = (byte)(shots - 1);
                if (shots < 0) { timer = 0xF7; callback = 1; }
                else {
                    shotInterval = (short)(shotInterval - 0x10); timer = shotInterval;
                    services().playSfx(Sonic3kSfx.BOSS_PROJECTILE.id); spawnTable(0x7A19A,2);
                }
            }
            case 1 -> {
                routine = 6; state().setLavaDirection(state().lavaDirection() & 0xFF00); state().setDriftClock(0);
            }
            case 2 -> { routine = 10; timer = 0x3F; callback = 3; }
            case 3 -> {
                routine = 2; motion.yVel = -0x580; flipped = !flipped;
                motion.x = flipped ? 0xA30 : 0xB10; services().playSfx(Sonic3kSfx.BOSS_MAGMA.id);
            }
            default -> throw new IllegalStateException("LRZ callback " + callback);
        }
    }
    private void followLava() {
        motion.y = 0x600 - (state().lavaHeight(((getX()-0x9E0)&65535)>>>1)-0x30);
    }
    /** loc_79EC4 calls this after its half-open parent-relative range test. */
    boolean publishMineHit() {
        if (hitPending || defeated) return false;
        hitPending = true; return true;
    }
    private void consumeMineHit() {
        if (!hitPending) return;
        if (flash == 0) {
            hits = (hits - 1) & 255;
            if (hits == 0) {
                defeated = true; entry = 4; timer = 0x7F;
                state().setStreamDirection(state().streamDirection() & 0xFF00); state().setDriftClock(0);
                state().requestBackgroundExit();
                spawnChild(() -> new LrzEndBossExplosion(this,4));
                state().setLavaDirection(state().lavaDirection() & 0xFF00);
                if (services().levelGamestate()!=null) services().levelGamestate().pauseTimer();
                services().gameState().addScore(1000);
                return;
            }
            flash = 0x20; services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        }
        int address = (flash & 1) == 0 ? 0x79FF6 : 0x79FEE;
        try {
            int[] colors = new int[4];
            for(int i=0;i<4;i++) colors[i]=services().rom().read16BitAddr(address+2*i);
            S3kPaletteWriteSupport.applyColors(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),
                    services().graphicsManager(),"lrz.endboss.hit",200,1,new int[]{4,12,13,14},colors);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
        if (--flash == 0) hitPending = false;
    }
    private void spawnTable(int address,int count) {
        try {
            for(int i=0;i<count;i++) {
                int row=address+2+6*i;
                int code=services().rom().read32BitAddr(row);
                byte[] offset=services().rom().readBytes(row+4,2);
                var child=spawnChild(() -> new LrzEndBossChild(this,code,offset[0],offset[1]));
                if(child==null || child.getSlotIndex()<0 || child.isDestroyed()) break;
            }
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void restorePlayers() {
        for(var p:services().playerQuery().playersFor(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
            if(p instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite)
                {
                com.openggf.sprites.playable.ObjectControlState.none().applyTo(sprite);
                sprite.setControlLocked(false); sprite.clearAirForNativeControlRestore();
                // Restore_PlayerControl/2 publishes anim/prev_anim=$0505 and clears animation clocks.
                sprite.setAnimationId(5); sprite.setForcedAnimationId(-1);
                sprite.getAnimationManager().publishPreviousAnimationId(5);
                sprite.setAnimationFrameIndex(0); sprite.setAnimationTick(0);
                if(sprite==services().playerQuery().mainPlayerOrNull()) sprite.clearLogicalInputState();
            }
        }
    }
    private void loadPalette() {
        try {
            S3kPaletteWriteSupport.applyLine(services().paletteOwnershipRegistryOrNull(),services().currentLevel(),
                    services().graphicsManager(),"lrz.endboss.setup",190,1,services().rom().readBytes(0x7A1EE,32),true);
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void loadRawPlc(int address) {
        try {
            var rom=services().rom(); int count=(short)rom.read16BitAddr(address)+1;
            var entries=new ArrayList<PlcParser.PlcEntry>();
            for(int i=0;i<count;i++) entries.add(new PlcParser.PlcEntry(rom.read32BitAddr(address+2+6*i)&0xFFFFFF,
                    rom.read16BitAddr(address+6+6*i)/32));
            if(services().currentLevel() instanceof Sonic3kLevel level) {
                var modified=Sonic3kPlcLoader.applyToLevel(new PlcParser.PlcDefinition(-1,entries),level,rom);
                Sonic3kPlcLoader.refreshAffectedRenderers(modified,services().levelManager());
            }
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private long queueArt(int address,int tile) {
        try {
            var queue=services().kosinskiModuleQueue(); if(queue==null) return -1;
            Sonic3kPlcLoader.bindRuntimePatternDmaTarget(queue,services());
            queue.enqueue(services().rom(),address,tile*32);
            return com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.from(services()).moduleQueue()
                    .queue(services().rom(),address,tile).ordinal();
        } catch(IOException failure) { throw new UncheckedIOException(failure); }
    }
    private long claimReadyArt(long ordinal) {
        if(ordinal<0) return ordinal;
        var queue=com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator.from(services()).moduleQueue();
        var handle=services().hardwareTiming().pendingHandle(com.openggf.game.timing.HardwareWorkKind.KOS_MODULE_QUEUE,ordinal).orElseThrow();
        if(!queue.isReady(handle)) return ordinal;
        queue.claim(handle); return -1;
    }
    boolean flipped() { return flipped; }
    boolean hitPending() { return hitPending; }
    boolean defeated() { return defeated; }
    boolean childrenReleased() { return childrenReleased; }
    int hits() { return hits; }
    @Override public int getX() { return (short)motion.x; }
    @Override public int getY() { return (short)motion.y; }
    @Override public int getPriorityBucket() { return 5; }
    @Override public int getOnScreenHalfWidth() { return 0x28; }
    @Override public int getOnScreenHalfHeight() { return 0x2C; }
    @Override public int getCollisionFlags() { return entry==3 && routine!=0 ? 0xB8 : 0; }
    @Override public int getCollisionProperty() { return hits; }
    @Override public int romObjectCodePointerHighWord() { return 7; }
    // These ROM routines own their deletion; none calls placement-range unloading.
    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if(entry<3 || entry>=5 || routine==0) return;
        var renderer=getRenderer(Sonic3kObjectArtKeys.LRZ_END_BOSS);
        if(renderer!=null && renderer.isReady()) renderer.drawFrameIndex(0,getX(),getY(),flipped,false);
    }
}
