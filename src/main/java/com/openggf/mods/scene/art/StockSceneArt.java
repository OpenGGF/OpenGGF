package com.openggf.mods.scene.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneRomArt;
import static com.openggf.mods.scene.RomSpriteRequest.Compression.*;

/** Named recipes for the verified S3&K locked-on ROM, from sonic3k.lst Art/Map labels. */
@com.openggf.game.ModApi
public enum StockSceneArt {
    S3K_RING(0x192AEE, NEMESIS, 0x01A99A, 1, "ArtNem_RingHUDText / Map_Ring"),
    S3K_ROBOTNIK_SHIP(0x0D771E, NEMESIS, 0x06820C, 0, "ArtNem_RobotnikShip / Map_RobotnikShip"),
    S3K_EGG_CAPSULE(0x0DD990, NEMESIS, 0x086BFC, 0, "ArtNem_EggCapsule / Map_EggCapsule"),
    S3K_EXPLOSION(0x19200A, NEMESIS, 0x01E758, 0, "ArtNem_Explosion / Map_Explosion"),
    S3K_EGG_ROBO(0x17B17E, KOSINSKI_MODULED, 0x184F34, 0, "ArtKosM_EggRoboBadnik / Map_EggRobo"),
    S3K_SNALE_BLASTER(0x377996, KOSINSKI_MODULED, 0x360400, 1, "ArtKosM_SnaleBlaster / Map_SnaleBlaster"),
    S3K_MONKEY_DUDE(0x36800C, KOSINSKI_MODULED, 0x361776, 1, "ArtKosM_AIZ_MonkeyDude / Map_MonkeyDude"),
    S3K_BUGGERNAUT(0x36A3E0, NEMESIS, 0x360EB4, 1, "ArtNem_HCZDragonfly / Map_Buggernaut"),
    S3K_TURBO_SPIKER(0x36A968, KOSINSKI_MODULED, 0x361212, 1, "ArtKosM_TurboSpiker / Map_TurboSpiker"),
    S3K_ORBINAUT(0x377D1A, KOSINSKI_MODULED, 0x3604A4, 1, "ArtKosM_Orbinaut / Map_Orbinaut"),
    S3K_BLUE_FLICKY(0x1931D6, NEMESIS, 0x02CEBA, 0, 3, "ArtNem_BlueFlicky / Map_Animals1");

    private final RomSpriteRequest request;
    private final String reference;
    StockSceneArt(int art, RomSpriteRequest.Compression compression, int mappings, int line, String reference) {
        this(art, compression, mappings, line, 0, reference);
    }

    StockSceneArt(int art, RomSpriteRequest.Compression compression, int mappings, int line, int frames, String reference) {
        // Map_Animals1 has three pointers; frame 2 precedes frame 0 (sonic3k.lst 2CEBA).
        request = RomSpriteRequest.of(art, compression, mappings, line).withMappingFrameCount(frames);
        this.reference = reference;
    }

    public String gameId() { return "s3k"; }
    public String romSha1() { return "cfbf98c36c776677290a872547ac47c53d2761d6"; }
    public String reference() { return reference; }
    public RomSpriteRequest request(SceneRomArt rom) {
        if (!gameId().equals(rom.gameId()) || !romSha1().equalsIgnoreCase(rom.romSha1())) {
            throw new IllegalArgumentException(name() + " requires the verified S3&K locked-on ROM; got "
                    + rom.gameId() + " / " + rom.romSha1());
        }
        return request;
    }
}
