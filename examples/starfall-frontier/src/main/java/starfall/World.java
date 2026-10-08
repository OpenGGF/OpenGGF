package starfall;

import java.util.ArrayList;
import java.util.List;
import static starfall.Content.Item.*;

/** A creator-owned tile world, independent of stock zone geometry and gameplay clocks. */
public final class World {
    public static final int W=256, H=96, T=12;
    public static final int AIR=0, DIRT=1, GRASS=2, STONE=3, COPPER=4, IRON=5, CRYSTAL=6,
            LOG=7, LEAVES=8, PLANK=9, PLATFORM=10, TORCH=11, BENCH=12, FURNACE=13,
            ANVIL=14, SHRINE=15, CHEST=16, BUSH=17, BEDROCK=18, SNOW=19, EMBER=20;
    public final Content content=new Content();
    public final byte[] tiles=new byte[W*H], walls=new byte[W*H], seen=new byte[W*H];
    public final int[] inventory=new int[Content.Item.values().length], gathered=new int[inventory.length];
    public final Content.Item[] hotbar={PICK,AXE,SWORD,WOOD,Content.Item.TORCH,Content.Item.PLATFORM,WALL,BERRY};
    public final List<Enemy> enemies=new ArrayList<>();
    public final List<Shot> shots=new ArrayList<>();
    public final List<Particle> particles=new ArrayList<>();
    public final int[] shrineX={68,140,215}, shrineY={52,62,73};
    public long seed, randomState, ticks;
    public double x,y,vx,vy;
    public int hp=100,maxHp=100,mana=100,slot,quest,wardens,kills,blocksPlaced,woodChopped,
            invulnerable,actionCooldown,healCooldown,respawns,aimX,aimY,miningX=-1,miningY=-1,mining;
    public boolean grounded, facingLeft, won;
    public String notice="Welcome to the frontier. Chop a tree to begin.";
    public int noticeTicks=300;
    public record Input(int move,boolean jump,boolean drop,boolean jumpHeld) {
        public Input(int move,boolean jump,boolean drop){this(move,jump,drop,jump);}
    }
    public static final class Enemy {
        public double x,y,vx,vy;
        public int kind,hp,maxHp,timer,hit,shrine=-1;
        public Enemy(double x,double y,int kind) { this.x=x;this.y=y;this.kind=kind; hp=maxHp=kind==3?240:kind==2?45:kind==1?32:24; }
    }
    public static final class Shot {
        public double x,y,vx,vy;
        public int damage,life=100;
        public boolean hostile,magic;
        public Shot(double x,double y,double vx,double vy,int damage,boolean hostile,boolean magic) {
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.damage=damage;this.hostile=hostile;this.magic=magic;
        }
    }
    public static final class Particle {
        public double x,y,vx,vy;
        public int color,life=30;
        public String text;
        public Particle(double x,double y,double vx,double vy,int color,String text) {
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.color=color;this.text=text;
        }
    }
    public World(long seed) { this(seed,true); }
    World(long seed,boolean generate) {
        this.seed=seed;randomState=seed==0?1:seed;
        if(generate) {
            generate();x=40*T+6;y=surface(40)*T-12;
            add(PICK,1);add(AXE,1);add(SWORD,1);add(Content.Item.TORCH,8);add(BERRY,6);add(GEL,3);add(ACORN,3);
            aimX=42;aimY=surface(42)-1;reveal();
        }
    }
    public int random(int bound) {
        randomState^=randomState<<13;randomState^=randomState>>>7;randomState^=randomState<<17;
        return (int)Long.remainderUnsigned(randomState,bound);
    }
    public int surface(int tx) {
        if(tx>=32&&tx<=51) return 29;
        return 29+(int)Math.round(Math.sin(tx*.095)*2+Math.sin(tx*.031)*3);
    }
    private void generate() {
        for(int tx=0;tx<W;tx++) for(int ty=0;ty<H;ty++) {
            int s=surface(tx),v=AIR;
            if(ty>=s) {
                v=ty==s?GRASS:ty<s+5?DIRT:STONE;
                if(ty>s+5) {
                    double cave=Math.sin(tx*.18+Math.sin(ty*.12)*3)+Math.cos(ty*.21+Math.sin(tx*.08)*2);
                    if(cave>1.0) v=AIR;
                    else {
                        int ore=random(1000);
                        if(ore<60&&ty>s+5) v=COPPER;
                        if(ore<45&&ty>s+15) v=IRON;
                        if(ore<35&&ty>57) v=CRYSTAL;
                        walls[ty*W+tx]=2;
                    }
                }
            }
            if(tx==0||tx==W-1||ty>=H-2) v=BEDROCK;
            set(tx,ty,v);
        }
        for(int tx=6;tx<W-6;tx+=5+random(5)) {
            if(tx>=36&&tx<=51) continue;
            tree(tx,surface(tx)-1,4+random(4));
        }
        for(int tx=9;tx<W-9;tx+=9+random(7)) if((tx<36||tx>47)&&tile(tx,surface(tx)-1)==AIR) set(tx,surface(tx)-1,BUSH);
        // A gently lit starter descent; one-way platforms make the surface reachable again.
        for(int ty=29;ty<=54;ty++) {
            for(int tx=48;tx<=51;tx++) set(tx,ty,AIR);
            if(ty%4==1) { set(48,ty,PLATFORM);set(49,ty,PLATFORM);set(51,ty-1,TORCH); }
        }
        for(int tx=43;tx<48;tx++) for(int ty=36;ty<39;ty++) set(tx,ty,COPPER);
        for(int tx=54;tx<60;tx++) for(int ty=44;ty<48;ty++) set(tx,ty,IRON);
        for(int i=0;i<3;i++) {
            int sx=shrineX[i],sy=shrineY[i];
            for(int tx=sx-8;tx<=sx+8;tx++) for(int ty=sy-5;ty<=sy;ty++) {
                set(tx,ty,ty==sy?STONE:AIR);walls[ty*W+tx]=2;
            }
            set(sx,sy-1,SHRINE);set(sx-7,sy-2,TORCH);set(sx+7,sy-2,TORCH);
            set(sx-5,sy-1,CHEST);
            for(int tx=sx+10;tx<sx+14;tx++) for(int ty=sy+2;ty<sy+6;ty++) set(tx,ty,i==0?IRON:CRYSTAL);
        }
        // Bonus treasure rooms and heartstones are fixed to the seed, never player progress.
        for(int n=0;n<12;n++) {
            int tx=15+random(W-30),ty=40+random(43);
            for(int a=-2;a<=2;a++) for(int b=-2;b<0;b++) set(tx+a,ty+b,AIR);
            for(int a=-2;a<=2;a++) set(tx+a,ty,STONE);
            set(tx,ty-1,CHEST);
        }
    }
    private void tree(int tx,int base,int height) {
        for(int ty=base-height;ty<=base;ty++) set(tx,ty,LOG);
        for(int a=-3;a<=3;a++) for(int b=-2;b<=2;b++)
            if(tile(tx+a,base-height+b)==AIR) set(tx+a,base-height+b,LEAVES);
    }
    public boolean inside(int tx,int ty) { return tx>0&&tx<W-1&&ty>=0&&ty<H-2; }
    public int tile(int tx,int ty) { return tx<0||tx>=W||ty>=H?BEDROCK:ty<0?AIR:Byte.toUnsignedInt(tiles[ty*W+tx]); }
    void set(int tx,int ty,int tile) { if(tx>=0&&tx<W&&ty>=0&&ty<H) tiles[ty*W+tx]=(byte)tile; }
    public static boolean solid(int t) {
        return t==DIRT||t==GRASS||t==SNOW||t==EMBER||t==STONE||t==COPPER||t==IRON||t==CRYSTAL||t==PLANK||t==BEDROCK;
    }
    boolean blocked(double cx,double cy,int rx,int ry) {
        for(int tx=(int)Math.floor((cx-rx)/T);tx<=(int)Math.floor((cx+rx-.01)/T);tx++)
            for(int ty=(int)Math.floor((cy-ry)/T);ty<=(int)Math.floor((cy+ry-.01)/T);ty++) if(solid(tile(tx,ty))) return true;
        return false;
    }
    public int count(Content.Item item) { return inventory[item.ordinal()]; }
    public void add(Content.Item item,int n) {
        inventory[item.ordinal()]=Math.min(9999,inventory[item.ordinal()]+n);
        gathered[item.ordinal()]=Math.min(999999,gathered[item.ordinal()]+n);
    }
    public boolean take(Content.Item i,int n) { if(count(i)<n) return false;inventory[i.ordinal()]-=n;return true; }
    public Content.Item selected() { return hotbar[slot]; }
    public int pickTier() { return count(IRON_PICK)>0?2:count(COPPER_PICK)>0?1:0; }
    public boolean reachable(int tx,int ty) { return inside(tx,ty)&&Math.hypot(tx*T+6-x,ty*T+6-y)<=T*6; }
    public void say(String text) { notice=text;noticeTicks=240; }
    private void burst(double px,double py,int color,int n) {
        for(int i=0;i<n;i++) particles.add(new Particle(px,py,(random(100)-50)/35.0,-random(80)/30.0,color,null));
    }
    private void popup(double px,double py,String text,int color) { particles.add(new Particle(px,py,0,-.5,color,text)); }
    public void step(Input input) {
        ticks++;if(noticeTicks>0)noticeTicks--;if(invulnerable>0)invulnerable--;
        if(actionCooldown>0)actionCooldown--;if(healCooldown>0)healCooldown--;
        if(ticks%12==0) mana=Math.min(100,mana+1);
        // S3K Sonic_Move / Sonic_Jump / Sonic_JumpHeight: $600 cap, $680 jump,
        // $400 released-jump cap, $38 gravity. Creator terrain has no native slopes.
        // Ground acceleration ($18) and friction ($40) deliberately exceed native $0C
        // for precise mining/building; air control retains twice native acceleration.
        if(input.move!=0) {
            facingLeft=input.move<0;
            double acceleration=grounded?(Math.signum(vx)==-input.move?.5:24/256.0):24/256.0;
            if(Math.abs(vx)<6||Math.signum(vx)!=input.move)vx=Math.max(-6,Math.min(6,vx+input.move*acceleration));
        }else if(grounded)vx=approachZero(vx,64/256.0);
        if(input.jump&&grounded) {vy=-6.5;grounded=false;}
        if(!input.jumpHeld&&vy<-4)vy=-4;
        vy=Math.min(16,vy+56/256.0);
        movePlayer(vx,0,input.drop);grounded=false;movePlayer(0,vy,input.drop);
        if(ticks%45==0&&hp<maxHp&&(sheltered()||enemies.isEmpty())) hp=Math.min(maxHp,hp+1);
        if(ticks%210==0&&enemies.size()<18) spawnEnemy();
        updateEnemies();updateShots();if(hp<=0)respawn();updateParticles();reveal();if(ticks%30==0)advanceQuests();
    }
    private double approachZero(double value,double amount){return Math.copySign(Math.max(0,Math.abs(value)-amount),value);}
    private void movePlayer(double dx,double dy,boolean drop) {
        int steps=(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy)));if(steps==0)return;
        double sx=dx/steps,sy=dy/steps;
        for(int i=0;i<steps;i++) {
            double nx=x+sx,ny=y+sy;
            boolean hit=blocked(nx,ny,4,10);
            if(sy>0&&!drop) {
                int oldRow=(int)Math.floor((y+9.99)/T),newRow=(int)Math.floor((ny+9.99)/T);
                if(newRow>oldRow) for(int tx=(int)((nx-4)/T);tx<=(int)((nx+3.99)/T);tx++) if(tile(tx,newRow)==PLATFORM) hit=true;
            }
            if(hit) { if(dy!=0){if(sy>0)grounded=true;vy=0;}else vx=0;break; }
            x=nx;y=ny;
        }
        x=Math.max(T+5,Math.min((W-1)*T-5,x));y=Math.max(10,y);
    }
    public boolean sheltered() {
        int tx=(int)x/T,ty=(int)y/T;
        if(ty<0||ty>=H) return false;
        int n=0;for(int a=-2;a<=2;a++)for(int b=-2;b<=0;b++) if(inside(tx+a,ty+b)&&walls[(ty+b)*W+tx+a]==1)n++;
        boolean roof=false;for(int b=1;b<=5;b++)if(solid(tile(tx,ty-b)))roof=true;
        return n>=8&&roof&&nearStation(TORCH);
    }
    public boolean nearStation(int station) {
        if(station==0)return true;
        int tx=(int)x/T,ty=(int)y/T;
        for(int a=-5;a<=5;a++)for(int b=-4;b<=4;b++)if(tile(tx+a,ty+b)==station)return true;
        return false;
    }
    public boolean canCraft(Content.Recipe r) {
        return nearStation(r.station())&&(!r.item().unique()||count(r.item())==0)
                &&r.costs().stream().allMatch(c->count(c.item())>=c.count());
    }
    public boolean craft(int index) {
        if(index<0||index>=content.recipes.size())return false;
        var r=content.recipes.get(index);
        if(!canCraft(r)){say(!nearStation(r.station())?"Stand near a "+Content.stationName(r.station())+".":"Missing resources, or already owned.");return false;}
        r.costs().forEach(c->take(c.item(),c.count()));add(r.item(),r.count());
        if(r.item()==COPPER_PICK||r.item()==IRON_PICK)hotbar[0]=r.item();
        if(r.item()==IRON_SWORD)hotbar[2]=r.item();
        say("Crafted "+r.count()+" "+r.item().label+".");advanceQuests();return true;
    }
    public void equip(Content.Item i) { hotbar[slot]=i;say(i.label+" assigned to slot "+(slot+1)+"."); }
    public boolean use(int tx,int ty) {
        aimX=tx;aimY=ty;
        Content.Item i=selected();
        if(i.weapon()) return attack(tx*T+6,ty*T+6,i);
        if(i==BERRY||i==POTION||i==HEART)return consume(i);
        if(!reachable(tx,ty)){mining=0;return false;}
        if(i.tool())return mine(tx,ty,i);
        return place(tx,ty,i);
    }
    public boolean mine(int tx,int ty,Content.Item tool) {
        if(!reachable(tx,ty)||actionCooldown>0||!tool.tool()||count(tool)==0)return false;
        int tier=tool==IRON_PICK?2:tool==COPPER_PICK?1:0;
        int t=tile(tx,ty);
        if(t==AIR&&walls[ty*W+tx]==1) {
            walls[ty*W+tx]=0;add(WALL,1);actionCooldown=8;return true;
        }
        if(t==AIR||t==BEDROCK||t==SHRINE)return false;
        if(tool==AXE&&t!=LOG&&t!=LEAVES&&t!=BUSH){say("Use a pick to mine terrain.");return false;}
        if(t==LOG||t==LEAVES) {
            if(tool!=AXE){say("Use the axe to fell trees.");return false;}
        }else if(t==IRON&&tier<1){say("Iron needs a copper pick.");return false;}
        else if(t==CRYSTAL&&tier<2){say("Chaos shards need an iron pick.");return false;}
        if(t==CHEST||t==BUSH){interact(tx,ty);return true;}
        if(miningX!=tx||miningY!=ty){miningX=tx;miningY=ty;mining=0;}
        mining++;
        int required=t==LOG||t==LEAVES?18:t==STONE||t==IRON||t==CRYSTAL?Math.max(8,26-tier*8):10;
        if(mining<required){if(mining%6==0)burst(tx*T+6,ty*T+6,color(t),2);return false;}
        mining=0;
        if(t==LOG) {
            int low=ty,high=ty;
            while(tile(tx,low+1)==LOG)low++;while(tile(tx,high-1)==LOG)high--;
            int wood=(low-high+1)*3;
            for(int a=-3;a<=3;a++)for(int b=high-3;b<=low;b++)if(tile(tx+a,b)==LEAVES||a==0&&tile(tx,b)==LOG)set(tx+a,b,AIR);
            add(WOOD,wood);add(ACORN,2);woodChopped++;popup(tx*T,ty*T,"+"+wood+" TIMBER",WOOD.color);
        }else {
            set(tx,ty,AIR);
            Content.Item loot=switch(t) {
                case DIRT,GRASS,SNOW,EMBER -> Content.Item.DIRT;case STONE -> Content.Item.STONE;
                case COPPER -> Content.Item.COPPER;case IRON -> Content.Item.IRON;case CRYSTAL -> Content.Item.CRYSTAL;
                case PLANK,LEAVES -> WOOD;case PLATFORM -> Content.Item.PLATFORM;
                case TORCH -> Content.Item.TORCH;case BENCH -> Content.Item.BENCH;
                case FURNACE -> Content.Item.FURNACE;case ANVIL -> Content.Item.ANVIL;default -> WOOD;
            };
            add(loot,1);popup(tx*T,ty*T,"+1 "+loot.label.toUpperCase(),loot.color);
        }
        burst(tx*T+6,ty*T+6,color(t),8);actionCooldown=4;advanceQuests();return true;
    }
    public boolean place(int tx,int ty,Content.Item i) {
        if(!reachable(tx,ty)||actionCooldown>0||count(i)==0)return false;
        if(i==WALL) {
            if(walls[ty*W+tx]==1)return false;
            walls[ty*W+tx]=1;take(i,1);blocksPlaced++;actionCooldown=8;return true;
        }
        if(i==ACORN) {
            if(tile(tx,ty)!=AIR||!solid(tile(tx,ty+1)))return false;
            for(int a=-3;a<=3;a++)for(int b=ty-8;b<=ty;b++)if(tile(tx+a,b)!=AIR||Math.abs(tx+a-x/T)<1&&Math.abs(b-y/T)<2)return false;
            take(i,1);tree(tx,ty,5);actionCooldown=20;say("A new tree takes root.");return true;
        }
        if(i.tile<0||tile(tx,ty)!=AIR)return false;
        // Adjacency prevents unsupported floating blocks; player body cannot be entombed.
        boolean adjacent=walls[ty*W+tx]==1;
        for(int[] d:new int[][]{{0,1},{0,-1},{-1,0},{1,0}})if(tile(tx+d[0],ty+d[1])!=AIR)adjacent=true;
        if(!adjacent)return false;
        if(solid(i.tile)&&Math.abs(tx*T+6-x)<10&&Math.abs(ty*T+6-y)<16)return false;
        for(Enemy e:enemies)if(solid(i.tile)&&Math.abs(tx*T+6-e.x)<15&&Math.abs(ty*T+6-e.y)<15)return false;
        set(tx,ty,i.tile);take(i,1);blocksPlaced++;actionCooldown=8;burst(tx*T+6,ty*T+6,i.color,3);advanceQuests();return true;
    }
    public boolean interact(int tx,int ty) {
        if(!reachable(tx,ty))return false;
        int t=tile(tx,ty);
        if(t==BUSH){set(tx,ty,AIR);add(BERRY,3);burst(tx*T,ty*T,BERRY.color,6);say("Collected wild berries. H heals.");return true;}
        if(t==CHEST) {
            set(tx,ty,AIR);add(Content.Item.IRON,6);add(Content.Item.COPPER,9);add(POTION,2);add(Content.Item.CRYSTAL,3);add(HEART,1);
            say("Explorer's cache: ore, crystals, potions, a heartstone!");burst(tx*T,ty*T,0xFFFFDC91,20);return true;
        }
        if(t==SHRINE) {
            int which=-1;for(int n=0;n<3;n++)if(tx==shrineX[n]&&ty==shrineY[n]-1)which=n;
            if(which<0)return false;
            if((wardens&(1<<which))!=0){say("This sentinel's emerald fragment is already yours.");return false;}
            if(enemies.stream().anyMatch(e->e.kind==3)){say("A sentinel is already awake.");return false;}
            if(!take(SIGIL,1)){say("Craft a sentinel sigil at an anvil first.");return false;}
            Enemy boss=new Enemy(tx*T+6,(ty-3)*T,3);boss.shrine=which;enemies.add(boss);
            say("THE "+new String[]{"JUNGLE","RUINS","CORE"}[which]+" SENTINEL AWAKENS!");return true;
        }
        if(Math.abs(tx-40)<3&&Math.abs(ty-27)<4) {
            if(Integer.bitCount(wardens)==3&&take(BEACON,1)){won=true;quest=7;say("The beacon shines. The frontier is yours.");burst(x,y,0xFFEBD7A0,70);return true;}
            say("The beacon needs a starlight core. Seek three shrine sentinels.");return false;
        }
        return false;
    }
    public boolean consume(Content.Item i) {
        if(i!=HEART&&i!=BERRY&&i!=POTION)return false;
        if(i==HEART) {
            if(maxHp>=200||!take(i,1))return false;maxHp+=20;hp=Math.min(maxHp,hp+20);say("Heartstone: maximum health +20.");return true;
        }
        if(hp>=maxHp||healCooldown>0||!take(i,1))return false;
        hp=Math.min(maxHp,hp+(i==POTION?50:15));healCooldown=120;popup(x,y,"HEALED",0xFF91E7AE);return true;
    }
    public boolean heal() { return consume(count(POTION)>0?POTION:BERRY); }
    public boolean attack(double ax,double ay,Content.Item weapon) {
        if(actionCooldown>0||count(weapon)==0)return false;
        double dx=ax-x,dy=ay-y,len=Math.hypot(dx,dy);if(len<1){dx=facingLeft?-1:1;dy=0;len=1;}
        facingLeft=dx<0;
        if(weapon==BOW||weapon==STAFF) {
            if(weapon==BOW&&!take(ARROW,1)){say("Craft arrows at the workbench.");return false;}
            if(weapon==STAFF&&mana<12){say("Chaos energy is recharging.");return false;}
            if(weapon==STAFF)mana-=12;
            shots.add(new Shot(x,y,dx/len*5,dy/len*5,weapon==STAFF?26:17,false,weapon==STAFF));actionCooldown=weapon==STAFF?18:24;
        }else {
            actionCooldown=weapon==IRON_SWORD?22:26;
            for(Enemy e:enemies)if(e.hp>0&&Math.hypot(e.x-x,e.y-y)<(weapon==IRON_SWORD?52:40)
                    &&(e.x-x)*(facingLeft?-1:1)>-10) damage(e,weapon==IRON_SWORD?25:12);
            burst(x+(facingLeft?-18:18),y,weapon.color,7);
        }
        return true;
    }
    private void damage(Enemy e,int amount) {
        e.hp-=amount;e.hit=8;e.vx=(e.x<x?-1:1)*2.8;popup(e.x,e.y-12,""+amount,0xFFFFD395);
        if(e.hp<=0){burst(e.x,e.y,0xFF9FDFBC,18);add(GEL,e.kind==3?12:2);kills++;
            if(e.kind==3){wardens|=1<<e.shrine;add(RELIC,1);add(Content.Item.CRYSTAL,6);add(HEART,1);say("Sentinel defeated! An emerald fragment and heartstone are yours.");}
            else if(random(4)==0)add(BERRY,1);
        }
    }
    private void hurt(int amount,double sourceX) {
        if(invulnerable>0||hp<=0)return;
        hp-=Math.max(1,amount-(count(ARMOR)>0?5:0));invulnerable=65;vx=sourceX<x?3:-3;vy=-2;
        burst(x,y,0xFFEC869B,10);
    }
    private void respawn() {
        // Terrain remains changed; clear only a spawn pocket so a built camp cannot trap a return.
        int sy=surface(40);
        for(int a=39;a<=41;a++)for(int b=sy-3;b<sy;b++)set(a,b,AIR);
        set(40,sy,GRASS);x=40*T+6;y=sy*T-11;vx=vy=0;hp=maxHp;mana=100;respawns++;invulnerable=180;
        enemies.removeIf(e->e.kind==3);shots.clear();say("Rescued at camp. Your backpack and world are safe.");
    }
    public boolean recall() {
        if(enemies.stream().anyMatch(e->e.kind==3)){say("Defeat the sentinel or flee first.");return false;}
        respawn();respawns--;say("Returned to the camp beacon.");return true;
    }
    private void spawnEnemy() {
        boolean deep=y/T>surface((int)x/T)+7;
        boolean night=ticks%21600>13500;
        if(!deep&&!night&&random(3)!=0||sheltered())return;
        int tx=(int)x/T+(random(2)==0?-1:1)*(12+random(5));if(tx<2||tx>=W-2)return;
        int ty=deep?(int)y/T:surface(tx)-1;
        if(tile(tx,ty)!=AIR||nearTorch(tx,ty))return;
        Enemy e=new Enemy(tx*T+6,ty*T-4,deep?1:0);if(deep&&random(3)==0)e.kind=2;
        enemies.add(e);
    }
    private boolean nearTorch(int tx,int ty) {
        for(int a=-4;a<=4;a++)for(int b=-3;b<=3;b++)if(tile(tx+a,ty+b)==TORCH)return true;
        return false;
    }
    private void updateEnemies() {
        for(Enemy e:enemies) {
            if(e.hp<=0)continue;e.timer++;if(e.hit>0)e.hit--;
            double dir=x<e.x?-1:1;
            if(e.kind==1||e.kind==3) {
                double desiredY=e.kind==3?y-30:y-15;
                e.vx+=(dir*(e.kind==3?1.35:1.1)-e.vx)*.04;e.vy+=(Math.signum(desiredY-e.y)*.75-e.vy)*.04;
                if(e.kind==3) {
                    int period=e.shrine==0?110:e.shrine==1?140:95;
                    if(e.timer%period==period-25)burst(e.x,e.y,0xFFFFDD9B,12);
                    if(e.timer%period==0) {
                        double angle=Math.atan2(y-e.y,x-e.x);
                        if(e.shrine==1)for(int a=0;a<8;a++) {
                            double ring=angle+a*Math.PI/4;shots.add(new Shot(e.x,e.y,Math.cos(ring)*1.8,Math.sin(ring)*1.8,14,true,true));
                        }else for(int a=-1;a<=1;a++) {
                            double fan=angle+a*.3;double speed=e.shrine==2?3.1:2.2;
                            shots.add(new Shot(e.x,e.y,Math.cos(fan)*speed,Math.sin(fan)*speed,16,true,true));
                        }
                        if(e.shrine==2){e.vx=Math.cos(angle)*3.8;e.vy=Math.sin(angle)*3.8;}
                    }
                }
                if(e.kind==3){e.x+=e.vx;e.y+=e.vy;}else {
                    if(!blocked(e.x+e.vx,e.y,5,5))e.x+=e.vx;
                    if(!blocked(e.x,e.y+e.vy,5,5))e.y+=e.vy;
                }
            }else {
                e.vx+=(dir*.65-e.vx)*.06;e.vy=Math.min(6,e.vy+.2);
                if(!blocked(e.x+e.vx,e.y,6,6))e.x+=e.vx;else if(e.timer%30==0)e.vy=-3.5;
                if(!blocked(e.x,e.y+e.vy,6,6))e.y+=e.vy;
                else {e.vy=0;if(e.timer%55==0)e.vy=-3.7;}
            }
            e.x=Math.max(12,Math.min((W-1)*T,e.x));e.y=Math.max(8,Math.min((H-1)*T,e.y));
            if(Math.hypot(e.x-x,e.y-y)<(e.kind==3?22:15)) {
                if(!grounded&&vy>0&&y<e.y-3&&e.hit==0) {damage(e,25);vy=-4;}
                else if(e.hit==0)hurt(e.kind==3?20:e.kind==2?14:9,e.x);
            }
        }
        enemies.removeIf(e->e.hp<=0||e.kind!=3&&Math.abs(e.x-x)>480||e.y>H*T);
    }
    private void updateShots() {
        for(Shot s:shots) {
            s.life--;s.x+=s.vx;s.y+=s.vy;if(!s.magic)s.vy+=.03;
            if(s.x<0||s.x>=W*T||s.y<0||s.y>=H*T||solid(tile((int)(s.x/T),(int)(s.y/T))))s.life=0;
            if(s.life<=0)continue;
            if(s.hostile){if(Math.hypot(s.x-x,s.y-y)<12){hurt(s.damage,s.x);s.life=0;}}
            else for(Enemy e:enemies)if(e.hp>0&&Math.hypot(s.x-e.x,s.y-e.y)<(e.kind==3?20:10)){damage(e,s.damage);s.life=0;break;}
        }
        shots.removeIf(s->s.life<=0);
    }
    private void updateParticles() {
        for(Particle p:particles){p.life--;p.x+=p.vx;p.y+=p.vy;if(p.text==null)p.vy+=.09;}
        particles.removeIf(p->p.life<=0);if(particles.size()>300)particles.subList(0,particles.size()-300).clear();
    }
    private void reveal() {
        int tx=(int)x/T,ty=(int)y/T;
        for(int a=-15;a<=15;a++)for(int b=-10;b<=10;b++)if(inside(tx+a,ty+b)&&a*a+b*b<230)seen[(ty+b)*W+tx+a]=1;
    }
    public void advanceQuests() {
        while(quest<7) {
            boolean done=switch(quest) {
                case 0 -> gathered[WOOD.ordinal()]>=16;
                case 1 -> hasTile(BENCH);case 2 -> hasTile(FURNACE);
                case 3 -> count(COPPER_PICK)>0||count(IRON_PICK)>0;
                case 4 -> count(IRON_PICK)>0&&hasTile(ANVIL);
                case 5 -> Integer.bitCount(wardens)==3;case 6 -> won;default -> false;
            };
            if(!done)break;quest++;add(GEL,3);add(BERRY,2);
            say(quest<7?"Quest complete! Next: "+content.questNames.get(quest):"STARLIGHT RESTORED. Keep exploring and building!");
            burst(x,y,0xFFFFD995,18);
        }
    }
    private boolean hasTile(int tile) {for(byte t:tiles)if(t==tile)return true;return false;}
    public String biome() {return y/T>58?"ANGEL ISLAND / RUINS":y/T>surface((int)x/T)+7?"ANGEL ISLAND / CAVERNS":"ANGEL ISLAND / JUNGLE";}
    public static int color(int tile) {return 0xFF000000|switch(tile) {
        case DIRT -> 0x89674E;case GRASS -> 0x709C61;case STONE -> 0x697789;case COPPER -> 0xC48766;
        case IRON -> 0xA5B9C3;case CRYSTAL -> 0x74CED7;case LOG -> 0x967044;case LEAVES -> 0x528465;
        case PLANK,PLATFORM,BENCH -> 0xAE855B;case TORCH -> 0xE5BA68;case FURNACE -> 0x977C74;
        case ANVIL -> 0xADC3CA;case SHRINE -> 0x8ABBC1;case CHEST -> 0xC8A372;case BUSH -> 0x619A64;
        case SNOW -> 0xBFD5DD;case EMBER -> 0xA8675E;case BEDROCK -> 0x35404E;default -> 0x293340;
    };}
}
