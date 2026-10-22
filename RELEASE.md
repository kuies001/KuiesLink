# Release 與 App 內更新慣例

這份文件說明 KuiesLink 的正式 Release 資產格式。App 只用這套固定慣例驗證更新。

## 版本規則

- `versionCode` 必須嚴格遞增；App 以 `versionCode` 判斷新舊，不比較版本字串。
- `versionName` 使用 `major.minor.patch`，不要加 `v`。
- Git tag 必須是 `v<versionName>`，例如 `v0.3.3`。
- 更新用的 Manifest 必須與 tag、APK 檔名、大小、SHA-256 一致。

## Release 資產

每個正式 Release 至少要有三個資產：

| 檔名 | 內容 |
| --- | --- |
| `KuiesLink-v<versionName>.apk` | 已用 release keystore 簽章的 APK |
| `KuiesLink-v<versionName>.apk.sha256` | APK 的 SHA-256（純文字） |
| `update.json` | App 讀取的版本 Manifest |

App 只接受 `browser_download_url` 完全等於
`https://github.com/kuies001/KuiesLink/releases/download/v<versionName>/<asset>` 的資產；
Manifest 裡的任何 URL 都不會被直接信任。

## update.json

```json
{
  "schema": 1,
  "packageName": "tw.kuies.link",
  "versionCode": 10,
  "versionName": "0.3.3",
  "apkAsset": "KuiesLink-v0.3.3.apk",
  "sha256": "2e5e27e5edf09c4104cb327ead49c9acaeb8d727512612a393ba25afe2d8e0df",
  "sizeBytes": 4525194,
  "minSdk": 28,
  "changelog": "本次更新內容..."
}
```

- `schema` 目前固定為 `1`；App 不認得其他值。
- `apkAsset` 必須是 `KuiesLink-v<versionName>.apk`。
- `sha256` 必須是 64 個十六進位字元（小寫或大寫皆可）。
- `changelog` 會顯示在更新對話框，建議 1–6 行。

## App 的驗證順序

1. 取得 `GET /repos/kuies001/KuiesLink/releases/latest`。
2. 拒絕 Draft / Pre-release。
3. 檢查 tag 格式，找到 `update.json`，確認其下載 URL 屬於同一個 Release。
4. 解析 Manifest，檢查 schema、package、versionCode、versionName、minSdk、大小、SHA-256 格式。
5. 確認 APK 資產存在、大小相符、URL 等於固定慣例。
6. 只有 `versionCode` 大於目前版本才視為更新。
7. 下載到 App 私有快取，重新計算 SHA-256 與大小。
8. 用 PackageManager 檢查 APK 的 package、versionCode 與簽章憑證集合。
9. 通過後交給 Android 系統安裝程式，由使用者確認安裝。

任何一步失敗都會刪除下載檔，且不影響已安裝版本。

## 發布步驟

1. 更新 `versionCode` / `versionName`，跑完整測試。
2. 用 release keystore 建置 `:app:assembleRelease`。
3. `apksigner verify --print-certs` 確認簽章。
4. 產生 `update.json` 與 `.sha256`，確認 `sizeBytes` 與雜湊一致。
5. 建立 tag `v<versionName>` 與正式 Release（不要勾 Pre-release）。
6. 上傳三個資產。
7. 以未登入狀態下載 APK，重新計算 SHA-256。
8. 用已安裝的 App 按一次「檢查更新」，確認讀到相同版本並顯示已是最新。

## 簽章金鑰

- release keystore 不在 repository 內；Gradle 從被 `.gitignore` 忽略的 `keystore.properties` 讀取路徑與密碼。
- 不得用 debug keystore 簽正式版。
- 若要在已安裝 debug 版的裝置上改用正式版，Android 會因簽章不同而拒絕覆蓋；需要先解除安裝，並自行備份需要的設定。
