import java.nio.file.*;
import java.io.*;
public class CanonicalPaths {
  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory("revcoursier2-paths-");
    try {
      Path real = Files.createDirectory(root.resolve("real"));
      Path link = Files.createSymbolicLink(root.resolve("alias"), real);
      File direct = real.resolve("missing/parent/artifact").toFile();
      File alias = link.resolve("missing/parent/artifact").toFile();
      System.out.println("missing parents: canonical equal=" + direct.getCanonicalPath().equals(alias.getCanonicalPath()));
      Path relative = Path.of("").toAbsolutePath().relativize(direct.toPath());
      System.out.println("relative absolute: canonical equal=" + relative.toFile().getCanonicalPath().equals(direct.getCanonicalPath()));
      String before = direct.getCanonicalPath();
      Files.createDirectories(direct.toPath().getParent());
      Files.write(direct.toPath(), new byte[]{1});
      System.out.println("ordinary creation: canonical stable=" + before.equals(direct.getCanonicalPath()));
      System.out.println("case-sensitive test filesystem=" + !real.resolve("MISSING").toFile().exists());
      Path loop = root.resolve("loop");
      Files.createSymbolicLink(loop, loop);
      try {
        loop.resolve("artifact").toFile().getCanonicalPath();
        System.out.println("loop: canonical returned a path");
      } catch (IOException e) {
        System.out.println("loop: canonical rejected path with " + e.getClass().getSimpleName());
      }
      try {
        Files.write(loop.resolve("artifact"), new byte[]{1});
        System.out.println("loop: write accepted");
      } catch (IOException e) {
        System.out.println("loop: write rejected path with " + e.getClass().getSimpleName());
      }
    } finally {
      try (var paths = Files.walk(root)) {
        for (Path p: paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
      }
    }
  }
}
