package com.openggf.mods.scene.host;

import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.tools.PresentationOffscreenTestContext;
import openggf.timeattack.mp.InviteCodeView;
import openggf.timeattack.mp.RoomAccessView;
import openggf.timeattack.ui.FakeViewInput;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

@Isolated
class TestPrivateLobbyScreenRendering {
    @Test
    void actualScenePixelsHideCredentialsUntilRevealAndRestoreTheMask() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(800, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            SceneRenderer renderer = new SceneRenderer();
            try {
                renderer.init();
                SceneFontAtlas font = SceneFontAtlas.load();
                assertNotNull(font, "The production menu font must be loaded");
                for (int width : new int[]{320, 400, 800}) {
                    var first = invitation("7K3 M9X");
                    var second = invitation("2FT H8N");
                    RgbaImage mask = render(renderer, font, width, first::draw);
                    RgbaImage otherMask = render(renderer, font, width, second::draw);
                    assertArrayEquals(mask.pixels(), otherMask.pixels(), "Masked pixels cannot depend on the code");
                    long detailPixels = Arrays.stream(mask.pixels()).filter(pixel -> (pixel & 0xFFFFFF) != 0x101830).count();
                    assertTrue(detailPixels > 1000, "A blank scene is not masking evidence");

                    FakeViewInput input = new FakeViewInput();
                    input.tapEnter(keys -> first.update(keys, SceneMouse.none()));
                    input.tapEnter(keys -> second.update(keys, SceneMouse.none()));
                    RgbaImage revealed = render(renderer, font, width, first::draw);
                    RgbaImage otherReveal = render(renderer, font, width, second::draw);
                    assertTrue(codePixelsDiffer(revealed, otherReveal), "Distinct revealed code glyphs must reach the framebuffer");
                    first.hide();
                    second.hide();
                    RgbaImage hidden = render(renderer, font, width, first::draw);
                    RgbaImage neverFetched = render(renderer, font, width, invitation(null)::draw);
                    assertArrayEquals(neverFetched.pixels(), hidden.pixels(), "Hiding discards the code and restores the unfetched picture");
                    assertArrayEquals(hidden.pixels(), render(renderer, font, width, second::draw).pixels(),
                            "A hidden frame cannot retain code-dependent pixels");
                    export(mask, "invite-masked-" + width);
                    export(revealed, "invite-revealed-" + width);

                    RgbaImage passwordA = privateCreation(renderer, font, width, "correct horse battery");
                    RgbaImage passwordB = privateCreation(renderer, font, width, "another private password");
                    assertArrayEquals(passwordA.pixels(), passwordB.pixels(), "Creation pixels reveal neither password nor length");
                    export(passwordA, "create-private-" + width);
                    assertEquals(GL_NO_ERROR, glGetError(), "Screen rendering must not leave a graphics error");
                }
            } finally {
                renderer.cleanup();
            }
        }
    }

    @Test
    void actualLobbyAndFullTextPixelsSupportMultipleReadablePages() throws Exception {
        try (var context = PresentationOffscreenTestContext.openConfigured(1280, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41)) {
            SceneRenderer renderer = new SceneRenderer();
            try {
                renderer.init();
                SceneFontAtlas font = SceneFontAtlas.load();
                assertNotNull(font);
                var session = new com.openggf.net.client.ClientRaceSession(() -> 0);
                var round = new com.openggf.net.protocol.ControlMessage.RoundConfig("s2", 0, 0, 300, "OPEN", null);
                session.applyJoin(new com.openggf.net.protocol.ControlMessage.JoinAccepted("t", 0,
                        new com.openggf.net.protocol.ControlMessage.RoomDescriptor("部屋のレーサー", "s2", 0, 0,
                                "OPEN", null, 8, false, "FULL_CHAT"),
                        new com.openggf.net.protocol.ControlMessage.RoundSnapshot("LOBBY", null, 0, 0, java.util.List.of())));
                session.onControl(new com.openggf.net.protocol.ControlMessage.RoomState(java.util.List.of(
                        new com.openggf.net.protocol.ControlMessage.PlayerInfo(0, "abcdef", "日本語レーサー", "sonic"))));
                for (int i = 0; i < 30; i++) session.onControl(new com.openggf.net.protocol.ControlMessage.ChatBroadcast(
                        0, "日本語レーサー", "Message " + i + " Unicode e\u0301🚀 complete history"));
                openggf.timeattack.mp.RaceTransport transport = new openggf.timeattack.mp.RaceTransport() {
                    @Override public java.util.List<com.openggf.net.client.RaceClient.InboundEvent> drainInbound() {
                        return java.util.List.of();
                    }
                    @Override public void sendControl(com.openggf.net.protocol.ControlMessage message) {
                        fail("Reading must not send " + message);
                    }
                    @Override public void sendBinary(byte[] bytes) { fail("Reading must not send"); }
                    @Override public int playerSlot() { return 0; }
                    @Override public boolean isOpen() { return true; }
                    @Override public void close() { }
                };
                for (int width : new int[] {320, 400, 800, 1280}) {
                    var lobby = new openggf.timeattack.mp.RaceLobbyView(
                            new openggf.timeattack.mp.MultiplayerRaceCoordinator(transport, session), true,
                            round, "HOST_IP:27888#PRIVATE_INVITE", () -> fail("Reading must not leave"), cue -> { });
                    RgbaImage normal = render(renderer, font, width, lobby::draw);
                    FakeViewInput input = new FakeViewInput();
                    assertFalse(session.isLocalReady());
                    input.tapKey(lobby::update, com.openggf.mods.scene.SceneKeys.F1);
                    assertFalse(lobby.isOnMainPage(), "F1 opens the advertised full-text reader");
                    RgbaImage first = render(renderer, font, width, lobby::draw);
                    input.tapRight(lobby::update);
                    RgbaImage second = render(renderer, font, width, lobby::draw);
                    assertFalse(Arrays.equals(normal.pixels(), first.pixels()), "F1 opens a distinct reader");
                    assertFalse(Arrays.equals(first.pixels(), second.pixels()), "Right reaches new full-text pixels");
                    input.tapLeft(lobby::update);
                    assertArrayEquals(first.pixels(), render(renderer, font, width, lobby::draw).pixels(),
                            "Left returns to the first complete text page");
                    input.tapEnter(lobby::update);
                    assertTrue(lobby.isOnMainPage(), "Confirm closes the reader");
                    assertFalse(session.isLocalReady(), "Closing must not activate Ready up");
                    assertArrayEquals(normal.pixels(), render(renderer, font, width, lobby::draw).pixels(),
                            "Reading and closing leave the lobby unchanged");
                    assertTrue(Arrays.stream(first.pixels()).filter(pixel -> (pixel & 0xFFFFFF) != 0x101830).count() > 1000);
                    export(normal, "lobby-normal-" + width);
                    export(first, "lobby-full-text-first-" + width);
                    export(second, "lobby-full-text-next-" + width);
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            } finally {
                renderer.cleanup();
            }
        }
    }

    private static InviteCodeView invitation(String code) {
        return new InviteCodeView(() -> new InviteCodeView.Snapshot(code, 600_000, 1,
                true, true, true, ""), action -> {}, cue -> {}, () -> 0);
    }

    private static RgbaImage privateCreation(SceneRenderer renderer, SceneFontAtlas font,
                                              int width, String password) throws Exception {
        String[] submitted = new String[1];
        RoomAccessView view = new RoomAccessView(new RoomAccessView.Actions() {
            @Override public void create(boolean privateRoom, boolean membersCanInvite, String value) {
                assertTrue(privateRoom);
                assertTrue(membersCanInvite);
                submitted[0] = value;
            }
            @Override public void cancelled() { fail("Creation was not cancelled"); }
        }, cue -> {});
        FakeViewInput input = new FakeViewInput();
        input.tapRight(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        input.commit(view::update, password);
        input.tapEnter(view::update);
        RgbaImage result = render(renderer, font, width, view::draw);
        input.tapDown(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update);
        assertEquals(password, submitted[0], "The hidden value must still be submitted exactly");
        view.close();
        return result;
    }

    private static RgbaImage render(SceneRenderer renderer, SceneFontAtlas font, int width,
                                    Consumer<RecordingCanvas> draw) {
        glViewport(0, 0, width, 224);
        glDisable(GL_DITHER);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        RecordingCanvas canvas = new RecordingCanvas(width, 224, font);
        draw.accept(canvas);
        float[] projection = new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]);
        renderer.render(canvas.ops(), projection, width, 224, new int[]{0, 0, width, 224});
        return ScreenshotCapture.captureFramebuffer(width, 224);
    }

    private static boolean codePixelsDiffer(RgbaImage first, RgbaImage second) {
        for (int y = 41; y < 70; y++) {
            for (int x = 24; x < 200; x++) {
                if (first.pixels()[y * first.width() + x] != second.pixels()[y * second.width() + x]) return true;
            }
        }
        return false;
    }

    private static void export(RgbaImage image, String name) throws Exception {
        String configured = System.getenv("OPENGGF_PRIVATE_LOBBY_SCREENS");
        if (configured == null || configured.isBlank()) return;
        Path directory = Path.of(configured);
        Files.createDirectories(directory);
        ScreenshotCapture.savePNG(image, directory.resolve(name + ".png"));
    }
}
