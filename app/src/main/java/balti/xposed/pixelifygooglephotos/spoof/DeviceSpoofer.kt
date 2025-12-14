package balti.xposed.pixelifygooglephotos.spoof

import android.util.Log
import balti.xposed.pixelifygooglephotos.Constants
import balti.xposed.pixelifygooglephotos.Constants.PACKAGE_NAME_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class DeviceSpoofer: IXposedHookLoadPackage {

    private fun log(message: String){
        XposedBridge.log("PixelifyGooglePhotos: $message")
        Log.d("PixelifyGooglePhotos", message)
    }

    private val pref by lazy {
        XSharedPreferences("balti.xposed.pixelifygooglephotos", Constants.SHARED_PREF_FILE_NAME)
    }

    private val verboseLog: Boolean by lazy {
        pref.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
    }

    private val finalDeviceToSpoof by lazy {
        val deviceName = pref.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
        if (verboseLog) log("Device spoof: $deviceName")
        DeviceProps.getDeviceProps(deviceName)
    }

    // 古い手動設定ロジックを廃止し、常に選択されたデバイスのバージョンを使用します
    private val androidVersionToSpoof: DeviceProps.AndroidVersion? by lazy {
        finalDeviceToSpoof?.androidVersion
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (!pref.getBoolean(Constants.PREF_MODULE_ENABLED, true)) return

        if (pref.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) &&
            lpparam?.packageName != PACKAGE_NAME_GOOGLE_PHOTOS) return

        if (verboseLog) {
            log("Loaded DeviceSpoofer for ${lpparam?.packageName}")
            log("Device spoof target: ${finalDeviceToSpoof?.deviceName}")
        }

        finalDeviceToSpoof?.props?.let { props ->
            if (props.isEmpty()) return

            val classLoader = lpparam?.classLoader ?: return
            val classBuild = XposedHelpers.findClass("android.os.Build", classLoader)

            props.forEach { (key, value) ->
                XposedHelpers.setStaticObjectField(classBuild, key, value)
                if (verboseLog) log("DEVICE PROPS: $key - $value")
            }
        }

        androidVersionToSpoof?.getAsMap()?.let { versionProps ->
            val classLoader = lpparam?.classLoader ?: return
            val classBuildVersion = XposedHelpers.findClass("android.os.Build.VERSION", classLoader)

            versionProps.forEach { (key, value) ->
                XposedHelpers.setStaticObjectField(classBuildVersion, key, value)
                if (verboseLog) log("VERSION SPOOF: $key - $value")
            }
        }
    }
}