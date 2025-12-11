package balti.xposed.pixelifygooglephotos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import balti.xposed.pixelifygooglephotos.Constants.CONF_EXPORT_NAME
import balti.xposed.pixelifygooglephotos.Constants.FIELD_LATEST_VERSION_CODE
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_LAST_VERSION
import balti.xposed.pixelifygooglephotos.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_FEATURES_LIST
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL2
import balti.xposed.pixelifygooglephotos.Constants.SHARED_PREF_FILE_NAME
import balti.xposed.pixelifygooglephotos.Constants.TELEGRAM_GROUP
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL2
import balti.xposed.pixelifygooglephotos.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

class ActivityMain : AppCompatActivity() {

    // ViewBindingのインスタンス
    private lateinit var binding: ActivityMainBinding

    /**
     * Xposedモジュールの設定ファイル読み込み
     * MODE_WORLD_READABLE は通常クラッシュしますが、Xposedモジュールとしては必須です。
     */
    private val pref by lazy {
        try {
            // Context.MODE_WORLD_READABLE は非推奨ですがXposedには必要
            @Suppress("DEPRECATION")
            getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Exception) {
            null
        }
    }

    private val utils by lazy { Utils() }

    /**
     * BuildConfig.VERSION_CODE の代替
     * 現在のアプリのバージョンコードを動的に取得
     */
    private val currentVersionCode: Long
        get() {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    packageManager.getPackageInfo(packageName, 0)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }
            } catch (e: Exception) {
                -1L
            }
        }

    /**
     * 子Activityの結果を受け取るランチャー
     */
    private val childActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                showRebootSnack()
            }
        }

    // ファイル作成用ランチャー
    private val configCreateLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        utils.writeConfigFile(this, uri, pref)
                        showToast(getString(R.string.export_complete))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("${getString(R.string.share_error)}: ${e.message}")
            }
        }

    // ファイル読み込み用ランチャー
    private val configOpenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        utils.readConfigFile(this, uri, pref)
                        showToast(getString(R.string.import_complete))
                        restartActivity()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("${getString(R.string.read_error)}: ${e.message}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBindingによるレイアウト設定
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (pref == null) {
            AlertDialog.Builder(this)
                .setMessage(R.string.module_not_enabled)
                .setPositiveButton(R.string.close) { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        setupUI()
        checkForUpdates()
    }

    private fun setupUI() {
        // 設定リセットボタン
        binding.resetSettings.setOnClickListener {
            pref?.edit()?.apply {
                putString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
                putBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true)
                putBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true)
                putStringSet(
                    PREF_SPOOF_FEATURES_LIST,
                    DeviceProps.defaultFeatures.map { it.displayName }.toSet()
                )
                putBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
                putBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false)
                putString(PREF_SPOOF_ANDROID_VERSION_MANUAL, null)
                apply()
            }
            restartActivity()
        }

        // ROM機能レベルの上書きスイッチ
        binding.overrideRomFeatureLevels.apply {
            isChecked = pref?.getBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true) ?: false
            setOnCheckedChangeListener { _, isChecked ->
                pref?.edit()?.putBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, isChecked)?.apply()
                showRebootSnack()
            }
        }

        // Google Photosのみに適用するスイッチ
        binding.spoofOnlyInGooglePhotosSwitch.apply {
            isChecked = pref?.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: false
            setOnCheckedChangeListener { _, isChecked ->
                pref?.edit()?.putBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, isChecked)?.apply()
                showRebootSnack()
            }
        }

        // デバイス偽装スピナー
        setupSpinner()

        // 高度な設定リンク
        binding.advancedOptions.apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                childActivityLauncher.launch(Intent(this@ActivityMain, AdvancedOptionsActivity::class.java))
            }
        }

        // 強制停止ボタン
        binding.forceStopGooglePhotos.setOnClickListener {
            utils.forceStopPackage(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, this)
        }

        // アプリを開くボタン
        binding.openGooglePhotos.setOnClickListener {
            utils.openApplication(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, this)
        }

        // 機能カスタマイズ画面へ
        binding.customizeFeatureFlags.setOnClickListener {
            childActivityLauncher.launch(Intent(this, FeatureCustomize::class.java))
        }

        // Telegramリンク
        binding.telegramGroup.apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { openWebLink(TELEGRAM_GROUP) }
        }

        // 設定のエクスポート
        binding.confExport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.export_config)
                .setMessage(R.string.export_config_desc)
                .setPositiveButton(R.string.share) { _, _ -> shareConfFile() }
                .setNegativeButton(R.string.save) { _, _ -> saveConfFile() }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }

        // 設定のインポート
        binding.confImport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.import_config)
                .setMessage(R.string.import_config_desc)
                .setPositiveButton(android.R.string.ok) { _, _ -> importConfFile() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun setupSpinner() {
        val deviceNames = DeviceProps.allDevices.map { it.deviceName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.deviceSpooferSpinner.apply {
            this.adapter = adapter
            val defaultSelection = pref?.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)

            // 初期化時のリスナー発火を防ぐためのハック
            setSelection(adapter.getPosition(defaultSelection), false)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val deviceName = adapter.getItem(position)
                    pref?.edit()?.apply {
                        putString(PREF_DEVICE_TO_SPOOF, deviceName)
                        putStringSet(
                            PREF_SPOOF_FEATURES_LIST,
                            DeviceProps.getFeaturesUpToFromDeviceName(deviceName)
                        )
                        apply()
                    }
                    peekFeatureFlagsChanged(binding.featureFlagsChanged)
                    showRebootSnack()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    /**
     * AsyncTaskの代わりにCoroutinesを使用
     */
    private fun checkForUpdates() {
        // Dispatchers.IO でバックグラウンド実行
        lifecycleScope.launch(Dispatchers.IO) {
            val updateUrl = checkUpdateAvailable()

            // UI更新は Dispatchers.Main で実行
            if (updateUrl != null) {
                withContext(Dispatchers.Main) {
                    binding.updateAvailableLink.apply {
                        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        visibility = View.VISIBLE
                        setOnClickListener { openWebLink(updateUrl) }
                    }
                }
            }
        }
    }

    /**
     * アップデート確認ロジック (バックグラウンドで呼ばれる前提)
     */
    private fun checkUpdateAvailable(): String? {
        fun getUpdateStatus(url: String): Boolean {
            return try {
                val jsonString = URL(url).readText() // readText()はKotlinの便利な拡張関数
                if (jsonString.isNotBlank()) {
                    val json = JSONObject(jsonString)
                    val remoteVersion = json.getInt(FIELD_LATEST_VERSION_CODE)
                    currentVersionCode < remoteVersion // BuildConfigを使わず動的プロパティを使用
                } else false
            } catch (_: Exception) {
                false
            }
        }

        return when {
            getUpdateStatus(UPDATE_INFO_URL) -> RELEASES_URL
            getUpdateStatus(UPDATE_INFO_URL2) -> RELEASES_URL2
            else -> null
        }
    }

    private fun showRebootSnack() {
        if (pref == null) return
        Snackbar.make(binding.rootViewForSnackbar, R.string.please_force_stop_google_photos, Snackbar.LENGTH_SHORT).show()
    }

    private fun peekFeatureFlagsChanged(textView: TextView) {
        textView.apply {
            alpha = 1.0f
            animate().alpha(0.0f).apply {
                duration = 1000
                startDelay = 3000
            }.start()
        }
    }

    private fun restartActivity() {
        finish()
        startActivity(intent)
    }

    private fun showChangeLog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.version_head)
            .setMessage(R.string.version_desc)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_changelog -> showChangeLog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openWebLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        })
    }

    private fun shareConfFile() {
        try {
            val confFile = File(cacheDir, CONF_EXPORT_NAME)
            val uriFromFile = Uri.fromFile(confFile)

            confFile.delete()
            utils.writeConfigFile(this, uriFromFile, pref)

            // BuildConfig.APPLICATION_ID の代わりに packageName を使用
            val confFileShareUri = FileProvider.getUriForFile(this, packageName, confFile)

            Intent().run {
                action = Intent.ACTION_SEND
                type = "*/*"
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_STREAM, confFileShareUri)
                startActivity(Intent.createChooser(this, getString(R.string.share_config_file)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("${getString(R.string.share_error)}: ${e.message}")
        }
    }

    private fun saveConfFile() {
        val openIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, CONF_EXPORT_NAME)
        }
        showToast(getString(R.string.select_a_location))
        configCreateLauncher.launch(openIntent)
    }

    private fun importConfFile() {
        val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        configOpenLauncher.launch(openIntent)
    }

    // Toast表示用のヘルパー関数
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}