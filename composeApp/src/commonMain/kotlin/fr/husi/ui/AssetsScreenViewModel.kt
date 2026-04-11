package fr.husi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.RuleProvider
import fr.husi.database.AssetEntity
import fr.husi.database.DataStore
import fr.husi.database.SagerDatabase
import fr.husi.ktx.Logs
import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.readableMessage
import fr.husi.ktx.runOnDefaultDispatcher
import fr.husi.ktx.runOnIoDispatcher
import fr.husi.libcore.CopyCallback
import fr.husi.libcore.HTTPRequest
import fr.husi.libcore.Libcore
import fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import fr.husi.ktx.kxs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import fr.husi.resources.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

internal typealias UpdateProgress = (Float) -> Unit

@OptIn(FormatStringsInDatetimeFormats::class)
private val assetVersionFormat = LocalDateTime.Format {
    byUnicodePattern("yyyyMMddHHmmssSSS")
}

@Immutable
internal data class AssetsUiState(
    val process: Float? = null,
    val assets: List<AssetItem> = emptyList(),
    val pendingDeleteCount: Int = 0,
)

@Immutable
internal data class AssetItem(
    val file: File,
    val version: String,
    val builtIn: Boolean,
    val progress: Float? = null,
)

@Immutable
internal sealed interface AssetsScreenUiEvent {
    class Snackbar(val message: StringOrRes) : AssetsScreenUiEvent
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String = "",
)

internal data class GithubRepository(
    val author: String,
    val name: String,
    val branch: String = "rule-set",
    val unstableBranch: String? = null,
) {
    val fullName: String
        get() = "$author/$name"

    fun resolveBranch(useUnstableBranch: Boolean): String {
        return if (useUnstableBranch && unstableBranch != null) {
            unstableBranch
        } else {
            branch
        }
    }
}

internal data class GithubAssetSource(
    val repository: GithubRepository,
    val versionFile: File,
)

internal fun buildGithubAssetSources(provider: Int, versionFiles: List<File>): List<GithubAssetSource> {
    return when (provider) {
        RuleProvider.OFFICIAL -> listOf(
            GithubAssetSource(
                repository = GithubRepository(
                    author = "SagerNet",
                    name = "sing-geoip",
                ),
                versionFile = versionFiles[0],
            ),
            GithubAssetSource(
                repository = GithubRepository(
                    author = "SagerNet",
                    name = "sing-geosite",
                    unstableBranch = "rule-set-unstable",
                ),
                versionFile = versionFiles[1],
            ),
        )

        RuleProvider.LOYALSOLDIER -> listOf(
            GithubAssetSource(
                repository = GithubRepository(
                    author = "1715173329",
                    name = "sing-geoip",
                ),
                versionFile = versionFiles[0],
            ),
            GithubAssetSource(
                repository = GithubRepository(
                    author = "1715173329",
                    name = "sing-geosite",
                    unstableBranch = "rule-set-unstable",
                ),
                versionFile = versionFiles[1],
            ),
        )

        RuleProvider.CHOCOLATE4U -> listOf(
            GithubAssetSource(
                repository = GithubRepository(
                    author = "Chocolate4U",
                    name = "Iran-sing-box-rules",
                ),
                versionFile = versionFiles[0],
            ),
        )

        else -> throw IllegalStateException("Unknown provider $provider")
    }
}

