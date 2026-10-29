package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.CdProgramInitialization.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-derived, media-free initialization oracles, frozen before production.
 * All checks are UNRUN by this author; Root owns the missing-API RED and R2.
 * J b25e62cb94a2f3d7599bb0330e323373fde5e2d0;
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Initialization.asm:56-80; Functions (General).asm:39-47.
 * These pure values establish no actual program, presentation or input readiness.
 */
class TestCdProgramInitialization {
    private static final VersionCache UNKNOWN_CACHE = new VersionCache(0, 0);
    private static final VersionCache PARTIAL_CACHE = new VersionCache(0x40, 0x40);

    @Test
    void nineLiteralControllerAndMarkerCasesPreserveConditionalReadOrder() {
        List<BranchCase> cases = List.of(
                new BranchCase(KnownBit.UNKNOWN, MarkerKnowledge.UNKNOWN, null, Read.CONTROLLER_SETUP_BIT),
                new BranchCase(KnownBit.UNKNOWN, MarkerKnowledge.EQUALS_INIT, null, Read.CONTROLLER_SETUP_BIT),
                new BranchCase(KnownBit.UNKNOWN, MarkerKnowledge.DIFFERS_FROM_INIT, null, Read.CONTROLLER_SETUP_BIT),
                new BranchCase(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, Branch.DO_INIT, null),
                new BranchCase(KnownBit.CLEAR, MarkerKnowledge.EQUALS_INIT, Branch.DO_INIT, null),
                new BranchCase(KnownBit.CLEAR, MarkerKnowledge.DIFFERS_FROM_INIT, Branch.DO_INIT, null),
                new BranchCase(KnownBit.SET, MarkerKnowledge.UNKNOWN, null, Read.INIT_MARKER),
                new BranchCase(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, Branch.GAME_INIT, null),
                new BranchCase(KnownBit.SET, MarkerKnowledge.DIFFERS_FROM_INIT, Branch.DO_INIT, null));
        for (BranchCase vector : cases) {
            State current = new State(vector.controller(), vector.marker(), UNKNOWN_CACHE);
            State equalPreimage = new State(vector.controller(), vector.marker(), new VersionCache(0, 0));
            if (vector.missing() == null) {
                assertEquals(vector.branch(), selectStartBranch(current), vector.toString());
            } else {
                UnknownRead failure = assertThrows(UnknownRead.class, () -> selectStartBranch(current), vector.toString());
                assertEquals(vector.missing(), failure.read());
            }
            assertEquals(equalPreimage, current, "A predicate read cannot write an initialization fact");
        }
    }

