package dev.pneumann.gptew;

import dev.pneumann.gptew.io.MediaScanner;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class App {
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

  private static boolean parseBool(String s) {
    return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
  }

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
