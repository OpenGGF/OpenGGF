package com.openggf.game.mutators;

import com.openggf.game.LevelLoadCause;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.LevelMutatorTestWorld;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelMutatorRuntime {
    private static final LevelMutatorPolicy DENY = new LevelMutatorPolicy(Set.of(), true, true, true, true);

    @Test void preparedEntriesSurviveLaterDenialAndOnlyPublishOnce() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        long special = LevelMutatorPolicyAccess.admit(services, StageEntryKind.SPECIAL);
        long bonus = LevelMutatorPolicyAccess.admit(services, StageEntryKind.BONUS);
        services.policy(DENY);
        assertEquals(0, LevelMutatorPolicyAccess.admit(services, StageEntryKind.SPECIAL));
        assertFalse(LevelMutatorPolicyAccess.directEntryAllowed(services.worldSession(), StageEntryKind.SPECIAL));
        assertFalse(LevelMutatorPolicyAccess.publish(services, StageEntryKind.BONUS, special, () -> fail("wrong entry kind")));
        AtomicInteger published = new AtomicInteger();
        assertTrue(LevelMutatorPolicyAccess.publish(services, StageEntryKind.SPECIAL, special, () -> {
            assertTrue(LevelMutatorPolicyAccess.directEntryAllowed(services.worldSession(), StageEntryKind.SPECIAL));
            assertFalse(LevelMutatorPolicyAccess.directEntryAllowed(services.worldSession(), StageEntryKind.BONUS));
            published.incrementAndGet();
        }));
        assertFalse(LevelMutatorPolicyAccess.publish(services, StageEntryKind.SPECIAL, special, published::incrementAndGet));
        assertTrue(LevelMutatorPolicyAccess.publish(services, StageEntryKind.BONUS, bonus, published::incrementAndGet));
        assertEquals(2, published.get());
        assertFalse(services.runtime().isPublishing(StageEntryKind.SPECIAL));
    }

    @Test void captureRestoresEntryOwnershipAndResultsHandoffWithoutCrossWorldAuthority() {
        var first = new LevelMutatorTestWorld(new Sonic2GameModule());
        var second = new LevelMutatorTestWorld(new Sonic2GameModule());
        long permit = first.runtime().tryAdmit(StageEntryKind.SPECIAL);
        first.runtime().holdResultsPermit(permit);
        var snapshot = first.runtime().capture();
        assertFalse(second.runtime().publish(StageEntryKind.SPECIAL, permit, () -> fail("foreign permit")));
        assertThrows(IllegalArgumentException.class, () -> second.runtime().restore(snapshot));
        assertTrue(first.runtime().publish(StageEntryKind.SPECIAL, permit, () -> { }));
        assertEquals(0, first.runtime().resultsPermit());
        first.runtime().restore(snapshot);
        assertEquals(permit, first.runtime().resultsPermit());
        assertTrue(first.runtime().publish(StageEntryKind.SPECIAL, permit, () -> { }));
    }

    @Test void loadResetDuringPublicationPreservesOnlyTheScopedMatchingEntry() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        long permit = services.runtime().tryAdmit(StageEntryKind.SPECIAL);
        long canceled = services.runtime().tryAdmit(StageEntryKind.BONUS);
        services.policy(DENY);
        assertTrue(services.runtime().publish(StageEntryKind.SPECIAL, permit, () -> {
            services.runtime().clear();
            assertTrue(LevelMutatorPolicyAccess.directEntryAllowed(services.worldSession(), StageEntryKind.SPECIAL));
            assertFalse(LevelMutatorPolicyAccess.directEntryAllowed(services.worldSession(), StageEntryKind.BONUS));
        }));
        assertFalse(LevelMutatorPolicyAccess.directEntryAllowed(services.worldSession(), StageEntryKind.SPECIAL));
        assertFalse(services.runtime().publish(StageEntryKind.BONUS, canceled, () -> fail("canceled entry")));
    }

    @Test void retainedRuntimeCannotAdmitPublishOrRestoreAfterLifetimeRetirement() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        var retained = LevelMutatorPolicyAccess.runtime(services.worldSession());
        long permit = retained.tryAdmit(StageEntryKind.SPECIAL);
        retained.holdResultsPermit(permit);
        var beforeRetirement = retained.capture();
        retained.retire();
        services.policy(LevelMutatorPolicy.STOCK);
        retained.clear();
        assertEquals(0, retained.resultsPermit());
        assertEquals(0, retained.tryAdmit(StageEntryKind.SPECIAL));
        assertEquals(0, retained.tryAdmit(StageEntryKind.BONUS));
        assertFalse(retained.publish(StageEntryKind.SPECIAL, permit, () -> fail("retired publication")));
        assertFalse(retained.isPublishing(StageEntryKind.SPECIAL));
        assertThrows(IllegalStateException.class, () -> retained.restore(beforeRetirement));
    }

    @Test void reusingModuleProviderKeepsRuntimeBoundToEachWorldOwner() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        var first = services.runtime();
        var secondWorld = new com.openggf.game.session.WorldSession(new Sonic2GameModule(), services.module(), null);
        var second = LevelMutatorPolicyAccess.runtime(secondWorld);
        assertNotSame(first, second);
        assertSame(first, services.runtime());
        long permit = first.tryAdmit(StageEntryKind.BONUS);
        first.retire();
        assertFalse(first.publish(StageEntryKind.BONUS, permit, () -> fail("retired owner")));
        assertNotEquals(0, second.tryAdmit(StageEntryKind.BONUS));
    }

    @Test void queuedLoadRequestsDoNotAlterCurrentAdmissionAndDisableUsesSameBoundary() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        services.request(DENY);
        for (var cause : new LevelLoadCause[]{LevelLoadCause.PREVIEW, LevelLoadCause.CHECKPOINT_RESTORE,
                LevelLoadCause.EDITOR_SWAP, LevelLoadCause.SEAMLESS_HANDOFF, LevelLoadCause.DECODE_ONLY}) {
            WorldSessionPolicyAccess.beforeAssembly(services.worldSession(), cause);
            assertTrue(LevelMutatorPolicyAccess.ringsAllowed(services));
        }
        WorldSessionPolicyAccess.beforeAssembly(services.worldSession(), LevelLoadCause.FULL_DEATH_RELOAD);
        assertFalse(LevelMutatorPolicyAccess.ringsAllowed(services));
        services.request(LevelMutatorPolicy.STOCK);
        assertFalse(LevelMutatorPolicyAccess.ringsAllowed(services));
        WorldSessionPolicyAccess.beforeAssembly(services.worldSession(), LevelLoadCause.FULL_RESTART);
        assertTrue(LevelMutatorPolicyAccess.ringsAllowed(services));
    }
}
