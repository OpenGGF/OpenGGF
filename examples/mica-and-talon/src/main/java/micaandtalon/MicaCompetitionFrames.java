package micaandtalon;
import com.openggf.game.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.util.*;
/** StageSpriteSet.S3K_COMPETITION miniature body mappings for MICA. Indexed images and per-selector origins replace the native body while the host owns race mechanics and timing. */
final class MicaCompetitionFrames {
static StageSpriteSet load(ModAssetRoot assets) throws java.io.IOException {
var frames=new LinkedHashMap<Integer,StageSpriteSet.Frame>();
frames.put(0,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/00.png"),0,0));
frames.put(1,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/01.png"),12,9));
frames.put(2,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/02.png"),9,9));
frames.put(3,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/03.png"),9,10));
frames.put(4,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/04.png"),10,10));
frames.put(5,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/05.png"),10,10));
frames.put(6,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/06.png"),11,11));
frames.put(7,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/07.png"),11,11));
frames.put(8,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/08.png"),10,7));
frames.put(9,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/09.png"),12,8));
frames.put(10,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/0a.png"),9,12));
frames.put(11,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/0b.png"),10,11));
frames.put(12,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/0c.png"),11,12));
frames.put(13,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/0d.png"),10,9));
frames.put(14,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/0e.png"),9,9));
frames.put(15,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/0f.png"),10,11));
frames.put(16,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/10.png"),9,10));
frames.put(17,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/11.png"),9,9));
frames.put(18,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/12.png"),9,9));
frames.put(19,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/13.png"),12,10));
frames.put(20,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/14.png"),11,10));
frames.put(21,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/15.png"),8,9));
frames.put(22,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/16.png"),7,9));
frames.put(23,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/17.png"),7,8));
frames.put(24,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/18.png"),8,10));
frames.put(25,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/19.png"),7,11));
frames.put(26,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/1a.png"),7,9));
frames.put(27,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/1b.png"),8,9));
frames.put(28,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/1c.png"),8,9));
frames.put(29,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/1d.png"),8,8));
frames.put(30,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/1e.png"),9,8));
frames.put(31,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/1f.png"),10,10));
frames.put(32,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/20.png"),10,11));
frames.put(33,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/21.png"),9,10));
frames.put(34,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/22.png"),10,10));
frames.put(35,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/23.png"),6,4));
frames.put(36,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/24.png"),7,4));
frames.put(37,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/25.png"),10,8));
frames.put(38,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/26.png"),7,7));
frames.put(39,frames.get(38));
frames.put(40,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/28.png"),9,10));
frames.put(41,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/29.png"),9,8));
frames.put(42,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/2a.png"),8,9));
frames.put(43,new StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/competition/2b.png"),6,2));
return new StageSpriteSet(frames);
}
}
