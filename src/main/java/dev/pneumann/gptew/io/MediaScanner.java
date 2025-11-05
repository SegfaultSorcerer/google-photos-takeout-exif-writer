package dev.pneumann.gptew.io;

import dev.pneumann.gptew.exif.JpegExifWriter;
import dev.pneumann.gptew.json.GoogleSidecar;
import dev.pneumann.gptew.json.SidecarParser;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The MediaScanner class provides functionality to process media files within a directory,
 * analyze associated metadata from sidecar files, and optionally update the media files
 * with metadata. It supports parallel processing, progress reporting, and comprehensive logging.
 *
 * This class supports "dry-run" mode, in which no actual changes are made, and can process files
 * recursively or non-recursively based on the provided input. The processing results are summarized
 * in the console and logged using SLF4J.
 *
 * @author Patrik Neumann
 */
public final class MediaScanner {
  private static final Logger log = LoggerFactory.getLogger(MediaScanner.class);
  private static final int MAX_DISPLAYED_SKIPPED_FILES = 50;

  private final boolean dryRun;
  private final boolean setFileTimes;
  private final boolean backup;
  private final boolean parallel;
  private final boolean showProgress;
  private final SidecarParser parser = new SidecarParser();
  private final JpegExifWriter jpegWriter = new JpegExifWriter();

  // Statistics (thread-safe for parallel processing)
  private final AtomicInteger totalProcessed = new AtomicInteger(0);
  private final AtomicInteger successCount = new AtomicInteger(0);
  private final AtomicInteger skipNoSidecar = new AtomicInteger(0);
  private final AtomicInteger skipNoData = new AtomicInteger(0);
  private final AtomicInteger errorCount = new AtomicInteger(0);
  private final List<String> skippedFiles = new ArrayList<>();
  private final List<String> errorFiles = new ArrayList<>();

  public MediaScanner(boolean dryRun, boolean setFileTimes, boolean backup, boolean parallel, boolean showProgress) {
    this.dryRun = dryRun;
    this.setFileTimes = setFileTimes;
    this.backup = backup;
    this.parallel = parallel;
    this.showProgress = showProgress;
  }

  /**
   * Processes media files in the specified directory.
   * Files are filtered to include only media files (e.g., JPEG).
   * Depending on the input parameters, the method scans the directory either recursively
   * or non-recursively, and can process files in parallel for better performance.
   *
   * @param root the root directory to scan for media files
   * @param recursive whether the scan should include subdirectories recursively
   */
  public void process(Path root, boolean recursive) {
    log.info("=".repeat(80));
    log.info("GPTEW - Google Photos Takeout Exif Writer");
    log.info("Mode: {}", dryRun ? "DRY-RUN" : "WRITE");
    log.info("Root: {}", root);
    log.info("Recursive: {}", recursive);
    log.info("Set file times: {}", setFileTimes);
    log.info("Backup: {}", backup);
    log.info("Parallel: {}", parallel);
    log.info("=".repeat(80));

    System.out.println("Scanning for media files...");

    try {
      // First, collect all media files
      List<Path> mediaFiles;
      try (Stream<Path> walk = recursive ? Files.walk(root) : Files.list(root)) {
        mediaFiles = walk
            .filter(Files::isRegularFile)
            .filter(this::isMediaFile)
            .toList();
      }

      if (mediaFiles.isEmpty()) {
        System.out.println("No media files found.");
        log.warn("No media files found in directory: {}", root);
        return;
      }

      System.out.println("Found " + mediaFiles.size() + " media files to process.");
      log.info("Found {} media files to process", mediaFiles.size());

      // Process files
      if (parallel) {
        processParallel(mediaFiles);
      } else {
        processSequential(mediaFiles);
      }

      // Print and log summary
      printSummary();

    } catch (IOException e) {
      String errorMsg = "Error scanning directory: " + e.getMessage();
      System.err.println(errorMsg);
      log.error("FATAL ERROR: {}", errorMsg, e);
      throw new RuntimeException("Failed to scan directory: " + root, e);
    }
  }

  /**
   * Processes media files sequentially with optional progress reporting.
   */
  private void processSequential(List<Path> mediaFiles) {
    if (showProgress) {
      try (ProgressBar pb = new ProgressBarBuilder()
          .setTaskName("Processing")
          .setInitialMax(mediaFiles.size())
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(100)
          .build()) {

        for (Path media : mediaFiles) {
          processMedia(media);
          pb.step();
        }
      }
    } else {
      mediaFiles.forEach(this::processMedia);
    }
  }

