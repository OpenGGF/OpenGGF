package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;

import java.io.IOException;

/**
 * Sheets of the shared objects, registered by {@link SharedObjects}. A shared object's tiles come
 * from the stage's main list (PLC 1, Level/Main.asm:163-165) in some zones and from the title card's
 * end cue in others (PLC 2, Title Card/Main.asm:145-148): the block's art is {@code BlockGfxC} in
 * Tidal Tempest's main list (r4/gfx_lists_2c.asm:32-49) but in Stardust Speedway's section list
 * (r7/gfx_lists_1c.asm:48-62), and Quartz Quadrant's falling block is {@code RockGfxC} in its main
 * list (r5/gfx_lists_1c.asm:33-49). Sheets are windows into the VRAM the stage has once both cues
 * are in, the state gameplay draws from; section update lists are not modelled.
 */
public final class SharedObjectArt {
    public static final String BLOCK = "shared.block";
    public static final String FALLING_BLOCK = "shared.fallingblock";
    public static final String CRUSHER = "shared.crusher";
    public static final String COLLAPSE_FLOOR = "shared.collapsefloor";
    public static final String QQZ_SPIKES = "shared.qqzspikes";
    public static final String SWITCH = "shared.switch";
    public static final String WWZ_DOOR = "shared.wwzdoor";
    public static final String WWZ_PLATFORM = "shared.wwzplatform";
    public static final String QQZ_PLATFORM = "shared.qqzplatform";
    public static final String WWZ_CRUSHER = "shared.wwzcrusher";
    public static final String MMZ_TUBE_DOOR = "shared.mmztubedoor";
    public static final String DANGO_1 = "shared.dango1";
    public static final String DANGO_2 = "shared.dango2";
    public static final String SPIKE_BALL = "shared.spikeball";
    public static final String HOTARU_1 = "shared.hotaru1";
    public static final String HOTARU_2 = "shared.hotaru2";
    public static final String HOTARU_SPARK = "shared.hotaruspark";
    public static final String SWING = "shared.swing";
    public static final String MOVING_SPRING = "shared.movingspring.";
    public static final String WWZ_TUBE_DOOR = "shared.wwztubedoor";
    public static final String WWZ_SEESAW = "shared.wwzseesaw";
    public static final String MMZ_SEESAW = "shared.mmzseesaw";
    public static final String SPIKE_CHAIN = "shared.spikechain";
    public static final String SPIKE_CHAIN_BALL = "shared.spikechainball";
    /** Key prefixes; the tile word is appended (SetObjectSpriteTile picks one of several). */
    public static final String H_DOOR = "shared.hdoor.";
    public static final String H_DOOR_SPLASH = "shared.hdoorsplash.";
    public static final String TUNNEL_SPLASH = "shared.tunnelsplash.";
    public static final String WWZ_POH_BEE_1 = "shared.wwzpohbee1";
    public static final String WWZ_POH_BEE_2 = "shared.wwzpohbee2";
    public static final String WWZ_POH_MISSILE = "shared.wwzpohmissile";
    /** PLC 2: the title card's slide-out cue (Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;

    private SharedObjectArt() { }

    static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        var reader = art.image().reader();
        CdStageVram vram = null;
        int block = SharedObjectCatalogue.offset(payload, "blockMappings");
        if (block >= 0) {
            vram = gameplayVram(art);
            art.register(BLOCK, art.sheet(reader, block, SharedObjectCatalogue.offset(payload, "blockTile"), vram));
        }
        int falling = SharedObjectCatalogue.offset(payload, "fallingBlockMappings");
        if (falling >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(FALLING_BLOCK, art.sheet(reader, falling,
                    SharedObjectCatalogue.offset(payload, "fallingBlockTile"), vram));
        }
        // Metallic Madness's CrusherGfx is in its main list (r8/gfx_lists_2a.asm:33-50).
        int crusher = SharedObjectCatalogue.offset(payload, "crusherMappings");
        if (crusher >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(CRUSHER, art.sheet(reader, crusher, SharedObjectCatalogue.offset(payload, "crusherTile"), vram));
        }
        // Quartz Quadrant's CollapseFloorGfxC is in its section list (r5/gfx_lists_1c.asm:51-66); the act
        // selects the tile word (objects/r5/collapse_floor.asm:27-33).
        int collapse = SharedObjectCatalogue.offset(payload, "collapseFloorMappings");
        if (collapse >= 0) {
            if (vram == null) vram = gameplayVram(art);
            String tile = payload.stage().actNumber() == 3 ? "collapseFloorTileAct3" : "collapseFloorTile";
            art.register(COLLAPSE_FLOOR, art.sheet(reader, collapse, SharedObjectCatalogue.offset(payload, tile), vram));
        }
        // Quartz Quadrant's SpikesHV4Gfx is in its section list (r5/gfx_lists_1c.asm:51-66).
        int spikes = SharedObjectCatalogue.offset(payload, "qqzSpikesMappings");
        if (spikes >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(QQZ_SPIKES, art.sheet(reader, spikes, SharedObjectCatalogue.offset(payload, "qqzSpikesTile"), vram));
        }
        int switches = SharedObjectCatalogue.offset(payload, "switchMappings");
        if (switches < 0) switches = SharedObjectCatalogue.offset(payload, "switchByActMappings");
        if (switches >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(SWITCH, art.sheet(reader, switches, switchTile(payload), vram));
        }
        int door = SharedObjectCatalogue.offset(payload, "wwzDoorMappings");
        if (door >= 0) {
            if (vram == null) vram = gameplayVram(art);
            String tile = payload.stage().actNumber() == 3 ? "wwzDoorTileAct3" : "wwzDoorTile";
            art.register(WWZ_DOOR, art.sheet(reader, door, SharedObjectCatalogue.offset(payload, tile), vram));
        }
        int platform = SharedObjectCatalogue.offset(payload, "wwzPlatformMappings");
        if (platform >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(WWZ_PLATFORM, art.sheet(reader, platform, SharedObjectCatalogue.offset(payload, "wwzPlatformTile"), vram));
        }
        // Quartz Quadrant's PlatformGfx is in its main list (r5/gfx_lists_1a.asm:37); act 3 has its own tile word
        // (objects/r5/platform.asm:168-172).
        int qqzPlatform = SharedObjectCatalogue.offset(payload, "qqzPlatformMappings");
        if (qqzPlatform >= 0) {
            if (vram == null) vram = gameplayVram(art);
            String tile = payload.stage().actNumber() == 3 ? "qqzPlatformTileAct3" : "qqzPlatformTile";
            art.register(QQZ_PLATFORM, art.sheet(reader, qqzPlatform, SharedObjectCatalogue.offset(payload, tile), vram));
        }
        // Wacky Workbench's crusher (its tile word is an immediate, objects/r6/crusher.asm:35).
        int wwzCrusher = SharedObjectCatalogue.offset(payload, "wwzCrusherMappings");
        if (wwzCrusher >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(WWZ_CRUSHER, art.sheet(reader, wwzCrusher, SharedObjectCatalogue.offset(payload, "wwzCrusherTile"), vram));
        }
        // Metallic Madness's tube door (tile $33C is an immediate, objects/r8/tube_door.asm:91).
        int mmzTubeDoor = SharedObjectCatalogue.offset(payload, "mmzTubeDoorMappings");
        if (mmzTubeDoor >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(MMZ_TUBE_DOOR, art.sheet(reader, mmzTubeDoor, SharedObjectCatalogue.offset(payload, "mmzTubeDoorTile"), vram));
        }
        // Dango's tiles (the tile word is the past's or the other periods', objects/r7/dango.asm:46-50).
        int dango1 = SharedObjectCatalogue.offset(payload, "dangoMappings1");
        if (dango1 >= 0) {
            if (vram == null) vram = gameplayVram(art);
            String tile = com.openggf.game.soniccd.level.CdLevel.nativeTimeZone(payload.stage().period()) == 0 ? "dangoTilePast" : "dangoTile";
            int word = SharedObjectCatalogue.offset(payload, tile);
            art.register(DANGO_1, art.sheet(reader, dango1, word, vram));
            art.register(DANGO_2, art.sheet(reader, SharedObjectCatalogue.offset(payload, "dangoMappings2"), word, vram));
        }
        // Metallic Madness's Dango: one tile word for every period (objects/r8/dango.asm:41).
        int mmzDango1 = SharedObjectCatalogue.offset(payload, "mmzDangoMappings1");
        if (mmzDango1 >= 0) {
            if (vram == null) vram = gameplayVram(art);
            int word = SharedObjectCatalogue.offset(payload, "mmzDangoTile");
            art.register(DANGO_1, art.sheet(reader, mmzDango1, word, vram));
            art.register(DANGO_2, art.sheet(reader, SharedObjectCatalogue.offset(payload, "mmzDangoMappings2"), word, vram));
        }
        // Stardust Speedway's SpikeBallGfx is in section list 0 (r7/gfx_lists_1a.asm:57), PLC 2.
        int spikeBall = SharedObjectCatalogue.offset(payload, "spikeBallMappings");
        if (spikeBall >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(SPIKE_BALL, art.sheet(reader, spikeBall, SharedObjectCatalogue.offset(payload, "spikeBallTile"), vram));
        }
        // H door, its splash and the tunnel splash: sprite tiles from SetObjectSpriteTile indices $C and $D
        // (objects/r1/h_door.asm:72, 152) and tunnel_splash.asm:22-25.
        int hDoor = SharedObjectCatalogue.offset(payload, "hDoorMappings");
        if (hDoor >= 0) {
            if (vram == null) vram = gameplayVram(art);
            for (int tile : art.objectTileIds(0xC)) art.register(H_DOOR + tile, art.sheet(reader, hDoor, tile, vram));
            int splash = SharedObjectCatalogue.offset(payload, "hDoorSplashMappings");
            for (int tile : art.objectTileIds(0xD)) art.register(H_DOOR_SPLASH + tile, art.sheet(reader, splash, tile, vram));
            int tunnel = SharedObjectCatalogue.offset(payload, "tunnelSplashMappings");
            for (String column : new String[] {"tunnelSplashTilePresent", "tunnelSplashTilePast"}) {
                int tile = SharedObjectCatalogue.offset(payload, column);
                art.register(TUNNEL_SPLASH + tile, art.sheet(reader, tunnel, tile, vram));
            }
        }
        // Wacky Workbench's spike chain (SpikeChainSprites1 for the parent, SpikeChainSprites2 for the balls).
        int chain = SharedObjectCatalogue.offset(payload, "spikeChainMappings");
        if (chain >= 0) {
            if (vram == null) vram = gameplayVram(art);
            int tile = SharedObjectCatalogue.offset(payload, "spikeChainTile");
            art.register(SPIKE_CHAIN, art.sheet(reader, chain, tile, vram));
            art.register(SPIKE_CHAIN_BALL, art.sheet(reader, SharedObjectCatalogue.offset(payload, "spikeChainChildMappings"), tile, vram));
        }
        // Wacky Workbench's tube door.
        int tubeDoor = SharedObjectCatalogue.offset(payload, "wwzTubeDoorMappings");
        if (tubeDoor >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(WWZ_TUBE_DOOR, art.sheet(reader, tubeDoor, SharedObjectCatalogue.offset(payload, "wwzTubeDoorTile"), vram));
        }
        // The moving spring (tile from SetObjectSpriteTile index $E, objects/spring.asm:44-45).
        int movingSpring = SharedObjectCatalogue.offset(payload, "movingSpringMappings");
        if (movingSpring >= 0) {
            if (vram == null) vram = gameplayVram(art);
            for (int tile : art.objectTileIds(0xE)) art.register(MOVING_SPRING + tile, art.sheet(reader, movingSpring, tile, vram));
        }
        // Stardust Speedway's swing (SwingGfx, r7/gfx_lists_1a.asm:56, PLC 2).
        int swing = SharedObjectCatalogue.offset(payload, "swingMappings");
        if (swing >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(SWING, art.sheet(reader, swing, SharedObjectCatalogue.offset(payload, "swingTile"), vram));
        }
        // Stardust Speedway's Hotaru (HotaruGfx in section list 0, r7/gfx_lists_2d.asm:61, PLC 2): the tile word is the
        // spawner's, by act (objects/r7/hotaru.asm:44-50).
        int hotaru1 = SharedObjectCatalogue.offset(payload, "hotaruMappings1");
        if (hotaru1 >= 0) {
            if (vram == null) vram = gameplayVram(art);
            int tile = SharedObjectCatalogue.offset(payload, payload.stage().actNumber() == 3 ? "hotaruTileAct3" : "hotaruTile");
            art.register(HOTARU_1, art.sheet(reader, hotaru1, tile, vram));
            art.register(HOTARU_2, art.sheet(reader, SharedObjectCatalogue.offset(payload, "hotaruMappings2"), tile, vram));
            art.register(HOTARU_SPARK, art.sheet(reader, SharedObjectCatalogue.offset(payload, "hotaruSparkMappings"), tile, vram));
        }
        // Wacky Workbench's seesaw (the tile word is an immediate, objects/r6/seesaw.asm:31).
        int wwzSeesaw = SharedObjectCatalogue.offset(payload, "wwzSeesawMappings");
        if (wwzSeesaw >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(WWZ_SEESAW, art.sheet(reader, wwzSeesaw, SharedObjectCatalogue.offset(payload, "wwzSeesawTile"), vram));
        }
        // Metallic Madness's seesaw (tile $3A0 for the body and platform, $43A0 for the ball: immediates, objects/r8/seesaw.asm:36, 48).
        int mmzSeesaw = SharedObjectCatalogue.offset(payload, "mmzSeesawMappings");
        if (mmzSeesaw >= 0) {
            if (vram == null) vram = gameplayVram(art);
            art.register(MMZ_SEESAW, art.sheet(reader, mmzSeesaw, SharedObjectCatalogue.offset(payload, "mmzSeesawTile"), vram));
        }
        // Wacky Workbench's PohBeeGfx is in its main list (r6/gfx_lists_2d.asm:37).
        int pohBee1 = SharedObjectCatalogue.offset(payload, "wwzPohBeeMappings1");
        if (pohBee1 >= 0) {
            if (vram == null) vram = gameplayVram(art);
            int tile = SharedObjectCatalogue.offset(payload, "wwzPohBeeTile");
            art.register(WWZ_POH_BEE_1, art.sheet(reader, pohBee1, tile, vram));
            art.register(WWZ_POH_BEE_2, art.sheet(reader, SharedObjectCatalogue.offset(payload, "wwzPohBeeMappings2"), tile, vram));
            art.register(WWZ_POH_MISSILE, art.sheet(reader, SharedObjectCatalogue.offset(payload, "wwzPohMissileMappings"), tile, vram));
        }
    }

    /**
     * The switch's tile word: Wacky Workbench's single immediate (objects/r6/switch.asm:3-11), or
     * Metallic Madness's act-3 choice (cmpi.b #2,act, objects/r8/switch.asm:3-14).
     */
    static int switchTile(CdStagePayload payload) {
        int tile = SharedObjectCatalogue.offset(payload, "switchTile");
        if (tile >= 0) return tile;
        return SharedObjectCatalogue.offset(payload, payload.stage().actNumber() == 3 ? "switchByActTileAct3" : "switchByActTile");
    }

    /** Level-start VRAM plus PLC 2 where the stage has one. */
    private static CdStageVram gameplayVram(CdObjectArtProvider art) throws IOException {
        CdStageVram vram = art.vram().copy();
        int plcLists = CdStageCatalogue.entry(art.payload()).plcLists();
        if (CdStageVram.hasPlc(art.image(), plcLists, TITLE_CARD_PLC)) vram.loadPlc(art.image(), plcLists, TITLE_CARD_PLC);
        return vram;
    }
}
