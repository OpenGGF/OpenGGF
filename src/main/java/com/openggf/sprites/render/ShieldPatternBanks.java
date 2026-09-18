package com.openggf.sprites.render;

import com.openggf.graphics.PatternAtlasRange;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Level-art-owned shield banks. Obj_*Shield shares ArtTile_Shield for the native
 * player; extended teams need one bank per owner because their cursors differ.
 * Shield types remain mutually exclusive for one player, including insta-shield.
 */
public final class ShieldPatternBanks {
    // Reserved within TRANSIENT_EFFECTS: +$1000..+$1FFF. Snowboard art starts at +$2000.
    private static final int BASE = PatternAtlasRange.TRANSIENT_EFFECTS.base() + 0x1000;
    private static final int END = BASE + 0x1000;
    // Maximum of the shipped fire/lightning/bubble/insta mapping and DPLC capacities.
    private static final int OWNER_CAPACITY = 36;
    private final Map<AbstractPlayableSprite, OwnerBank> owners = new IdentityHashMap<>();
    private int nextBase = BASE;
    private boolean nativeBankClaimed;

    public PlayerSpriteRenderer renderer(AbstractPlayableSprite owner, SpriteArtSet art) {
        return renderer(owner, art, true);
    }

    /** Donated body art may occupy ArtTile_Shield, so its shield must use virtual space. */
    public PlayerSpriteRenderer renderer(AbstractPlayableSprite owner, SpriteArtSet art, boolean allowNativeBank) {
        if (art == null || owner == null) return null;
        if (art.bankSize() < 0 || art.bankSize() > OWNER_CAPACITY) {
            throw new IllegalArgumentException("Shield art exceeds owner bank capacity: " + art.bankSize());
        }
        OwnerBank bank = owners.get(owner);
        // Donation can be enabled after initial level boot but before playable-art
        // refresh. Rebind an existing native owner before publishing donated art.
        if (bank != null && !allowNativeBank && bank.base == art.basePatternIndex()) {
            bank = null;
        }
        if (bank == null) {
            int base;
            if (allowNativeBank && !nativeBankClaimed && !owner.isCpuControlled()) {
                nativeBankClaimed = true;
                base = art.basePatternIndex();
            } else {
                if (nextBase > END - OWNER_CAPACITY) {
                    throw new IllegalStateException("Shield owner banks exceed reserved effects range");
                }
                base = nextBase;
                nextBase += OWNER_CAPACITY;
            }
            bank = new OwnerBank(base);
            owners.put(owner, bank);
        }
        int base = bank.base;
        PlayerSpriteRenderer renderer = bank.renderers.computeIfAbsent(art, source -> new PlayerSpriteRenderer(new SpriteArtSet(
                source.artTiles(), source.mappingFrames(), source.dplcFrames(), source.paletteIndex(),
                base, source.frameDelay(), source.bankSize(), source.animationProfile(), source.animationSet())));
        // The mutually exclusive type previously displayed at this owner's address
        // may have overwritten the atlas, even when this renderer's frame is unchanged.
        if (bank.lastRenderer != renderer) renderer.invalidateDplcCache();
        bank.lastRenderer = renderer;
        return renderer;
    }

    public void clear() {
        owners.clear();
        nextBase = BASE;
        nativeBankClaimed = false;
    }

    private static final class OwnerBank {
        final int base;
        PlayerSpriteRenderer lastRenderer;
        final Map<SpriteArtSet, PlayerSpriteRenderer> renderers = new IdentityHashMap<>();
        OwnerBank(int base) { this.base = base; }
    }
}
