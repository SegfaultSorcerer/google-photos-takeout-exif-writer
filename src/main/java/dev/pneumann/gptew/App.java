package dev.pneumann.gptew;

import dev.pneumann.gptew.io.MediaScanner;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The App class serves as the entry point of the application. It processes command-line
 * arguments, parses them to determine operational behavior, and invokes the
 * MediaScanner to perform the desired actions.
 *
 * The class provides helper methods for parsing command-line arguments and interpreting
 * boolean configurations from strings. It supports various command-line options that control
 * the behavior of the media scanning process, such as operating in a dry-run mode, setting
 * file times, creating backups, and working on directories recursively.
 *
 * The methods in this class are all static, as the class itself is not intended to be
 * instantiated.
 *
 * @author Patrik Neumann
 */
public final class App {
  /**
   * The entry point of the application. This method processes command-line arguments
   * and uses them to initialize a `MediaScanner` for processing input directories
   * according to the specified options.
   *
   * @param args an array of command-line arguments. Expected arguments are:
   *             --input: the directory to process (required).
   *             --recursive: whether to process directories recursively (default: true).
   *             --dry-run: whether to perform a dry-run without making changes (default: true).
   *             --set-filetimes: whether to set file modification times (default: true).
   *             --backup: whether to create backups of processed files (default: false).
   */
  public static void main(String[] args) {
    Map<String,String> opts = parseArgs(args);
    if (!opts.containsKey("--input")) {
      System.err.println("Usage: java -jar gpmf.jar --input <dir> [--recursive=true|false] [--dry-run=true|false] [--set-filetimes=true|false] [--backup=true|false]");
      System.exit(2);
    }
    Path input = Path.of(opts.get("--input"));
    boolean recursive = parseBool(opts.getOrDefault("--recursive", "true"));
    boolean dryRun = parseBool(opts.getOrDefault("--dry-run", "true"));
    boolean setFileTimes = parseBool(opts.getOrDefault("--set-filetimes", "true"));
    boolean backup = parseBool(opts.getOrDefault("--backup", "false"));

    new MediaScanner(dryRun, setFileTimes, backup).process(input, recursive);
  }

  /**
   * Parses a string input and determines its boolean value. The input is interpreted as
   * true if it matches "true", "1", or "yes" (case-insensitive). All other input values
   * are considered false.
   *
   * @param s the string to parse as a boolean
   * @return true if the input string matches "true", "1", or "yes" (case-insensitive);
   *         false otherwise
   */
  private static boolean parseBool(String s) {
    return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
  }

  /**
   * Parses command-line arguments into a map of key-value pairs. Arguments with a
   * double dash prefix (e.g., --key) are treated as keys. If the argument has an
   * equal sign (e.g., --key=value), the part after the equal sign is treated as
   * the value. If no equal sign is present, the value will be the next argument
   * unless the next argument is also a key. Keys without values default to "true".
   *
   * @param args an array of command-line arguments to parse
   * @return a map containing parsed key-value pairs where keys are the argument names
   *         prefixed with "--", and values are their associated values or "true" if no
   *         explicit value is provided
   */
  private static Map<String,String> parseArgs(String[] args) {
    Map<String,String> m = new HashMap<>();
    for (int i=0; i<args.length; i++) {
      String a = args[i];
      if (a.startsWith("--")) {
        String val = "true";
        int eq = a.indexOf('=');
        if (eq > 0) {
          val = a.substring(eq+1);
          a = a.substring(0, eq);
        } else if (i+1 < args.length && !args[i+1].startsWith("--")) {
          val = args[++i];
        }
        m.put(a, val);
      }
    }
    return m;
  }
}
