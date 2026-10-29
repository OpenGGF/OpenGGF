package com.openggf.tools;

// Time Attack menu and multiplayer-view pictures through the real Engine startup and the bundled
// mod (TimeAttackVideoCaptureTool), including a test-owned loopback LAN fixture: a real master
// server (racing/server, test classes) and scripted peer RaceSessions from the Time Attack test
// classes, which host, join, chat, ready and list rooms over 127.0.0.1. Run it inside a private
// network namespace (`unshare -rn`, loopback only) so no socket can reach another machine.
// The engine classpath must NOT contain target/test-classes: the engine then resolves Time Attack only
// from the bundled jar through the normal mod pipeline. The fixture's master server and peers load from
// the test classes in a separate class loader and are driven reflectively.
// Usage (cwd = an empty, owned working directory):
//   env -u DISPLAY -u WAYLAND_DISPLAY java -Dopenggf.headless.gl=surfaceless-egl \
//     -cp <target/classes:deps> TimeAttackDesignCaptureTool.java <out> <rom-dir> <logical-width> <test-classes>
// Pictures: <out>/x1/*.png at a 1x window (exact logical pixels; 3x copies are derived later), or
// <out>/window/*.png when an optional fifth argument sets a larger integer window scale.
// SHORTCUT_CAPTURE_ONLY=1 restricts the scenario to four Tab-details pictures before/after readying.
// Origin: 2026-10-28 menu hill climb, based on a513dd5fd; promoted with the final Tab-details repair.
// Inputs: output directory, original ROM directory, logical width and fixture test-classes directory.
import com.openggf.tools.TimeAttackVideoCaptureTool;
import com.openggf.net.identity.PlayerIdentity;
import com.openggf.net.protocol.ChatMode;
import com.openggf.net.protocol.RoomAccessPolicy;
import com.openggf.net.protocol.RoomPassword;
import com.openggf.mods.run.RunHandle;

import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.lwjgl.glfw.GLFW.*;

public class TimeAttackDesignCaptureTool {
    static final String PASSWORD = "seaside hill relay 42";
    static TimeAttackVideoCaptureTool t;
    static Path out;
    static final List<Peer> peers = new ArrayList<>();
    static ClassLoader fixture;
    /** The loopback master's ephemeral port; peers list and create rooms there. */
    static int masterPort;
    static Path work;
    static int shots;
    /** The lobby's logical width, for laying out its page as drawn. */
    static int logicalWidth;
    static long lastFrameNanos;

    public static void main(String[] a) throws Exception {
        if (!"surfaceless-egl".equals(System.getProperty("openggf.headless.gl"))
                || System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null)
            throw new IllegalStateException("Use surfaceless EGL with DISPLAY and WAYLAND_DISPLAY unset");
        out = Path.of(a[0]).toAbsolutePath();
        Path roms = Path.of(a[1]);
        int logical = Integer.parseInt(a[2]);
        logicalWidth = logical;
        fixture = new java.net.URLClassLoader(new java.net.URL[] {Path.of(a[3]).toUri().toURL()},
                TimeAttackDesignCaptureTool.class.getClassLoader());
        if (TimeAttackDesignCaptureTool.class.getClassLoader().getResource("openggf/timeattack/TimeAttackScene.class") != null)
            throw new IllegalStateException("Time Attack classes are on the engine classpath; drop test-classes");
        int scale = a.length > 4 ? Integer.parseInt(a[4]) : 1;
        work = Path.of("").toAbsolutePath().resolve("fixture");
        Files.createDirectories(work);
        if (!isolated()) throw new IllegalStateException("run inside a loopback-only network namespace");
        Master master = new Master(work.resolve("master"));
        masterPort = master.port();
        try (var tool = new TimeAttackVideoCaptureTool(out.resolve(scale == 1 ? "x1" : "window"), roms,
                logical * scale, 224 * scale, logical)) {
            t = tool;
            // Pace at the 60 Hz the room expects: hosts drop a client whose lobby pose stream
            // exceeds 66 frames per second.
            t.beforeFrame(ignored -> {
                long wait = lastFrameNanos + 16_700_000L - System.nanoTime();
                if (wait > 0) {
                    try { Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L)); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                lastFrameNanos = System.nanoTime();
                peers.forEach(Peer::poll);
            });
            run(master, logical);
        } finally {
            peers.forEach(Peer::close);
            master.close();
        }
        System.out.println("captured " + shots + " pictures to " + out);
    }

