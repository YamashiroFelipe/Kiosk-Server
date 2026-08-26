package com.example.kioskclient



import android.content.Context

import androidx.work.Worker

import androidx.work.WorkerParameters

import java.io.OutputStreamWriter

import java.net.HttpURLConnection

import java.net.URL

import org.json.JSONObject



class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {



    override fun doWork(): Result {

        val prefs = applicationContext.getSharedPreferences("KioskClientPrefs", Context.MODE_PRIVATE)

        var ipSalvo = prefs.getString("server_ip", "") ?: ""

        if (ipSalvo.isEmpty()) return Result.success()



        // Garante que o IP comece com http:// e termine com a porta correta de forma limpa

        if (!ipSalvo.startsWith("http://") && !ipSalvo.startsWith("https://")) {

            ipSalvo = "http://$ipSalvo"

        }

        // Se o usuário digitou o IP sem especificar porta, adiciona a :8080 padrão do seu servidor

        val urlServidor = if (!ipSalvo.contains(":", ignoreCase = true) || ipSalvo.substringAfterLast(":").contains("/")) {

            "$ipSalvo:8080/api/pesquisa"

        } else {

            "$ipSalvo/api/pesquisa"

        }



        val dbHelper = DatabaseHelper(applicationContext)

        val pendentes = dbHelper.obterRespostasPendentes()



        for (item in pendentes) {

            var conn: HttpURLConnection? = null

            try {

                val url = URL(urlServidor)

                conn = url.openConnection() as HttpURLConnection

                conn.requestMethod = "POST"

                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

                conn.doOutput = true

                conn.connectTimeout = 3000

                conn.readTimeout = 3000 // Boa prática para evitar que a conexão fique presa para sempre



                val jsonObject = JSONObject().apply {

                    put("perguntaId", item["perguntaId"])

                    put("resposta", item["resposta"])

                }



                // Uso do '.use' garante o fechamento automático da Stream mesmo se der erro no meio

                conn.outputStream.use { os ->

                    OutputStreamWriter(os, "UTF-8").use { writer ->

                        writer.write(jsonObject.toString())

                        writer.flush()

                    }

                }



                if (conn.responseCode == 200) {

                    // Limpa o buffer de resposta

                    conn.inputStream.bufferedReader().use { it.readText() }

                    item["id"]?.let { dbHelper.removerRespostaSincronizada(it) }

                }

            } catch (e: Exception) {

                e.printStackTrace()

            } finally {

                // O bloco finally garante que a conexão vai fechar de qualquer jeito

                conn?.disconnect()

            }

        }

        return Result.success()

    }

}