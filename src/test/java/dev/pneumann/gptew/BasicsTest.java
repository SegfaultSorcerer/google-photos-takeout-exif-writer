package dev.pneumann.gptew;

import dev.pneumann.gptew.exif.ExifUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BasicsTest {
  @Test void dmsConversion() {
    var dms = ExifUtil.toDms(50.123456);
    assertEquals(3, dms.length);
    assertEquals("50/1", dms[0].toString());
  }
}
