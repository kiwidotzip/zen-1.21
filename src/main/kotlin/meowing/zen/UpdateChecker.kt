package meowing.zen

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.UIComponent
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.*
import gg.essential.elementa.constraints.*
import gg.essential.elementa.dsl.*
import meowing.zen.Zen.Companion.mc
import meowing.zen.Zen.Companion.prefix
import meowing.zen.utils.ChatUtils
import meowing.zen.utils.DataUtils
import meowing.zen.utils.NetworkUtils
import meowing.zen.utils.NetworkUtils.createConnection
import meowing.zen.utils.TickUtils
import net.fabricmc.loader.api.FabricLoader
import java.awt.Color
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CompletableFuture

object UpdateChecker {
    private const val current = "1.1.2"
    private var isMessageShown = false
    private var latestVersion: String? = null
    private var githubUrl: String? = null
    private var modrinthUrl: String? = null
    private var githubDownloadUrl: String? = null
    private var modrinthDownloadUrl: String? = null

    private val settingsData = DataUtils("update_settings", UpdateSettings())
    private val dontShowForVersion: String? get() = settingsData.getData().dontShowForVersion

    data class UpdateSettings(val dontShowForVersion: String? = null)
    data class GitHubRelease(val tag_name: String, val html_url: String, val prerelease: Boolean, val assets: List<GitHubAsset>)
    data class GitHubAsset(val name: String, val browser_download_url: String)
    data class ModrinthVersion(val id: String, val version_number: String, val date_published: String, val game_versions: List<String>, val loaders: List<String>, val status: String, val version_type: String, val files: List<ModrinthFile>)
    data class ModrinthFile(val url: String, val filename: String, val primary: Boolean)

    fun checkForUpdates() {
        CompletableFuture.supplyAsync {
            val github = checkGitHub()
            val modrinth = checkModrinth()
            val latest = listOfNotNull(github?.first, modrinth?.first).maxByOrNull { compareVersions(it, current) } ?: return@supplyAsync

            if (compareVersions(latest, current) > 0 && latest != dontShowForVersion) {
                isMessageShown = true
                latestVersion = latest
                githubUrl = github?.second
                githubDownloadUrl = github?.third
                modrinthUrl = modrinth?.second
                modrinthDownloadUrl = modrinth?.third
                TickUtils.schedule(2) { mc.setScreen(UpdateGUI()) }
            }
        }
    }

    private fun checkGitHub(): Triple<String, String, String?>? = runCatching {
        val connection = createConnection("https://api.github.com/repos/kiwidotzip/zen-1.21/releases") as HttpURLConnection
        connection.requestMethod = "GET"

        if (connection.responseCode == 200) {
            val releases: List<GitHubRelease> = Gson().fromJson(
                connection.inputStream.reader(),
                object : TypeToken<List<GitHubRelease>>() {}.type
            )

            releases.firstOrNull { !it.prerelease }?.let { release ->
                val downloadUrl = release.assets.firstOrNull { it.name.endsWith(".jar") }?.browser_download_url
                Triple(release.tag_name.replace("v", ""), release.html_url, downloadUrl)
            }
        } else null
    }.getOrNull()

    private fun checkModrinth(): Triple<String, String, String?>? = runCatching {
        val connection = createConnection("https://api.modrinth.com/v2/project/zenmod/version") as HttpURLConnection
        connection.requestMethod = "GET"

        if (connection.responseCode == 200) {
            val versions: List<ModrinthVersion> = Gson().fromJson(
                connection.inputStream.reader(),
                object : TypeToken<List<ModrinthVersion>>() {}.type
            )

            versions.filter {
                it.loaders.contains("fabric") && it.status == "listed" && it.version_type == "release" && it.game_versions.contains("1.21.5")
            }.maxByOrNull { it.date_published }?.let { version ->
                val primaryFile = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
                primaryFile?.let {
                    Triple(version.version_number, "https://modrinth.com/mod/zenmod/version/${version.id}", it.url)
                }
            }
        } else null
    }.getOrNull()

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    fun getCurrentVersion() = current
    fun getLatestVersion() = latestVersion
    fun getGithubUrl() = githubUrl
    fun getModrinthUrl() = modrinthUrl
    fun getGithubDownloadUrl() = githubDownloadUrl
    fun getModrinthDownloadUrl() = modrinthDownloadUrl
    fun setDontShowForVersion(version: String) = settingsData.setData(UpdateSettings(version))
}

