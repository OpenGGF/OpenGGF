package com.openggf.game.soniccd;
import com.openggf.game.save.SaveManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;
/** An equal DATA adapter and selected module grant no live loading/ordinary invocation. */
@Isolated class TestCdOriginalPastShieldOwner extends TestCdOriginalR11LoadingWorld {
 @Test void inactiveSelectedModuleCannotCopyOrPublishShieldArt()throws Exception{
  var source=TestCdOriginalBootPrefix.source();var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);var m=attempt.module().orElseThrow();
  var call=SonicCdGameModule.class.getDeclaredMethod("copyOriginalR11Powerup");call.setAccessible(true);
  var failure=assertThrows(InvocationTargetException.class,()->call.invoke(m));assertInstanceOf(IllegalStateException.class,failure.getCause());assertEquals(0,failure.getCause().getSuppressed().length);
 }
}
