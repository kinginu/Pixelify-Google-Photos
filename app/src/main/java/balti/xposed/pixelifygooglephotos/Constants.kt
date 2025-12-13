package balti.xposed.pixelifygooglephotos

object Constants {

    // --- App & Package Info ---
    const val PACKAGE_NAME_GOOGLE_PHOTOS = "com.google.android.apps.photos"
    const val SHARED_PREF_FILE_NAME = "prefs"

    // --- Social & Updates ---
    const val TELEGRAM_GROUP = "https://t.me/pixelifyGooglePhotos"

    const val UPDATE_INFO_URL = "https://raw.githubusercontent.com/BaltiApps/Pixelify-Google-Photos/main/update_info.json"
    const val UPDATE_INFO_URL2 = "https://raw.githubusercontent.com/Xposed-Modules-Repo/balti.xposed.pixelifygooglephotos/main/update_info.json"

    const val RELEASES_URL = "https://github.com/BaltiApps/Pixelify-Google-Photos/releases"
    const val RELEASES_URL2 = "https://github.com/Xposed-Modules-Repo/balti.xposed.pixelifygooglephotos/releases"

    const val FIELD_LATEST_VERSION_CODE = "latest_version_code"

    // --- Shared Preferences Keys ---

    // ターゲットデバイス名 (例: "Pixel 5")
    const val PREF_DEVICE_TO_SPOOF = "PREF_DEVICE_TO_SPOOF"

    // 機能フラグのリスト (Set<String>)
    const val PREF_SPOOF_FEATURES_LIST = "PREF_SPOOF_FEATURES_LIST"

    // Google Photosのみをフックするかどうか (Boolean)
    const val PREF_STRICTLY_CHECK_GOOGLE_PHOTOS = "PREF_STRICTLY_CHECK_GOOGLE_PHOTOS"

    // カスタムROMの機能を上書き無効化するか (Boolean)
    const val PREF_OVERRIDE_ROM_FEATURE_LEVELS = "PREF_OVERRIDE_ROM_FEATURE_LEVELS"

    // 詳細ログ出力 (Boolean)
    const val PREF_ENABLE_VERBOSE_LOGS = "PREF_ENABLE_VERBOSE_LOGS"

    // デバイスに合わせてAndroidバージョンを自動設定するか (Boolean)
    // ※リファクタリングで常にTrueとして扱われますが、MainActivityとの互換性のため残しています
    const val PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE = "PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE"

}