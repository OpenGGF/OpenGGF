package com.openggf.game.sonic2.specialstage;

import com.openggf.data.Rom;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.level.Pattern;
import com.openggf.tests.RomTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Whole ROM pieces, captured owner outputs and fixed H32 capacity, without GL. */
class TestSonic2SpecialStageSpriteAssembly {
    private static Sonic2SpecialStageDataLoader loader;
    private static Rom sourceRom;
    private static final Sonic2SpecialStageSpriteAssembly.ArtBases ART =
            new Sonic2SpecialStageSpriteAssembly.ArtBases(0x1000, 0x2000, 0x3000, 0x4000,
                    0x5000, 0x6000, 0x7000, 0x8000, 0x9000, 0xA000, 0xB000, 0xC000, 0xD000);
    private static final Sonic2SpecialStageRenderer.RingHudState TEAM =
            new Sonic2SpecialStageRenderer.RingHudState(true, true, 999, 999, 1998);

    @BeforeAll
    static void originalRom() throws Exception {
        var file = RomTestUtils.ensureSonic2RomAvailable();
        assumeTrue(file != null, "Original S2 ROM required");
        var rom = new Rom();
        assertTrue(rom.open(file.getAbsolutePath()));
        // The data loader owns the pipeline during these immutable ROM queries.
        loader = new Sonic2SpecialStageDataLoader(rom);
        sourceRom = rom;
    }

    @Test
    void submitsAscendingBucketsThenSlotsAndNativePieceOrderWithSeparateReplayOwnership() {
        var ring = state(9, Kind.RING, 3, 120, 100, 9, ArtRole.RING);
        var sonic = state(0, Kind.SONIC, 3, 80, 100, 0, ArtRole.SONIC);
        var tail = state(7, Kind.TAILS_TAIL, 1, 150, 100, 0, ArtRole.TAILS_TAIL);
        var groups = assembly(32).assemble(List.of(ring, sonic, tail), TEAM, List.of(), 0, 0);
        assertEquals(List.of(7, 0, 0, 0, 9), groups.stream().map(g -> g.owner().slot()).toList());
        assertTrue(groups.stream().allMatch(g -> g.geometry().size() == 1),
                "overlapping pieces must never borrow the neighbouring piece's art fragments");
        var body = groups.subList(1, 4);
        assertEquals(List.of(4, 3, 1), body.stream().map(g -> g.geometry().getFirst().piece().widthTiles()).toList());
        assertEquals(List.of(4, 3, 2), body.stream().map(g -> g.geometry().getFirst().piece().heightTiles()).toList());
        assertEquals(List.of(16, 9, 2), body.stream().map(g -> g.fragments().size()).toList());
        for (var group : groups) {
            var shape = group.geometry().getFirst().piece();
            assertEquals(shape.widthTiles() * shape.heightTiles(), group.fragments().size());
            assertTrue(group.fragments().stream().allMatch(f -> f.x() >= shape.x() && f.y() >= shape.y()
                    && f.endXExclusive() <= shape.endXExclusive() && f.endYExclusive() <= shape.endYExclusive()));
        }
    }

    @Test
    void asymmetricPlayerPiecesMirrorTheirGeometryAndKeepEachSourceFragmentWithItsOwnPiece() {
        var nativeState = state(0, Kind.SONIC, 3, 100, 100, 0, ArtRole.SONIC);
        var flipped = new RenderState(nativeState.identity(), 100, 100, 0, true, true, true, ArtRole.SONIC);
        var groups = assembly(32).assemble(List.of(flipped), TEAM, List.of(), 0, 0);
        assertEquals(List.of(116, 124, 116), groups.stream().map(g -> g.geometry().getFirst().piece().x()).toList());
        assertEquals(List.of(96, 72, 80), groups.stream().map(g -> g.geometry().getFirst().piece().y()).toList());
        assertEquals(140, groups.getFirst().fragments().getFirst().x());
        assertEquals(120, groups.getFirst().fragments().getFirst().y());
        assertEquals(140, groups.get(1).fragments().getFirst().x());
        assertEquals(88, groups.get(1).fragments().getFirst().y());
        assertTrue(groups.stream().flatMap(g -> g.fragments().stream()).allMatch(f -> f.hFlip() && f.vFlip()));
    }

