package com.openggf.game.sonic3k.competition;

import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Native miniature collision consumer of the real engine's ROM geometry.
 * Player_AnglePos (sonic3k.asm:18764-19153),
 * sub_F6B4 (sonic3k.asm:19785-19845),
 * CalcRoomOverHead (sonic3k.asm:19847-19883),
 * SonicKnux_DoLevelCollision (sonic3k.asm:24075-24362) and
 * Tails_DoLevelCollision (sonic3k.asm:28911-29148).
 * No campaign playable controller or ambient sensor owner runs.
 */
public final class S3kCompetitionTerrain implements S3kCompetitionPlayer.Terrain,
        S3kCompetitionSession.ItemTerrain {
    /** Always a native FindFloor/FindWall result, including the empty-tile distance. */
    public record Probe(int distance,int angle,int tileIndex) { }
    /** Production geometry boundary; coordinates are native unsigned words. */
    @FunctionalInterface public interface Geometry {
        Probe probe(Direction direction,int x,int y,int solidityBit,boolean secondaryCollisionPath);
    }
    private record Pair(Probe primary,Probe secondary) {
        Probe selected(){return secondary.distance()<=primary.distance()?secondary:primary;}
        int otherDistance(){return secondary.distance()<=primary.distance()?primary.distance():secondary.distance();}
    }
    private final Geometry geometry;
    private final Consumer<S3kCompetitionPlayer> death;

    public S3kCompetitionTerrain(LevelManager level,int layoutYMask,
            Supplier<S3kCompetitionSession> session,IntSupplier playerOneCameraY) {
        this(new LevelGeometry(level,layoutYMask),player->Objects.requireNonNull(session.get(),"competition session")
                .resolveDeadPlayer(player.slot(),playerOneCameraY.getAsInt()));
    }
    public S3kCompetitionTerrain(Geometry geometry,Consumer<S3kCompetitionPlayer> death) {
        this.geometry=Objects.requireNonNull(geometry);this.death=Objects.requireNonNull(death);
    }
    public Probe probe(Direction direction,int x,int y,int solidityBit,boolean secondaryCollisionPath) {
        return Objects.requireNonNull(geometry.probe(direction,x&0xFFFF,y&0xFFFF,solidityBit,secondaryCollisionPath),"native terrain probe");
    }
    @Override public void resolveDeath(S3kCompetitionPlayer player){death.accept(player);}

    @Override public boolean canJump(S3kCompetitionPlayer p,int clearance) {
        // Sonic_Jump passes angle+$80; CalcRoomOverHead adds $20 and selects a pair.
        int quadrant=(p.angle()+0xA0)&0xC0;
        Direction direction=direction(quadrant);
        int bit=direction==Direction.DOWN?p.topSolidBit():p.lrbSolidBit();
        return pair(p,direction,bit).selected().distance()>=clearance;
    }
    @Override public void resolveGroundWall(S3kCompetitionPlayer p) {
        if(p.groundVelocity()==0 || (p.objectControl()&0x40)!=0) return;
        if((p.angle()&0x3F)!=0 && (byte)(p.angle()+0x40)<0) return;
        int perpendicular=(p.angle()+(p.groundVelocity()<0?0x40:-0x40))&255;
        int quadrant=attachmentQuadrant(perpendicular);
        Direction direction=direction(quadrant);
        // Competition sub_F6B4 predicts the entire position pair and uses x_radius
        // in all four directions. It does not add the campaign's floor-side +8.
        int x=predicted(p.centreX(),p.xFraction(),p.xVelocity());
        int y=predicted(p.centreY(),p.yFraction(),p.yVelocity());
        switch(direction) {
            case DOWN -> y+=p.xRadius();
            case UP -> y-=p.xRadius();
            case LEFT -> x-=p.xRadius();
            case RIGHT -> x+=p.xRadius();
        }
        int distance=probe(direction,x,y,p.lrbSolidBit(),secondary(p)).distance();
        if(distance>=0) return;
        int correction=(short)(distance<<8);
        // The response re-quantizes with +$20, rather than the predictor's
        // signed boundary adjustment (loc_113A0).
        switch((perpendicular+0x20)&0xC0) {
            case 0 -> p.setYVelocity(p.yVelocity()+correction);
            case 0x80 -> p.setYVelocity(p.yVelocity()-correction);
            case 0x40 -> {
                p.setXVelocity(p.xVelocity()-correction);p.setGroundVelocity(0);
                if(p.facingLeft()) p.setPushing(true);
            }
            case 0xC0 -> {
                p.setXVelocity(p.xVelocity()+correction);p.setGroundVelocity(0);
                if(!p.facingLeft()) p.setPushing(true);
            }
        }
    }
    private static int predicted(int word,int fraction,int velocity) {
        return (short)((((word<<16)|(fraction&0xFFFF))+(velocity<<8))>>16);
    }
    /** Native angle boundary corrections used by Player_AnglePos/sub_F6B4. */
    private static int attachmentQuadrant(int angle) {
        int value=angle&255;
        if((byte)(value+0x20)<0) {
            if((byte)value<0) value=(value-1)&255;
            return (value+0x20)&0xC0;
        }
        if((byte)value<0) value=(value+1)&255;
        return (value+0x1F)&0xC0;
    }
    private static Direction direction(int quadrant) {
        return switch(quadrant) {case 0->Direction.DOWN;case 0x40->Direction.LEFT;case 0x80->Direction.UP;case 0xC0->Direction.RIGHT;
            default->throw new IllegalArgumentException("Native collision quadrant");};
    }
    private static boolean secondary(S3kCompetitionPlayer p){return p.topSolidBit()!=0xC;}
    private Pair pair(S3kCompetitionPlayer p,Direction direction,int solidity) {
        int x=p.centreX(),y=p.centreY(),xr=p.xRadius(),yr=p.yRadius();
        return switch(direction) {
            case DOWN -> new Pair(probe(direction,x+xr,y+yr,solidity,secondary(p)),probe(direction,x-xr,y+yr,solidity,secondary(p)));
            case UP -> new Pair(probe(direction,x+xr,y-yr,solidity,secondary(p)),probe(direction,x-xr,y-yr,solidity,secondary(p)));
            case RIGHT -> new Pair(probe(direction,x+yr,y-xr,solidity,secondary(p)),probe(direction,x+yr,y+xr,solidity,secondary(p)));
            case LEFT -> new Pair(probe(direction,x-yr,y-xr,solidity,secondary(p)),probe(direction,x-yr,y+xr,solidity,secondary(p)));
        };
    }
    @Override public void followGround(S3kCompetitionPlayer p) {
        if((p.status()&8)!=0) return; // Player_AnglePos's standing-object gate.
        Direction direction=direction(attachmentQuadrant(p.angle()));
        Pair pair=pair(p,direction,p.topSolidBit());Probe selected=pair.selected();
        int angle=selected.angle()&255;
        if((angle&1)!=0 || Math.abs((byte)(angle-p.angle()))>=0x20) angle=(p.angle()+0x20)&0xC0;
        p.setAngle(angle);
        int distance=selected.distance();
        if(distance==0 || distance< -0xE) return;
        if(distance>0 && !p.stickToConvex()) {
            int velocity=direction==Direction.DOWN || direction==Direction.UP?p.xVelocity():p.yVelocity();
            int threshold=Math.min(0xE,Math.abs((byte)(velocity>>8))+4);
            if((byte)distance>threshold) {
                p.setAirborne(true);p.setPushing(false);p.setPreviousAnimation(1);return;
            }
        }
        translate(p,direction,distance);
    }
    private static void translate(S3kCompetitionPlayer p,Direction direction,int distance) {
        switch(direction) {
            case DOWN -> p.setCentreNative(p.centreX(),p.centreY()+distance);
            case UP -> p.setCentreNative(p.centreX(),p.centreY()-distance);
            case LEFT -> p.setCentreNative(p.centreX()-distance,p.centreY());
            case RIGHT -> p.setCentreNative(p.centreX()+distance,p.centreY());
        }
    }
    @Override public void resolveAir(S3kCompetitionPlayer p) {
        int quadrant=TrigLookupTable.calcMovementQuadrant((short)p.xVelocity(),(short)p.yVelocity());
        switch(quadrant) {
            case 0 -> {
                hitWall(p,Direction.LEFT,false);hitWall(p,Direction.RIGHT,false);
                Pair floor=pair(p,Direction.DOWN,p.topSolidBit());Probe selected=floor.selected();
                if(selected.distance()>=0) return;
                // MOVE.B y_vel reads the HIGH byte of the native velocity word.
                int admissible=(byte)(-((byte)(p.yVelocity()>>8)+8));
                if((byte)selected.distance()<admissible && (byte)floor.otherDistance()<admissible) return;
                int angle=cardinalAngle(selected.angle(),0);
                p.setAngle(angle);translate(p,Direction.DOWN,selected.distance());
                if(((angle+0x20)&0x40)!=0) {
                    p.setXVelocity(0);if(p.yVelocity()>0xFC0) p.setYVelocity(0xFC0);
                    touchFloor(p);p.setGroundVelocity((byte)angle<0?-p.yVelocity():p.yVelocity());
                } else if(((angle+0x10)&0x20)!=0) {
                    p.setYVelocity(p.yVelocity()>>1);touchFloor(p);
                    p.setGroundVelocity((byte)angle<0?-p.yVelocity():p.yVelocity());
                } else {
                    p.setYVelocity(0);p.setGroundVelocity(p.xVelocity());touchFloor(p);
                }
            }
            case 0x40 -> resolveSideAir(p,Direction.LEFT,true);
            case 0xC0 -> resolveSideAir(p,Direction.RIGHT,false);
            case 0x80 -> {
                hitWall(p,Direction.LEFT,false);hitWall(p,Direction.RIGHT,false);
                Probe ceiling=pair(p,Direction.UP,p.lrbSolidBit()).selected();
                if(ceiling.distance()>=0) return;
                translate(p,Direction.UP,ceiling.distance());
                int angle=cardinalAngle(ceiling.angle(),0x80);
                if(((angle+0x20)&0x40)==0) p.setYVelocity(0);
                else {
                    p.setAngle(angle);touchFloor(p);
                    p.setGroundVelocity((byte)angle<0?-p.yVelocity():p.yVelocity());
                }
            }
        }
    }
    private void resolveSideAir(S3kCompetitionPlayer p,Direction side,boolean nativeLeftDepthGate) {
        hitWall(p,side,true);
        Probe ceiling=pair(p,Direction.UP,p.lrbSolidBit()).selected();
        if(ceiling.distance()<0) {
            if(nativeLeftDepthGate && -ceiling.distance()>=0x14) {hitWall(p,Direction.RIGHT,false);return;}
            translate(p,Direction.UP,ceiling.distance());
            if(p.yVelocity()<0) p.setYVelocity(0);
            return;
        }
        if(p.yVelocity()<0) return;
        Probe floor=pair(p,Direction.DOWN,p.topSolidBit()).selected();
        if(floor.distance()<0) {
            translate(p,Direction.DOWN,floor.distance());p.setAngle(cardinalAngle(floor.angle(),0));
            p.setYVelocity(0);p.setGroundVelocity(p.xVelocity());touchFloor(p);
        }
    }
    private void hitWall(S3kCompetitionPlayer p,Direction direction,boolean copyVerticalToGround) {
        int x=p.centreX()+(direction==Direction.LEFT?-p.xRadius():p.xRadius());
        Probe result=probe(direction,x,p.centreY(),p.lrbSolidBit(),secondary(p));
        if(result.distance()<0) {
            translate(p,direction,result.distance());p.setXVelocity(0);
            if(copyVerticalToGround) p.setGroundVelocity(p.yVelocity());
        }
    }
    private static int cardinalAngle(int angle,int cardinal){return (angle&1)!=0?cardinal:angle&255;}
    /** Player_TouchFloor_Check_Spindash's actual selective reset, including its skip branch. */
    private static void touchFloor(S3kCompetitionPlayer p) {
        if(p.spinDashFlag()==0) {
            int radius=p.yRadius();p.setRadii(5,0xB);p.setAnimation(0);
            if(p.rolling()) {
                p.setRolling(false);int shift=radius-0xB;
                if((byte)(p.angle()+0x40)<0) shift=-shift;
                p.setCentreNative(p.centreX(),p.centreY()+shift);
            }
        }
        p.setAirborne(false);p.setPushing(false);p.setRollingJump(false);p.setJumping(false);
        p.setFlipAngle(0);p.setFlipType(0);p.setFlipsRemaining(0);p.setDoubleJumpFlag(0);
    }

    /** NativeContext's object-floor sensor already includes its bottom offset. */
    public int floorDistance(int x,int bottomY) {return probe(Direction.DOWN,x,bottomY,0xC,false).distance();}
    @Override public int floorDistance(int x,int y,int yRadius,int topSolidBit) {
        return probe(Direction.DOWN,x,y+yRadius,topSolidBit,topSolidBit!=0xC).distance();
    }
    @Override public int leftWallDistance(int x,int y,int radius,int lrbSolidBit) {
        return probe(Direction.LEFT,x-radius,y,lrbSolidBit,lrbSolidBit!=0xD).distance();
    }
    @Override public S3kCompetitionSession.ItemGround followGround(int x,int y,int xRadius,int yRadius,
            int angle,int groundVelocity,int topSolidBit,int lrbSolidBit) {
        var actor=new S3kCompetitionPlayer(0,S3kCompetitionPlayer.Character.SONIC,x,y);
        actor.setRadii(xRadius,yRadius);actor.setAngle(angle);actor.setGroundVelocity(groundVelocity);
        actor.setXVelocity(TrigLookupTable.cosHex(angle)*groundVelocity>>8);
        actor.setYVelocity(TrigLookupTable.sinHex(angle)*groundVelocity>>8);actor.setSolidBits(topSolidBit,lrbSolidBit);
        followGround(actor);
        return new S3kCompetitionSession.ItemGround(actor.centreX(),actor.centreY(),actor.angle(),actor.airborne());
    }

    /**
     * FindFloor/FindWall's two-pass metric state machine on the existing live
     * LevelManager/ChunkDesc/SolidTile geometry. No service lookup, static sensor
     * override, detached mask, synthetic plane or alternate collision asset exists.
     */
    public static final class LevelGeometry implements Geometry {
        private final LevelManager level;
        private final int layoutYMask;
        public LevelGeometry(LevelManager level,int layoutYMask) {
            this.level=Objects.requireNonNull(level);this.layoutYMask=layoutYMask&0xFFFF;
        }
        private record Tile(SolidTile solid,ChunkDesc descriptor) {
            int angle(){return solid==null?3:solid.getAngle(descriptor.getHFlip(),descriptor.getVFlip())&255;}
        }
        private Tile tile(int x,int y,int solidity,boolean secondary) {
            ChunkDesc desc=level.getChunkDescAt((byte)0,x&0xFFFF,y&layoutYMask);
            SolidTile solid=desc==null?null:level.getSolidTileForChunkDesc(desc,solidity,secondary);
            return new Tile(solid,desc);
        }
        @Override public Probe probe(Direction direction,int x,int y,int solidity,boolean secondary) {
            x&=0xFFFF;y&=0xFFFF;
            boolean vertical=direction==Direction.DOWN || direction==Direction.UP;
            boolean reverse=direction==Direction.UP || direction==Direction.LEFT;
            int coordinate=vertical?y:x;
            int nibble=(reverse?coordinate^0xF:coordinate)&0xF;
            Tile first=tile(x,y,solidity,secondary);
            int metric=metric(first,direction,x,y);
            boolean regress=metric==16 || metric<0 && metric+nibble<0;
            boolean extend=metric==0 || metric<0 && metric+nibble>=0;
            if(!regress && !extend) return result(15-metric-nibble,first);
            int offset=regress?-16:16;
            int worldDelta=reverse?-offset:offset;
            Tile second=tile(vertical?x:x+worldDelta,vertical?y+worldDelta:y,solidity,secondary);
            metric=metric(second,direction,x,y);
            int distance;
            if(metric==0 || metric<0 && metric+nibble>=0) distance=15-nibble;
            else if(metric<0) distance=~nibble;
            else distance=15-metric-nibble;
            return result(distance+offset,second.solid()!=null?second:first);
        }
        private static Probe result(int distance,Tile tile) {
            return new Probe((short)distance,tile.angle(),tile.solid()==null?0:tile.solid().getIndex());
        }
        private static int metric(Tile tile,Direction direction,int x,int y) {
            if(tile.solid()==null) return 0;
            ChunkDesc desc=tile.descriptor();
            if(direction==Direction.UP || direction==Direction.DOWN) {
                int index=x&15;if(desc.getHFlip()) index=15-index;
                int value=tile.solid().getHeightAt((byte)index);
                return desc.getVFlip()^(direction==Direction.UP)?-value:value;
            }
            int index=y&15;if(desc.getVFlip()) index=15-index;
            int value=tile.solid().getWidthAt((byte)index);
            return desc.getHFlip()^(direction==Direction.LEFT)?-value:value;
        }
    }
}
