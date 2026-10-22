@file:WithArtifact("tools.kompile.buildJoinedRecorderMavenRepository()")
@file:WithArtifact("tools.kompile.buildTestMavenRepository()")
@file:WithArtifact("tools.kompile.buildInputFixtureTestAttemptTimelineJoinedWorkloadEvents()")
@file:WithArtifact("resource.arena:resource-arena:0.0.1")
@file:WithArtifact("build.kotlin.annotations:build-kotlin-annotations:0.0.10")
@file:WithArtifact("tools.kompile.buildMaven136DefinitionSiteScopeTests()")
@file:WithArtifact("kompile.buildscript:kompile-buildscript:0.0.48")
@file:WithArtifact("kompile.executionenvironment:kompile-executionenvironment:0.0.59")
@file:WithArtifact("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.3")
@file:WithArtifact("util.cache.hashtofile:hash-to-file-cache:0.0.13")
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
@file:WithArtifact("buildtest.server:buildtest-server-service:0.0.124")
@file:WithArtifact("foundation.url:resolver:0.0.556")

package kompile

import build.kotlin.withartifact.WithArtifact

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlinx.algebraiceffects.Effective
import kotlinx.algebraiceffects.NotificationEffect
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertIs

import community.kotlin.clocks.simple.ManualClock
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.json.JSONObject
import kotlin.test.*
fun testI419iJoinedWorkloadDiagnostic() {
    println("I419i diagnostic outer-body-start ns=" + System.nanoTime())
    // The compiler resolves its own pinned bootstrap graph in addition to this test's
    // probe artifacts. Serve those declared build inputs locally, even with an empty
    // user cache. Each test owns its server and runs in an isolated child JVM.
    val bootstrapExecutor = java.util.concurrent.Executors.newCachedThreadPool()
    val bootstrapServer = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val bootstrapSockets = java.util.concurrent.ConcurrentHashMap.newKeySet<java.net.Socket>()
    val bootstrapFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
    var bootstrapAcceptor: java.util.concurrent.Future<*>? = null
    try {
        bootstrapAcceptor = bootstrapExecutor.submit {
            try {
                while (!bootstrapServer.isClosed) {
                    val socket = bootstrapServer.accept().also { bootstrapSockets.add(it) }
                    bootstrapExecutor.submit {
                        try {
                            socket.use { connection ->
                                val reader = connection.getInputStream().bufferedReader()
                                val request = reader.readLine()?.split(' ') ?: error("Missing bootstrap HTTP request.")
                                while (reader.readLine()?.isNotEmpty() == true) { }
                                val path = request[1].substringBefore('?').removePrefix("/")
                                val resource = LocalBuildWorkspace::class.java.getResource("/kompile-test-maven/$path")
                                val output = connection.getOutputStream()
                                if (resource == null) {
                                    output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                                } else {
                                    val artifact = resource.openConnection()
                                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: ${artifact.contentLengthLong}\r\nConnection: close\r\n\r\n").toByteArray())
                                    artifact.getInputStream().use { if (request[0] != "HEAD") it.copyTo(output) }
                                }
                            }
                        } catch (failure: Throwable) {
                            if (!bootstrapServer.isClosed) bootstrapFailure.compareAndSet(null, failure)
                        } finally { bootstrapSockets.remove(socket) }
                    }
                }
            } catch (failure: Throwable) {
                // Closing the listening socket ends the accept loop during cleanup.
                if (!bootstrapServer.isClosed) bootstrapFailure.compareAndSet(null, failure)
            }
        }
        java.net.URL.setURLStreamHandlerFactory { protocol ->
            if (protocol != "https") null else object : java.net.URLStreamHandler() {
                override fun openConnection(url: java.net.URL): java.net.URLConnection {
                    require(url.host in setOf("kotlin.directory", "repo1.maven.org")) {
                        "Unexpected external repository in an isolated test: $url"
                    }
                    val path = url.path.removePrefix("/maven2").removePrefix("/")
                    return java.net.URL("http://127.0.0.1:${bootstrapServer.localPort}/$path")
                        .openConnection(java.net.Proxy.NO_PROXY)
                }
                override fun openConnection(url: java.net.URL, proxy: java.net.Proxy) = openConnection(url)
            }
        }

    val resource = "/input-fixtures/testAttemptTimelineJoinedWorkloadEvents.zip"
    val bytes = LocalBuildWorkspace::class.java.getResourceAsStream(resource)?.use { it.readBytes() }
        ?: error("Prepared input fixture $resource is missing from the declared test dependency")
    val entries = linkedMapOf<String, ByteArray>()
    java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            check(!entry.isDirectory && !File(entry.name).isAbsolute &&
                entry.name.split('/').none { it == ".." || it == "." || it.isEmpty() } &&
                '\\' !in entry.name) {
                "Prepared input fixture $resource has an invalid entry ${entry.name}"
            }
            check(entries.put(entry.name, zip.readBytes()) == null) { "Duplicate prepared fixture entry ${entry.name}" }
            zip.closeEntry()
        }
    }
    val manifest = org.json.JSONObject(entries.remove("manifest.json")?.toString(Charsets.UTF_8)
        ?: error("Prepared input fixture $resource has no content manifest"))
    check(manifest.getString("scenario") == "testAttemptTimelineJoinedWorkloadEvents") {
        "Prepared input fixture has the wrong owning scenario"
    }
    val listed = manifest.getJSONArray("files")
    val expectedEntries = linkedMapOf<String, String>()
    for (index in 0 until listed.length()) {
        val record = listed.getJSONObject(index)
        val path = record.getString("path")
        check(expectedEntries.put(path, record.getString("sha256")) == null) {
            "Duplicate prepared fixture manifest entry $path"
        }
    }
    check(entries.keys == expectedEntries.keys) { "Prepared input fixture entry inventory does not match its manifest" }
    for ((path, content) in entries) {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        check(digest == expectedEntries.getValue(path)) { "Prepared input fixture content differs at $path" }
    }
    val originalInputs = entries.filterKeys { it.startsWith("workspace/") }
        .mapKeys { it.key.removePrefix("workspace/") }
    assertEquals(setOf("project/build.kts", "project/tests/testProbe.kts", "manager-content.sha256"), originalInputs.keys,
        "Preparation must contain the complete compiler fixture source/metadata inputs without target output")
    check(entries.keys.all { it == "root.txt" || it.startsWith("workspace/") || it.startsWith("cache/buildscriptCache/") || it.startsWith("cache/compiledTestScripts/") }) {
        "Prepared input fixture must contain only its runtime identity, source inputs and compiler state"
    }
    val root = File(entries.getValue("root.txt").toString(Charsets.UTF_8))
    check(root.isAbsolute && root.toPath().normalize() == root.toPath() && root.name == "runtime" &&
        root.parentFile.name.startsWith("prepared-input-fixture-") &&
        root.parentFile.name.length > "prepared-input-fixture-".length) {
        "Prepared input fixture has an invalid runtime identity $root"
    }
    // Both probes and the real action hold the same runtime lease through cleanup.
    val invokeOwnedRuntime: (() -> Unit, () -> Unit) -> Unit = { beforeReclaim, action ->
        synchronized(root.absolutePath.intern()) {
            java.io.RandomAccessFile(File(root.parentFile.absolutePath + ".lock"), "rw").use { leaseFile ->
                leaseFile.channel.lock().use {
                    java.io.Closeable {
                        check(root.parentFile.deleteRecursively()) {
                            "Could not remove prepared input fixture runtime ${root.parentFile}"
                        }
                    }.use {
                        beforeReclaim()
                        check(root.parentFile.deleteRecursively()) { "Could not clear prepared input fixture runtime ${root.parentFile}" }
                        check(root.mkdirs()) { "Could not create prepared input fixture runtime $root" }
                        action()
                    }
                }
            }
        }
    }
    val sentinel = IllegalStateException("Deliberate prepared runtime body failure for testAttemptTimelineJoinedWorkloadEvents")
    val observed = kotlin.test.assertFailsWith<IllegalStateException> {
        invokeOwnedRuntime({}, {
            val marker = File(root, "failed-invocation.txt").apply { writeText("failed owner") }
            assertTrue(marker.isFile, "The deliberate failure must leave a runtime file for cleanup")
            throw sentinel
        })
    }
    kotlin.test.assertSame(sentinel, observed,
        "Runtime cleanup must preserve the original body failure")
    val staleMarker = File(root.parentFile, "interrupted-owner.txt")
    val staleInput = File(root, "stale-input.bin")
    invokeOwnedRuntime({
        assertTrue(!root.parentFile.exists(),
            "A failed invocation must remove its complete runtime before the next lease owner")
        assertTrue(root.mkdirs(), "The persisted-state probe must recreate its owned runtime root")
        staleMarker.writeText("previous owner")
        staleInput.writeText("previous input")
        assertTrue(staleMarker.isFile && staleInput.isFile,
            "The persisted-state probe must create stale parent and root files before reclamation")
    }) {
        assertTrue(!staleMarker.exists() && !staleInput.exists(),
            "A new invocation must reclaim stale parent and root files before restoring inputs")
        assertEquals(emptyList(), root.listFiles().orEmpty().map { it.name },
            "A reclaimed runtime must be empty before restoring inputs")
        val project = File(root, "project").apply { check(mkdirs()) }
        val cache = File(root, "cache")
        val clock = ManualClock(1000)
        val fs = FakeFileSystem()
        val path = "/attempts.jsonl".toPath()
        val portDirectory = File(root, "runtime-ports")
        val bootstrapPortFile = File(portDirectory, "bootstrap-port.txt")
        val handoffPortFile = File(portDirectory, "handoff-port.txt")
        assertFalse(portDirectory.exists(), "No runtime port data may exist before source and compiler-state verification")
        java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { handoff ->
        val release = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
        val currentManager = File(LocalBuildWorkspace::class.java.protectionDomain.codeSource.location.toURI())
        assertTrue(currentManager.isFile, "The compiler fixture requires the actual current manager JAR")
        val managerDigest = currentManager.inputStream().use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
        val managerArtifact = File(root, "manager-artifact")
        val managerJar = File(managerArtifact, "manager.jar")
        File(project, "build.kts").writeText("""
            @KotlinBuildScript("https://tools.kotlin.build/")
            package timeline.fixture
            fun manager(): java.io.File { return java.io.File("${managerArtifact.absolutePath}") }
        """.trimIndent())
        val observed = java.util.concurrent.CountDownLatch(2)
        val sawWaiting = java.util.concurrent.atomic.AtomicBoolean()
        val sawGc = java.util.concurrent.atomic.AtomicBoolean()
        var releaseTask: java.util.concurrent.Future<*>? = null
        val recordingFs = object : okio.ForwardingFileSystem(fs) {
            override fun appendingSink(file: okio.Path, mustExist: Boolean): okio.Sink {
                val sink = super.appendingSink(file, mustExist)
                return object : okio.ForwardingSink(sink) {
                    override fun write(source: okio.Buffer, byteCount: Long) {
                        val row = source.clone().readUtf8(byteCount)
                        super.write(source, byteCount)
                        println("I419i diagnostic event " + row.trim())
                        val releaseFuture = releaseTask
                        if (releaseFuture != null && releaseFuture.state() == java.util.concurrent.Future.State.FAILED) {
                            println("I419i diagnostic handoff-future-failure " + releaseFuture.exceptionNow())
                        }
                        if (row.contains("\"threadName\":\"attributed-fixture-worker\"") &&
                            row.contains("\"state\":\"WAITING\"") && sawWaiting.compareAndSet(false, true)) observed.countDown()
                        if (row.contains("\"event\":\"gc-pause\"") && sawGc.compareAndSet(false, true)) observed.countDown()
                    }
                }
            }
        }
        val script = File(project, "tests/testProbe.kts").apply { parentFile.mkdirs() }
        script.writeText("""
            @file:build.kotlin.withartifact.WithArtifact("org.jetbrains.kotlin:kotlin-stdlib:1.8.22")
            @file:WithArtifact("build.kotlin.annotations:build-kotlin-annotations:0.0.10")
@file:WithArtifact("kompile.buildscript:kompile-buildscript:0.0.48")
@file:WithArtifact("kompile.executionenvironment:kompile-executionenvironment:0.0.59")
@file:WithArtifact("community.kotlin.clocks.simple:community-kotlin-clocks-simple:0.0.3")
@file:WithArtifact("util.cache.hashtofile:hash-to-file-cache:0.0.13")
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
@file:WithArtifact("buildtest.server:buildtest-server-service:0.0.124")
@file:WithArtifact("foundation.url:resolver:0.0.556")
@file:WithArtifact("timeline.fixture.manager()")
@file:WithArtifact("resource.arena:resource-arena:0.0.1")

package timeline.fixture
import build.kotlin.withartifact.WithArtifact

            fun testProbe() {
                val bootstrapPort = java.io.File("${bootstrapPortFile.absolutePath}").readText(Charsets.UTF_8).trim().toInt()
                val handoffPort = java.io.File("${handoffPortFile.absolutePath}").readText(Charsets.UTF_8).trim().toInt()
                java.net.URL.setURLStreamHandlerFactory { protocol ->
                    if (protocol != "https") null else object : java.net.URLStreamHandler() {
                        override fun openConnection(url: java.net.URL): java.net.URLConnection {
                            check(url.host in setOf("kotlin.directory", "repo1.maven.org", "maven-central.storage-download.googleapis.com")) {
                                "Unexpected repository in the child fixture: " + url.host
                            }
                            return java.net.URL("http://127.0.0.1:${'$'}bootstrapPort/" + url.path.removePrefix("/maven2").removePrefix("/"))
                                .openConnection(java.net.Proxy.NO_PROXY)
                        }
                        override fun openConnection(url: java.net.URL, proxy: java.net.Proxy) = openConnection(url)
                    }
                }
                val workload = java.nio.file.Files.createTempDirectory("joined-workload").toFile()
                try {
                    val source = java.io.File(workload, "Probe.kt").apply { writeText("package attribution; fun value() = 42") }
                    val output = java.io.File(workload, "classes").apply { mkdirs() }
                    val stdlib = java.io.File(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI())
                    kompile.compileKotlin(listOf(source), listOf(stdlib), output,
                        java.util.concurrent.ConcurrentLinkedQueue<kotlinx.algebraiceffects.NotificationEffect>())
                    check(java.io.File(output, "attribution/ProbeKt.class").isFile)
                    val invalid = java.io.File(workload, "Invalid.kt").apply { writeText("package attribution; fun broken() = missing") }
                    val failure = kotlin.test.assertFailsWith<Error> {
                        kompile.compileKotlin(listOf(invalid), listOf(stdlib), java.io.File(workload, "invalid-classes"),
                            java.util.concurrent.ConcurrentLinkedQueue<kotlinx.algebraiceffects.NotificationEffect>())
                    }
                    kotlin.test.assertEquals("Compile failure: Unresolved reference: missing", failure.message)
                    val project = java.io.File(workload, "project").apply { mkdirs() }
                    java.io.File(project, "build.kts").writeText("@KotlinBuildScript(\"https://tools.kotlin.build/\")\npackage joined.fixture\n")
                    val cache = java.io.File(workload, "cache")
                    kompile.LocalBuildWorkspace(listOf(project), cache).use { nested ->
                        val nestedScript = java.io.File(project, "tests/testNested.kts").apply {
                            parentFile.mkdirs()
                            writeText("@file:build.kotlin.withartifact.WithArtifact(\"org.jetbrains.kotlin:kotlin-stdlib:1.8.22\")\npackage joined.fixture\nfun testNested() { check(1 + 1 == 2) }\n")
                        }
                        nested.prepareTestBatch(listOf(nestedScript.absolutePath), 1).use { }
                        nested.prepareTestBatch(listOf(nestedScript.absolutePath), 1).use { }
                        util.resources.ResourceArena { arena ->
                            val assistant = kompile.executionenvironment.PrivilegedBuildAssistant(arena, cache, nested)
                            val generated = java.io.File(assistant.createDirectory(), "generated.txt").apply { writeText("generated input") }
                            val digest = org.apache.commons.codec.digest.DigestUtils.sha256Hex(generated.readBytes())
                            assistant.recordDeclaredCacheInputs(mapOf(generated.absolutePath to digest), true)
                            check(assistant.snapshotObservedInputFileHashes().isNotEmpty())
                        }
                    }
                } finally { check(workload.deleteRecursively()) }

                val monitor = Object()
                val waiting = java.util.concurrent.CountDownLatch(1)
                val released = java.util.concurrent.CountDownLatch(1)
                val worker = Thread({
                    synchronized(monitor) {
                        waiting.countDown()
                        while (released.count > 0) monitor.wait()
                    }
                }, "attributed-fixture-worker")
                worker.start()
                try {
                    check(waiting.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    java.net.Socket("127.0.0.1", handoffPort).use { control ->
                        System.gc()
                        check(control.getInputStream().read() == 1)
                    }
                } finally {
                    synchronized(monitor) { released.countDown(); monitor.notifyAll() }
                    worker.join(5000)
                    check(!worker.isAlive)
                }
            }
        """.trimIndent())
        File(root, "manager-content.sha256").writeText(managerDigest + "\n", Charsets.UTF_8)
        val actualInputs = root.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
        assertEquals(originalInputs.keys, actualInputs.keys,
            "Prepared compiler state must match the complete freshly written source and manager-proof inventory")
        for ((path, content) in originalInputs) {
            assertTrue(content.contentEquals(actualInputs.getValue(path)),
                "Prepared compiler state must match the exact fresh source/manager-proof bytes at $path")
        }
        check(managerArtifact.mkdirs()) { "Could not create the runtime manager artifact container $managerArtifact." }
        currentManager.copyTo(managerJar)
        assertEquals(listOf("manager.jar"), managerArtifact.listFiles().orEmpty().map { it.name }.sorted(),
            "The runtime manager artifact container must contain only manager.jar")
        assertTrue(managerJar.isFile, "The runtime manager artifact entry must be a file")
        val aliasDigest = managerJar.inputStream().use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
        assertEquals(managerDigest, aliasDigest, "The fixed manager alias must contain the actual current manager bytes")
        val seedEntries = entries.filterKeys {
            it.startsWith("cache/buildscriptCache/") || it.startsWith("cache/compiledTestScripts/")
        }
        for (tree in listOf("buildscriptCache", "compiledTestScripts")) {
            check(seedEntries.keys.any { it.startsWith("cache/$tree/") }) {
                "Prepared compiler fixture $resource has no $tree compiler state"
            }
        }
        for ((path, content) in seedEntries) {
            val target = File(root, path)
            check(target.parentFile.mkdirs() || target.parentFile.isDirectory)
            target.writeBytes(content)
        }
        val restoredCache = cache.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
        assertEquals(seedEntries.keys, restoredCache.keys, "Only the two declared compiler cache trees may be restored")
        for ((path, content) in seedEntries) {
            assertTrue(content.contentEquals(restoredCache.getValue(path)), "Restored compiler state differs at $path")
        }
        assertEquals(listOf("buildscriptCache", "compiledTestScripts"), cache.listFiles().orEmpty().map { it.name }.sorted(),
            "Preparation must restore compiler state without producer result state")
        assertEquals(setOf("build.kts", "tests/testProbe.kts"), project.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(project).invariantSeparatorsPath }.toSet(),
            "The target workspace must contain only the original build and probe sources before execution")
        assertFalse(portDirectory.exists(), "Prepared compiler resources must not restore runtime port data")
        assertTrue(bootstrapServer.isBound && !bootstrapServer.isClosed,
            "The bootstrap port must belong to this invocation's active server")
        assertTrue(handoff.isBound && !handoff.isClosed,
            "The handoff port must belong to this invocation's active server")
        assertEquals(java.net.InetAddress.getByName("127.0.0.1"), bootstrapServer.inetAddress,
            "The bootstrap server must own its loopback endpoint")
        assertEquals(java.net.InetAddress.getByName("127.0.0.1"), handoff.inetAddress,
            "The handoff server must own its loopback endpoint")
        check(portDirectory.mkdirs()) { "Could not create the runtime port directory $portDirectory." }
        bootstrapPortFile.writeText(bootstrapServer.localPort.toString() + "\n", Charsets.UTF_8)
        handoffPortFile.writeText(handoff.localPort.toString() + "\n", Charsets.UTF_8)
        assertEquals(setOf("bootstrap-port.txt", "handoff-port.txt"), portDirectory.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(portDirectory).invariantSeparatorsPath }.toSet(),
            "The runtime port directory must contain exactly this invocation's two port files")
        assertEquals(bootstrapServer.localPort.toString() + "\n", bootstrapPortFile.readText(Charsets.UTF_8),
            "The bootstrap port file must name this invocation's owned server")
        assertEquals(handoff.localPort.toString() + "\n", handoffPortFile.readText(Charsets.UTF_8),
            "The handoff port file must name this invocation's owned server")
        assertEquals(setOf("cache", "manager-artifact", "manager-content.sha256", "project", "runtime-ports"),
            root.listFiles().orEmpty().map { it.name }.toSet(),
            "Only verified source, manager, compiler state and current runtime ports may precede execution")
        val preparationWork = mutableListOf<TestPreparationWorkEffect>()
        LocalBuildWorkspace(listOf(project), cache).use { workspace ->
            fs.write(path) { writeUtf8("{\"existing\":true}\n") }
            val batch = Effective {
                handler { _: kompile.buildscript.StartingKotlinCompile ->
                    throw AssertionError("testAttemptTimelineJoinedWorkloadEvents compiled its outer fixture inside the timed body; compiler state must come from its declared build dependency")
                }
                handler { work: TestPreparationWorkEffect -> preparationWork.add(work); Unit }
                handler { _: NotificationEffect -> /* Only lifecycle records are under test. */ }
                println("I419i diagnostic prepare-start ns=" + System.nanoTime())
                workspace.prepareTestBatch(listOf(script.absolutePath), 1).also { println("I419i diagnostic prepare-end ns=" + System.nanoTime()) }
            }
            val listener = object : PreparedTestExecutionListener {
                override fun onChildProcessStarted(test: PreparedTestExecutionIdentity, pid: Long) { println("I419i diagnostic nested-launched pid=" + pid + " ns=" + System.nanoTime()); clock.advanceBy(10) }
                override fun onTestReady(test: PreparedTestExecutionIdentity) {
                    println("I419i diagnostic nested-ready ns=" + System.nanoTime())
                    clock.advanceBy(2000)
                    releaseTask = release.submit {
                        println("I419i diagnostic handoff-accept-start ns=" + System.nanoTime())
                        handoff.soTimeout = 10000
                        handoff.accept().use { socket ->
                            println("I419i diagnostic handoff-accept-return ns=" + System.nanoTime())
                            try { check(observed.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "The runtime observations were not recorded while the worker was waiting." } }
                            finally { socket.getOutputStream().write(1); socket.getOutputStream().flush() }
                        }
                    }
                }
            }
            val results = batch.use { Effective {
                handler { work: TestPreparationWorkEffect ->
                    preparationWork.add(work)
                    assertEquals(0, work.compilationsPerformed,
                        "The timed probe preparation must perform zero compiler invocations")
                    assertEquals(1, work.compilationsReused,
                        "The timed probe preparation must reuse its one declared compiled script")
                    Unit
                }
                handler { _: NotificationEffect -> /* Results are checked below. */ }
                try { it.executeWithTimeline(path, recordingFs, clock, listener) }
                finally {
                    try { releaseTask?.get(15, java.util.concurrent.TimeUnit.SECONDS) }
                    finally { handoff.close(); release.shutdownNow(); check(release.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) }
                }
            } }
            assertEquals(ExecutionStatus.SUCCESS, results.results.single().status, results.results.single().error?.stackTraceToString())
            val lines = fs.read(path) { readUtf8() }.lines().filter { it.isNotBlank() }.map(::JSONObject)
            assertTrue(lines.first().getBoolean("existing"))
            val events = lines.drop(1)
            assertEquals(listOf("suite-start", "queued", "launched", "ready", "body-finished", "result"), events.map { it.getString("event") })
            assertEquals(listOf(0L, 0L, 0L, 10L, 2010L, 2010L), events.map { it.getLong("offsetMillis") })
            assertTrue(events.first().has("wallClockMillis"))
            assertTrue(events.drop(1).none { it.has("wallClockMillis") })
            assertEquals(1, events.drop(1).map { it.getString("attemptId") }.distinct().size)
            assertTrue(events.drop(1).all { it.getInt("attemptNumber") == 1 && it.getString("functionName") == "testProbe" && it.getString("filePath") == script.absolutePath })
            val pid = events[2].getLong("pid")
            assertTrue(pid > 0)
            assertTrue(events.drop(2).all { it.getLong("pid") == pid })
            assertEquals("SUCCESS", events.last().getString("outcome"))
            val context = fs.read((path.toString() + ".resources.jsonl").toPath()) {
                readUtf8()
            }.lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()
            val coverage = context.single { it.getString("event") == "recorder-start" }
            assertTrue(coverage.getBoolean("upstreamCompilerPhasesAvailable"))
            assertTrue(coverage.getBoolean("upstreamLockSignalsAvailable"))
            assertTrue(coverage.getBoolean("isolatedRuleProcessStartsAvailable"))
            val summary = context.single { it.getString("event") == "child-observer-summary" }
            val attemptId = events[1].getString("attemptId")
            val attributed = context.filter { it.has("childMonotonicNanos") }
            assertTrue(attributed.isNotEmpty())
            val requiredEvents = setOf("compiler-request", "compiler-start", "compiler-phase-start",
                "compiler-phase-end", "compiler-finish", "compiler-cache-request", "compiler-cache-hit", "lock-requested", "lock-acquired", "lock-released",
                "publication-wait-start", "publication-wait-end", "publication-committed",
                "thread-observed", "heap-sample", "gc-pause", "child-observer-summary")
            assertEquals(requiredEvents, attributed.map { it.getString("event") }.toSet().intersect(requiredEvents),
                "One recorded workload must contain every compiler, lock, publication, thread and collector event.")
            val cacheRequests = attributed.filter { it.getString("event") == "compiler-cache-request" }
            val cacheHits = attributed.filter { it.getString("event") == "compiler-cache-hit" }
            assertEquals(2, cacheRequests.size)
            assertEquals(1, cacheHits.size)
            assertEquals(1, cacheRequests.map { it.getString("cacheKey") }.distinct().size)
            assertEquals(cacheRequests.first().getString("cacheKey"), cacheHits.single().getString("cacheKey"))
            val compilerFinishes = attributed.filter { it.getString("event") == "compiler-finish" }
            assertTrue(compilerFinishes.any { it.getString("compilerOutcome") == "OK" })
            assertEquals(1, compilerFinishes.count { it.getString("compilerOutcome") == "COMPILATION_ERROR" })
            val publication = attributed.filter { it.optString("event").startsWith("publication-") }
            val committed = publication.single { it.getString("event") == "publication-committed" }
            val publicationKey = committed.getString("cacheKey")
            assertTrue(publicationKey.isNotBlank())
            val waitStart = publication.single { it.getString("event") == "publication-wait-start" && it.getString("cacheKey") == publicationKey }
            val waitEnd = publication.single { it.getString("event") == "publication-wait-end" && it.getString("operationId") == waitStart.getString("operationId") }
            assertEquals("returned", waitEnd.getString("waitOutcome"))
            assertEquals("generated-input-snapshot", committed.getString("publicationKind"))
            assertTrue(waitEnd.getLong("waitNanos") >= 0)

            assertTrue(attributed.all { it.getString("attemptId") == attemptId && it.getLong("processStartMillis") > 0 })
            assertTrue(context.any { it.getString("event") == "heap-sample" && it.getLong("heapUsedBytes") >= 0 })
            val workerStates = context.filter { it.optString("threadName") == "attributed-fixture-worker" }
            assertTrue(workerStates.any { it.getString("event") == "thread-observed" })
            assertTrue(workerStates.any { it.optString("state") == "WAITING" && !it.isNull("lockIdentity") })
            assertTrue(context.any { it.getString("event") == "gc-pause" && it.getLong("pauseMillis") >= 0 })
            // Budgets count bounded work rather than the speed of this machine.
            assertEquals(1024, summary.getInt("queueCapacity"))
            assertTrue(summary.getInt("maxQueueDepth") <= 1024)
            assertEquals(0, summary.getInt("stackDepth"))
            assertEquals(250, summary.getInt("sampleIntervalMillis"))
            assertEquals(0L, summary.getLong("droppedEvents"))
            assertTrue(summary.getLong("samplingCpuNanos") >= 0)
            assertTrue(summary.getLong("senderCpuNanos") >= 0)
            val observerCpuNanos = summary.getLong("startupCpuNanos") +
                summary.getLong("samplingCpuNanos") + summary.getLong("senderCpuNanos")
            assertTrue(summary.getLong("startupCpuNanos") >= 0)
            assertTrue(observerCpuNanos <= 250_000_000L,
                "This controlled fixture allows at most 250 ms of observer startup/sampling/sender CPU, but used $observerCpuNanos ns.")
            val before = fs.read(path) { readUtf8() }
            assertFailsWith<IllegalStateException> { batch.executeWithTimeline(path, fs, clock) }
            assertEquals(before, fs.read(path) { readUtf8() })
        }
        assertEquals(1, preparationWork.size, "Exactly one preparation-work report must describe the probe script")
    } finally {
        try {
            // Preparation can fail before the execution cleanup block is entered.
            handoff.close()
            release.shutdownNow()
            check(release.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            root.deleteRecursively()
            cache.deleteRecursively()
        }
    }
        }
    }
        bootstrapFailure.get()?.let { throw AssertionError("The local bootstrap repository failed.", it) }
    } finally {
        try {
            bootstrapServer.close()
            try {
                // Stop registration before closing the accepted sockets and their workers.
                bootstrapAcceptor?.get(10, java.util.concurrent.TimeUnit.SECONDS)
            } finally {
                bootstrapSockets.forEach { it.close() }
            }
        } finally {
            bootstrapExecutor.shutdownNow()
            check(bootstrapExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                "The local bootstrap repository did not stop."
            }
        }
    }
}
