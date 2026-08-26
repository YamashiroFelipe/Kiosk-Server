package com.example.kioskclient

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "KioskClientCache.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE respostas_pendentes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pergunta_id INTEGER NOT NULL,
                resposta TEXT NOT NULL,
                data_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS respostas_pendentes")
        onCreate(db)
    }

    fun salvarRespostaLocal(perguntaId: Long, resposta: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("pergunta_id", perguntaId)
            put("resposta", resposta)
        }
        db.insert("respostas_pendentes", null, values)
    }

    fun obterRespostasPendentes(): List<Map<String, String>> {
        val lista = mutableListOf<Map<String, String>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, pergunta_id, resposta FROM respostas_pendentes", null)

        if (cursor.moveToFirst()) {
            do {
                val item = mapOf(
                    "id" to cursor.getLong(0).toString(),
                    "perguntaId" to cursor.getLong(1).toString(),
                    "resposta" to cursor.getString(2)
                )
                lista.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }

    fun removerRespostaSincronizada(id: String) {
        val db = writableDatabase
        db.delete("respostas_pendentes", "id = ?", arrayOf(id))
    }
}