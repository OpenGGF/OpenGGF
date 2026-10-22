package com.openggf;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.game.GameServices;
import com.openggf.graphics.AppearancePalettes;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.Objects;

/**
 * Draws a hosted run's own character in the player's chosen look. The character's renderer (and
 * Tails' separately drawn tails) are pointed at private palette rows composed from the rows they
 * already used, so only that sprite changes: rings, the HUD and objects sharing line 0 keep the
 * stock colours, and Super/Hyper cycles still show because only entries still at their stock
 * colour are replaced. Presentation only; nothing here is gameplay or rewind state.
 */
final class LocalPlayerLook {
    private PlayerAppearance requested = PlayerAppearance.STOCK;
    private AppearancePalettes.Block block;
    private PlayerSpriteRenderer body;
    private PlayerSpriteRenderer tails;
    private RenderContext bodyOriginal;
    private RenderContext tailsOriginal;
    private RenderContext applied;
    // The renderer and look whose rows could not be reserved; not retried until either changes.
    private PlayerSpriteRenderer failedBody;
    private PlayerAppearance failedLook;

    void request(PlayerAppearance look) {
        requested = Objects.requireNonNull(look, "look");
    }

    PlayerAppearance requested() {
        return requested;
    }

    /** Applies the requested look to {@code player}'s current renderers; call once per frame. */
    void refresh(AbstractPlayableSprite player, String characterCode) {
        refresh(player, characterCode, requested.isStock() ? null : GameServices.graphics(),
                LocalPlayerLook::builtinStock);
    }

    /** {@code stockLines} reads a builtin character's stock line (used when it has no context). */
    void refresh(AbstractPlayableSprite player, String characterCode, GraphicsManager graphics,
                 java.util.function.Function<String, Palette> stockLines) {
        if (requested.isStock() || player == null) {
            clear();
            failedBody = null;
            return;
        }
        PlayerSpriteRenderer currentBody = player.getSpriteRenderer();
        PlayerSpriteRenderer currentTails = player.getTailsTailsController() == null
                ? null : player.getTailsTailsController().getRenderer();
        if (currentBody == null) {
            clear();
            return;
        }
        if (block == null || currentBody != body || currentTails != tails || !block.look().equals(requested)) {
            clear();
            if (currentBody == failedBody && requested.equals(failedLook)) {
                return;
            }
            acquire(currentBody, currentTails, characterCode, graphics, stockLines);
            if (block == null) {
                failedBody = currentBody;
                failedLook = requested;
                return;
            }
        }
        RenderContext context = block.context();
        if (context == null) {
            return;
        }
        // Re-assert every frame: a palette reset (game change) moves the rows to a new bank.
        applied = context;
        body.setRenderContext(context);
        if (tails != null) tails.setRenderContext(context);
    }

    private void acquire(PlayerSpriteRenderer currentBody, PlayerSpriteRenderer currentTails, String characterCode,
                         GraphicsManager graphics, java.util.function.Function<String, Palette> stockLines) {
        RenderContext original = currentBody.getRenderContext();
        int line = currentBody.paletteLine();
        Palette stock = original != null ? original.getPalette(line) : stockLines.apply(characterCode);
        AppearancePalettes.Block acquired = AppearancePalettes.acquire(graphics,
                original == null ? null : original.getGameId(),
                original == null ? 0 : original.getPaletteLineBase(), line, stock, true, requested);
        if (acquired == null) {
            return;
        }
        block = acquired;
        body = currentBody;
        bodyOriginal = original;
        tails = currentTails;
        tailsOriginal = currentTails == null ? null : currentTails.getRenderContext();
    }

    /** The current game's stock line for a builtin character, read through the ROM pipeline. */
    private static Palette builtinStock(String characterCode) {
        var level = GameServices.levelOrNull();
        if (level == null || !(level.getGame() instanceof PlayerSpriteArtProvider provider) || characterCode == null) {
            return null;
        }
        return provider.loadCharacterPalette(characterCode);
    }

    /** Restores the renderers' own contexts and returns the rows. */
    void clear() {
        if (block == null) {
            return;
        }
        // Restore only renderers still drawing with these rows; anything else re-pointed them.
        if (body != null && body.getRenderContext() == applied) body.setRenderContext(bodyOriginal);
        if (tails != null && tails.getRenderContext() == applied) tails.setRenderContext(tailsOriginal);
        block.release();
        applied = null;
        block = null;
        body = null;
        tails = null;
        bodyOriginal = null;
        tailsOriginal = null;
    }
}
