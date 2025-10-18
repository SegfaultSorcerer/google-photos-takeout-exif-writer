package dev.pneumann.gptew.io;

import dev.pneumann.gptew.exif.JpegExifWriter;
import dev.pneumann.gptew.json.GoogleSidecar;
import dev.pneumann.gptew.json.SidecarParser;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class MediaScanner {
  private final boolean dryRun;
  private final boolean setFileTimes;
  private final boolean backup;
  private final SidecarParser parser = new SidecarParser();
  private final JpegExifWriter jpegWriter = new JpegExifWriter();

  // Statistics
  private int totalProcessed = 0;
  private int successCount = 0;
  private int skipNoSidecar = 0;
  private int skipNoData = 0;
  private int errorCount = 0;
  private final List<String> skippedFiles = new ArrayList<>();
  private final List<String> errorFiles = new ArrayList<>();

  // Logging
  private BufferedWriter logWriter;
  private Path logFile;

  public MediaScanner(boolean dryRun, boolean setFileTimes, boolean backup) {
    this.dryRun = dryRun;
    this.setFileTimes = setFileTimes;
    this.backup = backup;
  }

  public void process(Path root, boolean recursive) {
    // Initialize log file
    initLogFile(root);

    log("=".repeat(80));
    log("GPTEW - Google Photos Takeout Exif Writer");
    log("Started: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    log("Mode: " + (dryRun ? "DRY-RUN" : "WRITE"));
    log("Root: " + root);
    log("Recursive: " + recursive);
    log("Set file times: " + setFileTimes);
    log("Backup: " + backup);
    log("=".repeat(80));
    log("");

    try (Stream<Path> walk = recursive ? Files.walk(root) : Files.list(root)) {
      walk.filter(Files::isRegularFile)
          .filter(this::isMediaFile)
          .forEach(this::processMedia);
    } catch (IOException e) {
      System.err.println("Error scanning directory: " + e.getMessage());
      log("FATAL ERROR: " + e.getMessage());
      closeLogFile();
      System.exit(2);
    }

    // Print and log summary
    printSummary();
    closeLogFile();
  }

  private boolean isMediaFile(Path p) {
    String name = p.getFileName().toString().toLowerCase();
    return name.endsWith(".jpg") || name.endsWith(".jpeg");
  }

  private void processMedia(Path media) {
    totalProcessed++;
    Path sidecar = findSidecar(media);

    if (sidecar == null) {
      String msg = "SKIP\t" + media + "\t(no sidecar)";
      System.out.println(msg);
      log(msg);
      skipNoSidecar++;
      skippedFiles.add(media.toString() + " (no sidecar)");
      return;
    }

    try {
      GoogleSidecar sc = parser.parse(sidecar);
      if (sc.photoTakenTimeTimestamp() == null && sc.latitude() == null) {
        String msg = "SKIP\t" + media + "\t(sidecar has no relevant data)";
        System.out.println(msg);
        log(msg);
        skipNoData++;
        skippedFiles.add(media.toString() + " (no relevant data)");
        return;
      }

      String info = formatSidecarInfo(sc);
      if (dryRun) {
        String msg = "DRY-RUN\t" + media + "\t" + info;
        System.out.println(msg);
        log(msg);
        successCount++;
      } else {
        jpegWriter.apply(media, sc, backup);
        if (setFileTimes && sc.photoTakenTimeTimestamp() != null) {
          Files.setLastModifiedTime(media, FileTime.from(Instant.ofEpochSecond(sc.photoTakenTimeTimestamp())));
        }
        String msg = "OK\t" + media + "\t" + info;
        System.out.println(msg);
        log(msg);
        successCount++;
      }
    } catch (Exception e) {
      String msg = "ERROR\t" + media + "\t" + e.getMessage();
      System.err.println(msg);
      log(msg);
      errorCount++;
      errorFiles.add(media.toString() + " (" + e.getMessage() + ")");
    }
  }

  private Path findSidecar(Path media) {
    String mediaName = media.getFileName().toString();

    // Try exact match first: "image.jpg" -> "image.jpg.json"
    Path candidate = media.resolveSibling(mediaName + ".json");
    if (Files.exists(candidate)) return candidate;

    // Try truncated extension matches for Google Takeout edge cases
    // e.g., "image.jpg" -> "image.jp.json" or "image.j.json"
    String baseName = mediaName;
    int lastDot = mediaName.lastIndexOf('.');
    if (lastDot > 0) {
      String ext = mediaName.substring(lastDot + 1); // e.g., "jpg"
      baseName = mediaName.substring(0, lastDot);    // e.g., "image"

      // Try progressively shorter extensions: .jpg -> .jp -> .j
      for (int len = ext.length() - 1; len >= 1; len--) {
        String truncated = ext.substring(0, len);
        candidate = media.resolveSibling(baseName + "." + truncated + ".json");
        if (Files.exists(candidate)) return candidate;
      }
    }

    return null;
  }

  private String formatSidecarInfo(GoogleSidecar sc) {
    StringBuilder sb = new StringBuilder();
    if (sc.photoTakenTimeTimestamp() != null) {
      sb.append("time=").append(Instant.ofEpochSecond(sc.photoTakenTimeTimestamp()));
    }
    if (sc.latitude() != null && sc.longitude() != null) {
      if (!sb.isEmpty()) sb.append(" ");
      sb.append("gps=(").append(sc.latitude()).append(",").append(sc.longitude()).append(")");
    }
    return sb.toString();
  }

  private void initLogFile(Path root) {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    String logFileName = "gptew-" + timestamp + ".log";

    // Try to create log file in the root directory, fall back to current dir if that fails
    try {
      logFile = root.resolve(logFileName);
      logWriter = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      System.out.println("Log file: " + logFile);
    } catch (IOException e1) {
      try {
        logFile = Path.of(logFileName);
        logWriter = Files.newBufferedWriter(logFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("Log file: " + logFile);
      } catch (IOException e2) {
        System.err.println("Warning: Could not create log file: " + e2.getMessage());
        logWriter = null;
      }
    }
  }

  private void log(String message) {
    if (logWriter != null) {
      try {
        logWriter.write(message);
        logWriter.newLine();
      } catch (IOException e) {
        // Silently ignore log write errors
      }
    }
  }

  private void closeLogFile() {
    if (logWriter != null) {
      try {
        logWriter.close();
      } catch (IOException e) {
        // Ignore
      }
    }
  }

  private void printSummary() {
    String separator = "=".repeat(80);

    log("");
    log(separator);
    log("SUMMARY");
    log(separator);
    log("Total files processed: " + totalProcessed);
    log("Successfully updated:  " + successCount + (dryRun ? " (dry-run)" : ""));
    log("Skipped (no sidecar): " + skipNoSidecar);
    log("Skipped (no data):    " + skipNoData);
    log("Errors:               " + errorCount);
    log(separator);

    System.out.println();
    System.out.println(separator);
    System.out.println("SUMMARY");
    System.out.println(separator);
    System.out.println("Total files processed: " + totalProcessed);
    System.out.println("Successfully updated:  " + successCount + (dryRun ? " (dry-run)" : ""));
    System.out.println("Skipped (no sidecar): " + skipNoSidecar);
    System.out.println("Skipped (no data):    " + skipNoData);
    System.out.println("Errors:               " + errorCount);
    System.out.println(separator);

    // List skipped files if any
    if (!skippedFiles.isEmpty()) {
      log("");
      log("SKIPPED FILES (" + skippedFiles.size() + "):");
      System.out.println();
      System.out.println("SKIPPED FILES (" + skippedFiles.size() + "):");

      int displayLimit = 50;
      for (int i = 0; i < Math.min(skippedFiles.size(), displayLimit); i++) {
        String file = skippedFiles.get(i);
        log("  " + file);
        System.out.println("  " + file);
      }

      if (skippedFiles.size() > displayLimit) {
        String more = "  ... and " + (skippedFiles.size() - displayLimit) + " more (see log file for full list)";
        System.out.println(more);
        for (int i = displayLimit; i < skippedFiles.size(); i++) {
          log("  " + skippedFiles.get(i));
        }
      }
    }

    // List error files if any
    if (!errorFiles.isEmpty()) {
      log("");
      log("ERROR FILES (" + errorFiles.size() + "):");
      System.out.println();
      System.out.println("ERROR FILES (" + errorFiles.size() + "):");

      for (String file : errorFiles) {
        log("  " + file);
        System.out.println("  " + file);
      }
    }

    if (logFile != null) {
      System.out.println();
      System.out.println("Full log written to: " + logFile);
    }

    log("");
    log("Finished: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    log(separator);
  }
}
