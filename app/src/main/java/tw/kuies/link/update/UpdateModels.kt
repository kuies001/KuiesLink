package tw.kuies.link.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A validated update candidate. The APK URL always comes from the GitHub release asset list. */
data class AvailableUpdate(
    val tagName: String,
    val versionCode: Long,
    val versionName: String,
    val changelog: String,
    val apkAssetName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val sha256: String,
)

enum class UpdateFailure {
    NETWORK,
    TIMEOUT,
    RELEASE_NOT_FOUND,
    NO_STABLE_RELEASE,
    RATE_LIMITED,
    MANIFEST_MISSING,
    MANIFEST_INVALID,
    ASSET_MISSING,
    ASSET_MISMATCH,
    APK_TOO_LARGE,
    INCOMPATIBLE,
    DOWNLOAD_FAILED,
    DOWNLOAD_INTERRUPTED,
    SIZE_MISMATCH,
    HASH_MISMATCH,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    SIGNATURE_MISMATCH,
    INSTALL_PERMISSION_REQUIRED,
    INSTALL_UNAVAILABLE,
    STORAGE,
    UNKNOWN,
}

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val update: AvailableUpdate) : UpdateCheck
    data class Failed(val failure: UpdateFailure) : UpdateCheck
}

sealed interface PrepareResult {
    data class Prepared(val apk: java.io.File) : PrepareResult
    data class Failed(val failure: UpdateFailure) : PrepareResult
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val failure: UpdateFailure) : ApiResult<Nothing>
}

data class GitHubRelease(
    val tagName: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubAsset>,
)

data class GitHubAsset(
    val name: String,
    val size: Long,
    val downloadUrl: String,
)

data class UpdateManifest(
    val schema: Int,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val apkAsset: String,
    val sha256: String,
    val sizeBytes: Long,
    val minSdk: Int,
    val changelog: String,
)

/** Manifest/release parsing kept out of the network layer so tests can feed raw JSON directly. */
object UpdateJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseRelease(text: String): GitHubRelease? = runCatching {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return null
        val tag = root.string("tag_name") ?: return null
        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            GitHubAsset(
                name = asset.string("name") ?: return@mapNotNull null,
                size = asset.long("size") ?: -1L,
                downloadUrl = asset.string("browser_download_url") ?: return@mapNotNull null,
            )
        }
        GitHubRelease(
            tagName = tag,
            draft = root.boolean("draft") ?: false,
            prerelease = root.boolean("prerelease") ?: false,
            assets = assets,
        )
    }.getOrNull()

    fun parseManifest(text: String): UpdateManifest? = runCatching {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return null
        UpdateManifest(
            schema = root.int("schema") ?: return null,
            packageName = root.string("packageName") ?: return null,
            versionCode = root.long("versionCode") ?: return null,
            versionName = root.string("versionName") ?: return null,
            apkAsset = root.string("apkAsset") ?: return null,
            sha256 = root.string("sha256") ?: return null,
            sizeBytes = root.long("sizeBytes") ?: return null,
            minSdk = root.int("minSdk") ?: return null,
            changelog = root.string("changelog").orEmpty(),
        )
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull
}
