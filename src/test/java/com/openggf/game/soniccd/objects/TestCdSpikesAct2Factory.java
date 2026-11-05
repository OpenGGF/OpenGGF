package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SOURCE ONLY; Java/JUnit UNRUN by this author. Actual retained original-USA R12A factory inputs use
 * the configured CdProgramAssets loader, never source-tree/runtime fallback bytes. N1ecc5f3a:
 * ObjSpikes_Init/Main (Level/_Objects/Spikes/Main.asm:19-83), SetObjectTileID (Object Tile ID Set.asm:7-17),
 * ObjectTileIDs/ObjectTileID_06 (Palmtree Panic/Object Tile IDs (Act 2 Present).asm:7-40),
 * StandOnObject (Object Solid.asm:65-146). The R12A Init STORE operand at$20BF58 selects$20C018.
 *
 * Constructed Actor/slot ingress and labelled A-format terrain below exercise the genuine shared Solid
 * and Hurt algorithms; they are not natural-route, native execution or physics parity evidence. Public
 * callbacks simulate only component publication and Draw. They certify no Frame/World owner, origin,
 * private receipt, once-only adoption, FM ledger, current art or render behavior. No Outcome/d0 is invented
 * and no private pool/placement Snapshot is constructed. Existing component cases are untouched.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdSpikesAct2Factory {
    private static final int SLOT = 40;
    private static Media retained;

    @Test void actualRetainedBodyAssetsBorrowThe74ByteTableCachedVisualAnd20C018Metadata() throws Exception {
        var objects = media().terrain().objects();
        var assets = CdSpikes.BodyAssets.fromAct2Assets(objects);
        assertSame(objects.view(CdR12aProfile.Asset.OBJECT_TILE_IDS), assets.objectTileIds());
        assertSame(objects.spikes(), assets.visual());
        assertSame(assets.visual(), CdSpikes.BodyAssets.fromAct2Assets(objects).visual());
        assertEquals(74, assets.objectTileIds().length());
        assertEquals(0x0032, assets.objectTileIds().unsignedWord(0x14));
        assertEquals(0x0330, assets.objectTileIds().unsignedWord(0x32));
        assertEquals(0x20C018, assets.mappingsAddress());
        assertArrayEquals(HexFormat.of().parseHex("000204f0050000f000050004f0f005000000000500040000"),
                objects.view(CdR12aProfile.Asset.MAP_SPR_SPIKES).copy());
        assertEquals(1, assets.visual().mappingFrames().size());
        assertEquals(4, assets.visual().mappingFrames().getFirst().pieces().size());
        assertTrue(assets.visual().animations().isEmpty());
        var h = new H();
        assertTrue(h.calls.isEmpty()); assertTrue(h.events.isEmpty());
        assertThrows(NullPointerException.class, () -> CdSpikes.BodyAssets.fromAct2Assets(null));
    }

    @Test void actualFactoryInitPreservesEveryDirtyNonwriteAndSetsVflipCollision83() throws Exception {
        for (int flip = 0; flip < 4; flip++) {
            var h = new H();
            h.put(dirty(h.state()).moveByte(ID, 0x26).moveByte(ROUTINE, 0).moveByte(SUBTYPE2, 0)
                    .moveByte(SPRITE_FLAGS, flip).moveByte(SAVED_FLAGS_ID, 0).moveWord(X, 0x1000).moveWord(Y, 0x200));
            h.p.y = 0;
            var before = h.placement.capture(); var old = h.state(); var result = h.run();
            assertArrayEquals(initBytes(old, true), result.state().copyBytes(), "all64 bytes, including unnamed dirt");
            assertEquals(0x20C018, result.state().longBits(MAPPINGS)); assertEquals(0x0330, result.state().word(TILE));
            assertEquals(flip < 2 ? old.unsignedByte(COLLISION_TYPE) : 0x83, result.state().unsignedByte(COLLISION_TYPE));
            assertAllImages(before, h, result.state(), List.of());
            assertTrue(h.calls.isEmpty()); assertEquals(List.of("draw"), h.events); assertFalse(result.deleted());
        }
    }

    @Test void retainedFactoryKeepsTheReachedSecondaryBoundaryAfterOnlyTheInitPrefix() throws Exception {
        for (int secondary : new int[]{1, 0x80, 0xFF}) {
            var h = new H(); h.put(dirty(h.state()).moveByte(ID, 0x26).moveByte(ROUTINE, 0)
                    .moveByte(SUBTYPE2, secondary).moveByte(SAVED_FLAGS_ID, 0));
            var before = h.placement.capture(); var old = h.state();
            var failure = assertThrows(CdSpikes.UnsupportedSpikes.class, () -> h.body.run(SLOT, world(), null));
            assertEquals(CdSpikes.UnsupportedBoundary.TILE_ID_SUBTYPE2, failure.boundary());
            assertEquals(SLOT, failure.wholeIndex()); assertArrayEquals(initBytes(old, false), h.state().copyBytes());
            assertAllImages(before, h, h.state(), List.of()); assertNoLater(h); assertTrue(h.calls.isEmpty());
        }
    }

    @Test void unsignedDy32AndWrapCasesNeverReachSolidOrHurtButStillDraw() throws Exception {
        for (int[] pair : new int[][]{{1000, 968}, {1000, 1032}, {65535, 0}, {0, 65535}}) {
            var h = new H(); h.put(h.state().moveWord(Y, pair[0]).moveByte(SPRITE_FLAGS, 4)); h.p.y = pair[1] << 16;
            var before = h.placement.capture(); var expected = h.p.player(); var result = h.run();
            assertTrue(h.calls.isEmpty()); assertEquals(List.of("draw"), h.events);
            assertEquals(expected, result.player()); assertEquals(0, h.rewinds.size()); assertEquals(0, h.hurts.size());
            assertAllImages(before, h, before.pool().slots().get(SLOT - 32), List.of());
        }
    }

    @Test void actualR12aMovementDebugPathReturnsGenuinePlainSolidCompletedBeforeDraw() throws Exception {
        var source = CdStagePlayerSource.fromAct2(media().terrain(), media().player());
        var h = new H(new CdSolidObject(CdPlayerMovement.fromStageSource(source)));
        h.debug = 0x100; h.put(h.state().moveByte(SOLID_TYPE, 0xA5)); var before = h.placement.capture();
        var result = h.run(); var done = completed(h);
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT, done.entry());
        assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE), done.getOffReason());
        assertEquals(OptionalInt.of(0), done.d0()); assertEquals(0, result.state().unsignedByte(SOLID_TYPE));
        assertEquals(done.player(), h.livePlayer); assertEquals(done.globals(), h.liveGlobals);
        assertAllImages(before, h, afterDraw(done.object()), done.otherSlots());
        assertEquals(List.of("solid", "draw"), h.events); assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty());
    }

    @Test void constructedLandingPublishesActualOtherSlotReleaseAndGenuineD0BeforeHurtGate() throws Exception {
        var h = new H(); h.invincible = 1; h.p.flags = 8; h.p.stand = 41; h.p.yv = 0;
        h.pool().write(h.pool().state(41).moveByte(ID, 0).moveByte(FLAGS, 8).moveByte(SUBTYPE, 0xC7));
        var before = h.placement.capture(); var result = h.run(); var done = completed(h);
        assertEquals(CdSolidObject.Path.STAND_ON, done.path()); assertEquals(OptionalInt.of(1), done.d0());
        assertEquals(1, done.otherSlots().size()); assertEquals(0, h.pool().state(41).flags());
        assertEquals(0xC7, h.pool().state(41).subtype()); assertEquals(SLOT, result.player().standObject());
        assertAllImages(before, h, afterDraw(done.object()), done.otherSlots());
        assertEquals(List.of("solid", "draw"), h.events); assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty());
    }

    @Test void genuineStoppedStandReleasePublishesItsPrefixThenRethrowsTheIdenticalOriginal() throws Exception {
        for (int stand : new int[]{1, 5, 31, 128, 255}) {
            var h = new H(); h.p.flags = 8; h.p.stand = stand; h.p.yv = 0; h.put(h.state().moveByte(SOLID_TYPE, 2));
            var before = h.placement.capture();
            var failure = assertThrows(CdSolidObject.UnsupportedSolidObject.class, h::run);
            var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome());
            assertSame(stopped.stop().originalException(), failure); assertEquals(CdSolidObject.StopSite.STAND_RELEASE, stopped.stop().site());
            assertEquals(stand, assertInstanceOf(CdSolidObject.SlotAddress.class, stopped.stop().detail()).slotByte());
            assertEquals(8, stopped.prefix().object().flags()); assertEquals(stopped.prefix().player(), h.livePlayer);
            assertEquals(stopped.prefix().globals(), h.liveGlobals);
            assertAllImages(before, h, stopped.prefix().object(), stopped.prefix().otherSlots()); assertNoLater(h);
            assertEquals(List.of("solid"), h.events);
        }
    }

    @Test void constructedMissingFloorLeafPublishesTheRealPrefixBeforeItsSameProbeException() throws Exception {
        var terrain = new Terrain(); terrain.fg[2 + 42 + 16] = (byte) 0xF9;
        var h = new H(terrain.owner()); h.p.spr = 0x44; h.put(h.state().moveByte(SOLID_TYPE, 0xA7));
        var before = h.placement.capture(); var failure = assertThrows(IllegalStateException.class, h::run);
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, only(h).outcome());
        assertSame(stopped.stop().originalException(), failure); assertEquals(CdSolidObject.StopSite.FLOOR_PRIMARY, stopped.stop().site());
        assertEquals(stopped.prefix().player(), h.livePlayer); assertEquals(stopped.prefix().globals(), h.liveGlobals);
        assertAllImages(before, h, stopped.prefix().object(), stopped.prefix().otherSlots()); assertNoLater(h);
    }

    @Test void constructedSelfStandingCallsSolidThenDirectYThenHurtThenDrawUsingFreshActualCut() throws Exception {
        var h = new H(); h.p.flags = 8; h.p.stand = SLOT; h.put(h.state().moveByte(FLAGS, 8));
        var before = h.placement.capture(); var result = h.run(); var done = completed(h);
        assertEquals(OptionalInt.of(1), done.d0()); assertEquals(CdSolidObject.Path.STAND_ON, done.path());
        assertEquals(List.of("solid", "y", "hurt", "draw"), h.events);
        assertEquals(1, h.rewinds.size()); assertEquals(1, h.hurts.size());
        var rewind = h.rewinds.getFirst(); var hurt = h.hurts.getFirst();
        assertEquals(done.player().actor(), rewind.expectedActor());
        assertEquals(done.player().actor().motion().centreY16_16()
                - (((short) done.player().actor().motion().yVelocity()) << 8), rewind.finalYBits());
        assertEquals(rewind.finalYBits(), hurt.expectedActor().motion().centreY16_16());
        assertNotSame(only(h).actualCut(), hurt.callCut());
        assertEquals(hurt.sourceA2(), hurt.callCut().slots().get(SLOT - 32));
        assertEquals(4, result.player().actor().motion().routine()); assertEquals(0x78, result.player().actor().hurt());
        assertEquals(0, result.playerGlobals().shield()); assertTrue(hurt.result().changedSlots().isEmpty());
        assertAllImages(before, h, afterDraw(done.object()), done.otherSlots());
    }

    @Test void existingPostSolidGatesAndVflipSuppressOnlyTheDirectYAndHurtCallbacks() throws Exception {
        for (String gate : List.of("warp", "invincible", "hurtWord", "vflip")) {
            var h = new H();
            switch (gate) {
                case "warp" -> h.timeWarp = 0x80;
                case "invincible" -> h.invincible = 1;
                case "hurtWord" -> h.p.hurt = 0x100;
                case "vflip" -> h.put(h.state().moveByte(SPRITE_FLAGS, 0x86));
                default -> throw new AssertionError(gate);
            }
            var result = h.run(); var done = completed(h);
            assertEquals(OptionalInt.of(1), done.d0()); assertEquals(CdSolidObject.Path.STAND_ON, done.path());
            assertEquals(done.player(), result.player()); assertEquals(List.of("solid", "draw"), h.events);
            assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty());
        }
    }

    private static CdSpikes.Globals world() { return new CdSpikes.Globals(0, 0, 0x1080); }
    private static CdSpikes.PrefixSolidCall only(H h) { assertEquals(1, h.calls.size()); return h.calls.getFirst(); }
    private static CdSolidObject.Result completed(H h) {
        return assertInstanceOf(CdSolidObject.Completed.class, only(h).outcome()).completedResult();
    }
    private static CdObjectState afterDraw(CdObjectState state) { return state.moveByte(SPRITE_FLAGS, state.spriteFlags() & 0x7F); }
    private static void assertNoLater(H h) { assertTrue(h.draws.isEmpty()); assertTrue(h.rewinds.isEmpty()); assertTrue(h.hurts.isEmpty()); }
    private static void assertAllImages(CdObjectPlacementRuntime.Snapshot before, H h, CdObjectState own, List<CdObjectState> others) {
        var writes = new ArrayList<CdObjectState>(); writes.add(own); writes.addAll(others);
        assertEquals(CdObjectPool.overlay(before.pool(), writes).slots(), h.pool().capture().slots());
        assertEquals(before.status(), h.placement.status()); assertArrayEquals(before.savedFlags().copy(), h.placement.capture().savedFlags().copy());
    }
    /** Independent literal native stores; this does not invoke the factory/format decoder to derive expectations. */
    private static byte[] initBytes(CdObjectState old, boolean finish) {
        byte[] bytes = old.copyBytes(); bytes[0x24] += 2;
        ByteBuffer.wrap(bytes).putInt(4, 0x20C018); bytes[1] |= 4; bytes[0x18] = 3;
        if (finish) {
            word(bytes, 2, 0x0330); bytes[0x19] = 0x10; bytes[0x16] = 8;
            if ((bytes[1] & 2) != 0) { bytes[0x19] = 0x12; bytes[0x20] = (byte) 0x83; }
        }
        return bytes;
    }
    /** Construct all64 dirty slot bytes while retaining the genuine pool owner; never construct a Snapshot. */
    private static CdObjectState dirty(CdObjectState base) throws Exception {
        byte[] bytes = new byte[64]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (0x31 + i * 17);
        var owner = CdObjectState.class.getDeclaredField("owner"); owner.setAccessible(true);
        var constructor = CdObjectState.class.getDeclaredConstructor(Object.class, CdObjectState.Identity.class, byte[].class);
        constructor.setAccessible(true); return constructor.newInstance(owner.get(base), base.identity(), bytes);
    }
    private static CdPlayerTouchResponse.Actor withY(CdPlayerTouchResponse.Actor before, int yBits) {
        var m = before.motion();
        return new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(m.centreX16_16(), yBits, m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(), m.previousAnimation(),
                m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle()),
                before.tile(), before.priority(), before.hurt(), before.rotationDistance());
    }
    private static CdSolidObject.Player withActor(CdSolidObject.Player before, CdPlayerTouchResponse.Actor actor) {
        return new CdSolidObject.Player(actor, before.slotId(), before.pushObject(), before.standObject());
    }

    /** Stand-in publication only: the real pool/capture is used, with no Frame/World authority claim. */
    private static final class H {
        final CdObjectPlacementRuntime placement; final CdSpikes body; final P p = new P();
        final List<CdSpikes.PrefixSolidCall> calls = new ArrayList<>(); final List<CdSpikes.YRewindCall> rewinds = new ArrayList<>();
        final List<CdSpikes.HurtCall> hurts = new ArrayList<>(); final List<CdObjectState> draws = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        CdSolidObject.Player livePlayer; CdSolidObject.Globals liveGlobals;
        CdPlayerTouchResponse.PlayerGlobals livePlayerGlobals; CdPlayerTouchResponse.WorldGlobals liveWorld;
        int debug, invincible, timeWarp;
        H() throws Exception { this(new Terrain().owner()); }
        H(CdSolidObject solid) throws Exception {
            var objects = media().terrain().objects(); placement = new CdObjectPlacementRuntime(objects.placements());
            put(state().moveByte(ID, 0x26).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0x84)
                    .moveWord(X, 0x1000).moveWord(Y, 0x200).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 8)
                    .moveLong(MAPPINGS, 0x20C018).moveWord(TILE, 0x0330).moveByte(PRIORITY, 3));
            body = CdSpikes.fromAct2AssetsWithPrefix(placement, objects, solid, this::commit, this::rewind, this::hurt, this::draw);
        }
        CdObjectPool pool() { return placement.pool(); } CdObjectState state() { return pool().state(SLOT); }
        void put(CdObjectState state) { pool().write(state); }
        CdSpikes.Result run() {
            livePlayer = p.player(); liveGlobals = new CdSolidObject.Globals(invincible, 4, 0x12, 0x34);
            livePlayerGlobals = new CdPlayerTouchResponse.PlayerGlobals(timeWarp, invincible, 1, 7, 0, debug, 4, 0);
            liveWorld = new CdPlayerTouchResponse.WorldGlobals(7, 3, 0, 0, 5000, 0);
            return body.run(SLOT, world(), new CdSpikes.Inputs(livePlayer, new CdSolidObject.Gates(debug, 0),
                    liveGlobals, livePlayerGlobals, liveWorld));
        }
        void commit(CdSpikes.PrefixSolidCall call) {
            calls.add(call); pool().validateRestore(call.actualCut()); assertEquals(call.actualCut().slots(), pool().capture().slots());
            assertEquals(livePlayer, call.expectedPlayer()); assertEquals(liveGlobals, call.expectedGlobals());
            CdObjectState own; List<CdObjectState> others;
            if (call.outcome() instanceof CdSolidObject.Completed done) {
                var result = done.completedResult(); own = result.object(); others = result.otherSlots(); livePlayer = result.player(); liveGlobals = result.globals();
            } else {
                var prefix = ((CdSolidObject.NativeStopped) call.outcome()).prefix(); own = prefix.object(); others = prefix.otherSlots(); livePlayer = prefix.player(); liveGlobals = prefix.globals();
            }
            others.forEach(pool()::write); pool().write(own);
            livePlayerGlobals = new CdPlayerTouchResponse.PlayerGlobals(timeWarp, liveGlobals.invincible(), livePlayerGlobals.shield(),
                    livePlayerGlobals.combineRing(), 0, debug, liveGlobals.scoreChain(), 0); events.add("solid");
        }
        void rewind(CdSpikes.YRewindCall call) {
            assertEquals(state().identity(), call.sourceIdentity()); assertEquals(livePlayer.actor(), call.expectedActor());
            rewinds.add(call); livePlayer = withActor(livePlayer, withY(livePlayer.actor(), call.finalYBits())); events.add("y");
        }
        void hurt(CdSpikes.HurtCall call) {
            pool().validateRestore(call.callCut()); assertEquals(call.callCut().slots(), pool().capture().slots());
            assertEquals(state(), call.sourceA2()); assertEquals(livePlayer.actor(), call.expectedActor());
            assertEquals(livePlayerGlobals, call.expectedPlayer()); assertEquals(liveWorld, call.expectedWorld()); hurts.add(call);
            var result = call.result(); result.changedSlots().forEach(pool()::write); livePlayer = withActor(livePlayer, result.actor());
            livePlayerGlobals = result.player(); liveWorld = result.world();
            liveGlobals = new CdSolidObject.Globals(livePlayerGlobals.invincible(), livePlayerGlobals.scoreChain(),
                    liveGlobals.primaryAngle(), liveGlobals.secondaryAngle()); events.add("hurt");
        }
        void draw(CdObjectPool pool, CdObjectState.Identity identity) {
            draws.add(pool.state(identity.wholeIndex())); pool.write(afterDraw(pool.state(identity.wholeIndex()))); events.add("draw");
        }
    }
    /** Explicit constructed Actor ingress; no natural route or native captured player is asserted. */
    private static final class P {
        int x = 0x10008000, y = 0x01E51234, xv = 0x123, yv = 0x180, flags = 2, stand, spr = 4, hurt;
        CdSolidObject.Player player() {
            var motion = new CdPlayerMotionState(x, y, xv, yv, 0, 2, 0x10, flags, spr, 9, 0x13, 0, 0, 1, 0, 0, 0, 0, 0x56, 0x78);
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion, 0x780, 2, hurt, 0), 1, 0, stand);
        }
    }
    /**
     * Explicit constructed A-format geometry, adapted from the immutable existing TestCdSpikesPastPrefix
     * fixture (whose helpers are private). Real existing decoders/probes/Solid run; this is not R12A terrain
     * or physics parity. F9 is a deliberate absent foreground leaf, not a fabricated native exception.
     */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16); angles[2] = (byte) 0xC0;
        }
        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578]; for (int i = 0; i < 60; i++) word(animations, i * 2, 120); animations[122] = (byte) 0xFF;
            byte[] maps = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]); byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }
    private static void word(byte[] bytes, int at, int value) { ByteBuffer.wrap(bytes).putShort(at, (short) value); }
    private record Media(CdR12aTerrainAssets terrain, CdR12aPlayerAssets player) { }
    private static Media media() throws Exception {
        if (retained != null) return retained;
        var root = System.getProperty("soniccd.media.root"); assertNotNull(root); assertFalse(root.isBlank()); Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            retained = new Media(CdR12aTerrainAssets.fromPrograms(source), CdR12aPlayerAssets.fromPrograms(source));
        }
        assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7", retained.terrain().identity().mmdSha256());
        return retained;
    }
}