    /** True when the only interface is loopback (a fresh network namespace). */
    static boolean isolated() throws Exception {
        var names = new ArrayList<String>();
        java.net.NetworkInterface.networkInterfaces().forEach(nic -> names.add(nic.getName()));
        return names.equals(List.of("lo"));
    }

    static void run(Master master, int logical) throws Exception {
        t.frame();
        t.openTimeAttack();
        shot("ta01-name-empty");
        tap(GLFW_KEY_ENTER);
        shot("ta02-name-error");
        type("Tails");
        shot("ta03-name-typed");
        tap(GLFW_KEY_ENTER);
        expectView("menu");
        t.frames(20);
        shot("ta04-menu-solo");
        menuTo("TRACK");
        shot("ta05-menu-track");
        mode("HOST_LAN");
        shot("ta06-menu-host");
        menuTo("POOL");
        shot("ta07-menu-pool");
        tap(GLFW_KEY_ENTER);
        expectView("poolEditor");
        shot("ta08-pools");
        indexTo(field(scene(), "poolEditor"), 1);
        tap(GLFW_KEY_ENTER);
        shot("ta09-pool-new");
        indexTo(field(scene(), "poolEditor"), 1);
        tap(GLFW_KEY_ENTER);
        indexTo(field(scene(), "poolEditor"), 3);
        tap(GLFW_KEY_ENTER);
        shot("ta10-pool-toggled");
        while (!"menu".equals(view())) tap(GLFW_KEY_ESCAPE);
        t.frames(10);
        shot("ta11-menu-after-pools");

        // Settings and their editors.
        menuTo("SETTINGS");
        shot("ta12-menu-settings-row");
        tap(GLFW_KEY_ENTER);
        expectView("settingsView");
        Object settingsView = field(scene(), "settingsView");
        shot("ta13-settings");
        tap(GLFW_KEY_ENTER);
        shot("ta14-settings-name-edit");
        tap(GLFW_KEY_ESCAPE);
        focus(settingsView, "LOOK");
        tap(GLFW_KEY_ENTER);
        t.frames(10);
        Object profile = field(settingsView, "profile");
        shot("ta15-profile");
        focus(profile, "EDIT");
        tap(GLFW_KEY_RIGHT); tap(GLFW_KEY_RIGHT);
        shot("ta16-profile-entry");
        focus(profile, "RED");
        shot("ta17-profile-channel");
        tap(GLFW_KEY_ESCAPE);
        if (field(settingsView, "profile") != null) throw new IllegalStateException("profile did not close");
        focus(settingsView, "HOST_PORT");
        tap(GLFW_KEY_ENTER);
        shot("ta18-settings-port-edit");
        tap(GLFW_KEY_ESCAPE);
        focus(settingsView, "MASTER_URL");
        tap(GLFW_KEY_ENTER);
        type("ws://127.0.0.1:" + master.port() + "/master");
        shot("ta19-settings-master-edit");
        tap(GLFW_KEY_ENTER);
        focus(settingsView, "MASTER_TLS");
        tap(GLFW_KEY_RIGHT);
        shot("ta20-settings-trust-all");
        tap(GLFW_KEY_LEFT);
        focus(settingsView, "RETRY_KEY");
        tap(GLFW_KEY_ENTER);
        shot("ta21-settings-retry-key");
        tap(GLFW_KEY_ESCAPE);
        focus(settingsView, "CHAT_MODE");
        shot("ta22-settings-chat");
        focus(settingsView, "BACK");
        tap(GLFW_KEY_ENTER);
        expectView("menu");
        t.frames(10);

        // Join a private LAN room hosted by a peer.
        String fingerprint = fingerprint("s1");
        Peer knuckles = peer("knuckles", "Knuckles", ChatMode.FULL_CHAT);
        knuckles.hostLan(setup("knuckles"), fingerprint, new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, true),
                RoomPassword.of(PASSWORD));
        waitFor("peer host lobby", () -> "LOBBY".equals(knuckles.state()), 600);
        String invite = knuckles.lanInvite().replace("HOST_IP", "127.0.0.1");
        mode("JOIN_LAN");
        shot("ta23-menu-join");
        menuTo("INVITE");
        tap(GLFW_KEY_ENTER);
        expectView("inviteEditor");
        shot("ta24-invite-entry-empty");
        type(invite);
        shot("ta25-invite-entry-typed");
        tap(GLFW_KEY_ENTER);
        expectView("menu");
        shot("ta26-menu-join-invite-set");
        menuTo("ACTION");
        tap(GLFW_KEY_ENTER);
        t.frame();
        shot("ta27-connecting");
        waitFor("password prompt", () -> "PASSWORD_REQUIRED".equals(engineState()), 900);
        t.frames(4);
        shot("ta28-password-empty");
        type(PASSWORD);
        shot("ta29-password-typed");
        tap(GLFW_KEY_ENTER);
        waitFor("engine lobby", () -> "LOBBY".equals(engineState()), 900);
        t.frames(240);
        Object lobby = field(scene(), "lobby");
        shot("ta30-lobby-member");
        knuckles.chat("Welcome! First to the goal sign wins.");
        t.frames(30);
        knuckles.ready();
        t.frames(30);
        shot("ta31-lobby-chat");
        if (System.getenv("SHORTCUT_CAPTURE_ONLY") != null) {
            focus(lobby, "HISTORY");
            shot("lead-chat-not-ready");
            tap(GLFW_KEY_TAB);
            if (!Boolean.TRUE.equals(call(lobby, "isShowingDetails"))) throw new IllegalStateException("Tab failed before ready");
            shot("lead-details-not-ready");
            tap(GLFW_KEY_ESCAPE);
            focus(lobby, "READY");
            tap(GLFW_KEY_ENTER);
            Object race = call(call(session(), "coordinator"), "session");
            waitFor("local ready", () -> {
                try { return Boolean.TRUE.equals(call(race, "isLocalReady")); }
                catch (Exception failure) { throw new IllegalStateException("Cannot inspect the local ready state", failure); }
            }, 600);
            focus(lobby, "HISTORY");
            shot("lead-chat-ready");
            tap(GLFW_KEY_TAB);
            if (!Boolean.TRUE.equals(call(lobby, "isShowingDetails"))
                    || !Boolean.TRUE.equals(call(session(), "lobbyMenuShown")))
                throw new IllegalStateException("Tab must open details and keep the ready lobby menu shown");
            shot("lead-details-ready");
            return;
        }

