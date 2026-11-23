package com.openggf.game.sonic2.debug;

import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.data.RomByteReader;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameServices;
import com.openggf.game.internal.LevelDebugPlacementProvider;
import com.openggf.game.sonic2.Sonic2RomAddresses;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.level.LevelManager;
import com.openggf.level.metadata.ObjectPreview;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/** Cartridge catalogues for the retail and lock-on levels. */
public final class Sonic2LevelDebugPlacement implements LevelDebugPlacementProvider {
    private static final int RETAIL_TABLE = 0x41D0C;
    private static final int LOCK_ON_TABLE = 0x33A2FC;
    private static final int ZONES = 17;
    private final RomByteReader lockOnImage;
    private int cachedZone = -1;
    private com.openggf.data.Rom cachedRetailRom;
    private List<Entry> cachedEntries = List.of();

    public Sonic2LevelDebugPlacement() {
        this(null);
    }

    public Sonic2LevelDebugPlacement(RomByteReader lockOnImage) {
        this.lockOnImage = lockOnImage;
    }

    @Override
    public boolean canEnter(LevelManager level, AbstractPlayableSprite player,
                            PlayerInputState input, boolean cheatEnabled) {
        // Obj01_Control (s2.asm:36223-36230), Obj01_Hurt (s2.asm:38164-38171),
        // and Obj01_Dead (s2.asm:38255-38262) read the physical P1 press.
        return cheatEnabled && (input.actionPressedMask() & InputActionMasks.ACTION_B) != 0;
    }

    @Override
    public List<Entry> entries(LevelManager level) {
        int zone = level.getRomZoneId();
        if (zone < 0 || zone >= ZONES) return List.of();
        try {
            var retailRom = lockOnImage == null ? GameServices.rom().getRom() : null;
            if (zone != cachedZone || retailRom != cachedRetailRom) {
                var reader = lockOnImage != null ? lockOnImage : RomByteReader.fromRom(retailRom);
                int table = lockOnImage != null ? LOCK_ON_TABLE : Sonic2RomAddresses.address(reader, RETAIL_TABLE);
                cachedEntries = decode(reader, table, zone);
                cachedZone = zone;
                cachedRetailRom = retailRom;
            }
            return cachedEntries;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read debug placement catalogue", e);
        }
    }

    // DebugObjectLists (s2.asm:88869-88887). The lock-on program at c336fede
    // aliases every zone to DbgObjList_Def through DebugObjectLists on lines 92208–92271.
    static List<Entry> decode(RomByteReader rom, int table, int zone) {
        int list = table + rom.readU16BE(table + zone * 2);
        int count = rom.readU16BE(list);
        if (count == 0 || count > 0xFF || list + 2 + count * 8 > rom.size()) {
            throw new IllegalArgumentException("Invalid debug placement list at " + Integer.toHexString(list));
        }
        var result = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) {
            int row = list + 2 + i * 8;
            int id = rom.readU8(row);
            int subtype = rom.readU8(row + 4);
            int frame = rom.readU8(row + 5);
            result.add(new Entry(id, subtype, preview(id, subtype, frame)));
        }
        return List.copyOf(result);
    }

    private static Preview preview(int id, int subtype, int frame) {
        if (id == Sonic2ObjectIds.RING || id == Sonic2ObjectIds.LAYER_SWITCHER) {
            return (level, x, y, flipX, flipY) -> level.getRingManager().drawRingFrameAt(x, y, frame);
        }
        // The stock fixBugs=0 waterfall row deliberately uses blank frame zero.
        if (id == Sonic2ObjectIds.EHZ_WATERFALL && frame == 0) {
            return (level, x, y, flipX, flipY) -> { };
        }
        var spawn = new ObjectSpawn(0, 0, id, subtype, 0, false, 0);
        ObjectPreview pose = Sonic2ObjectRegistry.staticPlacementPreview(spawn);
        return (level, x, y, flipX, flipY) -> {
            var art = level.getGameModule().getObjectArtProvider();
            if (art == null) return;
            for (var part : pose.parts()) {
                var renderer = art.getRenderer(part.artKey());
                if (renderer != null) {
                    renderer.drawFrameIndex(part.frame(),
                            x + (flipX ? -part.x() : part.x()),
                            y + (flipY ? -part.y() : part.y()),
                            flipX ^ part.hFlip(), flipY ^ part.vFlip(), part.paletteLine());
                }
            }
        };
    }

    // HudUpdate (s2.asm:87867-87874), loc_40F18 (s2.asm:88000-88023).
    @Override public boolean preventsTimeOver() { return true; }

    @Override
    public void initializeCursor(LevelManager level, AbstractPlayableSprite player) {
        // Debug_Init (s2.asm:88592-88613). fixBugs=0 retains airborne status.
        var camera = player.currentCamera();
        if (level.getRomZoneId() == Sonic2ZoneConstants.ROM_ZONE_SCZ) {
            camera.setMinX((short) 0);
            camera.setMaxX((short) 0x3FFF);
        }
        NativePositionOps.writeYPosPreserveSubpixel(player, player.getCentreY() & 0x7FF);
        camera.setY((short) (camera.getY() & 0x7FF));
    }

    @Override
    public void resetPlayer(AbstractPlayableSprite player) {
        // Debug_ResetPlayerStats (s2.asm:88825-88846), fixBugs=0 replaces
        // status with in_air, including clearing the underwater bit.
        player.setDead(false);
        player.setDeathCountdown(0);
        player.clearDrowningDeathState();
        player.setHurt(false);
        player.setObjectRoutineOverride(null);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setOnObject(false);
        player.setSpindash(false);
        player.setSpindashCounter((short) 0);
        player.setPinballMode(false);
        player.setPinballSpeedLock(false);
        player.setRollingFlagPreserveRadii(false);
        player.setRollingJump(false);
        player.applyCustomRadii(9, 19);
        player.clearUnderwaterStatusPreserveWaterPhysics();
        player.setDirection(Direction.RIGHT);
        player.setAir(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setSubpixelRaw(0, 0);
        player.setForcedAnimationId(-1);
        player.setAnimationId(player.resolveAnimationId(CanonicalAnimation.WALK));
        player.setAnimationFrameIndex(0);
    }
}
