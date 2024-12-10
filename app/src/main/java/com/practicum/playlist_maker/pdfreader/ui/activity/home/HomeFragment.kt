package com.practicum.playlist_maker.pdfreader.ui.activity.home

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.FragmentHomeBinding
import com.practicum.playlist_maker.pdfreader.adapter.PdfListAdapter
import com.practicum.playlist_maker.pdfreader.model.PdfNoteListModel
import com.practicum.playlist_maker.pdfreader.model.PdfNotesResponse
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.ui.activity.add.AddPdfActivity
import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity
import com.practicum.playlist_maker.pdfreader.ui.fragment.MoreOptionModel
import com.practicum.playlist_maker.pdfreader.ui.fragment.OptionPickFragment
import com.practicum.playlist_maker.pdfreader.utils.BundleArguments
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment(), View.OnClickListener, OptionPickFragment.Listener, PdfListAdapter.Listener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private var adapter: PdfListAdapter? = null
    private val pdfList = arrayListOf<PdfNoteListModel>()
    private val pdfListAll = arrayListOf<PdfNoteListModel>()

    companion object {
        const val FROM_GALLERY = 1
        const val DOWNLOAD_PDF = 2

        fun newInstance() = HomeFragment()
    }

    private val addPdfLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                if (it.data?.action == AddPdfActivity.RESULT_ACTION_PDF_ADDED) {
                    viewModel.getAllPdfs()
                }
            }
        }

    private val pdfReaderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            viewModel.getAllPdfs()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInsets()
        initView()
        observeViewModel()
        viewModel.getAllPdfs()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

//    private fun initView() {
//        binding.btAddNewPdf.setOnClickListener(this)
//        adapter = PdfListAdapter(pdfList, this)
//        binding.rvPdfList.apply {
//            adapter = this@HomeFragment.adapter
//            layoutManager = LinearLayoutManager(requireContext())
//        }
//    }

    private fun initView() {
        binding.btAddNewPdf.setOnClickListener(this)
        adapter = PdfListAdapter(pdfList, this)
        binding.rvPdfList.apply {
            adapter = this@HomeFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun observeViewModel() {
        viewModel.pdfListResponse.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ResponseState.Failed -> {
                    binding.progressBar.visibility = View.GONE
                    setError(state.error)
                }

                ResponseState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }

                is ResponseState.Success<*> -> {
                    binding.progressBar.visibility = View.GONE
                    val pdfListResponse = state.response as? PdfNotesResponse
                    if (pdfListResponse != null) {
                        pdfListAll.clear()
                        pdfListAll.addAll(pdfListResponse.notes)
                        pdfList.clear()
                        pdfList.addAll(pdfListAll)
                        adapter?.notifyDataSetChanged()
                    }

                    // Вызов метода checkItemCount для управления отображением
                    checkItemCount()
                }

                is ResponseState.ValidationError -> Unit
            }
        }
    }

    private fun checkItemCount() {
        if (pdfList.isEmpty()) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = getString(R.string.you_don_t_have_any_pdf_notes_yet)
        } else {
            binding.tvError.visibility = View.GONE
        }
    }



//    override fun onClick(v: View?) {
//        when (v?.id) {
//            R.id.btAddNewPdf -> {
//                val intent = Intent(requireContext(), AddPdfActivity::class.java)
//                intent.putExtra("key", "value")
//                startActivity(intent)
//            }
//        }
//    }
//    override fun onClick(v: View?) {
//    when (v?.id) {
//        R.id.btAddNewPdf -> {
//            showAddOptions() // Display options to the user
//        }
//    }
//}

//    override fun onClick(v: View?) {
//        when (v?.id) {
//            R.id.btAddNewPdf -> showAddOptions()
//        }
//    }
//
//    private fun showAddOptions() {
//        val options = arrayListOf(
//            MoreOptionModel(FROM_GALLERY, "Pick From Gallery"),
//            MoreOptionModel(DOWNLOAD_PDF, "Download PDF")
//        )
//
//        OptionPickFragment.show(
//            parentFragmentManager,
//            "Add New PDF",
//            options
//        )
//    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btAddNewPdf -> showOptionDialog()
        }
    }

    private fun showOptionDialog() {
        val options = arrayOf("Добавить PDF из файлов", "Скачать PDF и добавить")
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("PDF добавление")
            .setItems(options) { _, which ->
                val intent = Intent(requireContext(), AddPdfActivity::class.java)
                when (which) {
                    0 -> intent.putExtra(BundleArguments.ARGS_PAGE_TYPE, FROM_GALLERY) // Используем константу
                    1 -> intent.putExtra(BundleArguments.ARGS_PAGE_TYPE, DOWNLOAD_PDF) // Используем константу
                }
                startActivity(intent)
            }
        builder.show()
    }



    override fun onMoreOptionSelected(option: MoreOptionModel) {
        val launchIntent = Intent(requireContext(), AddPdfActivity::class.java)
        val pageType = if (option.id == FROM_GALLERY) {
            AddPdfActivity.PageType.PickFromGallery
        } else {
            AddPdfActivity.PageType.DownloadPdf
        }
        launchIntent.putExtra(BundleArguments.ARGS_PAGE_TYPE, pageType.name)
        addPdfLauncher.launch(launchIntent)
    }



    override fun onPdfItemClicked(pdf: PdfNoteListModel) {
        val intent = Intent(requireContext(), PdfReaderActivity::class.java)
        intent.putExtra(BundleArguments.ARGS_PDF_DETAILS, pdf)
        pdfReaderLauncher.launch(intent)
    }

    override fun onPdfItemLongClicked(pdf: PdfNoteListModel) {}

    private fun setError(error: String?) {
        if (error.isNullOrEmpty()) {
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = error
        }
    }

//    private fun showAddOptions() {
//        val options = arrayListOf(
//            MoreOptionModel(FROM_GALLERY, "Pick From Gallery"),
//            MoreOptionModel(DOWNLOAD_PDF, "Download PDF")
//        )
//        OptionPickFragment.show(
//            parentFragmentManager,
//            "Add New PDF",
//            options
//        )
//    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
