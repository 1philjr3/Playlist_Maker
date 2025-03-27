//package com.practicum.playlist_maker.ui.fragment
//
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageButton
//import android.widget.TextView
//import androidx.fragment.app.Fragment
//import androidx.recyclerview.widget.LinearLayoutManager
//import com.practicum.playlist_maker.R
//import com.practicum.playlist_maker.data.model.Directory
//import com.practicum.playlist_maker.mediaLibrary.ui.adapter.DirectoryAdapter
//import com.google.android.material.floatingactionbutton.FloatingActionButton
//import androidx.recyclerview.widget.RecyclerView
//import com.google.android.material.dialog.MaterialAlertDialogBuilder
//import android.widget.EditText
//import android.widget.Toast
//import okhttp3.*
//import okhttp3.MediaType.Companion.toMediaType
//import org.json.JSONObject
//import java.io.IOException
//
//class DirectoryContentFragment : Fragment() {
//    private lateinit var recyclerView: RecyclerView
//    private lateinit var fabAddDirectory: FloatingActionButton
//    private lateinit var btnBack: ImageButton
//    private lateinit var tvPath: TextView
//    private lateinit var directoryAdapter: DirectoryAdapter
//    private var currentDirectory: Directory? = null
//    private var pathHistory: MutableList<Directory> = mutableListOf()
//    private val client = OkHttpClient()
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        return inflater.inflate(R.layout.fragment_directory_content, container, false)
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        recyclerView = view.findViewById(R.id.rvDirectories)
//        fabAddDirectory = view.findViewById(R.id.fabAddDirectory)
//        btnBack = view.findViewById(R.id.btnBack)
//        tvPath = view.findViewById(R.id.tvPath)
//
//        setupRecyclerView()
//        setupFab()
//        setupBackButton()
//        loadDirectories()
//    }
//
//    private fun setupRecyclerView() {
//        directoryAdapter = DirectoryAdapter { directory ->
//            navigateToDirectory(directory)
//        }
//        recyclerView.apply {
//            layoutManager = LinearLayoutManager(context)
//            adapter = directoryAdapter
//        }
//    }
//
//    private fun setupFab() {
//        fabAddDirectory.setOnClickListener {
//            showAddDirectoryDialog()
//        }
//    }
//
//    private fun setupBackButton() {
//        btnBack.setOnClickListener {
//            navigateBack()
//        }
//    }
//
//    private fun navigateBack() {
//        if (pathHistory.isNotEmpty()) {
//            pathHistory.removeLast()
//            currentDirectory = pathHistory.lastOrNull()
//            updatePathDisplay()
//            loadDirectories()
//        }
//    }
//
//    private fun navigateToDirectory(directory: Directory) {
//        currentDirectory = directory
//        pathHistory.add(directory)
//        updatePathDisplay()
//        loadDirectories()
//    }
//
//    private fun updatePathDisplay() {
//        val path = pathHistory.joinToString(" / ") { it.name }
//        tvPath.text = path
//        btnBack.visibility = if (pathHistory.isNotEmpty()) View.VISIBLE else View.GONE
//    }
//
//    private fun showAddDirectoryDialog() {
//        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_directory, null)
//        val nameInput = dialogView.findViewById<EditText>(R.id.etDirectoryName)
//        val descriptionInput = dialogView.findViewById<EditText>(R.id.etDirectoryDescription)
//
//        MaterialAlertDialogBuilder(requireContext())
//            .setTitle("Добавить директорию")
//            .setView(dialogView)
//            .setPositiveButton("Добавить") { dialog, _ ->
//                val name = nameInput.text.toString()
//                val description = descriptionInput.text.toString()
//                if (name.isNotEmpty() && description.isNotEmpty()) {
//                    createDirectory(name, description)
//                }
//                dialog.dismiss()
//            }
//            .setNegativeButton("Отмена") { dialog, _ ->
//                dialog.dismiss()
//            }
//            .show()
//    }
//
//    private fun createDirectory(name: String, description: String) {
//        val imageUrl = "http://10.0.2.2:3000/uploads/default.jpg"
//
//        val json = JSONObject().apply {
//            put("name", name)
//            put("description", description)
//            put("imageUrl", imageUrl)
//            put("parentId", currentDirectory?.id)
//        }
//
//        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
//        val request = Request.Builder()
//            .url("http://10.0.2.2:3000/directories")
//            .post(requestBody)
//            .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                activity?.runOnUiThread {
//                    Toast.makeText(context, "Ошибка при создании директории", Toast.LENGTH_SHORT).show()
//                }
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                if (response.isSuccessful) {
//                    activity?.runOnUiThread {
//                        loadDirectories()
//                    }
//                }
//            }
//        })
//    }
//
//    private fun loadDirectories() {
//        val url = if (currentDirectory == null) {
//            "http://10.0.2.2:3000/directories/root"
//        } else {
//            "http://10.0.2.2:3000/directories?parentId=${currentDirectory?.id}"
//        }
//
//        val request = Request.Builder()
//            .url(url)
//            .get()
//            .build()
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                activity?.runOnUiThread {
//                    Toast.makeText(context, "Ошибка при загрузке директорий", Toast.LENGTH_SHORT).show()
//                }
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                if (response.isSuccessful) {
//                    val directories = response.body?.string()?.let { json ->
//                        // Здесь должна быть десериализация JSON в список Directory
//                        // Для примера возвращаем пустой список
//                        emptyList<Directory>()
//                    } ?: emptyList()
//
//                    activity?.runOnUiThread {
//                        directoryAdapter.submitList(directories)
//                    }
//                }
//            }
//        })
//    }
//}