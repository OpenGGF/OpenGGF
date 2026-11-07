package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.objects.ObjectArtKeys;
import java.util.Map;

/** REV01 Init display operands, before Adjust2PArtPointer. No virtual atlas addresses. */
public final class Sonic2CompetitionSpriteArtTable {
    private Sonic2CompetitionSpriteArtTable() { }
    public record Entry(int mappingsAddress, int artTile1P, int widthPixels,
                        int renderFlagsInit, int yRadius, String expectedArtKey, String initCitation) { }

    // Neither Init writes y_radius. Flags 4 select the approximate Y checks, which never read it:
    // BuildSprites_P1_ApproxYCheck (s2.asm:31075-31082), BuildSprites_P2_ApproxYCheck (s2.asm:31212-31219).
    // Zero here is an unused display input, not a claim about inherited SST contents.
    // Obj27_Init (s2.asm:46722-46732); ArtTile_ArtNem_Explosion (s2.constants.asm:2601).
    // Obj29_Init (s2.asm:24918-24926); ArtTile_ArtNem_Numbers (s2.constants.asm:2609).
    private static final Map<Integer,Entry> ENTRIES = Map.of(
            0x27, new Entry(Sonic2Constants.MAP_UNC_EXPLOSION_ADDR, 0x05A4, 0x0C, 4, 0,
                    ObjectArtKeys.EXPLOSION, "Obj27_Init (s2.asm:46722-46732)"),
            0x29, new Entry(Sonic2Constants.MAP_UNC_POINTS_ADDR, 0x84AC, 8, 4, 0,
                    ObjectArtKeys.POINTS, "Obj29_Init (s2.asm:24918-24926)"));

    public record Pending(String name, String initCitation, String reason) { }

    /** Qualification blockers until each actual ordinary draw and native SST projection is certified. */
    public static final Map<Integer,Pending> PENDING_ADOPTION = Map.ofEntries(
            Map.entry(0x03, new Pending("LayerSwitcher", "Obj03_Init (s2.asm:45626)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x06, new Pending("Spiral", "Obj06_Init (s2.asm:47122)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x0D, new Pending("GoalPlate", "Obj0D_Init (s2.asm:34636)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x11, new Pending("Bridge", "Obj11_Init (s2.asm:21938)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x15, new Pending("SwingingPlatform", "Obj15_Init (s2.asm:22466)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x18, new Pending("EHZPlatform / ARZPlatform", "Obj18_Init (s2.asm:23176)", "Subtype, child or multipart display fields require explicit projection")),
            Map.entry(0x1C, new Pending("Scenery", "Obj1C_Init (s2.asm:24079)", "Subtype, child or multipart display fields require explicit projection")),
            Map.entry(0x1F, new Pending("CollapsPform", "Obj1F_Init (s2.asm:23768)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x26, new Pending("Monitor", "Obj26_Init (s2.asm:25541)", "Subtype, child or multipart display fields require explicit projection")),
            Map.entry(0x28, new Pending("Animal", "Obj28_Init (s2.asm:24570)", "Subtype, child or multipart display fields require explicit projection")),
            Map.entry(0x29, new Pending("Points", "Obj29_Init (s2.asm:24918-24926)", "Current PointsObjectInstance inherits false isHighPriority; ROM Init sets art_tile bit 15")),
            Map.entry(0x2A, new Pending("Stomper", "Obj2A_Init (s2.asm:24215)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x2E, new Pending("MonitorContents", "Obj2E_Init (s2.asm:25745)", "Subtype, child or multipart display fields require explicit projection")),
            Map.entry(0x36, new Pending("Spikes", "Obj36_Init (s2.asm:29362)", "Subtype, child or multipart display fields require explicit projection")),
            Map.entry(0x37, new Pending("LostRings", "Obj37_Init (s2.asm:25125)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x3E, new Pending("EggPrison", "loc_3F212 / loc_3F228 (s2.asm:84959-84981)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x40, new Pending("Springboard", "Obj40_Init (s2.asm:52285)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x41, new Pending("Spring", "Obj41_Init (s2.asm:33824)", "Subtype, child or multipart display fields require explicit projection")),
            Map.entry(0x44, new Pending("Bumper", "Obj44_Init (s2.asm:45088)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x49, new Pending("EHZWaterfall", "Obj49_Init (s2.asm:46425)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x4B, new Pending("Buzzer", "Obj4B_Init (s2.asm:61021)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x5C, new Pending("Masher", "Obj5C_Init (s2.asm:61242)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x6A, new Pending("MCZRotPforms", "Obj6A_Init (s2.asm:54165)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x72, new Pending("CNZConveyorBelt", "Obj72_Init (s2.asm:55406)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x74, new Pending("InvisibleBlock", "Obj74_Init (s2.asm:46586)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x75, new Pending("MCZBrick", "Obj75_Init (s2.asm:55679)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x76, new Pending("SlidingSpikes", "Obj76_Init (s2.asm:55847)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x77, new Pending("MCZBridge", "Obj77_Init (s2.asm:55975)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x79, new Pending("Checkpoint", "Obj79_Init (s2.asm:44580)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x7A, new Pending("SidewaysPform", "Obj7A_Init (s2.asm:56295)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x7F, new Pending("VineSwitch", "Obj7F_Init (s2.asm:56656)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x80, new Pending("MovingVine", "Obj80_Init (s2.asm:56777)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x81, new Pending("MCZDrawbridge", "Obj81_Init (s2.asm:57063)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x84, new Pending("ForcedSpin", "Obj84_Init (s2.asm:46775)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x85, new Pending("LauncherSpring", "Obj85_Init (s2.asm:58026)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x86, new Pending("Flipper", "Obj86_Init (s2.asm:58433)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x9D, new Pending("Coconuts", "Obj9D_Init (s2.asm:75252)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0x9E, new Pending("Crawlton", "Obj9E_Init (s2.asm:75436)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xA3, new Pending("Flasher", "loc_3875A (s2.asm:76234-76237)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xC8, new Pending("Crawl", "ObjC8_Init (s2.asm:82476)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xD2, new Pending("CNZRectBlocks", "ObjD2_Init (s2.asm:58702)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xD4, new Pending("CNZBigBlock", "ObjD4_Init (s2.asm:58910)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xD5, new Pending("Elevator", "ObjD5_Init (s2.asm:59007)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xD6, new Pending("PointPokey", "ObjD6_Init (s2.asm:59142)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xD7, new Pending("HexBumper", "ObjD7_Init (s2.asm:59960)", "Native fields and ordinary draw not yet certified; explicit adoption required")),
            Map.entry(0xD8, new Pending("BonusBlock", "ObjD8_Init (s2.asm:60152)", "Native fields and ordinary draw not yet certified; explicit adoption required"))
    );

    public static Entry entry(int id) { return ENTRIES.get(id & 0xFF); }
    public static Map<Integer,Entry> entries() { return ENTRIES; }

    /** Adjust2PArtPointer (s2.asm:31456-31465): halve tile bits, retain attributes. */
    public static int adjust2PArtPointer(int word) {
        return (word & 0xF800) | ((word & 0x07FF) >>> 1);
    }
}
