package com.example.attendanceapp.activity

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.attendanceapp.R
import com.example.attendanceapp.model.Students
import com.example.attendanceapp.adapter.StudentAdapter
import com.example.attendanceapp.databinding.ActivityStudentBinding
import com.example.attendanceapp.databinding.DialogCustomBinding
import com.example.attendanceapp.databinding.ToolbarBinding
import com.example.attendanceapp.inter_face.OnStudentItemClickListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import org.apache.poi.hssf.usermodel.HSSFWorkbook

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import com.example.attendanceapp.model.Status
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

@Suppress("DEPRECATION")
class StudentActivity : AppCompatActivity(), OnStudentItemClickListener {
    private lateinit var binding: ActivityStudentBinding
    private lateinit var bindingToolbarBinding: ToolbarBinding
    private lateinit var studentAdapter: StudentAdapter
    private lateinit var studentList: ArrayList<Students>
    private lateinit var statusList: ArrayList<Status>
    private lateinit var dbRef: DatabaseReference
    private lateinit var statusRef: DatabaseReference
    private lateinit var attendanceSheet: MutableMap<String, MutableMap<String, String>>
    private var className: String? = null
    private var subjectName: String? = null
    private var cid: String? = null
    private var selectedStudentPosition: Int = -1
    private var calendar = Calendar.getInstance()
    private var dateSelected = ""
    private var documentUri: Uri? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        studentList = ArrayList()
        statusList = ArrayList()
        attendanceSheet = mutableMapOf()
        dateSelected = LocalDate.now().toString()
        statusRef = FirebaseDatabase.getInstance().getReference("status")

        className = intent?.getStringExtra("className").toString()
        subjectName = intent?.getStringExtra("subjectName").toString()
        cid = intent?.getStringExtra("cid").toString()
        setToolBar()

        studentAdapter = StudentAdapter(studentList, dateSelected, this, this) { position, view ->
            selectedStudentPosition = position
            binding.rlvStudent.showContextMenuForChild(view)
        }
        binding.rlvStudent.apply {
            layoutManager = LinearLayoutManager(this@StudentActivity)
            adapter = studentAdapter
        }
        fetchStudent()

        fetchStatus()