class UpdateGUI : WindowScreen(ElementaVersion.V10) {
    private val colors = mapOf(
        "bg" to Color(8, 12, 16, 230),
        "element" to Color(12, 16, 20, 255),
        "elementHover" to Color(18, 22, 26, 255),
        "accent" to Color(100, 245, 255, 255),
        "text" to Color(220, 223, 228, 255),
        "textSecondary" to Color(156, 163, 175, 255),
        "closeHover" to Color(220, 38, 38, 255),
        "success" to Color(34, 197, 94),
        "successHover" to Color(22, 163, 74),
        "progressBg" to Color(31, 41, 55, 255),
        "progressFill" to Color(59, 130, 246, 255)
    )

    private var isDownloading = false
    private var downloadButton: UIComponent? = null
    private var downloadButtonIcon: UIComponent? = null
    private var downloadButtonText: UIText? = null
    private var progressBar: UIComponent? = null
    private var progressFill: UIComponent? = null
    private var progressText: UIText? = null

    init { buildGui() }

    private fun buildGui() {
        val mainContainer = UIRoundedRectangle(8f).apply {
            setX(CenterConstraint())
            setY(CenterConstraint())
            setWidth(40.percent())
            setHeight(55.percent())
            setColor(colors["bg"]!!)
        } childOf window

        UIText("Zen | Update Available").apply {
            setX(CenterConstraint())
            setY(5.88.percent())
            setColor(colors["accent"]!!)
            setTextScale(1.5f.pixels())
        } childOf mainContainer

        createCloseButton() childOf mainContainer

        val versionContainer = UIContainer().apply {
            setX(CenterConstraint())
            setY(17.65.percent())
            setWidth(80.percent())
            setHeight(23.53.percent())
        } childOf mainContainer

        UIText("Current Version").apply {
            setX(0.percent())
            setY(0.percent())
            setColor(colors["textSecondary"]!!)
            setTextScale(0.9f.pixels())
        } childOf versionContainer

        UIText("v${UpdateChecker.getCurrentVersion()}").apply {
            setX(0.percent())
            setY(18.75.percent())
            setColor(Color(248, 113, 113))
            setTextScale(1.2f.pixels())
        } childOf versionContainer

        UIText("Latest Version").apply {
            setX(0.percent())
            setY(56.25.percent())
            setColor(colors["textSecondary"]!!)
            setTextScale(0.9f.pixels())
        } childOf versionContainer

        UIText("v${UpdateChecker.getLatestVersion()}").apply {
            setX(0.percent())
            setY(75.percent())
            setColor(Color(34, 197, 94))
            setTextScale(1.2f.pixels())
        } childOf versionContainer

        val buttonContainer = UIContainer().apply {
            setX(CenterConstraint())
            setY(47.06.percent())
            setWidth(80.percent())
            setHeight(35.29.percent())
        } childOf mainContainer

        val downloadUrl = UpdateChecker.getModrinthDownloadUrl() ?: UpdateChecker.getGithubDownloadUrl()
        downloadUrl?.let { url ->
            downloadButton = createDirectDownloadButton { downloadMod(url) }.apply {
                setX(0.percent())
                setY(0.percent())
                setWidth(100.percent())
                setHeight(29.17.percent())
            } childOf buttonContainer

            createProgressBar().apply {
                setX(0.percent())
                setY(45.83.percent())
                setWidth(100.percent())
                setHeight(5.percent())
            } childOf buttonContainer
        }

        val webButtonContainer = UIContainer().apply {
            setX(0.percent())
            setY(if (downloadUrl != null) 62.5.percent() else 37.5.percent())
            setWidth(100.percent())
            setHeight(29.17.percent())
        } childOf buttonContainer

        UpdateChecker.getModrinthUrl()?.let { url ->
            createDownloadButtonWithIcon("Modrinth", "modrinth") { openUrl(url) }.apply {
                setX(0.percent())
                setY(0.percent())
                setWidth(if (UpdateChecker.getGithubUrl() != null) 48.percent() else 100.percent())
                setHeight(100.percent())
            } childOf webButtonContainer
        }

        UpdateChecker.getGithubUrl()?.let { url ->
            createDownloadButtonWithIcon("GitHub", "github") { openUrl(url) }.apply {
                setX(if (UpdateChecker.getModrinthUrl() != null) 52.percent() else 0.percent())
                setY(0.percent())
                setWidth(if (UpdateChecker.getModrinthUrl() != null) 48.percent() else 100.percent())
                setHeight(100.percent())
            } childOf webButtonContainer
        }

        val bottomButtonContainer = UIContainer().apply {
            setX(CenterConstraint())
            setY(85.29.percent())
            setWidth(60.percent())
            setHeight(8.82.percent())
        } childOf mainContainer

        createButton("Don't Show Again", colors["element"]!!, colors["elementHover"]!!) {
            UpdateChecker.setDontShowForVersion(UpdateChecker.getLatestVersion() ?: "")
            mc.setScreen(null)
        }.apply {
            setX(0.percent())
            setY(0.percent())
            setWidth(48.percent())
            setHeight(100.percent())
        } childOf bottomButtonContainer

        createButton("Later", colors["element"]!!, colors["elementHover"]!!) {
            mc.setScreen(null)
        }.apply {
            setX(52.percent())
            setY(0.percent())
            setWidth(48.percent())
            setHeight(100.percent())
        } childOf bottomButtonContainer
    }

