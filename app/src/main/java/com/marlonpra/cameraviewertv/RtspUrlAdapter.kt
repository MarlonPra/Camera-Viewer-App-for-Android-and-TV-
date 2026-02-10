package com.marlonpra.cameraviewertv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RtspUrlAdapter(
    private val onClick: (String) -> Unit,
    private val onDelete: (String) -> Unit,
) : RecyclerView.Adapter<RtspUrlAdapter.Vh>() {

    private val items = ArrayList<String>()

    fun submit(urls: List<String>) {
        items.clear()
        items.addAll(urls)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_rtsp, parent, false)
        return Vh(v)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        val url = items[position]
        holder.text.text = url
        holder.itemView.setOnClickListener { onClick(url) }
        holder.text.setOnClickListener { onClick(url) }
        holder.deleteButton.setOnClickListener { onDelete(url) }
    }

    override fun getItemCount(): Int = items.size

    class Vh(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.urlText)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
    }
}
