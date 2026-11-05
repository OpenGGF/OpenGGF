package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests authored before the extension; ALL JAVA UNRUN. Constructed shared-component fixtures,
 * never World/Frame admission. Native N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/Level/_Objects/Monitor and Time Post/Main.asm, .GiveItem:480-482,
 * .Give1UP:490-494, .CheckRing/.GiveRings/.RingSound:498-517, wrapper:419-425.
 * Literal threshold expectations are independent of the implementation. The existing Monitor
 * fixture supplies complete native-format MM/MA includes and the real shared DrawObject queue.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
class TestCdMonitorItemRings {
    static final int WHOLE = 48;

    @Test void tenRingKernelUsesWordWrapUnsignedThresholdsAndBsetOldBitPriority() {
        // old rings, old flags, new rings, new flags, one-up (at most one per native call).
        int[][] cases = {{0,0,10,0,0}, {89,0,99,0,0}, {90,0,100,2,1}, {99,0,109,2,1},
                {100,2,110,2,0}, {189,2,199,2,0}, {190,0,200,2,1}, {190,2,200,6,1},
                {190,4,200,6,1}, {190,6,200,6,0}, {200,0,210,2,1},
                {32768,0,32778,2,1}, {65525,0,65535,2,1}, {65526,0,0,0,0},
                {65535,0,9,0,0}, {190,0xF8,200,0xFA,1}, {190,0xFA,200,0xFE,1},
                {190,0xFE,200,0xFE,0}, {0,0xFF,10,0xFF,0}};
        for (int[] c : cases) {
            var before = new CdRingGlobals(c[0], 0xA5, c[1], 0xB4, 0xC6, 0xFEDC, 0x81, 0xE3);
            var award = before.monitorItemTen();
            assertEquals(c[4] != 0, award.oneUp(), Arrays.toString(c));
            assertEquals(new CdRingGlobals(c[2], (0xA5 + c[4]) & 255, c[3], 0xB5,
                    (0xC6 + c[4]) & 255, 0xFEDC, 0x81, 0xE3), award.globals(), Arrays.toString(c));
            assertEquals(c[0], before.rings(), "The immutable input is never rewritten");
        }
    }

    @Test void extraLifeWrapsBothBytesAndHudRingOriPreservesEveryOtherBit() {
        var before = new CdRingGlobals(190, 255, 0xFA, 0xFE, 255, 0, 255, 255);
        assertEquals(new CdRingGlobals.MonitorItemAward(
                new CdRingGlobals(200, 0, 0xFE, 255, 0, 0, 255, 255), true), before.monitorItemTen());
        var noLife = new CdRingGlobals(65535, 255, 255, 0, 255, 65535, 0, 0).monitorItemTen();
        assertFalse(noLife.oneUp());
        assertEquals(new CdRingGlobals(9, 255, 255, 1, 255, 65535, 0, 0), noLife.globals());
        assertThrows(NullPointerException.class, () -> new CdRingGlobals.MonitorItemAward(null, false));
    }

