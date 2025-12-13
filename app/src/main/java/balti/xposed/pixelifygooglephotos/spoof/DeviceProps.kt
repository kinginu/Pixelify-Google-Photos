package balti.xposed.pixelifygooglephotos.spoof

/**
 * Pixelデバイスのプロパティと、それに関連付けられた機能フラグ、Androidバージョンを定義します。
 */
object DeviceProps {

    // --- データクラス定義 ---

    data class AndroidVersion(
        val label: String,
        val release: String,
        val sdk: Int,
    ) {
        fun getAsMap() = mapOf(
            "RELEASE" to release,
            "SDK_INT" to sdk,
            "SDK" to sdk.toString()
        )
    }

    data class DeviceEntry(
        val deviceName: String,
        val props: Map<String, String>,
        val featureLevel: String, // ★ここを private から public に変更しました
        val androidVersion: AndroidVersion?,
    )

    data class FeatureLevel(
        val name: String,
        val flags: List<String>
    )

    // --- 定数定義 (Android Version) ---

    private val VER_7_1_2 = AndroidVersion("Nougat 7.1.2", "7.1.2", 25)
    private val VER_8_1_0 = AndroidVersion("Oreo 8.1.0", "8.1.0", 27)
    private val VER_10_0 = AndroidVersion("Q 10.0", "10", 29)
    private val VER_11_0 = AndroidVersion("R 11.0", "11", 30)
    private val VER_12_0 = AndroidVersion("S 12.0", "12", 31)

    // --- 機能フラグ定義 ---

    private val allFeatureLevels = listOf(
        FeatureLevel("Pixel 2016", listOf(
            "com.google.android.apps.photos.NEXUS_PRELOAD",
            "com.google.android.apps.photos.nexus_preload",
            "com.google.android.feature.PIXEL_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_PRELOAD",
            "com.google.android.apps.photos.PIXEL_2016_PRELOAD",
        )),
        FeatureLevel("Pixel 2017", listOf(
            "com.google.android.feature.PIXEL_2017_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2017_PRELOAD"
        )),
        FeatureLevel("Pixel 2018", listOf(
            "com.google.android.feature.PIXEL_2018_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2018_PRELOAD"
        )),
        FeatureLevel("Pixel 2019 mid-year", listOf(
            "com.google.android.feature.PIXEL_2019_MIDYEAR_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2019_MIDYEAR_PRELOAD",
        )),
        FeatureLevel("Pixel 2019", listOf(
            "com.google.android.feature.PIXEL_2019_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2019_PRELOAD",
        )),
        FeatureLevel("Pixel 2020 mid-year", listOf(
            "com.google.android.feature.PIXEL_2020_MIDYEAR_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2020_MIDYEAR_PRELOAD",
        )),
        FeatureLevel("Pixel 2020", listOf(
            "com.google.android.feature.PIXEL_2020_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2020_PRELOAD",
        )),
        FeatureLevel("Pixel 2021 mid-year", listOf(
            "com.google.android.feature.PIXEL_2021_MIDYEAR_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2021_MIDYEAR_PRELOAD",
        )),
        FeatureLevel("Pixel 2021", listOf(
            "com.google.android.feature.PIXEL_2021_EXPERIENCE",
            "com.google.android.apps.photos.PIXEL_2021_PRELOAD",
        )),
    )

    // --- デバイスリスト定義 ---

    val allDevices = listOf(
        DeviceEntry("None", mapOf(), "None", null),

        DeviceEntry(
            deviceName = "Pixel XL",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "marlin",
                "PRODUCT" to "marlin",
                "MODEL" to "Pixel XL",
                "FINGERPRINT" to "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"
            ),
            featureLevel = "Pixel 2016",
            androidVersion = VER_10_0
        ),
        DeviceEntry(
            deviceName = "Pixel 2",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "walleye",
                "PRODUCT" to "walleye",
                "MODEL" to "Pixel 2",
                "FINGERPRINT" to "google/walleye/walleye:8.1.0/OPM1.171019.021/4565141:user/release-keys"
            ),
            featureLevel = "Pixel 2017",
            androidVersion = VER_8_1_0
        ),
        DeviceEntry(
            deviceName = "Pixel 3 XL",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "crosshatch",
                "PRODUCT" to "crosshatch",
                "MODEL" to "Pixel 3 XL",
                "FINGERPRINT" to "google/crosshatch/crosshatch:11/RQ3A.211001.001/7641976:user/release-keys"
            ),
            featureLevel = "Pixel 2018",
            androidVersion = VER_11_0
        ),
        DeviceEntry(
            deviceName = "Pixel 3a XL",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "bonito",
                "PRODUCT" to "bonito",
                "MODEL" to "Pixel 3a XL",
                "FINGERPRINT" to "google/bonito/bonito:11/RQ3A.211001.001/7641976:user/release-keys"
            ),
            featureLevel = "Pixel 2019 mid-year",
            androidVersion = VER_11_0
        ),
        DeviceEntry(
            deviceName = "Pixel 4 XL",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "coral",
                "PRODUCT" to "coral",
                "MODEL" to "Pixel 4 XL",
                "FINGERPRINT" to "google/coral/coral:12/SP1A.211105.002/7743617:user/release-keys"
            ),
            featureLevel = "Pixel 2019",
            androidVersion = VER_12_0
        ),
        DeviceEntry(
            deviceName = "Pixel 4a",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "sunfish",
                "PRODUCT" to "sunfish",
                "MODEL" to "Pixel 4a",
                "FINGERPRINT" to "google/sunfish/sunfish:11/RQ3A.211001.001/7641976:user/release-keys"
            ),
            featureLevel = "Pixel 2020 mid-year",
            androidVersion = VER_11_0
        ),
        DeviceEntry(
            deviceName = "Pixel 5",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "redfin",
                "PRODUCT" to "redfin",
                "MODEL" to "Pixel 5",
                "FINGERPRINT" to "google/redfin/redfin:12/SP1A.211105.003/7757856:user/release-keys"
            ),
            featureLevel = "Pixel 2020",
            androidVersion = VER_12_0
        ),
        DeviceEntry(
            deviceName = "Pixel 5a",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "barbet",
                "PRODUCT" to "barbet",
                "MODEL" to "Pixel 5a",
                "FINGERPRINT" to "google/barbet/barbet:11/RD2A.211001.002/7644766:user/release-keys"
            ),
            featureLevel = "Pixel 2021 mid-year",
            androidVersion = VER_11_0
        ),
        DeviceEntry(
            deviceName = "Pixel 6 Pro",
            props = mapOf(
                "BRAND" to "google",
                "MANUFACTURER" to "Google",
                "DEVICE" to "raven",
                "PRODUCT" to "raven",
                "MODEL" to "Pixel 6 Pro",
                "FINGERPRINT" to "google/raven/raven:12/SD1A.210817.036/7805805:user/release-keys"
            ),
            featureLevel = "Pixel 2021",
            androidVersion = VER_12_0
        ),
    )

    // --- パブリック API ---

    const val defaultDeviceName = "Pixel 5"

    fun getDeviceProps(deviceName: String?): DeviceEntry? =
        allDevices.find { it.deviceName == deviceName }

    fun getFeaturesUpToFromDeviceName(deviceName: String?): Set<String> {
        val entry = getDeviceProps(deviceName) ?: return emptySet()
        val targetIndex = allFeatureLevels.indexOfFirst { it.name == entry.featureLevel }
        if (targetIndex == -1) return emptySet()

        return allFeatureLevels
            .take(targetIndex + 1)
            .flatMap { it.flags }
            .toSet()
    }
}