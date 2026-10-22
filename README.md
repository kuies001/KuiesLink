# KuiesLink

## 下載

**[下載最新版 APK](https://github.com/kuies001/KuiesLink/releases/latest)** ・ [v0.3.3 直接下載](https://github.com/kuies001/KuiesLink/releases/download/v0.3.3/KuiesLink-v0.3.3.apk) ・ [SHA-256 檢查碼](https://github.com/kuies001/KuiesLink/releases/download/v0.3.3/KuiesLink-v0.3.3.apk.sha256)

KuiesLink 是一個 Android 網址清理與短網址工具：貼上或從其他 App 分享連結，先移除已知的追蹤參數，再選擇分享乾淨網址或建立 Kuies 短網址。

App 以 Kotlin + Jetpack Compose + Material 3 撰寫，走工業簡約風，支援深色模式，不需要帳號。

## 主要功能

- **網址清理**：移除 Instagram、Threads、Facebook 等常見分享連結的追蹤參數。
- **短網址**：可選擇建立 `u.kuies.tw` 短網址，或只分享清理後的原網址。
- **分享模式**
  - 確認模式：先看清清理結果，再決定複製或分享。
  - 快速模式：清理後直接開啟系統分享選單。
- **Sharesheet 入口**：可從其他 App 的分享選單選擇 KuiesLink。
- **離線清理**：一般清理在裝置上完成；只有建立短網址時才會連線到後端。
- **設定保存**：分享模式、分享內容、深色模式等偏好以 DataStore 保存在裝置上。
- **App 內更新**：從 GitHub Releases 檢查正式版本，下載後驗證 SHA-256、套件名稱、版本與簽章，再交由 Android 系統安裝程式確認。

## Android 系統需求

- Android 9（API 28）以上
- 不在 Google Play 上架，APK 由 GitHub Releases 提供

## 支援範圍

| 平台 | 支援方式 |
| --- | --- |
| Instagram | 分享連結至 KuiesLink；清理已知的分享參數 |
| Threads | 分享連結至 KuiesLink；必要時由後端還原包裝連結 |
| Facebook | 分享連結至 KuiesLink；必要時由後端還原包裝連結 |

支援範圍依目前 App 與後端規則而定。清理只針對**已知**的追蹤參數；這是一個隱私強化工具，**不保證**第三方平台完全無法追蹤，也不保證每個網址都能還原或清理。

## 離線清理與短網址

- 一般清理在手機上完成，不會上傳你貼上的文字。
- 按下「清理並縮短網址」，或快速模式預設分享短網址時，原始網址會送到 `https://u.kuies.tw` 清理並建立短網址。
- 短網址服務需要保存短碼與導向目標，才能提供轉址；伺服器不會保存你貼上的其他內容。
- 短網址清理依後端目前支援的規則執行；後端回報 `CLEANED`、`UNCHANGED` 或 `UNSUPPORTED`，App 不會把不確定的結果宣稱為已清理。

## App 內更新

- 只向 `kuies001/KuiesLink` 的 GitHub Releases 取得最新**正式**版本（不採用 Draft 或 Pre-release）。
- 版本以 Release 附加的 `update.json` 的 `versionCode` 判斷，不只看版本字串。
- APK 只從該 Release 的固定檔名資產下載：`KuiesLink-v<versionName>.apk`。
- 下載後依序檢查大小、SHA-256、套件名稱、`versionCode`，以及 APK 簽章是否與目前安裝版本相容。
- 通過檢查後才開啟 Android 系統安裝程式；**不會**靜默安裝，也不會用網頁假裝完成更新。
- 不允許降版；檢查或下載失敗不會影響目前版本。

## 安裝

1. 到 [Releases](https://github.com/kuies001/KuiesLink/releases) 下載最新的 `KuiesLink-v<version>.apk`。
2. 允許瀏覽器或檔案管理器安裝未知來源的 App。
3. 安裝後開啟 KuiesLink。

> 第一次從其他簽章的舊版（例如本機 debug 版）轉為正式版時，Android 會拒絕覆蓋安裝，需要先解除安裝舊版。正式版之後的更新會使用同一把 release 金鑰，可直接覆蓋。

## 隱私

完整說明請見 [PRIVACY.md](PRIVACY.md)。摘要：

- 不在 App 內收集分析資料，也不要求帳號。
- 偏好設定只存在裝置上。
- 短網址與更新檢查是唯一會連線的兩種操作。
- 更新檢查會向 GitHub API 取得 Release 資訊；GitHub 會看到你的 IP，這是任何 HTTPS 請求都會發生的情況。

## 已知限制

- 只清理目前規則已知的追蹤參數；未知或功能性的參數會保留。
- 社群包裝連結的還原依賴後端；後端無法還原時會顯示提示，不會猜測。
- 短網址服務需要網路；後端不在離線時提供服務。
- App 內更新只檢查正式 Release；GitHub API 未登入時有速率限制（每小時 60 次），短時間重複檢查可能被暫時限制。
- 不在 Google Play 上架；Android 可能會對側載 App 顯示額外警告。

## 建置

需求：JDK 17+、Android SDK 37、Gradle Wrapper。

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Release 建置需要一個不在版本控制內的 `keystore.properties`：

```properties
storeFile=C:/path/outside/repo/kuieslink-release.jks
storePassword=...
keyAlias=kuieslink
keyPassword=...
```

`keystore.properties`、`*.jks`、`*.keystore` 都已列入 `.gitignore`。沒有這個檔案時仍可建置 debug，但 `assembleRelease` 會產生未簽章 APK。

更新 Manifest 與 Release 資產慣例見 [RELEASE.md](RELEASE.md)。

## 授權

程式碼以 [MIT License](LICENSE) 釋出。第三方圖示與函式庫授權見 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

Instagram、Threads、Facebook 為 Meta Platforms, Inc. 的商標；本專案與 Meta 無關。
