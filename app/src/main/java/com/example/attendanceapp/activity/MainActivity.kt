package com.example.attendanceapp.activity


import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.attendanceapp.R
import com.example.attendanceapp.adapter.ClassAdapter

import com.example.attendanceapp.databinding.ActivityMainBinding
import com.example.attendanceapp.databinding.DialogCustomBinding
import com.example.attendanceapp.databinding.ToolbarBinding
import com.example.attendanceapp.inter_face.OnClassItemClickListener
import com.example.attendanceapp.model.ClassRoom
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity(), OnClassItemClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var classAdapter: ClassAdapter
    private lateinit var classList: ArrayList<ClassRoom>
    private lateinit var bindingToolbarBinding: ToolbarBinding
    private lateinit var dbRef: DatabaseReference
    private var selectedClassPosition: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolBar()
        classList = ArrayList()
        classAdapter = ClassAdapter(classList, this) { position, view ->
            selectedClassPosition = position
            binding.rlvClass.showContextMenuForChild(view)

        }
        binding.rlvClass.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = classAdapter
        }
        binding.btnAdd.setOnClickListener { showDialog() }
        getClassInFirebase()

        registerForContextMenu(binding.rlvClass)

    }
// cài đặt tool bar(thanh trên cùng) cho giao diện
    private fun setToolBar() {
        bindingToolbarBinding = ToolbarBinding.inflate(layoutInflater)

        bindingToolbarBinding.txtTitle.text = "Attendance App"
        bindingToolbarBinding.btnBack.visibility = View.GONE
        bindingToolbarBinding.btnSave.visibility = View.INVISIBLE
        bindingToolbarBinding.txtSubTitle.visibility = View.INVISIBLE

        binding.root.addView(bindingToolbarBinding.root)
    }
    // lấy dữ liệu trên firebase
    private fun getClassInFirebase() {
        dbRef = FirebaseDatabase.getInstance().getReference("class")
        dbRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                classList.clear()
                if (snapshot.exists()) {
                    for (snapItem in snapshot.children) {
                        snapItem.getValue(ClassRoom::class.java)?.let {
                            classList.add(it)
                            Log.d("class", it.class_name + it.subject_name)
                        }
                    }
                }
                classAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("err", error.message)
            }
        })
    }
// hiển thị dialog để insert
    @SuppressLint("NotifyDataSetChanged")
    private fun showDialog() {
        val dialogBinding = DialogCustomBinding.inflate(layoutInflater)

        val builder = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
        dialogBinding.txtTitle.text = "Add new class"
        dialogBinding.edt01.hint = "Class name..."
        dialogBinding.edt02.hint = "Subject name..."
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        alertDialog.show()
        dialogBinding.btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogBinding.btnAdd.setOnClickListener {
            val name = dialogBinding.edt01.text.trim().toString()
            val subject = dialogBinding.edt02.text.trim().toString()
            insertClass(name, subject)
            alertDialog.dismiss()
            classAdapter.notifyDataSetChanged()

        }

    }
// insert lên firebase và update lên màn hình
    private fun insertClass(name: String, subject: String) {
        dbRef = FirebaseDatabase.getInstance().getReference("class")
        val cid = dbRef.push().key!!
        val classInsert = ClassRoom(cid, name, subject)
        dbRef.child(cid).setValue(classInsert)
            .addOnCompleteListener {
                Log.d("class", "Insert class successfully")
            }.addOnFailureListener { err ->
                Log.d("class", "Fail: " + err.message)
            }
    }

    override fun onCreateContextMenu(
        menu: ContextMenu?,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        menuInflater.inflate(R.menu.context_menu, menu)
        super.onCreateContextMenu(menu, v, menuInfo)
    }
//xử lí các sự kiện khi nhấn giữ các item trong list
    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.edit -> {
                showDialogUpdate(selectedClassPosition)
                true
            }

            R.id.delete -> {
                deleteClass(selectedClassPosition)
                true
            }

            else -> super.onContextItemSelected(item)
        }

    }
// hiển thị dialog update khi người dùng bấm edit
    @SuppressLint("NotifyDataSetChanged")
    private fun showDialogUpdate(position: Int) {
        val dialogBinding = DialogCustomBinding.inflate(layoutInflater)
        val classItemUpdate = classList[position]
        val cid = classItemUpdate.cid
        val builder = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
        dialogBinding.txtTitle.text = "Update Class"
        dialogBinding.edt01.hint = "Enter Class Name ....."
        dialogBinding.edt02.hint = "Enter Subject Name ....."
        dialogBinding.edt01.setText(classItemUpdate.class_name)
        dialogBinding.edt02.setText(classItemUpdate.subject_name)
        dialogBinding.btnAdd.text = "Update"
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        alertDialog.show()

        dialogBinding.btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }


        dialogBinding.btnAdd.setOnClickListener {
            dbRef = FirebaseDatabase.getInstance().getReference("class")

            val name = dialogBinding.edt01.text.trim().toString()
            val subject = dialogBinding.edt02.text.trim().toString()
            val newClass = ClassRoom(cid, name, subject)
            dbRef.child(cid!!).setValue(newClass)
                .addOnCompleteListener {
                    Log.d("class", "Update class successfully")
                    classList[position].class_name = name
                    classList[position].subject_name = subject
                }.addOnFailureListener { err ->
                    Log.d("class", "Fail: " + err.message)
                }
            alertDialog.dismiss()
            classAdapter.notifyItemChanged(position)

        }
    }
// xử lí khi người dùng bấm delete
    private fun deleteClass(position: Int) {
        val classItem = classList[position]
        val cid = classItem.cid

        dbRef.child(cid!!).removeValue()
            .addOnCompleteListener {
                Log.d("class", "Delete class successfully")
            }
            .addOnFailureListener { err ->
                Log.d("class", "Fail: " + err.message)
            }
        val studentRef = dbRef.child(cid).child("students")
        studentRef.removeValue()
            .addOnCompleteListener {
                Log.d("class", "Delete all students of class succesfully")
            }
            .addOnFailureListener { err ->
                Log.d("class", "Fail: " + err.message)
            }

        classList.removeAt(position)
        classAdapter.notifyItemRemoved(position)
    }



// xử lí khi người dùng nhấp vào lớp trong list
    override fun OnClassItemClick(classRoom: ClassRoom) {
        val intent = Intent(this, StudentActivity::class.java).apply {
            putExtra("cid", classRoom.cid)
            putExtra("className", classRoom.class_name)
            putExtra("subjectName", classRoom.subject_name)
        }
        startActivity(intent)
    }

}