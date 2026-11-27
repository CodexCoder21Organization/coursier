package coursier.cli

import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets.UTF_8

import cats.data.Validated
import coursier.cli.fetch.{Fetch, FetchOptions, FetchParams}
import coursier.cli.options.{CacheOptions, RepositoryOptions}
import coursier.cli.resolve.SharedResolveOptions
import coursier.util.Sync
import utest._

import scala.concurrent.ExecutionContext

object FetchCustomHandlerTests extends TestSuite {
  val tests = Tests {
    test("fetch artifact through caller supplied URL handler") {
      val root = os.temp.dir(prefix = "coursier-fetch-handler-")
      val pool = Sync.fixedThreadPool(1)
      val ec   = ExecutionContext.fromExecutorService(pool)
      try {
        val content  = "artifact served through a custom URL handler"
        val artifact = root / "artifact.jar"
        os.write(artifact, content)
        val artifactUrl = "fetchtest://repository/artifact.jar"
        val factory = new URLStreamHandlerFactory {
          def createURLStreamHandler(protocol: String): URLStreamHandler =
            if (protocol == "fetchtest")
              new URLStreamHandler {
                protected def openConnection(url: URL): URLConnection = {
                  assert(url.toString == artifactUrl)
                  artifact.toIO.toURI.toURL.openConnection()
                }
              }
            else null
        }
        val options = FetchOptions(
          resolveOptions = SharedResolveOptions(
            cacheOptions = CacheOptions(
              cache = Some((root / "cache").toString),
              checksum = List("none"),
              useEnvCredentials = false
            ),
            repositoryOptions = RepositoryOptions(noDefault = true)
          )
        )
        val params = FetchParams(options) match {
          case Validated.Valid(value) => value
          case Validated.Invalid(errors) =>
            sys.error(errors.toList.mkString(System.lineSeparator()))
        }
        val (_, _, _, files) = Fetch.task(
          params,
          pool,
          Seq("test:artifact:1.0,url=" + encode(artifactUrl, UTF_8.name())),
          customHandlerFactory = Some(factory)
        ).unsafeRun(wrapExceptions = true)(ec)

        assert(files.map(_._1.url) == Seq(artifactUrl))
        assert(files.map { case (_, file) => os.read(os.Path(file)) } == Seq(content))
      }
      finally {
        pool.shutdown()
        os.remove.all(root)
      }
    }
  }
}
