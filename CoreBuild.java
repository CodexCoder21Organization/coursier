import java.io.File;
import java.util.List;
import kompile.LocalBuildWorkspace;
import community.kotlin.clocks.simple.SystemClock;
public final class CoreBuild {
    public static void main(String[] args) throws Exception {
        try (LocalBuildWorkspace workspace = new LocalBuildWorkspace(List.of(new File(args[0])), new File(args[1]))) {
            workspace.execute("tools.kompile.buildFatJar", new File(args[2]), List.of(), null, new SystemClock());
        }
    }
}
