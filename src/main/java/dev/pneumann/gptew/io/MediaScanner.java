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

/**
 * The MediaScanner class provides functionality to process media files within a directory,
 * analyze associated metadata from sidecar files, and optionally update the media files
 * with metadata. It also includes features for logging and error tracking during the processing workflow.
 *
 * This class supports "dry-run" mode, in which no actual changes are made, and can process files
 * recursively or non-recursively based on the provided input. The processing results are summarized
 * in the console and optionally logged to a file.
 *
 * @author Patrik Neumann
 */
public final class MediaScanner {
  private static final int MAX_DISPLAYED_SKIPPED_FILES = 50;

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

  /**
   * Processes media files in the specified directory and writes metadata to log files.
   * Files are filtered to include only media files (e.g., JPEG).
   * Depending on the input parameters, the method scans the directory either recursively
   * or non-recursively.
   * Sidecar files associated with media files are analyzed for metadata updates. If the
   * method encounters errors while processing files, it logs these errors and includes
   * them in the summary report.
   *
   * @param root the root directory to scan for media files
   * @param recursive whether the scan should include subdirectories recursively
   */
  public void process(Path root, boolean recursive) {
    // Initialize log file
    initLogFile(root);

    try {
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
        System.exit(2);
      }

      // Print and log summary
      printSummary();
    } finally {
      // Ensure log file is always closed, even if an exception occurs
      closeLogFile();
    }
  }

  /**
   * Determines whether the specified file path corresponds to a media file.
   * This method checks if the file name has a `.jpg` or `.jpeg` extension.
   *
   * @param p the {@link Path} object representing the file to be checked
   * @return {@code true} if the file is a media file with a `.jpg` or `.jpeg` extension;
   *         {@code false} otherwise
   */
  private boolean isMediaFile(Path p) {
    String name = p.getFileName().toString().toLowerCase();
    return name.endsWith(".jpg") || name.endsWith(".jpeg");
  }

  /**
   * Processes a media file and its associated sidecar file, applying metadata to the media file
   * if the sidecar contains relevant information. Supports a "dry-run" mode where no changes
   * are made to the media file, and logs information about operations performed or errors encountered.
   *
   * The method checks for the existence of a sidecar file that matches the given media file.
   * If no sidecar file is found, or if the sidecar lacks relevant metadata, the media file
   * is skipped. If a valid sidecar file is found, metadata such as timestamps and geolocation
   * can be applied to the media file, and file timestamps may be updated if configured.
   *
   * @param media the {@link Path} to the media file to be processed
   */
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

  /**
   * Attempts to find a sidecar file associated with the given media file.
   * The method searches for a sidecar file in the same directory as the media file with a `.json` extension.
   * If no exact match is found, it tries progressively truncated extensions to account for edge cases,
   * such as those generated by Google Takeout. For example, `image.jpg` might have sidecar file variants
   * like `image.jp.json` or `image.j.json`.
   *
   * If no matching sidecar file is found, the method returns {@code null}.
   *
   * @param media the {@link Path} to the media file for which to find a sidecar file
   * @return the {@link Path} to the matching sidecar file if found; {@code null} if no match is found
   */
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

  /**
   * Formats the relevant information from a given {@link GoogleSidecar} object into a string.
   * The generated string includes the photo's timestamp if available, and its GPS coordinates
   * (latitude and longitude) if both values are non-null.
   *
   * @param sc the {@link GoogleSidecar} object containing metadata such as timestamps and geolocation
   * @return a formatted string with the timestamp and/or GPS coordinates of the photo, or an empty
   *         string if none of the relevant information is available
   */
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

  /**
   * Initializes a log file to record processing details. The log file is created in the
   * specified root directory. If the creation fails, it attempts to create the file in the
   * current working directory. A timestamp is appended to the log file name to ensure uniqueness.
   *
   * @param root the root directory where the log file should be created
   */
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

  /**
   * Writes a message to the log file if a writer is available.
   * If {@code logWriter} is null or an IOException occurs, no action is performed.
   *
   * @param message the message to log
   */
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

  /**
   * Writes a message to both the log file and the console.
   *
   * @param message the message to output
   */
  private void logAndPrint(String message) {
    System.out.println(message);
    log(message);
  }

  /**
   * Closes the log file if it is open.
   *
   * This method ensures that the {@code logWriter} is properly closed to release
   * any resources associated with the log file. If {@code logWriter} is null, no
   * action is taken. If an {@code IOException} occurs while attempting to close
   * the log file, the exception is ignored.
   */
  private void closeLogFile() {
    if (logWriter != null) {
      try {
        logWriter.close();
      } catch (IOException e) {
        // Ignore
      }
    }
  }

  /**
   * Prints a summary of the media processing results to both the log file and the console.
   *
   * The summary includes the following information:
   * - Total number of files processed.
   * - Number of successfully updated files, indicating if it was a dry-run.
   * - Number of skipped files due to missing sidecar files or data.
   * - Total number of errors encountered during processing.
   *
   * If there were skipped files, their details are listed, up to a maximum of 50 files. If
   * more than 50 skipped files exist, the remaining count is shown, and their details are
   * only logged to the log file.
   *
   * If errors occurred, the list of files with errors is displayed in the summary.
   *
   * Additionally, the path to the log file is displayed in the console if a log file was created.
   * The method concludes by logging a timestamp of when the processing finished.
   */
  private void printSummary() {
    String separator = "=".repeat(80);

    logAndPrint("");
    logAndPrint(separator);
    logAndPrint("SUMMARY");
    logAndPrint(separator);
    logAndPrint("Total files processed: " + totalProcessed);
    logAndPrint("Successfully updated:  " + successCount + (dryRun ? " (dry-run)" : ""));
    logAndPrint("Skipped (no sidecar): " + skipNoSidecar);
    logAndPrint("Skipped (no data):    " + skipNoData);
    logAndPrint("Errors:               " + errorCount);
    logAndPrint(separator);

    // List skipped files if any
    if (!skippedFiles.isEmpty()) {
      logAndPrint("");
      logAndPrint("SKIPPED FILES (" + skippedFiles.size() + "):");

      for (int i = 0; i < Math.min(skippedFiles.size(), MAX_DISPLAYED_SKIPPED_FILES); i++) {
        logAndPrint("  " + skippedFiles.get(i));
      }

      if (skippedFiles.size() > MAX_DISPLAYED_SKIPPED_FILES) {
        System.out.println("  ... and " + (skippedFiles.size() - MAX_DISPLAYED_SKIPPED_FILES) + " more (see log file for full list)");
        // Log remaining files to log file only
        for (int i = MAX_DISPLAYED_SKIPPED_FILES; i < skippedFiles.size(); i++) {
          log("  " + skippedFiles.get(i));
        }
      }
    }

    // List error files if any
    if (!errorFiles.isEmpty()) {
      logAndPrint("");
      logAndPrint("ERROR FILES (" + errorFiles.size() + "):");

      for (String file : errorFiles) {
        logAndPrint("  " + file);
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
