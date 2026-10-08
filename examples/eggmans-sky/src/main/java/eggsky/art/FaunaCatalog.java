package eggsky.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Every creature body the galaxy can use, by game. Addresses are labels from each game's
 * disassembly (noted beside them) and the art_tile palette line each object uses; frames were
 * picked from contact sheets of the mappings. Zone masks use each game's public zone ids
 * (Sonic 1: 0 GHZ, 1 MZ, 2 SYZ, 3 LZ, 4 SLZ, 5 SBZ; Sonic 2: 0 EHZ, 1 CPZ, 2 ARZ, 3 CNZ, 5 MCZ,
 * 6 OOZ, 7 MTZ, 8 SCZ, 9 WFZ; S3K: 0 AIZ, 1 HCZ, 2 MGZ, 3 CNZ, 4 FBZ, 5 ICZ, 6 LBZ, 7 MHZ,
 * 8 SOZ, 9 LRZ, 10 SSZ, 11 DEZ).
 */
public final class FaunaCatalog {
    private final List<FaunaDef> all = new ArrayList<>();

    public FaunaCatalog() {
        // ---- Sonic 1 ----
        s1("motobug", "Motobug", z(0), 0x37A2C, 0xFE2C, 0, FaunaDef.WALK, f(0, 1, 2), 6);       // Nem_Motobug, Map_Moto
        s1("crabmeat", "Crabmeat", z(0, 2), 0x35EB0, 0x9DCE, 0, FaunaDef.WALK, f(0, 1, 2), 8);  // Nem_Crabmeat, Map_Crab
        s1("buzzbomber", "Buzz Bomber", z(0, 2, 4), 0x3639E, 0xA0B4, 0, FaunaDef.FLY, f(0, 1), 2); // Nem_Buzz
        s1("chopper", "Chopper", z(0), 0x37016, 0xB254, 0, FaunaDef.HOP, f(0, 1), 6);           // Nem_Chopper
        s1("newtron", "Newtron", z(0), 0x37CB6, 0xE5D0, 1, FaunaDef.WALK, f(1, 2, 3), 10);      // Nem_Newtron
        s1("jaws", "Jaws", z(3), 0x3727E, 0xB2FA, 1, FaunaDef.FLY, f(0, 1, 2, 3), 6);           // Nem_Jaws
        s1("burrobot", "Burrobot", z(3), 0x3692C, 0xB4E6, 0, FaunaDef.HOP, f(0, 1), 6);         // Nem_Burrobot
        s1("roller", "Roller", z(2), 0x37508, 0xE830, 0, FaunaDef.ROLL, f(2, 3, 4), 4);         // Nem_Roller
        s1("yadrin", "Yadrin", z(1, 2), 0x382D4, 0xFFBA, 1, FaunaDef.WALK, f(0, 1, 2), 8);      // Nem_Yadrin
        s1("basaran", "Basaran", z(1), 0x386BC, 0x108CA, 0, FaunaDef.FLY, f(1, 2, 3), 6);       // Nem_Basaran
        s1("bomb", "Bomb", z(4, 5), 0x38C00, 0x122FC, 0, FaunaDef.WALK, f(0, 1, 2), 8);         // Nem_Bomb
        s1("orbinaut1", "Orbinaut", z(3, 4), 0x38E98, 0x125B8, 0, FaunaDef.FLY, f(0), 8);       // Nem_Orbinaut
        s1("caterkiller", "Caterkiller", z(1, 5), 0x39076, 0x1751A, 1, FaunaDef.WALK, f(0, 1, 2), 8); // Nem_Cat
        // Anml_Variables (0x95E4): rabbit, chicken, penguin, seal, pig, flicky, squirrel.
        s1a("pocky1", "Pocky", 0x3B884, 0x9AE4);      // Nem_Rabbit
        s1a("cucky1", "Cucky", 0x3B9DC, 0x9AFC);      // Nem_Chicken
        s1a("pecky1", "Pecky", 0x3BB38, 0x9AE4);      // Nem_Penguin
        s1a("rocky1", "Rocky", 0x3BCB4, 0x9AFC);      // Nem_Seal
        s1a("picky1", "Picky", 0x3BDD0, 0x9B14);      // Nem_Pig
        s1a("flicky1", "Flicky", 0x3BF06, 0x9AFC);    // Nem_Flicky
        s1a("ricky1", "Ricky", 0x3C040, 0x9B14);      // Nem_Squirrel

        // ---- Sonic 2 ----
        s2("buzzer", "Buzzer", z(0), 0x8316A, 0x2D2EA, 0, FaunaDef.FLY, f(0), 8);               // ArtNem_Buzzer
        s2("masher", "Masher", z(0), 0x839EA, 0x2D442, 0, FaunaDef.HOP, f(0, 1), 6);            // ArtNem_Masher
        s2("coconuts", "Coconuts", z(0), 0x8A87A, 0x37D96, 0, FaunaDef.WALK, f(0, 1, 2), 8);    // ArtNem_Coconuts
        s2("spiny", "Spiny", z(1), 0x8B430, 0x38CCA, 1, FaunaDef.WALK, f(0, 1), 8);             // ArtNem_Spiny
        s2("grabber", "Grabber", z(1), 0x8B6B4, 0x3921A, 1, FaunaDef.FLY, f(0, 1), 8);          // ArtNem_Grabber
        s2("chopchop", "Chop Chop", z(2), 0x89B9A, 0x36EF6, 1, FaunaDef.FLY, f(0, 1), 8);       // ArtNem_ChopChop
        s2("whisp", "Whisp", z(2), 0x895E4, 0x36A4E, 1, FaunaDef.FLY, f(0, 1), 3);              // ArtNem_Whisp
        s2("grounder", "Grounder", z(2), 0x8970E, 0x36CF0, 1, FaunaDef.WALK, f(2, 3, 4), 6);    // ArtNem_Grounder
        s2("crawlton", "Crawlton", z(5), 0x8AB36, 0x37FF2, 1, FaunaDef.FLY, f(0, 1), 10);       // ArtNem_Crawlton
        s2("flasher", "Flasher", z(5), 0x8AC5E, 0x388F0, 0, FaunaDef.FLY, f(0, 1), 6);          // ArtNem_Flasher
        s2("octus", "Octus", z(6), 0x8336A, 0x2CBFE, 1, FaunaDef.HOP, f(0, 1, 2), 8);           // ArtNem_Octus
        s2("aquis", "Aquis", z(6), 0x8368A, 0x2CF94, 1, FaunaDef.FLY, f(0, 3, 4), 8);           // ArtNem_Aquis
        s2("asteron", "Asteron", z(7), 0x8B300, 0x38A96, 0, FaunaDef.FLY, f(0, 1), 12);         // ArtNem_MtzSupernova
        s2("shellcracker", "Shellcracker", z(7), 0x8B058, 0x38314, 0, FaunaDef.WALK, f(0, 1, 2), 8); // ArtNem_Shellcracker
        s2("slicer", "Slicer", z(7), 0x8AD80, 0x385E2, 1, FaunaDef.WALK, f(0, 1, 2), 8);        // ArtNem_MtzMantis
        s2("crawl", "Crawl", z(3), 0x901A4, 0x3D450, 0, FaunaDef.WALK, f(0, 1), 10);            // ArtNem_Crawl
        s2("nebula", "Nebula", z(8), 0x8A142, 0x3789A, 1, FaunaDef.FLY, f(0, 1, 2, 3), 4);      // ArtNem_Nebula
        s2("turtloid", "Turtloid", z(8), 0x8A362, 0x37B62, 0, FaunaDef.FLY, f(0, 1), 10);       // ArtNem_Turtloid
        s2("balkiry", "Balkiry", z(8, 9), 0x8BC16, 0x393CC, 0, FaunaDef.FLY, f(0, 1), 4);       // ArtNem_Balkiry
        s2a("flicky2", "Flicky", 0x7EF60, 0x11E1C);   // ArtNem_Flicky
        s2a("pocky2", "Pocky", 0x7FDD2, 0x11EAC);     // ArtNem_Rabbit
        s2a("tocky2", "Tocky", 0x7FADE, 0x11E40);     // ArtNem_Turtle

        // ---- Sonic 3 & Knuckles ----
        s3u("rhinobot", "Rhinobot", z(0), 0x36732A, 0xAA0, 0x3615A8, 0x36156E, FaunaDef.WALK, f(0, 1, 2, 3), 4);
        s3("bloominator", "Bloominator", z(0), 0x367DCA, 0x3616C0, FaunaDef.WALK, f(0, 1), 12);
        s3("monkeydude", "Monkey Dude", z(0), 0x36800C, 0x361776, FaunaDef.HOP, f(0, 1, 2), 8);
        s3("jawz", "Jawz", z(1), 0x36A552, 0x361364, FaunaDef.FLY, f(0, 1), 4);
        s3("blastoid", "Blastoid", z(1), 0x36A7C6, 0x360DD0, FaunaDef.WALK, f(0), 8);
        s3n("buggernaut", "Buggernaut", z(1), 0x36A3E0, 0x360EB4, FaunaDef.FLY, f(0, 1), 2);
        s3("turbospiker", "Turbo Spiker", z(1), 0x36A968, 0x361212, FaunaDef.WALK, f(0, 1), 8);
        s3("megachopper", "Mega Chopper", z(1), 0x36A6C4, 0x360F26, FaunaDef.HOP, f(0, 1), 6);
        s3("pointdexter", "Pointdexter", z(1), 0x36AD8A, 0x360E72, FaunaDef.FLY, f(0, 1), 10);
        s3("spiker3", "Spiker", z(2), 0x36E0C4, 0x361CB8, FaunaDef.WALK, f(1, 2), 10);
        s3("mantis", "Mantis", z(2), 0x36E2D6, 0x361D26, FaunaDef.HOP, f(0, 1, 2, 3), 8);
        s3u("bubbles", "Bubbles", z(2), 0x36D6A4, 0xA20, 0x361C68, 0x361C40, FaunaDef.FLY, f(0, 1, 2), 6);
        s3u("clamer", "Clamer", z(3), 0x36EF18, 0x1140, 0x361ABC, 0x361A78, FaunaDef.WALK, f(0, 1), 12);
        s3("sparkle", "Sparkle", z(3), 0x3700CA, 0x361B34, FaunaDef.FLY, f(0, 1), 6);
        s3("batbot", "Batbot", z(3), 0x3703EC, 0x361BD0, FaunaDef.FLY, f(0, 1, 2), 4);
        s3("blaster", "Blaster", z(4), 0x0DC6C2, 0x08977C, FaunaDef.WALK, f(0, 1, 2), 8);
        s3("technosqueek", "Technosqueek", z(4), 0x0DC9C4, 0x089B78, FaunaDef.WALK, f(0, 1), 4);
        s3("starpointer", "Star Pointer", z(5), 0x3751C6, 0x361FAE, FaunaDef.FLY, f(0), 8);
        s3u("penguinator", "Penguinator", z(5), 0x374154, 0x1000, 0x361E90, 0x361E4E, FaunaDef.WALK, f(0, 1, 2), 8);
        s3("snaleblaster", "Snale Blaster", z(6), 0x377996, 0x360400, FaunaDef.WALK, f(0, 1), 12);
        s3("orbinaut3", "Orbinaut", z(6), 0x377D1A, 0x3604A4, FaunaDef.FLY, f(0), 8);
        s3("ribot", "Ribot", z(6), 0x377BE8, 0x3604B8, FaunaDef.FLY, f(0, 1), 8);
        s3u("flybot", "Flybot767", z(6), 0x377EBE, 0x1320, 0x36065A, 0x3607EC, FaunaDef.FLY, f(0, 1, 2), 4);
        s3("madmole", "Madmole", z(7), 0x165F02, 0x08D9F2, FaunaDef.WALK, f(0, 1, 2), 10);
        s3("mushmeanie", "Mushmeanie", z(7), 0x166234, 0x08DCF8, FaunaDef.HOP, f(1, 2, 3), 6);
        s3("dragonfly", "Dragonfly", z(7), 0x166386, 0x08DFDA, FaunaDef.FLY, f(0, 1, 2, 3, 4), 4);
        s3u("butterdroid", "Butterdroid", z(7), 0x16652A, 0x800, 0x08E12E, 0x08E160, FaunaDef.FLY, f(0, 1), 6);
        s3u("cluckoid", "Cluckoid", z(7), 0x166A8A, 0x1000, 0x08E546, 0x08E4B8, FaunaDef.WALK, f(0, 1, 2), 8);
        s3("skorp", "Skorp", z(8), 0x16ADC6, 0x186C84, FaunaDef.WALK, f(0, 1, 2), 8);
        s3("sandworm", "Sandworm", z(8), 0x16B038, 0x186D10, FaunaDef.HOP, f(0, 1, 2), 8);
        s3("rockn", "Rockn", z(8), 0x16B2BA, 0x08F086, FaunaDef.WALK, f(0, 1, 2), 8);
        s3u("fireworm", "Fireworm", z(9), 0x16EFB2, 0x380, 0x8FABE, 0x8FAA6, FaunaDef.FLY, f(1, 2, 3), 4);
        s3("iwamodoki", "Iwamodoki", z(9), 0x16F4E4, 0x8FC90, FaunaDef.WALK, f(1, 2, 3, 4, 5), 8);
        s3("toxomister", "Toxomister", z(9), 0x16F7E6, 0x9008E, FaunaDef.FLY, f(1), 8);
        // word_2C7EA selects Map_Animals1-5; tile dimensions/strides differ by species.
        s3a("flicky3", "Flicky", 0x1931D6, 0x02CEBA);   // ArtNem_BlueFlicky, Map_Animals1
        s3a("cucky3", "Cucky", 0x193308, 0x02CEBA);     // ArtNem_Chicken
        s3a("picky3", "Picky", 0x19308A, 0x02CEF6);     // ArtNem_Pig, Map_Animals3
        s3a("rocky3", "Rocky", 0x192F6E, 0x02CF14);     // ArtNem_Seal
        s3a("ricky3", "Ricky", 0x1935A8, 0x02CED8);     // ArtNem_Squirrel, Map_Animals2
        s3a("pecky3", "Pecky", 0x193456, 0x02CF32);     // ArtNem_Penguin, Map_Animals5
        s3a("pocky3", "Pocky", 0x193706, 0x02CF32);     // ArtNem_Rabbit, Map_Animals5
    }

