package com.openggf.net.host;

import com.openggf.net.flow.WorkBudget;
import com.openggf.net.hub.HubConnection;
import com.openggf.net.hub.RoomAdmissionBridge;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.GhostPackets;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Player-host endpoint for guests tunneled through the host's master connection. */
@com.openggf.game.ModApi
public final class HostMasterLink {
    volatile java.util.function.Supplier<String> authenticationBinding =
            () -> { throw new IllegalStateException("master transport has not been bound"); };
    @com.openggf.game.ModApi
    public interface MessageSink {
        void sendControl(ControlMessage message);
        void sendBinary(byte[] data);
    }

    private final BiConsumer<HubConnection, ControlMessage.RelayGuestOpen> onConnected;
    private final BiConsumer<HubConnection, String> onText;
    private final BiConsumer<HubConnection, byte[]> onBinary;
    private final Consumer<HubConnection> onDisconnected;
    private final Consumer<List<HubConnection>> onMasterLost;
    private final Consumer<ControlMessage.RoomRouteRetireNotice> onRouteRetire;
    private final Consumer<Runnable> controlExecutor;
    private final Consumer<Runnable> dataExecutor;
    private final Consumer<Runnable> retirementExecutor;
    private final MessageSink sink;
    private final Map<Integer, Guest> guests = new ConcurrentHashMap<>();
    private final WorkBudget.Account controls = WorkBudget.PROCESS.account(512, 8L * 1024 * 1024);
    private final WorkBudget.Account data = WorkBudget.PROCESS.account(512, 8L * 1024 * 1024);
    private volatile boolean retired;

    HostMasterLink(Consumer<HubConnection> onConnected,
                          BiConsumer<HubConnection, String> onText,
                          BiConsumer<HubConnection, byte[]> onBinary,
                          Consumer<HubConnection> onDisconnected,
                          MessageSink sink) {
        this((connection, ignored) -> onConnected.accept(connection), onText, onBinary,
                onDisconnected, sink,
                abandoned -> abandoned.forEach(onDisconnected),
                ignored -> { }, Runnable::run, Runnable::run, Runnable::run);
    }

    private HostMasterLink(BiConsumer<HubConnection, ControlMessage.RelayGuestOpen> onConnected,
                           BiConsumer<HubConnection, String> onText,
                           BiConsumer<HubConnection, byte[]> onBinary,
                           Consumer<HubConnection> onDisconnected,
                           MessageSink sink, Consumer<List<HubConnection>> onMasterLost,
                           Consumer<ControlMessage.RoomRouteRetireNotice> onRouteRetire,
                           Consumer<Runnable> controlExecutor, Consumer<Runnable> dataExecutor,
                           Consumer<Runnable> retirementExecutor) {
        this.onConnected = onConnected;
        this.onText = onText;
        this.onBinary = onBinary;
        this.onDisconnected = onDisconnected;
        this.onMasterLost = onMasterLost;
        this.onRouteRetire = onRouteRetire;
        this.sink = sink;
        this.controlExecutor = controlExecutor;
        this.dataExecutor = dataExecutor;
        this.retirementExecutor = retirementExecutor;
    }

    public static HostMasterLink forServer(RaceRoomHost server, MessageSink sink) {
        return new HostMasterLink(
                (connection, open) -> RoomAdmissionBridge.connectTunnel(server.room(), connection, open),
                (connection, text) -> server.room().onText(connection, text),
                (connection, data) -> server.room().onBinary(connection, data),
                connection -> server.room().onDisconnected(connection), sink,
                abandoned -> {
                    server.room().brokerUnavailable();
                    abandoned.forEach(server.room()::onDisconnected);
                },
                notice -> {
                    if (!server.room().instanceId().equals(notice.instanceId())) return;
                    server.room().retireRoute(notice.grantReference(), notice.routeGeneration());
                    sink.sendControl(new ControlMessage.RoomRouteRetireAck(notice.roomId(),
                            notice.instanceId(), notice.grantReference(),
                            notice.routeGeneration(), notice.retirementId()));
                }, server::executeControl, server::execute, server::executeLinkRetirement);
    }

    public void onMasterControl(ControlMessage message) {
        if (message instanceof ControlMessage.RelayGuestText text) {
            Guest guest;
            synchronized (this) {
                if (retired) return;
                guest = guests.get(text.guestId());
            }
            if (guest != null) submitData(guest, 2L * text.text().length(),
                    () -> onText.accept(guest.connection, text.text()));
            return;
        }
        if (message instanceof ControlMessage.RoomRouteRetireNotice notice) {
            retireRoute(notice);
        } else if (message instanceof ControlMessage.RelayGuestOpen open) {
            open(open);
        } else if (message instanceof ControlMessage.RelayGuestClose close) {
            synchronized (this) {
                if (retired) return;
                Guest guest = guests.get(close.guestId());
                if (guest != null) guest.retire(false, close.reason());
            }
        }
    }

