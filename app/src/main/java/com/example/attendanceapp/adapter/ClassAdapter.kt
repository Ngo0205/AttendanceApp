package com.example.attendanceapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.attendanceapp.inter_face.OnClassItemClickListener
import com.example.attendanceapp.databinding.ClassItemBinding
import com.example.attendanceapp.model.ClassRoom

class ClassAdapter(
    private val classList: List<ClassRoom>,
    private val listener: OnClassItemClickListener,
    private val onLongClickListener: (Int, View) -> Unit
) : RecyclerView.Adapter<ClassAdapter.ClassViewHolder>() {
    inner class ClassViewHolder(private val binding: ClassItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(classItem: ClassRoom) {
            binding.txtClass.text = classItem.class_name
            binding.txtSubject.text = classItem.subject_name
            binding.root.setOnClickListener {
                listener.OnClassItemClick(classItem)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val binding = ClassItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClassViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return classList.size
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val classItem = classList[position]
        holder.bind(classItem)
        holder.itemView.setOnLongClickListener {
            onLongClickListener(position, it)
            true
        }
    }
}