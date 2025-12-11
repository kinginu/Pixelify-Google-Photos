package balti.xposed.pixelifygooglephotos.ui

import android.os.Build
import android.util.Log
import balti.xposed.pixelifygooglephotos.ui.Constants.PACKAGE_NAME_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.HashMap

/**
 * Codenames of pixels:
 * https://oneandroid.net/all-google-pixel-codenames-from-sailfish-to-redfin/
 *
 * Device properties stored in [DeviceProps].
 */
class DeviceSpoofer: IXposedHookLoadPackage {

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
        // 【修正箇所】BuildConfigを使わず、パッケージ名を直接指定します
        XSharedPreferences("balti.xposed.pixelifygooglephotos", Constants.SHARED_PREF_FILE_NAME)
    }

    private val verboseLog: Boolean by lazy {
        pref.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
    }

    /**
     * This will always be null if the user has not chosen to spoof android version.
     * If not null, then following will be spoofed:
     * [Build.VERSION.RELEASE], [Build.VERSION.SDK_INT]
     *
     * @see DeviceProps.AndroidVersion
     */
    private val androidVersionToSpoof: DeviceProps.AndroidVersion? by lazy {
        if (pref.getBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false))
            finalDeviceToSpoof?.androidVersion
        else {
            pref.getString(PREF_SPOOF_ANDROID_VERSION_MANUAL, null)?.let {
                DeviceProps.getAndroidVersionFromLabel(it)
            }
        }
    }

    /**
     * This is the final device to spoof.
     * By default use Pixel 5.
     */
    private val finalDeviceToSpoof by lazy {
        val deviceName = pref.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
        log("Device spoof: $deviceName")
        DeviceProps.getDeviceProps(deviceName)
    }

    /**
     * Inspired by:
     * https://github.com/itsuki-t/FakeDeviceData/blob/master/src/jp/rmitkt/xposed/fakedevicedata/FakeDeviceData.java
     */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {

        /**
         * If user selects to never use this on any other app other than Google photos,
         * then check package name and return if necessary.
         */
        if (pref.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) &&
            lpparam?.packageName != PACKAGE_NAME_GOOGLE_PHOTOS) return

        log("Loaded DeviceSpoofer for ${lpparam?.packageName}")
        log("Device spoof: ${finalDeviceToSpoof?.deviceName}")

        finalDeviceToSpoof?.props?.run {

            if (this.keys.isEmpty()) return
            val classLoader = lpparam?.classLoader ?: return

            val classBuild = XposedHelpers.findClass("android.os.Build", classLoader)
            this.keys.forEach { // これで Map のキーの Iterable が取得され、forEach が正しく解決される
                XposedHelpers.setStaticObjectField(classBuild, it, this[it])
                if (verboseLog) log("DEVICE PROPS: $it - ${this[it]}")
            }

        }

        androidVersionToSpoof?.getAsMap()?.run {

            val classLoader = lpparam?.classLoader ?: return
            val classBuild = XposedHelpers.findClass("android.os.Build.VERSION", classLoader)

            this.keys.forEach {
                XposedHelpers.setStaticObjectField(classBuild, it, this[it])
                if (verboseLog) log("VERSION SPOOF: $it - ${this[it]}")
            }
        }

    }

}