    public void onMasterBinary(byte[] packet) {
        if (!GhostPackets.hasRelayGuestBinaryHeader(packet)) return;
        int guestId = (packet[1] & 0xFF) << 8 | (packet[2] & 0xFF);
        Guest guest;
        synchronized (this) {
            if (retired) return;
            guest = guests.get(guestId);
        }
        if (guest == null) return;
        WorkBudget.Admission<WorkBudget.Reservation> admission = reserveGuestData(guest, packet.length);
        if (!admission.accepted()) {
            if (admission.refusal() == WorkBudget.Refusal.ORIGIN)
                guest.retire(true, "tunnel source capacity unavailable");
            return;
        }
        WorkBudget.Reservation reserved = admission.value();
        try {
            GhostPackets.RelayGuestBinary wrapped =
                    GhostPackets.decodeRelayGuestBinary(packet);
            dispatchData(guest, reserved, () -> onBinary.accept(guest.connection, wrapped.payload()));
        } catch (RuntimeException | Error failure) {
            reserved.close();
            throw failure;
        }
    }

    public synchronized void onMasterLost() {
        if (retired) return;
        retired = true;
        List<Guest> abandoned = guests.values().stream()
                .filter(guest -> guest.retiring.compareAndSet(false, true)).toList();
        guests.clear();
        WorkBudget.Task retirement = WorkBudget.Task.owned(
                () -> onMasterLost.accept(abandoned.stream().map(guest -> guest.connection).toList()),
                () -> abandoned.forEach(guest -> guest.closeReservation.close()));
        submit(retirementExecutor, retirement);
    }

    private void open(ControlMessage.RelayGuestOpen open) {
        long bytes = retainedStringBytes(open.roomId(), open.instanceId(), open.fingerprint(),
                open.grantReference());
        var admission = reserveControls(List.of(new WorkBudget.Cost(1, bytes),
                new WorkBudget.Cost(1, 0)));
        if (!admission.accepted()) {
            if (admission.refusal() == WorkBudget.Refusal.ORIGIN) {
                synchronized (this) {
                    if (retired) return;
                    Guest previous = guests.get(open.guestId());
                    if (previous != null) previous.retire(false, "tunnel replacement unavailable");
                    sink.sendControl(new ControlMessage.RelayGuestClose(open.guestId(),
                            "tunnel owner capacity unavailable"));
                }
            }
            return;
        }
        List<WorkBudget.Reservation> reservations = admission.value();
        Guest guest = new Guest(open.guestId(), reservations.getLast());
        WorkBudget.Task connected = WorkBudget.Task.reserved(reservations.getFirst(),
                reservations, () -> {
                    if (!guest.active()) return;
                    try { onConnected.accept(guest.connection, open); }
                    catch (RuntimeException | Error failure) {
                        guest.retire(true, "tunnel owner callback failed");
                        throw failure;
                    }
                }, guest::discardOpen);
        Guest previous;
        synchronized (this) {
            if (retired) {
                connected.close();
                return;
            }
            previous = guests.put(open.guestId(), guest);
        }
        try {
            if (previous != null) previous.retire(false, "tunnel replaced");
            submit(controlExecutor, connected);
        } catch (RuntimeException | Error failure) {
            connected.close();
            throw failure;
        }
    }

    private void retireRoute(ControlMessage.RoomRouteRetireNotice notice) {
        var admission = reserveControls(List.of(new WorkBudget.Cost(1,
                retainedStringBytes(notice.roomId(), notice.instanceId(),
                        notice.grantReference()))));
        if (!admission.accepted()) {
            if (admission.refusal() == WorkBudget.Refusal.ORIGIN) onMasterLost();
            return;
        }
        WorkBudget.Reservation reserved = admission.value().getFirst();
        synchronized (this) {
            if (retired) {
                reserved.close();
                return;
            }
            if (!submit(controlExecutor, WorkBudget.Task.reserved(reserved, List.of(reserved),
                    () -> onRouteRetire.accept(notice), () -> { }))) onMasterLost();
        }
    }

