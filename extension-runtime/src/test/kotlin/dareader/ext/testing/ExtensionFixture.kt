package dareader.ext.testing

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider

/**
 * Builds a real extension jar at test runtime — no checked-in binaries: a
 * minimal [eu.kanade.tachiyomi.source.Source] implementation is compiled
 * against this module's classpath (JDK compiler) and jarred, so loader tests
 * exercise genuine classloading.
 */
object ExtensionFixture {
    const val PKG = "com.example.fixture"
    const val SOURCE_CLASS = "fixture.TestSource"
    const val BROKEN_SOURCE_CLASS = "fixture.BoomSource"
    const val FAKE_SOURCE_ID = 424242L

    fun buildSourceJar(workDir: Path): Path = build(workDir, includeFactory = false)

    fun buildFactoryJar(workDir: Path): Path = build(workDir, includeFactory = true)

    private fun build(workDir: Path, includeFactory: Boolean): Path {
        Files.createDirectories(workDir)
        val sourceFile = workDir.resolve("src/fixture/TestSource.java")
        val brokenFile = workDir.resolve("src/fixture/BoomSource.java")
        val factoryFile = workDir.resolve("src/fixture/TestFactory.java")
        val helperFile = workDir.resolve("src/fixture/UnlinkableSource.java")
        val stubFile = workDir.resolve("src/fixture/StubException.java")
        val classesDir = workDir.resolve("classes")
        Files.createDirectories(sourceFile.parent)
        Files.createDirectories(classesDir)
        Files.writeString(sourceFile, JAVA_SOURCE)
        Files.writeString(brokenFile, BOOM_SOURCE)
        Files.writeString(helperFile, UNLINKABLE_SOURCE)
        Files.writeString(stubFile, STUB_EXCEPTION)
        val inputs = mutableListOf(sourceFile, brokenFile, helperFile, stubFile)
        if (includeFactory) {
            Files.writeString(factoryFile, FACTORY_SOURCE)
            inputs.add(factoryFile)
        }

        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) {
            "fixture needs a JDK (no system Java compiler found)"
        }
        // `java.class.path` can be a Gradle classpath manifest jar under the
        // test worker; the build passes the real path via a system property.
        val classpath = System.getProperty("dareader.test.classpath")
            ?: System.getProperty("java.class.path")
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
        val task =
            compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-classpath", classpath, "-d", classesDir.toString()),
                null,
                fileManager.getJavaFileObjectsFromFiles(inputs.map(Path::toFile)),
            )
        check(task.call()) {
            "fixture compilation failed:\n" + diagnostics.diagnostics.joinToString("\n")
        }
        fileManager.close()

        val jar = workDir.resolve("extension.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            Files.walk(classesDir).use { stream ->
                stream.filter { Files.isRegularFile(it) }.forEach { file ->
                    val name = classesDir.relativize(file).toString().replace(File.separatorChar, '/')
                    // The stub is compile-time only: jarring it would make the
                    // helper link, so the jar would no longer mirror real jars.
                    if (name == "fixture/StubException.class") return@forEach
                    out.putNextEntry(JarEntry(name))
                    Files.copy(file, out)
                    out.closeEntry()
                }
            }
        }
        return jar
    }

    private val STUB_EXCEPTION = """
        package fixture;

        /** Compile-time stand-in for a type only the extension runtime lacks. */
        public class StubException extends RuntimeException {
        }
    """.trimIndent()

    private val UNLINKABLE_SOURCE = """
        package fixture;

        /**
         * Helper that loads but cannot link: the exception table makes the
         * verifier resolve fixture.StubException, which the jar omits. Real
         * jars carry the same shape (e.g. a deep-link Activity catching
         * android.content.ActivityNotFoundException), and the entry scan must
         * skip it instead of failing the whole jar.
         */
        public class UnlinkableSource {
            public boolean probe() {
                try {
                    return System.nanoTime() != 0L;
                } catch (StubException e) {
                    return false;
                }
            }
        }
    """.trimIndent()

    private val BOOM_SOURCE = """
        package fixture;

        /** Loads fine, then dies during class initialization (LinkageError path). */
        public class BoomSource {
            static {
                if (System.nanoTime() != Long.MIN_VALUE) {
                    throw new RuntimeException("boom during static init");
                }
            }
        }
    """.trimIndent()

    private val FACTORY_SOURCE = """
        package fixture;

        import eu.kanade.tachiyomi.source.Source;
        import eu.kanade.tachiyomi.source.SourceFactory;
        import java.util.Collections;
        import java.util.List;

        public class TestFactory implements SourceFactory {
            @Override public List<Source> createSources() {
                return Collections.<Source>singletonList(new TestSource());
            }
        }
    """.trimIndent()

    private val JAVA_SOURCE = """
        package fixture;

        import eu.kanade.tachiyomi.source.Source;
        import eu.kanade.tachiyomi.source.model.FilterList;
        import eu.kanade.tachiyomi.source.model.MangasPage;
        import eu.kanade.tachiyomi.source.model.Page;
        import eu.kanade.tachiyomi.source.model.SChapter;
        import eu.kanade.tachiyomi.source.model.SManga;
        import eu.kanade.tachiyomi.source.model.SMangaUpdate;
        import java.util.Collections;
        import java.util.List;
        import kotlin.coroutines.Continuation;
        import rx.Observable;

        public class TestSource implements Source {
            @Override public long getId() { return 424242L; }
            @Override public String getName() { return "Fixture"; }
            @Override public String getLang() { return "en"; }
            @Override public boolean getSupportsLatest() { return false; }
            @Override public FilterList getFilterList() { return new FilterList(); }

            @Override
            public Object getPopularManga(int page, Continuation<? super MangasPage> continuation) {
                return new MangasPage(Collections.<SManga>emptyList(), false);
            }

            @Override
            public Object getLatestUpdates(int page, Continuation<? super MangasPage> continuation) {
                return new MangasPage(Collections.<SManga>emptyList(), false);
            }

            @Override
            public Object getSearchManga(int page, String query, FilterList filters, Continuation<? super MangasPage> continuation) {
                return new MangasPage(Collections.<SManga>emptyList(), false);
            }

            @Override
            public Object getMangaUpdate(SManga manga, List<? extends SChapter> chapters, boolean fetchDetails, boolean fetchChapters, Continuation<? super SMangaUpdate> continuation) {
                return new SMangaUpdate(manga, chapters);
            }

            @Override
            public Object getPageList(SChapter chapter, Continuation<? super List<? extends Page>> continuation) {
                return Collections.<Page>emptyList();
            }

            @Override public Observable<SManga> fetchMangaDetails(SManga manga) { return null; }
            @Override public Observable<List<SChapter>> fetchChapterList(SManga manga) { return null; }
            @Override public Observable<List<Page>> fetchPageList(SChapter chapter) { return null; }
        }
    """.trimIndent()
}
