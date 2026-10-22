@file:WithArtifact("build.kotlin.annotations:build-kotlin-annotations:0.0.10")
@file:WithArtifact("tools.kompile.buildMaven()")
@file:WithArtifact("kompile.buildscript:kompile-buildscript:0.0.45")
@file:WithArtifact("kompile.executionenvironment:kompile-executionenvironment:0.0.59")
@file:WithArtifact("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.15")
@file:WithArtifact("util.cache.hashtofile:hash-to-file-cache:0.0.11")
@file:WithArtifact("cache.key.builder:cache-key-builder:0.0.6")
@file:WithArtifact("community.kotlin.logging:community-kotlin-logging:0.0.1")
@file:WithArtifact("kompile:build-kotlin-jvm:0.0.33")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-test:1.8.22")
@file:WithArtifact("commons-io:commons-io:2.13.0")
@file:WithArtifact("org.jetbrains.kotlin:kotlin-reflect:1.8.22")
@file:WithArtifact("commons-codec:commons-codec:1.15")
@file:WithArtifact("org.json:json:20230618")
@file:WithArtifact("io.get-coursier:interface:2.1.31-freshcache2")
@file:WithArtifact("community.kotlin.psi.leakproof:community-kotlin-psi-leakproof:0.0.2")
@file:WithArtifact("org.apache.commons:commons-compress:1.26.1")
@file:WithArtifact("com.squareup.okio:okio-fakefilesystem-jvm:3.4.0")

package kompile