    private void submitData(Guest guest, long bytes, Runnable action) {
        WorkBudget.Admission<WorkBudget.Reservation> admission = reserveGuestData(guest, bytes);
        if (!admission.accepted()) {
            if (admission.refusal() == WorkBudget.Refusal.ORIGIN)
                guest.retire(true, "tunnel source capacity unavailable");
            return;
        }
        dispatchData(guest, admission.value(), action);
    }

    private WorkBudget.Admission<WorkBudget.Reservation> reserveGuestData(Guest guest, long bytes) {
        while (guest.active()) {
            var admission = WorkBudget.PROCESS.tryReserveDetailed(guest.dataAccount, data,
                    new WorkBudget.Cost(1, bytes));
            if (admission.accepted() || admission.refusal() == WorkBudget.Refusal.ORIGIN)
                return admission;
            try {
                WorkBudget.PROCESS.awaitChange(50);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new WorkBudget.Admission<>(null, null);
    }

    private void dispatchData(Guest guest, WorkBudget.Reservation reserved, Runnable action) {
        if (!submit(dataExecutor, WorkBudget.Task.reserved(reserved, List.of(reserved),
                () -> { if (guest.active()) action.run(); }, () -> { })))
            guest.retire(true, "tunnel owner capacity unavailable");
    }

    private WorkBudget.Admission<List<WorkBudget.Reservation>> reserveControls(
            List<WorkBudget.Cost> costs) {
        while (!retired) {
            var admission = WorkBudget.PROCESS.tryReserveBatchDetailed(controls, controls, costs);
            if (admission.accepted() || admission.refusal() == WorkBudget.Refusal.ORIGIN)
                return admission;
            try {
                WorkBudget.PROCESS.awaitChange(50);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new WorkBudget.Admission<>(null, null);
    }

    private static boolean submit(Consumer<Runnable> executor, WorkBudget.Task task) {
        try {
            executor.accept(task);
            return true;
        } catch (IllegalStateException | RejectedExecutionException unavailable) {
            task.close();
            return false;
        } catch (RuntimeException | Error failure) {
            task.close();
            throw failure;
        }
    }

    private static long retainedStringBytes(String... values) {
        long bytes = 0;
        for (String value : values) if (value != null) bytes += 2L * value.length();
        return bytes;
    }

    private final class Guest {
        private final int guestId;
        private final HubConnection connection;
        private final WorkBudget.Account dataAccount = WorkBudget.PROCESS.account(64, 512L * 1024);
        private final WorkBudget.Reservation closeReservation;
        private final AtomicBoolean retiring = new AtomicBoolean();

        private Guest(int guestId, WorkBudget.Reservation closeReservation) {
            this.guestId = guestId;
            this.closeReservation = closeReservation;
            connection = HostLinkAuthentication.boundTunnel(new TunnelConnection(this),
                    () -> authenticationBinding.get());
        }

        private boolean active() {
            return !retired && !retiring.get() && guests.get(guestId) == this;
        }

        private void discardOpen() {
            synchronized (HostMasterLink.this) {
                if (!retiring.compareAndSet(false, true)) return;
                boolean owned = guests.remove(guestId, this);
                closeReservation.close();
                if (owned && !retired) sink.sendControl(new ControlMessage.RelayGuestClose(guestId,
                        "tunnel owner capacity unavailable"));
            }
        }

        private void retire(boolean notify, String reason) {
            synchronized (HostMasterLink.this) {
                if (!retiring.compareAndSet(false, true)) return;
                boolean owned = guests.remove(guestId, this);
                submit(controlExecutor, WorkBudget.Task.reserved(closeReservation,
                        List.of(closeReservation), () -> onDisconnected.accept(connection), () -> { }));
                if (notify && owned && !retired)
                    sink.sendControl(new ControlMessage.RelayGuestClose(guestId, reason));
            }
        }
    }

    private final class TunnelConnection implements HubConnection {
        private final Guest guest;

        private TunnelConnection(Guest guest) { this.guest = guest; }

        @Override public void sendText(String text) {
            synchronized (HostMasterLink.this) {
                if (guest.active()) sink.sendControl(new ControlMessage.RelayGuestText(guest.guestId, text));
            }
        }

        @Override public void sendBinary(byte[] data) {
            synchronized (HostMasterLink.this) {
                if (guest.active()) sink.sendBinary(GhostPackets.encodeRelayGuestBinary(guest.guestId, data));
            }
        }

        @Override public void close(String reason) {
            guest.retire(true, reason);
        }

        @Override public String remoteHost() {
            return "relay:" + guest.guestId;
        }
    }
}