    public List<FaunaDef> all() {
        return all;
    }

    /** Bodies from {@code game}. */
    public List<FaunaDef> forGame(String game) {
        List<FaunaDef> out = new ArrayList<>();
        for (FaunaDef def : all) {
            if (def.game().equals(game)) {
                out.add(def);
            }
        }
        return out;
    }

    public FaunaDef byKey(String key) {
        for (FaunaDef def : all) {
            if (def.key().equals(key)) {
                return def;
            }
        }
        return null;
    }

    private static long z(int... zones) {
        long mask = 0;
        for (int zone : zones) {
            mask |= 1L << zone;
        }
        return mask;
    }

    private static int[] f(int... frames) {
        return frames;
    }

    private void s1(String key, String name, long zones, int art, int map, int line, int motion, int[] frames, int ticks) {
        all.add(new FaunaDef(key, name, "s1", zones, RomSpriteRequest.of(art, Compression.NEMESIS, map, line), motion,
                frames, ticks, false));
    }

    /** Sonic 1 Anml_Variables selects Map_Animal1/2/3 by species, not one shared layout. */
    private void s1a(String key, String name, int art, int map) {
        all.add(new FaunaDef(key, name, "s1", -1L, RomSpriteRequest.of(art, Compression.NEMESIS, map, 0),
                key.startsWith("flicky") || key.startsWith("cucky") ? FaunaDef.FLY : FaunaDef.HOP, f(0, 1), 6, true));
    }