        Object ctx = field(scene(), "context");
        long ticks0 = ((Number) field(ctx, "ticks")).longValue();
        t.frame();
        System.out.println("probe ticks per frame: " + (((Number) field(ctx, "ticks")).longValue() - ticks0)
                + " mode=" + t.engine().getCurrentGameMode());
        System.out.println("probe before DOWN: " + field(lobby, "focus"));
        t.key(GLFW_KEY_DOWN, true); t.frame();
        System.out.println("probe after 1 frame held: " + field(lobby, "focus"));
        t.key(GLFW_KEY_DOWN, false); t.frame();
        System.out.println("probe after release: " + field(lobby, "focus"));
        focus(lobby, "QUICK_CHAT");
        shot("ta32-lobby-quickchat-row");
        tap(GLFW_KEY_ENTER);
        t.frames(6);
        shot("ta33-quickchat");
        Object chooser = field(lobby, "quickChat");
        System.out.println("quick chat open=" + call(chooser, "isOpen") + " state=" + engineState()
                + " menuShown=" + call(session(), "lobbyMenuShown") + " runActive=" + call(session(), "lobbyRunActive"));
        t.key(GLFW_KEY_ESCAPE, true); t.frame();
        System.out.println("probe esc held: open=" + call(chooser, "isOpen") + " state=" + engineState());
        t.key(GLFW_KEY_ESCAPE, false); t.frame();
        System.out.println("probe esc released: open=" + call(chooser, "isOpen") + " state=" + engineState());
        for (int i = 0; i < 4; i++) { t.frame(); System.out.println("probe +" + i + " state=" + engineState()); }
        t.frames(6);
        focus(lobby, "INVITE");
        shot("ta36-lobby-invite-row");
        tap(GLFW_KEY_ENTER);
        t.frames(10);
        shot("ta37-invite-member");
        closeInvites();
        t.frames(10);
        focus(lobby, "PROFILE");
        tap(GLFW_KEY_ENTER);
        t.frames(10);
        shot("ta38-lobby-profile");
        tap(GLFW_KEY_ESCAPE);
        t.frames(10);
        focus(lobby, "PLAYERS");
        shot("ta39-lobby-players-focus");
        focus(lobby, "HISTORY");
        tap(GLFW_KEY_ENTER);
        t.frames(6);
        shot("ta40-lobby-details");
        tap(GLFW_KEY_ESCAPE);
        t.frames(6);
        focus(lobby, "READY");
        tap(GLFW_KEY_ENTER);
        t.frames(30);
        shot("ta34-lobby-ready");
        tap(GLFW_KEY_ENTER);
        t.frames(10);
        shot("ta35-lobby-menu-hidden");
        tap(GLFW_KEY_ENTER);
        t.frames(10);
        // Back leaves the room from the lobby's main page (Enter is Start once ready).
        tap(GLFW_KEY_ESCAPE);
        waitFor("leave room", () -> "IDLE".equals(engineState()), 600);
        t.frames(90);
        shot("ta41-menu-after-leave");

