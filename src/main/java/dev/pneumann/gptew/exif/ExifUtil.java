package dev.pneumann.gptew.exif;

import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;

public final class ExifUtil {
  private ExifUtil(){}

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

  public static RationalNumber toRational(double v) {
    long num = Math.round(v * 100.0);
    return new RationalNumber((int) num, 100);
  }

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
