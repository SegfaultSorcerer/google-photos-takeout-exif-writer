package dev.pneumann.gptew;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic sanity tests for the application
 */
public class BasicsTest {

  @Test
  void testAppClassExists() {
    // Verify the main App class exists and can be instantiated
    assertDoesNotThrow(() -> Class.forName("dev.pneumann.gptew.App"));
  }

  @Test
  void testMainMethodExists() throws Exception {
    // Verify App has a main method
    Class<?> appClass = Class.forName("dev.pneumann.gptew.App");
    assertNotNull(appClass.getMethod("main", String[].class));
  }
}
