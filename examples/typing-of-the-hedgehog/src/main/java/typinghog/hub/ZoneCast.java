package typinghog.hub;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.RomSpriteRequest.DplcLayout;

/**
 * One signature badnik per story zone, decoded from the player's ROM and drawn beside the
 * story map's stop panel in the act's own palette (its level kit). Addresses are disassembly
 * labels (sonic.lst, s2.asm, sonic3k.lst) and the palette line is the object's
 * {@code make_art_tile}; the same requests drive Eggman's Sky's fauna, where their frames were
 * picked from contact sheets. Zones without a regular badnik (final and boss-only zones) have
 * none.
 */
final class ZoneCast {
    /** A badnik: its ROM sprite request and the mapping frames of its idle cycle. */
    record Badnik(String name, RomSpriteRequest request, int frames0, int frames1, int frames2, int delay) {
        int frame(long ticks) {
            int count = frames2 >= 0 ? 3 : frames1 >= 0 ? 2 : 1;
            int i = (int) ((ticks / delay) % count);
            return i == 0 ? frames0 : i == 1 ? frames1 : frames2;
        }
    }

    private ZoneCast() { }

    static Badnik of(String game, int zone) {
        return switch (game) {
            case "s1" -> switch (zone) {
                case 0 -> nem("Motobug", 0x37A2C, 0xFE2C, 0, 6, 0, 1, 2);              // Nem_Motobug, Map_Moto
                case 1 -> nem("Batbrain", 0x386BC, 0x108CA, 0, 6, 1, 2, 3);           // Nem_Basaran
                case 2 -> nem("Roller", 0x37508, 0xE830, 0, 4, 2, 3, 4);              // Nem_Roller
                case 3 -> nem("Jaws", 0x3727E, 0xB2FA, 1, 6, 0, 1, 2);                // Nem_Jaws
                case 4 -> nem("Bomb", 0x38C00, 0x122FC, 0, 8, 0, 1, 2);               // Nem_Bomb
                case 5 -> nem("Caterkiller", 0x39076, 0x1751A, 1, 8, 0, 1, 2);        // Nem_Cat
                default -> null;
            };
            case "s2" -> switch (zone) {
                case 0 -> nem("Coconuts", 0x8A87A, 0x37D96, 0, 8, 0, 1, 2);           // ArtNem_Coconuts
                case 1 -> nem("Grabber", 0x8B6B4, 0x3921A, 1, 8, 0, 1, -1);           // ArtNem_Grabber
                case 2 -> nem("Grounder", 0x8970E, 0x36CF0, 1, 6, 2, 3, 4);           // ArtNem_Grounder
                case 3 -> nem("Crawl", 0x901A4, 0x3D450, 0, 10, 0, 1, -1);            // ArtNem_Crawl
                case 5 -> nem("Flasher", 0x8AC5E, 0x388F0, 0, 6, 0, 1, -1);           // ArtNem_Flasher
                case 6 -> nem("Aquis", 0x8368A, 0x2CF94, 1, 8, 0, 3, 4);              // ArtNem_Aquis
                case 7 -> nem("Slicer", 0x8AD80, 0x385E2, 1, 8, 0, 1, 2);             // ArtNem_MtzMantis
                case 8 -> nem("Nebula", 0x8A142, 0x3789A, 1, 4, 0, 1, 2);             // ArtNem_Nebula
                case 9 -> nem("Balkiry", 0x8BC16, 0x393CC, 0, 4, 0, 1, -1);           // ArtNem_Balkiry
                default -> null;
            };
            case "s3k" -> switch (zone) {
                case 0 -> new Badnik("Rhinobot", RomSpriteRequest.streamed(0x36732A, 0xAA0, 0x3615A8, 0x36156E,
                        DplcLayout.OBJECT, 1), 0, 1, 2, 4);                              // ArtUnc_AIZRhinobot
                case 1 -> kos("Jawz", 0x36A552, 0x361364, 4, 0, 1, -1);                 // ArtKosM_Jawz
                case 2 -> kos("Mantis", 0x36E2D6, 0x361D26, 8, 0, 1, 2);                // ArtKosM_Mantis
                case 3 -> kos("Batbot", 0x3703EC, 0x361BD0, 4, 0, 1, 2);                // ArtKosM_Batbot
                case 4 -> kos("Technosqueek", 0x0DC9C4, 0x089B78, 4, 0, 1, -1);         // ArtKosM_Technosqueek
                case 5 -> new Badnik("Penguinator", RomSpriteRequest.streamed(0x374154, 0x1000, 0x361E90, 0x361E4E,
                        DplcLayout.OBJECT, 1), 0, 1, 2, 8);                              // ArtUnc_Penguinator
                case 6 -> kos("Snale Blaster", 0x377996, 0x360400, 12, 0, 1, -1);       // ArtKosM_SnaleBlaster
                case 7 -> kos("Madmole", 0x165F02, 0x08D9F2, 10, 0, 1, 2);              // ArtKosM_Madmole
                case 8 -> kos("Skorp", 0x16ADC6, 0x186C84, 8, 0, 1, 2);                 // ArtKosM_Skorp
                case 9 -> kos("Iwamodoki", 0x16F4E4, 0x8FC90, 8, 1, 2, 3);              // ArtKosM_Iwamodoki
                default -> null;
            };
            default -> null;
        };
    }

    private static Badnik nem(String name, int art, int map, int line, int delay, int f0, int f1, int f2) {
        return new Badnik(name, RomSpriteRequest.of(art, Compression.NEMESIS, map, line), f0, f1, f2, delay);
    }

    /** S3K badniks: Kosinski Moduled art on palette line 1. */
    private static Badnik kos(String name, int art, int map, int delay, int f0, int f1, int f2) {
        return new Badnik(name, RomSpriteRequest.of(art, Compression.KOSINSKI_MODULED, map, 1), f0, f1, f2, delay);
    }
}
