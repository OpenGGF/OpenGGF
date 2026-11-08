package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;
import static com.openggf.game.soniccd.level.CdProgrammeDefinition.*;

/** Closed original placement DATA spans, independently matched to native tables and original MMDs.
 * StageObjectSpawnInit (src/common/object_spawn.asm:30-47),
 * CheckObjectTimeZone (src/common/object_spawn.asm:210-223),
 * StageObjectSpawnLoadRecord (src/common/object_spawn.asm:242-280).
 * Original physical row order and full mask/subtype bytes are preserved; no live spawn is admitted.
 */
final class CdProgrammePlacementProfiles {
    private CdProgrammePlacementProfiles() { }
    private record Fact(int offset,int bytes,String sha,CdObjectPlacements.Layout layout,int rows) { }
    static void configure(Builder b,CdStagePayload payload) {
        var f=switch(payload) {
            case R61A -> new Fact(0x79CC,3714,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a",CdObjectPlacements.Layout.ORIGINAL_461_60,461);
            case R61B -> new Fact(0x796A,3714,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a",CdObjectPlacements.Layout.ORIGINAL_461_60,461);
            case R61C -> new Fact(0x797C,3714,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a",CdObjectPlacements.Layout.ORIGINAL_461_60,461);
            case R61D -> new Fact(0x79A4,3714,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a",CdObjectPlacements.Layout.ORIGINAL_461_60,461);
            case R62A -> new Fact(0x7980,2890,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49",CdObjectPlacements.Layout.ORIGINAL_358_60,358);
            case R62B -> new Fact(0x7966,2890,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49",CdObjectPlacements.Layout.ORIGINAL_358_60,358);
            case R62C -> new Fact(0x7924,2890,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49",CdObjectPlacements.Layout.ORIGINAL_358_60,358);
            case R62D -> new Fact(0x794C,2890,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49",CdObjectPlacements.Layout.ORIGINAL_358_60,358);
            case R63C -> new Fact(0x78D8,602,"b5d03a0be3fde57fa3d243f1203905bc7094ea9b17e5d7bab68f0756db8e2db7",CdObjectPlacements.Layout.ORIGINAL_72_64,72);
            case R63D -> new Fact(0x7900,602,"b5d03a0be3fde57fa3d243f1203905bc7094ea9b17e5d7bab68f0756db8e2db7",CdObjectPlacements.Layout.ORIGINAL_72_64,72);
            case R71A -> new Fact(0x78E4,5146,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611",CdObjectPlacements.Layout.ORIGINAL_640_60,640);
            case R71B -> new Fact(0x791A,5146,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611",CdObjectPlacements.Layout.ORIGINAL_640_60,640);
            case R71C -> new Fact(0x79A6,5146,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611",CdObjectPlacements.Layout.ORIGINAL_640_60,640);
            case R71D -> new Fact(0x7996,5146,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611",CdObjectPlacements.Layout.ORIGINAL_640_60,640);
            case R72A -> new Fact(0x78E4,6602,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58",CdObjectPlacements.Layout.ORIGINAL_822_60,822);
            case R72B -> new Fact(0x791A,6602,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58",CdObjectPlacements.Layout.ORIGINAL_822_60,822);
            case R72C -> new Fact(0x79A6,6602,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58",CdObjectPlacements.Layout.ORIGINAL_822_60,822);
            case R72D -> new Fact(0x7996,6602,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58",CdObjectPlacements.Layout.ORIGINAL_822_60,822);
            case R73C -> new Fact(0x783A,658,"f7846f26330b6ef436032f03dd5b72ae7cadd6c43e79f218c83ef76ddd623b4b",CdObjectPlacements.Layout.ORIGINAL_79_60,79);
            case R73D -> new Fact(0x7848,658,"f7846f26330b6ef436032f03dd5b72ae7cadd6c43e79f218c83ef76ddd623b4b",CdObjectPlacements.Layout.ORIGINAL_79_60,79);
            default -> throw new UnsupportedOperationException("No closed original placement span");
        };
        var key=b.joined("ORIGINAL_PLACEMENTS",f.offset(),f.bytes(),f.sha());
        b.placements=new PlacementBinding(key,f.layout(),f.rows());
    }
}