import build.kotlin.withartifact.WithArtifact
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.nio.file.Files
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@build.kotlin.annotations.BillingQuotaExecutionLimit(quantity = 512, resource_name = "memory_megabytes")
fun testSmallResolverResponsesBoundTransferBuffers() {
    val expectedDownloadClasses = mapOf(
        "coursier/cache/internal/Downloader\$\$anonfun\$coursier\$cache\$internal\$Downloader\$\$downloading\$1.class" to "c69488677dd1cd15f347146f29ba9e6ccd0749f8cb0c2f8c0f8972e4c1baa338",
        "coursier/cache/internal/Downloader\$.class" to "0f390cd93553f81c2edeb9230dc004c10f3a1ab99cbdd342f62343254e214b1d",
        "coursier/cache/internal/Downloader\$Blocking\$.class" to "cc689cfd988119a2cc3c5af6550f32cb58c1479ca6962af428000ce43026ec0d",
        "coursier/cache/internal/Downloader\$UnknownProtocol\$.class" to "c63110823187f2e7985657146ec1f5b5aa5cda48861e3ac0a5cd0810f5442a4b",
        "coursier/cache/internal/Downloader.class" to "bd281b342b6733f39f18d15989109c3ed691f7ccd7fceb71672b377e46d44121",
    )
    for ((name, expected) in expectedDownloadClasses) {
        val bytes = coursier.cache.FileCache::class.java.classLoader.getResourceAsStream(name)?.use { it.readBytes() }
            ?: error("Missing download class resource: $name")
        val actual = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        check(actual == expected) { "The consumer loaded different download class bytes for $name: $actual" }
        println("SNAPSHOT_CLASS $name sha256=$actual")
    }
    val directories = mutableListOf<File>()
    val openStreams = ConcurrentHashMap.newKeySet<InputStream>()
    val reads = Collections.synchronizedList(mutableListOf<Pair<String, Triple<Long, Int, Int>>>())
    val servedKinds = ConcurrentHashMap.newKeySet<String>()
    val serverFailure = AtomicReference<Throwable?>()
    val stopping = AtomicBoolean(false)
    var server: HttpServer? = null
    var serverStarted = false
    var workerExecutor: ExecutorService? = null
    var workspace: LocalBuildWorkspace? = null
    val previousCoursierCache = System.getProperty("coursier.cache")

    fun closeStreams(streams: List<InputStream>, index: Int = 0) {
        if (index >= streams.size) return
        try {
            streams[index].close()
        } finally {
            closeStreams(streams, index + 1)
        }
    }

    fun deleteDirectories(index: Int = directories.lastIndex) {
        if (index < 0) return
        try {
            val directory = directories[index]
            assertTrue(directory.deleteRecursively(), "Could not delete fixture directory $directory.")
        } finally {
            deleteDirectories(index - 1)
        }
    }

    fun recordServerFailure(failure: Throwable) {
        if (!stopping.get()) serverFailure.compareAndSet(null, failure)
    }

    try {
        val projectRoot = Files.createTempDirectory("small-body-project").toFile().also { directories.add(it) }
        val buildCache = Files.createTempDirectory("small-body-build-cache").toFile().also { directories.add(it) }
        val coursierCache = Files.createTempDirectory("small-body-coursier-cache").toFile().also { directories.add(it) }
        System.setProperty("coursier.cache", coursierCache.absolutePath)

        val groupId = "fixture.smallbody." + UUID.randomUUID().toString().replace("-", "")
        val artifactId = "tiny"
        val version = "1.0"
        val groupPath = groupId.replace('.', '/')
        val artifactPath = "/repo/$groupPath/$artifactId/$version/$artifactId-$version"
        val pomPath = "$artifactPath.pom"
        val jarPath = "$artifactPath.jar"
        val pomBytes = (
            "<project><modelVersion>4.0.0</modelVersion><groupId>$groupId</groupId>" +
                "<artifactId>$artifactId</artifactId><version>$version</version><packaging>jar</packaging></project>"
            ).toByteArray()
        val jarBytes = ByteArrayOutputStream().use { buffer ->
            JarOutputStream(buffer).use { jar ->
                jar.putNextEntry(JarEntry("fixture.txt"))
                jar.write("small-body-resolver-artifact".toByteArray())
                jar.closeEntry()
            }
            buffer.toByteArray()
        }
        assertTrue(pomBytes.size < 1024 && jarBytes.size < 1024, "The fixture bodies must stay tiny.")

        val localServer = HttpServer.create(
            InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
            0,
        )
        server = localServer
        val localWorkerExecutor = Executors.newCachedThreadPool()
        workerExecutor = localWorkerExecutor
        localServer.executor = localWorkerExecutor
        localServer.createContext("/") { exchange: HttpExchange ->
            try {
                val response = when (exchange.requestURI.path) {
                    pomPath -> "pom" to pomBytes
                    jarPath -> "jar" to jarBytes
                    else -> null
                }
                if (response == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    val (kind, body) = response
                    servedKinds.add(kind)
                    exchange.responseHeaders.set("Content-Type", if (kind == "pom") "application/xml" else "application/java-archive")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { output -> output.write(body) }
                }
            } catch (failure: Throwable) {
                recordServerFailure(failure)
            } finally {
                try {
                    exchange.close()
                } catch (failure: Throwable) {
                    recordServerFailure(failure)
                }
            }
        }
        localServer.start()
        serverStarted = true

        URL.setURLStreamHandlerFactory(URLStreamHandlerFactory { protocol ->
            if (protocol != "smallbodyhttp") null else object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection {
                    val delegate = URL("http", url.host, url.port, url.file).openConnection()
                    return object : URLConnection(url) {
                        private var exposedStream: InputStream? = null

                        override fun connect() {
                            if (!connected) {
                                delegate.connect()
                                connected = true
                            }
                        }

                        override fun getHeaderField(name: String?): String? = delegate.getHeaderField(name)
                        override fun getContentLengthLong(): Long = delegate.contentLengthLong
                        override fun getLastModified(): Long = delegate.lastModified

                        override fun getInputStream(): InputStream {
                            exposedStream?.let { return it }
                            connect()
                            val kind = when {
                                url.path.endsWith(".pom") -> "pom"
                                url.path.endsWith(".jar") -> "jar"
                                else -> null
                            }
                            val advertisedLength = delegate.contentLengthLong
                            val input = delegate.getInputStream()
                            val observed = object : FilterInputStream(input) {
                                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                                    if (kind != null) {
                                        reads.add(kind to Triple(advertisedLength, length, bytes.size))
                                    }
                                    return super.read(bytes, offset, length)
                                }

                                override fun close() {
                                    try {
                                        super.close()
                                    } finally {
                                        openStreams.remove(this)
                                    }
                                }
                            }
                            openStreams.add(observed)
                            exposedStream = observed
                            return observed
                        }
                    }
                }
            }
        })

        val repository = "smallbodyhttp://127.0.0.1:${localServer.address.port}/repo/"
        val localWorkspace = LocalBuildWorkspace(listOf(projectRoot), buildCache)
        workspace = localWorkspace
        val resolvedFiles = WorkspaceCoordinateResolver { localWorkspace }
            .resolve(listOf("$groupId:$artifactId:$version"), listOf(repository))

        assertEquals(1, resolvedFiles.size, "Expected exactly one resolved artifact.")
        assertTrue(
            resolvedFiles.single().readBytes().contentEquals(jarBytes),
            "The resolved artifact bytes did not match the tiny JAR served by the local repository.",
        )
        serverFailure.get()?.let { throw AssertionError("The local Maven repository failed.", it) }
        assertTrue(servedKinds.containsAll(setOf("pom", "jar")), "Expected the repository to serve both the POM and JAR.")

        val observedReads = synchronized(reads) { reads.toList() }
        val pomReads = observedReads.filter { it.first == "pom" }
        val jarReads = observedReads.filter { it.first == "jar" }
        assertTrue(pomReads.isNotEmpty(), "Expected to observe at least one POM body read.")
        assertTrue(jarReads.isNotEmpty(), "Expected to observe at least one JAR body read.")
        assertTrue(pomReads.all { it.second.first == pomBytes.size.toLong() }, "The POM response did not advertise its known Content-Length.")
        assertTrue(jarReads.all { it.second.first == jarBytes.size.toLong() }, "The JAR response did not advertise its known Content-Length.")
        observedReads.forEach { (kind, sizes) ->
            val (advertisedLength, requestedLength, arrayLength) = sizes
            assertTrue(
                requestedLength.toLong() <= advertisedLength,
                "$kind requested $requestedLength bytes with an array of $arrayLength, " +
                    "but its advertised body length was $advertisedLength.",
            )
            assertTrue(
                arrayLength.toLong() <= advertisedLength,
                "$kind used a ${arrayLength}-byte read array for an advertised body length of $advertisedLength.",
            )
        }
    } finally {
        stopping.set(true)
        try {
            workspace?.close()
        } finally {
            try {
                closeStreams(openStreams.toList())
            } finally {
                try {
                    if (serverStarted) server?.stop(0)
                } finally {
                    try {
                        val executor = workerExecutor
                        if (executor != null) {
                            executor.shutdownNow()
                            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "The local HTTP server executor did not terminate.")
                        }
                    } finally {
                        try {
                            if (previousCoursierCache == null) System.clearProperty("coursier.cache")
                            else System.setProperty("coursier.cache", previousCoursierCache)
                        } finally {
                            deleteDirectories()
                        }
                    }
                }
            }
        }
    }
}
