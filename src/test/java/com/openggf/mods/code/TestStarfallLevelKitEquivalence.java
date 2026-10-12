package com.openggf.mods.code;

import com.openggf.game.GameServices;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exact locked-on pixel qualification before replacing any creator native-art path. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarfallLevelKitEquivalence {
    @TempDir Path work;
    @Test void materialAndBackdropCapabilitiesAreComparedToCurrentNativeDecoder() throws Exception {
        SceneRomArt rom=SceneRomArtFactory.forModule(new com.openggf.game.sonic3k.Sonic3kGameModule(),GameServices.rom().getRom());
        assertEquals("cfbf98c36c776677290a872547ac47c53d2761d6",rom.romSha1());
        try(ExampleModHarness harness=ExampleModHarness.build(Path.of("examples/starfall-frontier"),work)) {
            Class<?> biome=harness.loader().loadClass("starfall.Biome");
            Class<?> bank=harness.loader().loadClass("starfall.BiomeArt");
            for(Object value:biome.getEnumConstants()) {
                String name=((Enum<?>)value).name();if(name.equals("ANGEL_ISLAND"))continue;
                int zone=biome.getField("zone").getInt(value);
                int[] acts=List.of("SANDOPOLIS","HYDROCITY","LAVA_REEF").contains(name)?new int[]{0,1}:
                        name.equals("HIDDEN_PALACE")?new int[]{1}:new int[]{0};
                for(int act:acts) {
                    long started=System.nanoTime();
                    SceneImage[] originals=rawMaterials(rom,bank,name,zone,act);
                    long rawNanos=System.nanoTime()-started;
                    started=System.nanoTime();
                    Object current=bank.getConstructor(SceneRomArt.class,biome,int.class).newInstance(rom,value,act);
                    SceneLevelKit kit=rom.levelKit(zone,act);assertNotNull(kit,name+":"+act);
                    SceneImage[] samples={(SceneImage)bank.getField("surface").get(current),
                            (SceneImage)bank.getField("interior").get(current),
                            ((SceneImage[])bank.getField("variations").get(current))[1]};
                    long currentNanos=System.nanoTime()-started;
                    for(int n=0;n<samples.length;n++)assertArrayEquals(originals[n].pixels(),samples[n].pixels(),
                            name+":"+act+" native material "+n);
                    SceneBackdrop rawBackdrop=(SceneBackdrop)bank.getField("backdrop").get(current);
                    SceneBackdrop existing=kit.backdrop();
                    boolean sameImage=rawBackdrop.image().width()==existing.image().width()
                            &&rawBackdrop.image().height()==existing.image().height()
                            &&Arrays.equals(rawBackdrop.image().pixels(),existing.image().pixels());
                    assertEquals(List.of("LAUNCH_BASE:0","HYDROCITY:0","SKY_SANCTUARY:0").contains(name+":"+act),sameImage,
                            "qualified backdrop capability disposition "+name+":"+act);
                    System.out.printf(java.util.Locale.ROOT,"STARFALL_PREP %s:%d raw_material_ms=%.3f current_bank_ms=%.3f%n",
                            name,act,rawNanos/1_000_000d,currentNanos/1_000_000d);

                }
            }
        }
    }
    private SceneImage[] rawMaterials(SceneRomArt rom,Class<?> bank,String name,int zone,int act) throws Exception {
        // The retained custom-background decoder is the pre-migration ROM block oracle.
        int index=name.equals("HIDDEN_PALACE")?47:zone*2+act;
        byte[] entry=rom.read(0x091F0C+index*24,24);
        int[] palette=new int[64];System.arraycopy(rom.palette(0x0A8A3C,16),0,palette,0,16);
        int paletteAddress=pointer(rom.read(0x0A872C+(entry[8]&255)*8,4),0);
        System.arraycopy(rom.palette(paletteAddress,48),0,palette,16,48);
        int primary=pointer(entry,0),secondary=pointer(entry,4);
        int count=word(rom.read(primary,2))/32;
        int second=primary==secondary?0:word(rom.read(secondary,2))/32;
        var archive=bank.getDeclaredMethod("archivePair",SceneRomArt.class,byte[].class,int.class);archive.setAccessible(true);
        byte[] maps=(byte[])archive.invoke(null,rom,entry,8);
        var images=bank.getDeclaredMethod("blockImages",SceneRomArt.class,int.class,int.class,int.class,int.class,
                int[].class,byte[].class,SceneImage[][].class);images.setAccessible(true);
        SceneImage[] nativeBlocks=(SceneImage[])images.invoke(null,rom,primary,secondary,count,second,palette,maps,null);
        int[] ids=switch(name) {
            case "MARBLE_GARDEN"->new int[]{280,2,36};case "MUSHROOM_HILL"->new int[]{50,51,44};
            case "CARNIVAL_NIGHT"->new int[]{56,20,45};case "ICECAP"->new int[]{36,70,71};
            case "SANDOPOLIS"->new int[]{1,4,162};case "LAUNCH_BASE"->new int[]{4,11,14};
            case "HYDROCITY"->new int[]{36,33,38};case "LAVA_REEF"->new int[]{5,17,23};
            case "HIDDEN_PALACE"->new int[]{13,1,3};case "SKY_SANCTUARY"->new int[]{4,1,2};
            default->throw new AssertionError(name);
        };
        return new SceneImage[]{nativeBlocks[ids[0]],nativeBlocks[ids[1]],nativeBlocks[ids[2]]};
    }
    private int pointer(byte[] value,int at){return (value[at+1]&255)<<16|(value[at+2]&255)<<8|value[at+3]&255;}
    private int word(byte[] value){return (value[0]&255)<<8|value[1]&255;}
}
