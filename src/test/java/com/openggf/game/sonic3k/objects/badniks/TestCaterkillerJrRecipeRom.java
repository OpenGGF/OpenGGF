package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.*;
import com.openggf.mods.objects.CaterkillerJrFactory;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Short local ROM art/contact proofs, independent of campaign routes or desktop rendering. */
@RequiresRom(SonicGame.SONIC_3K)
class TestCaterkillerJrRecipeRom {
    private ObjectManager manager;
    private Sonic sonic;
    private Tails tails;
    private GameStateManager score;
    private Sonic3kObjectArtProvider art;

    private CaterkillerJrHeadInstance setup(int act) throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(0, act).withSkippedZoneIntro().build();
        sonic = new Sonic("sonic", (short) 400, (short) 200);
        tails = new Tails("tails", (short) 400, (short) 200);
        tails.setCpuControlled(true);
        score = new GameStateManager();
        var camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 900; }
            @Override public short getHeight() { return 500; }
            @Override public AbstractPlayableSprite getFocusedSprite() { return sonic; }
        };
        art = (Sonic3kObjectArtProvider) GameServices.module().getObjectArtProvider();
        var render = new ObjectRenderManager(art);
        var module = new Sonic3kGameModule();
        var services = new TestObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public ObjectRenderManager renderManager() { return render; }
        }.withCamera(camera).withGameState(score).withGameModule(module)
                .withDebugOverlay(mock(DebugOverlayManager.class)).withLevelManager(GameServices.level()).withSidekicks(List.of(tails));
        var table = new TouchResponseTable(com.openggf.data.RomByteReader.fromRom(TestEnvironment.currentRom()),
                Sonic3kConstants.TOUCH_SIZES_ADDR, Sonic3kConstants.TOUCH_SIZES_COUNT);
        manager = new ObjectManager(List.of(), module.createObjectRegistry(), 0, null, table,
                GraphicsManager.getInstance(), camera, services);
        AbstractObjectInstance.updateCameraBounds(0, 0, 900, 500, 0);
        var head = manager.createDynamicObject(() -> (CaterkillerJrHeadInstance) CaterkillerJrFactory.create(
                new ObjectSpawn(400, 200, 0, 0, 0, false, 200, -1,
                        "second-strike", "second-strike:caterkiller"), CaterkillerJrRecipe.DOUBLE_LENGTH));
        head.update(0, sonic); head.update(1, sonic);
        return head;
    }

    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void allTwelvePartsDrawThroughTheNativeRomArtProviderInBothAizActs(int act) throws Exception {
        var head = setup(act);
        assertEquals(12, bodies().size());
        assertSame(GameServices.module().getObjectArtProvider(), art);
        for (String key : List.of(Sonic3kObjectArtKeys.CATERKILLER_JR, Sonic3kObjectArtKeys.MONKEY_DUDE)) {
            var sheet = art.getSheet(key);
            assertNotNull(sheet);
            assertTrue(sheet.getPatterns().length > 0);
            assertEquals(1, sheet.getPaletteIndex());
            assertTrue(art.getRenderer(key).isReady());
        }
        var parts = new ArrayList<AbstractObjectInstance>();
        parts.add(head); parts.addAll(bodies());
        for (var part : parts) {
            var presentation = SpritePresentation.prepare(GraphicsManager.getInstance(), 0, 0,
                    () -> part.appendRenderCommands(new ArrayList<>()), descriptor -> {
                        var d = (PatternDesc) descriptor;
                        return new SpritePresentation.Attributes(d.getPaletteIndex(), d.getHFlip(), d.getVFlip(), d.getPriority());
                    });
            assertFalse(presentation.tiles().isEmpty(), "each native head/body/tail mapping must draw");
            assertTrue(presentation.tiles().stream().allMatch(tile -> tile.palette() == 1));
        }
    }

    @Test void rollingMainAndNativeSidekickAreHurtByBodyButFinalHeadHitKeepsNativeBounce() throws Exception {
        var head = setup(0);
        for (int frame = 2; frame < 150; frame++) {
            for (var object : List.copyOf(manager.getActiveObjects())) object.update(frame, sonic);
        }
        var tail = bodies().getLast();
        attackPosition(sonic, tail.getX(), tail.getY());
        attackPosition(tails, tail.getX(), tail.getY());
        sonic.setRingCount(1);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 150);
        manager.runTouchResponsesForPlayer(tails, 150);
        assertTrue(sonic.isHurt(), "rolled main player still takes native HURT contact");
        assertTrue(tails.isHurt(), "real Tails occupies native P2 hurt-only path");
        assertFalse(head.isDestroyed());
        assertEquals(12, bodies().size());
        assertEquals(0, score.getScore());
        assertEquals(0x98, tail.getCollisionFlags());
        sonic.setHurt(false); sonic.setInvulnerableFrames(0);
        attackPosition(sonic, head.getX(), head.getY());
        NativePositionOps.writeXPosPreserveSubpixel(tails, 850);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(sonic, 151);
        assertTrue(head.isDestroyed());
        assertEquals(0, bodies().size());
        assertEquals(100, score.getScore());
        assertEquals(0x300, sonic.getYSpeed(), "stock final bounce subtracts $100 at/below head");
        manager.runTouchResponsesForPlayer(sonic, 151);
        assertEquals(100, score.getScore(), "head collision cannot award a duplicate kill");
    }

    private void attackPosition(AbstractPlayableSprite player, int x, int y) {
        player.setAir(true); player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
        NativePositionOps.writeXPosPreserveSubpixel(player, x);
        NativePositionOps.writeYPosPreserveSubpixel(player, y);
        player.setXSpeed((short) 0x280); player.setYSpeed((short) 0x400); player.setGSpeed((short) 0x280);
    }
    private List<CaterkillerJrBodyInstance> bodies() {
        return manager.getActiveObjects().stream().filter(CaterkillerJrBodyInstance.class::isInstance)
                .filter(object -> !object.isDestroyed()).map(CaterkillerJrBodyInstance.class::cast)
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }
}