    @Test
    void explicitUnknownIngressDoesNotClaimKnownZeroOrSuccessfulSetup() {
        State expected = new State(KnownBit.UNKNOWN, MarkerKnowledge.UNKNOWN, new VersionCache(0, 0));
        assertEquals(expected, unknown());
        assertEquals(expected.hashCode(), unknown().hashCode());
        assertNotEquals(new VersionCache(0, 0xFF), unknown().versionCache());
        assertNotEquals(new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE), unknown());
        assertNotEquals(new State(KnownBit.UNKNOWN, MarkerKnowledge.DIFFERS_FROM_INIT, UNKNOWN_CACHE), unknown());
    }

    @Test
    void environmentKnowledgeIsRequiredOnlyByItsSeparateReachedRead() {
        ExecutionEnvironment absent = new ExecutionEnvironment(Optional.empty());
        State clear = new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE);
        State equal = new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, UNKNOWN_CACHE);
        assertEquals(Branch.DO_INIT, selectStartBranch(clear));
        assertEquals(Branch.GAME_INIT, selectStartBranch(equal));
        UnknownRead failure = assertThrows(UnknownRead.class, () -> requireExecutionRegion(absent));
        assertEquals(Read.EXECUTION_REGION, failure.read());
        assertEquals(new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE), clear);
        assertEquals(new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, UNKNOWN_CACHE), equal);
        assertEquals(Optional.empty(), absent.region());
    }

    @Test
    void fourRegionInputsRetainOnlyTheExplicitNativeC0Domain() {
        for (int bits : new int[]{0x00, 0x40, 0x80, 0xC0}) {
            ExecutionRegion region = new ExecutionRegion(bits);
            ExecutionEnvironment environment = new ExecutionEnvironment(Optional.of(region));
            assertEquals(bits, region.versionC0());
            assertEquals(new ExecutionRegion(bits), requireExecutionRegion(environment));
            assertEquals(new ExecutionEnvironment(Optional.of(new ExecutionRegion(bits))), environment);
            assertEquals(new ExecutionRegion(bits).hashCode(), requireExecutionRegion(environment).hashCode());
        }
        for (int invalid : new int[]{-1, 0x01, 0x20, 0x41, 0x7F, 0xC1, 0xFF, 0x100,
                Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new ExecutionRegion(invalid),
                    "Execution region must be VERSION & C0, not a software variant or arbitrary byte");
        }
    }

    @Test
    void representativeCacheMasksPreservePartialKnowledgeAndCanonicalUnknownStorage() {
        List<VersionCache> valid = List.of(
                new VersionCache(0, 0), new VersionCache(0, 0x40), new VersionCache(0x40, 0x40),
                new VersionCache(0x80, 0x80), new VersionCache(0x80, 0xC0), new VersionCache(0xC0, 0xC0),
                new VersionCache(0x14, 0x15), new VersionCache(0xFF, 0xFF));
        for (VersionCache value : valid) {
            assertEquals(value, new VersionCache(value.value(), value.knownBits()));
            assertEquals(value.hashCode(), new VersionCache(value.value(), value.knownBits()).hashCode());
        }
        int[][] invalid = {
                {-1, 0xFF}, {0x100, 0xFF}, {0, -1}, {0, 0x100},
                {1, 0}, {0x80, 0x40}, {0xC0, 0x40}, {0x01, 0xFE}
        };
        for (int[] vector : invalid) {
            assertThrows(IllegalArgumentException.class, () -> new VersionCache(vector[0], vector[1]));
        }
        assertNotEquals(new VersionCache(0, 0), new VersionCache(0, 0x40));
        assertNotEquals(new VersionCache(0, 0x40), new VersionCache(0x40, 0x40));
        assertEquals(2, Set.of(new VersionCache(0, 0), new VersionCache(0, 0xFF)).size());
    }

    @Test
    void cacheWriterWritesTheWholeMaskedByteAndOnlyThatFact() {
        State before = new State(KnownBit.CLEAR, MarkerKnowledge.DIFFERS_FROM_INIT,
                new VersionCache(0x15, 0x95));
        State equalPreimage = new State(KnownBit.CLEAR, MarkerKnowledge.DIFFERS_FROM_INIT,
                new VersionCache(0x15, 0x95));
        for (int bits : new int[]{0x00, 0x40, 0x80, 0xC0}) {
            State after = writeVersionCache(before, new ExecutionRegion(bits));
            assertEquals(new State(KnownBit.CLEAR, MarkerKnowledge.DIFFERS_FROM_INIT,
                    new VersionCache(bits, 0xFF)), after);
            assertEquals(0, after.versionCache().value() & 0x3F);
            assertEquals(0xFF, after.versionCache().knownBits());
            assertEquals(equalPreimage, before);
        }
        State zero = writeVersionCache(before, new ExecutionRegion(0));
        State eighty = writeVersionCache(zero, new ExecutionRegion(0x80));
        assertEquals(new VersionCache(0x80, 0xFF), eighty.versionCache());
        assertEquals(new VersionCache(0, 0xFF), zero.versionCache());
    }

    @Test
    void markerWriterPreservesControllerAndExactPartialCacheKnowledge() {
        for (MarkerKnowledge marker : MarkerKnowledge.values()) {
            State before = new State(KnownBit.UNKNOWN, marker, PARTIAL_CACHE);
            State after = writeInitMarker(before);
            assertEquals(new State(KnownBit.UNKNOWN, MarkerKnowledge.EQUALS_INIT,
                    new VersionCache(0x40, 0x40)), after);
            assertEquals(new State(KnownBit.UNKNOWN, marker, PARTIAL_CACHE), before);
        }
    }

    @Test
    void controllerWriterPreservesMarkerAndExactPartialCacheKnowledge() {
        for (KnownBit controller : KnownBit.values()) {
            State before = new State(controller, MarkerKnowledge.UNKNOWN, PARTIAL_CACHE);
            State after = initializeControllers(before);
            assertEquals(new State(KnownBit.SET, MarkerKnowledge.UNKNOWN,
                    new VersionCache(0x40, 0x40)), after);
            assertEquals(new State(controller, MarkerKnowledge.UNKNOWN, PARTIAL_CACHE), before);
            assertEquals(Read.INIT_MARKER,
                    assertThrows(UnknownRead.class, () -> selectStartBranch(after)).read());
        }
    }

    @Test
    void separateWritersExposeTheNativeScalarPrefixWithoutClaimingActualPresentation() {
        State before = new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE);
        assertEquals(Branch.DO_INIT, selectStartBranch(before));
        State cached = writeVersionCache(before, new ExecutionRegion(0x80));
        assertEquals(new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, new VersionCache(0x80, 0xFF)), cached);
        State marked = writeInitMarker(cached);
        assertEquals(new State(KnownBit.CLEAR, MarkerKnowledge.EQUALS_INIT, new VersionCache(0x80, 0xFF)), marked);
        assertEquals(Branch.DO_INIT, selectStartBranch(marked), "Marker success does not set the earlier controller gate");
        State controllers = initializeControllers(marked);
        assertEquals(new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0x80, 0xFF)), controllers);
        assertEquals(Branch.GAME_INIT, selectStartBranch(controllers));
        assertEquals(new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE), before);
    }

    @Test
    void skipPredicateDoesNotRepairAnUnknownOrDifferentCachedRegion() {
        State partial = new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, PARTIAL_CACHE);
        State different = new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0xC0, 0xFF));
        ExecutionEnvironment explicitZero = new ExecutionEnvironment(Optional.of(new ExecutionRegion(0)));
        assertEquals(Branch.GAME_INIT, selectStartBranch(partial));
        assertEquals(Branch.GAME_INIT, selectStartBranch(different));
        assertEquals(new ExecutionRegion(0), requireExecutionRegion(explicitZero));
        assertEquals(new VersionCache(0x40, 0x40), partial.versionCache());
        assertEquals(new VersionCache(0xC0, 0xFF), different.versionCache());
        assertEquals(new VersionCache(0, 0),
                new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, UNKNOWN_CACHE).versionCache());
    }

    @Test
    void semanticValueEqualityIncludesAllThreeFactsAndAllCacheKnowledgeBits() {
        State original = new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0x40, 0x40));
        State equalCopy = new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0x40, 0x40));
        assertNotSame(original, equalCopy);
        assertEquals(original, equalCopy);
        assertEquals(original.hashCode(), equalCopy.hashCode());
        assertTrue(Set.of(original).contains(equalCopy));
        assertEquals("semantic value", Map.of(original, "semantic value").get(equalCopy));
        assertNotEquals(original, new State(KnownBit.CLEAR, MarkerKnowledge.EQUALS_INIT, PARTIAL_CACHE));
        assertNotEquals(original, new State(KnownBit.SET, MarkerKnowledge.UNKNOWN, PARTIAL_CACHE));
        assertNotEquals(original, new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0, 0x40)));
        assertNotEquals(original, new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0x40, 0xC0)));
        assertNotEquals(original, new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0x40, 0xFF)));
        ExecutionEnvironment first = new ExecutionEnvironment(Optional.of(new ExecutionRegion(0x80)));
        ExecutionEnvironment equalEnvironment = new ExecutionEnvironment(Optional.of(new ExecutionRegion(0x80)));
        assertEquals(first, equalEnvironment);
        assertEquals(first.hashCode(), equalEnvironment.hashCode());
        assertNotEquals(first, new ExecutionEnvironment(Optional.empty()));
    }

    @Test
    void equalValueCopiesAndRepeatedPureCallsDoNotBecomeIdentityBasedReplayFailures() {
        State first = new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, PARTIAL_CACHE);
        State copy = new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, new VersionCache(0x40, 0x40));
        assertEquals(selectStartBranch(first), selectStartBranch(copy));
        assertEquals(writeVersionCache(first, new ExecutionRegion(0x80)),
                writeVersionCache(copy, new ExecutionRegion(0x80)));
        assertEquals(writeInitMarker(first), writeInitMarker(copy));
        assertEquals(initializeControllers(first), initializeControllers(copy));
        assertEquals(writeInitMarker(first), writeInitMarker(writeInitMarker(first)));
        assertEquals(initializeControllers(first), initializeControllers(initializeControllers(first)));
        State cached = writeVersionCache(first, new ExecutionRegion(0x80));
        assertEquals(cached, writeVersionCache(cached, new ExecutionRegion(0x80)));
        assertEquals(Branch.GAME_INIT, selectStartBranch(first));
        assertEquals(Branch.GAME_INIT, selectStartBranch(first));
        assertEquals(new State(KnownBit.SET, MarkerKnowledge.EQUALS_INIT, PARTIAL_CACHE), first);
    }

    @Test
    void componentRetainsExactlyTheAcceptedFiniteValueShape() {
        assertFalse(Modifier.isPublic(CdProgramInitialization.class.getModifiers()));
        assertTrue(State.class.isRecord());
        assertTrue(VersionCache.class.isRecord());
        assertTrue(ExecutionRegion.class.isRecord());
        assertTrue(ExecutionEnvironment.class.isRecord());
        assertEquals(List.of("controllerSetupBit", "initMarker", "versionCache"),
                Arrays.stream(State.class.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals(List.of(KnownBit.class, MarkerKnowledge.class, VersionCache.class),
                Arrays.stream(State.class.getRecordComponents()).map(component -> component.getType()).toList());
        assertEquals(List.of(KnownBit.UNKNOWN, KnownBit.CLEAR, KnownBit.SET), List.of(KnownBit.values()));
        assertEquals(List.of(MarkerKnowledge.UNKNOWN, MarkerKnowledge.EQUALS_INIT, MarkerKnowledge.DIFFERS_FROM_INIT),
                List.of(MarkerKnowledge.values()));
        assertEquals(List.of(Branch.DO_INIT, Branch.GAME_INIT), List.of(Branch.values()));
        assertEquals(List.of(Read.CONTROLLER_SETUP_BIT, Read.INIT_MARKER, Read.EXECUTION_REGION), List.of(Read.values()));
    }

    @Test
    void nullInputsAreRejectedWithoutSubstitutingUnknownOrKnownZero() {
        State valid = new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE);
        ExecutionRegion region = new ExecutionRegion(0x80);
        assertThrows(NullPointerException.class, () -> new State(null, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE));
        assertThrows(NullPointerException.class, () -> new State(KnownBit.CLEAR, null, UNKNOWN_CACHE));
        assertThrows(NullPointerException.class, () -> new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, null));
        assertThrows(NullPointerException.class, () -> new ExecutionEnvironment(null));
        assertThrows(NullPointerException.class, () -> selectStartBranch(null));
        assertThrows(NullPointerException.class, () -> requireExecutionRegion(null));
        assertThrows(NullPointerException.class, () -> writeVersionCache(null, region));
        assertThrows(NullPointerException.class, () -> writeVersionCache(valid, null));
        assertThrows(NullPointerException.class, () -> writeInitMarker(null));
        assertThrows(NullPointerException.class, () -> initializeControllers(null));
        assertEquals(new State(KnownBit.CLEAR, MarkerKnowledge.UNKNOWN, UNKNOWN_CACHE), valid);
    }

    private record BranchCase(KnownBit controller, MarkerKnowledge marker, Branch branch, Read missing) { }
}
