package com.monza.radio.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.monza.radio.R

class FavoritesAdapter(private val onClick: (Float) -> Unit)
    : ListAdapter<Float, FavoritesAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Float>() {
            override fun areItemsTheSame(oldItem: Float, newItem: Float) = oldItem == newItem
            override fun areContentsTheSame(oldItem: Float, newItem: Float) = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite_chip, parent, false)
        return VH(v as TextView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val freq = getItem(position)
        holder.bind(freq)
    }

    inner class VH(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(freq: Float) {
            tv.text = String.format("%.1f MHz", freq)
            tv.setOnClickListener { onClick(freq) }
        }
    }
}
