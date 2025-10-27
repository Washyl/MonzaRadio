
package com.monza.radio.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class FavoritesAdapter(private val onClick: (Float) -> Unit)
    : ListAdapter<Float, FavoritesAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Float>() {
            override fun areItemsTheSame(oldItem: Float, newItem: Float) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Float, newItem: Float) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val freq = getItem(position)
        holder.bind(freq)
    }

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val tv = view.findViewById<TextView>(android.R.id.text1)
        fun bind(freq: Float) {
            tv.text = String.format("%.1f MHz", freq)
            itemView.setOnClickListener { onClick(freq) }
        }
    }
}
