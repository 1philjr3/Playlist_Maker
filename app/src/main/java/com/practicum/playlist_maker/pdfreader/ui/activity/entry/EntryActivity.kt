//package com.practicum.playlist_maker.pdfreader.ui.activity.entry
//
//import android.os.Bundle
//import android.view.View
//import androidx.activity.enableEdgeToEdge
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import com.practicum.playlist_maker.R
//import com.practicum.playlist_maker.databinding.ActivityEntryBinding
//import com.practicum.playlist_maker.pdfreader.base.ui.BaseActivity
//import com.practicum.playlist_maker.pdfreader.ui.activity.home.HomeActivity
//
//class EntryActivity : BaseActivity(), View.OnClickListener {
//    private lateinit var binding: ActivityEntryBinding
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        binding = ActivityEntryBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//
//        initView()
//    }
//
//    private fun initView() {
//        binding.btStart.setOnClickListener(this)
//        binding.btStart.callOnClick()
//    }
//
//    override fun onClick(v: View?) {
//        when (v?.id) {
//            R.id.btStart -> {
//                launchTo(HomeActivity::class.java, finishThisActivity = true)
//            }
//        }
//    }
//}