package com.example.kioskserver

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: SlideAdapter
    private lateinit var txtStatus: TextView
    private var servidorRodando = false
    private lateinit var btnToggleServer: Button

    private val selecionarMidiaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uriOriginal ->
            try {
                try {
                    contentResolver.takePersistableUriPermission(
                        uriOriginal,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignora caso a URI não suporte permissão persistente
                }

                val caminhoLocal = copiarUriParaArquivoLocal(uriOriginal)

                if (caminhoLocal != null) {
                    exibirDialogTempoSlide(caminhoLocal)
                } else {
                    Toast.makeText(this, "Erro ao copiar arquivo de mídia", Toast.LENGTH_LONG).show()
                }
            } catch (ex: Exception) {
                Toast.makeText(this, "Falha ao processar mídia: ${ex.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private var perguntaEmCriacaoTexto: String? = null

    private val selecionarFotoPesquisaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val texto = perguntaEmCriacaoTexto
        if (uri != null && texto != null) {
            val caminhoLocal = copiarUriParaArquivoLocal(uri)
            if (caminhoLocal != null) {
                val idPergunta = dbHelper.adicionarPergunta(texto)
                dbHelper.adicionarSlidePesquisa(idPergunta, uriMidia = caminhoLocal)
                carregarSlides()
                Toast.makeText(this, "Pesquisa com foto criada!", Toast.LENGTH_SHORT).show()
            }
        }
        perguntaEmCriacaoTexto = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = DatabaseHelper(this)

        // Fundo da tela principal em cinza moderno
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        // Card de Status do Servidor
        txtStatus = TextView(this).apply {
            text = "🔴 Servidor Desligado\nIP: http://${obterIpWifi()}:8080"
            textSize = 15f
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 20f
                setStroke(2, Color.parseColor("#E2E8F0"))
            }
        }

        // Botão Principal (Ligar / Desligar Servidor Dinâmico)
        btnToggleServer = Button(this).apply {
            text = "🟢 Ligar Servidor"
            setTextColor(Color.WHITE)
            textSize = 15f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = 20f
            }
            setOnClickListener {
                if (!servidorRodando) {
                    val serviceIntent = Intent(this@MainActivity, ServerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    servidorRodando = true
                    text = "🔴 Desligar Servidor"
                    (background as GradientDrawable).setColor(Color.parseColor("#EF4444"))
                    txtStatus.text = "🟢 Servidor ATIVO em:\nhttp://${obterIpWifi()}:8080"
                    Toast.makeText(this@MainActivity, "Servidor em execução!", Toast.LENGTH_SHORT).show()
                } else {
                    val serviceIntent = Intent(this@MainActivity, ServerService::class.java).apply {
                        action = "PARAR"
                    }
                    startService(serviceIntent)
                    servidorRodando = false
                    text = "🟢 Ligar Servidor"
                    (background as GradientDrawable).setColor(Color.parseColor("#2563EB"))
                    txtStatus.text = "🔴 Servidor Desligado\nIP: http://${obterIpWifi()}:8080"
                    Toast.makeText(this@MainActivity, "Servidor desligado!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val layoutBotoes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 24)
        }

        // Função interna para padronizar os botões secundários em código
        fun criarBotaoSecundario(texto: String): Button {
            return Button(this).apply {
                text = texto
                textSize = 13f
                setTextColor(Color.parseColor("#0F172A"))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 16f
                    setStroke(2, Color.parseColor("#CBD5E1"))
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(8, 0, 8, 0)
                }
            }
        }

        val btnAddMidia = criarBotaoSecundario("+ Mídia").apply {
            setOnClickListener { selecionarMidiaLauncher.launch("*/*") }
        }

        val btnAddPesquisa = criarBotaoSecundario("+ Pergunta").apply {
            setOnClickListener { exibirDialogNovaPergunta() }
        }

        val btnVerRespostas = criarBotaoSecundario("📊 Relatório").apply {
            setOnClickListener { exibirDialogRespostas() }
        }

        layoutBotoes.addView(btnAddMidia)
        layoutBotoes.addView(btnAddPesquisa)
        layoutBotoes.addView(btnVerRespostas)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Configuração do Adapter com suporte a Edição e Remoção
        adapter = SlideAdapter(
            itens = mutableListOf(),
            onEdit = { slideParaEditar ->
                exibirDialogEdicaoSlide(slideParaEditar)
            },
            onDelete = { slideExcluir ->
                // 1. Apaga o arquivo físico da memória se for uma mídia local
                slideExcluir.uriMidia?.let { caminho ->
                    try {
                        val arquivo = java.io.File(caminho)
                        if (arquivo.exists()) {
                            arquivo.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 2. Remove o registro do banco de dados SQLite
                dbHelper.removerSlide(slideExcluir.id)

                // 3. Atualiza a lista na tela e avisa o usuário
                carregarSlides()
                Toast.makeText(this, "Mídia e arquivo removidos com sucesso!", Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.adapter = adapter

        // --- SUPORTE A ARRASTAR E SOLTAR (DRAG & DROP) ---
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val dePosicao = viewHolder.adapterPosition
                val paraPosicao = target.adapterPosition

                if (dePosicao != RecyclerView.NO_POSITION && paraPosicao != RecyclerView.NO_POSITION) {
                    val moverParaCima = dePosicao > paraPosicao
                    val slideMover = adapter.obterItem(dePosicao)

                    // Atualiza o banco de dados e move o item na lista
                    dbHelper.moverSlide(slideMover.id, moverParaCima)
                    adapter.notificarItemMovido(dePosicao, paraPosicao)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Não utilizado
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
        // -----------------------------------------------------

        rootLayout.addView(txtStatus)
        rootLayout.addView(btnToggleServer)
        rootLayout.addView(layoutBotoes)
        rootLayout.addView(recyclerView)

        setContentView(rootLayout)
        carregarSlides()
    }

    private fun exibirDialogTempoSlide(caminhoLocal: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // 1. Campo de Nome / Identificação da Mídia
        val txtTituloLabel = TextView(this).apply {
            text = "🏷️ Nome/Título da Mídia:"
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 0, 0, 8)
        }
        val inputTitulo = EditText(this).apply {
            hint = "Ex: Promoção do Almoço"
            setText(caminhoLocal.substringAfterLast("/"))
        }

        // 2. Campo de Tempo de Exibição
        val txtTempoLabel = TextView(this).apply {
            text = "\n⏱️ Duração na Tela (Segundos):"
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 8, 0, 8)
        }
        val inputTempo = EditText(this).apply {
            hint = "Ex: 10"
            setText("10")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        // 3. Seleção de Dias da Semana
        val txtDiasLabel = TextView(this).apply {
            text = "\n📅 Dias de Exibição:"
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 8, 0, 8)
        }

        val diasNomes = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
        val diasValores = arrayOf("1", "2", "3", "4", "5", "6", "7")
        val checkboxesDias = mutableListOf<CheckBox>()

        val layoutGridDias = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (i in diasNomes.indices) {
            val cb = CheckBox(this).apply {
                text = diasNomes[i]
                isChecked = true
                textSize = 11f
                setPadding(0, 0, 8, 0)
            }
            checkboxesDias.add(cb)
            layoutGridDias.addView(cb)
        }

        // 4. Janela de Horário
        val txtHorarioLabel = TextView(this).apply {
            text = "\n🕒 Horário de Exibição (HH:MM):"
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 8, 0, 8)
        }

        val layoutHorarios = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val inputHoraInicio = EditText(this).apply {
            hint = "00:00"
            setText("00:00")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val txtAte = TextView(this).apply { text = " até "; setPadding(8, 16, 8, 0) }
        val inputHoraFim = EditText(this).apply {
            hint = "23:59"
            setText("23:59")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        layoutHorarios.addView(inputHoraInicio)
        layoutHorarios.addView(txtAte)
        layoutHorarios.addView(inputHoraFim)

        layout.addView(txtTituloLabel)
        layout.addView(inputTitulo)
        layout.addView(txtTempoLabel)
        layout.addView(inputTempo)
        layout.addView(txtDiasLabel)
        layout.addView(layoutGridDias)
        layout.addView(txtHorarioLabel)
        layout.addView(layoutHorarios)

        android.app.AlertDialog.Builder(this)
            .setTitle("⚙️ Agendar Nova Mídia")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Salvar Mídia") { _, _ ->
                val tituloPersonalizado = inputTitulo.text.toString().trim()
                val tempoSegundos = inputTempo.text.toString().trim().toIntOrNull() ?: 10

                val diasSelecionados = mutableListOf<String>()
                for (i in checkboxesDias.indices) {
                    if (checkboxesDias[i].isChecked) {
                        diasSelecionados.add(diasValores[i])
                    }
                }
                val strDias = if (diasSelecionados.isNotEmpty()) diasSelecionados.joinToString(",") else "1,2,3,4,5,6,7"

                var horaInicio = inputHoraInicio.text.toString().trim()
                var horaFim = inputHoraFim.text.toString().trim()

                if (horaInicio.isEmpty()) horaInicio = "00:00"
                if (horaFim.isEmpty()) horaFim = "23:59"

                dbHelper.adicionarSlideMidia(
                    uri = caminhoLocal,
                    tempoSegundos = tempoSegundos,
                    diasSemana = strDias,
                    horaInicio = horaInicio,
                    horaFim = horaFim,
                    titulo = if (tituloPersonalizado.isNotEmpty()) tituloPersonalizado else null
                )

                carregarSlides()
                Toast.makeText(this, "Mídia agendada com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun exibirDialogEdicaoSlide(slide: Slide) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val txtTituloLabel = TextView(this).apply { text = "🏷️ Nome/Identificação da Mídia:" }
        val inputTitulo = EditText(this).apply {
            hint = "Ex: Promoção X-Tudo Almoço"
            setText(slide.titulo ?: slide.uriMidia?.substringAfterLast("/") ?: "")
        }

        val txtTempoLabel = TextView(this).apply { text = "\n⏱️ Duração na Tela (Segundos):" }
        val inputTempo = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(slide.tempoExibicaoSegundos.toString())
        }

        val txtDiasLabel = TextView(this).apply { text = "\n📅 Dias de Exibição:" }
        val diasNomes = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
        val diasValores = arrayOf("1", "2", "3", "4", "5", "6", "7")
        val checkboxesDias = mutableListOf<CheckBox>()
        val layoutGridDias = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val diasAtuais = slide.diasSemana?.split(",") ?: listOf("1", "2", "3", "4", "5", "6", "7")

        for (i in diasNomes.indices) {
            val cb = CheckBox(this).apply {
                text = diasNomes[i]
                isChecked = diasAtuais.contains(diasValores[i])
                textSize = 11f
            }
            checkboxesDias.add(cb)
            layoutGridDias.addView(cb)
        }

        val txtHorarioLabel = TextView(this).apply { text = "\n🕒 Horário (HH:MM):" }
        val layoutHorarios = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val inputHoraInicio = EditText(this).apply {
            setText(slide.horaInicio ?: "00:00")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val txtAte = TextView(this).apply { text = " até "; setPadding(8, 16, 8, 0) }
        val inputHoraFim = EditText(this).apply {
            setText(slide.horaFim ?: "23:59")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        layoutHorarios.addView(inputHoraInicio)
        layoutHorarios.addView(txtAte)
        layoutHorarios.addView(inputHoraFim)

        layout.addView(txtTituloLabel)
        layout.addView(inputTitulo)
        layout.addView(txtTempoLabel)
        layout.addView(inputTempo)
        layout.addView(txtDiasLabel)
        layout.addView(layoutGridDias)
        layout.addView(txtHorarioLabel)
        layout.addView(layoutHorarios)

        android.app.AlertDialog.Builder(this)
            .setTitle("✏️ Editar Slide")
            .setView(layout)
            .setPositiveButton("Salvar Alterações") { _, _ ->
                val novoTitulo = inputTitulo.text.toString().trim()
                val tempoSegundos = inputTempo.text.toString().toIntOrNull() ?: 10

                val diasSelecionados = mutableListOf<String>()
                for (i in checkboxesDias.indices) {
                    if (checkboxesDias[i].isChecked) {
                        diasSelecionados.add(diasValores[i])
                    }
                }
                val strDias = if (diasSelecionados.isNotEmpty()) diasSelecionados.joinToString(",") else "1,2,3,4,5,6,7"

                val horaInicio = inputHoraInicio.text.toString().ifEmpty { "00:00" }
                val horaFim = inputHoraFim.text.toString().ifEmpty { "23:59" }

                dbHelper.atualizarSlide(
                    id = slide.id,
                    titulo = novoTitulo,
                    tempoSegundos = tempoSegundos,
                    diasSemana = strDias,
                    horaInicio = horaInicio,
                    horaFim = horaFim
                )

                carregarSlides()
                Toast.makeText(this, "Slide atualizado!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun copiarUriParaArquivoLocal(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val extensao = contentResolver.getType(uri)?.substringAfter("/") ?: "jpg"
            val arquivoDestino = java.io.File(filesDir, "midia_${System.currentTimeMillis()}.$extensao")

            inputStream.use { input ->
                arquivoDestino.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            arquivoDestino.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun carregarSlides() {
        val slidesDoBanco = dbHelper.listarSlides()

        val listaSlides = slidesDoBanco.map { map ->
            val tipoStr = map["tipo"] as? String ?: "MIDIA"
            val tipoEnum = if (tipoStr == "PESQUISA") TipoSlide.PESQUISA else TipoSlide.MIDIA

            Slide(
                titulo = map["titulo"] as? String,
                id = (map["id"] as? Number)?.toLong() ?: 0L,
                tipo = tipoEnum,
                uriMidia = map["uriMidia"] as? String ?: map["uri_midia"] as? String ?: map["url_ou_conteudo"] as? String ?: "",
                tempoExibicaoSegundos = (map["tempoExibicaoSegundos"] as? Number)?.toInt()
                    ?: (map["tempo_segundos"] as? Number)?.toInt()
                    ?: (map["tempo"] as? Number)?.toInt()
                    ?: 10,
                perguntaId = (map["perguntaId"] as? Number)?.toLong() ?: (map["pergunta_id"] as? Number)?.toLong(),
                ativo = (map["ativo"] as? Number)?.toInt() ?: 1,
                diasSemana = map["diasSemana"] as? String ?: map["dias_semana"] as? String ?: "1,2,3,4,5,6,7",
                horaInicio = map["horaInicio"] as? String ?: map["hora_inicio"] as? String ?: "00:00",
                horaFim = map["horaFim"] as? String ?: map["hora_fim"] as? String ?: "23:59"
            )
        }

        adapter.atualizar(listaSlides)
    }

    private fun exibirDialogRespostas() {
        val respostas = dbHelper.listarRespostasDetalhadas()

        if (respostas.isEmpty()) {
            Toast.makeText(this, "Nenhuma resposta registrada ainda.", Toast.LENGTH_SHORT).show()
            return
        }

        val stringBuilder = StringBuilder()
        respostas.forEach { item ->
            stringBuilder.append("❓ ${item["pergunta"]}\n")
            stringBuilder.append("⭐ Resposta: ${item["resposta"]}\n")
            stringBuilder.append("📅 Data: ${item["dataHora"]}\n")
            stringBuilder.append("-----------------------------------\n")
        }

        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            text = stringBuilder.toString()
            setPadding(32, 24, 32, 24)
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        scrollView.addView(textView)

        android.app.AlertDialog.Builder(this)
            .setTitle("📊 Relatório de Respostas (${respostas.size})")
            .setView(scrollView)
            .setPositiveButton("Fechar", null)
            .setNegativeButton("Exportar / WhatsApp") { _, _ ->
                exportarECompartilharRespostas()
            }
            .setNeutralButton("Limpar Histórico") { _, _ ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("Atenção")
                    .setMessage("Deseja realmente apagar todas as respostas salvas?")
                    .setPositiveButton("Sim, apagar") { _, _ ->
                        dbHelper.limparTodasRespostas()
                        Toast.makeText(this, "Histórico apagado com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .show()
    }

    private fun exportarECompartilharRespostas() {
        val respostas = dbHelper.listarRespostasDetalhadas()

        if (respostas.isEmpty()) {
            Toast.makeText(this, "Nenhuma resposta registrada para exportar.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvHeader = "Pergunta,Resposta,Data e Hora\n"
            val csvBody = StringBuilder()

            respostas.forEach { item ->
                val pergunta = item["pergunta"]?.replace("\"", "\"\"") ?: ""
                val resposta = item["resposta"]?.replace("\"", "\"\"") ?: ""
                val dataHora = item["dataHora"] ?: ""

                csvBody.append("\"$pergunta\",\"$resposta\",\"$dataHora\"\n")
            }

            val conteudoCompleto = csvHeader + csvBody.toString()

            val arquivoCsv = java.io.File(cacheDir, "relatorio_respostas_kiosk.csv")
            arquivoCsv.writeText(conteudoCompleto)

            val contentUri: Uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                arquivoCsv
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Relatório de Respostas - Kiosk Server")
                putExtra(Intent.EXTRA_TEXT, "Segue em anexo o relatório das pesquisas coletadas no totem.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartilhar Relatório via...")
            val resInfoList = packageManager.queryIntentActivities(chooser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao exportar relatório: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun salvarRelatorioNaPastaDownloads() {
        val respostas = dbHelper.listarRespostasDetalhadas()

        if (respostas.isEmpty()) {
            Toast.makeText(this, "Nenhuma resposta para salvar.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val csvHeader = "Pergunta,Resposta,Data e Hora\n"
            val csvBody = StringBuilder()

            respostas.forEach { item ->
                val pergunta = item["pergunta"]?.replace("\"", "\"\"") ?: ""
                val resposta = item["resposta"]?.replace("\"", "\"\"") ?: ""
                val dataHora = item["dataHora"] ?: ""
                csvBody.append("\"$pergunta\",\"$resposta\",\"$dataHora\"\n")
            }

            val conteudoCompleto = csvHeader + csvBody.toString()

            val pastaDownloads = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )

            val nomeArquivo = "Relatorio_Kiosk_${System.currentTimeMillis()}.csv"
            val arquivoFinal = java.io.File(pastaDownloads, nomeArquivo)

            arquivoFinal.writeText(conteudoCompleto)

            Toast.makeText(this, "Salvo em Downloads:\n$nomeArquivo", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exibirDialogNovaPergunta() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val inputPergunta = EditText(this).apply { hint = "Ex: Como foi seu atendimento?" }
        layout.addView(inputPergunta)

        android.app.AlertDialog.Builder(this)
            .setTitle("Nova Pergunta de Pesquisa")
            .setView(layout)
            .setPositiveButton("Salvar com Foto") { _, _ ->
                val texto = inputPergunta.text.toString().trim()
                if (texto.isNotEmpty()) {
                    perguntaEmCriacaoTexto = texto
                    selecionarFotoPesquisaLauncher.launch("image/*")
                }
            }
            .setNegativeButton("Salvar sem Foto") { _, _ ->
                val texto = inputPergunta.text.toString().trim()
                if (texto.isNotEmpty()) {
                    val idPergunta = dbHelper.adicionarPergunta(texto)
                    dbHelper.adicionarSlidePesquisa(idPergunta, uriMidia = null)
                    carregarSlides()
                }
            }
            .show()
    }

    private fun obterIpWifi(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        var ipAddress = wifiManager.connectionInfo.ipAddress
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress)
        }
        val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
        return try {
            InetAddress.getByAddress(ipByteArray).hostAddress ?: "127.0.0.1"
        } catch (ex: Exception) {
            "Não Conectado"
        }
    }
}