package starfall;

import com.openggf.mods.scene.*;
import com.openggf.mods.scene.art.AnimationSampling;
import java.util.Optional;

/** Scene orchestration. Debug commands: play, craft, inventory, journal, map, cavern, warden, victory. */
public final class FrontierScene implements ModScene,DebuggableScene {
    private final byte[] font;
    private FrontierView view;
    private SceneSpriteSet sonic;
    private SceneContext context;
    private World world, saved;
    private String screen="TITLE",returnScreen="PLAY";
    private int cursor,recipe,inventoryCursor,presentation,saveTimer,mapX,mapY;
    private boolean dirty,debug,existingSave,mouseAim;
    private int currentMusic=-1;
    private WorldSize worldSize=WorldSize.MEDIUM;
    private double cameraX,cameraY;
    private String status="";
    public FrontierScene(byte[] font) {this.font=font.clone();}
    public World world(){return world;}
    public String screen(){return screen;}
    @Override public void enter(SceneContext ctx) {
        context=ctx;view=new FrontierView(font,ctx.art().rom());existingSave=ctx.storage().read("world.sav").isPresent();
        if(ctx.art().rom()!=null)sonic=ctx.art().rom().character("sonic");
        Optional<World> primary=WorldSaves.read(ctx.storage(),"world.sav").flatMap(SaveCodec::decode);
        saved=primary.orElseGet(()->WorldSaves.read(ctx.storage(),"world-backup.sav").flatMap(SaveCodec::decode).orElse(null));
        if(primary.isEmpty()&&saved!=null)status="Recovered your backup save.";
        else if(saved==null&&ctx.storage().read("world.sav").isPresent())status="Save unreadable. Original files preserved.";
        if(saved!=null)worldSize=WorldSize.forWidth(saved.width);
        world=new World(0x57A2FA11L);snapCamera(ctx);music(ctx,0x2F);
    }
    @Override public void update(SceneContext ctx) {
        presentation++;
        if(screen.equals("TITLE")) {titleInput(ctx);return;}
        if(screen.equals("NEW")) {
            newWorldInput(ctx);return;
        }
        if(screen.equals("HELP")) {if(back(ctx)||confirm(ctx)||ctx.keyPressed(SceneKeys.H))screen=returnScreen;return;}
        if(!screen.equals("PLAY")) {panelInput(ctx);return;}
        if(ctx.buttonPressed(SceneButtons.START)||ctx.keyPressed(SceneKeys.ESCAPE)||ctx.keyPressed(SceneKeys.P)) {screen="PAUSE";cursor=0;return;}
        if(ctx.keyPressed(SceneKeys.C)||ctx.buttonPressed(SceneButtons.B)){screen="CRAFT";return;}
        if(ctx.keyPressed(SceneKeys.TAB)||ctx.keyPressed(SceneKeys.I)){screen="INVENTORY";return;}
        if(ctx.keyPressed(SceneKeys.J)){screen="JOURNAL";return;}
        if(ctx.keyPressed(SceneKeys.M)){openMap();return;}
        if(ctx.keyPressed(SceneKeys.F1)){help("PLAY");return;}
        if(ctx.mouse().leftPressed()) {
            if(ctx.mouse().over(ctx.width()-170,7,162,30)){screen="JOURNAL";return;}
            if(ctx.mouse().over(8,210,212,14)) {
                int mx=ctx.mouse().x();screen=mx<63?"INVENTORY":mx<115?"CRAFT":mx<174?"JOURNAL":"MAP";if(screen.equals("MAP"))openMap();return;
            }
        }
        for(int i=0;i<8;i++)if(ctx.keyPressed(SceneKeys.DIGIT_1+i))world.slot=i;
        if(ctx.keyPressed(SceneKeys.Q))world.slot=(world.slot+7)%8;
        if(ctx.keyPressed(SceneKeys.R))world.slot=(world.slot+1)%8;
        if(ctx.mouse().wheel()!=0)world.slot=Math.floorMod(world.slot-Integer.signum(ctx.mouse().wheel()),8);
        if(ctx.mouse().leftPressed()&&ctx.mouse().over((ctx.width()-240)/2,181,240,28)) {
            world.slot=(ctx.mouse().x()-(ctx.width()-240)/2)/30;return;
        }
        int move=(ctx.buttonDown(SceneButtons.RIGHT)||ctx.keyDown(SceneKeys.D)?1:0)-(ctx.buttonDown(SceneButtons.LEFT)||ctx.keyDown(SceneKeys.A)?1:0);
        boolean jump=ctx.buttonPressed(SceneButtons.A)||ctx.keyPressed(SceneKeys.W)||ctx.keyPressed(SceneKeys.SPACE);
        boolean drop=ctx.buttonDown(SceneButtons.DOWN)||ctx.keyDown(SceneKeys.S);
        int oldHp=world.hp,oldKills=world.kills,oldQuest=world.quest;
        boolean held=ctx.buttonDown(SceneButtons.A)||ctx.keyDown(SceneKeys.W)||ctx.keyDown(SceneKeys.SPACE);
        boolean takeoff=jump&&world.grounded;
        world.step(new World.Input(move,jump,drop,held));dirty=true;saveTimer++;
        if(takeoff)ctx.audio().playSfx(0x62);
        cameraX+=(targetCameraX(ctx)-cameraX)*.12;cameraY+=(targetCameraY()-cameraY)*.12;
        int tx=(int)(world.x/World.T)+(world.facingLeft?-2:2),ty=(int)(world.y/World.T);
        if(drop){tx=(int)(world.x/World.T);ty=(int)((world.y+14)/World.T);}
        if(ctx.buttonDown(SceneButtons.UP)){tx=(int)(world.x/World.T);ty=(int)((world.y-22)/World.T);}
        if(ctx.mouse().inside()&&(ctx.mouse().moved()||ctx.mouse().leftDown()||ctx.mouse().rightDown()))mouseAim=true;
        if(ctx.buttonDown(SceneButtons.C))mouseAim=false;
        if(mouseAim&&ctx.mouse().inside()&&ctx.mouse().y()>32&&ctx.mouse().y()<180) {
            tx=(int)Math.floor((ctx.mouse().x()+cameraX)/World.T);ty=(int)Math.floor((ctx.mouse().y()+cameraY)/World.T);
        }
        tx=Math.max(1,Math.min(world.width-2,tx));ty=Math.max(0,Math.min(world.height-3,ty));world.aimX=tx;world.aimY=ty;
        if(ctx.keyPressed(SceneKeys.E)||ctx.buttonPressed(SceneButtons.UP))interact(ctx,tx,ty);
        if(ctx.keyPressed(SceneKeys.H)&&world.heal())ctx.audio().playSfx(0x33);
        boolean mouseWorld=ctx.mouse().inside()&&ctx.mouse().y()>32&&ctx.mouse().y()<180;
        if(ctx.buttonDown(SceneButtons.C)||ctx.keyDown(SceneKeys.F)||mouseWorld&&ctx.mouse().leftDown()) {
            if(world.use(tx,ty)){ctx.audio().playSfx(world.selected().weapon()?0x42:0x3D);}
        }else {world.mining=0;}
        if(mouseWorld&&ctx.mouse().rightDown()&&world.place(tx,ty,world.selected()))ctx.audio().playSfx(0x9E);
        if(world.hp<oldHp)ctx.audio().playSfx(0x37);
        if(world.kills>oldKills||world.quest>oldQuest)ctx.audio().playSfx(0x33);
        music(ctx,world.musicId());view.updateBackdrop(world);
        if(saveTimer>=1800)save(ctx);
    }
    private void interact(SceneContext ctx,int tx,int ty) {
        boolean ok=world.interact(tx,ty);
        if(!ok) {
            outer:for(int a=-3;a<=3;a++)for(int b=-3;b<=3;b++) {
                int px=(int)world.x/World.T+a,py=(int)world.y/World.T+b;
                int t=world.tile(px,py);
                if((t==World.CHEST||t==World.BUSH||t==World.SHRINE)&&world.interact(px,py)){ok=true;break outer;}
            }
        }
        if(ok)ctx.audio().playSfx(0x33);
    }
    private void titleInput(SceneContext ctx) {
        if(up(ctx))cursor=Math.floorMod(cursor-1,4);if(down(ctx))cursor=(cursor+1)%4;
        int picked=confirm(ctx)?cursor:-1;
        for(int i=0;i<4;i++)if(ctx.mouse().over(ctx.width()/2-96,108+i*19,192,17)) {
            if(ctx.mouse().lastInputWasMouse())cursor=i;if(ctx.mouse().leftPressed())picked=i;
        }
        switch(picked) {
            case 0 -> {if(saved!=null){world=saved;saved=null;debug=false;screen="PLAY";dirty=false;snapCamera(ctx);music(ctx,world.musicId());}else screen="NEW";}
            case 1 -> screen="NEW";
            case 2 -> help("TITLE");case 3 -> ctx.exitToMasterTitle();default -> { }
        }
    }
    private void newWorldInput(SceneContext ctx) {
        if(back(ctx)||ctx.keyPressed(SceneKeys.ESCAPE)){screen="TITLE";return;}
        int change=(right(ctx)||down(ctx)?1:0)-(left(ctx)||up(ctx)?1:0);
        if(ctx.mouse().wheel()!=0)change-=Integer.signum(ctx.mouse().wheel());
        WorldSize[] sizes=WorldSize.values();
        worldSize=sizes[Math.floorMod(worldSize.ordinal()+change,sizes.length)];
        for(int i=0;i<sizes.length;i++)if(ctx.mouse().leftPressed()&&ctx.mouse().over(60,74+i*24,408,22))worldSize=sizes[i];
        if(confirm(ctx)||ctx.mouse().leftPressed()&&ctx.mouse().over(ctx.width()/2-90,168,180,19))newWorld(ctx);
    }
    private void newWorld(SceneContext ctx) {
        world=new World(java.util.concurrent.ThreadLocalRandom.current().nextLong(),worldSize);screen="PLAY";
        saved=null;debug=false;dirty=true;saveTimer=0;snapCamera(ctx);save(ctx);music(ctx,world.musicId());
    }
    private void openMap(){screen="MAP";mapX=(int)(world.x/World.T);mapY=(int)(world.y/World.T);}
    private void help(String from){returnScreen=from;screen="HELP";}
    private void panelInput(SceneContext ctx) {
        if(screen.equals("PAUSE")) {
            if(back(ctx)||ctx.buttonPressed(SceneButtons.START)||ctx.keyPressed(SceneKeys.P)){screen="PLAY";return;}
            if(up(ctx))cursor=Math.floorMod(cursor-1,8);if(down(ctx))cursor=(cursor+1)%8;
            int picked=confirm(ctx)?cursor:-1;
            for(int i=0;i<8;i++)if(ctx.mouse().over(ctx.width()/2-100,47+i*17,200,15)) {
                if(ctx.mouse().lastInputWasMouse())cursor=i;if(ctx.mouse().leftPressed())picked=i;
            }
            switch(picked) {
                case 0 -> screen="PLAY";case 1 -> screen="INVENTORY";case 2 -> screen="CRAFT";
                case 3 -> screen="JOURNAL";case 4 -> openMap();
                case 5 -> {world.recall();snapCamera(ctx);music(ctx,world.musicId());screen="PLAY";dirty=true;}
                case 6 -> help("PAUSE");
                case 7 -> {if(save(ctx)||!dirty){saved=SaveCodec.decode(SaveCodec.encode(world)).orElse(null);screen="TITLE";cursor=0;music(ctx,0x2F);}}
                default -> { }
            }
            return;
        }
        if(back(ctx)||ctx.buttonPressed(SceneButtons.START)||ctx.keyPressed(SceneKeys.ESCAPE)
                ||screen.equals("CRAFT")&&ctx.keyPressed(SceneKeys.C)
                ||screen.equals("INVENTORY")&&(ctx.keyPressed(SceneKeys.TAB)||ctx.keyPressed(SceneKeys.I))
                ||screen.equals("MAP")&&ctx.keyPressed(SceneKeys.M)||screen.equals("JOURNAL")&&ctx.keyPressed(SceneKeys.J)) {screen="PLAY";return;}
        if(screen.equals("MAP")) {
            if(left(ctx))mapX-=16;if(right(ctx))mapX+=16;
            if(up(ctx))mapY-=8;if(down(ctx))mapY+=8;
            mapX+=ctx.mouse().wheel()*64;
            if(ctx.mouse().leftPressed()&&ctx.mouse().over(33,34,461,12))mapX=(ctx.mouse().x()-33)*world.width/461;
            mapX=Math.max(0,Math.min(world.width-1,mapX));mapY=Math.max(0,Math.min(world.height-1,mapY));
        }else if(screen.equals("CRAFT")) {
            if(up(ctx)||ctx.mouse().wheel()>0)recipe=Math.floorMod(recipe-1,world.content.recipes.size());
            if(down(ctx)||ctx.mouse().wheel()<0)recipe=(recipe+1)%world.content.recipes.size();
            int top=Math.max(0,Math.min(world.content.recipes.size()-10,recipe-4));
            for(int i=0;i<10;i++)if(ctx.mouse().over(54,55+i*13,192,12)&&ctx.mouse().leftPressed())recipe=top+i;
            if(confirm(ctx)||ctx.mouse().leftPressed()&&ctx.mouse().over(270,166,190,18)) {
                if(world.craft(recipe)){dirty=true;ctx.audio().playSfx(0x33);}
            }
        }else if(screen.equals("INVENTORY")) {
            int n=Content.Item.values().length;
            if(left(ctx))inventoryCursor=Math.floorMod(inventoryCursor-1,n);
            if(right(ctx))inventoryCursor=(inventoryCursor+1)%n;
            if(up(ctx))inventoryCursor=Math.floorMod(inventoryCursor-8,n);
            if(down(ctx))inventoryCursor=(inventoryCursor+8)%n;
            for(int i=0;i<n;i++)if(ctx.mouse().over(51+(i%8)*53,55+(i/8)*28,49,25)&&ctx.mouse().leftPressed())inventoryCursor=i;
            for(int i=0;i<8;i++)if(ctx.keyPressed(SceneKeys.DIGIT_1+i))world.slot=i;
            if(ctx.keyPressed(SceneKeys.Q))world.slot=(world.slot+7)%8;if(ctx.keyPressed(SceneKeys.R))world.slot=(world.slot+1)%8;
            if(ctx.buttonPressed(SceneButtons.C))world.slot=(world.slot+1)%8;
            if(ctx.buttonPressed(SceneButtons.A)||ctx.keyPressed(SceneKeys.ENTER)||ctx.mouse().leftPressed()&&ctx.mouse().over(260,182,208,16)) {
                Content.Item item=Content.Item.values()[inventoryCursor];
                if(world.count(item)>0){world.equip(item);dirty=true;ctx.audio().playSfx(0x33);}else world.say("You have not collected this yet.");
            }
            if(ctx.keyPressed(SceneKeys.H)){if(world.consume(Content.Item.values()[inventoryCursor])){dirty=true;ctx.audio().playSfx(0x33);}}
        }
    }
    private boolean save(SceneContext ctx) {
        if(debug)return true;
        String data=SaveCodec.encode(world);
        if(!WorldSaves.write(ctx.storage(),data)){status="SAVE FAILED. Retry from the pause menu.";world.say(status);return false;}
        dirty=false;existingSave=true;saveTimer=0;status="WORLD SAVED";return true;
    }
    private void music(SceneContext ctx,int id){if(currentMusic!=id){currentMusic=id;ctx.audio().playMusic(id);}}
    private double targetCameraX(SceneContext ctx){return Math.max(0,Math.min(world.width*World.T-ctx.width(),world.x-ctx.width()*.46));}
    private double targetCameraY(){return Math.max(0,Math.min(world.height*World.T-224,world.y-109));}
    private void snapCamera(SceneContext ctx){cameraX=targetCameraX(ctx);cameraY=targetCameraY();view.snapBackdrop(world);}
    private boolean confirm(SceneContext ctx){return ctx.buttonPressed(SceneButtons.A|SceneButtons.C)||ctx.keyPressed(SceneKeys.ENTER);}
    private boolean back(SceneContext ctx){return ctx.buttonPressed(SceneButtons.B)||ctx.keyPressed(SceneKeys.BACKSPACE)||ctx.mouse().rightPressed();}
    private boolean up(SceneContext ctx){return ctx.buttonRepeated(SceneButtons.UP);}
    private boolean down(SceneContext ctx){return ctx.buttonRepeated(SceneButtons.DOWN);}
    private boolean left(SceneContext ctx){return ctx.buttonRepeated(SceneButtons.LEFT);}
    private boolean right(SceneContext ctx){return ctx.buttonRepeated(SceneButtons.RIGHT);}
    @Override public void draw(SceneContext ctx,SceneCanvas canvas) {
        view.world(canvas,world,cameraX,cameraY,presentation);
        if(sonic!=null&&(!screen.equals("PLAY")||world.invulnerable%8<5)) {
            int anim=screen.equals("TITLE")?5:!world.grounded?2:Math.abs(world.vx)>4?1:Math.abs(world.vx)>.2?0:5;
            SceneSprite pose=AnimationSampling.frame(sonic,anim,world.ticks,new AnimationSampling.Timing(0,30,Math.max(1,8-(int)Math.abs(world.vx)),1));
            if(pose!=null)canvas.draw(pose,(float)(world.x-cameraX),(float)(world.y+10-cameraY-(pose.height()-pose.originY())*.65),
                    SceneDraw.plain().withScale(.65f).withFlipX(world.facingLeft));
        }
        if(screen.equals("TITLE"))view.title(canvas,cursor,saved!=null,presentation,status);
        else if(screen.equals("NEW"))view.confirmNew(canvas,worldSize,saved!=null||existingSave);
        else {
            view.hud(canvas,world,status,presentation,screen.equals("PLAY"),cameraX,cameraY);
            switch(screen) {
                case "CRAFT" -> view.crafting(canvas,world,recipe);
                case "INVENTORY" -> view.inventory(canvas,world,inventoryCursor);
                case "JOURNAL" -> view.journal(canvas,world);
                case "MAP" -> view.map(canvas,world,mapX,mapY);
                case "PAUSE" -> view.pause(canvas,cursor);
                case "HELP" -> view.help(canvas);
                default -> { }
            }
        }
        if(screen.equals("HELP")&&returnScreen.equals("TITLE"))view.help(canvas);
    }
    @Override public void exit(SceneContext ctx){if(dirty&&world!=null)save(ctx);}
    @Override public boolean debugJump(String command) {
        boolean priorDebug=debug;debug=true;
        boolean showEnemy=command.startsWith("enemy-biome-");if(showEnemy)command=command.substring(6);
        if(command.equals("play")){screen="PLAY";snapCamera(context);return true;}
        if(command.equals("craft")||command.equals("inventory")||command.equals("journal")||command.equals("map")) {
            screen=command.toUpperCase();if(screen.equals("MAP"))openMap();return true;
        }
        if(command.equals("cavern")||command.equals("warden")) {
            world.x=world.shrineX[0]*World.T-24;world.y=(world.shrineY[0]-2)*World.T;world.hp=world.maxHp=160;
            for(Content.Item i:Content.Item.values())world.add(i,30);
            world.hotbar[0]=Content.Item.IRON_PICK;world.hotbar[2]=Content.Item.STAFF;
            if(command.equals("warden"))world.interact(world.shrineX[0],world.shrineY[0]-1);
            screen="PLAY";snapCamera(context);return true;
        }
        for(Biome biome:Biome.values())if(command.equals("biome-"+biome.name().toLowerCase(java.util.Locale.ROOT))) {
            int tx=world.geographyTile(switch(biome) {
                case ANGEL_ISLAND -> 40;case MARBLE_GARDEN -> 80;case MUSHROOM_HILL -> 112;
                case CARNIVAL_NIGHT -> 144;case ICECAP -> 176;case SANDOPOLIS -> 208;
                case LAUNCH_BASE -> 240;case HYDROCITY -> 68;case LAVA_REEF -> 215;
                case HIDDEN_PALACE -> 140;case SKY_SANCTUARY -> 116;
            });
            int ty=biome==Biome.HYDROCITY?world.surface(tx)+world.depthTiles(14):
                    biome==Biome.LAVA_REEF?world.surface(tx)+world.depthTiles(36):
                    biome==Biome.HIDDEN_PALACE?world.surface(tx)+world.depthTiles(48):
                    biome==Biome.SKY_SANCTUARY?world.surface(tx)-world.depthTiles(19):world.surface(tx)-1;
            world.x=tx*World.T+6;world.y=ty*World.T-1;world.vx=world.vy=0;
            world.enemies.clear();world.shots.clear();
            for(int a=-8;a<=8;a++)for(int b=-3;b<=0;b++)world.set(tx+a,ty+b,World.AIR);
            for(int a=-8;a<=8;a++)world.set(tx+a,ty+1,World.STONE);
            if(showEnemy) {
                EnemyType type=EnemyType.forBiome(biome,0);
                world.enemies.add(new World.Enemy(world.x+36,world.y+(type.flying()?-18:7),type.ordinal()));
            }
            screen="PLAY";snapCamera(context);music(context,world.musicId());return true;
        }
        if(command.equals("victory")){world.wardens=7;world.won=true;world.quest=7;screen="JOURNAL";return true;}
        debug=priorDebug;return false;
    }
}
