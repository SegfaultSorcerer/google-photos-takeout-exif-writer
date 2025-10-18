package dev.pneumann.gptew.exif;

import dev.pneumann.gptew.json.GoogleSidecar;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The {@code JpegExifWriter} class provides functionality to update EXIF metadata
 * of JPEG files. This includes updating timestamps, GPS information, and other metadata
 * based on the information provided in an external {@code GoogleSidecar} object. The metadata
 * modifications are applied in a lossless manner, preserving image quality.
 *
 * This class ensures compatibility with standard EXIF specifications and utilizes
 * third-party libraries to handle metadata extraction and modification.
 *
 * Key features include:
 * - Updating photo taken, modification, and creation timestamps.
 * - Embedding GPS coordinates (latitude, longitude, and optional altitude).
 * - Optionally backing up the original JPEG file before modifications.
 *
 * It employs a temporary file mechanism to ensure atomic updates to metadata,
 * thus preventing corruption or partial writes in case of interruptions.
 *
 * The class is designed to be final to prevent inheritance.
 *
 * @author Patrik Neumann
 */
public final class JpegExifWriter {
  private static final DateTimeFormatter EXIF_FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

  /**
   * Updates the EXIF metadata of a JPEG file using information provided in a {@code GoogleSidecar} record.
   *
   * @param jpeg the path to the JPEG file whose metadata is to be updated
   * @param sc the {@code GoogleSidecar} containing information to write into the EXIF metadata, such as timestamps, GPS coordinates, and more
   * @param backup whether to create a backup of the original JPEG file before applying changes
   * @throws IOException if an I/O error occurs during reading, writing, or file manipulation
   * @throws ImagingException if an error occurs while processing the image or its metadata
   */
  public void apply(Path jpeg, GoogleSidecar sc, boolean backup) throws IOException, ImagingException {
    var metadata = Imaging.getMetadata(jpeg.toFile());
    TiffOutputSet output = null;
    if (metadata instanceof JpegImageMetadata jim) {
      var exif = jim.getExif();
      if (exif != null) output = exif.getOutputSet();
    }
    if (output == null) output = new TiffOutputSet();

    var exifDir = output.getOrCreateExifDirectory();
    if (sc.photoTakenTimeTimestamp() != null) {
      var ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(sc.photoTakenTimeTimestamp()), ZoneId.systemDefault());
      var s = EXIF_FMT.format(ldt);
      exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
      exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, s);

      // "CreateDate" in EXIF is DateTimeDigitized
      exifDir.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
      exifDir.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, s);
    }
    if (sc.modificationTimeTimestamp() != null) {
      var ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(sc.modificationTimeTimestamp()), ZoneId.systemDefault());
      var s = EXIF_FMT.format(ldt);
      // ModifyDate is a baseline TIFF tag
      exifDir.removeField(TiffTagConstants.TIFF_TAG_DATE_TIME);
      exifDir.add(TiffTagConstants.TIFF_TAG_DATE_TIME, s);
    }

    if (sc.latitude() != null && sc.longitude() != null) {
      // Manually set GPS data since setGPSInDegrees is not available in this version
      var gpsDir = output.getOrCreateGpsDirectory();
      ExifUtil.setGPSCoordinates(gpsDir, sc.latitude(), sc.longitude());
      // Altitude is optional; could be extended later
      if (sc.altitude() != null) {
        ExifUtil.setGPSAltitude(gpsDir, sc.altitude());
      }
    }

    var tmp = jpeg.resolveSibling(jpeg.getFileName().toString() + ".tmp");
    try (var os = Files.newOutputStream(tmp)) {
      new ExifRewriter().updateExifMetadataLossless(jpeg.toFile(), os, output);
    }
    if (backup) Files.copy(jpeg, jpeg.resolveSibling(jpeg.getFileName()+".bak"), StandardCopyOption.REPLACE_EXISTING);
    Files.move(tmp, jpeg, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }
}
