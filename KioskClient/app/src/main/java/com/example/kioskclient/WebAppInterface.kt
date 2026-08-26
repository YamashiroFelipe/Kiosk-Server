package com.example.kioskclient

import android.content.Context
import android.webkit.JavascriptInterface

class WebAppInterface(private val context: Context, private val dbHelper: DatabaseHelper) {

    @JavascriptInterface
    fun salvarRespostaLocal(perguntaIdString: String, resposta: String) {
        val perguntaId = perguntaIdString.toLongOrNull() ?: return

        // 1. Salva no banco SQLite local do Client
        dbHelper.salvarRespostaLocal(perguntaId, resposta)

        // 2. Dispara o SyncWorker IMEDIATAMENTE
        if (context is MainActivity) {
            context.runOnUiThread {
                context.agendarSincronizacaoImediata()
            }
        }
    }
}