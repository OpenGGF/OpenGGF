package starfall;

import java.io.*;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Versioned, bounded saves. Decode into a fresh world; reject the entire malformed document. */
public final class SaveCodec {
    private SaveCodec() { }
    private static final int MAGIC=0x53544631, VERSION=1, MAX_BYTES=300000;
    public static String encode(World w) {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(DataOutputStream o=new DataOutputStream(new GZIPOutputStream(bytes))) {
                o.writeInt(MAGIC);o.writeInt(VERSION);o.writeLong(w.seed);o.writeLong(w.randomState);o.writeLong(w.ticks);
                o.write(w.tiles);o.write(w.walls);o.write(w.seen);
                for(int n:w.inventory)o.writeInt(n);for(int n:w.gathered)o.writeInt(n);
                for(Content.Item i:w.hotbar)o.writeInt(i.ordinal());
                o.writeDouble(w.x);o.writeDouble(w.y);o.writeDouble(w.vx);o.writeDouble(w.vy);
                for(int n:new int[]{w.hp,w.maxHp,w.mana,w.slot,w.quest,w.wardens,w.kills,w.blocksPlaced,w.woodChopped,
                        w.invulnerable,w.actionCooldown,w.healCooldown,w.respawns,w.aimX,w.aimY,w.miningX,w.miningY,w.mining})o.writeInt(n);
                o.writeBoolean(w.grounded);o.writeBoolean(w.facingLeft);o.writeBoolean(w.won);
                o.writeInt(w.enemies.size());
                for(World.Enemy e:w.enemies) {
                    o.writeDouble(e.x);o.writeDouble(e.y);o.writeDouble(e.vx);o.writeDouble(e.vy);
                    for(int n:new int[]{e.kind,e.hp,e.maxHp,e.timer,e.hit,e.shrine})o.writeInt(n);
                }
                o.writeInt(w.shots.size());
                for(World.Shot s:w.shots){o.writeDouble(s.x);o.writeDouble(s.y);o.writeDouble(s.vx);o.writeDouble(s.vy);
                    o.writeInt(s.damage);o.writeInt(s.life);o.writeBoolean(s.hostile);o.writeBoolean(s.magic);}
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException e){throw new IllegalStateException("Cannot encode world",e);}
    }
    public static Optional<World> decode(String text) {
        if(text==null||text.length()>500000)return Optional.empty();
        try {
            byte[] compressed=Base64.getDecoder().decode(text);
            byte[] expanded;
            try(var gzip=new GZIPInputStream(new ByteArrayInputStream(compressed))){expanded=gzip.readNBytes(MAX_BYTES+1);}
            if(expanded.length>MAX_BYTES)return Optional.empty();
            try(var in=new DataInputStream(new ByteArrayInputStream(expanded))) {
                if(in.readInt()!=MAGIC||in.readInt()!=VERSION)return Optional.empty();
                World w=new World(in.readLong(),false);w.randomState=in.readLong();w.ticks=in.readLong();
                in.readFully(w.tiles);in.readFully(w.walls);in.readFully(w.seen);
                for(byte t:w.tiles)if(t<0||t>World.EMBER)return Optional.empty();
                for(byte t:w.walls)if(t<0||t>2)return Optional.empty();
                for(byte t:w.seen)if(t<0||t>1)return Optional.empty();
                for(int i=0;i<w.inventory.length;i++)w.inventory[i]=bounded(in.readInt(),0,9999);
                for(int i=0;i<w.gathered.length;i++)w.gathered[i]=bounded(in.readInt(),0,999999);
                for(int i=0;i<8;i++)w.hotbar[i]=Content.Item.values()[bounded(in.readInt(),0,Content.Item.values().length-1)];
                w.x=coordinate(in.readDouble(),World.W*World.T);w.y=coordinate(in.readDouble(),World.H*World.T);
                w.vx=velocity(in.readDouble());w.vy=velocity(in.readDouble());
                w.hp=bounded(in.readInt(),1,200);w.maxHp=bounded(in.readInt(),100,200);w.mana=bounded(in.readInt(),0,100);
                w.slot=bounded(in.readInt(),0,7);w.quest=bounded(in.readInt(),0,7);w.wardens=bounded(in.readInt(),0,7);
                w.kills=bounded(in.readInt(),0,10000000);w.blocksPlaced=bounded(in.readInt(),0,10000000);w.woodChopped=bounded(in.readInt(),0,10000000);
                w.invulnerable=bounded(in.readInt(),0,180);w.actionCooldown=bounded(in.readInt(),0,100);
                w.healCooldown=bounded(in.readInt(),0,120);w.respawns=bounded(in.readInt(),0,10000000);
                w.aimX=bounded(in.readInt(),0,World.W-1);w.aimY=bounded(in.readInt(),0,World.H-1);
                w.miningX=bounded(in.readInt(),-1,World.W-1);w.miningY=bounded(in.readInt(),-1,World.H-1);
                w.mining=bounded(in.readInt(),0,100);w.grounded=in.readBoolean();w.facingLeft=in.readBoolean();w.won=in.readBoolean();
                int n=bounded(in.readInt(),0,32);
                for(int i=0;i<n;i++) {
                    double x=coordinate(in.readDouble(),World.W*World.T),y=coordinate(in.readDouble(),World.H*World.T);
                    double vx=velocity(in.readDouble()),vy=velocity(in.readDouble());
                    World.Enemy e=new World.Enemy(x,y,bounded(in.readInt(),0,3));e.vx=vx;e.vy=vy;
                    e.hp=bounded(in.readInt(),1,240);e.maxHp=bounded(in.readInt(),24,240);
                    e.timer=bounded(in.readInt(),0,Integer.MAX_VALUE);e.hit=bounded(in.readInt(),0,8);e.shrine=bounded(in.readInt(),-1,2);
                    if(e.kind==3&&e.shrine<0)return Optional.empty();w.enemies.add(e);
                }
                n=bounded(in.readInt(),0,256);
                for(int i=0;i<n;i++) {
                    World.Shot s=new World.Shot(coordinate(in.readDouble(),World.W*World.T),coordinate(in.readDouble(),World.H*World.T),
                            velocity(in.readDouble()),velocity(in.readDouble()),bounded(in.readInt(),1,100),false,false);
                    s.life=bounded(in.readInt(),1,100);s.hostile=in.readBoolean();s.magic=in.readBoolean();w.shots.add(s);
                }
                if(in.read()!=-1||w.hp>w.maxHp||w.ticks<0||w.randomState==0)return Optional.empty();
                w.say("Welcome back. Your frontier awaits.");return Optional.of(w);
            }
        }catch(IOException|IllegalArgumentException e){return Optional.empty();}
    }
    private static int bounded(int v,int low,int high){if(v<low||v>high)throw new IllegalArgumentException("Invalid save value");return v;}
    private static double coordinate(double v,int limit){if(!Double.isFinite(v)||v<0||v>limit)throw new IllegalArgumentException("Invalid position");return v;}
    private static double velocity(double v){if(!Double.isFinite(v)||Math.abs(v)>20)throw new IllegalArgumentException("Invalid velocity");return v;}
}
