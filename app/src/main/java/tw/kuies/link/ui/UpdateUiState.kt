package tw.kuies.link.ui

import tw.kuies.link.update.AvailableUpdate
import tw.kuies.link.update.UpdateFailure

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val available: AvailableUpdate? = null,
    val progress: Int? = null,
    val failure: UpdateFailure? = null,
    val apkPath: String? = null,
)

enum class UpdatePhase {
    Idle,
    Checking,
    UpToDate,
    Available,
    Downloading,
    ReadyToInstall,
    NeedsPermission,
    Failed,
}

fun UpdateFailure.userMessage(): String = when (this) {
    UpdateFailure.NETWORK -> "無法連線到更新服務，請確認網路後再試。"
    UpdateFailure.TIMEOUT -> "更新服務回應逾時，請稍後再試。"
    UpdateFailure.RELEASE_NOT_FOUND -> "找不到正式版本資訊。"
    UpdateFailure.NO_STABLE_RELEASE -> "目前沒有可用的正式版本。"
    UpdateFailure.RATE_LIMITED -> "更新服務暫時忙碌，請稍後再試。"
    UpdateFailure.MANIFEST_MISSING -> "更新資訊不完整，已停止更新。"
    UpdateFailure.MANIFEST_INVALID -> "更新資訊格式不正確，已停止更新。"
    UpdateFailure.ASSET_MISSING -> "找不到對應的 APK 檔案。"
    UpdateFailure.ASSET_MISMATCH -> "更新檔案與版本資訊不一致，已停止更新。"
    UpdateFailure.APK_TOO_LARGE -> "更新檔案大小超出限制。"
    UpdateFailure.INCOMPATIBLE -> "這個版本不支援目前的 Android 版本。"
    UpdateFailure.DOWNLOAD_FAILED -> "下載失敗，請稍後再試。"
    UpdateFailure.DOWNLOAD_INTERRUPTED -> "下載中斷，請重試。"
    UpdateFailure.SIZE_MISMATCH -> "下載檔案大小不符，已刪除。"
    UpdateFailure.HASH_MISMATCH -> "APK 校驗碼不符，已刪除下載檔案。"
    UpdateFailure.PACKAGE_MISMATCH -> "APK 套件名稱不符，已停止安裝。"
    UpdateFailure.VERSION_MISMATCH -> "APK 版本與更新資訊不符，已停止安裝。"
    UpdateFailure.SIGNATURE_MISMATCH -> "APK 簽章與目前版本不相容，無法覆蓋安裝。"
    UpdateFailure.INSTALL_PERMISSION_REQUIRED -> "需要允許 KuiesLink 安裝應用程式。"
    UpdateFailure.INSTALL_UNAVAILABLE -> "無法開啟系統安裝程式。"
    UpdateFailure.STORAGE -> "裝置儲存空間不足或無法寫入。"
    UpdateFailure.UNKNOWN -> "更新失敗，請稍後再試。"
}