@Stable
internal class AssetsScreenViewModel(
    assetsDir: File,
    geoDir: File,
) : ViewModel() {

    companion object {
        fun isBuiltIn(index: Int): Boolean = index < 2
    }

    private val _uiState = MutableStateFlow(AssetsUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<AssetsScreenUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private lateinit var assetsDir: File
    private lateinit var geoDir: File

    private var previousAssetNames = emptySet<String>()
    private var initializedFor: Pair<String, String>? = null
    private var assetsObserveJob: Job? = null

    private var deleteTimer: Job? = null
    private val hiddenAssetsAccess = Mutex()
    private val hiddenAssets = mutableSetOf<String>()

    init {
        initialize(assetsDir, geoDir)
    }

    fun initialize(assetsDir: File, geoDir: File) {
        val args = assetsDir.absolutePath to geoDir.absolutePath
        if (initializedFor == args && assetsObserveJob?.isActive == true) return
        initializedFor = args
        assetsObserveJob?.cancel()
        this.assetsDir = assetsDir
        this.geoDir = geoDir

        assetsObserveJob = viewModelScope.launch {
            SagerDatabase.assetDao.getAll().collectLatest { assets ->
                val currentNames = assets.map { it.name }.toSet()
                val newAssets = currentNames - previousAssetNames

                newAssets.forEach { name ->
                    updateSingleAsset(File(geoDir, name))
                }

                previousAssetNames = currentNames
                refreshAssets0(assets)
            }
        }
    }

    fun refreshAssets() = viewModelScope.launch {
        val assets = SagerDatabase.assetDao.getAll().first()
        refreshAssets0(assets)
    }

    private suspend fun refreshAssets0(dbAssets: List<AssetEntity>) {
        val files = buildList {
            add(File(assetsDir, "geoip.version.txt"))
            add(File(assetsDir, "geosite.version.txt"))
            dbAssets.forEach { add(File(geoDir, it.name)) }
        }

        hiddenAssetsAccess.withLock {
            _uiState.update { state ->
                state.copy(
                    assets = files.mapIndexed { i, asset ->
                        buildAssetItem(i, asset)
                    }.filterNot { hiddenAssets.contains(it.file.name) },
                    pendingDeleteCount = hiddenAssets.size,
                    process = null,
                )
            }
        }
    }

    private fun buildAssetItem(index: Int, file: File): AssetItem {
        val isVersionName = file.name.endsWith(".version.txt")
        val versionFile = if (isVersionName) {
            file
        } else {
            File(assetsDir, "${file.name}.version.txt")
        }
        val version = runCatching {
            if (versionFile.isFile) {
                versionFile.readText().trim()
            } else {
                versionFile.writeText("Unknown")
                null
            }
        }.getOrNull().blankAsNull() ?: "Unknown"
        return AssetItem(
            file = file,
            version = version,
            builtIn = isBuiltIn(index),
            progress = null,
        )
    }

    suspend fun deleteAssets(files: List<File>) {
        for (file in files) {
            file.delete()
            val versionFile = File(assetsDir, "${file.name}.version.txt")
            if (versionFile.isFile) versionFile.delete()
            SagerDatabase.assetDao.delete(file.name)
        }
    }

    fun updateAsset(destinationDir: File, cacheDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateAsset0(destinationDir, cacheDir)
            } catch (_: NoUpdateException) {
                _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Res(Res.string.route_asset_no_update)))
                return@launch
            } catch (e: Exception) {
                Logs.e(e)
                _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
            }
            val assets = SagerDatabase.assetDao.getAll().first()
            refreshAssets0(assets)
        }
    }

    fun resetRuleSet() = viewModelScope.launch(Dispatchers.IO) {
        if (DataStore.rulesProvider != RuleProvider.OFFICIAL) return@launch
        _uiState.update { it.copy(process = 0f) }
        try {
            copyBundledRuleSetAssetsIfNeeded()
            File(assetsDir, "geoip.version.txt").delete()
            File(assetsDir, "geosite.version.txt").delete()
            Libcore.extractAssets()
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
        }
        val assets = SagerDatabase.assetDao.getAll().first()
        refreshAssets0(assets)
    }

    private suspend fun updateAsset0(destinationDir: File, cacheDir: File) {
        _uiState.update { it.copy(process = 0f) }

        var process = 0f
        val updateProgress: UpdateProgress = { p ->
            process += p
            _uiState.update { it.copy(process = process) }
        }

        val assetsDir = destinationDir.parentFile!!
        val versionFiles = listOf(
            File(assetsDir, "geoip.version.txt"),
            File(assetsDir, "geosite.version.txt"),
        )
        val provider = DataStore.rulesProvider
        val updater = if (provider == RuleProvider.CUSTOM) {
            CustomAssetUpdater(
                versionFiles,
                updateProgress,
                cacheDir,
                destinationDir,
                DataStore.customRuleProvider.lines(),
            )
        } else {
            GithubAssetUpdater(
                versionFiles,
                updateProgress,
                cacheDir,
                destinationDir,
                buildGithubAssetSources(provider, versionFiles),
                RuleProvider.hasUnstableBranch(provider),
            )
        }

        updater.runUpdateIfAvailable()
    }

    fun updateSingleAsset(asset: File) = viewModelScope.launch(Dispatchers.IO) {
        try {
            updateSingleAsset0(asset)
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(AssetsScreenUiEvent.Snackbar(StringOrRes.Direct(e.readableMessage)))
        }
        val assets = SagerDatabase.assetDao.getAll().first()
        refreshAssets0(assets)
    }

    private suspend fun updateSingleAsset0(asset: File) {
        val name = asset.name
        val entity = SagerDatabase.assetDao.get(name)!!
        val url = entity.url

        _uiState.update { state ->
            state.copy(
                assets = state.assets.map {
                    if (it.file == asset) {
                        it.copy(
                            progress = 0f,
                        )
                    } else {
                        it
                    }
                },
            )
        }

        Libcore.newHttpClient().apply {
            keepAlive()
            if (DataStore.serviceState.started) {
                useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
            }
        }.newRequest().apply {
            setURL(url)
            setUserAgent(USER_AGENT)
        }.execute()
            .writeTo(
                File(geoDir, name).absolutePath,
                object : CopyCallback {
                    var saved: Double = 0.0
                    var length: Double = 0.0
                    override fun setLength(length: Long) {
                        this.length = length.toDouble()
                    }

                    override fun update(n: Long) {
                        if (length <= 0) return
                        saved += n.toDouble()
                        val progress = ((saved / length) * 100).toFloat()
                        _uiState.update { state ->
                            state.copy(
                                assets = state.assets.map {
                                    if (it.file == asset) {
                                        it.copy(
                                            progress = progress,
                                        )
                                    } else {
                                        it
                                    }
                                },
                            )
                        }
                    }

                },
            )

        val time = assetVersionFormat.format(
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        )
        File(assetsDir, "$name.version.txt").writeText(time)
    }

    fun undoableRemove(fileName: String) = viewModelScope.launch {
        hiddenAssetsAccess.withLock {
            _uiState.update { state ->
                val assets = state.assets.toMutableList()
                val assetIndex = assets.indexOfFirst { it.file.name == fileName }
                if (assetIndex >= 0) {
                    val asset = assets.removeAt(assetIndex)
                    hiddenAssets.add(asset.file.name)
                }
                state.copy(
                    assets = assets,
                    pendingDeleteCount = hiddenAssets.size,
                )
            }
        }
        startDeleteTimer()
    }

    private fun startDeleteTimer() {
        deleteTimer?.cancel()
        deleteTimer = viewModelScope.launch {
            delay(5000)
            commit()
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenAssetsAccess.withLock {
            hiddenAssets.clear()
        }
        refreshAssets()
    }

    fun commit() = runOnDefaultDispatcher {
        deleteTimer?.cancel()
        deleteTimer = null
        val toDelete = hiddenAssetsAccess.withLock {
            val toDelete = hiddenAssets.toList()
            hiddenAssets.clear()
            toDelete
        }
        runOnIoDispatcher {
            for (fileName in toDelete) {
                val file = if (fileName.endsWith(".version.txt")) {
                    File(assetsDir, fileName)
                } else {
                    File(geoDir, fileName)
                }
                file.delete()
                if (!fileName.endsWith(".version.txt")) {
                    val versionFile = File(assetsDir, "$fileName.version.txt")
                    if (versionFile.isFile) versionFile.delete()
                    SagerDatabase.assetDao.delete(fileName)
                }
            }
        }
    }

}