        registerForContextMenu(binding.rlvStudent)

    }

    //cài đặt tool bar cho màn hình
    @SuppressLint("SetTextI18n")
    private fun setToolBar() {
        bindingToolbarBinding = ToolbarBinding.inflate(layoutInflater)

        bindingToolbarBinding.txtTitle.text = className
        bindingToolbarBinding.txtSubTitle.text = "$subjectName | $dateSelected"

        bindingToolbarBinding.btnSave.visibility = View.INVISIBLE

        bindingToolbarBinding.btnBack.setOnClickListener { onBackPressed() }
        setSupportActionBar(bindingToolbarBinding.toolbar)
        binding.root.addView(bindingToolbarBinding.root)
    }

    //cài đặt menu option
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.student_menu, menu)
        return true
    }

    // xử lí sự kiện khi chọn item trên menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add_student -> {
                showDialog()
                true
            }

            R.id.date_time -> {
                changeDate()
                true
            }

            R.id.import_xls -> {
                documentPicker.launch(null)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    // hàm thay đổi ngày thaáng
    private fun changeDate() {
        val initialYear = calendar.get(Calendar.YEAR)
        val initialMonth = calendar.get(Calendar.MONTH)
        val initialDay = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dateSelected = dateFormat.format(calendar.time)
                updateTitle(dateSelected)
                studentAdapter.updateStatusWithTimeSelected(dateSelected)
            },
            initialYear, initialMonth, initialDay
        )
        datePicker.show()

    }

    private fun updateTitle(dateSelected: String) {
        bindingToolbarBinding.txtSubTitle.text = "$subjectName | $dateSelected"
    }

    // xử lí khi người dùng export file xml điểm danh
    private fun generateXlsFile() {
        documentUri?.let { uri ->
            try {
                val wb = HSSFWorkbook()
                val sheet: Sheet = wb.createSheet("Student Record")
                var rowIndex = 0

                val headerRow: Row = sheet.createRow(rowIndex++)
                headerRow.createCell(0).setCellValue("ID")
                headerRow.createCell(1).setCellValue("Name")

                val date = attendanceSheet.keys.sorted()
                for ((i, dateItem) in date.withIndex()) {
                    headerRow.createCell(2 + i).setCellValue(dateItem)
                }

                for (student in studentList) {
                    val status =
                        statusList.find { it.uid == student.uid && it.date == dateSelected }?.status
                            ?: "N/A"
                    val row: Row = sheet.createRow(rowIndex++)
                    row.createCell(0).setCellValue(student.roll)
                    row.createCell(1).setCellValue(student.name)
                    for ((i, dateItem) in date.withIndex()) {
                        val statusItem = attendanceSheet[dateItem]?.get(student.uid) ?: "N/A"
                        row.createCell(2 + i).setCellValue(statusItem)
                    }
                }


                val fileName =
                    "Student_Record_" + className + "_" + subjectName + "_" + dateSelected + ".xls"

                val documentFileUri = createDocument(uri, fileName)
                documentFileUri?.let { docUri ->
                    val outputStream: OutputStream? = contentResolver.openOutputStream(docUri)
                    outputStream.use { wb.write(it) }
                    Toast.makeText(this, "File saved to $docUri", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createDocument(uri: Uri, fileName: String): Uri? {
        return try {
            val treeUri = DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            DocumentsContract.createDocument(
                contentResolver,
                treeUri,
                "application/vnd.ms-excel",
                fileName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private val documentPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                documentUri = it
                Log.d("XlsExportActivity", "Selected URI: $uri")
                generateXlsFile()
            }
        }

    //show dialog để thêm học sinh
    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    private fun showDialog() {

        val dialogBinding = DialogCustomBinding.inflate(layoutInflater)

        val builder = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
        dialogBinding.txtTitle.text = "Add Student"
        dialogBinding.edt01.hint = "Enter ID Student..."
        dialogBinding.edt02.hint = "Enter Name Student..."
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()
        dialogBinding.btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogBinding.btnAdd.setOnClickListener {
            val roll = dialogBinding.edt01.text.toString().trim()
            val name = dialogBinding.edt02.text.trim().toString()
            saveStudents(roll, name)
            dialogBinding.edt01.setText((roll.toInt() + 1).toString())
            dialogBinding.edt02.setText("")
            alertDialog.dismiss()
            studentAdapter.notifyDataSetChanged()


        }
    }

    //lưu thông tin học sinh lên firebase và update view
    @SuppressLint("NotifyDataSetChanged")
    private fun saveStudents(roll: String, name: String) {
        dbRef = FirebaseDatabase.getInstance().getReference("students")
        val uid = dbRef.push().key!!
        val studentSave = Students(uid, cid, roll, name)
        dbRef.child(uid).setValue(studentSave)
            .addOnCompleteListener {
                studentList.add(studentSave)
                studentAdapter.notifyDataSetChanged()
                Log.d("student", "Insert student successfully")
            }.addOnFailureListener { err ->
                Log.d("student", "Fail: " + err.message)
            }

        val sid = FirebaseDatabase.getInstance().getReference("status").push().key!!
        val today = LocalDate.now().toString()
        val statusStudent = Status(sid, uid, today, "")
        FirebaseDatabase.getInstance().getReference("status").child(sid).setValue(statusStudent)
            .addOnCompleteListener {
                studentAdapter.notifyDataSetChanged()
                Log.d("status", "Insert status successfully")
            }.addOnFailureListener { err ->
                Log.d("status", "Fail: " + err.message)
            }

    }

    //thay đổi trạng thái của sinh viên
    override fun OnStudentItemClick(position: Int) {
        changeStatus(position)
    }

    @SuppressLint("NewApi", "NotifyDataSetChanged")
    private fun changeStatus(position: Int) {
        dbRef = FirebaseDatabase.getInstance().reference
        val curStudent = studentList[position]
        Toast.makeText(this, curStudent.name, Toast.LENGTH_SHORT).show()
        val list: ArrayList<Status> = ArrayList()
        val query: Query = dbRef.child("status").orderByChild("uid").equalTo(curStudent.uid)
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
                    if (statusItem.date == dateSelected) {
                        statusItem.status = if (statusItem.status == "P") "A" else "P"
                        statusItem.sid?.let { sid ->
                            // Sử dụng sid để cập nhật trạng thái
                            dbRef.child("status").child(sid).setValue(statusItem)
                                .addOnCompleteListener {
                                    // Cập nhật lại giao diện sau khi thay đổi trạng thái
                                    studentAdapter.notifyItemChanged(position)
                                    Log.d("status", "Update status successfully")
                                }.addOnFailureListener { err ->
                                    Log.d("status", "Failed to update status: ${err.message}")
                                    // Hiển thị thông báo lỗi nếu cần
                                }
                        }
                    }
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("status", "Database error: ${error.message}")
            }
        })


    }

    //tạo context menu trên item list
    override fun onCreateContextMenu(
        menu: ContextMenu?,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        menuInflater.inflate(R.menu.context_menu, menu)
        super.onCreateContextMenu(menu, v, menuInfo)
    }

    // xử lí khi người dùng chọn item trên context menu
    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.edit -> {
                showDialogUpdate(selectedStudentPosition)
                true

            }

            R.id.delete -> {
                deleteStudent(selectedStudentPosition)
                true
            }

            else -> super.onContextItemSelected(item)
        }

    }

    // xoá student khi chọn
    private fun deleteStudent(position: Int) {
        val studentItemDelete = studentList[position]
        val uid = studentItemDelete.uid

        dbRef = FirebaseDatabase.getInstance().getReference("students")

        dbRef.child(uid!!).removeValue()
            .addOnCompleteListener {
                Log.d("student", "Delete student successfully")
            }.addOnFailureListener { err ->
                Log.d("student", "Fail: " + err.message)
            }
        studentList.removeAt(position)
        studentAdapter.notifyItemRemoved(position)

    }

    // update student và update lên firebase
    private fun showDialogUpdate(position: Int) {
        val studentItemUpdate = studentList[position]
        val uid = studentItemUpdate.uid

        dbRef = FirebaseDatabase.getInstance().getReference("students")
        val dialogBinding = DialogCustomBinding.inflate(layoutInflater)

        val builder = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
        dialogBinding.txtTitle.text = "Update Student"
        dialogBinding.edt01.hint = "Enter ID Student..."
        dialogBinding.edt02.hint = "Enter Name Student..."
        dialogBinding.btnAdd.text = "Update"
        dialogBinding.edt01.setText(studentItemUpdate.roll)
        dialogBinding.edt02.setText(studentItemUpdate.name)
        val alertDialog = builder.create()
        alertDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        alertDialog.show()

        dialogBinding.btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogBinding.btnAdd.setOnClickListener {
            val name = dialogBinding.edt02.text.toString().trim()
            val roll = dialogBinding.edt01.text.toString().trim()
            val newStudent = Students(uid, studentItemUpdate.cid, roll, name)
            dbRef.child(uid!!).setValue(newStudent)
                .addOnCompleteListener {
                    Log.d("student", "Update student successfully")
                    studentList[position].name = name
                    studentList[position].roll = roll
                }.addOnFailureListener { err ->
                    Log.d("student", "Fail" + err.message)
                }
            alertDialog.dismiss()
            studentAdapter.notifyItemChanged(position)
        }

    }

    // khởi tạo và lấy dữ danh sách sinh viên thuộc lớp đã chọn
    private fun fetchStudent() {
        dbRef = FirebaseDatabase.getInstance().reference
        val query: Query = dbRef.child("students").orderByChild("cid").equalTo(cid)
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                studentList.clear()
                if (snapshot.exists()) {
                    for (snapItem in snapshot.children) {
                        snapItem.getValue(Students::class.java)?.let {
                            studentList.add(it)
                            Log.d("student", it.name!!)
                            ensureStatusEntryForToday(it)
                        }
                    }
                } else {
                    Log.d("student", "No data found for class ID: $cid")
                }
                studentAdapter.notifyDataSetChanged()

            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("err", error.message)
            }
        })
    }

    // khởi tạo và lấy dữ liệu điểm danh(nếu có)
    private fun fetchStatus() {
        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                statusList.clear()
                for (data in snapshot.children) {
                    val status = data.getValue(Status::class.java)
                    status?.let {
                        val date = it.date ?: ""
                        val uid = it.uid ?: ""
                        val status = it.status ?: ""
                        if (!attendanceSheet.containsKey(date)) {
                            attendanceSheet[date] = mutableMapOf()
                        }
                        attendanceSheet[date]?.set(uid, status)
                        statusList.add(it)


                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // chưa có dữ liệu điểm danh thì sẽ khởi tạo mới điểm danh theo ngày đó
    private fun ensureStatusEntryForToday(student: Students) {
        val today = dateSelected
        val query: Query = dbRef.child("status").orderByChild("uid").equalTo(student.uid)

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var statusForTodayExists = false

                if (snapshot.exists()) {
                    for (snapItem in snapshot.children) {
                        snapItem.getValue(Status::class.java)?.let {
                            if (it.date == today) {
                                statusForTodayExists = true
                            }
                        }
                    }
                }

                if (!statusForTodayExists) {
                    // Add new status entry for today if it doesn't exist
                    val sid = dbRef.child("status").push().key!!
                    val newStatus = Status(sid, student.uid, today, "")
                    dbRef.child("status").child(sid).setValue(newStatus)
                        .addOnCompleteListener {
                            Log.d("status", "New status added for ${student.name} on $today")
                        }.addOnFailureListener { err ->
                            Log.d("status", "Failed to add new status: ${err.message}")
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d("status", "Database error: ${error.message}")
            }
        })
    }

}