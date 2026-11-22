package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdStageCatalogue;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.art.CdStageVram;
import com.openggf.game.soniccd.objects.ttz.ObjElevatorSwitch;
import com.openggf.game.soniccd.objects.ttz.ObjSwitchElevator;
import com.openggf.game.soniccd.objects.ttz.ObjTtzSwing;
import com.openggf.game.soniccd.objects.ttz.TtzObjectArt;
import com.openggf.game.soniccd.objects.ttz.TtzSwitch;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TestTtzPresentPlatforms {
    @TempDir Path tempDir;
    private EngineContext previous;
    private CdDiscDataSource disc;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }

    @Test
    void swingSpawnsFromDiscAndReturnsAfterOneFullTurn() throws Exception {
        bootAt(0x4BD, 0x1C0, 0x29);
        runner.stepIdleFrames(3);
        var pivot = active(ObjTtzSwing.class, object -> object.getSpawn().x() == 0x480);
        assertEquals(new Point(0x480, 0x180), point(pivot)); // StageObjects (r4/stage_object_maps_1.asm:38-41)
        assertEquals(0x10, pivot.getSpawn().subtype());
        assertEquals(7, active(ObjTtzSwing.class).size());
        var seat = swingSeat();
        var button = active(TtzSwitch.class, object -> object.getSpawn().x() == 0);
        assertEquals(new Point(seat.getX() + 16, seat.getY() - 16), point(button));
        rewindIdentical(12);

        // sub_20D0F2 (objects/r4/swing.asm:127-159): the $10 subtype adds one
        // step per frame. Take the rightmost point as angle zero.
        for (int i = 0; i < 256 && point(seat).x() != 0x4E0; i++) {
            runner.stepIdleFrames(1);
            seat = swingSeat();
        }
        assertEquals(new Point(0x4E0, 0x180), point(seat));
        var image = art().image();
        // CalcSine (Level/Functions (Misc).asm:69-76) reaches SineTable at $7AA in the USA R41A MMD.
        int sineTable = 0x7AA;
        for (int step = 1; step <= 256; step++) {
            runner.stepIdleFrames(1);
            int angle = step & 0xFF;
            int cos = (short) image.u16(sineTable + 2 * (angle + 0x40));
            int sin = (short) image.u16(sineTable + 2 * angle);
            assertEquals(new Point(0x480 + ((cos * 6) >> 4), 0x180 + ((sin * 6) >> 4)),
                    point(swingSeat()), "swing angle " + angle);
        }
    }

    @Test
    void swingCarriesSonicOnItsFarEnd() throws Exception {
        bootAt(0x4A0, 0xB8);
        sonic.setYSpeed((short) 0);
        sonic.setAir(false);
        runner.stepIdleFrames(3);
        var seat = swingSeat();
        for (int i = 0; i < 42 && !standingOn(seat); i++) runner.stepIdleFrames(1);
        assertTrue(standingOn(seat), "TopSolidObject catches Sonic");
        int playerX = sonic.getCentreX();
        int seatX = seat.getX();
        runner.stepIdleFrames(6);
        assertTrue(standingOn(seat));
        assertEquals(seat.getX() - seatX, sonic.getCentreX() - playerX);
        assertEquals(seat.getY() - seat.solidYRadius() - sonic.getYRadius() + 2, sonic.getCentreY());
    }

    @Test
    void pressureSwitchRaisesBothElevatorPiecesAndCarriesSonic() throws Exception {
        bootAt(0x9D0, 0x4C0, 0x30);
        runner.stepIdleFrames(3);
        var column = active(ObjSwitchElevator.class, object -> object.getSpawn().x() == 0x9E0);
        var side = active(ObjSwitchElevator.class, object -> object.getSpawn().x() == 0x9D0);
        var button = active(ObjElevatorSwitch.class, object -> true);
        assertEquals(0x540, column.getSpawn().y()); // StageObjects (r4/stage_object_maps_1.asm:93-94)
        assertEquals(0x9F0, column.getX());
        assertEquals(new Point(column.getX() - 32, column.getY() + 32), point(side));
        assertEquals(new Point(column.getX(), column.getY() - 68), point(button));
        assertFalse(button.pressed());

        dropAt(button.getX(), button.getY() - button.solidYRadius() - sonic.getYRadius() - 2);
        int startY = column.getY();
        int playerY = sonic.getCentreY();
        runner.stepIdleFrames(18);
        assertTrue(button.pressed());
        rewindIdentical(12);
        runner.stepIdleFrames(15);
        column = active(ObjSwitchElevator.class, object -> object.getSpawn().x() == 0x9E0);
        button = active(ObjElevatorSwitch.class, object -> true);
        side = active(ObjSwitchElevator.class, object -> object.getSpawn().x() == 0x9D0);
        assertTrue(button.pressed());
        assertEquals(-0x100, column.solidYVelocity());
        assertTrue(column.getY() < startY - 15);
        assertEquals(new Point(column.getX() - 32, column.getY() + 32), point(side));
        assertTrue(standingOn(button), "Sonic rides the rising pressure switch");
        assertTrue(sonic.getCentreY() < playerY - 15);

        dropAt(side.getX(), side.getY() - side.solidYRadius() - sonic.getYRadius() - 1);
        for (int i = 0; i < 8 && !standingOn(side); i++) runner.stepIdleFrames(1);
        assertTrue(standingOn(side), "Sonic can also ride the side platform");
        assertFalse(button.pressed());
        runner.stepIdleFrames(68);
        assertEquals(0x100, column.solidYVelocity());
    }

    @Test
    void appearanceUsesTheDiscMappingsAndPlcTiles() throws Exception {
        bootAt(0x460, 0x1A0);
        var art = art();
        var discVram = CdObjectArtProvider.loadVram(art.image(), art.payload());
        discVram.loadPlc(art.image(), CdStageCatalogue.entry(art.payload()).plcLists(), 2);
        // SectionGfxInitList0 (r4/gfx_lists_1a.asm:57-73) is PLC 2 in both placement windows.
        // SwingSprites (objects/r4/swing.asm:164-166), SwitchSprites2 (objects/r4/switch_elevator.asm:114-116)
        // and SwitchElevatorSprites (objects/r4/switch_elevator.asm:296-298) supply the mapping frames.
        assertSheet(art, discVram, TtzObjectArt.SWING, 0x0340, 0, 0, 2, -8, -8);
        assertSheet(art, discVram, TtzObjectArt.SWING, 0x0340, 0, 2, 2, -8, -8);
        assertSheet(art, discVram, TtzObjectArt.SWING, 0x4340, 2, 1, 2, -32, -8);
        assertSheet(art, discVram, TtzObjectArt.SWITCH_ELEVATOR, 0x446A, 2, 1, 4, -16, -64);
        assertSheet(art, discVram, TtzObjectArt.SWITCH_ELEVATOR, 0x446A, 2, 2, 2, -16, -32);
        assertSheet(art, discVram, TtzObjectArt.ELEVATOR_SWITCH, 0x050A, 0, 0, 2, -16, -12);
        assertSheet(art, discVram, TtzObjectArt.ELEVATOR_SWITCH, 0x050A, 0, 1, 2, -16, -4);
    }

    private static void assertSheet(CdObjectArtProvider art,
                                    CdStageVram discVram,
                                    String kind, int tile, int palette, int frame, int pieces,
                                    int x, int y) {
        String key = TtzObjectArt.key(art, kind, tile, 0);
        assertNotNull(key);
        var sheet = art.getSheet(key);
        assertEquals(palette, sheet.getPaletteIndex(), kind);
        var mapped = sheet.getFrame(frame).pieces();
        assertEquals(pieces, mapped.size(), kind);
        assertEquals(new Point(x, y), new Point(mapped.getFirst().xOffset(), mapped.getFirst().yOffset()));
        var patterns = sheet.getPatterns();
        var discPatterns = discVram.window(tile & 0x7FF, patterns.length);
        byte[] actual = new byte[patterns.length * Pattern.PATTERN_SIZE_IN_MEM];
        byte[] expected = new byte[actual.length];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i].copyInto(actual, i * Pattern.PATTERN_SIZE_IN_MEM);
            discPatterns[i].copyInto(expected, i * Pattern.PATTERN_SIZE_IN_MEM);
        }
        assertArrayEquals(expected, actual, kind);
        assertFalse(java.util.Arrays.equals(expected, new byte[expected.length]), kind + " disc tiles are populated");
    }

    private static CdObjectArtProvider art() {
        return (CdObjectArtProvider) GameServices.level().getObjectRenderManager().getArtProvider();
    }

    private void bootAt(int x, int y) throws Exception {
        bootAt(x, y, 0);
    }

    private void bootAt(int x, int y, int rewindLayoutId) throws Exception {
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        sonic = CdFixtureReplay.boot(tempDir, disc, CdStagePayload.R41A);
        runner = new HeadlessTestRunner(sonic);
        dropAt(x, y);
        GameServices.camera().updatePosition(true);
        if (rewindLayoutId != 0) {
            // Retain the disc placement, terrain and manager. Other R41A actors
            // can hold references to retired objects outside these rewind checks.
            var objects = GameServices.level().getObjectManager();
            var layout = objects.getAllSpawns().stream().filter(spawn -> spawn.objectId() == rewindLayoutId).toList();
            objects.resyncSpawnList(layout);
            objects.reset(GameServices.camera().getX());
            objects.setRewindInPlaceRestoreEnabledForTest(false);
            var contacts = GameServices.solidExecutionRegistry();
            contacts.beginFrame(GameServices.level().getFrameCounter(), List.of(sonic));
            contacts.finishFrame();
        } else {
            ObjectManagerInternalAccess.refreshPlacementAfterRelocation(GameServices.level().getObjectManager());
        }
        GameServices.level().updateObjectPositions();
    }

    private void dropAt(int x, int y) {
        NativePositionOps.writeXPosResetSubpixel(sonic, x);
        NativePositionOps.writeYPosResetSubpixel(sonic, y);
        sonic.setAir(true);
        sonic.setXSpeed((short) 0);
        sonic.setYSpeed((short) 0x180);
        sonic.setGSpeed((short) 0);
    }

    private boolean standingOn(AbstractObjectInstance object) {
        return GameServices.level().getObjectManager().hasObjectStandingBit(sonic, object);
    }

    private ObjTtzSwing swingSeat() {
        return active(ObjTtzSwing.class, object -> object.solidXVelocity() != 0 || object.solidYVelocity() != 0);
    }

    private static <T> T active(Class<T> type, Predicate<T> filter) {
        return active(type).stream().filter(filter).findFirst()
                .orElseThrow(() -> new AssertionError("Missing live " + type.getSimpleName()));
    }

    private static <T> List<T> active(Class<T> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed()).filter(type::isInstance).map(type::cast).toList();
    }

    private record Point(int x, int y) {}

    private static Point point(AbstractObjectInstance object) {
        return new Point(object.getX(), object.getY());
    }

    private void rewindIdentical(int frames) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var before = graph();
        CompositeSnapshot snapshot = registry.capture();
        var first = sample(frames);
        registry.restore(snapshot);
        assertEquals(before, graph(), "restore platform positions, velocity, pressure and rider");
        var second = sample(frames);
        assertEquals(first, second, "restored motion repeats on the same input");
    }

    private List<List<String>> sample(int frames) {
        List<List<String>> result = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            runner.stepIdleFrames(1);
            result.add(graph());
        }
        return result;
    }

    private List<String> graph() {
        var state = new ArrayList<String>();
        state.add(sonic.getCentreX() + "," + sonic.getCentreY() + "," + sonic.isOnObject());
        for (var object : active(AbstractObjectInstance.class)) {
            if (object instanceof ObjTtzSwing || object instanceof ObjSwitchElevator || object instanceof ObjElevatorSwitch
                    || object instanceof TtzSwitch && object.getSpawn().x() == 0) {
                var solid = (ReevaluatingSolidObject) object;
                state.add(object.getSlotIndex() + " " + point(object) + " " + solid.solidXVelocity() + " "
                        + solid.solidYVelocity() + " " + standingOn(object)
                        + " " + (object instanceof ObjElevatorSwitch button && button.pressed()));
            }
        }
        return state;
    }
}
