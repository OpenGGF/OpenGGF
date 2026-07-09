package com.openggf.game.timeattack.mp;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugColor;
import com.openggf.game.timeattack.TimeAttackTimeFormat;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.net.protocol.ControlMessage;

import java.util.Objects;

/** Stateless overlay renderer for multiplayer countdown, window and standings. */
public final class MultiplayerHudRenderer {
    private static final float SCALE = 0.5f;
    private final SonicConfigurationService config;

    public MultiplayerHudRenderer(SonicConfigurationService config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void render(PixelFontTextRenderer text, MultiplayerHudState state) {
        if (state == null || !state.active()) {
            return;
        }
        text.beginBatch();
        try {
            int screenWidth = config.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
            if (state.remainingCountdownMillis() > 0) {
                String count = Long.toString(Math.max(1,
                        (state.remainingCountdownMillis() + 999) / 1000));
                int x = (screenWidth - text.measureWidth(count, 1f)) / 2;
                text.drawShadowedText(count, x, 84, DebugColor.YELLOW, 1f);
            }
            if (state.remainingWindowMillis() >= 0) {
                long seconds = state.remainingWindowMillis() / 1000;
                drawRight(text, "W %d:%02d".formatted(seconds / 60, seconds % 60),
                        screenWidth, 4, DebugColor.WHITE);
            }
            int y = 12;
            for (ControlMessage.StandingsRow row : state.standings()) {
                drawRight(text, "%d %-8s %s".formatted(row.rank(), row.displayName(),
                                TimeAttackTimeFormat.frames(row.bestTimeFrames())),
                        screenWidth, y, DebugColor.LIGHT_GRAY);
                y += 6;
            }
            if (state.connectionLost()) {
                text.drawShadowedText("CONNECTION LOST", 8, 8, DebugColor.RED, SCALE);
            } else if (state.kickReason() != null) {
                text.drawShadowedText("KICKED: " + state.kickReason(),
                        8, 8, DebugColor.RED, SCALE);
            }
        } finally {
            text.endBatch();
        }
    }

    private static void drawRight(PixelFontTextRenderer text, String line,
                                  int screenWidth, int y, DebugColor color) {
        int x = screenWidth - 4 - text.measureWidth(line, SCALE);
        text.drawShadowedText(line, x, y, color, SCALE);
    }
}