    @Test void actualRingPrimitiveFollowsPublishedPrefixAndCompletesBeforeRealDrawExactlyOnce() {
        var h = new Harness(); var before = h.slot();
        var result = h.run();
        var expected = before.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29);
        assertEquals(List.of("REWARD_PREFIX", "RINGS", "WRAPPER_READY", "DRAW", "COMPLETE"),
                h.calls.stream().map(TestCdMonitorItemRings::operationName).toList());
        assertEquals(List.of("RINGS", "DRAW"), h.external);
        assertEquals(1, h.ringCount); assertEquals(0, h.shieldCount); assertEquals(0, h.shoesCount);
        var actual = (CdMonitorItem.RingCall) h.calls.get(1);
        assertEquals(expected, actual.source()); assertEquals(expected, actual.cut().pool().slots().get(WHOLE - 32));
        assertSame(actual.request(), h.request); assertEquals(expected.identity(), h.request.identity());
        assertEquals(1, h.request.rawAnimation());
        assertEquals(expected.moveByte(SPRITE_FLAGS, result.state().spriteFlags()), result.state());
        var effect = result.effects().stream().filter(CdMonitorItem.RingsAwarded.class::isInstance)
                .map(CdMonitorItem.RingsAwarded.class::cast).findFirst().orElseThrow();
        assertSame(h.request, effect.call()); assertSame(h.ringResult, effect.result());
        assertSame(h.ringResult, h.results.get(1)); assertSame(result, h.adopted);
        assertEquals(CdMonitorItem.RingAwardSound.FM_RING, effect.result().sound());
        assertThrows(UnsupportedOperationException.class, () -> result.effects().clear());
        h.run(); assertEquals(1, h.ringCount); assertEquals(28, h.slot().word(MONITOR_ITEM_DELETE));
    }

    @Test void oneUpCompletionIsOnlyTheReturnedScalarSoundAndEffectsDoNotReplayWork() {
        var h = new Harness();
        h.ringResult = new CdMonitorItem.RingAwardResult(CdMonitorItem.RingAwardSound.SCMD_YESSFX);
        var result = h.run(); var effect = result.effects().stream()
                .filter(CdMonitorItem.RingsAwarded.class::isInstance).map(CdMonitorItem.RingsAwarded.class::cast)
                .findFirst().orElseThrow();
        assertEquals(CdMonitorItem.RingAwardSound.SCMD_YESSFX, effect.result().sound());
        for (int i = 0; i < 3; i++) { effect.result().sound(); effect.call().identity(); result.effects(); }
        assertEquals(1, h.ringCount); assertEquals(List.of("RINGS", "DRAW"), h.external);
        assertTrue(result.effects().stream().noneMatch(CdMonitorItem.Awarded.class::isInstance));
        assertTrue(result.effects().stream().noneMatch(CdMonitorItem.ShieldAwarded.class::isInstance));
    }

    @Test void originalCurrentReceiptProtocolRejectsEqualClonesReentryAndStaleReads() {
        var h = new Harness();
        h.beforeHook = call -> {
            if (call instanceof CdMonitorItem.RingCall c) {
                var clone = new CdMonitorItem.RingCall(c.cut(), c.source(), c.request());
                assertEquals(c, clone); assertNotSame(c, clone); assertFalse(h.body.isCurrentCall(clone));
                assertFalse(h.body.isCurrentPrimitive(clone));
                assertThrows(IllegalStateException.class, () -> h.body.currentOrdinal(clone));
                assertThrows(IllegalStateException.class, () -> h.body.currentResult(clone));
            }
            assertThrows(IllegalStateException.class, h::run);
        };
        h.ringHook = () -> {
            assertTrue(h.body.isCurrentPrimitive(h.pending));
            assertThrows(IllegalStateException.class, () -> h.body.currentResult(h.pending));
            assertThrows(IllegalStateException.class, h::run);
        };
        h.run();
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), h.ordinals);
        assertEquals(h.calls.size(), h.preparedCount); assertEquals(h.calls.size(), h.completedCount);
        for (Object stale : h.calls) {
            assertFalse(h.body.isCurrentCall(stale)); assertFalse(h.body.isCurrentPrimitive(stale));
            assertThrows(IllegalStateException.class, () -> h.body.currentOrdinal(stale));
            assertThrows(IllegalStateException.class, () -> h.body.currentResult(stale));
        }
    }

    @Test void explicitUnobservedOptInStillInvokesActualRingPortAndGuardsSourceStorage() {
        var h = new Harness(); var calls = new ArrayList<CdMonitorItem.RingAwardCall>();
        var body = CdMonitorItem.withRingAndShieldAward(h.runtime, h.assets, h::shoes, h::shield, c -> {
            assertEquals(4, h.slot().routine()); assertEquals(29, h.slot().word(MONITOR_ITEM_DELETE));
            assertTrue(h.external.isEmpty()); calls.add(c); return h.ringResult;
        }, (pool, identity) -> h.external.add("DRAW"));
        var result = body.run(WHOLE, Optional.empty());
        assertEquals(1, calls.size()); assertEquals(List.of("DRAW"), h.external);
        assertTrue(result.effects().stream().anyMatch(CdMonitorItem.RingsAwarded.class::isInstance));
        var bad = new Harness(); var rejected = CdMonitorItem.withRingAndShieldAward(bad.runtime, bad.assets,
                bad::shoes, bad::shield, c -> { bad.corrupt("OTHER"); return bad.ringResult; }, (pool, id) -> {});
        assertThrows(IllegalStateException.class, () -> rejected.run(WHOLE, Optional.empty()));
    }

    @Test void originalRingFaultRetainsPrefixAndSuppressesStorageOrObserverFaultsWithoutReplay() {
        for (String target : List.of("OWN", "OTHER", "SAVED", "STATUS")) {
            var h = new Harness(); h.ringHook = () -> { h.corrupt(target); throw h.original; };
            h.failedHook = call -> { throw new IllegalArgumentException("constructed failed observer"); };
            assertSame(h.original, assertThrows(IllegalStateException.class, h::run));
            assertTrue(h.original.getSuppressed().length >= 2, target);
            assertEquals(4, h.slot().routine()); assertEquals(29, h.slot().word(MONITOR_ITEM_DELETE));
            assertEquals(List.of("RINGS"), h.external); assertEquals(1, h.failed.size());
            assertInstanceOf(CdMonitorItem.RingCall.class, h.failed.getFirst());
            assertFalse(h.body.isCurrentCall(h.failed.getFirst()));
            assertTrue(h.calls.stream().noneMatch(CdMonitorItem.CompletionCall.class::isInstance));
        }
        var retry = new Harness(); retry.ringHook = () -> { throw retry.original; };
        assertSame(retry.original, assertThrows(IllegalStateException.class, retry::run));
        retry.ringHook = () -> {}; retry.run(); assertEquals(1, retry.ringCount);
        assertEquals(28, retry.slot().word(MONITOR_ITEM_DELETE));
    }

    @Test void successfulPortCannotMutateSourcePoolSavedOrPlacementStorage() {
        for (String target : List.of("OWN", "OTHER", "SAVED", "STATUS")) {
            var h = new Harness(); h.ringHook = () -> h.corrupt(target);
            assertThrows(IllegalStateException.class, h::run, target);
            assertEquals(1, h.ringCount); assertEquals(List.of("RINGS"), h.external);
            assertEquals(4, h.slot().routine()); assertEquals(29, h.slot().word(MONITOR_ITEM_DELETE));
        }
        var h = new Harness(); h.preparedHook = c -> { if (c instanceof CdMonitorItem.RingCall) h.corrupt("OTHER"); };
        assertThrows(IllegalStateException.class, h::run); assertEquals(0, h.ringCount); assertTrue(h.external.isEmpty());
    }

    @Test void nullAndErrorReturnsKeepIdenticalFailurePrefixAndNeverProduceCompletionOrDraw() {
        var h = new Harness(); h.ringResult = null;
        assertThrows(NullPointerException.class, h::run); assertEquals(List.of("RINGS"), h.external);
        assertEquals(4, h.slot().routine()); assertEquals(29, h.slot().word(MONITOR_ITEM_DELETE));
        assertTrue(h.calls.stream().noneMatch(CdMonitorItem.CompletionCall.class::isInstance));
        var k = new Harness(); var error = new AssertionError("original constructed ring error");
        k.ringHook = () -> { k.corrupt("OTHER"); throw error; };
        assertSame(error, assertThrows(AssertionError.class, k::run)); assertTrue(error.getSuppressed().length >= 1);
        assertEquals(List.of("RINGS"), k.external);
    }

    @Test void drawNoOpIsValidButOnlyItsActualBitSevenMayChangeAndItFreezesBeforeCallbacks() {
        var h = new Harness(); h.useQueue = false; var before = h.slot();
        assertEquals(before.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29), h.run().state());
        var k = new Harness(); k.useQueue = false; k.drawHook = () -> k.edit(o -> o.moveByte(SPRITE_FLAGS, 0xA4));
        assertEquals(0xA4, k.run().state().spriteFlags());
        var j = new Harness(); j.useQueue = false; j.drawHook = () -> j.edit(o -> o.moveByte(SPRITE_FLAGS, 0xE4));
        assertThrows(IllegalStateException.class, j::run); assertEquals(0xE4, j.slot().spriteFlags());
        var after = new Harness(); after.useQueue = false; after.afterHook = c -> {
            if (c instanceof CdMonitorItem.DrawCall) after.edit(o -> o.moveByte(SPRITE_FLAGS, o.spriteFlags() ^ 128));
        };
        assertThrows(IllegalStateException.class, after::run);
        assertTrue(after.calls.stream().noneMatch(CdMonitorItem.CompletionCall.class::isInstance));
    }

    @Test void drawFailurePreservesOriginalAndFreezesBitSevenBeforeFailedObserverRepairsOtherSlot() {
        for (boolean throwInDraw : List.of(false, true)) {
            var h = new Harness(); h.useQueue = false;
            h.drawHook = () -> { h.corrupt("OTHER"); if (throwInDraw) throw h.original; };
            h.failedHook = c -> {
                if (c instanceof CdMonitorItem.DrawCall) {
                    h.runtime.pool().deleteObject(127);
                    h.edit(o -> o.moveByte(SPRITE_FLAGS, o.spriteFlags() ^ 128));
                }
            };
            var failure = assertThrows(IllegalStateException.class, h::run);
            if (throwInDraw) assertSame(h.original, failure);
            else assertTrue(failure.getMessage().contains("at 127"));
            assertEquals(0xA4, h.slot().spriteFlags());
            assertTrue(Arrays.stream(failure.getSuppressed()).anyMatch(t -> t.getMessage() != null
                    && t.getMessage().contains("unowned dynamic bytes at " + WHOLE)));
            assertEquals(1, h.ringCount); assertEquals(List.of("RINGS", "DRAW"), h.external);
            assertTrue(h.calls.stream().noneMatch(CdMonitorItem.CompletionCall.class::isInstance));
        }
        var onlyBit = new Harness(); onlyBit.useQueue = false;
        onlyBit.drawHook = () -> { onlyBit.edit(o -> o.moveByte(SPRITE_FLAGS, 0xA4)); throw onlyBit.original; };
        assertSame(onlyBit.original, assertThrows(IllegalStateException.class, onlyBit::run));
        assertEquals(0xA4, onlyBit.slot().spriteFlags()); assertEquals(0, onlyBit.original.getSuppressed().length);
    }

    @Test void oldConstructorShieldFactoryAndWorldFactoryKeepPreciseUnboundRingBoundary() {
        for (int mode = 0; mode < 3; mode++) {
            var h = new Harness();
            var old = switch (mode) {
                case 0 -> new CdMonitorItem(h.runtime, h.assets, h::shoes, (p, i) -> fail("No draw before boundary"));
                case 1 -> CdMonitorItem.withShieldAward(h.runtime, h.assets, h::shoes, h::shield,
                        (p, i) -> fail("No draw before boundary"));
                default -> CdMonitorItem.withWorldOwner(h.runtime, h.assets, h::shoes, h::shield,
                        (p, i) -> fail("No draw before boundary"), new CdMonitorItem.DispatchObserver() {
                            public void beforeOperation(Object call) {}
                            public void afterOperation(Object call) {}
                            public void failedOperation(Object call, Throwable original) {}
                        }, new CdMonitorItem.ReceiptGuard() {
                            public void prepared(Object call) {}
                            public void completed(Object call) {}
                        });
            };
            var before = h.slot(); var e = assertThrows(CdMonitorItem.UnsupportedMonitorItem.class,
                    () -> old.run(WHOLE, Optional.empty()));
            assertEquals(CdMonitorItem.UnsupportedBoundary.REWARD_RINGS, e.boundary());
            assertEquals(WHOLE, e.wholeIndex()); assertEquals(1, e.value());
            assertEquals(before.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 29), h.slot());
            assertEquals(0, h.ringCount); assertEquals(0, h.shoesCount); assertEquals(0, h.shieldCount);
        }
    }

    @Test void ringOptInLeavesInheritedShoesShieldUnsupportedAndDeleteBranchesIntact() {
        for (int kind : new int[]{0, 2, 3, 4, 5, 6, 7, 128, 255}) {
            var h = new Harness(); h.edit(o -> o.moveByte(ANIMATION, kind));
            if (kind == 2 || kind == 4) {
                var result = h.run(); assertEquals(1, kind == 2 ? h.shieldCount : h.shoesCount);
                assertEquals(0, h.ringCount); assertEquals(4, result.state().routine());
            } else {
                assertThrows(CdMonitorItem.UnsupportedMonitorItem.class, h::run);
                assertEquals(0, h.ringCount); assertTrue(h.external.isEmpty());
            }
        }
        var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 4).moveWord(MONITOR_ITEM_DELETE, 0));
        var result = h.run(); assertTrue(result.deleted()); assertArrayEquals(new byte[64], result.state().copyBytes());
        assertEquals(List.of("DRAW"), h.external); assertEquals(0, h.ringCount);
        assertEquals(List.of(CdMonitorItem.AwardSound.SCMD_FADEPCM, CdMonitorItem.AwardSound.SCMD_SHOESMUS),
                List.of(CdMonitorItem.AwardSound.values()));
        assertEquals(List.of(CdMonitorItem.ShieldSound.FM_SHIELD), List.of(CdMonitorItem.ShieldSound.values()));
    }

    @Test void newShapesAndFactoriesRejectMissingOwnersWithoutExpandingAnimationOrSoundGrammar() {
        var h = new Harness(); var identity = h.slot().identity();
        assertEquals(List.of(CdMonitorItem.RingAwardSound.FM_RING, CdMonitorItem.RingAwardSound.SCMD_YESSFX),
                List.of(CdMonitorItem.RingAwardSound.values()));
        for (int raw : new int[]{-1, 0, 2, 4, 255, 256, 257})
            assertThrows(IllegalArgumentException.class, () -> new CdMonitorItem.RingAwardCall(identity, raw));
        assertThrows(NullPointerException.class, () -> new CdMonitorItem.RingAwardCall(null, 1));
        assertThrows(NullPointerException.class, () -> new CdMonitorItem.RingAwardResult(null));
        assertThrows(NullPointerException.class, () -> new CdMonitorItem.RingsAwarded(null, h.ringResult));
        assertThrows(NullPointerException.class, () -> new CdMonitorItem.RingsAwarded(
                new CdMonitorItem.RingAwardCall(identity, 1), null));
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withRingAndShieldAward(h.runtime, h.assets,
                h::shoes, h::shield, null, (p, i) -> {}));
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withRingAndShieldAward(h.runtime, h.assets,
                h::shoes, null, h::rings, (p, i) -> {}));
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withWorldOwner(h.runtime, h.assets,
                h::shoes, h::shield, h::rings, (p, i) -> {}, null, h));
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withWorldOwner(h.runtime, h.assets,
                h::shoes, h::shield, h::rings, (p, i) -> {}, h, null));
        assertThrows(NullPointerException.class, () -> CdMonitorItem.withWorldOwner(h.runtime, h.assets,
                h::shoes, h::shield, null, (p, i) -> {}, h, h));
    }

    static String operationName(Object call) {
        if (call instanceof CdMonitorItem.Publication p) return p.kind().name();
        if (call instanceof CdMonitorItem.RingCall) return "RINGS";
        if (call instanceof CdMonitorItem.ShieldCall) return "SHIELD";
        if (call instanceof CdMonitorItem.ShoesCall) return "SHOES";
        if (call instanceof CdMonitorItem.DeleteCall) return "DELETE";
        if (call instanceof CdMonitorItem.DrawCall) return "DRAW";
        if (call instanceof CdMonitorItem.CompletionCall) return "COMPLETE";
        throw new AssertionError("Unexpected constructed operation " + call);
    }

    static final class Harness implements CdMonitorItem.DispatchObserver, CdMonitorItem.ReceiptGuard {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(TestCdMonitor.layout());
        final CdMonitor.Assets assets = TestCdMonitor.assets();
        final CdMonitorItem body;
        final IllegalStateException original = new IllegalStateException("original constructed rings failure");
        final List<Object> calls = new ArrayList<>(), results = new ArrayList<>(), failed = new ArrayList<>();
        final List<String> external = new ArrayList<>();
        final List<Long> ordinals = new ArrayList<>();
        Consumer<Object> beforeHook = c -> {}, preparedHook = c -> {}, afterHook = c -> {}, failedHook = c -> {};
        Runnable ringHook = () -> {}, drawHook = () -> {};
        CdMonitorItem.RingAwardResult ringResult = new CdMonitorItem.RingAwardResult(CdMonitorItem.RingAwardSound.FM_RING);
        CdMonitorItem.RingAwardCall request;
        CdMonitorItem.Result adopted;
        Object pending;
        int ringCount, shieldCount, shoesCount, preparedCount, completedCount;
        boolean useQueue = true;

        Harness() {
            edit(o -> o.moveByte(ID, 0x1A).moveByte(ROUTINE, 2).moveByte(ANIMATION, 1)
                    .moveByte(SPRITE_FLAGS, 0x24).moveByte(PRIORITY, 3).moveByte(WIDTH, 8)
                    .moveWord(X, 0x850).moveWord(Y, 0x2D8).moveWord(Y_VELOCITY, 0));
            body = CdMonitorItem.withWorldOwner(runtime, assets, this::shoes, this::shield, this::rings,
                    this::draw, this, this);
        }
        CdObjectState slot() { return runtime.pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> transform) { runtime.pool().write(transform.apply(slot())); }
        CdMonitorItem.Result run() { return body.run(WHOLE, Optional.empty()); }
        CdMonitorItem.RingAwardResult rings(CdMonitorItem.RingAwardCall call) {
            assertTrue(body.isCurrentPrimitive(pending));
            var operation = assertInstanceOf(CdMonitorItem.RingCall.class, pending);
            assertSame(operation.request(), call); assertEquals(slot(), operation.source());
            assertEquals(slot().identity(), call.identity()); assertEquals(1, call.rawAnimation());
            assertEquals(4, slot().routine()); assertEquals(29, slot().word(MONITOR_ITEM_DELETE));
            assertTrue(external.isEmpty()); request = call; ringCount++; external.add("RINGS");
            ringHook.run(); return ringResult;
        }
        CdMonitorItem.AwardResult shoes(CdMonitorItem.AwardCall call) {
            shoesCount++; return new CdMonitorItem.AwardResult(List.of(CdMonitorItem.AwardSound.SCMD_SHOESMUS));
        }
        CdMonitorItem.ShieldAwardResult shield(CdMonitorItem.ShieldAwardCall call) {
            shieldCount++; return new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD);
        }
        void draw(CdObjectPool pool, CdObjectState.Identity identity) {
            assertSame(runtime.pool(), pool); assertEquals(slot().identity(), identity);
            assertTrue(body.isCurrentPrimitive(pending)); external.add("DRAW"); drawHook.run();
            if (useQueue) TestCdMonitor.drawThroughQueue(pool, identity, 0x800);
        }
        void corrupt(String target) {
            switch (target) {
                case "OWN" -> edit(o -> o.moveByte(ANGLE, o.unsignedByte(ANGLE) ^ 1));
                case "OTHER" -> runtime.pool().write(runtime.pool().state(127).moveByte(ANGLE, 1));
                case "SAVED" -> runtime.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                case "STATUS" -> runtime.spawnObjects(0, 0);
                default -> throw new AssertionError(target);
            }
        }
        public void beforeOperation(Object call) {
            pending = call; calls.add(call); assertTrue(body.isCurrentCall(call)); assertFalse(body.isCurrentPrimitive(call));
            ordinals.add(body.currentOrdinal(call));
            assertThrows(IllegalStateException.class, () -> body.currentResult(call)); beforeHook.accept(call);
        }
        public void prepared(Object call) {
            assertSame(pending, call); assertFalse(body.isCurrentPrimitive(call)); preparedCount++; preparedHook.accept(call);
        }
        public void afterOperation(Object call) {
            assertSame(pending, call); assertTrue(body.isCurrentCall(call)); assertFalse(body.isCurrentPrimitive(call));
            Object result = body.currentResult(call); results.add(result);
            if (call instanceof CdMonitorItem.CompletionCall) adopted = (CdMonitorItem.Result) result;
            afterHook.accept(call);
        }
        public void completed(Object call) {
            assertSame(pending, call); assertFalse(body.isCurrentPrimitive(call));
            assertSame(results.getLast(), body.currentResult(call)); completedCount++;
        }
        public void failedOperation(Object call, Throwable original) {
            assertSame(pending, call); assertTrue(body.isCurrentCall(call)); assertFalse(body.isCurrentPrimitive(call));
            assertNotNull(original); failed.add(call); failedHook.accept(call);
        }
    }
}