        // Host a private room and let a peer join it.
        mode("HOST_LAN");
        menuTo("ACTION");
        shot("ta42-menu-create-row");
        tap(GLFW_KEY_ENTER);
        t.frames(4);
        expectView("accessView");
        Object access = field(scene(), "accessView");
        shot("ta43-access-public");
        tap(GLFW_KEY_RIGHT);
        shot("ta44-access-private-unset");
        focus(access, "PASSWORD");
        tap(GLFW_KEY_ENTER);
        type(PASSWORD);
        tap(GLFW_KEY_ENTER);
        shot("ta45-access-private-ready");
        focus(access, "CREATE");
        shot("ta46-access-create-focus");
        tap(GLFW_KEY_ENTER);
        waitFor("host lobby", () -> "LOBBY".equals(engineState()), 900);
        t.frames(240);
        lobby = field(scene(), "lobby");
        shot("ta47-lobby-host");
        String hostInvite = ((String) call(session(), "lanInvite")).replace("HOST_IP", "127.0.0.1");
        Peer amy = peer("amy", "Amy", ChatMode.FULL_CHAT);
        amy.joinLan(hostInvite, "sonic", fingerprint);
        waitFor("peer password prompt", () -> "PASSWORD_REQUIRED".equals(amy.state()), 900);
        amy.submitPassword(RoomPassword.of(PASSWORD));
        waitFor("peer in host lobby", () -> "LOBBY".equals(amy.state()), 900);
        t.frames(30);
        amy.chat("Ready when you are!");
        amy.ready();
        t.frames(60);
        shot("ta48-lobby-host-two");
        focus(lobby, "INVITE");
        shot("ta49-lobby-host-invite-row");
        tap(GLFW_KEY_ENTER);
        t.frames(10);
        shot("ta50-invite-host-masked");
        tap(GLFW_KEY_ENTER);
        t.frames(10);
        shot("ta51-invite-host-revealed");
        closeInvites();
        t.frames(10);
        // Disabled Start traversal (G4): Down from Ready up with a player still to ready; each step pictured.
        Peer tails = peer("tails", "Tails", ChatMode.FULL_CHAT);
        tails.joinLan(hostInvite, "tails", fingerprint);
        waitFor("tails password prompt", () -> "PASSWORD_REQUIRED".equals(tails.state()), 900);
        tails.submitPassword(RoomPassword.of(PASSWORD));
        waitFor("tails in host lobby", () -> "LOBBY".equals(tails.state()), 900);
        t.frames(30);
        focus(lobby, "READY");
        shot("ta62-traverse-k0");
        for (int step = 1; step <= 3; step++) {
            tap(GLFW_KEY_DOWN);
            t.frames(2);
            shot("ta62-traverse-k" + step + "-" + field(lobby, "focus"));
        }
        // Crowded room (G3): eight players with long names, a chat history and the host's notice.
        String[][] crowd = {{"knuckles2", "Knuckles the Echidna of Angel Island"}, {"cream", "Cream the Rabbit and Cheese Chao"},
                {"silver", "Silver from two hundred years ahead"}, {"blaze", "Blaze the Cat of the Sol Dimension"},
                {"espio", "Espio the Chameleon of Team Chaotix"}};
        // The host allows four connections per address (JdkHostLimits), so each crowd peer dials its
        // own private address on this namespace's loopback (ta_capture.py adds 10.77.0.2-9 to lo).
        int address = 2;
        for (String[] who : crowd) {
            Peer peer = peer(who[0], who[1], ChatMode.FULL_CHAT);
            peer.joinLan(hostInvite.replace("127.0.0.1", "10.77.0." + address++), "sonic", fingerprint);
            waitFor(who[0] + " password prompt", () -> "PASSWORD_REQUIRED".equals(peer.state()), 900);
            peer.submitPassword(RoomPassword.of(PASSWORD));
            waitFor(who[0] + " in host lobby", () -> "LOBBY".equals(peer.state()), 900);
            t.frames(10);
            peer.chat("Hi from " + who[1] + "! Ready in a moment, just picking a character.");
            t.frames(10);
        }
        tails.chat("Tails here: let's race Green Hill first, then vote on the next track together?");
        t.frames(60);
        focus(lobby, "READY");
        shot("ta63-lobby-crowded");
        crowdedHostNotice(lobby);
        focus(lobby, "PLAYERS");
        tap(GLFW_KEY_RIGHT);
        t.frames(4);
        shot("ta64-lobby-crowded-players-page-2");
        // Room details from the chat card (opening at the newest message), then Up to the players. Everyone is
        // ready, so Enter is the lobby's Start (it hides the menu); the card's own cap is A (Space).
        focus(lobby, "HISTORY");
        tap(GLFW_KEY_SPACE);
        t.frames(6);
        shot("ta65-crowded-room-details");
        for (int i = 0; i < 12; i++) { tap(GLFW_KEY_UP); t.frames(2); }
        t.frames(4);
        shot("ta66-crowded-room-details-history");
        tap(GLFW_KEY_ESCAPE);
        t.frames(6);
        focus(lobby, "READY");
        t.frames(4);
        // Leave only Amy, as before, so the later states are unchanged.
        for (Peer extra : new ArrayList<>(peers)) {
            if (extra == amy || extra == knuckles) continue;
            extra.close();
            peers.remove(extra);
        }
        t.frames(30);
        amy.close();
        peers.remove(amy);
        t.frames(30);
        // Back leaves from the lobby's main page; Enter would toggle the menu, because the crowd
        // made the default ready rule ready everyone (six or more players) and readiness is kept.
        tap(GLFW_KEY_ESCAPE);
        waitFor("leave hosted room", () -> "IDLE".equals(engineState()), 600);
        t.frames(30);

