package com.openggf.mods.scene.host;

import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModContextTestAccess;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.ScenePeer;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Scene lifecycle integration: no endpoint survives a visit, exit request or callback fault. */
class TestSceneNetworkLifetime {
    @TempDir Path temp;

    private static final class NetworkScene implements ModScene, DebuggableScene {
        final int port;
        final String fail;
        SceneContext context;
        ScenePeer peer;

        NetworkScene(int port, String fail) { this.port = port; this.fail = fail; }

        private void callback(String phase) {
            if (phase.equals(fail)) throw new IllegalStateException("network scene " + phase);
        }

        @Override public void enter(SceneContext ctx) {
            context = ctx;
            peer = ctx.network().host(port);
            callback("enter");
        }
        @Override public void update(SceneContext ctx) { callback("update"); }
        @Override public void draw(SceneContext ctx, SceneCanvas canvas) { callback("draw"); }
        @Override public void exit(SceneContext ctx) {
            assertEquals(ScenePeer.State.CLOSED, peer.state(), "network is retired before creator exit code");
            assertThrows(IllegalStateException.class, () -> ctx.network().host(port));
            callback("exit");
        }
        @Override public boolean debugJump(String command) { callback("debug"); return true; }
    }

    private static int port() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private void open(ModSceneHost host, NetworkScene scene) {
        var boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        host.open(ModContextTestAccess.ownedScene("network-test", () -> scene, boundary),
                new SceneServices(null, null, temp, null, () -> { }, () -> { }), 320, 224);
    }

    private static void closed(NetworkScene scene) {
        assertEquals(ScenePeer.State.CLOSED, scene.peer.state());
        assertFalse(scene.peer.send("stale"));
        assertTrue(scene.peer.poll().isEmpty());
        assertThrows(IllegalStateException.class, () -> scene.context.network().host(scene.port));
        assertThrows(IllegalStateException.class, () -> scene.context.network().connect("localhost", scene.port));
    }

    @Test
    void exitRequestsImmediatelyCancelTheListenerAndCloseCannotReopenIt() throws Exception {
        for (boolean master : new boolean[] {false, true}) {
            ModSceneHost host = new ModSceneHost();
            NetworkScene scene = new NetworkScene(port(), "");
            open(host, scene);
            assertTimeout(Duration.ofMillis(250), () -> {
                host.update(null);
                if (master) scene.context.exitToMasterTitle(); else scene.context.exitToGameTitle();
            });
            closed(scene);
            host.close();
            host.close();
            assertFalse(host.isOpen());
        }
    }

    @Test
    void everyCallbackFaultClosesSceneNetworkingBeforeControlReturns() throws Exception {
        for (String phase : new String[] {"enter", "update", "draw", "debug", "exit"}) {
            ModSceneHost host = new ModSceneHost();
            NetworkScene scene = new NetworkScene(port(), phase);
            if (phase.equals("enter")) {
                assertThrows(ModFaultBoundary.CallbackAborted.class, () -> open(host, scene));
            } else {
                open(host, scene);
                switch (phase) {
                    case "update" -> assertThrows(ModFaultBoundary.CallbackAborted.class, () -> host.update(null));
                    case "draw" -> assertThrows(ModFaultBoundary.CallbackAborted.class, () -> host.draw(null, null));
                    case "debug" -> assertThrows(ModFaultBoundary.CallbackAborted.class, () -> host.debugJump("fail"));
                    case "exit" -> host.close();
                    default -> fail(phase);
                }
            }
            assertFalse(host.isOpen(), phase);
            closed(scene);
            host.cleanup();
        }
    }

    @Test
    void replacingASceneClosesOldNetworkingWhileTheNewContextWorks() throws Exception {
        ModSceneHost host = new ModSceneHost();
        NetworkScene old = new NetworkScene(port(), "");
        NetworkScene next = new NetworkScene(port(), "");
        open(host, old);
        open(host, next);
        closed(old);
        assertEquals(ScenePeer.State.LISTENING, next.peer.state());
        assertSame(next.context.network(), next.context.network());
        host.cleanup();
        closed(next);
    }
}