    @Test
    void hudUsesTenNativePiecesAndCountersUseTheirCapturedPublicationRatherThanCurrentRings() {
        var hud = state(2, Kind.HUD, 0, 0, 0, -1, ArtRole.HUD);
        var fixed = state(4, Kind.PLAYER_COUNTER, 0, 0, 0, -1, ArtRole.NUMBER);
        var total = state(20, Kind.TOTAL_COUNTER, 0, 0, 0, -1, ArtRole.NUMBER);
        var outputs = List.of(new Sonic2SpecialStageIntro.NativeCounterPayload(fixed.identity().handle(), true,
                        List.of(new Sonic2SpecialStageIntro.NativeCounterDigit(0x48, 0x20, 9),
                                new Sonic2SpecialStageIntro.NativeCounterDigit(0xE0, 0x20, 0))),
                new Sonic2SpecialStageIntro.NativeCounterPayload(total.identity().handle(), true,
                        List.of(new Sonic2SpecialStageIntro.NativeCounterDigit(0x7C, 0x20, 1),
                                new Sonic2SpecialStageIntro.NativeCounterDigit(0x84, 0x20, 0))));
        var groups = assembly(0).assemble(List.of(total, fixed, hud), TEAM, outputs, 0, 0);
        assertEquals(14, groups.size());
        assertEquals(10, groups.stream().filter(g -> g.owner().slot() == 2).count());
        assertEquals(List.of(0x44, 0xDC), groups.stream().filter(g -> g.owner().slot() == 4)
                .map(g -> g.geometry().getFirst().piece().x()).toList());
        assertEquals(List.of(ART.hud() + 0x24, ART.hud() + 0x12), groups.stream()
                .filter(g -> g.owner().slot() == 4).map(g -> g.fragments().getFirst().firstPatternIndex()).toList());
        assertEquals(ART.tailsText(), groups.get(4).fragments().getFirst().firstPatternIndex(),
                "native HUD frame2's $5AA offset addresses TAILS art at $7A4");
        assertEquals(4, groups.get(8).geometry().getFirst().piece().widthTiles());
        assertEquals(4, groups.get(8).geometry().getFirst().piece().heightTiles());
        assertTrue(assembly(0).assemble(List.of(fixed), TEAM,
                List.of(new Sonic2SpecialStageIntro.NativeCounterPayload(fixed.identity().handle(), false,
                        outputs.getFirst().digits())), 0, 0).isEmpty(), "init has no DisplaySprite");
    }

    @Test
    void retail101BcdUsesUnitsFirstAndNativeOutOfTableSignedCountSkip() {
        var number = state(8, Kind.RINGS_TO_GO_NUMBER, 0, 0, 0, -1, ArtRole.NUMBER);
        var groups = assembly(0).assemble(List.of(number), TEAM, List.of(), 0x02F7, 3);
        assertEquals(List.of(0x56, 0x46), groups.stream().map(g -> g.geometry().getFirst().piece().x()).toList());
        assertEquals(List.of(ART.hud() + 0x20, ART.hud() + 0x16), groups.stream()
                .map(g -> g.fragments().getFirst().firstPatternIndex()).toList());
        var invalidDigit = loader.getNativeSpriteFrame(Sonic2SpecialStageRomMappings.Family.DIGITS, 15);
        assertEquals(0x72CE, invalidDigit.romAddress());
        assertTrue(invalidDigit.pieces().isEmpty(), "$A800-1 is negative: BuildSprites skips, not digit10");
    }

    @Test
    void h32CountPixelAndTableLimitsStayFixedAcrossOuterWidthsAndOffscreenEntries() {
        for (int outerWidth : List.of(256, 320, 640)) {
            int origin = (outerWidth - 256) / 2;
            List<RenderState> rings = new ArrayList<>();
            for (int slot = 8; slot < 25; slot++) rings.add(state(slot, Kind.RING, 3, 100, 100, 0, ArtRole.RING));
            assertEquals(16, admit(assembly(origin).assemble(rings, TEAM, List.of(), 0, 0), origin).size());
            List<RenderState> bombs = new ArrayList<>();
            for (int slot = 8; slot < 17; slot++) {
                // Negative X still consumes32 fetched pixels, even with no visible art.
                bombs.add(state(slot, Kind.BOMB, 3, slot == 8 ? -80 : 80, 100, 9, ArtRole.BOMB));
            }
            var pixelLimited = admit(assembly(origin).assemble(bombs, TEAM, List.of(), 0, 0), origin);
            assertEquals(7, pixelLimited.size(), "offscreen32 pixels consume the eighth fetched piece");
            assertEquals(224, pixelLimited.stream().mapToInt(e -> e.piece().widthTiles() * 8).sum());
            List<RenderState> table = new ArrayList<>();
            for (int slot = 8; slot < 73; slot++) table.add(state(slot, Kind.RING, 3, 100,
                    slot == 72 ? 100 : -40, 0, ArtRole.RING));
            assertTrue(admit(assembly(origin).assemble(table, TEAM, List.of(), 0, 0), origin).isEmpty(),
                    "64 offscreen table entries still exclude the later visible owner");
        }
    }

