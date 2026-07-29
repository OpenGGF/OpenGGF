package com.openggf.level;

import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.level.resources.DeferredLevelResourceManifest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestSeamlessTransitionResourceHandoffRegistry {

    @Test
    void claimIsSingleUseButRewindRestoresThePendingOwner() {
        SeamlessTransitionResourceHandoffRegistry registry =
                new SeamlessTransitionResourceHandoffRegistry();
        TestHandoff handoff = new TestHandoff();
        SeamlessTransitionResourceHandoffId id =
                registry.register(handoff);
        SeamlessTransitionResourceHandoffRegistry.Snapshot beforeClaim =
                registry.capture();

        assertSame(handoff, registry.claim(id));
        assertThrows(IllegalStateException.class,
                () -> registry.claim(id));

        registry.restore(beforeClaim);
        assertSame(handoff, registry.claim(id));
        assertThrows(IllegalStateException.class,
                () -> registry.claim(id));
    }

    @Test
    void compositeRewindOnBothSidesOfClaimRestoresExactOwnership() {
        SeamlessTransitionResourceHandoffRegistry handoffs =
                new SeamlessTransitionResourceHandoffRegistry();
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(handoffs);
        TestHandoff handoff = new TestHandoff();
        SeamlessTransitionResourceHandoffId id =
                handoffs.register(handoff);
        CompositeSnapshot beforeClaim = rewind.capture();

        assertSame(handoff, handoffs.claim(id));
        CompositeSnapshot afterClaim = rewind.capture();

        rewind.restore(beforeClaim);
        assertSame(handoff, handoffs.peek(id));
        rewind.restore(afterClaim);
        assertThrows(IllegalStateException.class,
                () -> handoffs.peek(id));
    }

    @Test
    void claimedHandoffStaysConsumedWhenExecutionFailsAfterClaim() {
        SeamlessTransitionResourceHandoffRegistry registry =
                new SeamlessTransitionResourceHandoffRegistry();
        SeamlessTransitionResourceHandoffId id =
                registry.register(new TestHandoff());

        registry.claim(id);

        assertThrows(IllegalStateException.class,
                () -> registry.peek(id));
        assertThrows(IllegalStateException.class,
                () -> registry.claim(id));
    }

    @Test
    void postInitTransferRunsExactlyOncePerSuccessfulClaim() {
        SeamlessTransitionResourceHandoffRegistry registry =
                new SeamlessTransitionResourceHandoffRegistry();
        TestHandoff handoff = new TestHandoff();
        SeamlessTransitionResourceHandoffId id =
                registry.register(handoff);

        registry.claim(id).transferAfterTargetInit();

        assertEquals(1, handoff.transferCount.get());
        assertThrows(IllegalStateException.class,
                () -> registry.claim(id));
    }

    private static final class TestHandoff
            implements SeamlessTransitionResourceHandoff {
        private final AtomicInteger transferCount = new AtomicInteger();

        @Override
        public DeferredLevelResourceManifest deferredResources() {
            return DeferredLevelResourceManifest.EMPTY;
        }

        @Override
        public void transferAfterTargetInit() {
            transferCount.incrementAndGet();
        }
    }
}
