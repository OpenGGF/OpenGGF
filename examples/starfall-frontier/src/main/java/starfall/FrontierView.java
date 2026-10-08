package starfall;

import com.openggf.mods.scene.*;
import com.openggf.mods.ui.AtlasFont;
import static starfall.World.*;

/** S3K biome scenery over a mutable creator world, with compact workshop panels. */
public final class FrontierView {
    private static final int INK=0xFF10184C, PANEL=0xF2182860, EDGE=0xFF4080B0,
            TEXT=0xFFFFFFFF, MUTED=0xFFA8C8F8, GOLD=0xFFFFDA28, MINT=0xFF80F860;
    private final AtlasFont font;
    private final AngelIslandArt art;
    private final EnemyArt enemyArt;
    private final BiomeArt[][] biomes;
    private final BackdropBlend background=new BackdropBlend();
    public FrontierView(byte[] bytes,SceneRomArt rom) {
        font=AtlasFont.parse(bytes,5);art=rom==null?null:new AngelIslandArt(rom);
        enemyArt=rom==null?null:new EnemyArt(rom,art);
        biomes=new BiomeArt[Biome.values().length][2];
        if(rom!=null)for(Biome biome:Biome.values())if(biome!=Biome.ANGEL_ISLAND) {
            biomes[biome.ordinal()][0]=new BiomeArt(rom,biome,0);
            biomes[biome.ordinal()][1]=biome==Biome.SANDOPOLIS||biome==Biome.HYDROCITY||biome==Biome.LAVA_REEF?
                    new BiomeArt(rom,biome,1):biomes[biome.ordinal()][0];
        }
    }
    public void snapBackdrop(World world){background.snap(world);}
    public void updateBackdrop(World world){background.update(world);}
    private int backdropSky() {
        double red=0,green=0,blue=0;
        for(Biome biome:Biome.values())for(int act=0;act<2;act++) {
            double weight=background.weight(biome.ordinal()*2+act);int sky=biome.sky;
            red+=(sky>>>16&255)*weight;green+=(sky>>>8&255)*weight;blue+=(sky&255)*weight;
        }
        return (int)red<<16|(int)green<<8|(int)blue;
    }
    private void backgrounds(SceneCanvas c,World world,double cameraX) {
        c.clear(backdropSky());
        double cumulative=0;
        for(Biome biome:Biome.values())for(int act=0;act<2;act++) {
            double weight=background.weight(biome.ordinal()*2+act);
            if(weight<=0)continue;
            cumulative+=weight;
            BiomeArt bank=biomes[biome.ordinal()][act];
            SceneBackdrop backdrop=bank==null?art.backdrop:bank.backdrop;
            int top=bank==null?Math.max(0,Math.min(256,backdrop.image().height()-224)):bank.backdropTop;
            // Source-over alpha w / cumulative produces the weighted mix even when a fade
            // is interrupted by a third biome. The first image always fills the screen.
            SceneDraw style=SceneDraw.plain().withAlpha((float)(weight/cumulative));
            for(SceneBackdrop.Band band:backdrop.bands()) {
                int y0=Math.max(top,band.top()),y1=Math.min(top+224,band.top()+band.height());
                if(y1<=y0)continue;
                int column=backdrop.column(band,cameraX,world.ticks),imageWidth=backdrop.image().width();
                for(int done=0;done<c.width();) {
                    int source=(column+done)%imageWidth,run=Math.min(imageWidth-source,c.width()-done);
                    c.drawRegion(backdrop.image(),source,y0,run,y1-y0,done,y0-top,run,y1-y0,style);
                    done+=run;
                }
            }
        }
    }
    private BiomeArt terrain(World w,int tx,int ty) {
        Biome biome=Biome.at(w,tx,ty);return biomes[biome.ordinal()][biome.act(w,tx,ty)];
    }
    private void text(SceneCanvas c,String s,int x,int y,int color){font.draw(c,s,x,y,color,1);}
    private void center(SceneCanvas c,String s,int x,int y,int color,int scale){font.draw(c,s,x-font.width(s)*scale/2,y,color,scale);}
    private void box(SceneCanvas c,int x,int y,int w,int h){c.fill(x+2,y+3,w,h,0x50000000);c.fill(x,y,w,h,EDGE);c.fill(x+1,y+1,w-2,h-2,PANEL);c.fill(x+2,y+2,w-4,1,0xFF677C80);}
    private int hash(int x,int y){int v=x*374761393+y*668265263;v=(v^(v>>>13))*1274126177;return v^(v>>>16);}
    private int shade(int color,double k){int r=(int)(((color>>16)&255)*k),g=(int)(((color>>8)&255)*k),b=(int)((color&255)*k);return 0xFF000000|(r<<16)|(g<<8)|b;}
    private int mix(int a,int b,double t){int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t),z=(int)((a&255)*(1-t)+(b&255)*t);return 0xFF000000|r<<16|g<<8|z;}
    public void world(SceneCanvas c,World w,double cameraX,double cameraY,int tick) {
        int width=c.width();
        double day=(Math.sin(w.ticks/21600.0*Math.PI*2)+1)*.5;
        double deep=Math.max(0,Math.min(1,(cameraY-300)/150));
        if(art==null) {
        int sky=mix(0x20344C,w.region().sky,day*.85);
        for(int y=0;y<224;y+=4)c.fill(0,y,width,4,mix(mix(sky,0x182838,deep),mix(0xE2C9A2,0x263B49,deep),y/224.0));
        if(deep<.7) {
            for(int i=0;i<34;i++) {
                int px=Math.floorMod(hash(i,0)-(int)(cameraX*.05),width),py=Math.floorMod(hash(i,1),100);
                if(day<.55)c.fill(px,py,1+(i%4==0?1:0),1,0x99D5E6DD);
            }
            disk(c,width-94-(int)(cameraX*.02)%80,45,11,0xFFDBCFA4);disk(c,width-97-(int)(cameraX*.02)%80,41,9,mix(sky,0x87ADAF,day));
            for(int layer=0;layer<3;layer++) {
                int color=mix(0x4E818B,0x254E5B,layer*.36);
                for(int x=0;x<width;x+=3) {
                    double k=x+cameraX*(.08+layer*.1);
                    int h=(int)(124+layer*18+Math.sin(k*.012+layer*2)*19+Math.sin(k*.025)*9-cameraY*.04);
                    c.fill(x,h,3,224-h,color);
                }
            }
            for(int i=0;i<12;i++) {
                int px=Math.floorMod(i*137-(int)(cameraX*.4),width+100)-50;
                int py=100+(int)(Math.sin(i*3.1)*22)- (int)(cameraY*.07);
                c.fill(px+14,py,4,85,0x55315051);
                for(int row=0;row<4;row++)c.fill(px-row*5,py+row*9,32+row*10,10,0x44345A5C);
            }
            for(int i=0;i<8;i++) {
                int px=Math.floorMod(i*79+tick/4-(int)(cameraX*.1),width+100)-50,py=34+i%3*16;
                c.fill(px+8,py-2,26,4,0x36FFF0CB);c.fill(px,py,48,4,0x36FFF0CB);
            }
        }
        }else {
            backgrounds(c,w,cameraX);
            if(deep>.05)c.fill(0,0,width,224,(int)(deep*140)<<24|backdropSky());
        }
        int x0=Math.max(0,(int)cameraX/T-1),x1=Math.min(w.width-1,(int)(cameraX+width)/T+1);
        int y0=Math.max(0,(int)cameraY/T-1),y1=Math.min(w.height-1,(int)(cameraY+224)/T+1);
        for(int ty=y0;ty<=y1;ty++)for(int tx=x0;tx<=x1;tx++) {
            int sx=(int)Math.round(tx*T-cameraX),sy=(int)Math.round(ty*T-cameraY),t=w.tile(tx,ty);
            if(w.walls[ty*w.width+tx]!=0) {
                int wall=w.walls[ty*w.width+tx]==1?0xFF5B4B43:shade(Biome.at(w,tx,ty).color,.25);
                c.fill(sx,sy,T,T,wall);c.fill(sx,sy+10,T,2,shade(wall,.76));c.fill(sx+(ty%2)*6,sy,1,T,shade(wall,.8));
            }
            tile(c,w,tx,ty,sx,sy,t,tick);
        }
        // The camp's beacon is independent of breakable terrain.
        int bx=(int)(40*T+6-cameraX),by=(int)(w.surface(40)*T-cameraY);
        if(art!=null)art.sprite(c,art.starpost,w.won?2:0,bx,by,.75f,false);
        else {
        c.fill(bx-9,by-3,18,3,0xFF6C7C87);c.fill(bx-6,by-6,12,3,0xFF9AA7A4);
        c.fill(bx-3,by-27,6,21,0xFF89998F);c.fill(bx-6,by-28,12,3,0xFFCAD0B8);
        diamond(c,bx,by-34,7,w.won?0xFFD8F5C5:0xFF95BEB8);c.fill(bx-2,by-38,2,4,0xFFDEEBDD);
        }
        if(w.won) {glow(c,bx,by-34,0x18FFE7A2);c.fill(bx-1,0,2,Math.max(0,by-38),0x45FFE2A6);}
        for(World.Enemy e:w.enemies)enemy(c,w,e,(int)(e.x-cameraX),(int)(e.y-cameraY),tick);
        for(World.Shot s:w.shots) {
            int sx=(int)(s.x-cameraX),sy=(int)(s.y-cameraY),color=s.hostile?0xFFFF9E99:s.magic?0xFFAFF1DF:0xFFE1CFAA;
            if(s.magic){c.fill(sx-3,sy-3,6,6,0x44A7DCCD);diamond(c,sx,sy,3,color);}else c.fill(sx-3,sy,7,1,color);
        }
        if(w.selected().weapon()&&w.actionCooldown>12&&!w.selected().equals(Content.Item.BOW)&&!w.selected().equals(Content.Item.STAFF)) {
            int px=(int)(w.x-cameraX),py=(int)(w.y-cameraY),dir=w.facingLeft?-1:1;
            for(int i=0;i<9;i++) {double a=(i-4)*.25;int ax=(int)(px+Math.cos(a)*29*dir),ay=(int)(py+Math.sin(a)*29);c.fill(ax,ay,3,3,0xCCF9E6B5);}
        }
        for(World.Particle p:w.particles) {
            int sx=(int)(p.x-cameraX),sy=(int)(p.y-cameraY);
            if(p.text==null)c.fill(sx,sy,2,2,p.color);else if(p.life>5)center(c,p.text,sx,sy,p.color,1);
        }
        if(deep>.2) {
            // Visible darkness around the explorer; lanterns and crystals illuminate local tile details.
            int px=(int)(w.x-cameraX),py=(int)(w.y-cameraY);
            for(int a=0;a<width;a+=12)for(int b=0;b<224;b+=12) {
                double distance=Math.hypot((a-px)*.65,b-py);
                int alpha=(int)(Math.max(0,Math.min(150,(distance-42)*1.6))*deep);
                if(alpha>0)c.fill(a,b,12,12,alpha<<24|0x080F20);
            }
        }
        for(int i=0;i<12;i++) {
            double drift=tick*(.1+(i%3)*.04);
            int px=Math.floorMod(hash(i,6)+(int)drift-(int)(cameraX*.8),width),py=Math.floorMod(hash(i,7)-(int)(tick*.12),210);
            if(i%3==0)c.fill(px,py,1,2,0x88DCE9BA);
        }
    }
    private void tile(SceneCanvas c,World w,int tx,int ty,int sx,int sy,int t,int tick) {
        if(t==AIR)return;
        if(art!=null) {
            if(solid(t)&&t!=PLANK) {
                c.fill(sx,sy,T,T,t==STONE||t==BEDROCK?0xFF704838:0xFF985020);
                BiomeArt bank=terrain(w,tx,ty);
                if(bank==null)art.ground(c,tx,ty,sx,sy,t==GRASS||t==SNOW||t==EMBER,t==STONE||t==BEDROCK||t==COPPER||t==IRON||t==CRYSTAL);
                else c.drawRegion(t==GRASS||t==SNOW||t==EMBER?bank.surface:bank.variations[Math.floorMod(hash(tx,ty),bank.variations.length)],
                        0,0,16,16,sx,sy,T,T,SceneDraw.plain());
                if(t==COPPER||t==IRON||t==CRYSTAL) {
                    // Resources are creator materials, over the original AIZ rock faces.
                    diamond(c,sx+6,sy+6,3,t==COPPER?0xFFFFB840:t==IRON?0xFFD8E8FF:0xFF40FFE0);
                    c.fill(sx+5,sy+3,1,3,0xFFFFFFFF);
                }
                return;
            }
            if(t==LOG){c.drawRegion(art.trunk,0,0,16,16,sx+2,sy,8,T,SceneDraw.plain());return;}
            if(t==LEAVES&&Biome.at(w,tx,ty)!=Biome.ANGEL_ISLAND) {
                int color=Biome.at(w,tx,ty).color;
                c.fill(sx,sy,T,T,shade(color,.75));
                if(w.tile(tx,ty-1)!=LEAVES) {
                    c.fill(sx,sy,T,4,0xFFDA6028);c.fill(sx+2,sy+1,2,2,0xFFFFD888);
                    c.fill(sx+8,sy+2,2,2,0xFFFFD888);
                }
                c.fill(sx+3,sy+7,4,2,shade(color,.95));return;
            }
            if(t==LEAVES) {
                // Map each surviving foliage cell to the palm crown; mining edits remain visible.
                int root=tx;
                for(int a=-3;a<=3;a++)for(int b=-2;b<=2;b++)if(w.tile(tx+a,ty+b)==LOG&&w.tile(tx+a,ty+b-1)!=LOG)root=tx+a;
                int top=ty;
                for(int b=-2;b<=2;b++)if(w.tile(root,ty+b)==LOG&&w.tile(root,ty+b-1)!=LOG)top=ty+b;
                int col=Math.max(0,Math.min(6,tx-root+3)),row=Math.max(0,Math.min(4,ty-top+2));
                c.drawRegion(art.canopy,col*9,row*18,9,18,sx,sy,T,T,SceneDraw.plain());return;
            }
            if(t==CHEST){art.sprite(c,art.monitor,0,sx+6,sy+12,.6f,false);return;}
            if(t==SHRINE){art.sprite(c,art.starpost,0,sx+6,sy+12,.65f,false);art.sprite(c,art.emerald,(tx/20)%7,sx+6,sy-10,1.2f,false);return;}
            if(t==BUSH){c.draw(art.canopy,sx-1,sy-2,SceneDraw.plain().withScale(.24f));return;}
        }

        int color=World.color(t),h=hash(tx,ty);
        if(solid(t)) {
            c.fill(sx,sy,T,T,color);c.fill(sx,sy+T-2,T,2,shade(color,.73));c.fill(sx+T-1,sy,1,T,shade(color,.8));
            for(int n=0;n<3;n++) {
                int a=1+Math.floorMod(h>>(n*4),9),b=2+Math.floorMod(h>>(n*5+3),7);
                c.fill(sx+a,sy+b,2,1,shade(color,n%2==0?1.13:.82));
            }
            if(!solid(w.tile(tx,ty-1))) {
                c.fill(sx,sy,T,2,shade(color,1.12));
                if(t==GRASS||t==SNOW||t==EMBER){c.fill(sx,sy,T,3,t==GRASS?0xFF90B574:t==SNOW?0xFFDFE8E5:0xFFE09271);c.fill(sx+2,sy-2,1,3,t==GRASS?0xFFB2C98C:shade(color,1.1));}
            }
            if(t==COPPER||t==IRON||t==CRYSTAL) {
                c.fill(sx+3,sy+2,4,3,t==COPPER?0xFFE1AD86:t==IRON?0xFFD0DADB:0xFFB4F2E4);
                c.fill(sx+7,sy+7,3,2,shade(color,1.17));
                if(t==CRYSTAL&&((tick/18+tx+ty)%5==0))c.fill(sx+4,sy+2,1,3,0xFFFFFFFF);
            }
            if(t==PLANK)for(int b=3;b<12;b+=4)c.fill(sx,sy+b,12,1,shade(color,.7));
        }else switch(t) {
            case LOG -> {c.fill(sx+3,sy,6,12,color);c.fill(sx+4,sy,2,12,0xFFB39466);c.fill(sx+8,sy,1,12,0xFF634A38);c.fill(sx+3,sy+Math.floorMod(h,10),3,1,0xFF6E543D);}
            case LEAVES -> {
                color=tx<88?0xFF5D8E6D:tx<170?0xFF83AEB3:0xFFAA7C70;
                c.fill(sx,sy,12,12,color);
                if(w.tile(tx,ty-1)!=LEAVES)c.fill(sx,sy,12,2,shade(color,1.22));
                if(w.tile(tx,ty+1)!=LEAVES)c.fill(sx,sy+10,12,2,shade(color,.72));
                c.fill(sx+2,sy+2,4,2,shade(color,1.12));c.fill(sx+7,sy+7,3,2,shade(color,.84));
                if(Math.floorMod(h,4)==0)c.fill(sx+5,sy+6,2,2,tx<88?0xFFECC28B:tx<170?0xFFDCEBE8:0xFFE8B99A);
            }
            case PLATFORM -> {c.fill(sx,sy,12,3,0xFFB69B72);c.fill(sx,sy+3,2,3,0xFF705B42);c.fill(sx+10,sy+3,2,3,0xFF705B42);}
            case TORCH -> {c.fill(sx+5,sy+4,2,8,0xFFA58259);glow(c,sx+6,sy+2,0x10F7D596);c.fill(sx+3,sy-1,6,6,0xFFEBC68B);c.fill(sx+4,sy-2+(tick/7+tx)%2,4,4,0xFFF8E6B2);c.fill(sx+2,sy-2,8,1,0xFF685E4C);}
            case BENCH -> {c.fill(sx-4,sy+3,20,3,0xFFBF9B68);c.fill(sx-2,sy+6,3,6,0xFF826844);c.fill(sx+11,sy+6,3,6,0xFF826844);c.fill(sx+4,sy,4,3,0xFFD3C3A1);}
            case FURNACE -> {c.fill(sx,sy+2,12,10,0xFF9A9088);c.fill(sx+1,sy,10,2,0xFFBBADA0);c.fill(sx+3,sy+6,6,6,0xFF3D3740);c.fill(sx+4,sy+8,4,3,0xFFF1AB77);c.fill(sx+5,sy+7,2,4,0xFFFFD498);}
            case ANVIL -> {c.fill(sx-2,sy+2,16,3,0xFFBBCBCB);c.fill(sx+3,sy+5,6,4,0xFF748893);c.fill(sx,sy+9,12,3,0xFF97A9AE);}
            case SHRINE -> {c.fill(sx-8,sy+9,28,3,0xFF758E95);c.fill(sx-5,sy+6,22,3,0xFF98AEB0);c.fill(sx+1,sy-1,10,7,0xFF829B99);diamond(c,sx+6,sy-7,5,0xFFB2E6DA);glow(c,sx+6,sy-7,0x108FD6DA);}
            case CHEST -> {c.fill(sx-1,sy+3,14,9,0xFF976B47);c.fill(sx-1,sy+3,14,3,0xFFDFC494);c.fill(sx+5,sy+5,2,4,0xFFFFE1A1);c.fill(sx+1,sy+7,2,5,0xFFBA9E72);c.fill(sx+9,sy+7,2,5,0xFFBA9E72);}
            case BUSH -> {disk(c,sx+6,sy+8,6,0xFF679B68);c.fill(sx+2,sy+6,2,2,0xFFDF94A4);c.fill(sx+8,sy+4,2,2,0xFFEDA2A7);c.fill(sx+6,sy+9,2,2,0xFFF5C2B2);}
            default -> { }
        }
    }
    private void enemy(SceneCanvas c,World w,World.Enemy e,int x,int y,int tick) {
        if(art!=null) {
            if(e.kind==3) {
                c.draw(art.ship.frame(5),x,y,SceneDraw.plain().withScale(.6f).withFlipX(w.enemyFlip(e)));
                c.draw(art.ship.frame(e.hit>0?2:0),x,y-10,SceneDraw.plain().withScale(.6f).withFlipX(w.enemyFlip(e)));
                if(e.hit>0)c.fill(x-12,y-18,24,30,0x44FFFFFF);
            }else {
                SceneSpriteSet set=enemyArt.sprites[e.kind];
                int frame=enemyArt.frame(e,tick);
                art.sprite(c,set,frame,x,y+6,.6f,w.enemyFlip(e));
                if(e.type()==EnemyType.ORBINAUT)for(int n=0;n<4;n++) {
                    double angle=e.timer*.035+n*Math.PI/2;
                    art.sprite(c,set,1,x+(float)(Math.cos(angle)*11),y+6+(float)(Math.sin(angle)*11),.6f,false);
                }
            }
            if(e.hp<e.maxHp){c.fill(x-11,y-24,22,2,INK);c.fill(x-11,y-24,22*e.hp/e.maxHp,2,GOLD);}
            return;
        }
        int color=e.hit>0?0xFFFFF7D8:e.kind==3?new int[]{0xFF92B693,0xFFA5CCD7,0xFFD79177}[Math.max(0,e.shrine)]:e.kind==1?0xFFB1ACD0:e.kind==2?0xFFA5C5C8:0xFF8EB9A3;
        if(e.kind==3) {
            glow(c,x,y,0x188CDDCF);diamond(c,x,y,19,shade(color,.62));diamond(c,x,y,13,color);
            for(int n=0;n<4;n++) {double a=tick*.02+n*Math.PI*.5;diamond(c,x+(int)(Math.cos(a)*25),y+(int)(Math.sin(a)*25),4,color);}
            c.fill(x-7,y-3,5,3,INK);c.fill(x+3,y-3,5,3,INK);c.fill(x-4,y-3,2,2,0xFFEEE9C4);c.fill(x+3,y-3,2,2,0xFFEEE9C4);
        }else if(e.kind==1) {
            int flap=(tick/7)%2*4;c.fill(x-5,y-4,10,9,color);c.fill(x-13,y-7+flap,8,4,color);c.fill(x+5,y-7+flap,8,4,color);
            c.fill(x-3,y-2,2,2,GOLD);c.fill(x+2,y-2,2,2,GOLD);
        }else {
            int bounce=e.vy<0?2:0;c.fill(x-7,y-4-bounce,14,10+bounce,color);c.fill(x-4,y-7-bounce,8,3,color);
            c.fill(x-4,y-4-bounce,3,2,shade(color,1.12));c.fill(x-4,y,2,2,INK);c.fill(x+3,y,2,2,INK);
        }
        if(e.hp<e.maxHp){c.fill(x-11,y-14,22,2,INK);c.fill(x-11,y-14,22*e.hp/e.maxHp,2,GOLD);}
    }
    private void disk(SceneCanvas c,int x,int y,int radius,int color) {
        for(int row=-radius;row<=radius;row++){int length=(int)Math.sqrt(radius*radius-row*row);c.fill(x-length,y+row,length*2+1,1,color);}
    }
    private void diamond(SceneCanvas c,int x,int y,int radius,int color) {
        for(int row=-radius;row<=radius;row++){int r=radius-Math.abs(row);c.fill(x-r,y+row,r*2+1,1,color);}
    }
    private void glow(SceneCanvas c,int x,int y,int color) {disk(c,x,y,24,color);disk(c,x,y,15,color);disk(c,x,y,8,color);}
    public void title(SceneCanvas c,int cursor,boolean saved,int tick,String status) {
        c.fill(0,0,c.width(),224,0x45202B38);
        center(c,"S3K EXPLORATION & CRAFTING",c.width()/2,25,0xFFDFE8CC,1);
        center(c,"STARFALL",c.width()/2+2,44,0xFF213F48,4);
        center(c,"STARFALL",c.width()/2,42,0xFFF1D6A3,4);
        center(c,"F R O N T I E R",c.width()/2,77,0xFFBAE0CA,2);
        String[] choices={saved?"CONTINUE YOUR WORLD":"BEGIN YOUR ADVENTURE","NEW FRONTIER","FIELD GUIDE","RETURN TO OPEN GGF"};
        for(int i=0;i<4;i++) {
            if(i==cursor){box(c,c.width()/2-97,106+i*19,194,18);text(c,">",c.width()/2-88,111+i*19,GOLD);}
            center(c,choices[i],c.width()/2,111+i*19,i==cursor?GOLD:TEXT,1);
        }
        center(c,"MINE  /  CRAFT  /  BUILD  /  DISCOVER",c.width()/2,192,MINT,1);
        center(c,status.isEmpty()?"ARROWS + ENTER OR CLICK TO CHOOSE":status.toUpperCase(),c.width()/2,207,TEXT,1);
    }
    public void hud(SceneCanvas c,World w,String status,int tick,boolean targeting,double cameraX,double cameraY) {
        box(c,8,7,126,26);text(c,"VITALITY",15,12,MUTED);text(c,w.hp+" / "+w.maxHp,69,12,TEXT);
        c.fill(15,23,110,4,0xFF34434C);c.fill(15,23,110*w.hp/w.maxHp,4,0xFFDBA39D);
        c.fill(15,29,110,1,0xFF34434C);c.fill(15,29,110*w.mana/100,1,0xFF9ACDDA);
        box(c,142,7,208,26);text(c,w.biome(),148,12,TEXT);text(c,(w.sheltered()?"SHELTERED":"DAY "+(1+w.ticks/21600))+"  |  DEPTH "+Math.max(0,(int)w.y/T-w.surface((int)w.x/T)),148,23,MUTED);
        box(c,c.width()-170,7,162,30);
        text(c,w.quest<7?"QUEST "+(w.quest+1)+" / 7":"STARLIGHT RESTORED",c.width()-162,12,GOLD);
        text(c,w.quest<7?w.content.questNames.get(w.quest).toUpperCase():"KEEP EXPLORING AND BUILDING",c.width()-162,23,TEXT);
        int start=(c.width()-240)/2;
        for(int i=0;i<8;i++) {
            c.fill(start+i*30+2,182,28,28,i==w.slot?GOLD:EDGE);c.fill(start+i*30+3,183,26,26,INK);
            icon(c,w.hotbar[i],start+i*30+16,195,1);text(c,""+(i+1),start+i*30+5,184,MUTED);
            int n=w.count(w.hotbar[i]);if(!w.hotbar[i].unique())text(c,""+n,start+i*30+5,202,n==0?0xFFB27678:TEXT);
        }
        if(w.noticeTicks>0) {
            int width=font.width(w.notice.toUpperCase())+16;
            box(c,(c.width()-width)/2,163,width,14);center(c,w.notice.toUpperCase(),c.width()/2,167,TEXT,1);
        }
        text(c,"[TAB] PACK  [C] CRAFT  [J] QUESTS  [M] MAP",8,214,MUTED);
        String item=w.selected().label.toUpperCase();text(c,item,c.width()-font.width(item)-8,214,GOLD);
        if(targeting) {
            int tx=(int)(w.aimX*T-cameraX),ty=(int)(w.aimY*T-cameraY),color=w.reachable(w.aimX,w.aimY)?0xFFDFD5B0:0xFFBC797D;
            c.fill(tx,ty,4,1,color);c.fill(tx,ty,1,4,color);c.fill(tx+8,ty,4,1,color);c.fill(tx+11,ty,1,4,color);
            c.fill(tx,ty+11,4,1,color);c.fill(tx,ty+8,1,4,color);c.fill(tx+8,ty+11,4,1,color);c.fill(tx+11,ty+8,1,4,color);
            if(w.mining>0){c.fill(tx,ty+14,12,2,INK);c.fill(tx,ty+14,Math.min(12,w.mining/2),2,GOLD);}
        }
        for(World.Enemy e:w.enemies)if(e.kind==3) {
            box(c,c.width()/2-110,42,220,22);center(c,new String[]{"EGGMAN / JUNGLE SENTINEL","EGGMAN / RUINS SENTINEL","EGGMAN / CORE SENTINEL"}[e.shrine],c.width()/2,46,GOLD,1);
            c.fill(c.width()/2-102,57,204,3,INK);c.fill(c.width()/2-102,57,204*e.hp/e.maxHp,3,0xFFDBA39D);
        }
    }
    private void overlay(SceneCanvas c,String title) {c.fill(0,0,c.width(),224,0xAA0C1826);box(c,42,30,444,175);text(c,title,54,39,GOLD);text(c,"B / BACKSPACE: CLOSE",360,39,MUTED);}
    public void crafting(SceneCanvas c,World w,int selected) {
        overlay(c,"THE WORKSHOP");
        int top=Math.max(0,Math.min(w.content.recipes.size()-10,selected-4));
        for(int i=0;i<10;i++) {
            int n=top+i;var r=w.content.recipes.get(n);
            if(n==selected)c.fill(51,53+i*13,198,12,0xFF3B5059);
            icon(c,r.item(),59,59+i*13,1);text(c,r.item().label.toUpperCase(),71,56+i*13,w.canCraft(r)?MINT:MUTED);
            if(w.canCraft(r))text(c,"+",239,56+i*13,GOLD);
        }
        c.fill(255,52,1,132,EDGE);var r=w.content.recipes.get(selected);
        icon(c,r.item(),366,72,3);center(c,r.item().label.toUpperCase(),366,96,GOLD,1);
        text(c,r.description().toUpperCase(),269,109,TEXT);text(c,"STATION: "+Content.stationName(r.station()).toUpperCase(),269,122,w.nearStation(r.station())?MINT:0xFFD4A19D);
        for(int i=0;i<r.costs().size();i++) {
            var cost=r.costs().get(i);text(c,cost.item().label.toUpperCase()+"  "+w.count(cost.item())+" / "+cost.count(),269,135+i*11,w.count(cost.item())>=cost.count()?TEXT:0xFFD4A19D);
        }
        box(c,268,166,198,18);center(c,w.canCraft(r)?"ENTER / A: CRAFT "+r.count():"RESOURCES OR STATION REQUIRED",367,172,w.canCraft(r)?MINT:MUTED,1);
        text(c,"ARROWS / WHEEL: RECIPES   + = READY TO CRAFT",54,190,MUTED);
        c.fill(250,54+(selected*126/w.content.recipes.size()),2,8,GOLD);
    }
    public void inventory(SceneCanvas c,World w,int selected) {
        overlay(c,"EXPLORER'S BACKPACK");
        for(int i=0;i<Content.Item.values().length;i++) {
            Content.Item item=Content.Item.values()[i];int x=51+i%8*53,y=55+i/8*28;
            c.fill(x,y,49,25,i==selected?GOLD:EDGE);c.fill(x+1,y+1,47,23,w.count(item)>0?0xFF293E4C:INK);
            icon(c,item,x+12,y+12,1);text(c,""+w.count(item),x+24,y+10,w.count(item)>0?TEXT:MUTED);
        }
        Content.Item item=Content.Item.values()[selected];
        text(c,item.label.toUpperCase(),54,173,item.color);text(c,"SLOT "+(w.slot+1)+"  Q / R / PAD C: SLOT",54,187,MUTED);
        box(c,260,182,208,16);center(c,"ENTER / A: ASSIGN TO HOTBAR",364,187,MINT,1);
        text(c,"H: USE SELECTED FOOD OR HEARTSTONE",260,172,MUTED);
    }
    public void journal(SceneCanvas c,World w) {
        overlay(c,"THE STARFALL JOURNAL");
        for(int i=0;i<7;i++) {
            int y=55+i*18;
            text(c,i<w.quest?"+":i==w.quest?">":".",54,y,i<w.quest?MINT:GOLD);
            text(c,(i+1)+". "+w.content.questNames.get(i).toUpperCase(),67,y,i<=w.quest?GOLD:MUTED);
            text(c,w.content.questText.get(i).toUpperCase(),67,y+9,i==w.quest?TEXT:MUTED);
        }
        text(c,"SENTINELS "+Integer.bitCount(w.wardens)+" / 3   BUILT "+w.blocksPlaced+"   FOES "+w.kills,54,190,MINT);
        if(w.won){c.fill(43,31,442,21,0xFF466558);center(c,"ANGEL ISLAND SHINES AGAIN",264,38,0xFFFFE4AE,1);}
    }
    public void map(SceneCanvas c,World w,int centerX,int centerY) {
        c.fill(0,0,c.width(),224,0xEE132331);text(c,"ATLAS OF THE FRONTIER",12,10,GOLD);text(c,"M / B: CLOSE",430,10,MUTED);
        double scale=1.8;int left=33,top=52,columns=256,rows=82;
        int startX=Math.max(0,Math.min(w.width-columns,centerX-columns/2));
        int startY=Math.max(0,Math.min(w.height-rows,centerY-rows/2));
        text(c,"WORLD "+w.width+" X "+w.height+"   LOCAL DISCOVERIES",33,24,MINT);
        for(int px=0;px<461;px++)c.fill(left+px,36,1,8,w.surfaceBiome(px*w.width/461).color);
        c.fill(left+(int)(w.x/T*461/w.width),34,2,12,TEXT);
        c.fill(left,top,461,148,0xFF132331);
        for(int dy=0;dy<rows;dy++)for(int dx=0;dx<columns;dx++) {
            int tx=startX+dx,ty=startY+dy;
            if(w.seen[ty*w.width+tx]>0||ty<w.surface(tx)) {
                int t=w.tile(tx,ty);c.fill(left+(int)(dx*scale),top+(int)(dy*scale),2,2,
                        t==AIR?shade(Biome.at(w,tx,ty).sky,.65):solid(t)&&t!=PLANK?Biome.at(w,tx,ty).color:World.color(t));
            }
        }
        for(int i=0;i<3;i++)if(w.shrineX[i]>=startX&&w.shrineX[i]<startX+columns
                &&w.shrineY[i]>=startY&&w.shrineY[i]<startY+rows
                &&w.seen[(w.shrineY[i]-1)*w.width+w.shrineX[i]]>0)
            diamond(c,left+(int)((w.shrineX[i]-startX)*scale),top+(int)((w.shrineY[i]-startY)*scale),3,(w.wardens&(1<<i))!=0?MINT:GOLD);
        if(40>=startX&&40<startX+columns&&w.surface(40)>=startY&&w.surface(40)<startY+rows)
            diamond(c,left+(int)((40-startX)*scale),top+(int)((w.surface(40)-startY)*scale),3,MINT);
        if(w.x/T>=startX&&w.x/T<startX+columns&&w.y/T>=startY&&w.y/T<startY+rows)
            c.fill(left+(int)((w.x/T-startX)*scale)-1,top+(int)((w.y/T-startY)*scale)-2,3,5,TEXT);
        center(c,"ARROWS: PAN   WHITE: YOU   GREEN: CAMP   GOLD: SHRINE",c.width()/2,211,MUTED,1);
    }
    public void pause(SceneCanvas c,int cursor) {
        c.fill(0,0,c.width(),224,0xAA132331);box(c,c.width()/2-112,25,224,170);center(c,"A MOMENT AT CAMP",c.width()/2,33,GOLD,1);
        String[] choices={"RETURN TO THE FRONTIER","BACKPACK","CRAFTING","QUEST JOURNAL","WORLD MAP","RECALL TO CAMP","FIELD GUIDE","SAVE AND RETURN TO TITLE"};
        for(int i=0;i<choices.length;i++) {
            if(i==cursor)c.fill(c.width()/2-104,46+i*17,208,15,0xFF3B5059);
            center(c,choices[i],c.width()/2,50+i*17,i==cursor?GOLD:TEXT,1);
        }
    }
    public void help(SceneCanvas c) {
        overlay(c,"FIELD GUIDE");
        String[] lines={"ARROWS / A,D: RUN   HOLD SPACE / W / PAD A: HIGH JUMP",
            "MOUSE: AIM   HOLD LEFT / F / PAD C: USE SELECTED ITEM",
            "RIGHT CLICK: BUILD   E / PAD UP: OPEN CACHE OR SHRINE",
            "1-8 / WHEEL / Q,R: HOTBAR   TAB: BACKPACK   C: CRAFT",
            "J: QUEST JOURNAL   M: MAP   H: HEAL   START / P: PAUSE",
            "LAND ON BADNIKS TO SPIN ATTACK. DOWN: DROP THROUGH.",
            "PACK: SELECT AN ITEM, Q/R/PAD C: SLOT, ENTER/A: ASSIGN",
            "PACK H: CONSUME HEARTSTONES (+20 MAX HP) OR FOOD",
            "PLACE STATIONS THEN STAND NEAR THEM TO CRAFT",
            "BUILD A ROOF, 8 WALLS AND A LANTERN FOR A SAFE SHELTER",
            "SIGILS AWAKEN SHRINE SENTINELS. FIND SHRINES UNDERGROUND.",
            "PAUSE TO RECALL. DEATH KEEPS YOUR WORLD AND ITEMS."};
        for(int i=0;i<lines.length;i++)text(c,lines[i],54,55+i*11,i<6?TEXT:MUTED);
        text(c,"AUTOSAVES EVERY 30 SECONDS. SAVE FROM PAUSE TO LEAVE.",54,191,MINT);
    }
    public void confirmNew(SceneCanvas c) {
        c.fill(0,0,c.width(),224,0xAA142332);box(c,94,65,340,87);
        center(c,"SET OUT FOR A NEW FRONTIER?",c.width()/2,79,GOLD,1);
        center(c,"THIS REPLACES YOUR ACTIVE WORLD.",c.width()/2,96,TEXT,1);
        center(c,"NEW WORLD: 8192 X 384 TILES",c.width()/2,109,MINT,1);
        center(c,"ENTER / A: BEGIN   B / BACKSPACE: KEEP WORLD",c.width()/2,123,MUTED,1);
    }
    public void icon(SceneCanvas c,Content.Item i,int x,int y,int scale) {
        int color=i.color;
        if(art!=null&&(i==Content.Item.GEL||i==Content.Item.CRYSTAL||i==Content.Item.RELIC||i==Content.Item.SIGIL)) {
            SceneSpriteSet set=i==Content.Item.GEL?art.ring:art.emerald;
            SceneSprite sprite=set.frame(i==Content.Item.GEL?0:i==Content.Item.RELIC?0:i==Content.Item.SIGIL?1:4);
            float size=10f*scale/Math.max(sprite.width(),sprite.height());
            art.sprite(c,set,i==Content.Item.GEL?0:i==Content.Item.RELIC?0:i==Content.Item.SIGIL?1:4,x,y+5*scale,size,false);return;
        }
        if(i.tool()||i.weapon()) {
            for(int n=-4;n<=4;n++)c.fill(x+n*scale,y-n*scale,scale,scale,0xFFB29164);
            if(i==Content.Item.BOW){for(int n=-4;n<=4;n++)c.fill(x+(Math.abs(n)/2)*scale,y+n*scale,scale,scale,color);c.fill(x,y-4*scale,scale,8*scale,TEXT);}
            else if(i==Content.Item.STAFF)diamond(c,x+3*scale,y-3*scale,3*scale,color);
            else if(i==Content.Item.AXE){c.fill(x+scale,y-5*scale,5*scale,5*scale,color);c.fill(x+3*scale,y-5*scale,3*scale,scale,TEXT);}
            else if(i.weapon()) {
                for(int n=-1;n<6;n++){c.fill(x+n*scale,y-n*scale,2*scale,scale,color);}
                c.fill(x-3*scale,y-scale,5*scale,scale,GOLD);c.fill(x-scale,y-3*scale,scale,5*scale,GOLD);
            }else{c.fill(x-3*scale,y-4*scale,9*scale,2*scale,color);c.fill(x+5*scale,y-2*scale,scale,2*scale,color);}
        }else if(i==Content.Item.BENCH) {
            c.fill(x-5*scale,y-scale,10*scale,2*scale,color);c.fill(x-4*scale,y+scale,2*scale,4*scale,color);c.fill(x+3*scale,y+scale,2*scale,4*scale,color);
        }else if(i==Content.Item.FURNACE) {
            c.fill(x-4*scale,y-4*scale,8*scale,9*scale,0xFFA9A299);c.fill(x-2*scale,y,4*scale,4*scale,INK);c.fill(x-scale,y+scale,2*scale,3*scale,GOLD);
        }else if(i==Content.Item.ANVIL) {
            c.fill(x-5*scale,y-3*scale,10*scale,3*scale,color);c.fill(x-2*scale,y,4*scale,3*scale,color);c.fill(x-4*scale,y+3*scale,8*scale,scale,color);
        }else if(i==Content.Item.ARMOR) {
            c.fill(x-4*scale,y-3*scale,8*scale,7*scale,color);c.fill(x-6*scale,y-4*scale,3*scale,3*scale,color);c.fill(x+3*scale,y-4*scale,3*scale,3*scale,color);c.fill(x-scale,y-3*scale,2*scale,2*scale,INK);
        }else if(i==Content.Item.ARROW) {
            c.fill(x-5*scale,y,10*scale,scale,color);diamond(c,x+4*scale,y,2*scale,TEXT);c.fill(x-5*scale,y-2*scale,2*scale,5*scale,color);
        }else if(i==Content.Item.POTION) {
            c.fill(x-2*scale,y-5*scale,4*scale,2*scale,GOLD);c.fill(x-scale,y-3*scale,2*scale,2*scale,TEXT);c.fill(x-3*scale,y-scale,6*scale,6*scale,color);c.fill(x-2*scale,y,scale,3*scale,TEXT);
        }else if(i==Content.Item.HEART) {
            disk(c,x-2*scale,y-2*scale,3*scale,color);disk(c,x+2*scale,y-2*scale,3*scale,color);diamond(c,x,y+scale,4*scale,color);c.fill(x-3*scale,y-3*scale,2*scale,scale,TEXT);
        }else if(i==Content.Item.WALL) {
            c.fill(x-5*scale,y-4*scale,10*scale,8*scale,color);for(int n=-2;n<=2;n+=2)c.fill(x-5*scale,y+n*scale,10*scale,scale,shade(color,.7));
        }else if(i==Content.Item.TORCH){c.fill(x-scale,y-scale,2*scale,6*scale,0xFFB29164);diamond(c,x,y-3*scale,3*scale,color);}
        else if(i==Content.Item.CRYSTAL||i==Content.Item.RELIC||i==Content.Item.SIGIL||i==Content.Item.BEACON||i==Content.Item.HEART) {
            diamond(c,x,y,5*scale,color);c.fill(x-scale,y-3*scale,scale,3*scale,TEXT);
        }else if(i==Content.Item.BERRY||i==Content.Item.GEL||i==Content.Item.POTION||i==Content.Item.ACORN) {
            disk(c,x,y,4*scale,color);c.fill(x-scale,y-3*scale,2*scale,scale,TEXT);c.fill(x,y-5*scale,scale,2*scale,MINT);
        }else {c.fill(x-4*scale,y-3*scale,8*scale,7*scale,color);c.fill(x-3*scale,y-2*scale,6*scale,scale,shade(color,1.1));c.fill(x-3*scale,y+scale,6*scale,scale,shade(color,.7));}
    }
}
