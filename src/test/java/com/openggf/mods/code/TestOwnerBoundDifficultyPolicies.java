package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.game.sonic3k.difficulty.GumballSpringPolicy;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.runtime.OwnerBoundCallbacks;
import com.openggf.mods.runtime.OwnerBoundGamePatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Both owner projections must admit native policy values before handing them to consumers. */
class TestOwnerBoundDifficultyPolicies {
    private static final String OWNER = "registered-owner";
    private static final String CLAIMED_OWNER = "claimed-other-owner";

    private enum Binding { SERVICE_BUNDLE, EXPLICIT_PATCH }

    private record Fixture(ModFaultBoundary boundary, ModRuntimeFindingStore findings,
                           AtomicReference<Set<String>> disabled) {
        static Fixture create() {
            var findings = new ModRuntimeFindingStore();
            var disabled = new AtomicReference<Set<String>>();
            var boundary = new ModFaultBoundary(Map.of("dependent", Set.of(OWNER)), findings,
                    ignored -> new ModStateSaveResult.Saved(), disabled::set);
            return new Fixture(boundary, findings, disabled);
        }
    }

    @Test
    void invalidAttackDelaysFaultTheirRegisteredOwnerAfterOneCallback() {
        for (Binding binding : Binding.values()) {
            for (int delay : new int[] {-2, 0, 15, 61}) {
                Fixture fixture = Fixture.create();
                AtomicInteger calls = new AtomicInteger();
                AizMinibossAttackPolicy owned = bind(binding, AizMinibossAttackPolicy.class, remaining -> {
                    assertEquals(OWNER, OwnerCallbackScope.current());
                    calls.incrementAndGet();
                    return delay;
                }, fixture);
                assertOwnerFault(fixture, IllegalArgumentException.class, () -> owned.followupDelayTicks(3));
                assertEquals(1, calls.get());
            }
        }
    }

    @Test
    void acceptedAttackDelaysAndMissingPoliciesKeepTheirNormalBehavior() {
        for (Binding binding : Binding.values()) {
            Fixture fixture = Fixture.create();
            AtomicInteger calls = new AtomicInteger();
            AizMinibossAttackPolicy owned = bind(binding, AizMinibossAttackPolicy.class, remaining -> {
                calls.incrementAndGet();
                return remaining > 3 ? -1 : 20;
            }, fixture);
            assertEquals(-1, owned.followupDelayTicks(6));
            assertEquals(20, owned.followupDelayTicks(3));
            assertEquals(2, calls.get());
            assertNull(bind(binding, AizMinibossAttackPolicy.class, null, fixture));
            assertNull(bind(binding, BlueSphereLayoutPolicy.class, null, fixture));
            assertNull(bind(binding, GumballSpringPolicy.class, null, fixture));
            assertTrue(fixture.findings.snapshot().isEmpty());
            assertNull(fixture.disabled.get());
        }
    }

    @Test
    void nullAndOutOfRangeRecoveryLayoutsFaultInsideBothOwnerBoundaries() {
        for (Binding binding : Binding.values()) {
            for (int[] offsets : new int[][] {null, {-49, 49}, {0, 0}, {-32, 0, 16}}) {
                Fixture fixture = Fixture.create();
                AtomicInteger calls = new AtomicInteger();
                GumballSpringPolicy owned = bind(binding, GumballSpringPolicy.class, () -> {
                    calls.incrementAndGet();
                    return offsets;
                }, fixture);
                assertOwnerFault(fixture, offsets == null ? NullPointerException.class : IllegalArgumentException.class,
                        owned::springOffsets);
                assertEquals(1, calls.get());
            }
        }
    }

    @Test
    void retainedGumballArraysCannotMutateAnAdmittedLayout() {
        for (Binding binding : Binding.values()) {
            Fixture fixture = Fixture.create();
            int[] retained = {-32, 0, 32};
            AtomicInteger calls = new AtomicInteger();
            GumballSpringPolicy owned = bind(binding, GumballSpringPolicy.class, () -> {
                calls.incrementAndGet();
                return retained;
            }, fixture);
            int[] admitted = owned.springOffsets();
            assertNotSame(retained, admitted);
            retained[0] = -1000;
            assertArrayEquals(new int[] {-32, 0, 32}, admitted);
            assertOwnerFault(fixture, IllegalArgumentException.class, owned::springOffsets);
            assertEquals(2, calls.get());
            assertArrayEquals(new int[] {-32, 0, 32}, admitted);
        }
    }

    @Test
    void mutatedOfferedBlueBoardCannotRedefineTheTrustedOriginal() {
        for (Binding binding : Binding.values()) {
            Fixture fixture = Fixture.create();
            byte[] original = board();
            AtomicInteger calls = new AtomicInteger();
            BlueSphereLayoutPolicy owned = bind(binding, BlueSphereLayoutPolicy.class, (stage, offered) -> {
                assertNotSame(original, offered, "creator receives isolated entry bytes");
                assertEquals(OWNER, OwnerCallbackScope.current());
                assertEquals(0, stage);
                calls.incrementAndGet();
                offered[10 * 32 + 10] = 0; // removing a native blue must not redefine the saved original
                return offered;
            }, fixture);
            assertOwnerFault(fixture, IllegalArgumentException.class, () -> owned.transformLayout(0, original));
            assertEquals(1, calls.get());
            assertEquals(2, original[10 * 32 + 10]);
        }
    }

