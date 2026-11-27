package coursier.cache

import java.nio.file.Files
import java.util.concurrent.Executors

import coursier.util.{Artifact, Task}

import scala.concurrent.Await
import scala.concurrent.duration._

/** A separate JVM that downloads one artifact into a cache directory shared with other processes.
  *
  * Used by [[SharedDownloadOwnershipTests]]: cross-process download ownership is coordinated with
  * file locks, which a single JVM cannot contend for against itself.
  *
  * Arguments: cache location, artifact URL. Prints `OK <length> bytes, SHA-256 <digest>` of the returned file and exits 0, or
  * prints `ERROR <description>` and exits 1.
  */
object SharedDownloadOwnershipProcess {
  def main(args: Array[String]): Unit = {
    val location = new java.io.File(args(0))
    val url      = args(1)
    val pool     = Executors.newFixedThreadPool(2)
    val outcome =
      try {
        val cache = FileCache[Task](
          location = location,
          cachePolicies = Seq(CachePolicy.FetchMissing),
          checksums = Seq(None),
          credentials = Nil,
          logger = CacheLogger.nop,
          pool = pool,
          ttl = Some(24.hours),
          localArtifactsShouldBeCached = false,
          followHttpToHttpsRedirections = true,
          followHttpsToHttpRedirections = false,
          maxRedirections = Some(20),
          sslRetry = 1,
          sslSocketFactoryOpt = None,
          hostnameVerifierOpt = None,
          retry = 1,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 10.milliseconds,
          retryBackoffMultiplier = 2.0
        )
        Await.result(cache.file(Artifact(url)).run.future()(cache.ec), 120.seconds) match {
          case Left(error) => s"ERROR ${error.describe}"
          case Right(file) =>
            val bytes = Files.readAllBytes(file.toPath)
            val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            s"OK ${bytes.length} bytes, SHA-256 ${sha256.map(b => f"${b & 0xff}%02x").mkString}"
        }
      }
      catch {
        case e: Throwable => s"ERROR ${e.toString}"
      }
      finally pool.shutdownNow()
    println(outcome)
    System.out.flush()
    sys.exit(if (outcome.startsWith("OK ")) 0 else 1)
  }
}