        // Browse the loopback master: empty, then with two listed rooms.
        mode("BROWSE");
        shot("ta52-menu-browse");
        menuTo("ACTION");
        tap(GLFW_KEY_ENTER);
        waitFor("browser", () -> "BROWSING".equals(engineState()), 900);
        t.frames(60);
        shot("ta53-browser-empty");
        Peer sonic = peer("sonic", "Sonic", ChatMode.QUICK_CHAT);
        Peer rouge = peer("rouge", "Rouge the Bat", ChatMode.FULL_CHAT);
        for (String id : List.of("sonic", "rouge")) {
            master.establish(PlayerIdentity.loadOrCreate(work.resolve(id + "-identity")).fingerprint());
        }
        sonic.browse(setup("sonic"), fingerprint);
        waitFor("sonic browsing", () -> "BROWSING".equals(sonic.state()), 900);
        sonic.createMasterRoom("RELAY");
        waitFor("sonic room", () -> "LOBBY".equals(sonic.state()), 900);
        rouge.browse(setup("rouge"), fingerprint);
        waitFor("rouge browsing", () -> "BROWSING".equals(rouge.state()), 900);
        rouge.createMasterRoom("DIRECT", new RoomAccessPolicy(RoomAccessPolicy.Mode.PRIVATE, false),
                RoomPassword.of(PASSWORD));
        waitFor("rouge room", () -> "LOBBY".equals(rouge.state()), 900);
        t.frames(200);
        Object browser = field(scene(), "browser");
        focus(browser, "ROOMS");
        shot("ta54-browser-rooms");
        tap(GLFW_KEY_DOWN);
        shot("ta55-browser-room2");
        focus(browser, "CREATE");
        shot("ta56-browser-create-row");
        focus(browser, "CODE");
        tap(GLFW_KEY_ENTER);
        t.frames(4);
        shot("ta57-join-code");
        tap(GLFW_KEY_ESCAPE);
        t.frames(4);
        tap(GLFW_KEY_ESCAPE);
        waitFor("leave browser", () -> "IDLE".equals(engineState()), 600);
        t.frames(10);
    }

    static void closeInvites() throws Exception {
        for (int i = 0; i < 6; i++) {
            Object invites = field(scene(), "invites");
            if (invites == null || !(Boolean) call(invites, "isOpen")) return;
            tap(GLFW_KEY_ESCAPE);
        }
        throw new IllegalStateException("invite view did not close");
    }

    /** The scene's front view: an open overlay field, else "menu". */
    static String view() throws Exception {
        Object scene = scene();
        if (scene == null) return "closed";
        for (String name : List.of("accessView", "codeEditor", "passwordEditor", "namePrompt", "settingsView",
                "poolEditor", "inviteEditor")) if (field(scene, name) != null) return name;
        return "menu";
    }

    static void expectView(String expected) throws Exception {
        t.frames(2);
        if (!expected.equals(view())) {
            shot("FAILED-view-" + expected);
            throw new IllegalStateException("expected " + expected + " but saw " + view());
        }
    }

    static Object scene() throws Exception { return t.openScene(); }

    static Object field(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(name);
    }

    /** Main-menu row index for a field row name, INVITE, ACTION or SETTINGS. */
    static int menuIndex(String target) throws Exception {
        Object state = field(scene(), "state");
        List<?> rows = (List<?>) call(state, "visibleRows");
        boolean joining = "JOIN_LAN".equals(call(state, "mode").toString());
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).toString().equals(target)) return i;
        int go = rows.size() + (joining ? 1 : 0);
        return switch (target) {
            case "INVITE" -> rows.size();
            case "ACTION" -> go;
            case "SETTINGS" -> go + 1;
            default -> throw new IllegalArgumentException(target);
        };
    }

    static void menuTo(String target) throws Exception {
        expectView("menu");
        int index = menuIndex(target);
        for (int i = 0; i < 30; i++) {
            int focus = (Integer) field(scene(), "focus");
            if (focus == index) return;
            tap(focus < index ? GLFW_KEY_DOWN : GLFW_KEY_UP);
        }
        throw new IllegalStateException("menu focus did not reach " + target);
    }

    static void mode(String target) throws Exception {
        menuTo("MODE");
        for (int i = 0; i < 8; i++) {
            if (call(field(scene(), "state"), "mode").toString().equals(target)) return;
            tap(GLFW_KEY_RIGHT);
        }
        throw new IllegalStateException("mode did not reach " + target);
    }

    /** Moves a view's enum focus to {@code target} by ordinal (views list choices in enum order). */
    /**
     * A host notice in the crowded room through RaceLobbyView's own START owner: the host makes
     * ready required, cancels their own ready and presses Start round, which refuses with "Waiting
     * for every player to be ready". Asserted in the view and on its drawn page, pictured before any
     * further input clears it, then the rule and ready state are restored for the later states.
     * Everyone is ready, so Enter is the lobby's Start (it hides the menu); rows take A (Space).
     */
    static void crowdedHostNotice(Object lobby) throws Exception {
        String expected = "Waiting for every player to be ready";
        Object race = call(field(lobby, "coordinator"), "session");
        focus(lobby, "RULE");
        tap(GLFW_KEY_RIGHT); // DEFAULT_BY_SIZE -> REQUIRED
        t.frames(10);
        if (!"REQUIRED".equals(String.valueOf(call(race, "readyRule"))))
            throw new IllegalStateException("ready rule did not become REQUIRED: " + call(race, "readyRule"));
        focus(lobby, "READY");
        tap(GLFW_KEY_SPACE); // Cancel ready
        t.frames(10);
        if (Boolean.TRUE.equals(call(race, "isLocalReady"))) throw new IllegalStateException("host is still ready");
        focus(lobby, "START");
        tap(GLFW_KEY_SPACE); // Start round (waiting)
        t.frames(2);
        if (!expected.equals(field(lobby, "notice")))
            throw new IllegalStateException("host notice is " + field(lobby, "notice"));
        Method screen = lobby.getClass().getDeclaredMethod("screen", int.class, int.class);
        screen.setAccessible(true);
        Object drawn = screen.invoke(lobby, logicalWidth, 224);
        String page = String.valueOf(drawn.getClass().getMethod("plain").invoke(drawn));
        // The menu card's notes, rejoined: the notice wraps by measured width but must be whole.
        List<?> cards = (List<?>) drawn.getClass().getMethod("cards").invoke(drawn);
        Object menu = cards.get(cards.size() - 1);
        List<String> notes = new ArrayList<>();
        for (Object row : (List<?>) menu.getClass().getMethod("rows").invoke(menu)) {
            if (row.getClass().getSimpleName().equals("Note")) notes.add(String.valueOf(call(row, "text")));
        }
        if (!String.join(" ", notes).contains(expected) || notes.stream().anyMatch(note -> note.endsWith("\u2026")))
            throw new IllegalStateException("the notice is not drawn whole in the menu card: " + notes);
        System.out.println("crowded host notice drawn: " + page);
        shot("ta63a-lobby-crowded-notice");
        focus(lobby, "READY");
        tap(GLFW_KEY_SPACE); // Ready up again, while ready is still required
        t.frames(10);
        focus(lobby, "RULE");
        tap(GLFW_KEY_LEFT); // REQUIRED -> DEFAULT_BY_SIZE
        t.frames(10);
        if (!"DEFAULT_BY_SIZE".equals(String.valueOf(call(race, "readyRule"))) || !Boolean.TRUE.equals(call(race, "isLocalReady")))
            throw new IllegalStateException("crowded room not restored: " + call(race, "readyRule") + " ready=" + call(race, "isLocalReady"));
    }

    static void focus(Object view, String target) throws Exception {
        for (int i = 0; i < 30; i++) {
            Enum<?> current = (Enum<?>) field(view, "focus");
            if (current.name().equals(target)) return;
            if (i > 3) System.out.println("focus " + current + " -> " + target + " state=" + engineState()
                    + " status=" + field(scene(), "status") + " knuckles=" + (peers.isEmpty() ? "" : peers.get(0).state()
                    + "/" + peers.get(0).status()));
            @SuppressWarnings({"unchecked", "rawtypes"})
            Enum<?> wanted = Enum.valueOf((Class) current.getDeclaringClass(), target);
            tap(current.ordinal() < wanted.ordinal() ? GLFW_KEY_DOWN : GLFW_KEY_UP);
        }
        shot("FAILED-focus-" + target);
        throw new IllegalStateException("focus did not reach " + target);
    }

    static void indexTo(Object view, int target) throws Exception {
        for (int i = 0; i < 40; i++) {
            int current = (Integer) field(view, "focus");
            if (current == target) return;
            tap(current < target ? GLFW_KEY_DOWN : GLFW_KEY_UP);
        }
        throw new IllegalStateException("index focus did not reach " + target);
    }

    static Object setup(String who) throws Exception {
        return construct("openggf.timeattack.mp.RaceSession$RoundSetup", 7, "s1", 0, 0, "sonic", "OPEN", null, 300);
    }

    static Peer peer(String id, String name, ChatMode chat) throws Exception {
        Object settings = construct("openggf.timeattack.TimeAttackSettings", 0);
        try (ServerSocket socket = new ServerSocket(0)) { invoke(settings, "setHostPort", socket.getLocalPort()); }
        invoke(settings, "setDisplayName", name);
        invoke(settings, "setChatMode", chat);
        invoke(settings, "setMasterUrl", "ws://127.0.0.1:" + masterPort + "/master");
        Path identity = work.resolve(id + "-identity");
        Object ghosts = construct("openggf.timeattack.GhostStore", 1, work.resolve(id + "-ghosts"));
        Class<?> launcherType = fixture.loadClass("openggf.timeattack.mp.RaceSession$RunLauncher");
        Object launcher = java.lang.reflect.Proxy.newProxyInstance(fixture, new Class<?>[] {launcherType},
                (proxy, method, args) -> method.getName().equals("launch") ? new RunHandle() {
                    @Override public void retry() { }
                    @Override public void leave() { }
                    @Override public void spectate(boolean on, int dx, int dy) { }
                    @Override public boolean isActive() { return true; }
                } : method.getName().equals("toString") ? "fixture launcher" : null);
        java.util.function.Supplier<Object> runtimes = () -> {
            try {
                return construct("openggf.timeattack.TimeAttackRuntime", 3, ghosts, identity,
                        (java.util.function.BooleanSupplier) () -> false);
            } catch (Exception e) { throw new RuntimeException(e); }
        };
        Object session = construct("openggf.timeattack.mp.RaceSession", 7, identity, ghosts, settings,
                construct("openggf.timeattack.MemoryModStorage", 0), launcher, runtimes,
                (java.util.function.LongSupplier) System::currentTimeMillis);
        Peer peer = new Peer(session);
        peers.add(peer);
        return peer;
    }

    /** A scripted RaceSession from the fixture loader. */
    record Peer(Object session) {
        String state() { return call0("state").toString(); }
        String status() { return String.valueOf(call0("status")); }
        void poll() { call0("poll"); }
        void close() { call0("close"); }
        String lanInvite() { return (String) call0("lanInvite"); }
        void hostLan(Object setup, String fp, RoomAccessPolicy policy, RoomPassword password) throws Exception {
            invoke(session, "hostLan", setup, fp, policy, password);
        }
        void joinLan(String invite, String character, String fp) throws Exception {
            invoke(session, "joinLan", invite, character, fp);
        }
        void submitPassword(RoomPassword password) throws Exception { invoke(session, "submitPassword", password); }
        void browse(Object setup, String fp) throws Exception { invoke(session, "browse", setup, fp); }
        void createMasterRoom(String routing) throws Exception { invoke(session, "createMasterRoom", routing); }
        void createMasterRoom(String routing, RoomAccessPolicy policy, RoomPassword password) throws Exception {
            invoke(session, "createMasterRoom", routing, policy, password);
        }
        void chat(String text) throws Exception { invoke(call0("coordinator"), "sendChat", text); }
        void ready() throws Exception { invoke(call0("coordinator"), "setReady", true); }
        private Object call0(String name) {
            try { return invoke(session, name); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    /** The racing master server (test configuration) from the fixture loader. */
    static final class Master {
        private final Object server;
        Master(Path directory) throws Exception {
            Object config = fixture.loadClass("openggf.racing.server.master.TestMasterServer")
                    .getMethod("testConfig").invoke(null);
            Class<?> type = fixture.loadClass("openggf.racing.server.master.MasterServer");
            Method start = null;
            for (Method m : type.getMethods())
                if (m.getName().equals("start") && m.getParameterCount() == 2) start = m;
            server = start.invoke(null, config, directory);
        }
        int port() throws Exception { return (Integer) invoke(server, "port"); }
        void establish(String fingerprint) throws Exception {
            CountDownLatch done = new CountDownLatch(1);
            invoke(server, "execute", (Runnable) () -> {
                try { invoke(server, "establishForTest", fingerprint); }
                catch (Exception e) { throw new RuntimeException(e); }
                finally { done.countDown(); }
            });
            if (!done.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("establish");
        }
        void close() throws Exception { invoke(server, "close"); }
    }

    static Object construct(String type, int arity, Object... args) throws Exception {
        for (var constructor : fixture.loadClass(type).getDeclaredConstructors()) {
            if (constructor.getParameterCount() == arity) {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            }
        }
        throw new NoSuchMethodException(type + "/" + arity);
    }

    /** Calls the public method {@code name} whose arity matches (overloads differ by arity here). */
    static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                try { return method.invoke(target, args); }
                catch (java.lang.reflect.InvocationTargetException e) {
                    if (e.getCause() instanceof Exception ex) throw ex;
                    throw e;
                }
            }
        }
        throw new NoSuchMethodException(name + "/" + args.length);
    }

    static String fingerprint(String game) throws Exception {
        Object scene = t.openScene();
        var field = scene.getClass().getDeclaredField("context");
        field.setAccessible(true);
        Object context = field.get(scene);
        Object gameplay = call(context, "gameplay");
        Method method = gameplay.getClass().getMethod("determinismFingerprint", String.class);
        method.setAccessible(true);
        return ((java.util.Optional<?>) method.invoke(gameplay, game)).map(Object::toString)
                .orElseThrow(() -> new IllegalStateException("no fingerprint for " + game));
    }

    static Object session() throws Exception { return field(scene(), "session"); }

    static String engineState() {
        try { return call(session(), "state").toString(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    static Object call(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException next) { }
        }
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    static void waitFor(String what, BooleanSupplier condition, int frames) throws Exception {
        for (int i = 0; i < frames; i++) {
            if (condition.getAsBoolean()) return;
            t.frame();
            Thread.sleep(5);
        }
        shot("FAILED-" + what.replace(' ', '-'));
        throw new IllegalStateException("timed out waiting for " + what + " (engine " + engineState() + ")");
    }

    static void tap(int key) throws Exception {
        t.tap(key);
        t.frames(2);
    }

    static void type(String text) throws Exception {
        Method append = com.openggf.control.InputHandler.class.getDeclaredMethod("appendMenuCodepoint", int.class);
        append.setAccessible(true);
        for (int cp : text.codePoints().toArray()) { append.invoke(t.input(), cp); t.frame(); }
        t.frames(4);
    }

    static void shot(String name) throws Exception {
        t.frames(2);
        if (System.getenv("SHORTCUT_CAPTURE_ONLY") != null && !name.startsWith("lead-")) return;
        t.png(name + ".png");
        shots++;
        System.out.println("shot " + name);
    }
}
