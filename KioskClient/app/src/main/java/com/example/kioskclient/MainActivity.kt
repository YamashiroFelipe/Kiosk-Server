package com.example.kioskclient

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var dbHelper: DatabaseHelper
    private val PREFS_NAME = "KioskClientPrefs"
    private val KEY_SERVER_IP = "server_ip"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Manter a tela sempre acesa (Modo Kiosk)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        dbHelper = DatabaseHelper(this)
        esconderBarrasSistemaTotem()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = true
            }
            webViewClient = WebViewClient()
        }

        // Registrar interface JS para o player HTML se comunicar com o banco de dados nativo
        webView.addJavascriptInterface(WebAppInterface(this, dbHelper), "AndroidClient")

        rootLayout.addView(webView)
        setContentView(rootLayout)

        val ipSalvo = obterIpSalvo()
        if (ipSalvo.isEmpty()) {
            exibirDialogConfigurarIp()
        } else {
            carregarPlayer(ipSalvo)
        }

        // Clique longo para reconfigurar o IP do servidor
        webView.setOnLongClickListener {
            exibirDialogConfigurarIp()
            true
        }

        iniciarSincronizacaoPeriodica()
    }

    private fun carregarPlayer(ip: String) {
        val url = if (ip.startsWith("http://") || ip.startsWith("https://")) {
            ip
        } else {
            "http://$ip:8080"
        }
        webView.loadUrl(url)
    }

    fun agendarSincronizacaoImediata() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(this).enqueue(syncRequest)
    }

    private fun iniciarSincronizacaoPeriodica() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SyncRespostasKiosk",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun esconderBarrasSistemaTotem() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            esconderBarrasSistemaTotem()
        }
    }

    private fun exibirDialogConfigurarIp() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val inputIp = EditText(this).apply {
            hint = "Ex: 10.0.5.195"
            setText(obterIpSalvo())
        }
        layout.addView(inputIp)

        AlertDialog.Builder(this)
            .setTitle("⚙️ Configurar IP do Servidor")
            .setMessage("Informe o IP do tablet servidor:")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Salvar e Conectar") { _, _ ->
                val novoIp = inputIp.text.toString().trim()
                if (novoIp.isNotEmpty()) {
                    salvarIp(novoIp)
                    carregarPlayer(novoIp)
                }
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun salvarIp(ip: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_SERVER_IP, ip).apply()
    }

    private fun obterIpSalvo(): String {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_SERVER_IP, "") ?: ""
    }
}