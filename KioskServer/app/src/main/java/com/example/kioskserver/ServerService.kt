package com.example.kioskserver

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

class ServerService : Service() {

    private var server: ApplicationEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Se a intent pedir para PARAR, encerra o servidor Ktor e o serviço de primeiro plano
        if (intent?.action == "PARAR") {
            pararServidorKtor()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        iniciarNotificacaoForeground()
        iniciarServidorKtor()
        return START_STICKY
    }

    private fun iniciarServidorKtor() {
        if (server == null) {
            val dbHelper = DatabaseHelper(this)

            garantirIndexHtmlLocal()

            try {
                server = embeddedServer(CIO, port = 8080) {
                    install(CORS) {
                        anyHost()
                        allowHeader(HttpHeaders.ContentType)
                    }
                    install(ContentNegotiation) {
                        gson()
                    }
                    routing {
                        // 1. Servir o index.html na raiz
                        staticFiles("/", File(filesDir.absolutePath)) {
                            default("index.html")
                        }

                        // 2. Servir as mídias salvas
                        staticFiles("/midia", File(filesDir.absolutePath))

                        // 3. Endpoint de Configuração dos Slides
                        get("/api/config") {
                            try {
                                val slides = dbHelper.listarSlidesAtivosParaAgora()

                                val slidesTratados = slides.map { slide ->
                                    val map = slide.toMutableMap()
                                    val uriOriginal = map["uriMidia"] as? String ?: ""

                                    if (uriOriginal.isNotEmpty()) {
                                        val nomeArquivo = File(uriOriginal).name
                                        map["urlPublica"] = "/midia/$nomeArquivo"
                                    }
                                    map
                                }

                                call.respond(mapOf("itens" to slidesTratados))
                            } catch (e: Exception) {
                                Log.e("KtorServer", "Erro ao listar slides", e)
                                call.respond(HttpStatusCode.InternalServerError, mapOf("erro" to e.message))
                            }
                        }

                        // 4. Endpoint de Download do CSV
                        get("/api/download-csv") {
                            try {
                                val respostas = dbHelper.listarRespostasDetalhadas()
                                val csvHeader = "Pergunta,Resposta,Data e Hora\n"
                                val csvBody = StringBuilder()

                                respostas.forEach { item ->
                                    val pergunta = item["pergunta"]?.replace("\"", "\"\"") ?: ""
                                    val resposta = item["resposta"]?.replace("\"", "\"\"") ?: ""
                                    val dataHora = item["dataHora"] ?: ""
                                    csvBody.append("\"$pergunta\",\"$resposta\",\"$dataHora\"\n")
                                }

                                val conteudoCsv = csvHeader + csvBody.toString()

                                call.response.header(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment.withParameter(
                                        ContentDisposition.Parameters.FileName, "relatorio_kiosk.csv"
                                    ).toString()
                                )

                                call.respondText(conteudoCsv, ContentType.Text.CSV)
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.InternalServerError, mapOf("erro" to e.message))
                            }
                        }

                        // 5. Endpoint de Registro de Pesquisa
                        post("/api/pesquisa") {
                            try {
                                val params = call.receive<Map<String, String>>()
                                val perguntaId = params["perguntaId"]?.toLongOrNull()
                                val resposta = params["resposta"]

                                if (perguntaId != null && resposta != null) {
                                    dbHelper.salvarResposta(perguntaId, resposta)
                                    call.respond(HttpStatusCode.OK, mapOf("status" to "sucesso"))
                                } else {
                                    call.respond(HttpStatusCode.BadRequest, mapOf("status" to "dados_invalidos"))
                                }
                            } catch (e: Exception) {
                                call.respond(HttpStatusCode.InternalServerError, mapOf("erro" to e.message))
                            }
                        }
                    }
                }

                server?.start(wait = false)
                Log.d("KtorServer", "Servidor Ktor iniciado com sucesso na porta 8080")
            } catch (e: Exception) {
                Log.e("KtorServer", "Erro ao iniciar servidor Ktor", e)
            }
        }
    }

    private fun pararServidorKtor() {
        try {
            server?.stop(500, 1000)
            server = null
            Log.d("KtorServer", "Servidor Ktor parado com sucesso.")
        } catch (e: Exception) {
            Log.e("KtorServer", "Erro ao parar servidor Ktor", e)
        }
    }

    private fun garantirIndexHtmlLocal() {
        val arquivoIndex = File(filesDir, "index.html")

        val conteudoHtml = """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Kiosk Slides & Pesquisas</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
    body, html { width: 100%; height: 100%; overflow: hidden; font-family: 'Segoe UI', Roboto, sans-serif; background: #000; }
    
    /* Widget de Hora no Canto Superior Direito */
    .weather-widget {
      position: absolute;
      top: 20px;
      right: 20px;
      background: rgba(15, 23, 42, 0.75);
      backdrop-filter: blur(8px);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: white;
      padding: 8px 16px;
      border-radius: 30px;
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 1rem;
      font-weight: 600;
      z-index: 1000;
      box-shadow: 0 4px 15px rgba(0, 0, 0, 0.4);
    }
    
    .player-container {
      position: relative;
      width: 100vw;
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #000;
      overflow: hidden;
    }
    
    .media-wrapper {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      overflow: hidden;
    }

    .slide-media { 
      position: absolute;
      top: 0; left: 0;
      width: 100%; height: 100%;
      opacity: 0;
      transition: opacity 0.8s ease-in-out;
      pointer-events: none;
      box-shadow: 0 0 20px rgba(0,0,0,0.8);
    }

    .slide-media.active { 
      opacity: 1;
      pointer-events: auto;
    }

    .slide-media.contain-mode { object-fit: contain; }
    .slide-media.cover-mode { object-fit: cover; }
    .slide-media.clickable { cursor: pointer; }

    .player-controls {
      position: absolute;
      bottom: 25px;
      left: 50%;
      transform: translateX(-50%) translateZ(0);
      -webkit-transform: translateX(-50%) translateZ(0);
      background: rgba(15, 23, 42, 0.85);
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
      border: 1px solid rgba(255, 255, 255, 0.2);
      padding: 10px 20px;
      border-radius: 40px;
      display: flex;
      align-items: center;
      gap: 18px;
      z-index: 999;
      opacity: 0.9;
      transition: opacity 0.3s ease;
      box-shadow: 0 10px 25px rgba(0,0,0,0.5);
    }

    .player-controls.hidden-controls {
      opacity: 0;
      pointer-events: none;
    }

    .ctrl-btn {
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: white;
      font-size: 20px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: 50%;
      transition: background 0.2s, transform 0.1s;
    }
    .ctrl-btn:active { 
      background: rgba(37, 99, 235, 0.8);
      transform: scale(0.92);
    }

    .modal { 
      display: none; 
      position: fixed; 
      top: 0; left: 0; 
      width: 100vw; height: 100vh; 
      background: rgba(0, 0, 0, 0.85); 
      align-items: flex-start; 
      justify-content: center; 
      z-index: 9999; 
      backdrop-filter: blur(6px); 
      padding: 10px;
      overflow-y: auto;
    }
    .modal.open { display: flex; }
    
    .modal-content { 
      background: #ffffff; 
      padding: 20px 25px; 
      border-radius: 16px; 
      text-align: center; 
      max-width: 500px; 
      width: 100%; 
      color: #111827; 
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
      display: flex;
      flex-direction: column;
      align-items: center;
      margin: 10px auto;
    }

    body.keyboard-open .kiosk-logo { display: none !important; }
    body.keyboard-open .modal-content { padding: 12px 20px; }

    .kiosk-logo { max-width: 120px; max-height: 50px; object-fit: contain; margin-bottom: 8px; }
    .modal-header { width: 100%; margin-bottom: 10px; }
    .step-indicator { font-size: 12px; font-weight: 600; color: #2563eb; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 3px; }
    .modal-content h2 { font-size: 18px; font-weight: 700; color: #1f2937; }
    
    .options-container { display: flex; flex-direction: column; gap: 10px; margin-bottom: 15px; width: 100%; max-height: 35vh; overflow-y: auto; }
    .option-btn { padding: 12px 18px; border: 2px solid #2563eb; background: #f8fafc; color: #2563eb; border-radius: 10px; font-weight: 700; font-size: 16px; cursor: pointer; transition: all 0.2s; text-align: center; }
    .option-btn:hover, .option-btn:active { background: #2563eb; color: #ffffff; }

    .actions-container { display: flex; gap: 10px; width: 100%; }
    .btn-close { background: #ef4444; color: white; border: none; padding: 12px 16px; font-size: 14px; border-radius: 8px; cursor: pointer; font-weight: bold; flex: 1; }

    .thanks-box { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 20px 10px; }
    .thanks-box .icon { font-size: 48px; margin-bottom: 10px; }
    .thanks-box h3 { font-size: 22px; color: #16a34a; margin-bottom: 5px; }
    .thanks-box p { font-size: 14px; color: #4b5563; }
  </style>
</head>
<body>

  <div class="player-container" onclick="toggleVisibilidadeControles(event)">
    <div class="media-wrapper" id="carousel">
      <p style="color: white;">Carregando player...</p>
    </div>
    
    <div class="weather-widget" id="weatherWidget">
      <span id="clockDisplay">00:00</span>
    </div>

    <div class="player-controls" id="playerControls" onclick="event.stopPropagation()">
      <button class="ctrl-btn" onclick="slideAnterior()" title="Anterior">⏮️</button>
      <button class="ctrl-btn" id="btnPlayPause" onclick="togglePlayPause()" title="Pausar/Play">⏸️</button>
      <button class="ctrl-btn" onclick="proximoSlide()" title="Próximo">⏭️</button>
      <button class="ctrl-btn" onclick="toggleFitMode()" title="Alternar Ajuste de Tela">🔍</button>
    </div>
  </div>

  <div class="modal" id="pesquisaModal">
    <div class="modal-content" id="modalContent">
      <img src="logo.png" alt="Logo" class="kiosk-logo" onerror="this.style.display='none'">
      
      <div id="painelPerguntas" style="width: 100%;">
        <div class="modal-header">
          <div id="stepIndicator" class="step-indicator">Pesquisa de Opinião</div>
          <h2 id="perguntaTitulo">Pergunta...</h2>
        </div>
        
        <div id="respostasContainer" class="options-container"></div>
        
        <div class="actions-container">
          <button class="btn-close" onclick="fecharModal()">Cancelar</button>
        </div>
      </div>

      <div id="painelAgradecimento" class="thanks-box" style="display: none;">
        <div class="icon">✅</div>
        <h3>Obrigado!</h3>
        <p>Sua resposta foi registrada com sucesso.</p>
      </div>

    </div>
  </div>

  <script>
    let slides = [];
    let currentSlideIndex = 0;
    let slideTimer = null;
    let currentSlideData = null;
    let isPaused = false;
    let fitMode = 'contain-mode';

    async function carregarSlides() {
      try {
        const response = await fetch('/api/config');
        const data = await response.json();
        const novosSlides = data.itens || [];

        if (novosSlides.length > 0) {
          slides = novosSlides;
          renderizarSlides();
          iniciarCarrossel();
        } else {
          document.getElementById('carousel').innerHTML = '<p style="color: white;">Nenhum slide cadastrado para este horário.</p>';
        }
      } catch (err) {
        console.error('Erro ao carregar slides do Ktor:', err);
      }
    }

    function renderizarSlides() {
      const container = document.getElementById('carousel');
      container.innerHTML = '';

      slides.forEach((slide, index) => {
        let el;
        
        if (slide.tipo === 'MIDIA' && (slide.urlPublica || slide.uriMidia)) {
          const urlArquivo = slide.urlPublica || ('/midia/' + slide.uriMidia.split('/').pop());
          const nomeArquivo = urlArquivo.toLowerCase();
          const ehVideo = nomeArquivo.endsWith('.mp4') || nomeArquivo.endsWith('.webm');

          if (ehVideo) {
            el = document.createElement('video');
            el.src = urlArquivo;
            el.muted = true;
            el.playsInline = true;
            el.setAttribute('playsinline', '');
          } else {
            el = document.createElement('img');
            el.src = urlArquivo;
          }
        } else if (slide.tipo === 'PESQUISA') {
          el = document.createElement('div');
          el.className = 'slide-media clickable';
          
          const urlFoto = slide.urlPublica || (slide.uriMidia ? ('/midia/' + slide.uriMidia.split('/').pop()) : null);

          if (urlFoto) {
            el.style.cssText = "background: linear-gradient(rgba(0,0,0,0.4), rgba(0,0,0,0.7)), url('" + urlFoto + "') center/cover no-repeat; width: 100vw; height: 100vh; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; cursor: pointer;";
          } else {
            el.style.cssText = "background: #1e1e24; width: 100vw; height: 100vh; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; cursor: pointer;";
          }

          const titulo = slide.textoPergunta || 'Sua opinião é importante!';
          el.innerHTML = '<div style="background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(8px); padding: 30px 40px; border-radius: 20px; border: 1px solid rgba(255,255,255,0.2); max-width: 80%;">' +
            '<h1 style="font-size: 2.2rem; color: white; margin-bottom: 15px;">' + titulo + '</h1>' +
            '<button style="background: #2563eb; color: white; border: none; padding: 14px 28px; font-size: 1.2rem; font-weight: bold; border-radius: 30px; cursor: pointer; box-shadow: 0 4px 15px rgba(37,99,235,0.4);">' +
            '⭐ Responder Pesquisa</button></div>';

          el.onclick = (e) => {
            e.stopPropagation();
            iniciarFluxoPesquisa(slide);
          };
        }

        if (el) {
          el.className = 'slide-media ' + fitMode + (index === currentSlideIndex ? ' active' : '');
          container.appendChild(el);
        }
      });
    }

    function iniciarCarrossel() {
      if (slideTimer) clearTimeout(slideTimer);
      if (slides.length === 0 || isPaused) return;
      
      const slideAtual = slides[currentSlideIndex];
      const slidesElements = document.querySelectorAll('.slide-media');
      const elAtual = slidesElements[currentSlideIndex];

      if (elAtual && elAtual.tagName === 'VIDEO') {
        elAtual.currentTime = 0;
        elAtual.play().catch(err => console.log('Autoplay:', err));
        
        elAtual.onended = () => {
          if (!isPaused) proximoSlide();
        };
      } else {
        const tempoSegundos = slideAtual.tempoExibicaoSegundos || slideAtual.tempo || 10;
        const tempoExibicao = tempoSegundos * 1000;
        
        slideTimer = setTimeout(() => {
          if (!isPaused) proximoSlide();
        }, tempoExibicao);
      }
    }

    function proximoSlide() {
      const proximoIndex = (currentSlideIndex + 1) % slides.length;

      if (proximoIndex === 0) {
        atualizarEContinuar();
      } else {
        mudarSlide(proximoIndex);
      }
    }

    async function atualizarEContinuar() {
      try {
        const response = await fetch('/api/config');
        const data = await response.json();
        
        if (data.itens && data.itens.length > 0) {
          slides = data.itens;
          renderizarSlides();
        }
      } catch (err) {
        console.log('Sem conexão no momento, mantendo lista atual...');
      }
      mudarSlide(0);
    }

    function slideAnterior() {
      mudarSlide((currentSlideIndex - 1 + slides.length) % slides.length);
    }

    function mudarSlide(novoIndex) {
      const elements = document.querySelectorAll('.slide-media');
      if (elements.length === 0) return;

      const elAntigo = elements[currentSlideIndex];
      if (elAntigo && elAntigo.tagName === 'VIDEO') {
        elAntigo.pause();
        elAntigo.onended = null;
      }

      if (elAntigo) elAntigo.classList.remove('active');

      currentSlideIndex = novoIndex;
      const elNovo = elements[currentSlideIndex];

      if (elNovo) {
        if (elNovo.tagName === 'IMG') {
          if (elNovo.complete) {
            elNovo.classList.add('active');
          } else {
            elNovo.onload = () => elNovo.classList.add('active');
          }
        } else if (elNovo.tagName === 'VIDEO') {
          if (elNovo.readyState >= 3) {
            elNovo.classList.add('active');
          } else {
            elNovo.oncanplay = () => elNovo.classList.add('active');
          }
        } else {
          elNovo.classList.add('active');
        }
      }

      iniciarCarrossel();
    }

    function togglePlayPause() {
      isPaused = !isPaused;
      const btn = document.getElementById('btnPlayPause');
      btn.innerText = isPaused ? '▶️' : '⏸️';

      const elements = document.querySelectorAll('.slide-media');
      const elAtual = elements[currentSlideIndex];

      if (isPaused) {
        if (slideTimer) clearTimeout(slideTimer);
        if (elAtual && elAtual.tagName === 'VIDEO') elAtual.pause();
      } else {
        if (elAtual && elAtual.tagName === 'VIDEO') elAtual.play();
        else iniciarCarrossel();
      }
    }

    function toggleFitMode() {
      fitMode = (fitMode === 'contain-mode') ? 'cover-mode' : 'contain-mode';
      const elements = document.querySelectorAll('.slide-media');
      elements.forEach(el => {
        el.classList.remove('contain-mode', 'cover-mode');
        el.classList.add(fitMode);
      });
    }

    function toggleVisibilidadeControles(e) {
      const controls = document.getElementById('playerControls');
      controls.classList.toggle('hidden-controls');
    }

    function atualizarRelogio() {
      const agora = new Date();
      const horas = String(agora.getHours()).padStart(2, '0');
      const minutos = String(agora.getMinutes()).padStart(2, '0');
      document.getElementById('clockDisplay').innerText = `${'$'}{horas}:${'$'}{minutos}`;
    }
    setInterval(atualizarRelogio, 1000);
    atualizarRelogio();

    function iniciarFluxoPesquisa(slide) {
      currentSlideData = slide;
      if (slideTimer) clearTimeout(slideTimer);

      document.getElementById('painelPerguntas').style.display = 'block';
      document.getElementById('painelAgradecimento').style.display = 'none';
      document.getElementById('pesquisaModal').classList.add('open');
      
      document.getElementById('perguntaTitulo').innerText = slide.textoPergunta || "Como avalia nosso atendimento?";

      const containerOpcoes = document.getElementById('respostasContainer');
      containerOpcoes.innerHTML = '';

      const opcoes = ['1 ⭐', '2 ⭐', '3 ⭐', '4 ⭐', '5 ⭐'];
      opcoes.forEach(opcao => {
        const btn = document.createElement('button');
        btn.className = 'option-btn';
        btn.innerText = opcao;
        btn.onclick = () => enviarRespostaApi(opcao);
        containerOpcoes.appendChild(btn);
      });
    }

    async function enviarRespostaApi(resposta) {
      try {
        if (window.AndroidClient && typeof window.AndroidClient.salvarRespostaLocal === 'function') {
          window.AndroidClient.salvarRespostaLocal(String(currentSlideData.perguntaId), resposta);
        } else {
          await fetch('/api/pesquisa', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              perguntaId: String(currentSlideData.perguntaId),
              resposta: resposta
            })
          });
        }

        document.getElementById('painelPerguntas').style.display = 'none';
        document.getElementById('painelAgradecimento').style.display = 'flex';
        
        setTimeout(() => {
          fecharModal();
        }, 1500);
      } catch (err) {
        console.error('Erro ao registrar resposta:', err);
      }
    }

    function fecharModal() {
      document.getElementById('pesquisaModal').classList.remove('open');
      iniciarCarrossel();
    }

    carregarSlides();
  </script>
</body>
</html>
    """.trimIndent()

        arquivoIndex.writeText(conteudoHtml)
    }

    private fun iniciarNotificacaoForeground() {
        val channelId = "ServerChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Servidor de Mídia Kiosk",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Servidor Kiosk Rodando")
            .setContentText("Disponível na rede local na porta 8080")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        pararServidorKtor()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}