package com.practicum.playlist_maker.search.ui.activity

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.databinding.FragmentSearchBinding
import com.practicum.playlist_maker.pdfreader.adapter.PdfListAdapter
import com.practicum.playlist_maker.pdfreader.model.PdfNoteListModel
import com.practicum.playlist_maker.pdfreader.model.PdfNotesResponse
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.ui.activity.add.AddPdfActivity
import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeViewModel
import com.practicum.playlist_maker.pdfreader.ui.activity.reader.PdfReaderActivity
import com.practicum.playlist_maker.pdfreader.ui.fragment.MoreOptionModel
import com.practicum.playlist_maker.pdfreader.ui.fragment.OptionPickFragment
import com.practicum.playlist_maker.pdfreader.utils.Alerts
import com.practicum.playlist_maker.pdfreader.utils.BundleArguments
import com.practicum.playlist_maker.pdfreader.utils.log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment(), View.OnClickListener, OptionPickFragment.Listener, PdfListAdapter.Listener {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private var adapter: PdfListAdapter? = null
    private var pdfList = arrayListOf<PdfNoteListModel>()
    private var pdfListAll = arrayListOf<PdfNoteListModel>()

    companion object {
        private const val FROM_GALLERY = 1
        private const val DOWNLOAD_PDF = 2
    }

    private val addPdfLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (it.data?.action == AddPdfActivity.RESULT_ACTION_PDF_ADDED) {
                    Alerts.successSnackBar(binding.root, "Note added successfully.")
                    viewModel.getAllPdfs()
                }
            }
        }

    private val pdfReaderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onPdfReaderActivityResult)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        enableEdgeToEdge()
        setupInsets()
        initView()
        bindUI()
        viewModel.getAllPdfs()
    }

    private fun enableEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initView() {
        adapter = PdfListAdapter(pdfList, this)
        binding.rvPdfList.apply {
            adapter = this@SearchFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        binding.etSearch.doAfterTextChanged {
            filterPdfList(it?.toString())
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btBack -> {
                requireActivity().onBackPressed()
            }
            R.id.btAddNewPdf -> {
                showAddOptions()
            }
        }
    }

    private fun bindUI() {
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
                    val pdfListResponse = state.response as PdfNotesResponse?

                    if (pdfListResponse != null) {
                        pdfListAll.clear()
                        pdfListAll.addAll(pdfListResponse.notes)
                        pdfList.clear()
                        pdfList.addAll(pdfListAll)
                    }
                    pdfList.size.log("pdf size")
                    adapter?.notifyDataSetChanged()

                    if (pdfList.isEmpty()) {
                        setError(getString(R.string.you_don_t_have_any_pdf_notes_yet))
                    } else {
                        setError(null)
                    }
                }

                is ResponseState.ValidationError -> {
                    // Handle validation errors if needed
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterPdfList(key: String?) {
        pdfList.clear()
        if (key.isNullOrEmpty()) {
            pdfList.addAll(pdfListAll)
        } else {
            pdfList.addAll(pdfListAll.filter {
                it.title.uppercase().contains(key.uppercase()) ||
                        it.tag?.title?.uppercase()?.contains(key.uppercase()) ?: false
            })
        }
        adapter?.notifyDataSetChanged()

        if (pdfList.isEmpty()) {
            setError("You don't have note with that details")
        } else {
            setError(null)
        }
    }

    private fun setError(error: String?) {
        if (error.isNullOrEmpty()) {
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = error
        }
    }

    private fun showAddOptions() {
        val options = arrayListOf(
            MoreOptionModel(FROM_GALLERY, "Pick From Gallery"),
            MoreOptionModel(DOWNLOAD_PDF, "Download PDF")
        )
        OptionPickFragment.show(
            parentFragmentManager,
            "Add New PDF",
            options
        )
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

    private fun onPdfReaderActivityResult(result: ActivityResult?) {
        if (result != null && result.resultCode == RESULT_OK) {
            if (result.data?.action == PdfReaderActivity.RESULT_ACTION_PDF_DELETED) {
                Alerts.successSnackBar(binding.root, "Pdf Deleted")
                // Refresh data
                viewModel.getAllPdfs()
            }
        }
    }

    override fun onPdfItemClicked(pdf: PdfNoteListModel) {
        val launchIntent = Intent(requireContext(), PdfReaderActivity::class.java)
        launchIntent.putExtra(BundleArguments.ARGS_PDF_DETAILS, pdf)
        pdfReaderLauncher.launch(launchIntent)
    }

    override fun onPdfItemLongClicked(pdf: PdfNoteListModel) {
        // Handle long click if needed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
