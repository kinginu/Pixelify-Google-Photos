package balti.xposed.pixelifygooglephotos.spoof

import android.util.Log
import balti.xposed.pixelifygooglephotos.Constants.PACKAGE_NAME_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_FEATURES_LIST
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.SHARED_PREF_FILE_NAME
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

class FeatureSpoofer: IXposedHookLoadPackage {

    /**
     * Actual class is not android.content.pm.PackageManager.
     * It is an abstract class which cannot be hooked.
     * Actual class found from stackoverflow:
     * https://stackoverflow.com/questions/66523720/xposed-cant-hook-getinstalledapplications
     */
    private val CLASS_APPLICATION_MANAGER = "android.app.ApplicationPackageManager"

    /**
     * Method hasSystemFeature(). Two signatures exist. We need to hook both.
     * https://developer.android.com/reference/android/content/pm/PackageManager#hasSystemFeature(java.lang.String)
     * https://developer.android.com/reference/android/content/pm/PackageManager#hasSystemFeature(java.lang.String,%20int)
     */
    private val METHOD_HAS_SYSTEM_FEATURE = "hasSystemFeature"

    /**
     * Simple message to log messages in lsposed log as well as android log.
     */
    private fun log(message: String){
        XposedBridge.log("PixelifyGooglePhotos: $message")
        Log.d("PixelifyGooglePhotos", message)
    }

    /**
     * To read preference of user.
     */
    private val pref by lazy {
        XSharedPreferences("balti.xposed.pixelifygooglephotos", SHARED_PREF_FILE_NAME)
    }

    private val verboseLog: Boolean by lazy {
        pref.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
    }

    /**
     * This is the final list of features to spoof.
     * Update: Now reads the actual feature strings directly from Preferences,
     * as MainActivity saves them directly.
     */
    private val finalFeaturesToSpoof: Set<String> by lazy {
        // MainActivityでは実際の機能フラグ文字列(Set<String>)を保存するようになったため、
        // 複雑なマッピングロジックは不要です。
        val savedFeatures = pref.getStringSet(PREF_SPOOF_FEATURES_LIST, null)

        val features = if (savedFeatures != null) {
            log("Feature flags source: PREFS")
            savedFeatures
        } else {
            log("Feature flags source: DEFAULT")
            DeviceProps.getFeaturesUpToFromDeviceName(DeviceProps.defaultDeviceName)
        }

        features.apply {
            if (verboseLog) log("Pass TRUE for feature flags: $this")
        }
    }

    /**
     * Preference to override upper feature levels from custom ROMs
     */
    private val overrideCustomROMLevels by lazy {
        pref.getBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true)
    }

    /**
     * List of feature flags which are not present in [finalFeaturesToSpoof].
     * If any feature is in this list, spoof it as not present.
     * Only if preference [PREF_OVERRIDE_ROM_FEATURE_LEVELS] are enabled.
     */
    private val featuresNotToSpoof: Set<String> by lazy {
        // DeviceProps内の全機能リストを取得するために、最新のデバイス(Pixel 6 Pro)を指定して取得します。
        // これにより、定義されている全ての機能フラグのスーパーセットが取得できます。
        val allPossibleFeatures = DeviceProps.getFeaturesUpToFromDeviceName("Pixel 6 Pro")

        // 「全ての機能」から「有効にしたい機能」を引いたものが「無効化すべき機能」です
        (allPossibleFeatures - finalFeaturesToSpoof).apply {
            if (verboseLog) log("Pass FALSE for feature flags: $this")
        }
    }

    /**
     * If a feature needed for google photos is needed, i.e. features in [finalFeaturesToSpoof],
     * then set result of hooked method [METHOD_HAS_SYSTEM_FEATURE] as `true`.
     * If [PREF_OVERRIDE_ROM_FEATURE_LEVELS] is enabled, and the feature is present in [featuresNotToSpoof]
     * then set result as `false`.
     * Else don't set anything.
     */
    private fun spoofFeatureEnquiryResultIfNeeded(param: XC_MethodHook.MethodHookParam?){
        val arguments = param?.args?.toList() ?: return
        if (arguments.isEmpty()) return

        val featureName = arguments[0].toString()

        var passFeatureTrue = false
        var passFeatureFalse = false

        if (featureName in finalFeaturesToSpoof) {
            passFeatureTrue = true
        } else if (overrideCustomROMLevels) {
            if (featureName in featuresNotToSpoof) {
                passFeatureFalse = true
            }
        }

        if (passFeatureTrue) {
            param?.setResult(true)
            if (verboseLog) log("TRUE - feature: $featureName")
        }
        else if (passFeatureFalse) {
            param?.setResult(false)
            if (verboseLog) log("FALSE - feature: $featureName")
        }
        // else: 何もしない（元の実装に任せる）
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {

        /**
         * If user selects to never use this on any other app other than Google photos,
         * then check package name and return if necessary.
         */
        if (pref.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) &&
            lpparam?.packageName != PACKAGE_NAME_GOOGLE_PHOTOS) return

        if (verboseLog) log("Loaded FeatureSpoofer for ${lpparam?.packageName}")

        /**
         * Hook hasSystemFeature(String).
         */
        XposedHelpers.findAndHookMethod(
            CLASS_APPLICATION_MANAGER,
            lpparam?.classLoader,
            METHOD_HAS_SYSTEM_FEATURE, String::class.java,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    spoofFeatureEnquiryResultIfNeeded(param)
                }
            }
        )

        /**
         * Hook hasSystemFeature(String, int).
         */
        XposedHelpers.findAndHookMethod(
            CLASS_APPLICATION_MANAGER,
            lpparam?.classLoader,
            METHOD_HAS_SYSTEM_FEATURE, String::class.java, Int::class.java,
            object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    spoofFeatureEnquiryResultIfNeeded(param)
                }
            }
        )
    }
}