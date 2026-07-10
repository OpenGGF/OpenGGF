package com.openggf.game.timeattack.mp;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugColor;
import com.openggf.game.timeattack.TimeAttackTrackCatalog;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.net.protocol.ControlMessage;
import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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
                drawRight(text, HudTextLayout.standingsLine(row, state.characterPolicy()),
                        screenWidth, y, DebugColor.LIGHT_GRAY);
                y += 6;
            }
            if ("ROUND_END".equals(state.phase())) {
                drawBlock(text, HudTextLayout.podiumLines(state.podiumRows(),
                        state.localRank(), state.standings(), -1,
                        state.characterPolicy()), 72, 60, DebugColor.YELLOW);
            } else if ("VOTE".equals(state.phase())) {
                drawBlock(text, HudTextLayout.voteLines(state.voteOptions(),
                        state.voteCounts(), state.voteRemainingMillis(),
                        MultiplayerHudRenderer::trackLabel), 56, 60, DebugColor.YELLOW);
            } else if ("LOBBY".equals(state.phase())
                    && state.voteResultTrackKey() != null) {
                text.drawShadowedText(HudTextLayout.voteResultLine(
                                state.voteResultTrackKey(), MultiplayerHudRenderer::trackLabel),
                        8, 24, DebugColor.YELLOW, SCALE);
            }
            drawMinimap(text, state);
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

    private static void drawBlock(PixelFontTextRenderer text, java.util.List<String> lines,
                                  int x, int y, DebugColor color) {
        for (String line : lines) {
            text.drawShadowedText(line, x, y, color, SCALE);
            y += 8;
        }
    }

    private static String trackLabel(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", -1);
        if (parts.length != 3) {
            return key;
        }
        try {
            int zone = Integer.parseInt(parts[1]);
            int act = Integer.parseInt(parts[2]);
            return TimeAttackTrackCatalog.tracksFor(parts[0]).stream()
                    .filter(track -> track.zone() == zone && track.act() == act)
                    .map(TimeAttackTrackCatalog.Track::label).findFirst().orElse(key);
        } catch (NumberFormatException ignored) {
            return key;
        }
    }

    private void drawMinimap(PixelFontTextRenderer text, MultiplayerHudState state) {
        if (!"RUNNING".equals(state.phase())
                || !config.getBoolean(SonicConfiguration.TIME_ATTACK_HUD_MINIMAP)) {
            return;
        }
        var profile = LiveLevelProfileFactory.fromLoadedLevelOrNull();
        var sprites = GameServices.spritesOrNull();
        if (profile == null || sprites == null) {
            return;
        }
        java.util.List<MinimapLayout.Dot> dots = new java.util.ArrayList<>();
        for (var player : state.farPlayers()) {
            char glyph = player.status() == -1 ? 'o'
                    : MinimapLayout.glyphForFarStatus(player.status());
            if (glyph != ' ') {
                dots.add(new MinimapLayout.Dot(player.cellX() * 64 + 32, glyph));
            }
        }
        sprites.getAllSprites().stream()
                .filter(AbstractPlayableSprite.class::isInstance)
                .map(AbstractPlayableSprite.class::cast)
                .filter(sprite -> !sprite.isCpuControlled())
                .findFirst().ifPresent(sprite -> dots.add(
                        new MinimapLayout.Dot(sprite.getCentreX(), '*')));
        String strip = "[" + MinimapLayout.compose(profile.levelWidthPx(), dots) + "]";
        int screenHeight = config.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        text.drawShadowedText(strip, 4, screenHeight - 10,
                DebugColor.LIGHT_GRAY, SCALE);
    }
}
