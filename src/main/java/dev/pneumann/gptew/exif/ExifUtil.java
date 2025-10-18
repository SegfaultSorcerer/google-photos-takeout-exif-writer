package dev.pneumann.gptew.exif;

import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;

/**
 * Utility class for working with EXIF metadata, particularly GPS-related information.
 * This class provides methods to convert coordinate data into EXIF-compliant formats
 * and to set GPS metadata values such as latitude, longitude, and altitude
 * in TIFF output directories.
 *
 * This is a final class with a private constructor, and all its methods are static.
 *
 * @author Patrik Neumann
 */
public final class ExifUtil {
  private ExifUtil(){}

  /**
   * Converts a coordinate in decimal degrees format to an array of `RationalNumber` objects
   * representing degrees, minutes, and seconds.
   *
   * @param coord the coordinate in decimal degrees
   * @return an array of three `RationalNumber` objects representing degrees, minutes, and seconds
   */
  public static RationalNumber[] toDms(double coord) {
    double a = Math.abs(coord);
    int deg = (int) a;
    double rem = (a - deg) * 60.0;
    int min = (int) rem;
    double sec = (rem - min) * 60.0;
    return new RationalNumber[] {
      RationalNumber.valueOf(deg),
      RationalNumber.valueOf(min),
      RationalNumber.valueOf(sec)
    };
  }

  /**
   * Converts a double-precision floating-point number into a `RationalNumber`.
   * The conversion involves scaling the input by 100 and representing it as
   * a fraction with a denominator of 100.
   *
   * @param v the double value to be converted to a `RationalNumber`
   * @return a `RationalNumber` representation of the given double value
   */
  public static RationalNumber toRational(double v) {
    long num = Math.round(v * 100.0);
    return new RationalNumber((int) num, 100);
  }

  /**
   * Sets the GPS coordinates (latitude and longitude) in the specified TIFF output directory.
   * The coordinates are written to the GPS EXIF metadata fields in degrees, minutes, and seconds (DMS) format.
   *
   * @param gpsDir the TIFF output directory where GPS metadata will be written
   * @param lat the latitude value in decimal degrees; positive values represent north, negative values represent south
   * @param lon the longitude value in decimal degrees; positive values represent east, negative values represent west
   * @throws RuntimeException if an error occurs while setting the GPS coordinates
   */
  public static void setGPSCoordinates(TiffOutputDirectory gpsDir, double lat, double lon) {
    try {
      // Latitude
      gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF);
      gpsDir.add(GpsTagConstants.GPS_TAG_GPS_LATITUDE_REF, lat >= 0 ? "N" : "S");
      gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LATITUDE);
      gpsDir.add(GpsTagConstants.GPS_TAG_GPS_LATITUDE, toDms(lat));

      // Longitude
      gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF);
      gpsDir.add(GpsTagConstants.GPS_TAG_GPS_LONGITUDE_REF, lon >= 0 ? "E" : "W");
      gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_LONGITUDE);
      gpsDir.add(GpsTagConstants.GPS_TAG_GPS_LONGITUDE, toDms(lon));
    } catch (Exception e) {
      throw new RuntimeException("Failed to set GPS coordinates", e);
    }
  }

  /**
   * Sets the GPS altitude in the specified TIFF output directory.
   * The altitude is written to the GPS EXIF metadata fields in meters.
   * Also sets the altitude reference, indicating whether the altitude is above or below sea level.
   *
   * @param gpsDir the TIFF output directory where GPS metadata will be written
   * @param altitude the altitude value in meters; positive values indicate altitude above sea level,
   *                 negative values indicate altitude below sea level
   * @throws RuntimeException if an error occurs while setting the GPS altitude
   */
  public static void setGPSAltitude(TiffOutputDirectory gpsDir, double altitude) {
    try {
      gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF);
      gpsDir.add(GpsTagConstants.GPS_TAG_GPS_ALTITUDE_REF, altitude >= 0 ? (byte) 0 : (byte) 1);
      gpsDir.removeField(GpsTagConstants.GPS_TAG_GPS_ALTITUDE);
      gpsDir.add(GpsTagConstants.GPS_TAG_GPS_ALTITUDE, toRational(Math.abs(altitude)));
    } catch (Exception e) {
      throw new RuntimeException("Failed to set GPS altitude", e);
    }
  }
}
