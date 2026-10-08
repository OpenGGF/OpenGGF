package starfall;

/** Geography belongs to the creator world; zone and music IDs follow S3K's ROM tables. */
public enum Biome {
    ANGEL_ISLAND("ANGEL ISLAND",0,0x01,0x02,0xFF709C61,0xFF243868),
    MARBLE_GARDEN("MARBLE GARDEN",2,0x05,0x06,0xFFBCA478,0xFF544968),
    MUSHROOM_HILL("MUSHROOM HILL",7,0x0F,0x10,0xFF68A044,0xFF284830),
    CARNIVAL_NIGHT("CARNIVAL NIGHT",3,0x07,0x08,0xFFD068A8,0xFF301850),
    ICECAP("ICECAP",5,0x0B,0x0C,0xFFBCD8F0,0xFF284870),
    SANDOPOLIS("SANDOPOLIS",8,0x11,0x12,0xFFD8B060,0xFF584028),
    LAUNCH_BASE("LAUNCH BASE",6,0x0D,0x0E,0xFFA07858,0xFF283850),
    HYDROCITY("HYDROCITY",1,0x03,0x04,0xFF58A8B0,0xFF102848),
    LAVA_REEF("LAVA REEF",9,0x13,0x14,0xFFB86840,0xFF381828),
    HIDDEN_PALACE("HIDDEN PALACE",22,0x14,0x14,0xFF80B898,0xFF183838),
    SKY_SANCTUARY("SKY SANCTUARY",10,0x15,0x15,0xFFDED4A0,0xFF6888C0);

    public final String label;
    public final int zone,music1,music2,color,sky;
    Biome(String label,int zone,int music1,int music2,int color,int sky) {
        this.label=label;this.zone=zone;this.music1=music1;this.music2=music2;this.color=color;this.sky=sky;
    }
    public static Biome surface(int tx) {
        if(tx<64)return ANGEL_ISLAND;
        if(tx<96)return MARBLE_GARDEN;
        if(tx<128)return MUSHROOM_HILL;
        if(tx<160)return CARNIVAL_NIGHT;
        if(tx<192)return ICECAP;
        if(tx<224)return SANDOPOLIS;
        return LAUNCH_BASE;
    }
    public static Biome at(World world,int tx,int ty) {
        int depth=ty-world.surface(tx);
        int gx=world.geographyX(tx);
        if(gx>=64&&ty<world.surface(tx)-world.depthTiles(14))return SKY_SANCTUARY;
        if(depth>=world.depthTiles(43)&&gx>=96&&gx<160)return HIDDEN_PALACE;
        if(depth>=world.depthTiles(30))return LAVA_REEF;
        if(depth>=world.depthTiles(8))return gx<192?HYDROCITY:SANDOPOLIS;
        return surface(gx);
    }
    public int act(World world,int tx,int ty) {
        int depth=ty-world.surface(tx);
        return this==HIDDEN_PALACE||this==SANDOPOLIS&&depth>=world.depthTiles(8)
                ||this==LAVA_REEF&&depth>=world.depthTiles(45)||this==HYDROCITY&&depth>=world.depthTiles(20)?1:0;
    }
    public int music(World world,int tx,int ty){return act(world,tx,ty)==0?music1:music2;}
}
