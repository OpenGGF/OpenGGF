package com.openggf.integration.presence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestPresenceManager {

    @Test
    void tick_disabledConfigDoesNotConnectOrUpdate() {
        FakeClient client = new FakeClient();
        FakeProvider provider = new FakeProvider(PresenceSnapshot.menu());
        FakeClock clock = new FakeClock();
        PresenceManager manager = new PresenceManager(false, true, true, provider,
                new PresenceFormatter(), client, clock);

        manager.tick();

        assertEquals(0, client.connects);
        assertEquals(List.of(), client.updates);
    }

    @Test
    void tick_firstEnabledTickConnectsAndPublishesMenuPresence() {
        FakeClient client = new FakeClient();
        FakeProvider provider = new FakeProvider(PresenceSnapshot.menu());
        FakeClock clock = new FakeClock();
        PresenceManager manager = new PresenceManager(true, true, true, provider,
                new PresenceFormatter(), client, clock);

        manager.tick();

        assertEquals(1, client.connects);
        assertEquals("OpenGGF - In Menus", client.updates.get(0).details());
    }

    @Test
    void tick_meaningfulSnapshotChangePublishesImmediately() {
        FakeClient client = new FakeClient();
        FakeProvider provider = new FakeProvider(PresenceSnapshot.menu());
        FakeClock clock = new FakeClock();
        PresenceManager manager = new PresenceManager(true, true, true, provider,
                new PresenceFormatter(), client, clock);

        manager.tick();
        provider.snapshot = PresenceSnapshot.gameplay("Sonic 2", "Emerald Hill Zone", 1,
                "Sonic", "0:01", 60);
        manager.tick();

        assertEquals(2, client.updates.size());
        assertEquals("OpenGGF - Sonic 2", client.updates.get(1).details());
    }

    @Test
    void tick_menuModeChangesPublishImmediately() {
        FakeClient client = new FakeClient();
        FakeProvider provider = new FakeProvider(PresenceSnapshot.menu("Master Title", null));
        FakeClock clock = new FakeClock();
        PresenceManager manager = new PresenceManager(true, true, true, provider,
                new PresenceFormatter(), client, clock);

        manager.tick();
        provider.snapshot = PresenceSnapshot.menu("Level Select", "Sonic 2");
        manager.tick();

        assertEquals(2, client.updates.size());
        assertEquals("OpenGGF - Master Title", client.updates.get(0).details());
        assertEquals("OpenGGF - Sonic 2", client.updates.get(1).details());
        assertEquals("Level Select", client.updates.get(1).state());
    }

    @Test
    void tick_timerOnlyChangesAreThrottledToFifteenSeconds() {
        FakeClient client = new FakeClient();
        FakeProvider provider = new FakeProvider(PresenceSnapshot.gameplay("Sonic 2",
                "Emerald Hill Zone", 1, "Sonic", "0:01", 60));
        FakeClock clock = new FakeClock();
        PresenceManager manager = new PresenceManager(true, true, true, provider,
                new PresenceFormatter(), client, clock);

        manager.tick();
        provider.snapshot = PresenceSnapshot.gameplay("Sonic 2", "Emerald Hill Zone", 1,
                "Sonic", "0:02", 120);
        clock.now = 14_999;
        manager.tick();
        clock.now = 15_000;
        manager.tick();

        assertEquals(2, client.updates.size());
        assertEquals("Emerald Hill Zone Act 1 as Sonic - 0:02", client.updates.get(1).state());
    }

    @Test
    void tick_clientFailureClosesAndDisablesPresenceForRun() {
        FakeClient client = new FakeClient();
        client.failUpdate = true;
        FakeProvider provider = new FakeProvider(PresenceSnapshot.menu());
        FakeClock clock = new FakeClock();
        PresenceManager manager = new PresenceManager(true, true, true, provider,
                new PresenceFormatter(), client, clock);

        manager.tick();
        manager.tick();

        assertEquals(1, client.connects);
        assertEquals(1, client.closes);
        assertFalse(manager.isEnabledForRun());
    }

    private static final class FakeProvider implements PresenceSnapshotProvider {
        private PresenceSnapshot snapshot;

        private FakeProvider(PresenceSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public PresenceSnapshot capture() {
            return snapshot;
        }
    }

    private static final class FakeClient implements PresenceClient {
        private int connects;
        private int closes;
        private boolean failUpdate;
        private final List<PresencePayload> updates = new ArrayList<>();

        @Override
        public void connect() {
            connects++;
        }

        @Override
        public void update(PresencePayload payload) throws IOException {
            if (failUpdate) {
                throw new IOException("boom");
            }
            updates.add(payload);
        }

        @Override
        public void clear() {
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private static final class FakeClock implements LongSupplier {
        private long now;

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
