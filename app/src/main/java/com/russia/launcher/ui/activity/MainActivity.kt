package com.russia.launcher.ui.activity

import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.russia.game.R
import com.russia.game.core.Samp
import com.russia.launcher.async.dto.response.FileInfo
import com.russia.launcher.async.task.CacheChecker
import com.russia.launcher.config.Config.DONATE_URL
import com.russia.launcher.config.Config.FORUM_URL
import com.russia.launcher.domain.enums.DownloadType
import com.russia.launcher.domain.enums.StorageElements
import com.russia.launcher.service.impl.ActivityServiceImpl
import com.russia.launcher.storage.NativeStorage
import com.russia.launcher.storage.Storage
import com.russia.launcher.ui.dialogs.EnterLockedServerPasswordDialog
import com.russia.launcher.ui.fragment.MonitoringFragment
import com.russia.launcher.ui.fragment.SettingsFragment
import com.russia.launcher.utils.MainUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {
    private var animation: Animation? = null
    private var donateButton: LinearLayout? = null
    private var donateImage: ImageView? = null
    private var donateTV: TextView? = null
    private var monitoringButton: LinearLayout? = null
    private var monitoringFragment: MonitoringFragment? = null
    private var monitoringImage: ImageView? = null
    private var monitoringTV: TextView? = null
    private var playButton: LinearLayout? = null
    private var playImage: ImageView? = null
    private var rouletteButton: LinearLayout? = null
    private var rouletteImage: ImageView? = null
    private var rouletteTV: TextView? = null
    private var settingsButton: LinearLayout? = null
    private var settingsFragment: SettingsFragment? = null
    private var settingsImage: ImageView? = null
    private var settingsTV: TextView? = null
    private var container_layout: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTheme(R.style.AppBaseTheme)

//        setContentView(R.layout.spin_box);
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
//        gg = new SpinBox(this);
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        container_layout = findViewById(R.id.container)
        animation = AnimationUtils.loadAnimation(this, R.anim.button_click)
        monitoringTV = findViewById(R.id.monitoringTV)
        settingsTV = findViewById(R.id.settingsTV)
        rouletteTV = findViewById(R.id.forumTV) 
        donateTV = findViewById(R.id.donateTV)
        monitoringImage = findViewById(R.id.monitoringImage)
        settingsImage = findViewById(R.id.settingsImage)
        rouletteImage = findViewById(R.id.forumImage)
        donateImage = findViewById(R.id.donateImage)
        playImage = findViewById(R.id.playImage)
        monitoringButton = findViewById(R.id.monitoringButton)
        settingsButton = findViewById(R.id.settingsButton)
        rouletteButton = findViewById(R.id.rouletteButton)
        donateButton = findViewById(R.id.donateButton)
        playButton = findViewById(R.id.playButton)
        monitoringFragment = MonitoringFragment()
        settingsFragment = SettingsFragment()
        if (savedInstanceState != null && savedInstanceState.getBoolean(IS_AFTER_LOADING_KEY)) {
            replaceFragment(settingsFragment)
        } else if (savedInstanceState == null && intent.extras != null && intent.extras!!.getBoolean(IS_AFTER_LOADING_KEY)) {
            onClickSettings()
        } else {
            replaceFragment(monitoringFragment)
        }
        monitoringButton!!.setOnClickListener {
            onClickMonitoring()
        }

        settingsButton!!.setOnClickListener {
            onClickSettings()
        }

        rouletteButton!!.setOnClickListener {
            val address = Uri.parse(FORUM_URL)
            val openlink = Intent(Intent.ACTION_VIEW, address)
            startActivity(openlink)
        }
        donateButton!!.setOnClickListener {
            onClickDonate()
        }

        playButton!!.setOnClickListener {
            onClickPlay()
        }

    }

    private fun onClickPlay() {
        startGame()
        /*if (isCheckSkipping) {
            startGame()
        } else {
            val progressDialog = findViewById<ConstraintLayout>(R.id.progressDialog)
            progressDialog.visibility = View.VISIBLE

            GlobalScope.launch {
                val filesList = CacheChecker.getInvalidFilesList(this@MainActivity)
                withContext(Dispatchers.Main) {
                    doAfterCacheChecked(filesList)

                    progressDialog.visibility = View.GONE
                }
            }
        }*/
    }

    private val isCheckSkipping: Boolean
        get() {
            val isTestMode = NativeStorage.getClientProperty("test", this)

            //todo брать из Storage тк static стирается
            return TEST_MODE_ON_VALUE == isTestMode
    //        return true
        }

    private fun doAfterCacheChecked(fileToReload: MutableList<FileInfo>) {

        for (file in fileToReload) {
            println("invalid file = ${file.path}")
        }
        if (fileToReload.isEmpty()) {
            startGame()
        } else {
            MainUtils.FILES_TO_RELOAD = fileToReload

            MainUtils.type = DownloadType.RELOAD_GAME_FILES
            startActivity(Intent(this, LoaderActivity::class.java))
        }
    }

    private fun startGame() {
        // Primeira execução: instala a data pelo próprio app no armazenamento privado.
        // Isso evita EACCES/FUSE em Android/data.
        if (!isPrivateGameDataReady()) {
            selectGameDataZip()
            return
        }

        launchGameAfterDataReady()
    }

    private fun isPrivateGameDataReady(): Boolean {
        val marker = File(filesDir, PRIVATE_DATA_MARKER)

        // Além do marcador, confirma dois arquivos que o GTA realmente usa.
        val americanGxt = File(filesDir, "TEXT/AMERICAN.GXT")
        val mobileTxt = File(filesDir, "texdb/mobile/mobile.txt")

        return marker.exists() && americanGxt.isFile && mobileTxt.isFile
    }

    private fun selectGameDataZip() {
        Toast.makeText(
            this,
            "Selecione o dataparateste.zip",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }

        try {
            startActivityForResult(intent, REQUEST_GAME_DATA_ZIP)
        } catch (e: Exception) {
            // Alguns gerenciadores não anunciam application/zip corretamente.
            val fallback = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(fallback, REQUEST_GAME_DATA_ZIP)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_GAME_DATA_ZIP || resultCode != RESULT_OK) {
            return
        }

        val uri = data?.data ?: run {
            ActivityServiceImpl.showErrorMessage("Arquivo ZIP inválido.", this)
            return
        }

        try {
            val takeFlags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
            // A permissão temporária desta Activity já é suficiente para a instalação.
        }

        installGameDataZip(uri)
    }

    private fun installGameDataZip(uri: Uri) {
        val progressDialog = findViewById<ConstraintLayout>(R.id.progressDialog)
        progressDialog?.visibility = View.VISIBLE
        playButton?.isEnabled = false

        Toast.makeText(
            this,
            "Instalando a data... não feche o aplicativo.",
            Toast.LENGTH_LONG
        ).show()

        Thread {
            val traceFile = File(getExternalFilesDir(null), "data_install_trace.txt")

            fun trace(message: String) {
                try {
                    traceFile.appendText(message + "\n")
                } catch (_: Exception) {
                }
            }

            try {
                File(filesDir, PRIVATE_DATA_MARKER).delete()

                val destinationRoot = filesDir.canonicalFile
                val destinationPrefix = destinationRoot.path + File.separator

                var rootPrefix: String? = null
                var filesInstalled = 0
                var bytesInstalled = 0L

                trace("BEGIN uri=$uri")
                trace("DEST=${destinationRoot.absolutePath}")

                val rawInput = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Não foi possível abrir o ZIP selecionado.")

                BufferedInputStream(rawInput, 1024 * 1024).use { bufferedInput ->
                    ZipInputStream(bufferedInput).use { zip ->
                        val buffer = ByteArray(1024 * 1024)

                        while (true) {
                            val entry = zip.nextEntry ?: break

                            var entryName = entry.name.replace('\\', '/')
                            while (entryName.startsWith("/")) {
                                entryName = entryName.substring(1)
                            }

                            if (entryName.isBlank()) {
                                zip.closeEntry()
                                continue
                            }

                            // O ZIP atual possui uma pasta externa "dataparateste/".
                            // Detectamos a primeira pasta automaticamente e removemos esse prefixo.
                            if (rootPrefix == null) {
                                val firstSlash = entryName.indexOf('/')
                                rootPrefix = if (firstSlash >= 0) {
                                    entryName.substring(0, firstSlash + 1)
                                } else {
                                    ""
                                }
                                trace("ROOT_PREFIX=$rootPrefix")
                            }

                            val prefix = rootPrefix ?: ""
                            val relativeName =
                                if (prefix.isNotEmpty() && entryName.startsWith(prefix)) {
                                    entryName.substring(prefix.length)
                                } else {
                                    entryName
                                }

                            if (relativeName.isBlank()) {
                                zip.closeEntry()
                                continue
                            }

                            val output = File(destinationRoot, relativeName).canonicalFile

                            // Proteção contra caminhos como ../../ fora de filesDir.
                            if (!output.path.startsWith(destinationPrefix)) {
                                throw SecurityException("Entrada ZIP insegura: $entryName")
                            }

                            if (entry.isDirectory) {
                                if (!output.exists() && !output.mkdirs()) {
                                    throw IllegalStateException(
                                        "Não foi possível criar ${output.absolutePath}"
                                    )
                                }
                            } else {
                                output.parentFile?.let { parent ->
                                    if (!parent.exists() && !parent.mkdirs()) {
                                        throw IllegalStateException(
                                            "Não foi possível criar ${parent.absolutePath}"
                                        )
                                    }
                                }

                                BufferedOutputStream(output.outputStream(), 1024 * 1024).use { out ->
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read <= 0) break
                                        out.write(buffer, 0, read)
                                        bytesInstalled += read
                                    }
                                    out.flush()
                                }

                                filesInstalled++

                                if (filesInstalled % 50 == 0) {
                                    trace(
                                        "PROGRESS files=$filesInstalled bytes=$bytesInstalled last=$relativeName"
                                    )
                                }
                            }

                            zip.closeEntry()
                        }
                    }
                }

                val americanGxt = File(filesDir, "TEXT/AMERICAN.GXT")
                val mobileTxt = File(filesDir, "texdb/mobile/mobile.txt")

                if (!americanGxt.isFile) {
                    throw IllegalStateException("TEXT/AMERICAN.GXT não foi instalado.")
                }

                if (!mobileTxt.isFile) {
                    throw IllegalStateException("texdb/mobile/mobile.txt não foi instalado.")
                }

                File(filesDir, PRIVATE_DATA_MARKER).writeText(
                    "ok\nfiles=$filesInstalled\nbytes=$bytesInstalled\n"
                )

                trace("OK files=$filesInstalled bytes=$bytesInstalled")
                trace("AMERICAN=${americanGxt.length()}")
                trace("MOBILE_TXT=${mobileTxt.length()}")
                trace("END")

                runOnUiThread {
                    progressDialog?.visibility = View.GONE
                    playButton?.isEnabled = true

                    Toast.makeText(
                        this,
                        "Data instalada. Iniciando o jogo...",
                        Toast.LENGTH_LONG
                    ).show()

                    launchGameAfterDataReady()
                }
            } catch (e: Exception) {
                trace(
                    "FAIL type=${e.javaClass.simpleName} message=${e.message}"
                )

                runOnUiThread {
                    progressDialog?.visibility = View.GONE
                    playButton?.isEnabled = true
                    ActivityServiceImpl.showErrorMessage(
                        "Falha ao instalar a data: ${e.message}",
                        this
                    )
                }
            }
        }.start()
    }

    private fun launchGameAfterDataReady() {
        val log = File(getExternalFilesDir(null).toString() + "/log.txt")
        log.delete()

        // Mantém o comportamento original do cliente antes de iniciar o GTA.
        val cinfo = File(getExternalFilesDir(null).toString() + "/CINFO.BIN")
        cinfo.delete()

        val minfo = File(getExternalFilesDir(null).toString() + "/models/MINFO.BIN")
        minfo.delete()

        val nickname = NativeStorage.getClientProperty("name", this)

        if (StringUtils.isBlank(nickname)) {
            ActivityServiceImpl.showErrorMessage("Informe seu nick!", this)
            onClickSettings()
            return
        }

        /*
         * Beta Tester:
         * ignora completamente a lista/monitoramento de servidores russos.
         * O CNetGame lê estes valores através de CSettings.
         */
        NativeStorage.addClientProperty("name", nickname, this)
        NativeStorage.addClientProperty("server", "1", this)
        NativeStorage.addClientProperty("ip", SERVER_IP, this)
        NativeStorage.addClientProperty("port", SERVER_PORT, this)
        NativeStorage.addClientProperty("password", StringUtils.EMPTY, this)

        val intent = Intent(this, Samp::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    private fun saveServerPassword(password: String) {
        NativeStorage.addClientProperty("password", password, this)
        startActivity(Intent(this, Samp::class.java))
        finish()
    }

    private fun onClickSettings() {
        setTextColor(settingsButton, settingsTV, settingsImage)
        replaceFragment(settingsFragment)
    }

    private fun onClickDonate() {
        val address = Uri.parse(DONATE_URL)
        val openlink = Intent(Intent.ACTION_VIEW, address)
        startActivity(openlink)
    }

    private fun onClickMonitoring() {
        setTextColor(monitoringButton, monitoringTV, monitoringImage)
        replaceFragment(monitoringFragment)
    }

    fun setTextColor(linearLayout: LinearLayout?, textView: TextView?, imageView: ImageView?) {
        monitoringButton!!.alpha = 0.45f
        settingsButton!!.alpha = 0.45f
        rouletteButton!!.alpha = 0.45f
        donateButton!!.alpha = 0.45f
        monitoringTV!!.setTextColor(resources.getColor(R.color.menuTextDisable))
        settingsTV!!.setTextColor(resources.getColor(R.color.menuTextDisable))
        rouletteTV!!.setTextColor(resources.getColor(R.color.menuTextDisable))
        donateTV!!.setTextColor(resources.getColor(R.color.menuTextDisable))
        monitoringImage!!.setColorFilter(resources.getColor(R.color.menuTextDisable), PorterDuff.Mode.SRC_IN)
        settingsImage!!.setColorFilter(resources.getColor(R.color.menuTextDisable), PorterDuff.Mode.SRC_IN)
        rouletteImage!!.setColorFilter(resources.getColor(R.color.menuTextDisable), PorterDuff.Mode.SRC_IN)
        donateImage!!.setColorFilter(resources.getColor(R.color.menuTextDisable), PorterDuff.Mode.SRC_IN)
        linearLayout!!.alpha = 1.0f
        textView!!.setTextColor(resources.getColor(R.color.menuTextEnable))
        imageView!!.setColorFilter(resources.getColor(R.color.menuTextEnable), PorterDuff.Mode.SRC_IN)
    }

    private fun replaceFragment(fragment: Fragment?) {
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment!!).commitAllowingStateLoss()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    public override fun onRestart() {
        super.onRestart()
    }

    public override fun onStop() {
        super.onStop()
    }

    companion object {
        private const val IS_AFTER_LOADING_KEY = "isAfterLoading"
        private const val GAME_DIRECTORY_EMPTY_SIZE = 0
        private const val SERVER_LOCKED_VALUE = 1
        private const val TEST_MODE_ON_VALUE = "1"
        private const val REQUEST_GAME_DATA_ZIP = 5206
        private const val PRIVATE_DATA_MARKER = ".betatester_data_ready"

        // Servidor SA-MP direto do Beta Tester
        private const val SERVER_IP = "149.56.41.51"
        private const val SERVER_PORT = "7774"
    }
}