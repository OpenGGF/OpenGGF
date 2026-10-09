package openggf.timeattack.mp;

import com.openggf.mods.scene.SceneKeys;
import openggf.racing.protocol.ControlMessage;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The master room browser scene view. */
class TestServerBrowserView {
    private final FakeViewInput input = new FakeViewInput();
    private final List<MenuCue> cues = new ArrayList<>();
    private final RecordingActions actions = new RecordingActions();

    /** A directory that answers from scripted futures, then from a fixed room list. */
    private static final class Directory implements RoomDirectory {
        final Deque<CompletableFuture<ControlMessage.RoomListResult>> scripted = new ArrayDeque<>();
        final List<Integer> requestedPages = new ArrayList<>();
        List<ControlMessage.RoomSummary> rooms;
        boolean open = true;

        Directory(List<ControlMessage.RoomSummary> rooms) {
            this.rooms = rooms;
        }

        @Override public boolean isOpen() { return open; }

        @Override
        public CompletableFuture<ControlMessage.RoomListResult> listRooms(String gameId, int page) {
            assertEquals("s2", gameId);
            requestedPages.add(page);
            if (!scripted.isEmpty()) return scripted.poll();
            return CompletableFuture.completedFuture(new ControlMessage.RoomListResult(rooms, page, 1));
        }
    }

    private static final class RecordingActions implements ServerBrowserView.Actions {
        final List<ControlMessage.RoomSummary> joined = new ArrayList<>();
        final List<String> created = new ArrayList<>();
        int backs;

        @Override public void join(ControlMessage.RoomSummary room) { joined.add(room); }
        @Override public void create(String routing) { created.add(routing); }
        @Override public void back() { backs++; }
    }

    private ServerBrowserView browser(Directory directory) {
        return new ServerBrowserView(directory, "s2", actions, cues::add, () -> 0L);
    }

    private static ControlMessage.RoomSummary room(String id) {
        return new ControlMessage.RoomSummary(id, id, "s2", 0, 0, "OPEN", 1, 8, "RELAY", true);
    }

    @Test
    void visibleActionsCreateAndRefresh() {
        Directory directory = new Directory(List.of());
        ServerBrowserView view = browser(directory);
        input.tapRight(view::update);
        input.tapEnter(view::update);
        assertEquals(List.of("DIRECT"), actions.created);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        assertEquals(2, directory.requestedPages.size());
        assertTrue(actions.joined.isEmpty());
    }

    @Test
    void everyRoomRemainsReachablePastTheVisiblePage() {
        List<ControlMessage.RoomSummary> rooms = IntStream.range(0, 14).mapToObj(i ->
                new ControlMessage.RoomSummary("room" + i, "Room " + i, "s2", 0, 0,
                        "OPEN", 1, 8, "RELAY", true)).toList();
        ServerBrowserView view = browser(new Directory(rooms));
        for (int i = 0; i < 13; i++) input.tapDown(view::update);
        input.tapEnter(view::update);
        assertEquals(List.of(rooms.get(13)), actions.joined);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        assertEquals(List.of("RELAY"), actions.created);
    }

    @Test
    void backWinsOverSimultaneousAccept() {
        ServerBrowserView view = browser(new Directory(List.of()));
        input.clear();
        input.back = true;
        input.accept = true;
        view.update(input);
        assertEquals(1, actions.backs);
        assertTrue(actions.created.isEmpty());
    }

