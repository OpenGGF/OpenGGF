package com.openggf.net.hub;
import com.openggf.net.ghost.*;
import com.openggf.net.protocol.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TestGhostStreamValidatorWrap {
 @Test void wrapAcceptedButUnloggedJumpRejected() {
  var v=new GhostStreamValidator(new TrackValidationProfile(512,256,32,60,512,256,5,12),()->0,(k,d)->{});
  v.onAttemptStart(1);
  assertEquals(GhostStreamValidator.Verdict.ACCEPT,batch(v,0,508,252));
  assertEquals(GhostStreamValidator.Verdict.ACCEPT,batch(v,1,4,4));
  assertEquals(GhostStreamValidator.Verdict.DROP,batch(v,2,200,100));
  v.admitSwap(new ControlMessage.SwapTarget(200,100,0,0,false,false,0,false,0,0,0),2);
  assertEquals(GhostStreamValidator.Verdict.ACCEPT,batch(v,2,200,100));
  assertEquals(GhostStreamValidator.Verdict.DROP,batch(v,3,4,4));
 }
 @Test void rejectedBatchDoesNotConsumeLoggedDiscontinuity() {
  var v = new GhostStreamValidator(new TrackValidationProfile(1024,512,32,60),()->0,(k,d)->{});
  v.onAttemptStart(1);
  assertEquals(GhostStreamValidator.Verdict.ACCEPT,batch(v,0,100,100));
  v.admitSwap(new ControlMessage.SwapTarget(500,100,0,0,false,false,0,false,0,0,0),1);
  byte[] data = new byte[2 * GhostFrameCodec.BYTES];
  GhostFrameCodec.encode(new GhostFrame(500,100,0,false,false,false,0,false),data,0);
  GhostFrameCodec.encode(new GhostFrame(900,100,0,false,false,false,0,false),data,7);
  assertEquals(GhostStreamValidator.Verdict.DROP,v.onBatch(new GhostPackets.FramesBatch(1,1,2,data)));
  assertEquals(GhostStreamValidator.Verdict.ACCEPT,batch(v,1,500,100));
 }
 GhostStreamValidator.Verdict batch(GhostStreamValidator v,int index,int x,int y) {
  byte[] data=new byte[GhostFrameCodec.BYTES]; GhostFrameCodec.encode(new GhostFrame(x,y,0,false,false,false,0,false),data,0);
  return v.onBatch(new GhostPackets.FramesBatch(1,index,1,data));
 }
}