  /**
   * Processes media files in parallel using an ExecutorService with progress reporting.
   */
  private void processParallel(List<Path> mediaFiles) {
    int processors = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(processors);

    log.info("Using {} threads for parallel processing", processors);
    System.out.println("Using " + processors + " threads for parallel processing");

    if (showProgress) {
      try (ProgressBar pb = new ProgressBarBuilder()
          .setTaskName("Processing")
          .setInitialMax(mediaFiles.size())
          .setStyle(ProgressBarStyle.ASCII)
          .setUpdateIntervalMillis(100)
          .build()) {

        for (Path media : mediaFiles) {
          executor.submit(() -> {
            processMedia(media);
            pb.step();
          });
        }
      }
    } else {
      for (Path media : mediaFiles) {
        executor.submit(() -> processMedia(media));
      }
    }

    executor.shutdown();
    try {
      if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
        log.warn("Executor did not terminate in the specified time");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      log.error("Processing was interrupted", e);
      executor.shutdownNow();
      Thread.currentThread().interrupt();
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
   * are made to the media file.
   *
   * @param media the {@link Path} to the media file to be processed
   */
  private void processMedia(Path media) {
    totalProcessed.incrementAndGet();
    Path sidecar = findSidecar(media);

    if (sidecar == null) {
      log.debug("SKIP: {} (no sidecar)", media);
      skipNoSidecar.incrementAndGet();
      synchronized (skippedFiles) {
        skippedFiles.add(media.toString() + " (no sidecar)");
      }
      return;
    }

    try {
      GoogleSidecar sc = parser.parse(sidecar);
      if (sc.photoTakenTimeTimestamp() == null && sc.latitude() == null) {
        log.debug("SKIP: {} (sidecar has no relevant data)", media);
        skipNoData.incrementAndGet();
        synchronized (skippedFiles) {
          skippedFiles.add(media.toString() + " (no relevant data)");
        }
        return;
      }

      String info = formatSidecarInfo(sc);
      if (dryRun) {
        log.info("DRY-RUN: {} - {}", media.getFileName(), info);
        successCount.incrementAndGet();
      } else {
        // Validate file is writable before attempting modifications
        if (!Files.isWritable(media)) {
          log.error("ERROR: {} - File is not writable", media);
          errorCount.incrementAndGet();
          synchronized (errorFiles) {
            errorFiles.add(media.toString() + " (not writable)");
          }
          return;
        }

        jpegWriter.apply(media, sc, backup);
        if (setFileTimes && sc.photoTakenTimeTimestamp() != null) {
          Files.setLastModifiedTime(media, FileTime.from(Instant.ofEpochSecond(sc.photoTakenTimeTimestamp())));
        }
        log.info("OK: {} - {}", media.getFileName(), info);
        successCount.incrementAndGet();
      }
    } catch (Exception e) {
      log.error("ERROR: {} - {}", media, e.getMessage(), e);
      errorCount.incrementAndGet();
      synchronized (errorFiles) {
        errorFiles.add(media.toString() + " (" + e.getMessage() + ")");
      }
    }
  }

  /**
   * Attempts to find a sidecar file associated with the given media file.
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
    String baseName = mediaName;
    int lastDot = mediaName.lastIndexOf('.');
    if (lastDot > 0) {
      String ext = mediaName.substring(lastDot + 1);
      baseName = mediaName.substring(0, lastDot);

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
   *
   * @param sc the {@link GoogleSidecar} object containing metadata
   * @return a formatted string with the timestamp and/or GPS coordinates
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
   * Prints a summary of the media processing results to the console and logs it.
   */
  private void printSummary() {
    String separator = "=".repeat(80);

    System.out.println();
    System.out.println(separator);
    System.out.println("SUMMARY");
    System.out.println(separator);
    System.out.println("Total files processed: " + totalProcessed.get());
    System.out.println("Successfully updated:  " + successCount.get() + (dryRun ? " (dry-run)" : ""));
    System.out.println("Skipped (no sidecar): " + skipNoSidecar.get());
    System.out.println("Skipped (no data):    " + skipNoData.get());
    System.out.println("Errors:               " + errorCount.get());
    System.out.println(separator);

    log.info("=".repeat(80));
    log.info("SUMMARY");
    log.info("Total files processed: {}", totalProcessed.get());
    log.info("Successfully updated:  {} {}", successCount.get(), dryRun ? "(dry-run)" : "");
    log.info("Skipped (no sidecar): {}", skipNoSidecar.get());
    log.info("Skipped (no data):    {}", skipNoData.get());
    log.info("Errors:               {}", errorCount.get());

    // List skipped files if any
    if (!skippedFiles.isEmpty()) {
      System.out.println();
      System.out.println("SKIPPED FILES (" + skippedFiles.size() + "):");

      for (int i = 0; i < Math.min(skippedFiles.size(), MAX_DISPLAYED_SKIPPED_FILES); i++) {
        System.out.println("  " + skippedFiles.get(i));
      }

      if (skippedFiles.size() > MAX_DISPLAYED_SKIPPED_FILES) {
        System.out.println("  ... and " + (skippedFiles.size() - MAX_DISPLAYED_SKIPPED_FILES)
            + " more (see log file for full list)");
      }

      // Log all skipped files
      log.info("SKIPPED FILES ({}):", skippedFiles.size());
      for (String file : skippedFiles) {
        log.info("  {}", file);
      }
    }

    // List error files if any
    if (!errorFiles.isEmpty()) {
      System.out.println();
      System.out.println("ERROR FILES (" + errorFiles.size() + "):");

      for (String file : errorFiles) {
        System.out.println("  " + file);
        log.error("  {}", file);
      }
    }

    log.info("=".repeat(80));
  }
}