private class NoUpdateException : Exception()

internal sealed class UpdateInfo {
    data class Github(val source: GithubAssetSource, val newVersion: String) : UpdateInfo()
    data class Custom(val link: String) : UpdateInfo()
}

internal abstract class AssetsUpdater(
    val versionFiles: List<File>,
    val updateProgress: UpdateProgress,
    val cacheDir: File,
    val destinationDir: File,
) {
    private val httpClient = Libcore.newHttpClient().apply {
        keepAlive()
        if (DataStore.serviceState.started) {
            useSocks5(DataStore.mixedPort, DataStore.inboundUsername, DataStore.inboundPassword)
        }
    }

    fun newRequest(url: String): HTTPRequest = httpClient.newRequest().apply {
        setURL(url)
        setUserAgent(USER_AGENT)
    }

    suspend fun runUpdateIfAvailable() {
        val updatesToPerform = check()

        if (updatesToPerform.isNotEmpty()) {
            performUpdate(updatesToPerform)
        } else {
            throw NoUpdateException()
        }
    }

    protected abstract suspend fun check(): List<UpdateInfo>

    protected abstract suspend fun performUpdate(updates: List<UpdateInfo>)
}

internal class CustomAssetUpdater(
    versionFiles: List<File>,
    updateProgress: UpdateProgress,
    cacheDir: File,
    destinationDir: File,
    val links: List<String>,
) : AssetsUpdater(versionFiles, updateProgress, cacheDir, destinationDir) {

    override suspend fun check(): List<UpdateInfo> = links.map { link ->
        UpdateInfo.Custom(link)
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val cacheFiles = ArrayList<File>(updates.size)

        try {
            updateProgress(35f)
            for ((i, update) in updates.withIndex()) {
                update as UpdateInfo.Custom
                val response = newRequest(update.link).execute()

                val cacheFile = File(cacheDir, "custom_asset_$i.tmp")
                cacheFile.parentFile?.mkdirs()
                cacheFile.deleteOnExit()

                response.writeTo(cacheFile.absolutePath, null)
                cacheFiles.add(cacheFile)
            }

            updateProgress(25f)
            for (file in cacheFiles) {
                Libcore.tryUnpack(file.absolutePath, destinationDir.absolutePath)
            }

            updateProgress(25f)
            for (version in versionFiles) {
                version.writeText("custom")
            }
            updateProgress(15f)
        } finally {
            for (file in cacheFiles) {
                file.runCatching { delete() }
            }
        }
    }
}