    @Test
    void nullMalformedAndOutOfContractBlueBoardsAreOwnerFaults() {
        for (Binding binding : Binding.values()) {
            for (int invalid : new int[] {0, 1, 2, 3}) {
                Fixture fixture = Fixture.create();
                AtomicInteger calls = new AtomicInteger();
                BlueSphereLayoutPolicy owned = bind(binding, BlueSphereLayoutPolicy.class, (stage, offered) -> {
                    calls.incrementAndGet();
                    if (invalid == 0) return null;
                    if (invalid == 1) return new byte[1024];
                    if (invalid == 2) offered[1024] = 0x40; // native heading is immutable
                    if (invalid == 3) offered[5 * 32 + 5] = 6; // animation cells are not entry content
                    return offered;
                }, fixture);
                assertOwnerFault(fixture, invalid == 0 ? NullPointerException.class : IllegalArgumentException.class,
                        () -> owned.transformLayout(0, board()));
                assertEquals(1, calls.get());
            }
        }
    }

    @Test
    void retainedBlueInputAndOutputCannotMutateNativeOrAdmittedBytes() {
        for (Binding binding : Binding.values()) {
            Fixture fixture = Fixture.create();
            byte[] original = board();
            AtomicReference<byte[]> retained = new AtomicReference<>();
            AtomicInteger calls = new AtomicInteger();
            BlueSphereLayoutPolicy owned = bind(binding, BlueSphereLayoutPolicy.class, (stage, offered) -> {
                calls.incrementAndGet();
                offered[5 * 32 + 5] = 1;
                retained.set(offered);
                return offered;
            }, fixture);
            byte[] admitted = owned.transformLayout(0, original);
            assertNotSame(retained.get(), admitted);
            assertNotSame(original, retained.get());
            assertEquals(1, admitted[5 * 32 + 5]);
            assertEquals(0, original[5 * 32 + 5]);
            retained.get()[5 * 32 + 5] = 2;
            retained.get()[10 * 32 + 10] = 0;
            assertEquals(1, admitted[5 * 32 + 5]);
            assertEquals(2, admitted[10 * 32 + 10]);
            assertEquals(2, original[10 * 32 + 10]);
            assertEquals(1, calls.get());
            assertTrue(fixture.findings.snapshot().isEmpty());
        }
    }

    /** Matching method names and signatures alone do not grant difficulty-policy semantics. */
    public interface SimilarPolicy {
        int followupDelayTicks(int remaining);
        int[] springOffsets();
        byte[] transformLayout(int stage, byte[] layout);
    }

    @Test
    void unrelatedSameNamedMethodsKeepArgumentsResultsAndActionCounts() {
        for (Binding binding : Binding.values()) {
            Fixture fixture = Fixture.create();
            AtomicInteger calls = new AtomicInteger();
            int[] untouchedResult = {1000};
            byte[] offered = {7};
            SimilarPolicy unrelated = new SimilarPolicy() {
                @Override public int followupDelayTicks(int remaining) { calls.incrementAndGet(); return 0; }
                @Override public int[] springOffsets() { calls.incrementAndGet(); return untouchedResult; }
                @Override public byte[] transformLayout(int stage, byte[] layout) {
                    calls.incrementAndGet();
                    assertSame(offered, layout);
                    layout[0] = 8;
                    return null;
                }
            };
            SimilarPolicy owned = bind(binding, SimilarPolicy.class, unrelated, fixture);
            assertEquals(0, owned.followupDelayTicks(3));
            assertSame(untouchedResult, owned.springOffsets());
            assertNull(owned.transformLayout(0, offered));
            assertEquals(8, offered[0]);
            assertEquals(3, calls.get());
            assertTrue(fixture.findings.snapshot().isEmpty());
        }
    }

    public static final class AliasedPolicy implements SimilarPolicy, AizMinibossAttackPolicy,
            BlueSphereLayoutPolicy, GumballSpringPolicy {
        int calls;
        @Override public int followupDelayTicks(int remaining) { calls++; return 0; }
        @Override public int[] springOffsets() { calls++; return new int[] {-100, 100}; }
        @Override public byte[] transformLayout(int stage, byte[] layout) {
            calls++;
            layout[10 * 32 + 10] = 0;
            return layout;
        }
    }

