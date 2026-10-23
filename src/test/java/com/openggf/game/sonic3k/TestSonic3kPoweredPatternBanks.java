package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SuperState;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;


import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kPoweredPatternBanks {
    private RomByteReader reader;

    @BeforeEach
    void setup() throws Exception {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().initHeadless();
        GameServices.level().resetLevelGamestate(GameModuleRegistry.getCurrent().createLevelState());
        GameServices.sprites().clearAllSprites();
        for (int i = 0; i < 7; i++) GameServices.gameState().markEmeraldCollected(i);
        reader = RomByteReader.fromRom(TestEnvironment.currentRom());
    }

    @Test
    void sonicMainAndTwoPoweredOwnersKeepIndependentFullBanksAcrossRevertAndRewind() throws Exception {
        verifyTeam(new Sonic("sonic", (short) 0, (short) 0));
    }

    @Test
    void knucklesMainAndPoweredSonicKeepIndependentFullBanksAcrossRevertAndRewind() throws Exception {
        verifyTeam(new Knuckles("knuckles", (short) 0, (short) 0));
    }

    private void verifyTeam(AbstractPlayableSprite main) throws Exception {
        GameServices.sprites().addSprite(main, main instanceof Knuckles ? "knuckles" : "sonic");
        var art = new Sonic3kPlayerArt(reader).loadFormArtSet("sonic", S3kFormTier.NORMAL);
        assertEquals(29, art.bankSize());
        main.setSpriteRenderer(new PlayerSpriteRenderer(main instanceof Knuckles
                ? new Sonic3kPlayerArt(reader).loadFormArtSet("knuckles", S3kFormTier.NORMAL) : art));
        Sonic first = secondary(art, "sonic_p2");
        Sonic second = secondary(art, "sonic_p3");
        var normal = first.getSpriteRenderer();
        var firstController = controller(first);
        var secondController = controller(second);
        activate(firstController);
        activate(secondController);
        var powered = first.getSpriteRenderer();
        assertEquals(33, powered.patternBankCapacity());
        assertTrue(PatternAtlasRange.SIDEKICK_BANKS.contains(powered.patternBankBase()));
        assertTrue(powered.patternBankBase() >= second.getSpriteRenderer().patternBankBase()
                + second.getSpriteRenderer().patternBankCapacity()
                || second.getSpriteRenderer().patternBankBase() >= powered.patternBankBase() + 33);
        assertTrue(powered.patternBankBase() >= normal.patternBankBase() + normal.patternBankCapacity());
        assertEquals(0x680, main.getSpriteRenderer().patternBankBase());
        assertPublishedPixels(main, first, second);
        var snapshot = first.captureRewindState();
        firstController.debugDeactivate();
        assertSame(normal, first.getSpriteRenderer());
        first.restoreRewindState(snapshot);
        assertSame(powered, first.getSpriteRenderer());
        assertPublishedPixels(main, first, second);
        firstController.debugDeactivate();
        first.setRingCount(50);
        // The movement loop expires the one-frame revert invincibility grace.
        first.setInvincibleFrames(0);
        activate(firstController);
        assertSame(powered, first.getSpriteRenderer(), "repeated activation must retain the allocated bank");
        if (main instanceof Sonic) {
            activate(controller(main));
            assertEquals(0x680, main.getSpriteRenderer().patternBankBase(), "native Player_1 addressing stays native");
            assertPublishedPixels(main, first, second);
        }
    }

    private void assertPublishedPixels(AbstractPlayableSprite... players) throws Exception {
        var graphics = GameServices.graphics();
        assertEquals(320, graphics.getProjectionWidth());
        assertEquals(224, graphics.getConfiguredScreenHeightPx());
        int[][] origins = new int[players.length][2];
        int[] lineEntries = new int[224];
        int[] linePixels = new int[224];
        int nextTop = 8;
        int tableEntries = 0;
        int expectedTiles = 0;
        var romArt = new Sonic3kPlayerArt(reader);
        var expected = new java.util.HashMap<Integer, com.openggf.graphics.SpritePresentation.PatternVersion>();
        for (int i = 0; i < players.length; i++) {
            int frame = i + 1;
            var renderer = players[i].getSpriteRenderer();
            var bounds = renderer.getFrameBounds(frame, false, false);
            assertTrue(bounds.width() > 0 && bounds.height() > 0, "the ROM frame must have geometry");
            int originX = 8 - bounds.minX();
            int originY = nextTop - bounds.minY();
            origins[i][0] = originX;
            origins[i][1] = originY;
            assertTrue(originX + bounds.minX() >= 0 && originX + bounds.maxX() < 320);
            assertTrue(originY + bounds.minY() >= 0 && originY + bounds.maxY() < 224,
                    "all nonoverlapping ROM frames must fit the original native raster");
            nextTop += bounds.height() + 8;

            // Count the unprocessed ROM pieces, independently of SAT admission.
            // Strictly less than 320 pixels also prevents next-line mask carry.
            var tier = players[i].getSuperStateController() instanceof Sonic3kSuperStateController form
                    ? form.getActiveFormTier() : S3kFormTier.NORMAL;
            var mapping = romArt.loadFormArtSet(players[i] instanceof Knuckles ? "knuckles" : "sonic", tier)
                    .mappingFrames().get(frame);
            assertEquals(bounds, com.openggf.level.render.SpritePieceRenderer.computeFrameBounds(
                    mapping.pieces(), false, false));
            tableEntries += mapping.pieces().size();
            int frameTiles = 0;
            for (var piece : mapping.pieces()) {
                assertTrue(piece.widthTiles() > 0 && piece.heightTiles() > 0);
                frameTiles += piece.widthTiles() * piece.heightTiles();
                int top = originY + piece.yOffset();
                for (int y = top; y < top + piece.heightTiles() * 8; y++) {
                    lineEntries[y]++;
                    linePixels[y] += piece.widthTiles() * 8;
                }
            }
            var isolated = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0,
                    () -> renderer.drawFrame(frame, originX, originY, false, false));
            assertEquals(frameTiles, isolated.tiles().size(), "the isolated source contains every ROM mapping tile");
            assertFalse(isolated.patternVersions().isEmpty());
            expectedTiles += frameTiles;
            expected.putAll(isolated.patternVersions());
        }
        assertTrue(tableEntries <= 80, "unprocessed ROM pieces fit the native H40 SAT table");
        for (int y = 0; y < 224; y++) {
            assertTrue(lineEntries[y] <= 20, "unprocessed ROM pieces fit the native scanline entry budget");
            assertTrue(linePixels[y] < 320, "unprocessed ROM pieces cannot clip or arm pixel-budget carry");
        }
        for (boolean deferred : new boolean[]{false, true}) {
            for (boolean reversed : new boolean[]{false, true}) {
                var combined = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                    if (deferred) graphics.beginSpriteSatCollection();
                    for (int j = 0; j < players.length; j++) {
                        int i = reversed ? players.length - 1 - j : j;
                        players[i].getSpriteRenderer().drawFrame(i + 1, origins[i][0], origins[i][1], false, false);
                    }
                    if (deferred) graphics.endSpriteSatCollectionAndReplay();
                });
                assertEquals(expected, combined.patternVersions(), () -> patternDifference(
                        expected, combined.patternVersions(), deferred, reversed, players));
                assertEquals(expectedTiles, combined.tiles().size(), "the combined pass retains every source tile");
                for (var tile : combined.tiles()) assertFalse(tile.rowClipped(), "the native raster clips no source row");
            }
        }
    }

    private String patternDifference(
            java.util.Map<Integer, com.openggf.graphics.SpritePresentation.PatternVersion> expected,
            java.util.Map<Integer, com.openggf.graphics.SpritePresentation.PatternVersion> actual,
            boolean deferred, boolean reversed, AbstractPlayableSprite[] players) {
        var missing = new java.util.TreeSet<>(expected.keySet());
        missing.removeAll(actual.keySet());
        var extra = new java.util.TreeSet<>(actual.keySet());
        extra.removeAll(expected.keySet());
        var changed = new java.util.TreeSet<Integer>();
        for (int id : expected.keySet()) {
            if (actual.containsKey(id) && !expected.get(id).equals(actual.get(id))) changed.add(id);
        }
        var message = new StringBuilder("each owner must retain its isolated ROM pixels; deferred=")
                .append(deferred).append(" reversed=").append(reversed)
                .append(" expectedKeys=").append(expected.size()).append(" actualKeys=").append(actual.size());
        for (int i = 0; i < players.length; i++) {
            var renderer = players[i].getSpriteRenderer();
            message.append("; owner=").append(i).append(" code=").append(players[i].getCode())
                    .append(" base=0x").append(Integer.toHexString(renderer.patternBankBase()))
                    .append(" cap=").append(renderer.patternBankCapacity()).append(" frame=").append(i + 1)
                    .append(" bounds=").append(renderer.getFrameBounds(i + 1, false, false));
        }
        message.append("; missing=").append(missing.size()).append(patternIds(missing, players))
                .append(" extra=").append(extra.size()).append(patternIds(extra, players))
                .append(" sharedDifferent=").append(changed.size()).append(patternIds(changed, players));
        if (!changed.isEmpty()) {
            int id = changed.first();
            message.append(" firstChangedExpected=").append(expected.get(id))
                    .append(" firstChangedActual=").append(actual.get(id));
        }
        return message.toString();
    }

    private String patternIds(java.util.Set<Integer> ids, AbstractPlayableSprite[] players) {
        return ids.stream().limit(8).map(id -> {
            for (int i = 0; i < players.length; i++) {
                var renderer = players[i].getSpriteRenderer();
                int offset = id - renderer.patternBankBase();
                if (offset >= 0 && offset < renderer.patternBankCapacity())
                    return "0x%X(owner=%d,+0x%X)".formatted(id, i, offset);
            }
            return "0x%X(unowned)".formatted(id);
        }).toList().toString();
    }

    private Sonic secondary(SpriteArtSet art, String name) {
        int base = GameServices.level().reserveSidekickPatternBank(art.bankSize());
        var shifted = new SpriteArtSet(art.artTiles(), art.mappingFrames(), art.dplcFrames(),
                art.paletteIndex(), base, art.frameDelay(), art.bankSize(), art.animationProfile(), art.animationSet());
        Sonic player = new Sonic(name, (short) 0, (short) 0);
        player.setCpuControlled(true);
        player.setSpriteRenderer(new PlayerSpriteRenderer(shifted));
        return player;
    }

    private Sonic3kSuperStateController controller(AbstractPlayableSprite player) {
        player.setRingCount(50);
        var controller = new Sonic3kSuperStateController(player);
        player.setSuperStateController(controller);
        controller.loadRomData(reader);
        return controller;
    }

    private void activate(Sonic3kSuperStateController controller) {
        assertTrue(controller.activateFromAirAbility());
        for (int i = 0; i < 30; i++) controller.update();
        assertEquals(SuperState.SUPER, controller.getState());
    }
}
