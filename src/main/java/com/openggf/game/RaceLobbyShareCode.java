package com.openggf.game;

/** Internal engine adapter for showing a manual LAN invite in the race lobby. */
public final class RaceLobbyShareCode {
    private RaceLobbyShareCode() {
    }

    public static void show(MasterTitleScreen title, String inviteTemplate,
                            java.util.function.Consumer<String> clipboardWriter) {
        title.setRaceLobbyShareCode(inviteTemplate, clipboardWriter);
    }
}
