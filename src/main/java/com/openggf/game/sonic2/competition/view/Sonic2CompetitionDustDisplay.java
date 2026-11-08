package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.objects.*;
import com.openggf.sprites.managers.NativeDustDisplayInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.*;
import java.util.function.IntUnaryOperator;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;
import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

/** Completed Obj08 display/art latches. Existing fixed controllers alone advance animation. */
public final class Sonic2CompetitionDustDisplay implements NativeDustDisplayInternalAccess.Source,
        RewindSnapshottable<Sonic2CompetitionDustDisplay.State> {
    private final RomByteReader rom;
    private final Sonic2CompetitionArtResidency art;
    private final NativeCompetitionRoster roster;
    private final ObjectManager objects;
    private final IntUnaryOperator routine;
    private final int dash,skid;
    private final List<Pose> poses=new ArrayList<>(2);
    private final List<Integer> modes=new ArrayList<>(List.of(0,0));
    private final Map<ObjectRefId,Integer> childArt=new LinkedHashMap<>();
    public record Pose(boolean dashAnimation,int lastDplc,int artTile,ObjectSlot display,boolean admitted) { }
    public record State(List<Pose> poses,List<Integer> modes,Map<ObjectRefId,Integer> childArt) {
        public State {poses=List.copyOf(poses);modes=List.copyOf(modes);childArt=Map.copyOf(childArt);}
    }
    public Sonic2CompetitionDustDisplay(RomByteReader rom,Sonic2CompetitionArtResidency art,
            NativeCompetitionRoster roster,ObjectManager objects,IntUnaryOperator routine) {
        this.rom=Objects.requireNonNull(rom);this.art=Objects.requireNonNull(art);
        this.roster=Objects.requireNonNull(roster);this.objects=Objects.requireNonNull(objects);
        this.routine=Objects.requireNonNull(routine);
        // Ani_obj08 / Obj08Ani_Dash / Obj08Ani_Skid (s2.asm:42889-42902).
        int scripts=address(rom,0x1DF38);dash=scripts+rom.readU16BE(scripts+4);skid=scripts+rom.readU16BE(scripts+6);
        // Level clears Object_RAM through LevelOnly_Object_RAM_End (s2.asm:4808).
        for(int human=0;human<2;human++)poses.add(new Pose(false,0,adjustedArt(human),null,false));
    }
    @Override public boolean owns(AbstractPlayableSprite player) {
        int human=roster.slotOf(player);var dust=player==null?null:player.getSpindashDustController();
        return human>=0 && dust!=null && dust.fixedSlotIndex()==132+human;
    }
    // Sonic_CheckSpindash (s2.asm:37555-37571), Sonic_UpdateSpindash (s2.asm:37588-37637),
    // Tails_CheckSpindash (s2.asm:40470-40486), Tails_UpdateSpindash (s2.asm:40504-40550).
    // These are explicit Obj08.anim writes; teleporting a player's flag never writes this mode.
    @Override public void chargeStarted(AbstractPlayableSprite player) {
        if(player.getDrowningController().getRemainingAir()>=12)modes.set(roster.slotOf(player),2);
    }
    @Override public void chargeReleased(AbstractPlayableSprite player) {modes.set(roster.slotOf(player),0);}
    public int animation(int human){return modes.get(human);}
    @Override public boolean active(AbstractPlayableSprite player) {
        int human=roster.slotOf(player);
        // Obj08_MdSpindashDust (s2.asm:42763-42771) tests air_left, routine and raw spindash_flag.
        return modes.get(human)==2 && !player.isFixedSkidDustActive() && player.getDrowningController().getRemainingAir()>=12
                && routine.applyAsInt(human)<4 && player.getSpindash();
    }
    @Override public boolean holdsAnimation(AbstractPlayableSprite player) {
        // Obj08_ResetDisplayMode returns without AnimateSprite; Obj08_CheckSkid likewise holds its cursor.
        return modes.get(roster.slotOf(player))==2 || player.isFixedSkidDustActive();
    }
    @Override public void completed(AbstractPlayableSprite player,int nextFrame,int timer,boolean active) {
        int human=roster.slotOf(player);var previous=poses.get(human);
        if(player.getSpindashDustController().isSplashActive()
                || player.getSpindashDustController().isSurfaceSplashActive())
            throw new IllegalStateException("A competition dust owner reached a water-only animation");
        int tile=previous.artTile();ObjectSlot display=null;int frame;
        if(player.isFixedSkidDustActive()) {
            // Obj08_CheckSkid/SkidDust (s2.asm:42813-42859): no DisplaySprite; frame15 preloads the bank.
            frame=0x15;
        } else if(active) {
            // Shared cursor already points at the next entry on timer-hold passes too.
            // AnimateSprite keeps mapping_frame on those passes; select the preceding entry, no second clock.
            frame=rom.readU8(dash+1+Math.floorMod(nextFrame-1,7));
            if(!previous.dashAnimation())tile=adjustedArt(human)|(player.isHighPriority()?0x8000:0);
            int flags=4|(player.getDirection()==com.openggf.physics.Direction.LEFT?1:0);
            display=slot(frame,player.getCentreX(),player.getCentreY()-(human==1?4:0),16,flags,tile);
        } else if(modes.get(human)==2) {
            // Obj08_ResetDisplayMode (s2.asm:42804-42806) returns without a display/art call.
            modes.set(human,0);
            poses.set(human,new Pose(previous.dashAnimation(),previous.lastDplc(),tile,previous.display(),false));return;
        } else {
            frame=0;var old=previous.display();
            display=slot(0,old==null?0:old.x(),old==null?0:old.y(),16,old==null?4:old.renderFlags(),tile);
        }
        if(frame!=previous.lastDplc())loadArt(human,frame);
        poses.set(human,new Pose(active || frame!=0 && previous.dashAnimation(),frame,tile,display==null?previous.display():display,display!=null));
    }
    public ObjectSlot fixed(int human){return poses.get(human).admitted()?poses.get(human).display():null;}
    /** Called at the actual fixed slot after its existing allocation pass, never by a read-only projector. */
    public void captureChildren(int human) {
        childArt.keySet().removeIf(id->objects.objectQuery().resolve(id).filter(o->!o.isDestroyed()).isEmpty());
        for(var owner:objects.activeObjectsOfType(SkidDustObjectInstance.class)) {
            var pose=ObjectDrawInternalAccess.skidDustDisplayPose(owner);
            if(!roster.player(human).getCode().equals(pose.parentCode()))continue;
            var id=objects.objectQuery().identityOf(owner).orElseThrow();
            // Obj08_SkidDust (s2.asm:42843-42856): copied parent/art, then replace the priority bit at birth.
            childArt.putIfAbsent(id,adjustedArt(human)|(roster.player(human).isHighPriority()?0x8000:0));
        }
    }
    public ObjectSlot child(SkidDustObjectInstance owner) {
        var pose=ObjectDrawInternalAccess.skidDustDisplayPose(owner);
        // Lower dynamic SSTs allocated by132/133 do not execute until the next RunObjects walk.
        if(owner.isDestroyed() || pose.cursor()==0 && pose.timer()==3)return null;
        int index=Math.min(3,pose.cursor()-(pose.timer()==3?1:0));
        var id=objects.objectQuery().identityOf(owner).orElseThrow();
        int tile=Objects.requireNonNull(childArt.get(id),"Missing native skid birth art latch");
        // AnimateSprite's FC leaves the last mapping_frame displayed before the following DeleteObject.
        int frame=rom.readU8(skid+1+Math.max(0,index));
        return slot(frame,owner.getSpawn().x(),owner.getSpawn().y(),4,4,tile);
    }
    private ObjectSlot slot(int frame,int x,int y,int width,int flags,int tile) {
        // Obj08_Init (s2.asm:42721-42739); Obj08_Display (s2.asm:42797-42801).
        return new ObjectSlot(8,flags,x&65535,y&65535,width,0,
                new Mapping(address(rom,Sonic2Constants.MAP_UNC_OBJ08_ADDR),frame),tile,null);
    }
    private int adjustedArt(int human) {
        return Sonic2CompetitionSpriteArtTable.adjust2PArtPointer(human==0?
                Sonic2Constants.ART_TILE_SONIC_DUST:Sonic2Constants.ART_TILE_TAILS_DUST);
    }
    private void loadArt(int human,int frame) {
        // Obj08_LoadDustOrSplashArt (s2.asm:42862-42886): destination is the original 1P bank;
        // Adjust2PArtPointer changes mappings' cell index, not obj08_vram_address.
        int dplc=address(rom,Sonic2Constants.MAP_R_UNC_OBJ08_ADDR);
        int p=dplc+rom.readU16BE(dplc+frame*2),count=rom.readU16BE(p);p+=2;
        int destination=human==0?Sonic2Constants.ART_TILE_SONIC_DUST:Sonic2Constants.ART_TILE_TAILS_DUST;
        for(int i=0;i<count;i++,p+=2) {
            int word=rom.readU16BE(p),tiles=(word>>>12)+1;
            int source=address(rom,Sonic2Constants.ART_UNC_SPLASH_DUST_ADDR)+(word&0xFFF)*32;
            art.queueUpload(destination,rom.slice(source,tiles*32));destination+=tiles;
        }
    }
    @Override public String key(){return "sonic2.competition.dust-display";}
    @Override public State capture(){return new State(poses,modes,childArt);}
    @Override public void restore(State state){poses.clear();poses.addAll(state.poses());modes.clear();modes.addAll(state.modes());childArt.clear();childArt.putAll(state.childArt());}
}
