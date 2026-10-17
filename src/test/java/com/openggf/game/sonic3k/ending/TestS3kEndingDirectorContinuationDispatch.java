package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Draft by the aiz5-slots lane (2026-10-16, pinned e94f6c4e7), executed by the ending-pal lane;
 * negative-gate cases added there.
 * Full-table Create_Continue_Sprite writes issued by the rescue director's OWN
 * routines, not by direct createGroup calls:
 * <ul>
 *   <li>loc_5DB76 (sonic3k.asm:123878-123895, skdisasm 044fa46): routine $C
 *       hold expiry, word_5DBB6 x2 subtype $42, then Queue_Kos_Module.</li>
 *   <li>loc_5DD6A (:124031-124066): routine $1C expiry. Unless Player_mode 3
 *       or _unkFA88 < 0, six tables (18 entries: $41x6, $43x2, $44x4,
 *       $45/$46/$47 x2).</li>
 *   <li>Create_Continue_Sprite (:126846-126858): unchecked AllocateObject,
 *       five partial writes (code, subtype $2C, $2D = 4*entry, x_pos, y_pos).</li>
 * </ul>
 * Legal SST93 victims come from real native allocations, never synthetic owners.
 * For loc_5DB76 it is the catcher, admitted by fixed Tornado8 (an existing
 * declared prefix); D01 flight children do not exist before routine $14->$16.
 * For loc_5DD6A it is the small propeller, admitted at routine $18->$1A; it
 * retires only on flags38 bit1, written at $1E->$20 after the call (director
 * :140-153). Free SSTs are filled with IslandLift owners, the declared-pressure
 * boundary already accepted by TestS3kEndingNativeGraphPressure.
 * Expectations come from the independent byte-RAM oracle and loc_5EDAC/
 * loc_5EE18/loc_5EE86 literal writes (:125364-125453).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingDirectorContinuationDispatch {
    private static final int TABLE_5DBB6 = 0x5DBB6;
    private static final int[][] LOC_5DD6A = {
            {0x5DDD2, 0x41, 6}, {0x5DDEA, 0x43, 2}, {0x5DDF2, 0x44, 4},
            {0x5DE02, 0x45, 2}, {0x5DE0A, 0x46, 2}, {0x5DE12, 0x47, 2}};

    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void directorHoldExpiryOverwritesTheLiveCatcherAtSst93InItsOwnPass() throws Exception {
        var f = boot("sonic");
        var manager = GameServices.level().getObjectManager();
        // Existing declared prefix: plane8 and Master48 reserved; the catcher is the third allocation, at SST93.
        fillLeavingPrefix(3, List.of(8, 48));
        f.stepFrame(false, false, false, false, false);
        var catcher = assertInstanceOf(EndingRescueDirectorObjectInstance.Actor.class,
                NativeSstSlotAccess.currentOwner(manager, 93));
        assertEquals(EndingRescueDirectorObjectInstance.Actor.CATCHER, (int) get(catcher, "role"));

        until(f, 12, true);
        assertSame(catcher, NativeSstSlotAccess.currentOwner(manager, 93),
                "precondition: the catcher still owns SST93 when loc_5DB76's timer expires");
        assertTrue(owner().getSlotIndex() < 93, "precondition: SST93 is reached after the director in this pass");
        NativeSstState residue = catcher.nativeSstState();
        var identity = manager.objectQuery().identityOf(catcher).orElseThrow();
        byte[] expected93 = oracleAfter(manager, residue, new int[][]{{TABLE_5DBB6, 0x42, 2}});
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();

        f.stepFrame(false, false, false, false, false);

        assertEquals(14, owner().routine(), "loc_5DB76 advances routine to $E");
        var lift = assertInstanceOf(EndingIslandLiftObjectInstance.class, NativeSstSlotAccess.currentOwner(manager, 93));
        assertWrittenFields(expected93, lift);
        assertEquals(identity, manager.objectQuery().identityOf(lift).orElseThrow(),
                "the physical SST keeps its numeric identity");
        assertFalse(catcher.isDestroyed(), "an unchecked code write is not Delete_Current_Sprite");
        // loc_5EE18 ran in the same pass: mapping 4, $24 = $2D, no bit2 for Player_mode != 3;
        // loc_5EDAC wrote priority $300 / art $E2FF; status, flags38, fractions and anim_frame are inherited.
        assertEquals(new NativeSstState(residue.renderFlags(), residue.xFraction(), residue.yFraction(), 4,
                        residue.animationFrame(), 4, residue.flags38(), residue.status(), 0x300, 0xE2FF),
                lift.nativeSstState());
        assertRestoreAndReplay(f, registry, before);
    }

    @ParameterizedTest @ValueSource(strings = {"sonic", "tails"})
    void directorRoutine1CExpiryWritesEveryTableEntryInOrderOverTheLiveSmallPropeller(String main) throws Exception {
        var f = boot(main);
        var manager = GameServices.level().getObjectManager();
        until(f, 24, false);
        for (int n = 0; n < 1000 && owner().getX() != 0x220; n++) f.stepFrame(false, false, false, false, false);
        assertEquals(24, owner().routine());
        assertEquals(0x220, owner().getX(), "the next director pass admits the small-plane group");
        for (int slot = 4; slot < 93; slot++)
            if (NativeSstSlotAccess.currentOwner(manager, slot) == null)
                assertNotNull(manager.createDynamicObjectAtSlot(
                        () -> new EndingIslandLiftObjectInstance(0x41, 0, 0x100, 0x100), slot));
        assertNull(NativeSstSlotAccess.currentOwner(manager, 93));
        f.stepFrame(false, false, false, false, false);
        var propeller = assertInstanceOf(EndingKnucklesFlightChildObjectInstance.class,
                NativeSstSlotAccess.currentOwner(manager, 93));
        assertEquals(EndingKnucklesFlightChildObjectInstance.SMALL_PROPELLER, propeller.role(),
                "loc_5DD0E's first CreateChild takes the only SST after the director");

        until(f, 28, true);
        assertSame(propeller, NativeSstSlotAccess.currentOwner(manager, 93),
                "precondition: the small propeller is still live; its flags38 bit1 retirement follows routine $1E");
        NativeSstState residue = propeller.nativeSstState();
        var identity = manager.objectQuery().identityOf(propeller).orElseThrow();
        // Holes left by native flag4 retirements and any failed palette admission are taken first, in table order.
        List<Integer> holes = new ArrayList<>();
        for (int slot = 4; slot < 93; slot++) if (NativeSstSlotAccess.currentOwner(manager, slot) == null) holes.add(slot);
        assertTrue(holes.size() < 18, "precondition: the call overflows into SST93");
        var oracle = oracleFor(manager, residue);
        for (int[] table : LOC_5DD6A) oracle.group(table[0], table[1], table[2]);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();

        f.stepFrame(false, false, false, false, false);

        assertEquals(30, owner().routine(), "loc_5DD6A advances routine to $1E");
        for (int hole : holes) {
            var lift = assertInstanceOf(EndingIslandLiftObjectInstance.class, NativeSstSlotAccess.currentOwner(manager, hole),
                    "one scan per entry admits hole " + hole);
            assertWrittenFields(oracle.slot(hole), lift);
        }
        var last = assertInstanceOf(EndingIslandLiftObjectInstance.class, NativeSstSlotAccess.currentOwner(manager, 93));
        assertWrittenFields(oracle.slot(93), last);
        assertEquals(0x47, last.getSpawn().subtype(), "the last overflow entry owns SST93");
        assertEquals(4, (int) get(last, "nativeOrdinalOffset"), "$2D is the byte offset 4, not ordinal 1");
        assertEquals(identity, manager.objectQuery().identityOf(last).orElseThrow());
        assertFalse(propeller.isDestroyed(), "displacement is not deletion");
        // loc_5EE86 ran in the same pass: no mapping write, $24 = $C, bit2 set, art bit15 cleared after $E2FF.
        assertEquals(new NativeSstState(residue.renderFlags() | 4, residue.xFraction(), residue.yFraction(),
                        residue.mappingFrame(), residue.animationFrame(), 0x0C, residue.flags38(), residue.status(),
                        0x300, 0x62FF),
                last.nativeSstState());
        NativeSstState displaced = propeller.nativeSstState();
        for (int n = 0; n < 4; n++) f.stepFrame(false, false, false, false, false);
        assertEquals(displaced, propeller.nativeSstState(), "the displaced Java owner is no longer dispatched");
        assertRestoreAndReplay(f, registry, before);
    }

    /**
     * loc_5DD6A (044fa46 sonic3k.asm:124031-124040) writes routine $1E and Events_fg_4, then returns
     * before any Create_Continue_Sprite when Player_mode is 3 or _unkFA88 is negative. The real
     * routine $1C expiry must therefore leave every SST owner untouched.
     */
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"sonic,false", "knuckles,true"})
    void directorRoutine1CExpirySkipsEveryTableForNegativeProgressOrPlayerMode3(String main, boolean superEmeralds)
            throws Exception {
        var f = bootWith(main, superEmeralds);
        var manager = GameServices.level().getObjectManager();
        var state = ((com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState) GameServices.zoneRuntimeState()).ending();
        until(f, 28, true);
        assertEquals(superEmeralds ? 2 : -2, state.progress(), "precondition: declared native progress");
        var owners = new ArrayList<Object>();
        for (int slot = 0; slot < 94; slot++) owners.add(NativeSstSlotAccess.currentOwner(manager, slot));
        long lifts = manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).size();
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();

        f.stepFrame(false, false, false, false, false);

        assertEquals(30, owner().routine(), "loc_5DD6A still advances routine to $1E");
        assertEquals(lifts, manager.activeObjectsOfType(EndingIslandLiftObjectInstance.class).size(),
                "no Create_Continue_Sprite entry");
        for (int slot = 4; slot < 94; slot++) {
            var now = NativeSstSlotAccess.currentOwner(manager, slot);
            assertFalse(now instanceof EndingIslandLiftObjectInstance && now != owners.get(slot),
                    "no IslandLift write at SST" + slot);
        }
        assertRestoreAndReplay(f, registry, before);
    }

    private HeadlessTestFixture bootWith(String main, boolean superEmeralds) {
        var c = SonicConfigurationService.getInstance();
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameServices.camera().refreshViewportDimensions(c);
        var f = HeadlessTestFixture.builder().withZoneAndAct(13, 1).withFreshLevelStartLifecycle().build();
        if (superEmeralds) {
            var game = GameServices.gameState();
            game.configureSpecialStageProgress(7, 7);
            for (int i = 0; i < 7; i++) { game.markEmeraldCollected(i); game.markSuperEmeraldCollected(i); }
        }
        return f;
    }

    private void assertRestoreAndReplay(HeadlessTestFixture f, com.openggf.game.rewind.RewindRegistry registry,
                                        CompositeSnapshot before) {
        CompositeSnapshot firstAfter = null, firstForward = null;
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            same(before, registry.capture());
            f.stepFrame(false, false, false, false, false);
            CompositeSnapshot after = registry.capture();
            if (firstAfter == null) firstAfter = after; else same(firstAfter, after);
            for (int n = 0; n < 20; n++) f.stepFrame(false, false, false, false, false);
            CompositeSnapshot forward = registry.capture();
            if (firstForward == null) firstForward = forward; else same(firstForward, forward);
            registry.restore(before);
            same(before, registry.capture());
            registry.restore(after);
            same(after, registry.capture());
            for (int n = 0; n < 20; n++) f.stepFrame(false, false, false, false, false);
            same(forward, registry.capture());
        }
    }

    private static void assertWrittenFields(byte[] raw, EndingIslandLiftObjectInstance lift) throws Exception {
        assertEquals(0x5EDAC, ContinueSpriteRamOracle.u32(raw, 0));
        assertEquals(raw[0x2C] & 255, lift.getSpawn().subtype());
        assertEquals(raw[0x2D] & 255, (int) get(lift, "nativeOrdinalOffset"));
        assertEquals(ContinueSpriteRamOracle.u16(raw, 0x10), lift.getX());
        assertEquals(ContinueSpriteRamOracle.u16(raw, 0x14), lift.getY());
    }

    /** Byte-RAM model of the live table: occupied SSTs nonzero, SST93 carries the victim's consumed header. */
    private static ContinueSpriteRamOracle oracleFor(ObjectManager manager, NativeSstState victim) throws Exception {
        var oracle = new ContinueSpriteRamOracle(TestEnvironment.objectServices().romReader());
        for (int slot = 4; slot < 93; slot++) {
            byte[] raw = new byte[0x4A];
            if (NativeSstSlotAccess.currentOwner(manager, slot) != null) ContinueSpriteRamOracle.put32(raw, 0, 1);
            oracle.putSlot(slot, raw);
        }
        oracle.putSlot(93, rawHeader(victim));
        return oracle;
    }

    private static byte[] oracleAfter(ObjectManager manager, NativeSstState victim, int[][] tables) throws Exception {
        var oracle = oracleFor(manager, victim);
        for (int[] table : tables) oracle.group(table[0], table[1], table[2]);
        return oracle.slot(93);
    }

    private HeadlessTestFixture boot(String main) {
        var c = SonicConfigurationService.getInstance();
        c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        GameServices.camera().refreshViewportDimensions(c);
        var f = HeadlessTestFixture.builder().withZoneAndAct(13, 1).withFreshLevelStartLifecycle().build();
        var game = GameServices.gameState();
        game.configureSpecialStageProgress(7, 7);
        for (int i = 0; i < 7; i++) { game.markEmeraldCollected(i); game.markSuperEmeraldCollected(i); }
        return f;
    }

    private EndingRescueDirectorObjectInstance owner() {
        return GameServices.level().getObjectManager()
                .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst();
    }

    private void until(HeadlessTestFixture f, int routine, boolean lastTimer) throws Exception {
        for (int n = 0; n < 5000; n++) {
            if (owner().routine() == routine && (!lastTimer || (int) get(owner(), "timer") == 0)) return;
            f.stepFrame(false, false, false, false, false);
        }
        fail("native phase " + routine + " must admit before the finite cap");
    }

    /** Same declared prefix as TestS3kEndingNativeGraphPressure#fillLeavingPrefix. */
    private void fillLeavingPrefix(int count, List<Integer> requiredFree) {
        var manager = GameServices.level().getObjectManager();
        var free = new ArrayList<Integer>(requiredFree);
        for (int slot = 5; slot < 93 && free.size() < count - 1; slot++)
            if (!free.contains(slot) && NativeSstSlotAccess.currentOwner(manager, slot) == null) free.add(slot);
        assertEquals(count - 1, free.size());
        for (int slot = 4; slot < 93; slot++)
            if (!free.contains(slot) && NativeSstSlotAccess.currentOwner(manager, slot) == null)
                assertNotNull(manager.createDynamicObjectAtSlot(
                        () -> new EndingIslandLiftObjectInstance(0x41, 0, 0x100, 0x100), slot));
        assertNull(NativeSstSlotAccess.currentOwner(manager, 93));
    }

    private static byte[] rawHeader(NativeSstState s) {
        byte[] raw = new byte[0x4A];
        ContinueSpriteRamOracle.put32(raw, 0, 1);
        raw[4] = (byte) s.renderFlags();
        ContinueSpriteRamOracle.put16(raw, 8, s.priority());
        ContinueSpriteRamOracle.put16(raw, 0xA, s.artTile());
        ContinueSpriteRamOracle.put16(raw, 0x12, s.xFraction());
        ContinueSpriteRamOracle.put16(raw, 0x16, s.yFraction());
        raw[0x22] = (byte) s.mappingFrame();
        raw[0x23] = (byte) s.animationFrame();
        raw[0x24] = (byte) s.animationTimer();
        raw[0x38] = (byte) s.flags38();
        raw[0x2A] = (byte) s.status();
        return raw;
    }

    private static Object get(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void same(CompositeSnapshot a, CompositeSnapshot b) {
        assertEquals(a.entries().keySet(), b.entries().keySet());
        for (String key : a.entries().keySet())
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(), key);
    }
}