    @Test
    void longRoomLabelsStayBoundedAndSelectionScrollsIntoView() {
        List<ControlMessage.RoomSummary> rooms = IntStream.range(0, 14).mapToObj(i ->
                new ControlMessage.RoomSummary("room" + i, "Room" + i + "-" + "long".repeat(30), "s2", 0, 0,
                        "OPEN", 1, 8, "RELAY", false)).toList();
        ServerBrowserView view = browser(new Directory(rooms));
        for (int i = 0; i < 13; i++) input.tapDown(view::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertTrue(canvas.lines.stream().anyMatch(line -> line.startsWith("Room13-")), canvas.joined());
        assertFalse(canvas.lines.stream().anyMatch(line -> line.startsWith("Room0-")), canvas.joined());
    }

    @Test
    void transportCompletionOnlyPublishesDuringUpdateAndKeepsRoomIdentity() throws Exception {
        var a = room("a");
        var b = room("b");
        Directory directory = new Directory(List.of(a, b));
        var future = new CompletableFuture<ControlMessage.RoomListResult>();
        directory.scripted.add(CompletableFuture.completedFuture(new ControlMessage.RoomListResult(List.of(a, b), 0, 1)));
        directory.scripted.add(future);
        ServerBrowserView view = browser(directory);
        input.idle(view::update);
        input.tapDown(view::update);
        input.tapKey(view::update, SceneKeys.R);
        Thread transport = new Thread(() -> future.complete(new ControlMessage.RoomListResult(List.of(b, a), 0, 1)));
        transport.start();
        transport.join();
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertTrue(canvas.lines.indexOf("a") < canvas.lines.indexOf("b"), "Drawing must not consume transport results");
        input.tapEnter(view::update);
        assertEquals(List.of(b), actions.joined);
    }

    @Test
    void resultAfterLeavingCannotReviveBrowser() {
        Directory directory = new Directory(List.of());
        var future = new CompletableFuture<ControlMessage.RoomListResult>();
        directory.scripted.add(future);
        ServerBrowserView view = browser(directory);
        input.tapEscape(view::update);
        future.complete(new ControlMessage.RoomListResult(List.of(room("late")), 0, 1));
        input.tapEnter(view::update);
        assertEquals(1, actions.backs);
        assertTrue(actions.joined.isEmpty());
        assertTrue(actions.created.isEmpty());
    }

    @Test
    void disappearedSelectionMovesToRefreshRatherThanJoiningReplacement() {
        var a = room("a");
        var b = room("b");
        Directory directory = new Directory(List.of(a, b));
        var future = new CompletableFuture<ControlMessage.RoomListResult>();
        directory.scripted.add(CompletableFuture.completedFuture(new ControlMessage.RoomListResult(List.of(a, b), 0, 1)));
        directory.scripted.add(future);
        ServerBrowserView view = browser(directory);
        input.idle(view::update);
        input.tapKey(view::update, SceneKeys.R);
        future.complete(new ControlMessage.RoomListResult(List.of(b), 0, 1));
        input.tapEnter(view::update);
        assertTrue(actions.joined.isEmpty());
        assertTrue(actions.created.isEmpty());
        assertEquals(3, directory.requestedPages.size());
    }

    @Test
    void pagingBoundariesStaySilentAndRefreshFailureIsAnnouncedOnceOnUpdate() {
        ControlMessage.RoomSummary room = new ControlMessage.RoomSummary("room", "Room", "s2", 0, 0,
                "OPEN", 1, 8, "RELAY", true);
        Directory directory = new Directory(List.of(room));
        CompletableFuture<ControlMessage.RoomListResult> refresh = new CompletableFuture<>();
        directory.scripted.add(CompletableFuture.completedFuture(new ControlMessage.RoomListResult(List.of(room), 0, 1)));
        directory.scripted.add(refresh);
        ServerBrowserView view = browser(directory);
        input.tapLeft(view::update);
        input.tapRight(view::update);
        assertTrue(cues.isEmpty(), cues.toString());
        input.tapEnter(view::update);
        assertEquals(List.of(room), actions.joined);
        input.tapDown(view::update); // Create
        input.tapEnter(view::update);
        assertEquals(List.of("RELAY"), actions.created);
        input.tapDown(view::update); // Refresh
        input.tapEnter(view::update);
        assertEquals(List.of(MenuCue.CONFIRM, MenuCue.NAVIGATE, MenuCue.CONFIRM, MenuCue.NAVIGATE, MenuCue.CONFIRM), cues);
        refresh.completeExceptionally(new IllegalStateException("Offline"));
        assertEquals(MenuCue.CONFIRM, cues.getLast(), "A transport callback must not report feedback");
        input.idle(view::update);
        assertEquals(MenuCue.ERROR, cues.getLast());
        int count = cues.size();
        input.idle(view::update);
        assertEquals(count, cues.size(), "One failed request is announced once");
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertTrue(canvas.joined().contains("Refresh failed: Offline"), canvas.joined());
    }

    @Test
    void lostMasterConnectionIsShownAndRefreshIsRefused() {
        Directory directory = new Directory(List.of());
        ServerBrowserView view = browser(directory);
        directory.open = false;
        input.idle(view::update);
        cues.clear();
        input.tapKey(view::update, SceneKeys.R);
        assertEquals(List.of(MenuCue.ERROR), cues);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertTrue(canvas.joined().contains("Master connection lost"), canvas.joined());
    }
}
