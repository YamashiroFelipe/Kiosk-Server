package com.example.kioskserver

enum class TipoSlide { MIDIA, PESQUISA }

data class Slide(
    val id: Long = 0,
    val tipo: TipoSlide,
    val uriMidia: String? = null, // Caminho da foto/vídeo
    val perguntaId: Long? = null,  // ID da pergunta vinculada (se for PESQUISA)
    var ordem: Int = 0,
    val tempoExibicaoSegundos: Int = 10,
    val ativo: Int = 1,
    val diasSemana: String? = "1,2,3,4,5,6,7",
    val horaInicio: String? = "00:00",
    val horaFim: String? = "23:59",
    val titulo: String? = null // Adicionado para dar nome à mídia
)

data class Pergunta(
    val id: Long = 0,
    val textoPergunta: String,
    val tipoResposta: String = "ESTRELAS", // "ESTRELAS", "SIM_NAO", "EMOJI"
    var ordem: Int = 0
)

data class RespostaPesquisa(
    val id: Long = 0,
    val perguntaId: Long,
    val resposta: String,
    val dataHora: String
)