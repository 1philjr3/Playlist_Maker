package com.practicum.playlist_maker.pdfreader.ui.activity.annotations.comments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.practicum.playlist_maker.R
import com.practicum.playlist_maker.pdfreader.adapter.CommentListAdapter
import com.practicum.playlist_maker.pdfreader.base.ui.BaseActivity
import com.practicum.playlist_maker.databinding.ActivityCommentsListBinding
import com.practicum.playlist_maker.pdfreader.model.DeleteAnnotationResponse
import com.practicum.playlist_maker.pdfreader.state.ResponseState
import com.practicum.playlist_maker.pdfreader.tools.pdf.viewer.model.CommentModel
import com.practicum.playlist_maker.pdfreader.utils.Alerts
import com.practicum.playlist_maker.pdfreader.utils.BundleArguments
import com.practicum.playlist_maker.pdfreader.utils.getParcelableArrayListExtraVs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CommentsListActivity : BaseActivity(), View.OnClickListener, CommentListAdapter.Listener {
    private val viewModel : CommentsListViewModel by viewModels()
    private lateinit var binding : ActivityCommentsListBinding
    private var commentListAdapter: CommentListAdapter? = null
    private var comments = arrayListOf<CommentModel>()

    private var deletedCommentIds = arrayListOf<Long>()


    companion object {
        const val RESULT_ACTION_OPEN_COMMENT = "action.open.comment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCommentsListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        getDataFromIntent()
        initView()

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true){
            override fun handleOnBackPressed() {
                if (deletedCommentIds.isEmpty()) {
                    finish()
                } else {
                    val result = Intent()
                    result.putExtra(BundleArguments.ARGS_DELETED_COMMENT_IDS,deletedCommentIds.toLongArray())
                }
            }
        })
    }



    private fun getDataFromIntent() {
        intent?.let {
            it.getParcelableArrayListExtraVs(BundleArguments.ARGS_COMMENTS, CommentModel::class.java)?.also { comments->
                this.comments.clear()
                this.comments.addAll(comments)
            }
        }
    }

    private fun initView() {
        binding.btBack.setOnClickListener(this)
        binding.btDelete.setOnClickListener(this)
        commentListAdapter = CommentListAdapter(this,comments, this)
        binding.rvComments.apply {
            adapter = commentListAdapter
            layoutManager = LinearLayoutManager(this@CommentsListActivity)
        }

        checkItemCount()
    }

    private fun checkItemCount() {
        if (comments.isEmpty()) {
            setError("Комментарии не найдены")
        } else {
            setError(null)
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btBack -> {
                if (commentListAdapter?.adapterState == CommentListAdapter.AdapterState.SELECTION_MODE) {
                    clearAllSelection()
                } else {
                    onBackPressedDispatcher.onBackPressed()
                }

            }

            R.id.btDelete -> {
                deleteComments()
            }
        }
    }

    private fun deleteComments() {
        val idsToDelete = comments.filter { it.isSelected }.map { it.id }
        viewModel.deleteComments(idsToDelete)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun bindUI() = launch(Dispatchers.Main) {
        viewModel.deleteCommentResponse.state.observe(this@CommentsListActivity) {state->
            when (state) {
                is ResponseState.Failed -> {
                    Alerts.failureSnackBar(binding.root,state.error)
                }
                ResponseState.Loading -> {}
                is ResponseState.Success<*> -> {
                    val response = state.response as DeleteAnnotationResponse?
                    if (response != null) {
                        deletedCommentIds.addAll(response.deletedIds)
                        comments.removeAll {
                            response.deletedIds.contains(it.id)
                        }
                        commentListAdapter?.notifyDataSetChanged()
                        checkItemCount()
                    }
                }
                is ResponseState.ValidationError -> {}
            }
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


    override fun onItemClicked(
        item: CommentModel,
        state: CommentListAdapter.AdapterState,
        position: Int
    ) {
       if (state == CommentListAdapter.AdapterState.IDLE) {
           val result = Intent(RESULT_ACTION_OPEN_COMMENT).apply {
               putExtra(BundleArguments.ARGS_COMMENT, item)
               putExtra(BundleArguments.ARGS_DELETED_COMMENT_IDS, deletedCommentIds.toLongArray())
           }
       } else {
           comments[position].isSelected = !comments[position].isSelected
           commentListAdapter?.notifyItemChanged(position)

           val selectedCount = comments.count { it.isSelected }
           if (selectedCount == 0) {
               clearAllSelection()
           }
       }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onItemLongClicked(
        item: CommentModel,
        state: CommentListAdapter.AdapterState,
        position: Int
    ) {
        comments[position].isSelected = true
        commentListAdapter?.adapterState = CommentListAdapter.AdapterState.SELECTION_MODE
        commentListAdapter?.notifyDataSetChanged()
        setUpAppBar(CommentListAdapter.AdapterState.SELECTION_MODE)
    }

    private fun setUpAppBar(state: CommentListAdapter.AdapterState) {
        when (state) {
            CommentListAdapter.AdapterState.IDLE ->  {
                binding.btBack.setImageResource(R.drawable.ic_arrow_back)
                binding.btDelete.visibility = View.GONE
            }
            CommentListAdapter.AdapterState.SELECTION_MODE ->  {
                binding.btBack.setImageResource(R.drawable.ic_close_white)
                binding.btDelete.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearAllSelection () {
        comments.map { it.isSelected = false }
        commentListAdapter?.adapterState = CommentListAdapter.AdapterState.IDLE
        commentListAdapter?.notifyDataSetChanged()
        setUpAppBar(CommentListAdapter.AdapterState.IDLE)
    }

}