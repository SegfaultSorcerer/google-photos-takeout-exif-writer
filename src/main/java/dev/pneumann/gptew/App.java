package dev.pneumann.gptew;

import dev.pneumann.gptew.io.MediaScanner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * The App class serves as the entry point of the application. It uses the picocli
 * framework for command-line argument parsing and invokes the MediaScanner to perform
 * the desired actions.
 *
 * This class implements Callable to integrate with picocli's command execution model.
 *
 * @author Patrik Neumann
 */
@Command(
    name = "gptew",
    mixinStandardHelpOptions = true,
    version = "GPTEW 0.1.1",
    description = "Google Photos Takeout EXIF Writer - Restores metadata from Google Photos Takeout exports",
    sortOptions = false
)
public final class App implements Callable<Integer> {

  @Parameters(
      index = "0",
      description = "Root directory containing media and sidecar JSON files",
      paramLabel = "<input-directory>"
  )
  private Path input;

  @Option(
      names = {"-r", "--recursive"},
      description = "Scan subdirectories recursively (default: ${DEFAULT-VALUE})",
      defaultValue = "true"
  )
  private boolean recursive;

  @Option(
      names = {"-d", "--dry-run"},
      description = "Preview mode - no files are modified (default: ${DEFAULT-VALUE})",
      defaultValue = "true"
  )
  private boolean dryRun;

  @Option(
      names = {"-t", "--set-filetimes"},
      description = "Update file modification timestamps to match photo capture time (default: ${DEFAULT-VALUE})",
      defaultValue = "true"
  )
  private boolean setFileTimes;

  @Option(
      names = {"-b", "--backup"},
      description = "Create .bak backup files before modifying originals (default: ${DEFAULT-VALUE})",
      defaultValue = "false"
  )
  private boolean backup;

  @Option(
      names = {"-p", "--parallel"},
      description = "Process files in parallel for better performance (default: ${DEFAULT-VALUE})",
      defaultValue = "false"
  )
  private boolean parallel;

  @Option(
      names = {"--progress"},
      description = "Show progress bar during processing (default: ${DEFAULT-VALUE})",
      defaultValue = "true"
  )
  private boolean showProgress;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new App()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    try {
      // Validate input directory
      if (!Files.exists(input)) {
        System.err.println("Error: Input directory does not exist: " + input);
        return 1;
      }
      if (!Files.isDirectory(input)) {
        System.err.println("Error: Input path is not a directory: " + input);
        return 1;
      }
      if (!Files.isReadable(input)) {
        System.err.println("Error: Input directory is not readable: " + input);
        return 1;
      }

      MediaScanner scanner = new MediaScanner(dryRun, setFileTimes, backup, parallel, showProgress);
      scanner.process(input, recursive);
      return 0;

    } catch (RuntimeException e) {
      System.err.println("Fatal error during processing: " + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("Caused by: " + e.getCause().getMessage());
      }
      return 1;
    }
  }
}