    @Test
    void nativeDisplayListCapsOwnersBeforePieceExpansionAndLeavesTableSpaceForTheNextBucket() {
        List<RenderState> owners = new ArrayList<>();
        for (int slot = 8; slot < 72; slot++) {
            owners.add(state(slot, Kind.RING, 0, 100, slot == 71 ? 50 : 0, 0, ArtRole.RING));
        }
        owners.add(state(72, Kind.RING, 1, 100, 100, 0, ArtRole.RING));
        var groups = assembly(0).assemble(owners, TEAM, List.of(), 0, 0);
        assertEquals(64, groups.size());
        assertFalse(groups.stream().anyMatch(g -> g.owner().slot() == 71),
                "the64th owner was rejected by DisplaySprite, before the H32 table was built");
        assertEquals(72, groups.getLast().owner().slot());
        assertTrue(admit(groups, 0).stream().anyMatch(piece -> piece.piece().y() == 96),
                "the next bucket's owner occupies the remaining64th hardware entry");
    }

    @Test
    void shadowFamilyKeepsNativeMappingSizePaletteAndArtOrientationInTheSameOrder() {
        var shadow = state(30, Kind.OBJECT_SHADOW, 5, 100, 100, 1, ArtRole.SHADOW);
        var hand = state(20, Kind.HAND, 0, 128, 72, 21, ArtRole.MESSAGES);
        var groups = assembly(32).assemble(List.of(shadow, hand), TEAM, List.of(), 0, 0);
        assertEquals(5, groups.size());
        assertEquals(List.of(20, 20, 20, 20, 30), groups.stream().map(g -> g.owner().slot()).toList());
        var effect = groups.getLast();
        assertTrue(effect.shadow());
        assertEquals(4, effect.geometry().getFirst().piece().widthTiles());
        assertEquals(4, effect.geometry().getFirst().piece().heightTiles());
        assertEquals(3, effect.geometry().getFirst().piece().paletteIndex());
        assertEquals(ART.shadowDiag() + 0x2A, effect.fragments().getFirst().firstPatternIndex());
        assertTrue(effect.fragments().stream().allMatch(f -> f.paletteIndex() == 3 && f.piecePriority()));
    }

