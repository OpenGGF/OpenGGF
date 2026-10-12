package starfall;

import com.openggf.mods.scene.*;
import static com.openggf.mods.scene.RomSpriteRequest.Compression.*;

/** ROM-owned AIZ presentation; only the creator's geometry and material overlays are authored here. */
public final class AngelIslandArt {
    public final SceneBackdrop backdrop;
    public final SceneImage ground, trunk, canopy, stone, grass, soil;
    public final SceneSpriteSet blooms, monkeys, rhinobot, rock, monitor, starpost, emerald, ring, ship;
    public AngelIslandArt(SceneRomArt rom) {
        backdrop=rom.zoneBackdrop(0,0);
        int[] palette=new int[64];
        System.arraycopy(rom.palette(0x0A8A3C,16),0,palette,0,16); // Pal_SonicTails
        System.arraycopy(rom.palette(0x0A8B7C,48),0,palette,16,48); // Pal_AIZ
        // Obj_AIZ1Tree uses level art at VRAM tile 1, line 2. Read its source from
        // LevelLoadBlock, rather than bundling any disassembly asset bytes.
        byte[] ptr=rom.read(0x091F0C,4);
        int primary=(ptr[1]&255)<<16|(ptr[2]&255)<<8|ptr[3]&255;
        SceneSpriteSet tree=rom.sprites(RomSpriteRequest.of(primary,KOSINSKI_MODULED,0x21C3E8,2)
                .withTileOffset(-1).withMappingFrameCount(1),palette);
        trunk=tree.frame(0).image().crop(0,16,16,16);
        SceneSpriteSet plants=rom.sprites(RomSpriteRequest.of(0x38DC90,NEMESIS,0x22B8EC,2)
                .withMappingFrameCount(2),palette); // ArtNem_AIZMisc1 / Map_AIZForegroundPlant
        canopy=plants.frame(0).image();
        var stages=rom.levelStages(0,0,128,64,0);
        if(stages.isEmpty())throw new IllegalStateException("AIZ terrain unavailable");
        SceneImage chosen=null,grassSample=null,soilSample=null;int best=-1,grassScore=-1,soilScore=-1;
        // Reject solid canopy floors as soil samples: AIZ's grassy rock faces carry
        // brown pixels below their grass. Compare decoded ROM colours, not game geometry.
        for(int n=0;n<Math.min(64,stages.size());n++) {
            var stage=stages.get(n);
            SceneImage candidate=rom.levelForeground(0,0,(stage.x()+15)/16*16,stage.floorY()/16*16,128,64);
            int score=0;
            for(int yy=16;yy<64;yy++)for(int xx=0;xx<128;xx++) {
                int pixel=candidate.pixel(xx,yy),r=pixel>>>16&255,g=pixel>>>8&255,b=pixel&255;
                if(pixel>>>24!=0&&r>g*1.2&&r>b*1.2&&r>35)score++;
            }
            if(score>best){chosen=candidate;best=score;}
            for(int yy=0;yy<64;yy+=16)for(int xx=0;xx<128;xx+=16) {
                int opaque=0,brown=0,greenTop=0,green=0;
                for(int b=0;b<16;b++)for(int a=0;a<16;a++) {
                    int pixel=candidate.pixel(xx+a,yy+b),r=pixel>>>16&255,g=pixel>>>8&255,z=pixel&255;
                    if(pixel>>>24!=0)opaque++;
                    if(r>g*1.2&&r>z*1.2&&r>35)brown++;
                    if(g>r*1.3&&g>z*1.2){green++;if(b<5)greenTop++;}
                }
                if(opaque<250)continue;
                int gs=greenTop*3+brown-green;
                if(greenTop>8&&brown>60&&gs>grassScore){grassScore=gs;grassSample=candidate.crop(xx,yy,16,16);}
                int ss=brown-green*3;
                if(brown>100&&ss>soilScore){soilScore=ss;soilSample=candidate.crop(xx,yy,16,16);}
            }
        }
        ground=chosen;
        if(grassSample==null||soilSample==null)throw new IllegalStateException("AIZ material samples unavailable");
        grass=grassSample;soil=soilSample;
        if(backdrop==null||ground==null)throw new IllegalStateException("AIZ scenery unavailable");
        blooms=rom.sprites(RomSpriteRequest.of(0x367DCA,KOSINSKI_MODULED,0x3616C0,1),palette);
        monkeys=rom.sprites(RomSpriteRequest.of(0x36800C,KOSINSKI_MODULED,0x361776,1),palette);
        rhinobot=rom.sprites(RomSpriteRequest.streamed(0x36732A,0xAA0,0x3615A8,0x36156E,
                RomSpriteRequest.DplcLayout.OBJECT,1),palette);
        rock=rom.sprites(RomSpriteRequest.of(0x38DC90,NEMESIS,0x21DCDC,1),palette);
        SceneImage rockImage=rock.frame(0).image();
        stone=rockImage.crop((rockImage.width()-16)/2,(rockImage.height()-16)/2,16,16);
        monitor=rom.sprites(RomSpriteRequest.of(0x190F4A,NEMESIS,0x01DBA2,0),palette);
        starpost=rom.sprites(RomSpriteRequest.of(0x35D8A2,NEMESIS,0x02D348,0),palette);
        emerald=rom.sprites(RomSpriteRequest.of(0x387CA6,KOSINSKI_MODULED,0x364562,0),
                rom.palette(0x067AAA,64));
        ring=rom.sprites(RomSpriteRequest.of(0x192AEE,NEMESIS,0x01A99A,1),palette);
        ship=rom.sprites(RomSpriteRequest.of(0x0D771E,NEMESIS,0x06820C,0),palette);
    }
    public void sprite(SceneCanvas c,SceneSpriteSet set,int frame,float x,float feet,float scale,boolean flip) {
        SceneSprite s=set.frame(frame);
        c.draw(s,x,feet-(s.height()-s.originY())*scale,SceneDraw.plain().withScale(scale).withFlipX(flip));
    }
    public void ground(SceneCanvas c,int tx,int ty,int sx,int sy,boolean surface,boolean stone) {
        SceneImage image=surface?grass:stone?this.stone:soil;
        SceneDraw style=SceneDraw.plain();
        if(!surface)style=style.withFlipX((tx+ty)%2==0).withTint(stone?0xFFB8B0B0:0xFFFFFFFF);
        c.drawRegion(image,0,0,16,16,sx,sy,World.T,World.T,style);
    }
}