    @Test
    void policyMethodsRemainValidatedWhenTheirProxyWasPublishedUnderASharedSignatureAlias() {
        for (Binding binding : Binding.values()) {
            for (int operation : new int[] {0, 1, 2}) {
                Fixture fixture = Fixture.create();
                AliasedPolicy delegate = new AliasedPolicy();
                SimilarPolicy firstProjection = bind(binding, SimilarPolicy.class, delegate, fixture);
                assertInstanceOf(AizMinibossAttackPolicy.class, firstProjection);
                assertInstanceOf(BlueSphereLayoutPolicy.class, firstProjection);
                assertInstanceOf(GumballSpringPolicy.class, firstProjection);
                assertOwnerFault(fixture, IllegalArgumentException.class, switch (operation) {
                    case 0 -> () -> ((AizMinibossAttackPolicy) firstProjection).followupDelayTicks(3);
                    case 1 -> () -> ((GumballSpringPolicy) firstProjection).springOffsets();
                    default -> () -> ((BlueSphereLayoutPolicy) firstProjection).transformLayout(0, board());
                });
                assertEquals(1, delegate.calls);
            }
        }
    }

    @Test
    void inheritedPolicyFaultStillBelongsToItsEarlierOwner() {
        Fixture fixture = Fixture.create();
        GameModule root = rootModule();
        GameModule earlier = project("earlier-owner", root, AizMinibossAttackPolicy.class,
                (AizMinibossAttackPolicy) remaining -> 0, fixture);
        GameModule later = OwnerBoundGamePatch.wrap(OWNER, patch(base -> new DelegatingGameModule(base, CLAIMED_OWNER) { }),
                fixture.boundary).apply(earlier, null);
        AizMinibossAttackPolicy inherited = later.getGameService(AizMinibossAttackPolicy.class);
        assertSame(earlier.getGameService(AizMinibossAttackPolicy.class), inherited);
        var abort = assertThrows(ModFaultBoundary.CallbackAborted.class, () -> inherited.followupDelayTicks(3));
        assertEquals("earlier-owner", abort.owner());
        assertEquals(Set.of("earlier-owner"), fixture.disabled.get());
        assertTrue(fixture.findings.findingsFor(OWNER).isEmpty());
    }

    @Test
    void fatalVmAndThreadDeathErrorsRetainIdentityAndDoNotQuarantineAnOwner() {
        for (Binding binding : Binding.values()) {
            for (Error fatal : new Error[] {new OutOfMemoryError("policy fatal"), new ThreadDeath()}) {
                Fixture fixture = Fixture.create();
                AtomicInteger calls = new AtomicInteger();
                BlueSphereLayoutPolicy owned = bind(binding, BlueSphereLayoutPolicy.class, (stage, offered) -> {
                    calls.incrementAndGet();
                    throw fatal;
                }, fixture);
                assertSame(fatal, assertThrows(fatal.getClass(), () -> owned.transformLayout(0, board())));
                assertEquals(1, calls.get());
                assertNull(fixture.disabled.get());
                assertTrue(fixture.findings.snapshot().isEmpty());
                assertNull(OwnerCallbackScope.current());
            }
        }
    }

    private static <T> T bind(Binding binding, Class<T> contract, T value, Fixture fixture) {
        if (binding == Binding.SERVICE_BUNDLE) {
            return new OwnerBoundCallbacks(OWNER, fixture.boundary, 16, Map.of()).bind(contract, value);
        }
        return project(OWNER, rootModule(), contract, value, fixture).getGameService(contract);
    }

    private static <T> GameModule project(String owner, GameModule base, Class<T> contract, T value, Fixture fixture) {
        return OwnerBoundGamePatch.wrap(owner, patch(inherited -> new DelegatingGameModule(inherited, CLAIMED_OWNER) {
            @Override public <S> S getGameService(Class<S> requested) {
                return requested == contract ? requested.cast(value) : super.getGameService(requested);
            }
        }), fixture.boundary).apply(base, null);
    }

    private static GameModule rootModule() {
        GameModule base = mock(GameModule.class);
        when(base.rewindAdapters()).thenReturn(List.of());
        return base;
    }

    private static GamePatch patch(java.util.function.Function<GameModule, GameModule> apply) {
        return new GamePatch() {
            @Override public String id() { return CLAIMED_OWNER + ":difficulty"; }
            @Override public String displayName() { return CLAIMED_OWNER; }
            @Override public String baseGameId() { return "s3k"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(); }
            @Override public GameModule apply(GameModule base, PatchContext context) { return apply.apply(base); }
        };
    }

    private static byte[] board() {
        byte[] layout = new byte[0x408];
        layout[10 * 32 + 10] = 2;
        layout[12 * 32 + 12] = 4;
        return layout;
    }

    private static void assertOwnerFault(Fixture fixture, Class<? extends Throwable> cause, Runnable action) {
        var abort = assertThrows(ModFaultBoundary.CallbackAborted.class, action::run);
        assertEquals(OWNER, abort.owner());
        assertInstanceOf(cause, abort.getCause());
        assertEquals(Set.of(OWNER, "dependent"), fixture.disabled.get());
        assertEquals("MOD_CALLBACK_FAILED", fixture.findings.findingsFor(OWNER).getFirst().code());
        assertTrue(fixture.findings.findingsFor(CLAIMED_OWNER).isEmpty());
        assertNull(OwnerCallbackScope.current());
    }
}
