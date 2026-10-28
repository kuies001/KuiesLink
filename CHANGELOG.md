# Changelog

## 0.3.4 (versionCode 11) — 2026-10-29

- 外部分享改為透明 Dialog Activity：保留來源 App 畫面，不再跳回 KuiesLink 首頁。
- 小視窗顯示原始／清理後／短網址與移除參數，提供複製、分享、完成。
- 外部分享自動建立短網址；取消 Sharesheet 會回到小視窗，完成／返回回到來源 App。
- 首頁手動輸入仍維持完整「處理結果」頁。
- 更新隱私說明與快速模式文案。

## 0.3.3 (versionCode 10) — 2026-10-22

- 新增 App 內檢查更新：只讀取 GitHub Releases 的最新正式版本，以 `versionCode` 判斷。
- 新增 App 內下載與安裝：進度顯示、SHA-256、套件名稱、版本與簽章驗證，通過後才開啟系統安裝程式。
- 新增 `REQUEST_INSTALL_PACKAGES` 權限與未知來源安裝引導；不支援靜默安裝。
- 更新 Manifest 採 Release 附加的 `update.json`，APK 使用固定檔名 `KuiesLink-v<version>.apk`。
- 設定頁新增「檢查更新」，顯示已是最新、有新版本、下載進度、待授權與錯誤訊息。
- 新增正式 release 簽章設定；簽章金鑰與密碼不在版本控制內。
- 新增 README、PRIVACY、LICENSE、THIRD_PARTY_NOTICES 與 Release 慣例文件。
- 維持原有網址清理、短網址、分享模式與 DataStore 行為。

## 0.3.2 (versionCode 9) — 2026-10-22

- 首頁平台圖示改為官方品牌輪廓（Instagram／Threads／Facebook）。
- 設定圖示改為 Material outlined gear。
- 設定頁「分享內容」文案精簡，新增「短網址（推薦）」。
- 正式 launcher icon（adaptive icon、round icon、legacy mipmap）。

## 0.3.1 (versionCode 8) — 2026-10-22

- 平台圖示、設定齒輪、設定列 badge 與精簡選擇彈窗。

## 0.3.0 (versionCode 7) — 2026-10-22

- UI/UX 重構：設計系統、五個畫面、深色模式。

## 0.2.1 (versionCode 6) — 2026-10-21

- 對接正式站 `strict_cleaning` 契約。

## 0.2.0 (versionCode 5) — 2026-10-21

- 短網址接線、分享模式、預設分享內容與 DataStore 設定。
