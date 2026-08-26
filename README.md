# 📺 Kiosk Server & Digital Signage Player

Uma solução moderna, leve e **100% offline-first** para transformar qualquer Smart TV ou Android Box em um painel corporativo de mídia (Digital Signage) e totem de pesquisa de satisfação (NPS).

O projeto é dividido em dois módulos que trabalham em perfeita sintonia dentro da rede local:
* **Server App (Android):** Gerenciador com servidor web embarcado em Ktor (CIO), banco de dados SQLite local, agendador semanal/por horário e gerenciamento de arquivos.
* **Client App (HTML5/JS):** Player responsivo que roda no navegador da Smart TV, com transição suave de mídias, widget de relógio/clima e fluxo interativo de pesquisas.

---

## 🚀 Principais Funcionalidades

- **100% Offline & Local:** Funciona sem dependência de nuvem ou mensalidades. Os dados e mídias ficam salvos no próprio dispositivo Android.
- **Agendamento Inteligente:** Programe mídias por dias da semana específicos (ex: Seg-Sex) e intervalos de horário (ex: 11:00 às 15:00).
- **Suporte Multi-Mídia:** Exibição de imagens e vídeos com detecção automática de término de reprodução.
- **Pesquisas de Satisfação (NPS):** Exiba telas de avaliação interativas com captura de respostas locais.
- **Relatório & Exportação CSV:** Visualização das respostas no app e exportação em `.csv` via compartilhamento (WhatsApp, E-mail, Drive).
- **Reorganização Drag & Drop:** Reordene a sequência dos slides arrastando os itens na tela principal.
- **Limpeza Automática:** Remoção física dos arquivos de imagem/vídeo do armazenamento ao excluir uma mídia da lista.

---

## 🛠️ Arquitetura e Tecnologias

- **Servidor Android:** Kotlin, Ktor Server (Engine CIO), SQLite (`SQLiteOpenHelper`), Foreground Service.
- **Player Web:** HTML5, CSS3 moderno (Backdrop-filter, CSS Grid/Flexbox) e JavaScript Puro (ES6+).
- **Comunicação:** API REST local (endpoints `/api/config`, `/api/pesquisa`, `/api/download-csv`).

---

## 📦 Como Executar o Projeto

1. Clone o repositório:
   ```bash
   git clone [https://github.com/YamashiroFelipe/Kiosk-Server.git](https://github.com/YamashiroFelipe/Kiosk-Server.git)

   Abra a pasta server no Android Studio e compile o aplicativo no dispositivo Android que atuará como servidor.

Certifique-se de que o dispositivo Android e a Smart TV estão conectados na mesma rede local (Wi-Fi ou Ethernet).

Abra o aplicativo no Android e clique em Ligar Servidor.

No navegador da Smart TV, acesse o IP exibido na tela do app (exemplo: http://192.168.1.15:8080).

☕ Apoie o Projeto (Donation)
Se este projeto foi útil para o seu negócio, economizou custos com mensalidades de software de TV corporativa ou te ajudou nos seus estudos, considere fazer uma doação para apoiar o desenvolvimento e manutenção contínua!

Pix: fac9e28b-8f88-4942-a98a-db866dd78ef3

📜 Licença
Este projeto está sob a licença MIT - veja o arquivo LICENSE para mais detalhes. Desenvolvido por Felipe Yamashiro.