    private fun createProgressBar(): UIComponent {
        val container = UIContainer()

        progressBar = UIRoundedRectangle(3f).apply {
            setX(0.percent())
            setY(0.percent())
            setWidth(100.percent())
            setHeight(100.percent())
            setColor(colors["progressBg"]!!)
        } childOf container

        progressFill = UIRoundedRectangle(3f).apply {
            setX(0.percent())
            setY(0.percent())
            setWidth(0.percent())
            setHeight(100.percent())
            setColor(colors["progressFill"]!!)
        } childOf progressBar!!

        progressText = UIText("download the update :3").apply {
            setX(CenterConstraint())
            setY(55.percent())
            setColor(colors["textSecondary"]!!)
            setTextScale(0.8f.pixels())
        } childOf window

        container.hide()
        return container
    }

    private fun updateProgress(progress: Int, downloaded: Long, total: Long) {
        progressBar?.parent?.unhide()
        progressFill?.setWidth(progress.percent())
        progressText?.setText("$progress% • ${formatBytes(downloaded)} / ${formatBytes(total)}")
        if (progress == 100) {
            progressBar?.hide()
            progressFill?.hide()
            progressText?.setText("yippee :3")
        }
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.1f KB".format(kb)
            else -> "$bytes B"
        }
    }

    private fun createDirectDownloadButton(onClick: () -> Unit): UIComponent {
        val button = UIRoundedRectangle(6f).apply {
            setColor(colors["success"]!!)
            onMouseEnter { if (!isDownloading) setColor(colors["successHover"]!!) }
            onMouseLeave { if (!isDownloading) setColor(colors["success"]!!) }
            onMouseClick { if (!isDownloading) onClick() }
        }

        val iconContainer = UIContainer().apply {
            setX(CenterConstraint())
            setY(CenterConstraint())
            setWidth(ChildBasedSizeConstraint() + 4.pixels())
            setHeight(100.percent())
        } childOf button

        downloadButtonIcon = try {
            UIImage.ofResource("/assets/zen/logos/download.png").apply {
                setX(0.percent())
                setY(CenterConstraint())
                setWidth(16.pixels())
                setHeight(16.pixels())
            }
        } catch (_: Exception) {
            UIText("⬇").apply {
                setX(0.percent())
                setY(CenterConstraint())
                setColor(Color.WHITE)
                setTextScale(1.0f.pixels())
            }
        } childOf iconContainer

        downloadButtonText = UIText("Download & Install").apply {
            setX(20.pixels())
            setY(CenterConstraint())
            setColor(Color.WHITE)
            setTextScale(0.9f.pixels())
        } childOf iconContainer

        return button
    }

    private fun createCloseButton(): UIComponent {
        return UIRoundedRectangle(4f).apply {
            setX(92.5.percent())
            setY(2.94.percent())
            setWidth(5.percent())
            setHeight(5.88.percent())
            setColor(colors["element"]!!)
            onMouseEnter { setColor(colors["closeHover"]!!) }
            onMouseLeave { setColor(colors["element"]!!) }
            onMouseClick { mc.setScreen(null) }

            UIText("✕").apply {
                setX(CenterConstraint())
                setY(CenterConstraint())
                setColor(Color.WHITE)
                setTextScale(0.8f.pixels())
            } childOf this
        }
    }

    private fun createDownloadButtonWithIcon(text: String, platform: String, onClick: () -> Unit): UIComponent {
        return UIRoundedRectangle(6f).apply {
            setColor(Color(55, 65, 81))
            onMouseEnter { setColor(Color(75, 85, 99)) }
            onMouseLeave { setColor(Color(55, 65, 81)) }
            onMouseClick { onClick() }

            val iconContainer = UIContainer().apply {
                setX(CenterConstraint())
                setY(CenterConstraint())
                setWidth(ChildBasedSizeConstraint() + 4.pixels())
                setHeight(100.percent())
            } childOf this

            try {
                val iconUrl = when(platform) {
                    "modrinth" -> "/assets/zen/logos/modrinth.png"
                    "github" -> "/assets/zen/logos/github.png"
                    else -> null
                }

                iconUrl?.let { url ->
                    UIImage.ofResource(url).apply {
                        setX(0.percent())
                        setY(CenterConstraint())
                        setWidth(16.pixels())
                        setHeight(16.pixels())
                    } childOf iconContainer
                }
            } catch (_: Exception) {
                UIText(if (platform == "modrinth") "●" else "◐").apply {
                    setX(0.percent())
                    setY(CenterConstraint())
                    setColor(Color.WHITE)
                    setTextScale(1.0f.pixels())
                } childOf iconContainer
            }

            UIText(text).apply {
                setX(20.pixels())
                setY(CenterConstraint())
                setColor(Color.WHITE)
                setTextScale(0.9f.pixels())
            } childOf iconContainer
        }
    }

    private fun createButton(text: String, normalColor: Color, hoverColor: Color, onClick: () -> Unit): UIComponent {
        return UIRoundedRectangle(6f).apply {
            setColor(normalColor)
            onMouseEnter { setColor(hoverColor) }
            onMouseLeave { setColor(normalColor) }
            onMouseClick { onClick() }

            UIText(text).apply {
                setX(CenterConstraint())
                setY(CenterConstraint())
                setColor(colors["text"]!!)
                setTextScale(0.9f.pixels())
            } childOf this
        }
    }

    private fun downloadMod(downloadUrl: String) {
        if (isDownloading) return
        isDownloading = true

        downloadButton?.setColor(colors["element"]!!)
        downloadButtonText?.setText("Preparing...")
        if (downloadButtonIcon is UIText) (downloadButtonIcon as UIText).setText("...")

        val modsDir = FabricLoader.getInstance().gameDir.resolve("mods").toFile()
        if (!modsDir.exists()) modsDir.mkdirs()

        val oldFileName = "zen-1.21.5-fabric-${UpdateChecker.getCurrentVersion()}.jar"
        val oldFile = File(modsDir, oldFileName)
        if (oldFile.exists()) oldFile.delete()

        val fileName = "zen-1.21.5-fabric-${UpdateChecker.getLatestVersion()}.jar"
        val outputFile = File(modsDir, fileName)

        NetworkUtils.downloadFile(
            url = downloadUrl,
            outputFile = outputFile,
            headers = mapOf("User-Agent" to "Zen"),
            onProgress = { downloaded, contentLength ->
                if (contentLength > 0) {
                    val progress = ((downloaded * 100) / contentLength).toInt()
                    updateProgress(progress, downloaded, contentLength)
                }
            },
            onComplete = { file ->
                TickUtils.schedule(1) {
                    downloadButtonText?.setText("Downloaded!")
                    if (downloadButtonIcon is UIText) (downloadButtonIcon as UIText).setText("✓")
                    downloadButton?.setColor(colors["success"]!!)
                    progressBar?.parent?.hide(true)

                    TickUtils.schedule(40) {
                        ChatUtils.addMessage("$prefix §aUpdate downloaded! New version will be loaded when it restarts.")
                        mc.setScreen(null)
                    }
                }
                isDownloading = false
            },
            onError = { exception ->
                TickUtils.schedule(1) {
                    downloadButtonText?.setText("Error")
                    if (downloadButtonIcon is UIText) (downloadButtonIcon as UIText).setText("✗")
                    downloadButton?.setColor(Color(220, 38, 38))
                    progressBar?.parent?.hide(true)
                    ChatUtils.addMessage("$prefix §cDownload error: ${exception.message}")

                    TickUtils.schedule(60) {
                        resetDownloadButton()
                    }
                }
                isDownloading = false
            }
        ).also {
            TickUtils.schedule(1) {
                downloadButtonText?.setText("Downloading...")
            }
        }
    }

    private fun resetDownloadButton() {
        isDownloading = false
        downloadButtonText?.setText("Download & Install")
        if (downloadButtonIcon is UIText) (downloadButtonIcon as UIText).setText("⬇")
        downloadButton?.setColor(colors["success"]!!)
        progressFill?.setWidth(0.percent())
        progressText?.setText("")
    }

    private fun openUrl(url: String) {
        try {
            Desktop.getDesktop().browse(URI(url))
            mc.setScreen(null)
        } catch (_: Exception) {
            mc.setScreen(null)
        }
    }
}