internal class GithubAssetUpdater(
    versionFiles: List<File>,
    updateProgress: UpdateProgress,
    parent: File,
    toDir: File,
    val sources: List<GithubAssetSource>,
    val useUnstableBranch: Boolean,
) : AssetsUpdater(versionFiles, updateProgress, parent, toDir) {

    override suspend fun check(): List<UpdateInfo> {
        val updatesNeeded = mutableListOf<UpdateInfo.Github>()

        for (source in sources) {
            val latestVersion = fetchVersion(source.repository)
            val currentVersion = source.versionFile.readText()

            if (latestVersion.isNotEmpty() && latestVersion != currentVersion) {
                updatesNeeded.add(UpdateInfo.Github(source, latestVersion))
                updateProgress(5f)
            }
        }
        return updatesNeeded
    }

    override suspend fun performUpdate(updates: List<UpdateInfo>) {
        val cacheFiles = ArrayList<File>(updates.size)
        val progressTotalDownload = 60f
        val progressTotalUnpack = 25f

        try {
            val progressPerDownload = progressTotalDownload / updates.size
            for (update in updates) {
                update as UpdateInfo.Github
                // https://codeload.github.com/SagerNet/sing-geosite/tar.gz/refs/heads/rule-set
                val source = update.source
                val branchName = source.repository.resolveBranch(useUnstableBranch)
                val url =
                    "https://codeload.github.com/${source.repository.fullName}/tar.gz/refs/heads/${branchName}"
                val response = newRequest(url).execute()

                val cacheFile = File(
                    cacheDir,
                    "${source.repository.fullName.replace('/', '_')}-${update.newVersion}.tmp",
                )
                cacheFile.parentFile?.mkdirs()
                cacheFile.deleteOnExit()

                response.writeTo(cacheFile.absolutePath, null)
                cacheFiles.add(cacheFile)

                updateProgress(progressPerDownload)
            }

            val progressPerUnpack = progressTotalUnpack / cacheFiles.size
            for (file in cacheFiles) {
                Libcore.untargzWithoutDir(file.absolutePath, destinationDir.absolutePath)
                updateProgress(progressPerUnpack)
            }

            if (sources.size == 1) {
                // Chocolate4U
                val newVersion = (updates.firstOrNull() as? UpdateInfo.Github)?.newVersion ?: return
                versionFiles.forEach { it.writeText(newVersion) }
            } else {
                for (update in updates) {
                    update as UpdateInfo.Github
                    update.source.versionFile.writeText(update.newVersion)
                }
            }
        } finally {
            for (file in cacheFiles) {
                file.runCatching { delete() }
            }
        }
    }

    private fun fetchVersion(repository: GithubRepository): String {
        val response =
            newRequest("https://api.github.com/repos/${repository.fullName}/releases/latest").execute()
        return kxs.decodeFromString<GithubRelease>(response.contentString).tagName
    }
}
