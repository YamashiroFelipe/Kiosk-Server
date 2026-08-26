package com.example.kioskserver

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SlideAdapter(
    private var itens: MutableList<Slide>,
    private val onEdit: (Slide) -> Unit,
    private val onDelete: (Slide) -> Unit
) : RecyclerView.Adapter<SlideAdapter.ViewHolder>() {

    class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(40, 32, 40, 32)
            gravity = Gravity.CENTER_VERTICAL

            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 16)
            layoutParams = params

            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#E2E8F0"))
            }
        }
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val context = holder.container.context
        holder.container.removeAllViews()

        val txtIcone = TextView(context).apply {
            text = if (item.tipo == TipoSlide.MIDIA) "📷" else "❓"
            textSize = 20f
            setPadding(0, 0, 24, 0)
        }

        // Exibe o título customizado se existir; caso contrário, usa o nome do arquivo
        val nomeExibicao = item.titulo?.ifEmpty { null }
            ?: item.uriMidia?.substringAfterLast("/")
            ?: "Arquivo de Mídia"

        val txtInfo = TextView(context).apply {
            text = if (item.tipo == TipoSlide.MIDIA) {
                "$nomeExibicao\n⏱️ ${item.tempoExibicaoSegundos}s | 🕒 ${item.horaInicio ?: "00:00"} às ${item.horaFim ?: "23:59"}"
            } else {
                "❓ ${item.titulo ?: "Pesquisa de Opinião"}\n⏱️ ${item.tempoExibicaoSegundos}s | 🕒 ${item.horaInicio ?: "00:00"} às ${item.horaFim ?: "23:59"}"
            }
            textSize = 13f
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val layoutBotoesItem = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnEdit = Button(context).apply {
            text = "✏️ Editar"
            setTextColor(Color.parseColor("#2563EB"))
            textSize = 11f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EFF6FF"))
                cornerRadius = 14f
            }
            setOnClickListener { onEdit(item) }
        }

        val btnDelete = Button(context).apply {
            text = "🗑️ Excluir"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 11f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FEF2F2"))
                cornerRadius = 14f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(12, 0, 0, 0) }

            setOnClickListener { onDelete(item) }
        }

        layoutBotoesItem.addView(btnEdit)
        layoutBotoesItem.addView(btnDelete)

        holder.container.addView(txtIcone)
        holder.container.addView(txtInfo)
        holder.container.addView(layoutBotoesItem)
    }

    override fun getItemCount(): Int = itens.size

    fun atualizar(novosItens: List<Slide>) {
        itens.clear()
        itens.addAll(novosItens)
        notifyDataSetChanged()
    }

    fun obterItem(position: Int): Slide = itens[position]

    fun notificarItemMovido(dePosicao: Int, paraPosicao: Int) {
        val itemMover = itens.removeAt(dePosicao)
        itens.add(paraPosicao, itemMover)
        notifyItemMoved(dePosicao, paraPosicao)
    }
}