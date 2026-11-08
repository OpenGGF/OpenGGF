package com.openggf.net.hub;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TestRelevanceClassifierWrap {
 @Test void seamIsNearHorizontallyAndVertically() {
  var c=new RelevanceClassifier(); c.geometry(new TrackValidationProfile(4096,2048,32,60,4096,2048,5,12));
  c.updatePosition(0,4,4); c.updatePosition(1,4092,2044); c.updatePosition(2,2000,1000); c.rebucket();
  assertEquals(java.util.Set.of(1),c.nearSetFor(0));
 }
}
