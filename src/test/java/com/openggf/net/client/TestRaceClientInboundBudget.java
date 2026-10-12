package com.openggf.net.client;

import com.openggf.net.protocol.ControlMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRaceClientInboundBudget {
    @Test
    void queuedServerEventsHaveAFiniteBudget() throws Exception {
        Constructor<RaceClient> constructor = RaceClient.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RaceClient client = constructor.newInstance();
        Field field = RaceClient.class.getDeclaredField("inbound");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Queue<RaceClient.InboundEvent> inbound =
                (Queue<RaceClient.InboundEvent>) field.get(client);

        boolean rejected = false;
        for (int i = 0; i < 1_024; i++) {
            if (!inbound.offer(new RaceClient.Control(new ControlMessage.Pong(i, i)))) {
                rejected = true;
                break;
            }
        }
        assertTrue(rejected, "the network listener must not retain unlimited events");
        assertTrue(inbound.size() <= 512);
    }
}
