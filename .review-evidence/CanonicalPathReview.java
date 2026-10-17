import java.nio.file.*;
import java.io.File;
public class CanonicalPathReview {
  public static void main(String[] args) throws Exception {
    Path root = Files.createTempDirectory(Path.of(".review-evidence"), "path-check-");
    Path real = Files.createDirectory(root.resolve("real"));
    Path alias = Files.createSymbolicLink(root.resolve("alias"), Path.of("real"));
    File first = real.resolve("not-yet-created/artifact").toFile();
    File symbolicAlias = alias.resolve("not-yet-created/artifact").toFile();
    File absoluteAlias = first.getAbsoluteFile();
    System.out.println("symbolic parent: " + first.getCanonicalPath().equals(symbolicAlias.getCanonicalPath()));
    System.out.println("relative/absolute: " + first.getCanonicalPath().equals(absoluteAlias.getCanonicalPath()));
    System.out.println("filesystem: " + Files.getFileStore(real).type());
    Files.delete(alias);
    Files.delete(real);
    Files.delete(root);
  }
}
