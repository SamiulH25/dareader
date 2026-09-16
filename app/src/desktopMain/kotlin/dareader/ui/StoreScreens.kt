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
import androidx.compose.foundation.layout.width
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
import dareader.ext.store.NetworkExtensionStore
import dareader.ext.store.RepoEntry
import dareader.ext.store.defaultHttpClient
import dareader.ext.store.fetchRepos
import dareader.ext.store.fetchStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

private const val DEFAULT_REPO_URL =
    "https://raw.githubusercontent.com/keiyoushi/extension-repos/main/repo.json"

@Composable
fun StoreScreen(state: AppState) {
    val client = remember { defaultHttpClient() }
    val scope = rememberCoroutineScope()
    val gen = remember { AtomicInteger(0) }
    var url by remember { mutableStateOf(DEFAULT_REPO_URL) }
    var packageFilter by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<RepoEntry>?>(null) }
    var extensions by remember { mutableStateOf<List<NetworkExtensionStore.Extension>?>(null) }
    var lastIndexUrl by remember { mutableStateOf<String?>(null) }
    var storeKeys by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val installed by state.extensions.installed.collectAsState()
    val installedPkgs = remember(installed) { installed.map { it.pkg }.toSet() }

    fun loadRepos() {
        val id = gen.incrementAndGet()
        val target = url.trim()
        loading = true
        error = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { fetchRepos(client, target) }
            }
            if (id != gen.get()) return@launch
            result
                .onSuccess {
                    repos = it
                    if (it.isEmpty()) error = "no repos listed at $target"
                }
                .onFailure { error = "repo load error: ${it.message}" }
            loading = false
        }
    }

    fun loadStore(indexUrl: String) {
        val id = gen.incrementAndGet()
        loading = true
        error = null
        scope.launch {
            val key = runCatching {
                withContext(Dispatchers.IO) { fetchStore(client, indexUrl).signingKey }
            }.getOrDefault("")
            val list = state.fetchStoreIndex(indexUrl, packageFilter.ifBlank { null })
            if (id != gen.get()) return@launch
            if (list != null) {
                extensions = list
                lastIndexUrl = indexUrl
                if (key.isNotBlank()) storeKeys = storeKeys + (indexUrl to key)
            } else {
                error = state.error ?: "store load failed"
            }
            loading = false
        }
    }

    fun retry() {
        val index = lastIndexUrl
        if (index != null) loadStore(index) else loadRepos()
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Store", extensions?.let { "${it.size} extensions" })
        Text(
            "Repositories list store indexes; an index lists installable extensions. Installs go through the trust gate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            url,
            { url = it },
            Modifier.fillMaxWidth(),
            label = { Text("Repo or store address") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        OutlinedTextField(
            packageFilter,
            { packageFilter = it },
            Modifier.fillMaxWidth(),
            label = { Text("Package filter (optional)") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ loadRepos() }, enabled = !loading) { Text("Load repos") }
            OutlinedButton({ loadStore(url.trim()) }, enabled = !loading) { Text("Load store") }
        }
        if (loading && extensions == null && repos == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
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
        val repoList = repos
        if (repoList != null) {
            Text("Repos (${repoList.size})", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 160.dp),
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
                                loadStore(repo.url)
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
        val list = extensions
        if (list != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extensions (${list.size})", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(12.dp))
                lastIndexUrl?.let {
                    Text(
                        it,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.packageName }) { ext ->
                    StoreExtensionRow(
                        ext = ext,
                        installed = ext.packageName in installedPkgs,
                        onInstall = {
                            val key = lastIndexUrl?.let { storeKeys[it] }?.ifBlank { null }
                            state.installExtension(ext.resources.apkUrl, key)
                        },
                        onUninstall = { state.uninstallExtension(ext.packageName) },
                    )
                }
            }
        } else if (!loading && repos == null && error == null) {
            EmptyNotice("Load a repo list or a store index to browse extensions.") {
                Button({ loadRepos() }) { Text("Load default repos") }
            }
        }
    }
}

@Composable
private fun StoreExtensionRow(
    ext: NetworkExtensionStore.Extension,
    installed: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
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
            Text(ext.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${ext.packageName} · v${ext.versionName} (${ext.versionCode})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ext.sources.isNotEmpty()) {
                Text(
                    ext.sources.joinToString { "${it.name} (${it.language})" }.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (installed) {
            OutlinedButton({ onUninstall() }) { Text("Uninstall") }
        } else {
            Button({ onInstall() }) { Text("Install") }
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
                Text("Certificate:", style = MaterialTheme.typography.bodySmall)
                req.certHashes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (req.storeKey.isNotBlank()) {
                    Text("Store key: ${req.storeKey.take(64)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { Button({ state.answerTrust(true) }) { Text("Approve") } },
        dismissButton = { Button({ state.answerTrust(false) }) { Text("Deny") } },
    )
}
