package openggf.racing.server.master;

import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Opt-in JFR observations for the October 2026 relay scale study; no gameplay authority. */
public final class RelayTimingEvents {
    private RelayTimingEvents() { }

    @Name("openggf.RelayRoomTick")
    @Label("Relay room tick and queue delay")
    @StackTrace(false)
    public static final class RoomTick extends Event {
        public String roomId;
        public long queueDelayNanos;
    }

    @Name("openggf.RelayTickDispatch")
    @Label("Broker relay tick dispatch lateness")
    @StackTrace(false)
    public static final class Dispatch extends Event {
        public long latenessNanos;
    }

    private static final EventType ROOM = EventType.getEventType(RoomTick.class);
    private static final EventType DISPATCH = EventType.getEventType(Dispatch.class);

    static boolean roomEnabled() { return ROOM.isEnabled(); }

    static void roomTick(String roomId, long queuedAt, Runnable tick) {
        if (queuedAt == 0) {
            tick.run();
            return;
        }
        RoomTick event = new RoomTick();
        event.roomId = roomId;
        event.queueDelayNanos = Math.max(0, System.nanoTime() - queuedAt);
        event.begin();
        try {
            tick.run();
        } finally {
            event.end();
            event.commit();
        }
    }

    static void dispatch(long expectedAt) {
        if (DISPATCH.isEnabled()) {
            Dispatch event = new Dispatch();
            event.latenessNanos = Math.max(0, System.nanoTime() - expectedAt);
            event.commit();
        }
    }
}
