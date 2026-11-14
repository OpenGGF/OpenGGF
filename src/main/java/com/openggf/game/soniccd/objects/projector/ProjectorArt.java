package com.openggf.game.soniccd.objects.projector;

import com.openggf.data.RomByteReader;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The projector's sheets ({@code MapSpr_Projector}: one mapping set for the body and both holograms), registered for the
 * stages that link it. The projector's art ({@code Art_Projector}) is not what its init requests ({@code LoadPLC 5} loads the
 * hologram animals' art, {@code PLC_Projector}, at $9EE0, Palmtree Panic/Graphics Lists (Act 2 Past).asm:239-242); the body and
 * the holograms read tiles that the camera's section art holds at the zone's tile ({@code PLC_Cam5_Full} and its siblings
 * put {@code Art_Projector} at $8060, Palmtree Panic/Data (Act 1 Past).asm:128-130, 192-194). So the sheet is registered
 * once per section, over the level-start VRAM plus that section's init list ({@code LoadSectionArt}, Section Art Load.asm:7-10;
 * the zone art classes model sections the same way), and the object draws the one of the camera's section
 * ({@code UpdateSectionArt}, :19-39: the decode queue is not modelled).
 */
public final class ProjectorArt {
    public static final String PROJECTOR = "cd.projector.";
    /** PLC 2: the title card's slide-out cue, the level's art where a stage has no sections (Title Card/Main.asm:145-148). */
    private static final int TITLE_CARD_PLC = 2;

    private ProjectorArt() { }

    /** The stage's sections: {@code SectionRanges}' bounds (first ending in $FFFF) and the sheet key of each. */
    public record Sections(int[] bounds, List<String> keys) {
        /** The section the camera is in: the first bound above it (Section Art Load.asm:11-17). */
        public String keyAt(int cameraX) {
            int section = 0;
            while (section < bounds.length - 1 && (cameraX & 0xFFFF) >= bounds[section]) section++;
            return keys.get(Math.min(section, keys.size() - 1));
        }
    }

    /** Registers the sheets where the stage's object index starts the projector. */
    public static void register(CdObjectArtProvider art) throws IOException {
        CdStagePayload payload = art.payload();
        if (!ProjectorObjectCatalogue.indexedIn(payload)) return;
        ProjectorVariant variant = ProjectorVariant.of(payload.stage().zone());
        if (variant == null) return;
        CdStageImage stage = art.image();
        RomByteReader reader = stage.reader();
        int plcLists = CdStageCatalogue.entry(payload).plcLists();
        int ranges = sectionRanges(payload);
        List<CdStageVram> vrams = new ArrayList<>();
        int[] bounds = {0xFFFF};
        if (ranges >= 0) {
            // SectionRanges, SectionUpdatePLCs, SectionInitPLCs: equal-length word lists, the first ending in $FFFF.
            List<Integer> list = new ArrayList<>();
            int words = 0;
            while (true) {
                int value = stage.u16(ranges + words * 2);
                words++;
                list.add(value);
                if (value == 0xFFFF || words > 16) break;
            }
            bounds = list.stream().mapToInt(Integer::intValue).toArray();
            int init = ranges + 4 * words;
            for (int k = 0; k < words; k++) {
                CdStageVram vram = CdObjectArtProvider.loadVram(stage, payload);
                vram.loadPlc(stage, plcLists, stage.u16(init + k * 2));
                vrams.add(vram);
            }
        } else {
            CdStageVram vram = CdObjectArtProvider.loadVram(stage, payload);
            vram.loadPlc(stage, plcLists, TITLE_CARD_PLC);
            vrams.add(vram);
        }
        List<String> keys = new ArrayList<>();
        int tile = variant.tile(payload.stage().actNumber() - 1);
        for (int k = 0; k < vrams.size(); k++) {
            String key = PROJECTOR + k;
            art.register(key, art.sheet(reader, ProjectorObjectCatalogue.mappings(payload), tile, vrams.get(k)));
            keys.add(key);
        }
        final int[] finalBounds = bounds;
        art.zoneArt(Sections.class, () -> new Sections(finalBounds, keys));
    }

    /** The sheet key for the camera's section, or {@code null} where no sheet is registered. */
    public static String key(CdObjectArtProvider art, int cameraX) {
        Sections sections = art == null ? null : art.zoneArt(Sections.class);
        return sections == null ? null : sections.keyAt(cameraX);
    }

    /** The zone's {@code SectionRanges} table (its own catalogue anchors it), or -1 where the stage has none. */
    private static int sectionRanges(CdStagePayload payload) {
        return switch (payload.stage().zone()) {
            case PALMTREE_PANIC -> com.openggf.game.soniccd.objects.ppz.PpzObjectCatalogue.entry(payload).sectionRanges();
            case COLLISION_CHAOS -> com.openggf.game.soniccd.objects.ccz.CczObjectCatalogue.offset(payload, "sectionRanges");
            case TIDAL_TEMPEST -> com.openggf.game.soniccd.objects.ttz.TtzObjectCatalogue.offset(payload, "sectionRanges");
            case QUARTZ_QUADRANT -> com.openggf.game.soniccd.objects.qqz.QqzObjectCatalogue.offset(payload, "sectionRanges");
            case STARDUST_SPEEDWAY -> com.openggf.game.soniccd.objects.ssz.SszObjectCatalogue.offset(payload, "sectionRanges");
            case WACKY_WORKBENCH, METALLIC_MADNESS -> -1;
        };
    }
}
