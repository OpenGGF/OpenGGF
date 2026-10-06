package towerdefense.art;

import com.openggf.mods.scene.*;
import towerdefense.core.Catalog;

/**
 * S3K locked-on sprite requests, from the disassembly labels cited below.
 * Addresses/palette ownership also exercised by Slay the Robotnik's RomSprites.
 * Every recognizable game asset is decoded from the player's ROM at runtime.
 */
public final class RomArt {
    public final SceneBackdrop background;
    private final SceneSpriteSet[] defenses = new SceneSpriteSet[6];
    private final SceneSpriteSet flicky, robotnik, capsule, explosion;

    public RomArt(SceneRomArt rom) {
        if (rom == null) throw new IllegalStateException("Robotnik Tower Defence requires S3K ROM art");
        background = rom.zoneBackdrop(6, 0); // Launch Base act 1
        if (background == null) throw new IllegalStateException("Launch Base scenery unavailable");
        int[] aiz = palette(rom, 0x0A8B7C); // Pal_AIZ
        int[] hcz = palette(rom, 0x0A8D9C); // Pal_HCZ1
        int[] lbz = palette(rom, 0x0A929C); // Pal_LBZ1
        defenses[Catalog.SNALE] = rom.sprites(RomSpriteRequest.of(0x377996,
                RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x360400, 1), lbz); // SnaleBlaster
        defenses[Catalog.MORTAR] = rom.sprites(RomSpriteRequest.of(0x36800C,
                RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x361776, 1), aiz); // MonkeyDude
        defenses[Catalog.BUGGERNAUT] = rom.sprites(RomSpriteRequest.of(0x36A3E0,
                RomSpriteRequest.Compression.NEMESIS, 0x360EB4, 1), hcz); // HCZDragonfly / Buggernaut
        defenses[Catalog.SPIKER] = rom.sprites(RomSpriteRequest.of(0x36A968,
                RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x361212, 1), hcz); // TurboSpiker
        defenses[Catalog.ORBINAUT] = rom.sprites(RomSpriteRequest.of(0x377D1A,
                RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x3604A4, 1), lbz); // Orbinaut
        defenses[Catalog.EGG_ROBO] = rom.sprites(RomSpriteRequest.of(0x17B17E,
                RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x184F34, 0), rom.palette(0x05CBCA, 64)); // EggRobo / Continue
        flicky = rom.sprites(RomSpriteRequest.of(0x1931D6,
                RomSpriteRequest.Compression.NEMESIS, 0x02CEBA, 0), aiz); // BlueFlicky / Animals1
        robotnik = rom.sprites(RomSpriteRequest.of(0x0D771E,
                RomSpriteRequest.Compression.NEMESIS, 0x06820C, 0), lbz); // RobotnikShip: head 0-3, body 5
        capsule = rom.sprites(RomSpriteRequest.of(0x0DD990,
                RomSpriteRequest.Compression.NEMESIS, 0x086BFC, 0), lbz); // EggCapsule
        explosion = rom.sprites(RomSpriteRequest.of(0x19200A,
                RomSpriteRequest.Compression.NEMESIS, 0x01E758, 0), aiz); // Explosion
    }

    private int[] palette(SceneRomArt rom, int zone) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(0x0A8A3C, 16), 0, palette, 0, 16); // Pal_SonicTails
        System.arraycopy(rom.palette(zone, 48), 0, palette, 16, 48);
        return palette;
    }

    public void defense(SceneCanvas c, int kind, int ticks, float x, float feet, int maxSize, int tint) {
        SceneSpriteSet set = defenses[kind];
        if (kind == Catalog.EGG_ROBO) {
            // ChildObjDat_919D0: frame 0 is empty. Arm and legs belong behind body 1/3.
            float scale = maxSize / 72f, y = feet - 40 * scale;
            SceneDraw style = SceneDraw.plain().withScale(scale).withTint(tint).withFlipX(true);
            c.draw(set.frame(2), x + 28 * scale, y - 4 * scale, style);
            c.draw(set.frame(5), x + 12 * scale, y + 28 * scale, style);
            c.draw(set.frame(ticks % 2 == 0 ? 1 : 3), x, y, style);
            return;
        }
        if (kind == Catalog.SNALE) {
            // ChildObjDat_8C28A: two cannons behind the crawling body and hatch cover.
            float scale = maxSize / 40f, y = feet - 20 * scale;
            SceneDraw style = SceneDraw.plain().withScale(scale).withTint(tint).withFlipX(true);
            c.draw(set.frame(7), x + 8 * scale, y, style);
            c.draw(set.frame(7), x + 8 * scale, y + 7 * scale, style);
            c.draw(set.frame((ticks / 16) % 2 == 0 ? 4 : 3), x, y, style);
            c.draw(set.frame(5), x + 8 * scale, y + 4 * scale, style);
            return;
        }
        int frame = kind == Catalog.BUGGERNAUT ? (ticks / 3) % 3
                : kind == Catalog.SPIKER ? (ticks / 6) % 3 : 0;
        SceneSprite sprite = set.frame(frame);
        float scale = Math.min(1f, (float) maxSize / Math.max(sprite.width(), sprite.height()));
        float y = feet - (sprite.height() - sprite.originY()) * scale;
        SceneDraw style = SceneDraw.plain().withScale(scale).withTint(tint).withFlipX(kind != Catalog.MORTAR);
        if (kind == Catalog.SPIKER) c.draw(set.frame(3), x - 4 * scale, y, style); // spike child
        c.draw(sprite, x, y, style);
    }

    public void flicky(SceneCanvas c, int ticks, float x, float y, int tint, boolean hit, float scale) {
        SceneDraw style = SceneDraw.plain().withScale(scale).withTint(tint);
        if (hit) style = style.withFlash(0xFFFFFFFF);
        c.draw(flicky.frame((ticks / 7) % 2), x, y, style);
    }

    public void robotnik(SceneCanvas c, float x, float y, boolean panic, float scale) {
        c.draw(robotnik.frame(5), x, y, SceneDraw.plain().withScale(scale));
        c.draw(robotnik.frame(panic ? 2 : 0), x, y - 16 * scale, SceneDraw.plain().withScale(scale));
    }

    public void capsule(SceneCanvas c, float x, float y) { c.draw(capsule.frame(0), x, y, SceneDraw.plain().withScale(0.5f)); }
    public void explosion(SceneCanvas c, int age, float x, float y) {
        c.draw(explosion.frame(Math.min(explosion.frameCount() - 1, age / 4)), x, y, SceneDraw.plain());
    }
}
