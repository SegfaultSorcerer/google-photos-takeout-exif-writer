package dev.pneumann.gptew.exif;

import org.apache.commons.imaging.common.RationalNumber;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExifUtilTest {

  @Test
  void testToDms_PositiveLatitude() {
    // Test: 50.123456° → 50° 7' 24.4416"
    RationalNumber[] dms = ExifUtil.toDms(50.123456);

    assertEquals(3, dms.length);
    assertEquals(50, dms[0].intValue(), "Degrees should be 50");
    assertEquals(7, dms[1].intValue(), "Minutes should be 7");
    assertTrue(Math.abs(24.4416 - dms[2].doubleValue()) < 0.001, "Seconds should be ~24.44");
  }

  @Test
  void testToDms_NegativeLatitude() {
    // Test: -50.123456° → 50° 7' 24.4416" (absolute value)
    RationalNumber[] dms = ExifUtil.toDms(-50.123456);

    assertEquals(3, dms.length);
    assertEquals(50, dms[0].intValue(), "Degrees should be 50 (absolute)");
    assertEquals(7, dms[1].intValue(), "Minutes should be 7");
    assertTrue(Math.abs(24.4416 - dms[2].doubleValue()) < 0.001, "Seconds should be ~24.44");
  }

  @Test
  void testToDms_ZeroCoordinate() {
    // Test: 0.0° → 0° 0' 0"
    RationalNumber[] dms = ExifUtil.toDms(0.0);

    assertEquals(3, dms.length);
    assertEquals(0, dms[0].intValue(), "Degrees should be 0");
    assertEquals(0, dms[1].intValue(), "Minutes should be 0");
    assertEquals(0.0, dms[2].doubleValue(), "Seconds should be 0.0");
  }

  @Test
  void testToDms_MaxLatitude() {
    // Test: 90.0° → 90° 0' 0"
    RationalNumber[] dms = ExifUtil.toDms(90.0);

    assertEquals(3, dms.length);
    assertEquals(90, dms[0].intValue(), "Degrees should be 90");
    assertEquals(0, dms[1].intValue(), "Minutes should be 0");
    assertEquals(0.0, dms[2].doubleValue(), "Seconds should be 0.0");
  }

  @Test
  void testToDms_MaxLongitude() {
    // Test: 180.0° → 180° 0' 0"
    RationalNumber[] dms = ExifUtil.toDms(180.0);

    assertEquals(3, dms.length);
    assertEquals(180, dms[0].intValue(), "Degrees should be 180");
    assertEquals(0, dms[1].intValue(), "Minutes should be 0");
    assertEquals(0.0, dms[2].doubleValue(), "Seconds should be 0.0");
  }

  @Test
  void testToDms_FractionalDegrees() {
    // Test: 13.404954° (Berlin) → 13° 24' 17.8344"
    RationalNumber[] dms = ExifUtil.toDms(13.404954);

    assertEquals(3, dms.length);
    assertEquals(13, dms[0].intValue(), "Degrees should be 13");
    assertEquals(24, dms[1].intValue(), "Minutes should be 24");
    assertTrue(Math.abs(17.8344 - dms[2].doubleValue()) < 0.001, "Seconds should be ~17.83");
  }

  @Test
  void testToRational_PositiveValue() {
    RationalNumber result = ExifUtil.toRational(123.45);

    // 123.45 * 100 = 12345 / 100
    assertEquals(12345, result.numerator);
    assertEquals(100, result.divisor);
  }

  @Test
  void testToRational_NegativeValue() {
    RationalNumber result = ExifUtil.toRational(-123.45);

    // -123.45 * 100 = -12345 / 100
    assertEquals(-12345, result.numerator);
    assertEquals(100, result.divisor);
  }

  @Test
  void testToRational_Zero() {
    RationalNumber result = ExifUtil.toRational(0.0);

    assertEquals(0, result.numerator);
    assertEquals(100, result.divisor);
  }

  @Test
  void testToRational_SmallValue() {
    RationalNumber result = ExifUtil.toRational(0.12);

    // 0.12 * 100 = 12 / 100
    assertEquals(12, result.numerator);
    assertEquals(100, result.divisor);
  }

  @Test
  void testToRational_Altitude() {
    // Typical altitude value (meters)
    RationalNumber result = ExifUtil.toRational(543.7);

    assertEquals(54370, result.numerator);
    assertEquals(100, result.divisor);
    assertTrue(Math.abs(543.7 - result.doubleValue()) < 0.01);
  }
}
