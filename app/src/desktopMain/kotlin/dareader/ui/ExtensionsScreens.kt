package dareader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dareader.ext.manager.Installed
import dareader.ext.store.NetworkExtensionStore
import dareader.ext.store.RepoEntry
import dareader.ext.store.defaultHttpClient
import dareader.ext.store.fetchRepos
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Extensions: the single home for the Keiyoushi index, installs and manual
 * APK handles. The store index is pulled on first visit (a repository list
 * stays one click away), search filters the loaded list, and installs go
 * through the trust gate.
 */
@Composable
fun ExtensionsScreen(state: AppState) {
    val client = remember { defaultHttpClient() }
    val scope = rememberCoroutineScope()
    val gen = remember { AtomicInteger(0) }
    var url by remember { mutableStateOf(DEFAULT_STORE_INDEX_URL) }
    var apkUrl by remember { mutableStateOf(DEFAULT_APK_URL) }
    var query by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<RepoEntry>?>(null) }
    var index by remember { mutableStateOf<StoreIndex?>(null) }
    var loadedIndexUrl by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val installed by state.extensions.installed.collectAsState()
    val installedByPkg = remember(installed) { installed.associateBy { it.pkg } }
    val language by AppSettings.language.collectAsState()

    suspend fun loadStore(indexUrl: String) {
        val id = gen.incrementAndGet()
        loading = true
        error = null
        val loaded = state.fetchStoreIndex(indexUrl)
        if (id != gen.get()) return
        if (loaded != null) {
            index = loaded
            loadedIndexUrl = indexUrl
            repos = null
        } else {
            error = state.screenError ?: "index load failed"
        }
        loading = false
    }

    suspend fun loadRepos() {
        val id = gen.incrementAndGet()
        val target = url.trim()
        loading = true
        error = null
        val result = runCatching {
            withContext(Dispatchers.IO) { fetchRepos(client, target) }
        }
        if (id != gen.get()) return
        result
            .onSuccess {
                repos = it
                if (it.isEmpty()) error = "no repositories listed at $target"
            }
            .onFailure { error = "repository load error: ${it.message}" }
        loading = false
    }

    fun retry() {
        if (index != null) scope.launch { loadStore(url.trim()) } else scope.launch { loadRepos() }
    }

    // The index is pulled as soon as the screen appears; the kiosk entry can
    // hand over a specific one.
    LaunchedEffect(Unit) {
        val pending = state.pendingStoreUrl
        state.pendingStoreUrl = null
        loadStore(pending ?: url)
    }

    val all = index?.extensions
    val shown =
        remember(all, query, language) {
            val needle = query.trim()
            (all ?: emptyList())
                .filter { ext ->
                    language == null ||
                        ext.sources.any { source -> sourceMatchesLanguage(source.language, language) }
                }
                .filter { ext ->
                    needle.isBlank() ||
                        ext.name.contains(needle, ignoreCase = true) ||
                        ext.packageName.contains(needle, ignoreCase = true) ||
                        ext.sources.any { source ->
                            source.name.contains(needle, ignoreCase = true) ||
                                source.language.contains(needle, ignoreCase = true)
                        }
                }
                .sortedWith { a, b -> a.name.compareToCaseInsensitiveNaturalOrder(b.name) }
        }
    val strays =
        remember(installed, all) {
            val indexed = all?.map { it.packageName }?.toSet().orEmpty()
            if (all == null) emptyList() else installed.filter { it.pkg !in indexed }
        }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenTitle(
                "Extensions",
                when {
                    all == null -> "${installed.size} installed"
                    query.isBlank() && language == null -> "${all.size} available"
                    else -> "${shown.size} of ${all.size}"
                },
            )
            Spacer(Modifier.weight(1f))
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                url,
                { url = it },
                Modifier.weight(1f),
                placeholder = { Text("Store index or repo.json URL") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Button({ scope.launch { loadStore(url.trim()) } }, enabled = !loading && url.isNotBlank()) { Text("Refresh") }
            TextButton(
                { if (repos == null) scope.launch { loadRepos() } else repos = null },
                enabled = !loading,
            ) {
                Text(if (repos == null) "Repositories" else "Hide repositories")
            }
        }
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            placeholder = { Text("Search extensions") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        error?.let { message ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton({ retry() }, enabled = !loading) { Text("Retry") }
            }
        }
        repos?.let { repoList ->
            Text("Repositories (${repoList.size})", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 140.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(repoList, key = { it.url.ifBlank { it.name } }) { repo ->
                    Text(
                        repo.name.ifBlank { repo.url },
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(enabled = repo.url.isNotBlank()) {
                                url = repo.url
                                scope.launch { loadStore(repo.url) }
                            }
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        when {
            all == null && error == null ->
                EmptyNotice("No index loaded yet.") {
                    Button({ scope.launch { loadStore(DEFAULT_STORE_INDEX_URL) } }) { Text("Open the default index") }
                }
            all == null ->
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            else ->
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(shown, key = { it.packageName }) { ext ->
                        StoreExtensionRow(
                            ext = ext,
                            installed = installedByPkg[ext.packageName],
                            language = language,
                            onInstall = {
                                val key = loadedIndexUrl?.let { state.storeKey(it) }?.ifBlank { null }
                                state.installStoreExtension(ext, key)
                            },
                            onUninstall = { state.uninstallExtension(ext.packageName) },
                        )
                    }
                    if (shown.isEmpty() && query.isNotBlank()) {
                        item {
                            Text(
                                "No extensions match \"$query\"",
                                Modifier.padding(vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (strays.isNotEmpty()) {
                        item {
                            Text(
                                "Installed outside this index",
                                Modifier.padding(top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(strays, key = { it.pkg }) { ext ->
                            val names =
                                remember(ext, language) {
                                    val matching =
                                        if (language == null) {
                                            ext.sources
                                        } else {
                                            ext.sources.filter { sourceMatchesLanguage(it.lang, language) }
                                        }
                                    (if (matching.isEmpty()) ext.sources else matching)
                                        .map { it.name }
                                        .distinct()
                                        .joinToString(", ")
                                        .take(200)
                                }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExtensionIcon(
                                    ext.iconUrl?.ifBlank { null },
                                    Modifier.size(40.dp),
                                    fallbackText = ext.pkg.substringAfterLast('.').take(1).uppercase(),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        names.ifBlank { ext.pkg },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "v${ext.versionName} · ${ext.sources.size} sources",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton({ state.uninstallExtension(ext.pkg) }) { Text("Uninstall") }
                            }
                        }
                    }
                }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                apkUrl,
                { apkUrl = it },
                Modifier.weight(1f),
                placeholder = { Text("Install from an APK URL") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            TextButton({ state.loadExtension(apkUrl) }, enabled = apkUrl.isNotBlank()) { Text("Load once") }
            OutlinedButton({ state.installExtension(apkUrl) }, enabled = apkUrl.isNotBlank()) { Text("Install") }
        }
    }
}

/** True when the store offers a newer build than the installed one. */
internal fun updateAvailable(installed: Installed?, ext: NetworkExtensionStore.Extension): Boolean =
    installed != null && ext.versionCode > installed.versionCode

@Composable
private fun StoreExtensionRow(
    ext: NetworkExtensionStore.Extension,
    installed: Installed?,
    language: String?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val matchingSources =
        remember(ext, language) {
            if (language == null) {
                ext.sources
            } else {
                ext.sources.filter { sourceMatchesLanguage(it.language, language) }
            }
        }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(
            ext.resources.iconUrl.ifBlank { null },
            Modifier.size(44.dp),
            fallbackText = ext.name.take(1).uppercase(),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ext.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "v${ext.versionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val sourceNames = matchingSources.map { it.name }.distinct().joinToString(", ").take(200)
            if (sourceNames.isNotBlank()) {
                Text(
                    sourceNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            updateAvailable(installed, ext) -> Button({ onInstall() }) { Text("Update") }
            installed != null -> OutlinedButton({ onUninstall() }) { Text("Uninstall") }
            else -> Button({ onInstall() }) { Text("Install") }
        }
    }
}

/**
 * Trust prompt rendered by [ReaderApp] on top of every screen, so installs
 * always surface the approve/deny choice. The prompt is explicit: no
 * dismiss-on-outside-tap, the user must Approve or Deny.
 */
@Composable
fun TrustDialog(state: AppState) {
    val req = state.trustRequest ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Trust extension?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(req.pkg, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Version code ${req.versionCode}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (req.certHashes.isNotEmpty()) {
                    Text("Certificate:", style = MaterialTheme.typography.bodySmall)
                    req.certHashes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                } else if (req.artifactSha256 != null) {
                    Text(
                        "Jar installs carry no signing certificate; approving trusts exactly these bytes:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(req.artifactSha256, style = MaterialTheme.typography.bodySmall)
                }
                if (req.storeKey.isNotBlank()) {
                    Text("Store key: ${req.storeKey.take(64)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button({ state.answerTrust(true) }) { Text("Approve") } },
        dismissButton = { Button({ state.answerTrust(false) }) { Text("Deny") } },
    )
}
