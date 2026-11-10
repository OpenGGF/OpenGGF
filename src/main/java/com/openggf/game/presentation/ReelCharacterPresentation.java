package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.session.SessionManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/** Presentation-only reel portraits. No changes to symbol selection, clocks or rewards. */
public final class ReelCharacterPresentation {
    private static final Logger LOG = Logger.getLogger(ReelCharacterPresentation.class.getName());
    private ReelCharacterPresentation() { }

    public record Portrait(CharacterPresentation.Image image, int[] rgb) {
        public Portrait { rgb = rgb.clone(); }
        @Override public int[] rgb() { return rgb.clone(); }
    }

    public static Map<Integer, Portrait> active(CharacterPresentation.Host host) {
        var world = SessionManager.getCurrentWorldSession();
        if (world == null) return Map.of();
        var config = GameServices.configuration();
        var main = definition(ActiveGameplayTeamResolver.resolveMainCharacterCode(config));
        var followers = ActiveGameplayTeamResolver.resolveSidekicks(config);
        var sidekick = followers.isEmpty() ? null : definition(followers.getFirst());
        return resolve(host, main, sidekick);
    }

    private static CharacterDefinition definition(String code) {
        if (code == null) return null;
        return SessionManager.getCurrentWorldSession().getPlayableCharacterRegistry()
                .find(CharacterKey.parsePersisted(code)).orElse(null);
    }

    public static Map<Integer, Portrait> resolve(CharacterPresentation.Host host,
            CharacterDefinition main, CharacterDefinition sidekick) {
        var result = new LinkedHashMap<Integer, Portrait>();
        boolean tailsLead = main != null && main.behavesLike() == PlayerCharacter.TAILS_ALONE;
        put(result, host == CharacterPresentation.Host.S2 ? 0 : 1, main, host, false);
        if (tailsLead) put(result, host == CharacterPresentation.Host.S2 ? 1 : 2, main, host, false);
        else put(result, host == CharacterPresentation.Host.S2 ? 1 : 2, sidekick, host, false);
        if (host == CharacterPresentation.Host.S3K) put(result, 7, main, host, true);
        return Map.copyOf(result);
    }

    private static void put(Map<Integer, Portrait> result, int symbol, CharacterDefinition definition,
            CharacterPresentation.Host host, boolean powered) {
        if (definition == null || definition.key().isBuiltin() || definition.presentationSupplier() == null) return;
        try {
            var presentation = definition.presentationSupplier().load(definition.key().persisted(), switch (host) {
                case S1 -> GameId.S1; case S2 -> GameId.S2; case S3K -> GameId.S3K;
            });
            var face = presentation == null ? null : presentation.reelFaces().get(host);
            if (face == null) return;
            var image = powered ? face.poweredFace() : face.face();
            if (image != null) result.put(symbol, new Portrait(image, powered ? face.poweredRgb() : face.rgb()));
        } catch (IOException | RuntimeException failure) {
            LOG.warning("Reel portrait retained native fallback: " + failure.getMessage());
        }
    }

    /** RGBA atlas: alpha zero marks native palette indices in red; opaque pixels are authored RGB.
     * Transparent authored pixels retain the native flat backing index. */
    public static ByteBuffer atlas(ByteBuffer indices, int faces, Map<Integer, Portrait> portraits) {
        var rgba = org.lwjgl.system.MemoryUtil.memAlloc(32 * 32 * faces * 4);
        for (int f = 0; f < faces; f++) {
            var portrait = portraits.get(f);
            byte[] pixels = portrait == null ? null : portrait.image().pixels();
            for (int i = 0; i < 1024; i++) {
                int index = pixels == null ? indices.get(f * 1024 + i) & 15 : pixels[i] & 15;
                if (pixels != null && index == 0) index = indices.get(f * 1024 + 16) & 15;
                if (pixels == null || pixels[i] == 0) rgba.put((byte)index).put((byte)0).put((byte)0).put((byte)0);
                else {
                    int rgb = portrait.rgb[index];
                    rgba.put((byte)(rgb >> 16)).put((byte)(rgb >> 8)).put((byte)rgb).put((byte)255);
                }
            }
        }
        return rgba.flip();
    }
}
