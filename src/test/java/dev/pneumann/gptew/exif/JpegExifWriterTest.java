package dev.pneumann.gptew.exif;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import dev.pneumann.gptew.json.GoogleSidecar;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class JpegExifWriterTest {

  private final JpegExifWriter writer = new JpegExifWriter();
  private static final DateTimeFormatter EXIF_FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

  /**
   * Creates a simple test JPEG image without EXIF data
   */
  private Path createTestJpeg(Path dir, String name) throws IOException {
    BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.BLUE);
    g.fillRect(0, 0, 100, 100);
    g.dispose();

    Path jpegPath = dir.resolve(name);
    ImageIO.write(img, "jpg", jpegPath.toFile());
    return jpegPath;
  }

  @Test
  void testApply_WritesPhotoTakenTime(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test.jpg");

    // Create sidecar with timestamp: Sep 12, 2021, 1:53:09 PM UTC
    long timestamp = 1631456389L;
    GoogleSidecar sidecar = new GoogleSidecar(
        timestamp, null, null,
        null, null, null,
        null, null
    );

    writer.apply(jpeg, sidecar, false);

    // Verify using metadata-extractor
    Metadata metadata = ImageMetadataReader.readMetadata(jpeg.toFile());
    ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

    assertNotNull(exifDir, "EXIF directory should exist");

    String dateTimeOriginal = exifDir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
    assertNotNull(dateTimeOriginal, "DateTimeOriginal should be set");

    LocalDateTime expected = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    assertEquals(EXIF_FMT.format(expected), dateTimeOriginal);
  }

  @Test
  void testApply_WritesGpsCoordinates(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-gps.jpg");

    // Berlin coordinates
    GoogleSidecar sidecar = new GoogleSidecar(
        null, null, null,
        52.520008, 13.404954, null,
        null, null
    );

    writer.apply(jpeg, sidecar, false);

    // Verify using metadata-extractor
    Metadata metadata = ImageMetadataReader.readMetadata(jpeg.toFile());
    GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);

    assertNotNull(gpsDir, "GPS directory should exist");

    // Check latitude
    Double latitude = gpsDir.getGeoLocation().getLatitude();
    assertNotNull(latitude);
    assertEquals(52.520008, latitude, 0.0001, "Latitude should match");

    // Check longitude
    Double longitude = gpsDir.getGeoLocation().getLongitude();
    assertNotNull(longitude);
    assertEquals(13.404954, longitude, 0.0001, "Longitude should match");
  }

  @Test
  void testApply_WritesNegativeCoordinates(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-negative-coords.jpg");

    // Buenos Aires coordinates (southern/western hemisphere)
    GoogleSidecar sidecar = new GoogleSidecar(
        null, null, null,
        -34.603684, -58.381559, null,
        null, null
    );

    writer.apply(jpeg, sidecar, false);

    // Verify
    Metadata metadata = ImageMetadataReader.readMetadata(jpeg.toFile());
    GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);

    assertNotNull(gpsDir);

    Double latitude = gpsDir.getGeoLocation().getLatitude();
    Double longitude = gpsDir.getGeoLocation().getLongitude();

    assertEquals(-34.603684, latitude, 0.0001, "Negative latitude");
    assertEquals(-58.381559, longitude, 0.0001, "Negative longitude");
  }

  @Test
  void testApply_WritesAltitude(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-altitude.jpg");

    GoogleSidecar sidecar = new GoogleSidecar(
        null, null, null,
        48.858844, 2.294351, 35.0,
        null, null
    );

    writer.apply(jpeg, sidecar, false);

    // Verify using Commons Imaging
    ImageMetadata metadata = Imaging.getMetadata(jpeg.toFile());
    JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
    TiffImageMetadata exif = jpegMetadata.getExif();

    assertNotNull(exif);

    // Check altitude
    Object altitude = exif.getFieldValue(GpsTagConstants.GPS_TAG_GPS_ALTITUDE);
    assertNotNull(altitude, "Altitude should be set");
  }

  @Test
  void testApply_WritesFullMetadata(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-full.jpg");

    long timestamp = 1631456389L;
    GoogleSidecar sidecar = new GoogleSidecar(
        timestamp, null, 1631456391L,
        52.520008, 13.404954, 100.0,
        "Test description", "Test title"
    );

    writer.apply(jpeg, sidecar, false);

    // Verify timestamp
    Metadata metadata = ImageMetadataReader.readMetadata(jpeg.toFile());
    ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
    assertNotNull(exifDir);

    String dateTimeOriginal = exifDir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
    assertNotNull(dateTimeOriginal);

    // Verify GPS
    GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
    assertNotNull(gpsDir);
    assertNotNull(gpsDir.getGeoLocation());
  }

  @Test
  void testApply_CreatesBackup(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-backup.jpg");
    byte[] originalContent = Files.readAllBytes(jpeg);

    GoogleSidecar sidecar = new GoogleSidecar(
        1631456389L, null, null,
        null, null, null,
        null, null
    );

    // Apply with backup=true
    writer.apply(jpeg, sidecar, true);

    // Check backup file exists
    Path backup = jpeg.resolveSibling(jpeg.getFileName() + ".bak");
    assertTrue(Files.exists(backup), "Backup file should exist");

    // Verify backup content matches original
    byte[] backupContent = Files.readAllBytes(backup);
    assertArrayEquals(originalContent, backupContent, "Backup should match original");
  }

  @Test
  void testApply_NoBackup(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-no-backup.jpg");

    GoogleSidecar sidecar = new GoogleSidecar(
        1631456389L, null, null,
        null, null, null,
        null, null
    );

    // Apply with backup=false
    writer.apply(jpeg, sidecar, false);

    // Check backup file does NOT exist
    Path backup = jpeg.resolveSibling(jpeg.getFileName() + ".bak");
    assertFalse(Files.exists(backup), "Backup file should not exist");
  }

  @Test
  void testApply_OverwritesExistingExif(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-overwrite.jpg");

    // First write
    GoogleSidecar sidecar1 = new GoogleSidecar(
        1000000000L, null, null,
        10.0, 20.0, null,
        null, null
    );
    writer.apply(jpeg, sidecar1, false);

    // Second write (should overwrite)
    GoogleSidecar sidecar2 = new GoogleSidecar(
        2000000000L, null, null,
        30.0, 40.0, null,
        null, null
    );
    writer.apply(jpeg, sidecar2, false);

    // Verify second values were written
    Metadata metadata = ImageMetadataReader.readMetadata(jpeg.toFile());
    GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);

    assertEquals(30.0, gpsDir.getGeoLocation().getLatitude(), 0.0001);
    assertEquals(40.0, gpsDir.getGeoLocation().getLongitude(), 0.0001);
  }

  @Test
  void testApply_NullTimestampDoesNotCrash(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-null-timestamp.jpg");

    GoogleSidecar sidecar = new GoogleSidecar(
        null, null, null,
        52.520008, 13.404954, null,
        null, null
    );

    // Should not throw exception
    assertDoesNotThrow(() -> writer.apply(jpeg, sidecar, false));

    // GPS should still be written
    Metadata metadata = ImageMetadataReader.readMetadata(jpeg.toFile());
    GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
    assertNotNull(gpsDir);
  }

  @Test
  void testApply_NullGpsDoesNotCrash(@TempDir Path tempDir) throws Exception {
    Path jpeg = createTestJpeg(tempDir, "test-null-gps.jpg");

    GoogleSidecar sidecar = new GoogleSidecar(
        1631456389L, null, null,
        null, null, null,
        null, null
    );

    // Should not throw exception
    assertDoesNotThrow(() -> writer.apply(jpeg, sidecar, false));

    // Timestamp should still be written
    Metadata metadata = ImageMetadataReader.readMetadata(jpeg.toFile());
    ExifSubIFDDirectory exifDir = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
    assertNotNull(exifDir);
    assertNotNull(exifDir.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL));
  }
}
