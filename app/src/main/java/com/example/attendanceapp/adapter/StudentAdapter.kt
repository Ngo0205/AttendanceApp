package com.example.attendanceapp.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.attendanceapp.inter_face.OnStudentItemClickListener
import com.example.attendanceapp.R
import com.example.attendanceapp.model.Students
import com.example.attendanceapp.databinding.StudentItemBinding
import com.example.attendanceapp.model.Status
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

class StudentAdapter(
    private val studentList: List<Students>,
    private var selectedDate: String,
    private val listener: OnStudentItemClickListener,
    private val context: Context,
    private val onLongClickListener: (Int, View) -> Unit
) : RecyclerView.Adapter<StudentAdapter.StudentViewHolder>() {
    inner class StudentViewHolder(private val binding: StudentItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int) {
            val studentItem = studentList[position]
            binding.txtRoll.text = studentItem.roll.toString()
            binding.txtName.text = studentItem.name
            val list: ArrayList<Status> = ArrayList()
            val query: Query =
                FirebaseDatabase.getInstance().reference.child("status").orderByChild("uid")
                    .equalTo(studentItem.uid)
            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (snapItem in snapshot.children) {
                            snapItem.getValue(Status::class.java)?.let {
                                list.add(it)
                            }
                        }
                    }
                    for (statusItem in list) {
                        if (statusItem.date == selectedDate) {
                            if (statusItem.uid == studentItem.uid) {
                                binding.txtStatus.text = statusItem.status ?: ""
                                binding.cvStudent.setCardBackgroundColor(getColor(statusItem.status!!))
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
                }
            })

        }

        fun getColor(status: String): Int {
            if (status.equals("P")) {
                return ContextCompat.getColor(context, R.color.present)
            } else if (status.equals("A")) {
                return ContextCompat.getColor(context, R.color.absent)
            } else {
                return Color.TRANSPARENT
            }
        }


    }
    @SuppressLint("NotifyDataSetChanged")
    fun updateStatusWithTimeSelected(timeSelected: String){
        this.selectedDate = timeSelected
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val binding = StudentItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StudentViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return studentList.size
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(position)
        holder.itemView.setOnClickListener {
            listener.OnStudentItemClick(position)
        }
        holder.itemView.setOnLongClickListener {
            onLongClickListener(position, it)
            true
        }
    }
}