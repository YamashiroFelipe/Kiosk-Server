package com.example.kioskserver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Subimos a versão do banco para 4 para suportar a coluna 'titulo'
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "SignageKiosk.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE perguntas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                texto TEXT NOT NULL,
                tipo_resposta TEXT NOT NULL DEFAULT 'ESTRELAS',
                ordem INTEGER NOT NULL DEFAULT 1
            )
        """)

        // Tabela slides atualizada com suporte a agendamento e título customizado
        db.execSQL("""
            CREATE TABLE slides (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL,
                uri_midia TEXT,
                pergunta_id INTEGER,
                ordem INTEGER NOT NULL,
                tempo_segundos INTEGER DEFAULT 10,
                ativo INTEGER DEFAULT 1,
                dias_semana TEXT DEFAULT '1,2,3,4,5,6,7',
                hora_inicio TEXT DEFAULT '00:00',
                hora_fim TEXT DEFAULT '23:59',
                titulo TEXT,
                FOREIGN KEY(pergunta_id) REFERENCES perguntas(id)
            )
        """)

        db.execSQL("""
            CREATE TABLE respostas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pergunta_id INTEGER NOT NULL,
                resposta TEXT NOT NULL,
                data_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Adiciona os campos de agendamento
            db.execSQL("ALTER TABLE slides ADD COLUMN ativo INTEGER DEFAULT 1")
            db.execSQL("ALTER TABLE slides ADD COLUMN dias_semana TEXT DEFAULT '1,2,3,4,5,6,7'")
            db.execSQL("ALTER TABLE slides ADD COLUMN hora_inicio TEXT DEFAULT '00:00'")
            db.execSQL("ALTER TABLE slides ADD COLUMN hora_fim TEXT DEFAULT '23:59'")
        }
        if (oldVersion < 4) {
            // Adiciona a coluna de titulo sem perder mídias anteriores
            db.execSQL("ALTER TABLE slides ADD COLUMN titulo TEXT")
        }
    }

    // --- MÉTODOS DE SLIDES E MÍDIAS COM AGENDAMENTO E TÍTULO ---

    fun adicionarSlideMidia(
        uri: String,
        tempoSegundos: Int = 10,
        diasSemana: String = "1,2,3,4,5,6,7", // 1=Dom, 2=Seg, ..., 7=Sáb
        horaInicio: String = "00:00",
        horaFim: String = "23:59",
        titulo: String? = null
    ) {
        val db = writableDatabase
        val proximaOrdem = getProximaOrdemSlide()
        val values = ContentValues().apply {
            put("tipo", "MIDIA")
            put("uri_midia", uri)
            put("ordem", proximaOrdem)
            put("tempo_segundos", tempoSegundos)
            put("ativo", 1)
            put("dias_semana", diasSemana)
            put("hora_inicio", horaInicio)
            put("hora_fim", horaFim)
            put("titulo", titulo)
        }
        db.insert("slides", null, values)
    }

    fun adicionarSlidePesquisa(
        perguntaId: Long,
        uriMidia: String? = null,
        tempoSegundos: Int = 15,
        diasSemana: String = "1,2,3,4,5,6,7",
        horaInicio: String = "00:00",
        horaFim: String = "23:59",
        titulo: String? = null
    ) {
        val db = writableDatabase
        val proximaOrdem = getProximaOrdemSlide()
        val values = ContentValues().apply {
            put("tipo", "PESQUISA")
            put("pergunta_id", perguntaId)
            put("uri_midia", uriMidia)
            put("ordem", proximaOrdem)
            put("tempo_segundos", tempoSegundos)
            put("ativo", 1)
            put("dias_semana", diasSemana)
            put("hora_inicio", horaInicio)
            put("hora_fim", horaFim)
            put("titulo", titulo)
        }
        db.insert("slides", null, values)
    }

    /**
     * Retorna APENAS os slides agendados para o dia e horário atuais.
     * Este é o método chamado pelo Ktor (/api/config) para enviar a programação à TV.
     */
    fun listarSlidesAtivosParaAgora(): List<Map<String, Any?>> {
        val calendario = java.util.Calendar.getInstance()
        val diaHoje = calendario.get(java.util.Calendar.DAY_OF_WEEK).toString() // 1 a 7
        val horaAtual = String.format("%02d:%02d",
            calendario.get(java.util.Calendar.HOUR_OF_DAY),
            calendario.get(java.util.Calendar.MINUTE)
        )

        val lista = mutableListOf<Map<String, Any?>>()
        val db = readableDatabase

        val query = """
            SELECT s.id, s.tipo, s.uri_midia, s.pergunta_id, s.ordem, s.tempo_segundos, 
                   s.dias_semana, s.hora_inicio, s.hora_fim, s.titulo,
                   p.texto as texto_pergunta, p.tipo_resposta
            FROM slides s
            LEFT JOIN perguntas p ON s.pergunta_id = p.id
            WHERE s.ativo = 1 
              AND s.dias_semana LIKE ? 
              AND ? BETWEEN s.hora_inicio AND s.hora_fim
            ORDER BY s.ordem ASC
        """
        val cursor = db.rawQuery(query, arrayOf("%$diaHoje%", horaAtual))

        if (cursor.moveToFirst()) {
            do {
                val idxPerguntaId = cursor.getColumnIndexOrThrow("pergunta_id")
                val perguntaId = if (cursor.isNull(idxPerguntaId)) null else cursor.getLong(idxPerguntaId)

                val item = mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    "tipo" to cursor.getString(cursor.getColumnIndexOrThrow("tipo")),
                    "uriMidia" to cursor.getString(cursor.getColumnIndexOrThrow("uri_midia")),
                    "perguntaId" to perguntaId,
                    "ordem" to cursor.getInt(cursor.getColumnIndexOrThrow("ordem")),
                    "tempoExibicaoSegundos" to cursor.getInt(cursor.getColumnIndexOrThrow("tempo_segundos")),
                    "textoPergunta" to cursor.getString(cursor.getColumnIndexOrThrow("texto_pergunta")),
                    "tipoResposta" to cursor.getString(cursor.getColumnIndexOrThrow("tipo_resposta")),
                    "titulo" to cursor.getString(cursor.getColumnIndexOrThrow("titulo"))
                )
                lista.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }

    /**
     * Retorna TODOS os slides cadastrados para gerenciamento na tela da MainActivity do Servidor.
     */
    fun listarSlides(): List<Map<String, Any?>> {
        val lista = mutableListOf<Map<String, Any?>>()
        val db = readableDatabase

        val query = """
            SELECT s.id, s.tipo, s.uri_midia, s.pergunta_id, s.ordem, s.tempo_segundos, 
                   s.ativo, s.dias_semana, s.hora_inicio, s.hora_fim, s.titulo,
                   p.texto as texto_pergunta, p.tipo_resposta
            FROM slides s
            LEFT JOIN perguntas p ON s.pergunta_id = p.id
            ORDER BY s.ordem ASC
        """
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val idxPerguntaId = cursor.getColumnIndexOrThrow("pergunta_id")
                val perguntaId = if (cursor.isNull(idxPerguntaId)) null else cursor.getLong(idxPerguntaId)

                val item = mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    "tipo" to cursor.getString(cursor.getColumnIndexOrThrow("tipo")),
                    "uriMidia" to cursor.getString(cursor.getColumnIndexOrThrow("uri_midia")),
                    "perguntaId" to perguntaId,
                    "ordem" to cursor.getInt(cursor.getColumnIndexOrThrow("ordem")),
                    "tempoExibicaoSegundos" to cursor.getInt(cursor.getColumnIndexOrThrow("tempo_segundos")),
                    "ativo" to cursor.getInt(cursor.getColumnIndexOrThrow("ativo")),
                    "diasSemana" to cursor.getString(cursor.getColumnIndexOrThrow("dias_semana")),
                    "horaInicio" to cursor.getString(cursor.getColumnIndexOrThrow("hora_inicio")),
                    "horaFim" to cursor.getString(cursor.getColumnIndexOrThrow("hora_fim")),
                    "textoPergunta" to cursor.getString(cursor.getColumnIndexOrThrow("texto_pergunta")),
                    "tipoResposta" to cursor.getString(cursor.getColumnIndexOrThrow("tipo_resposta")),
                    "titulo" to cursor.getString(cursor.getColumnIndexOrThrow("titulo"))
                )
                lista.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }

    fun atualizarSlide(
        id: Long,
        titulo: String,
        tempoSegundos: Int,
        diasSemana: String,
        horaInicio: String,
        horaFim: String
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("titulo", titulo)
            put("tempo_segundos", tempoSegundos)
            put("dias_semana", diasSemana)
            put("hora_inicio", horaInicio)
            put("hora_fim", horaFim)
        }
        db.update("slides", values, "id = ?", arrayOf(id.toString()))
    }

    fun moverSlide(idSlide: Long, moverParaCima: Boolean) {
        val slides = listarSlides()
        val indexAtual = slides.indexOfFirst { (it["id"] as? Number)?.toLong() == idSlide }

        if (indexAtual == -1) return

        val indexAlvo = if (moverParaCima) indexAtual - 1 else indexAtual + 1

        if (indexAlvo in slides.indices) {
            val slideAtual = slides[indexAtual]
            val slideAlvo = slides[indexAlvo]

            val idAtual = (slideAtual["id"] as Number).toLong()
            val ordemAtual = (slideAtual["ordem"] as Number).toInt()

            val idAlvo = (slideAlvo["id"] as Number).toLong()
            val ordemAlva = (slideAlvo["ordem"] as Number).toInt()

            val db = writableDatabase
            db.beginTransaction()
            try {
                db.execSQL("UPDATE slides SET ordem = ? WHERE id = ?", arrayOf(ordemAlva, idAtual))
                db.execSQL("UPDATE slides SET ordem = ? WHERE id = ?", arrayOf(ordemAtual, idAlvo))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun removerSlide(id: Long) {
        val db = writableDatabase
        db.delete("slides", "id = ?", arrayOf(id.toString()))
    }

    private fun getProximaOrdemSlide(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT MAX(ordem) FROM slides", null)
        var max = 0
        if (cursor.moveToFirst()) {
            max = cursor.getInt(0)
        }
        cursor.close()
        return max + 1
    }

    // --- MÉTODOS DE PESQUISAS E RESPOSTAS ---

    fun adicionarPergunta(texto: String, tipoResposta: String = "ESTRELAS"): Long {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT MAX(ordem) FROM perguntas", null)
        var maxOrdem = 0
        if (cursor.moveToFirst()) {
            maxOrdem = cursor.getInt(0)
        }
        cursor.close()

        val values = ContentValues().apply {
            put("texto", texto)
            put("tipo_resposta", tipoResposta)
            put("ordem", maxOrdem + 1)
        }
        return db.insert("perguntas", null, values)
    }

    fun salvarResposta(perguntaId: Long, resposta: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("pergunta_id", perguntaId)
            put("resposta", resposta)
        }
        db.insert("respostas", null, values)
    }

    fun listarRespostasDetalhadas(): List<Map<String, String>> {
        val lista = mutableListOf<Map<String, String>>()
        val db = readableDatabase
        val query = """
            SELECT p.texto as pergunta, r.resposta, r.data_hora
            FROM respostas r
            JOIN perguntas p ON p.id = r.pergunta_id
            ORDER BY r.data_hora DESC
        """
        val cursor = db.rawQuery(query, null)

        if (cursor.moveToFirst()) {
            do {
                val item = mapOf(
                    "pergunta" to cursor.getString(cursor.getColumnIndexOrThrow("pergunta")),
                    "resposta" to cursor.getString(cursor.getColumnIndexOrThrow("resposta")),
                    "dataHora" to cursor.getString(cursor.getColumnIndexOrThrow("data_hora"))
                )
                lista.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return lista
    }

    fun limparTodasRespostas() {
        val db = writableDatabase
        db.delete("respostas", null, null)
    }

    fun buscarRespostasPorPergunta(): Map<String, Int> {
        val mapa = mutableMapOf<String, Int>()
        val db = readableDatabase
        val cursor = db.rawQuery("""
            SELECT p.texto, COUNT(r.id) as total 
            FROM respostas r 
            JOIN perguntas p ON p.id = r.pergunta_id 
            GROUP BY p.id
        """, null)

        if (cursor.moveToFirst()) {
            do {
                val texto = cursor.getString(0)
                val total = cursor.getInt(1)
                mapa[texto] = total
            } while (cursor.moveToNext())
        }
        cursor.close()
        return mapa
    }
}