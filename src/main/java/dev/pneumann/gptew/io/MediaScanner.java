package dev.pneumann.gptew.io;

import dev.pneumann.gptew.exif.JpegExifWriter;
import dev.pneumann.gptew.json.GoogleSidecar;
import dev.pneumann.gptew.json.SidecarParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Stream;

public final class MediaScanner {
  private final boolean dryRun;
  private final boolean setFileTimes;
  private final boolean backup;
  private final SidecarParser parser = new SidecarParser();
  private final JpegExifWriter jpegWriter = new JpegExifWriter();

  public MediaScanner(boolean dryRun, boolean setFileTimes, boolean backup) {
    this.dryRun = dryRun;
    this.setFileTimes = setFileTimes;
    this.backup = backup;
  }

  public void process(Path root, boolean recursive) {
    try (Stream<Path> walk = recursive ? Files.walk(root) : Files.list(root)) {
      walk.filter(Files::isRegularFile)
          .filter(this::isMediaFile)
          .forEach(this::processMedia);
    } catch (IOException e) {
      System.err.println("Error scanning directory: " + e.getMessage());
      System.exit(2);
    }
  }

  private boolean isMediaFile(Path p) {
    String name = p.getFileName().toString().toLowerCase();
    return name.endsWith(".jpg") || name.endsWith(".jpeg");
  }

  private void processMedia(Path media) {
    Path sidecar = findSidecar(media);
    if (sidecar == null) {
      System.out.println("SKIP\t" + media + "\t(no sidecar)");
      return;
    }

    try {
      GoogleSidecar sc = parser.parse(sidecar);
      if (sc.photoTakenTimeTimestamp() == null && sc.latitude() == null) {
        System.out.println("SKIP\t" + media + "\t(sidecar has no relevant data)");
        return;
      }

      if (dryRun) {
        System.out.println("DRY-RUN\t" + media + "\t" + formatSidecarInfo(sc));
      } else {
        jpegWriter.apply(media, sc, backup);
        if (setFileTimes && sc.photoTakenTimeTimestamp() != null) {
          Files.setLastModifiedTime(media, FileTime.from(Instant.ofEpochSecond(sc.photoTakenTimeTimestamp())));
        }
        System.out.println("OK\t" + media + "\t" + formatSidecarInfo(sc));
      }
    } catch (Exception e) {
      System.err.println("ERROR\t" + media + "\t" + e.getMessage());
    }
  }

  private Path findSidecar(Path media) {
    Path candidate = media.resolveSibling(media.getFileName() + ".json");
    return Files.exists(candidate) ? candidate : null;
  }

  private String formatSidecarInfo(GoogleSidecar sc) {
    StringBuilder sb = new StringBuilder();
    if (sc.photoTakenTimeTimestamp() != null) {
      sb.append("time=").append(Instant.ofEpochSecond(sc.photoTakenTimeTimestamp()));
    }
    if (sc.latitude() != null && sc.longitude() != null) {
      if (sb.length() > 0) sb.append(" ");
      sb.append("gps=(").append(sc.latitude()).append(",").append(sc.longitude()).append(")");
    }
    return sb.toString();
  }
}
