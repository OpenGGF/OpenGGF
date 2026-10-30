package com.openggf.game.session;

import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.internal.PatternModuleQueueOwner;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.resources.PlayerArtSourceAddressProfile;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.timing.HardwareReadinessAdmissionPolicy;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.resources.KosinskiModuleQueue;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class TestGameplayModeContextConstructionCleanup {
    @BeforeEach
    void configureServices() {
        TestEnvironment.resetEngineOnly();
    }

    @AfterEach
    void resetServices() {
        TestEnvironment.resetEngineOnly();
    }

    @ParameterizedTest
    @EnumSource(value = FailureStage.class,
            names = {"QUEUE_BIND", "SOURCE_LOOKUP", "SOURCE_ADDRESS", "RULES"})
    void constructorFailureReleasesTheAcquiredArtOwnerExactlyOnce(FailureStage stage) {
        var primary = new IllegalStateException("gameplay construction failed");
        var module = new ConstructionModule(stage, primary, null);
        var world = new WorldSession(module);

        assertSame(primary, assertThrows(IllegalStateException.class,
                () -> new GameplayModeContext(world, HardwareReadinessAdmissionPolicy.RECORDED)));

        assertEquals(1, module.acquisitions);
        assertEquals(1, module.art.queueBindings);
        if (stage == FailureStage.SOURCE_ADDRESS || stage == FailureStage.RULES) {
            assertTrue(module.sourceAddressReads > 0,
                    "The supplied source-address profile must run during construction");
        }
        assertEquals(1, module.art.resets,
                "A constructor that did not return must release its acquired art owner");
        assertArrayEquals(new Throwable[0], primary.getSuppressed());
    }

    @Test
    void cleanupFailureIsSuppressedOnTheOriginalConstructorError() {
        var primary = new AssertionError("source-address resolution failed");
        var cleanupFailure = new IllegalStateException("art cleanup failed");
        var module = new ConstructionModule(FailureStage.SOURCE_ADDRESS, primary, cleanupFailure);
        var world = new WorldSession(module);

        assertSame(primary, assertThrows(AssertionError.class,
                () -> new GameplayModeContext(world, HardwareReadinessAdmissionPolicy.RECORDED)));

        assertTrue(module.sourceAddressReads > 0);
        assertEquals(1, module.art.resets);
        assertArrayEquals(new Throwable[]{cleanupFailure}, primary.getSuppressed());
    }

    @Test
    void successfulConstructionKeepsItsArtOwnerUntilNormalTeardown() {
        var module = new ConstructionModule(FailureStage.NONE, null, null);
        var context = new GameplayModeContext(new WorldSession(module),
                HardwareReadinessAdmissionPolicy.RECORDED);
        try {
            assertSame(module.art, context.runtimeArtCoordinator());
            assertEquals(1, module.acquisitions);
            assertEquals(1, module.art.queueBindings);
            assertTrue(module.sourceAddressReads > 0);
            assertEquals(0, module.art.resets);
        } finally {
            context.tearDownManagers();
        }
        assertEquals(1, module.art.resets);
    }

    private enum FailureStage {
        QUEUE_BIND,
        SOURCE_LOOKUP,
        SOURCE_ADDRESS,
        RULES,
        NONE
    }

    private static final class ConstructionModule extends DelegatingGameModule {
        private final FailureStage failureStage;
        private final Throwable primaryFailure;
        private final ArtOwner art;
        private int acquisitions;
        private int sourceAddressReads;

        private ConstructionModule(FailureStage failureStage, Throwable primaryFailure,
                                   RuntimeException cleanupFailure) {
            super(new Sonic2GameModule(), "test:construction-cleanup");
            this.failureStage = failureStage;
            this.primaryFailure = primaryFailure;
            art = new ArtOwner(cleanupFailure);
        }

        @Override
        public RuntimeArtCoordinator createRuntimeArtCoordinator(HardwareTimingService timing) {
            acquisitions++;
            return art;
        }

        @Override
        public <T> T getGameService(Class<T> type) {
            if (type == PlayerArtSourceAddressProfile.class && acquisitions != 0) {
                failAt(FailureStage.SOURCE_LOOKUP);
                return type.cast((PlayerArtSourceAddressProfile) (game, owner, canonicalAddress) -> {
                    sourceAddressReads++;
                    failAt(FailureStage.SOURCE_ADDRESS);
                    return canonicalAddress;
                });
            }
            return super.getGameService(type);
        }

        @Override
        public GameRules getRules() {
            if (acquisitions != 0) failAt(FailureStage.RULES);
            return super.getRules();
        }

        private void failAt(FailureStage stage) {
            if (failureStage != stage) return;
            if (primaryFailure instanceof RuntimeException failure) throw failure;
            throw (Error) primaryFailure;
        }

        private final class ArtOwner implements RuntimeArtCoordinator, PatternModuleQueueOwner {
            private final RuntimeException cleanupFailure;
            private int queueBindings;
            private int resets;

            private ArtOwner(RuntimeException cleanupFailure) {
                this.cleanupFailure = cleanupFailure;
            }

            @Override
            public void bindPatternModuleQueue(KosinskiModuleQueue queue) {
                queueBindings++;
                failAt(FailureStage.QUEUE_BIND);
            }

            @Override
            public void resetForMissingSnapshot() {
                resets++;
                if (cleanupFailure != null) throw cleanupFailure;
            }
        }
    }
}
