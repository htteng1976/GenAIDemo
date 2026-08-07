# GenAI Demo — 裝置端語音辨識 (On-device Speech Recognition)

一個用 **ML Kit GenAI Speech Recognition**（裝置端、免 API key）的 Android demo：
按麥克風 → 說話 → 即時顯示辨識文字。預設語系為**繁體中文（台灣）** `cmn-Hant-TW`。

目的：做為可上架 Google Play 的 App（維持開發者帳號活躍），同時可在實機測試 Edge AI 語音辨識。

## 技術重點

- 100% 裝置端推論，無需網路、無需 API key。
- ML Kit GenAI Speech Recognition **Basic 模式**（Android 12 / API 31+）。
- 首次使用會由裝置下載模型，App 內有下載進度畫面。
- 不支援的裝置會顯示「您的裝置不能使用裝置端語音辨識」而不會崩潰。
- Jetpack Compose + Material 3 UI，adaptive icon（麥克風 logo）。

> 注意：ML Kit GenAI Speech Recognition 目前是 **1.0.0-alpha1**，API 之後可能變動。
> Advanced 模式辨識更佳，但目前僅限 Pixel 10；本 App 使用 Basic 模式。
> 官方限制：bootloader 解鎖的裝置無法使用。

## 環境需求

- JDK 21（本機用 Homebrew 的 `openjdk@21`，路徑已寫在 `gradle.properties` 的 `org.gradle.java.home`）
- Android SDK（`~/Library/Android/sdk`），compileSdk 36
- Gradle 透過 wrapper（8.11.1），AGP 8.9.1，Kotlin 2.1.0

## 建置指令

每次在新 shell 執行前，先設定環境變數：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=$HOME/Library/Android/sdk
```

- Debug APK（實機測試用）：
  ```bash
  ./gradlew :app:assembleDebug
  # 產物：app/build/outputs/apk/debug/app-debug.apk
  ```
- Release AAB（上架 Google Play 用，尚需簽章）：
  ```bash
  ./gradlew :app:bundleRelease
  # 產物：app/build/outputs/bundle/release/app-release.aab
  ```

## 安裝到手機（vivo X300s）測試

用 USB 連線並開啟開發者選項的 USB 偵錯，然後：

```bash
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

第一次進 App 會檢查/下載語音模型，之後按麥克風即可辨識。需授予麥克風權限。

## 上架 Google Play 待辦（下一步）

1. 建立上傳金鑰（keystore）：
   ```bash
   keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 \
     -validity 10000 -alias upload
   ```
2. 在 `app/build.gradle.kts` 加 `signingConfigs` 並讓 `release` 使用它（或用 Play App Signing）。
3. `./gradlew :app:bundleRelease` 產出簽章後的 AAB 上傳 Play Console。
4. 準備商店資訊：App 名稱、簡介、螢幕截圖、隱私權政策、麥克風權限用途說明。

## 專案結構

```
app/src/main/
  AndroidManifest.xml           # RECORD_AUDIO 權限
  java/com/genai/demo/
    MainActivity.kt             # Compose UI：麥克風按鈕、逐字稿、下載/不支援畫面
    SpeechViewModel.kt          # ML Kit GenAI 語音辨識邏輯與狀態
    Theme.kt                    # Material 3 主題（動態色彩）
  res/drawable/                 # adaptive icon 前景(麥克風)/背景(漸層)
  res/mipmap-anydpi-v26/        # ic_launcher adaptive icon
```
