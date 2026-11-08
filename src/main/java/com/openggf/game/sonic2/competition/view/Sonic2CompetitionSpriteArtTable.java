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

    // Obj37_Init (s2.asm:25147-25163); Obj25_MapUnc_12382 (s2.asm:25437-25440).
    // Ring art belongs to RingManager, not ObjectArtProvider's ordinary object sheets.
    private static final Entry LOST_RING = new Entry(0x12382, 0x26BC, 8, 0x84, 8, "",
            "Obj37_Init (s2.asm:25147-25163)");
    static Entry lostRing() { return LOST_RING; }

    public record Pending(String name, String initCitation, String reason) { }

    /** Qualification blockers until each actual ordinary draw and native SST projection is certified. */
    public static final Map<Integer,Pending> PENDING_ADOPTION = Map.ofEntries(
            Map.entry(0x3A, new Pending("Capsule results (conservative)", "loc_140AC (s2.asm:27803-27815)", "Placed Obj3E can call Load_EndOfAct; prove native 2P lifetime exclusion before removing this conservative child obligation")),
            Map.entry(0x44, new Pending("Bumper", "Obj44_Init (s2.asm:45088)", "Lane C owns this object; display adoption deferred")),
            Map.entry(0x76, new Pending("SlidingSpikes", "Obj76_Init (s2.asm:55847)", "Lane C has modified this owner relative to60aa1d658; defer to avoid concurrent edits")),
            Map.entry(0x7A, new Pending("SidewaysPform", "Obj7A_Init (s2.asm:56295)", "Lane C has modified this owner relative to60aa1d658; defer to avoid concurrent edits")),
            Map.entry(0x84, new Pending("ForcedSpin", "Obj84_Init (s2.asm:46775)", "Lane C owns this object; display adoption deferred")),
            Map.entry(0x85, new Pending("LauncherSpring", "Obj85_Init (s2.asm:58026)", "Lane C owns this object; display adoption deferred")),
            Map.entry(0xD3, new Pending("BombPrize", "loc_2BC86 (s2.asm:59221-59262)", "Lane C owns this object; display adoption deferred")),
            Map.entry(0xD6, new Pending("PointPokey", "ObjD6_Init (s2.asm:59142)", "Lane C owns this object; display adoption deferred")),
            Map.entry(0xD8, new Pending("BonusBlock", "ObjD8_Init (s2.asm:60152)", "Lane C owns this object; display adoption deferred")),
            Map.entry(0xDC, new Pending("RingPrize", "loc_2BD4E (s2.asm:59272-59313)", "Lane C owns this object; display adoption deferred"))
    );

    /** Fixed player accessories are not object-layout IDs; their existing SST owner supplies identity. */
    private static final java.util.List<Row> FIXED_ROLE_ROWS = java.util.List.of(
            new Row("sonic-dust",0x08,new Entry(Sonic2Constants.MAP_UNC_OBJ08_ADDR,
                    Sonic2Constants.ART_TILE_SONIC_DUST,0x10,4,0,"","Obj08_Init (s2.asm:42721-42739)"),1,0,0),
            new Row("tails-dust",0x08,new Entry(Sonic2Constants.MAP_UNC_OBJ08_ADDR,
                    Sonic2Constants.ART_TILE_TAILS_DUST,0x10,4,0,"","Obj08_Init (s2.asm:42721-42739)"),1,0,0),
            new Row("shield",0x38,new Entry(Sonic2Constants.MAP_UNC_SHIELD_ADDR,
                    0x04BE,0x18,4,0,ObjectArtKeys.SHIELD,"Obj38_Main (s2.asm:42393-42401)"),1,0,0),
            // ArtTile_ArtNem_Invincible_stars (s2.constants.asm:2611); Obj35's explicit DisplaySprite3 bucket1.
            new Row("invincibility-stars",0x35,new Entry(Sonic2Constants.MAP_UNC_INVINCIBILITY_STARS_ADDR,
                    0x04DE,0x10,0x44,0,ObjectArtKeys.INVINCIBILITY_STARS,
                    "Obj35_Main (s2.asm:42466-42490); loc_1DA44 (s2.asm:42517-42542)"),1,0,0));
    public static Row fixedRole(int id) {
        return FIXED_ROLE_ROWS.stream().filter(row->row.id()==id).findFirst().orElseThrow();
    }
    public static java.util.List<Row> fixedRoleRows() { return FIXED_ROLE_ROWS; }

    /** One ROM Init variant; masks are the actual placement byte selectors. */
    public record Row(String role, int id, Entry display, int priorityBucket, int subtypeMask, int subtypeValue) {
        public boolean accepts(int subtype) { return (subtype & subtypeMask)==subtypeValue; }
    }
    private static final java.util.List<Row> CAPTURE_ROWS=java.util.List.of(
            new Row("ehz-bridge",0x11,new Entry(0xFC70,0x43B6,128,4,0,"bridge","Obj11_Init (s2.asm:21938-21970); Obj11_MakeBdgSegment (s2.asm:21993-22016)"),3,0,0),
            new Row("ehz-stake",0x1C,new Entry(0xFC70,0x43B6,0x4,0x4,0x0,"bridge","Obj1C_InitData (s2.asm:24023-24026); Obj1C_Init (s2.asm:24079-24098)"),1,0xFF,0x2),
            new Row("monitor",0x26,new Entry(0x12D36,0x0680,0xF,0x4,0xE,"monitor","Obj26_Init (s2.asm:25541-25550)"),3,0x0,0x0),
            new Row("spikes-0",0x36,new Entry(0x15B68,0x2434,0x10,0x4,0x10,"spike","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x0),
            new Row("spikes-1",0x36,new Entry(0x15B68,0x2434,0x20,0x4,0x10,"spike","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x10),
            new Row("spikes-2",0x36,new Entry(0x15B68,0x2434,0x30,0x4,0x10,"spike","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x20),
            new Row("spikes-3",0x36,new Entry(0x15B68,0x2434,0x40,0x4,0x10,"spike","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x30),
            new Row("spikes-4",0x36,new Entry(0x15B68,0x242C,0x10,0x4,0x10,"spike_side","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x40),
            new Row("spikes-5",0x36,new Entry(0x15B68,0x242C,0x10,0x4,0x20,"spike_side","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x50),
            new Row("spikes-6",0x36,new Entry(0x15B68,0x242C,0x10,0x4,0x30,"spike_side","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x60),
            new Row("spikes-7",0x36,new Entry(0x15B68,0x242C,0x10,0x4,0x40,"spike_side","Obj36_InitData (s2.asm:29349-29359); Obj36_Init (s2.asm:29362-29389)"),4,0xF0,0x70),
            new Row("waterfall",0x49,new Entry(0x20C50,0x239E,0x20,0x14,0x80,"waterfall","Obj49_Init (s2.asm:46425-46435)"),0,0x0,0x0),
            new Row("buzzer",0x4B,new Entry(0x2D2EA,0x03D2,0x10,0x4,0x10,"buzzer","Obj4B_Init (s2.asm:61021-61032)"),3,0x0,0x0),
            new Row("buzzer-flame",0x4B,new Entry(0x2D2EA,0x03D2,0x10,0x4,0x0,"buzzer","Obj4B_Init (s2.asm:61038-61048)"),4,0x0,0x0),
            new Row("buzzer-shot",0x4B,new Entry(0x2D2EA,0x03D2,0x10,0x4,0x0,"buzzer","Obj4B_ShootProjectile (s2.asm:61165-61180)"),4,0x0,0x0),
            new Row("masher",0x5C,new Entry(0x2D442,0x0414,0x10,0x4,0x0,"masher","Obj5C_Init (s2.asm:61242-61251)"),4,0x0,0x0),
            new Row("coconuts",0x9D,new Entry(0x37D96,0x03EE,0xC,0x4,0x0,"coconuts","Obj9D_Init (s2.asm:75252-75255); Obj9D_SubObjData (s2.asm:75396-75397)"),5,0x0,0x0),
            new Row("coconut",0x98,new Entry(0x37D96,0x03EE,0x8,0x84,0x0,"coconuts","Obj98_Init (s2.asm:74790-74791); Obj9D_SubObjData2 (s2.asm:74860-74861)"),4,0xFF,0x20),
            new Row("springboard",0x40,new Entry(0x265F4,0x0440,0x1C,0x4,0x0,"springboard","Obj40_Init (s2.asm:52285-52296)"),4,0x0,0x0),
            new Row("spring-0-0",0x41,new Entry(0x1901C,0x045C,0x10,0x4,0x0,"spring_vertical","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x0),
            new Row("spring-0-2",0x41,new Entry(0x19032,0x245C,0x10,0x4,0x0,"spring_vertical_red","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x2),
            new Row("spring-1-0",0x41,new Entry(0x1901C,0x0470,0x8,0x4,0x0,"spring_horizontal","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x10),
            new Row("spring-1-2",0x41,new Entry(0x19032,0x2470,0x8,0x4,0x0,"spring_horizontal_red","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x12),
            new Row("spring-2-0",0x41,new Entry(0x1901C,0x045C,0x10,0x4,0x0,"spring_vertical","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x20),
            new Row("spring-2-2",0x41,new Entry(0x19032,0x245C,0x10,0x4,0x0,"spring_vertical_red","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x22),
            new Row("spring-3-0",0x41,new Entry(0x1901C,0x043C,0x10,0x4,0x0,"spring_diagonal","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x30),
            new Row("spring-3-2",0x41,new Entry(0x19032,0x243C,0x10,0x4,0x0,"spring_diagonal_red","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x32),
            new Row("spring-4-0",0x41,new Entry(0x1901C,0x043C,0x10,0x4,0x0,"spring_diagonal","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x40),
            new Row("spring-4-2",0x41,new Entry(0x19032,0x243C,0x10,0x4,0x0,"spring_diagonal_red","Obj41_Init (s2.asm:33824-33835); Obj41_Init_Common (s2.asm:33876-33891)"),4,0x72,0x42),
            new Row("mcz-bridge",0x77,new Entry(0x29064,0x643C,0x80,0x4,0x0,"mcz_bridge","Obj77_Init (s2.asm:55975-55981)"),0,0x0,0x0),
            new Row("vine-switch",0x7F,new Entry(0x29938,0x640E,0x8,0x4,0x0,"vine_switch","Obj7F_Init (s2.asm:56656-56663)"),4,0x0,0x0),
            new Row("moving-vine",0x80,new Entry(0x29C64,0x641E,0x10,0x14,0x80,"vine_pulley","Obj80_Init (s2.asm:56777-56784); Obj80_MCZ_Init (s2.asm:56814-56834)"),4,0x0,0x0),
            new Row("flipper",0x86,new Entry(0x2B45A,0x43B2,0x18,0x4,0x0,"flipper","Obj86_Init (s2.asm:58433-58445)"),4,0x0,0x0),
            new Row("cnz-rect",0xD2,new Entry(0x2B694,0x437C,0x8,0x4,0x0,"cnz_rect_blocks","ObjD2_Init (s2.asm:58702-58716)"),4,0x0,0x0),
            new Row("cnz-big",0xD4,new Entry(0x2B9CA,0x436C,0x20,0x4,0x0,"cnz_big_block","ObjD4_Init (s2.asm:58910-58928)"),4,0x0,0x0),
            new Row("cnz-elevator",0xD5,new Entry(0x2BB40,0x4384,0x10,0x4,0x0,"cnz_elevator","ObjD5_Init (s2.asm:59007-59024)"),4,0x0,0x0),
            new Row("mcz-crate",0x6A,new Entry(0x27D30,0x63D4,32,0x4,32,"mcz_crate","Obj6A_Init (s2.asm:54165-54234)"),4,0x0,0x0),
            new Row("mcz-crate-child-6",0x6A,new Entry(0x27D30,0x63D4,32,0x4,32,"mcz_crate","Obj6A_Init (s2.asm:54165-54190); Obj6A_InitSubObject (s2.asm:54213-54220)"),4,0xFF,0x6),
            new Row("mcz-crate-child-C",0x6A,new Entry(0x27D30,0x63D4,32,0x4,32,"mcz_crate","Obj6A_Init (s2.asm:54165-54190); Obj6A_InitSubObject (s2.asm:54213-54220)"),4,0xFF,0xC),
            new Row("checkpoint",0x79,new Entry(0x1F424,0x047C,8,0x4,0,"checkpoint","Obj79_Init (s2.asm:44580-44587)"),5,0x0,0x0),
            new Row("checkpoint-dongle",0x79,new Entry(0x1F424,0x047C,8,0x4,0,"checkpoint","Obj79_Main (s2.asm:44643-44660)"),4,0x0,0x0),
            new Row("hex-bumper",0xD7,new Entry(0x2C626,0x4394,16,0x4,0,"hex_bumper","ObjD7_Init (s2.asm:59960-59968)"),1,0x0,0x0),
            new Row("animal-0-0",0x28,new Entry(0x11E1C,0x0580,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-0-1",0x28,new Entry(0x11E1C,0x0594,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-1-0",0x28,new Entry(0x11E40,0x0580,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-1-1",0x28,new Entry(0x11E40,0x0594,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-2-0",0x28,new Entry(0x11E64,0x0580,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-2-1",0x28,new Entry(0x11E64,0x0594,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-3-0",0x28,new Entry(0x11E88,0x0580,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-3-1",0x28,new Entry(0x11E88,0x0594,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-4-0",0x28,new Entry(0x11EAC,0x0580,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("animal-4-1",0x28,new Entry(0x11EAC,0x0594,8,0x4,12,"animal","Obj28_Properties (s2.asm:24513-24524); Obj28_InitRandom (s2.asm:24597-24630)"),6,0x0,0x0),
            new Row("signpost",0xD,new Entry(0x19656,0x05E8,24,0x4,0,"signpost","Obj0D_Init (s2.asm:34636-34648); loc_1922C (s2.asm:34662-34669)"),4,0x0,0x0),
            new Row("mcz-swing-left",0x15,new Entry(0x10256,0x0000,24,0x4,8,"","Obj15_Init (s2.asm:22466-22568)"),3,0x70,0x10),
            new Row("mcz-swing-right",0x15,new Entry(0x10256,0x0000,24,0x4,8,"","Obj15_Init (s2.asm:22466-22568)"),3,0x70,0x30),
            new Row("mcz-swing-trap",0x15,new Entry(0x102DE,0x0000,24,0x4,8,"","Obj15_Init (s2.asm:22466-22568)"),3,0x70,0x40),
            new Row("ehz-platform",0x18,new Entry(0x107F6,0x4000,32,0x4,0,"","Obj18_Init (s2.asm:23176-23218)"),4,0x0,0x0),
            new Row("mcz-collapse",0x1F,new Entry(0x11106,0x63F4,32,0x4,0,"mcz_collapsing_platform","Obj1F_Init (s2.asm:23768-23804)"),4,0x0,0x0),
            new Row("sparkle",0x25,new Entry(0x12382,0x26BC,8,0x4,0,"","loc_19398 (s2.asm:34772-34799)"),2,0x0,0x0),
            new Row("stomper",0x2A,new Entry(0x11666,0x4000,16,0x14,80,"","Obj2A_Init (s2.asm:24215-24225)"),4,0x0,0x0),
            new Row("monitor-icon",0x2E,new Entry(0x12D36,0x8680,8,0x24,0,"monitor","Obj2E_Init (s2.asm:25745-25774)"),3,0x0,0x0),
            new Row("capsule-body",0x3E,new Entry(0x3F436,0x2680,32,0x84,0,"egg_prison","Obj3E_ObjLoadData (s2.asm:84951-84956); loc_3F228 (s2.asm:84973-84988)"),4,0x0,0x0),
            new Row("capsule-button",0x3E,new Entry(0x3F436,0x2680,16,0x84,0,"egg_prison","Obj3E_ObjLoadData (s2.asm:84951-84956); loc_3F228 (s2.asm:84973-84988)"),5,0x0,0x0),
            new Row("capsule-lock",0x3E,new Entry(0x3F436,0x2680,8,0x84,0,"egg_prison","Obj3E_ObjLoadData (s2.asm:84951-84956); loc_3F228 (s2.asm:84973-84988)"),3,0x0,0x0),
            new Row("mcz-brick",0x75,new Entry(0x28D8A,0x2000,16,0x4,0,"","Obj75_Init (s2.asm:55679-55743)"),4,0xF,0xF),
            new Row("mcz-spike-chain-6",0x75,new Entry(0x28D8A,0x2000,16,0x4,0,"","Obj75_Init (s2.asm:55679-55743)"),5,0xF,0x6),
            new Row("mcz-spike-chain-7",0x75,new Entry(0x28D8A,0x2000,16,0x4,0,"","Obj75_Init (s2.asm:55679-55743)"),5,0xF,0x7),
            new Row("mcz-drawbridge",0x81,new Entry(0x2A24E,0x643C,8,0x4,0,"mcz_drawbridge","Obj81_Init (s2.asm:57063-57115)"),5,0x0,0x0),
            new Row("crawlton",0x9E,new Entry(0x37FF2,0x23C0,128,0x4,128,"crawlton","Obj9E_Init (s2.asm:75436-75440); Obj9E_SubObjData (s2.asm:75590-75591)"),4,0x0,0x0),
            new Row("flasher",0xA3,new Entry(0x388F0,0x83A8,16,0x4,0,"flasher","ObjA3_SubObjData (s2.asm:76377-76378)"),4,0x0,0x0),
            new Row("crawl",0xC8,new Entry(0x3D450,0x8340,16,0x4,0,"crawl","ObjC8_SubObjData (s2.asm:82644-82645)"),3,0x0,0x0)
    );
    /** Child SST recipes; geometry comes from each live owner, counts follow these Init selectors. */
    public record MultiRow(String role,int id,int mapping,int artTile1P,int renderFlags,int width,
                           int height,int bucket,int mainFrame,int childFrame,int fixedCount,String initCitation) { }
    public static final java.util.List<MultiRow> MULTI_ROWS=java.util.List.of(
            new MultiRow("ehz-bridge-logs",0x11,0xFC70,0x43B6,0x44,64,0,3,0,0,-1,
                    "Obj11_Init (s2.asm:21964-21988); Obj11_MakeBdgSegment (s2.asm:21993-22016)"),
            new MultiRow("swing-chain",0x15,0x10256,0,0x54,72,80,4,1,1,-1,
                    "Obj15_Init (s2.asm:22524-22548): low subtype nibble count, first child frame2 and sub6 endpoint"),
            new MultiRow("brick-chain",0x75,0x28D8A,0x2000,0x54,64,64,5,0,1,-1,
                    "Obj75_Init (s2.asm:55710-55743): low subtype nibble count"),
            new MultiRow("drawbridge-logs",0x81,0x2A24E,0x643C,0x54,64,64,5,0,1,8,
                    "Obj81_Init (s2.asm:57089-57115)"),
            new MultiRow("crawlton-body",0x9E,0x37FF2,0x23C0,0x54,128,128,5,0,2,7,
                    "loc_37F74 (s2.asm:75556-75586)")
    );
    /** Static mappings point to one ROM piece, not a frame header or copied runtime asset. */
    public record StaticRow(String role,int id,int parentMappings,int frame,int artTile1P,
                            int renderFlags,int width,int bucket,String initCitation) { }
    public static final java.util.List<StaticRow> STATIC_ROWS=java.util.List.of(
            new StaticRow("mcz-collapse-piece",0x1F,0x11106,1,0x63F4,0x24,32,4,
                    "Obj1F_CreateFragments (s2.asm:23868-23912): frame1 pieces; inherit art/width/priority"),
            new StaticRow("monitor-icon",0x2E,0x12D36,-1,0x8680,0x24,8,3,
                    "Obj2E_Init / loc_128C6 (s2.asm:25745-25783): selected frame's first piece")
    );

    /** Explicit REV01 logic owners with no native display call. */
    public static final Map<Integer,String> NO_DISPLAY=Map.of(
            0x03,"Obj03_MainX / Obj03_MainY (s2.asm:45682-45843): logic returns without DisplaySprite",
            0x06,"Obj06 (s2.asm:47091-47112): movement/lifetime dispatch without drawing",
            0x72,"Obj72_Main (s2.asm:55421-55426): MarkObjGone3 only",
            0x74,"Obj74_Main (s2.asm:46603-46630): debug DisplaySprite exists only in REV00"
    );

    public static java.util.List<Row> captureRows() { return CAPTURE_ROWS; }
    public static Row row(String role) {
        return CAPTURE_ROWS.stream().filter(row->row.role().equals(role)).findFirst().orElseThrow();
    }

    /** Obj41_MapUnc_1901C / Obj41_MapUnc_19032 (s2.asm:34486-34514): two adjacent eleven-entry
     * offset tables share frame data. The first frame offset spans both tables, not just the first. */
    public static int nativeFrameLimit(int mappingAddress) {
        if(mappingAddress==Sonic2Constants.MAP_UNC_SPRING_ADDR
                || mappingAddress==Sonic2Constants.MAP_UNC_SPRING_RED_ADDR)
            return (Sonic2Constants.MAP_UNC_SPRING_RED_ADDR-Sonic2Constants.MAP_UNC_SPRING_ADDR)/2;
        return 256;
    }

    public static Entry entry(int id) { return ENTRIES.get(id & 0xFF); }
    public static Map<Integer,Entry> entries() { return ENTRIES; }

    /** Adjust2PArtPointer (s2.asm:31456-31465): halve tile bits, retain attributes. */
    public static int adjust2PArtPointer(int word) {
        return (word & 0xF800) | ((word & 0x07FF) >>> 1);
    }
}