    private void s2(String key, String name, long zones, int art, int map, int line, int motion, int[] frames, int ticks) {
        all.add(new FaunaDef(key, name, "s2", zones, RomSpriteRequest.of(art, Compression.NEMESIS, map, line), motion,
                frames, ticks, false));
    }

    /** Sonic 2 Obj28_Properties selects its mapping table by species. */
    private void s2a(String key, String name, int art, int map) {
        all.add(new FaunaDef(key, name, "s2", -1L, RomSpriteRequest.of(art, Compression.NEMESIS, map, 0),
                key.startsWith("flicky") ? FaunaDef.FLY : FaunaDef.HOP, f(0, 1), 6, true));
    }

    /** An S3K badnik with Kosinski Moduled art on palette line 1. */
    private void s3(String key, String name, long zones, int art, int map, int motion, int[] frames, int ticks) {
        all.add(new FaunaDef(key, name, "s3k", zones, RomSpriteRequest.of(art, Compression.KOSINSKI_MODULED, map, 1),
                motion, frames, ticks, false));
    }

    /** An S3K badnik with Nemesis art on palette line 1. */
    private void s3n(String key, String name, long zones, int art, int map, int motion, int[] frames, int ticks) {
        all.add(new FaunaDef(key, name, "s3k", zones, RomSpriteRequest.of(art, Compression.NEMESIS, map, 1),
                motion, frames, ticks, false));
    }

    /** An S3K badnik with uncompressed DPLC-streamed art on palette line 1. */
    private void s3u(String key, String name, long zones, int art, int size, int map, int dplc, int motion,
            int[] frames, int ticks) {
        all.add(new FaunaDef(key, name, "s3k", zones,
                RomSpriteRequest.streamed(art, size, map, dplc, DplcLayout.OBJECT, 1), motion, frames, ticks, false));
    }

    /** An S3K animal (Map_Animals1-5: flying or hopping 0-1, released 2). */
    private void s3a(String key, String name, int art, int map) {
        all.add(new FaunaDef(key, name, "s3k", -1L, RomSpriteRequest.of(art, Compression.NEMESIS, map, 0),
                key.startsWith("flicky") || key.startsWith("cucky") ? FaunaDef.FLY : FaunaDef.HOP, f(0, 1), 6, true));
    }
}
