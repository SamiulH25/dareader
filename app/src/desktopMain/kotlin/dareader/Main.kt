package dareader

import dareader.ext.ExtensionContract
import dareader.ext.pkg.requireAccepted
import dareader.ext.store.defaultHttpClient
import dareader.ext.store.fetchSplitExtensionList
import dareader.ext.store.fetchStore
import eu.kanade.tachiyomi.source.model.displayTitle

private fun printUsage() {
    println(
        """
        usage: dareader <subcommand> [args] | <indexUrl> [packageFilter]
          --ui [apkUrl] [--at=<screen>] launch Compose desktop UI; optional APK to load,
                                        optional stop point (browse|detail|reader|
                                        library|history|extensions|more)
          --probe                       self-check lib-version probe
          --help, -h                    show this help
          flare <url> [solverUrl]       fetch URL through the Cloudflare interceptor
          canvas                        exercise the android.graphics stub (renders test PNG)
          prefs <store> <key> [value]   get/set a stub SharedPreferences value
          js <script>                   evaluate JavaScript via the Rhino engine
          trust <indexUrl> <pkgSubstring>  show APK cert hashes vs store signing key
          dexcode <apkUrl> <class> <method>  disassemble a dex method
          dexinfo <apkUrl> <classFilter>     list dex classes matching filter
          popular <apkUrl> <sourceId>   fetch popular page 1 for a numeric source id
          load <apkUrl>                 parse manifest + list extension sources
          dex <apkUrl>                  convert dex to jar + list vendored classes
          apk <apkUrl>                  parse manifest + print package/version/judge
          <indexUrl> [packageFilter]    fetch extension store index + list extensions
        """.trimIndent(),
    )
}

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] == "--help" || args[0] == "-h") {
        printUsage()
        return
    }
    if (args.isNotEmpty() && args[0] == "--ui") {
        val state = dareader.ui.AppState()
        val options = args.drop(1)
        val apkUrl = options.firstOrNull { !it.startsWith("--") }
        val stopAt = options.firstOrNull { it.startsWith("--at=") }?.removePrefix("--at=")
        when {
            apkUrl != null -> state.autoDemo(apkUrl, stopAt)
            stopAt != null -> state.openScreen(stopAt)
        }
        androidx.compose.ui.window.application {
            val windowState = dareader.ui.rememberDareaderWindowState()
            androidx.compose.ui.window.Window(
                onCloseRequest = {
                    dareader.ui.saveWindowState(windowState)
                    state.shutdown()
                    exitApplication()
                },
                title = "dareader",
                state = windowState,
            ) {
                dareader.ui.ReaderApp(state)
            }
        }
        return
    }
    if (args.size == 1 && args[0] == "--probe") {
        val probe = ExtensionContract.libVersionFromVersionName("1.6.3")
        check(probe != null && ExtensionContract.isSupportedLibVersion(probe))
        println("dareader shell OK (lib probe=$probe)")
        return
    }
    if (args[0] == "flare") {
        require(args.size == 2 || args.size == 3) { "usage: flare <url> [solverUrl]" }
        if (args.size == 3) suwayomi.tachidesk.server.serverConfig.configure(args[2])
        dareader.ext.di.DareaderGraph.ensureCore()
        val flareClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor(eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor {})
            .build()
        val request = okhttp3.Request.Builder().url(args[1]).build()
        flareClient.newCall(request).execute().use { response ->
            println("flare: HTTP ${response.code} server=${response.header("Server")}")
        }
        return
    }

    if (args[0] == "canvas") {
        val bitmap = android.graphics.Bitmap.createBitmap(400, 200, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(-1)
        val paint = android.text.TextPaint()
        paint.setColor(-16777216)
        paint.setTextSize(24f)
        val layout = android.text.StaticLayout("hello dareader", paint, 380, android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, true)
        layout.draw(canvas)
        val out = java.nio.file.Files.createTempFile("dareader-canvas-", ".png")
        java.nio.file.Files.newOutputStream(out).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        println("canvas height=${layout.getHeight()} png=${java.nio.file.Files.size(out)} bytes")
        return
    }

    if (args[0] == "prefs") {
        require(args.size == 3 || args.size == 4) { "usage: prefs <store> <key> [value]" }
        val prefs = dareader.ext.di.DareaderGraph.application.getSharedPreferences(args[1], 0)
        if (args.size == 4) {
            prefs.edit().putString(args[2], args[3]).commit()
            println("set ${args[2]}=${args[3]}")
        } else {
            println("get ${args[2]}=${prefs.getString(args[2], "<unset>")}")
        }
        return
    }

    if (args[0] == "js") {
        require(args.size == 2) { "usage: js <script>" }
        val engine = eu.kanade.tachiyomi.network.JavaScriptEngine(dareader.ext.di.DareaderGraph.application)
        val result = kotlinx.coroutines.runBlocking { engine.evaluate<Any>(args[1]) }
        println("js: $result")
        return
    }

    if (args[0] == "trust") {
        require(args.size == 3) { "usage: trust <indexUrl> <pkgSubstring>" }
        val client = defaultHttpClient()
        val store = fetchStore(client, args[1])
        val extensions = store.extensionList?.extensions
            ?: store.extensionListUrl?.let { fetchSplitExtensionList(client, it).extensions }
            ?: emptyList()
        val ext = extensions.firstOrNull { it.packageName.contains(args[2]) }
            ?: error("no extension matching ${args[2]}")
        val apk = dareader.ext.pkg.downloadApk(client, ext.resources.apkUrl)
        try {
            val hashes = dareader.ext.trust.apkCertificateHashes(apk)
            println("cert: ${hashes.joinToString()}")
            println("store: ${store.signingKey}")
            println(if (dareader.ext.trust.matchesStoreKey(hashes, store.signingKey)) "TRUSTED" else "UNTRUSTED")
        } finally {
            java.nio.file.Files.deleteIfExists(apk)
        }
        return
    }

    if (args[0] == "dexcode") {
        require(args.size == 4) { "usage: dexcode <apkUrl> <class> <method>" }
        val apk = dareader.ext.pkg.downloadApk(defaultHttpClient(), args[1])
        try {
            dareader.ext.load.dexCode(apk, args[2], args[3]).forEach { println(it) }
        } finally {
            java.nio.file.Files.deleteIfExists(apk)
        }
        return
    }

    if (args[0] == "dexinfo") {
        require(args.size == 3) { "usage: dexinfo <apkUrl> <classFilter>" }
        val apk = dareader.ext.pkg.downloadApk(defaultHttpClient(), args[1])
        try {
            dareader.ext.load.dexInventory(apk, args[2]).forEach { println(it) }
        } finally {
            java.nio.file.Files.deleteIfExists(apk)
        }
        return
    }

    if (args[0] == "popular") {
        require(args.size == 3) { "usage: popular <apkUrl> <sourceId>" }
        val client = defaultHttpClient()
        val apk = dareader.ext.pkg.downloadApk(client, args[1])
        try {
            val manifest = dareader.ext.pkg.parseApkManifest(apk)
            manifest.requireAccepted()
            val raw = requireNotNull(manifest.entryClass) { "no source class or factory" }
            val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
            val jar = dareader.ext.load.dexToJar(apk)
            try {
                val ext = dareader.ext.load.loadExtensionSources(jar, fqcn, manifest.packageName)
                val sourceId = args[2].toLongOrNull()
                    ?: error("invalid source id '${args[2]}': expected numeric id (usage: popular <apkUrl> <sourceId>)")
                val source = ext.sources.firstOrNull { it.id == sourceId }
                    ?: error("no source with id $sourceId")
                val page = kotlinx.coroutines.runBlocking { source.getPopularManga(1) }
                println("popular: hasNext=${page.hasNextPage}")
                page.mangas.take(5).forEach { println("- ${it.displayTitle()} :: ${runCatching { it.url }.getOrNull()}") }
                ext.close()
            } finally {
                java.nio.file.Files.deleteIfExists(jar)
            }
        } finally {
            java.nio.file.Files.deleteIfExists(apk)
        }
        return
    }

    if (args[0] == "load") {
        require(args.size == 2) { "usage: load <apkUrl>" }
        val client = defaultHttpClient()
        val apk = dareader.ext.pkg.downloadApk(client, args[1])
        try {
            val manifest = dareader.ext.pkg.parseApkManifest(apk)
            println(manifest.judge())
            manifest.requireAccepted()
            val raw = requireNotNull(manifest.entryClass) { "no source class or factory" }
            val fqcn = if (raw.startsWith(".")) manifest.packageName + raw else raw
            val jar = dareader.ext.load.dexToJar(apk)
            try {
                val ext = dareader.ext.load.loadExtensionSources(jar, fqcn, manifest.packageName)
                ext.sources.forEach { println("source ${it.id} ${it.name}") }
                ext.close()
            } finally {
                java.nio.file.Files.deleteIfExists(jar)
            }
        } finally {
            java.nio.file.Files.deleteIfExists(apk)
        }
        return
    }

    if (args[0] == "dex") {
        require(args.size == 2) { "usage: dex <apkUrl>" }
        val client = defaultHttpClient()
        val apk = dareader.ext.pkg.downloadApk(client, args[1])
        try {
            val jar = dareader.ext.load.dexToJar(apk)
            println("jar=$jar (${java.nio.file.Files.size(jar)} bytes)")
            java.util.jar.JarFile(jar.toFile()).use { jf ->
                jf.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && (it.contains("keiyoushi") || it.contains("eu/kanade")) }
                    .take(20)
                    .forEach { println("  $it") }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(apk)
        }
        return
    }

    if (args[0] == "apk") {
        require(args.size == 2) { "usage: apk <apkUrl>" }
        val apk = dareader.ext.pkg.downloadApk(defaultHttpClient(), args[1])
        try {
            val manifest = dareader.ext.pkg.parseApkManifest(apk)
            println("${manifest.packageName} v${manifest.versionName} (${manifest.versionCode})")
            println("label=${manifest.label} name=${manifest.extensionName}")
            println(manifest.judge())
        } finally {
            java.nio.file.Files.deleteIfExists(apk)
        }
        return
    }


    val client = defaultHttpClient()
    val store = fetchStore(client, args[0])
    println("store: ${store.name} [${store.badgeLabel}] signingKey=${store.signingKey.take(16)}...")
    val list: dareader.ext.store.NetworkExtensionStore.ExtensionList? = store.extensionList
        ?: store.extensionListUrl?.let { fetchSplitExtensionList(client, it) }
    val extensions: List<dareader.ext.store.NetworkExtensionStore.Extension> =
        list?.extensions ?: emptyList()
    println("extensions: ${extensions.size}")
    val filter = args.getOrNull(1)
    extensions
        .filter { filter == null || it.packageName.contains(filter) }
        .take(if (filter == null) 10 else 50)
        .forEach { ext ->
            println("- ${ext.name} ${ext.packageName} v${ext.versionName} lib=${ext.extensionLib}")
            println("    apk=${ext.resources.apkUrl}")
            ext.sources.forEach { src ->
                println("    ${src.id} ${src.name} [${src.language}] ${src.homeUrl}")
            }
        }
}