    /**
     * Stock World REV01 qualification for the narrow native SHADOW_15 consumer.
     * Follows assembled, effective-palette fragments into the supplied ROM art;
     * player fragments use the production loader's ROM DPLC translation.
     * This is a pre-admission inventory: H32 clipping can remove these pixels,
     * and cannot introduce another source index. It does not qualify donor art,
     * results-screen objects, unsupported raw mapping bytes or a general S/H API.
     */
    @Test
    void stockNormalPaletteThreeFragmentsNeverReferenceHighlightOrShadowOperatorPixels() throws Exception {
        var inventory = new NativeOperatorInventory();
        inventory.addArt("player/DPLC", ART.player(), loader.getPlayerArtPatterns());
        inventory.addArt("HUD", ART.hud(), loader.getHudArtPatterns());
        inventory.addArt("START", ART.start(), loader.getStartArtPatterns());
        inventory.addArt("messages", ART.messages(), loader.getMessagesArtPatterns());
        inventory.addArt("TAILS-name", ART.tailsText(), loader.getTailsTextArtPatterns());
        inventory.addArt("ring", ART.ring(), loader.getRingArtPatterns());
        inventory.addArt("bomb", ART.bomb(), loader.getBombArtPatterns());
        inventory.addArt("stars", ART.stars(), loader.getStarsArtPatterns());
        inventory.addArt("explosion", ART.explosion(), loader.getExplosionArtPatterns());
        inventory.addArt("emerald", ART.emerald(), loader.getEmeraldArtPatterns());
        inventory.addArt("shadow-flat", ART.shadowFlat(), loader.getShadowHorizPatterns());
        inventory.addArt("shadow-diagonal", ART.shadowDiag(), loader.getShadowDiagPatterns());
        inventory.addArt("shadow-side", ART.shadowSide(), loader.getShadowVertPatterns());
        var cases = List.of(
                new NativeMappingInventoryCase(Kind.SONIC, ArtRole.SONIC, Sonic2SpecialStageRomMappings.Family.SONIC, 0, 18),
                new NativeMappingInventoryCase(Kind.TAILS, ArtRole.TAILS, Sonic2SpecialStageRomMappings.Family.TAILS, 0, 18),
                new NativeMappingInventoryCase(Kind.TAILS_TAIL, ArtRole.TAILS_TAIL, Sonic2SpecialStageRomMappings.Family.TAILS_TAILS, 0, 21),
                new NativeMappingInventoryCase(Kind.OBJECT_SHADOW, ArtRole.SHADOW, Sonic2SpecialStageRomMappings.Family.SHADOW, 0, 30),
                new NativeMappingInventoryCase(Kind.BANNER_PIECE, ArtRole.START_BANNER, Sonic2SpecialStageRomMappings.Family.START_BANNER, 0, 9),
                new NativeMappingInventoryCase(Kind.MESSAGE_LETTER, ArtRole.MESSAGES, Sonic2SpecialStageRomMappings.Family.MESSAGE, 0, 20),
                new NativeMappingInventoryCase(Kind.WING, ArtRole.MESSAGES, Sonic2SpecialStageRomMappings.Family.MESSAGE, 20, 21),
                new NativeMappingInventoryCase(Kind.HAND, ArtRole.MESSAGES, Sonic2SpecialStageRomMappings.Family.MESSAGE, 21, 22),
                new NativeMappingInventoryCase(Kind.MESSAGE_DIGIT, ArtRole.NUMBER, Sonic2SpecialStageRomMappings.Family.DIGITS, 0, 11),
                // Ani_obj5B_obj60 (s2.asm:72727-72760) switches art for $1E-$20;
                // Ani_obj61 (s2.asm:72768-72801) switches art for $0A-$0C.
                new NativeMappingInventoryCase(Kind.RING, ArtRole.RING, Sonic2SpecialStageRomMappings.Family.RING, 0, 30),
                new NativeMappingInventoryCase(Kind.RING, ArtRole.STARS, Sonic2SpecialStageRomMappings.Family.RING, 30, 33),
                new NativeMappingInventoryCase(Kind.BOMB, ArtRole.BOMB, Sonic2SpecialStageRomMappings.Family.BOMB, 0, 10),
                new NativeMappingInventoryCase(Kind.BOMB, ArtRole.EXPLOSION, Sonic2SpecialStageRomMappings.Family.BOMB, 10, 13),
                new NativeMappingInventoryCase(Kind.EMERALD, ArtRole.EMERALD, Sonic2SpecialStageRomMappings.Family.EMERALD, 0, 10));
        // Ordinary entry and checkpoint dispatch select GET plus phrases2/4/6.
        // Follow the shipped word/phrase tables, not every mapping's presence:
        // frames12..16/19 reference VS-only ArtNem_SpecialPlayerVSPlayer at $3DF.
        var ordinaryMessages = ordinaryMessageFrames();
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 17, 18), ordinaryMessages);
        var contexts = List.of(TEAM,
                new Sonic2SpecialStageRenderer.RingHudState(true, false, 999, 0, 999),
                new Sonic2SpecialStageRenderer.RingHudState(false, true, 0, 999, 999));
        for (int context = 0; context < contexts.size(); context++) {
            var team = contexts.get(context);
            for (var selection : cases) {
                assertTrue(selection.end() <= selection.family().frameCount());
                for (int frame = selection.first(); frame < selection.end(); frame++) {
                    if (selection.kind() == Kind.MESSAGE_LETTER && !ordinaryMessages.contains(frame)) continue;
                    for (int flips = 0; flips < 4; flips++) {
                        var base = state(8, selection.kind(), 3, 100, 100, frame, selection.role());
                        var owner = new RenderState(base.identity(), 100, 100, frame,
                                (flips & 1) != 0, (flips & 2) != 0, true, selection.role());
                        inventory.inspect("team=" + context + "/flips=" + flips + "/" + selection.kind(),
                                selection.role(), selection.family(), frame,
                                assembly(32).assemble(List.of(owner), team, List.of(), 0, 0));
                    }
                }
            }
            // The supported World/English HUD path emits frames0,2,3 by team,
            // rather than honoring the state's mappingFrame. Raw frame1/MILES
            // has no assembly path here and is explicitly outside this claim.
            var hud = state(2, Kind.HUD, 0, 0, 0, -1, ArtRole.HUD);
            var groups = assembly(32).assemble(List.of(hud), team, List.of(), 0, 0);
            int group = 0;
            for (int frame : team.sonicActive() && team.tailsActive() ? new int[]{0, 2, 3}
                    : team.sonicActive() ? new int[]{0} : new int[]{2}) {
                var hudFamily = Sonic2SpecialStageRomMappings.Family.HUD;
                int count = loader.getNativeSpriteFrame(hudFamily, frame).pieces().size();
                inventory.inspect("HUD/team=" + context, ArtRole.HUD, hudFamily, frame,
                        groups.subList(group, group + count));
                group += count;
            }
            assertEquals(groups.size(), group);
            // Follow the captured counter-output branch as well as direct digits.
            for (var kind : List.of(Kind.PLAYER_COUNTER, Kind.TOTAL_COUNTER, Kind.RINGS_TO_GO_NUMBER)) {
                for (int digit = 0; digit < 10; digit++) {
                    var owner = state(8, kind, 0, 0, 0, -1, ArtRole.NUMBER);
                    var payload = new Sonic2SpecialStageIntro.NativeCounterPayload(owner.identity().handle(), true,
                            List.of(new Sonic2SpecialStageIntro.NativeCounterDigit(100, 100, digit)));
                    inventory.inspect(kind + "/team=" + context, ArtRole.NUMBER,
                            Sonic2SpecialStageRomMappings.Family.DIGITS, digit,
                            assembly(32).assemble(List.of(owner), team, List.of(payload), digit, 1));
                }
            }
        }
        assertEquals(EnumSet.allOf(ArtRole.class), inventory.roles);
        assertTrue(inventory.normalPaletteThreeFragments > 0, "must actually inspect normal palette3 fragments");
        assertTrue(inventory.normalPaletteThreeRoles.containsAll(EnumSet.of(ArtRole.RING, ArtRole.EMERALD)));
        assertTrue(inventory.shadowFifteenPixels > 0,
                "independent control: the same ROM decoding observes index15 in intentionally shadow-owned art");
        assertTrue(inventory.operatorReferences.isEmpty(), () ->
                "NORMAL effective palette3 references S/H operators: " + inventory.operatorReferences.size()
                        + " distinct references; first examples=" + inventory.examples.values());
        System.out.printf("S2 submitted-fragment inventory: %d mapping cases, %d fragments, "
                        + "%d NORMAL palette3 fragments, %d unique NORMAL palette3 source tiles, "
                        + "%d excluded shadow index15 pixels%n", inventory.mappingCases, inventory.fragments,
                inventory.normalPaletteThreeFragments, inventory.normalPaletteThreeTiles.size(), inventory.shadowFifteenPixels);
    }

    private static Set<Integer> ordinaryMessageFrames() throws Exception {
        // Obj5A_CreateRingReqMessage (s2.asm:72261-72309): GET and RINGS!.
        // loc_35978 (s2.asm:71959-71973): COOL! or NOT ENOUGH RINGS....
        // SSMessage_TextFrames / SSMessage_TextPhrases retain their actual ROM offsets.
        Set<Integer> words = new HashSet<>(Set.of(0));
        for (int phrase : new int[]{2, 4, 6}) {
            int address = 0x35DDE + (short) sourceRom.read16BitAddr(0x35DDE + phrase);
            readTerminatedRomList(address, words);
        }
        Set<Integer> frames = new HashSet<>();
        for (int word : words) {
            int address = 0x35C62 + (short) sourceRom.read16BitAddr(0x35C62 + word);
            readTerminatedRomList(address, frames);
        }
        return Set.copyOf(frames);
    }

    private static void readTerminatedRomList(int address, Set<Integer> values) throws Exception {
        for (int count = 0; count < 64; count++) {
            int value = sourceRom.readByte(address + count) & 255;
            if ((value & 128) != 0) return;
            values.add(value);
        }
        fail("unterminated native text list at $" + Integer.toHexString(address));
    }

    private record NativeMappingInventoryCase(Kind kind, ArtRole role,
            Sonic2SpecialStageRomMappings.Family family, int first, int end) { }

    private record NativeInventoryPattern(String art, int tile, Pattern pattern) { }

    private static final class NativeOperatorInventory {
        private final Map<Integer, NativeInventoryPattern> resident = new HashMap<>();
        private final EnumSet<ArtRole> roles = EnumSet.noneOf(ArtRole.class);
        private final EnumSet<ArtRole> normalPaletteThreeRoles = EnumSet.noneOf(ArtRole.class);
        private final Set<Integer> normalPaletteThreeTiles = new HashSet<>();
        private final Set<String> operatorReferences = new HashSet<>();
        private final Map<String, String> examples = new LinkedHashMap<>();
        private int mappingCases, fragments, normalPaletteThreeFragments, shadowFifteenPixels;

        void addArt(String name, int base, Pattern[] patterns) {
            assertTrue(patterns.length > 0, "empty supplied-ROM art bank " + name);
            for (int tile = 0; tile < patterns.length; tile++)
                assertNull(resident.put(base + tile, new NativeInventoryPattern(name, tile, patterns[tile])),
                        "inventory art address overlap at " + Integer.toHexString(base + tile));
        }

        void inspect(String context, ArtRole role, Sonic2SpecialStageRomMappings.Family family,
                     int frame, List<Sonic2SpecialStageSpriteAssembly.Group> groups) {
            var nativeFrame = loader.getNativeSpriteFrame(family, frame);
            assertEquals(nativeFrame.pieces().size(), groups.size(), context + "/" + role + "/" + frame);
            roles.add(role);
            mappingCases++;
            for (int piece = 0; piece < groups.size(); piece++) {
                var group = groups.get(piece);
                for (var fragment : group.fragments()) {
                    fragments++;
                    boolean normalThree = !group.shadow() && fragment.paletteIndex() == 3;
                    if (normalThree) { normalPaletteThreeFragments++; normalPaletteThreeRoles.add(role); }
                    for (int col = fragment.startColTile(); col < fragment.startColTile() + fragment.colCountTiles(); col++) {
                        for (int row = fragment.startRowTile(); row < fragment.startRowTile() + fragment.rowCountTiles(); row++) {
                            int sourceCol = fragment.hFlip() ? fragment.widthTiles() - 1 - col : col;
                            int sourceRow = fragment.vFlip() ? fragment.heightTiles() - 1 - row : row;
                            int address = fragment.firstPatternIndex() + sourceCol * fragment.heightTiles() + sourceRow;
                            var source = resident.get(address);
                            String reference = role + "/" + family + "/frame=" + frame + "/mapROM=$"
                                    + Integer.toHexString(nativeFrame.romAddress()) + "/piece=" + piece
                                    + "/pattern=$" + Integer.toHexString(address)
                                    + "/priority=" + fragment.piecePriority();
                            assertNotNull(source, () -> "unresolved actually submitted art: " + context + "/" + reference);
                            if (normalThree) normalPaletteThreeTiles.add(address);
                            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                                    int index = source.pattern().getPixel(x, y) & 0xF;
                                    if (group.shadow() && fragment.paletteIndex() == 3 && index == 15) shadowFifteenPixels++;
                                    if (normalThree && (index == 14 || index == 15)) {
                                        String key = reference + "/index=" + index;
                                        operatorReferences.add(key);
                                        if (examples.size() < 16) examples.putIfAbsent(key,
                                                context + "/" + reference + "/palette=3/" + source.art()
                                                        + "[" + source.tile() + "]/pixel=" + x + "," + y + "/index=" + index);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static Sonic2SpecialStageSpriteAssembly assembly(int origin) {
        return new Sonic2SpecialStageSpriteAssembly(loader, ART, origin);
    }

    private static RenderState state(int slot, Kind kind, int priority, int x, int y, int frame, ArtRole role) {
        return new RenderState(new Entry(new Handle(slot, 1), kind, 1, priority, 0, null, 2, false),
                x, y, frame, false, false, true, role);
    }

    private static List<SpriteSatEntry> admit(List<Sonic2SpecialStageSpriteAssembly.Group> groups, int origin) {
        return SpriteSatMaskPostProcessor.process(groups.stream().flatMap(g -> g.geometry().stream()).toList(),
                false, SpriteSatMaskPostProcessor.Raster.h32(origin, 0, 224));
    }
}
