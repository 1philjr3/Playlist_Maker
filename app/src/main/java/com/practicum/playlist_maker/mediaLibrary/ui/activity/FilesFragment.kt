//package com.practicum.playlist_maker.mediaLibrary.ui.activity
//
//import android.content.Intent
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import com.practicum.playlist_maker.R
//import com.practicum.playlist_maker.mediaLibrary.model.File
//import com.practicum.playlist_maker.mediaLibrary.ui.FilesAdapter
//import com.practicum.playlist_maker.mediaLibrary.ui.view_model.FilesFragmentViewModel
//import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity
//import com.google.android.material.floatingactionbutton.FloatingActionButton
//import android.app.Activity.RESULT_OK
//
//class FilesFragment : Fragment() {
//
//    private val viewModel: FilesFragmentViewModel by viewModels() // Используем Koin для внедрения
//    private lateinit var adapter: FilesAdapter
//
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        val view = inflater.inflate(R.layout.fragment_files, container, false)
//        val recyclerView = view.findViewById<RecyclerView>(R.id.rvFiles)
//        recyclerView.layoutManager = LinearLayoutManager(requireContext())
//        adapter = FilesAdapter()
//        recyclerView.adapter = adapter
//
//        // Навигация к PdfReaderActivity при выборе PDF-файла
//        adapter.onItemClickListener = { file ->
//            if (file.isPdf) {
//                val intent = Intent(requireContext(), PdfReaderActivity::class.java).apply {
//                    putExtra("pdfFilePath", file.filePath)
//                    putExtra("pdfTitle", file.name)
//                }
//                startActivity(intent)
//            }
//        }
//
//        return view
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        val directoryId = arguments?.getString("directoryId")
//        if (directoryId != null) {
//            viewModel.loadFiles(directoryId)
//        }
//        viewModel.files.observe(viewLifecycleOwner) { files ->
//            adapter.submitList(files)
//        }
//
//        // Кнопка для добавления нового файла
//        view.findViewById<FloatingActionButton>(R.id.fabAddFile).setOnClickListener {
//            showAddFileDialog(directoryId ?: "")
//        }
//    }
//
//    private fun showAddFileDialog(directoryId: String) {
//        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
//            type = "application/pdf"
//        }
//        startActivityForResult(intent, REQUEST_CODE_PICK_PDF)
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CODE_PICK_PDF && resultCode == RESULT_OK) {
//            val uri = data?.data
//            if (uri != null) {
//                val file = File(
//                    id = "generated_id", // Генерируйте уникальный ID или используйте что-то вроде UUID.randomUUID().toString()
//                    name = uri.lastPathSegment ?: "unknown_file", // Имя файла из URI
//                    filePath = uri.toString(), // Полный путь к файлу
//                    isPdf = true // Предполагаем, что файл PDF
//                )
//                val directoryId = arguments?.getString("directoryId")
//                if (directoryId != null) {
//                    viewModel.uploadFile(file, directoryId,
//                        onSuccess = { fileUrl ->
//                            // Файл успешно загружен, можно сохранить fileUrl в Firestore
//                        },
//                        onFailure = { exception ->
//                            // Обработка ошибки загрузки файла
//                        }
//                    )
//                }
//            }
//        }
//    }
//
//    companion object {
//        private const val REQUEST_CODE_PICK_PDF = 1001
//    }
//}