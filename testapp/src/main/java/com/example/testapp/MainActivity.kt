package com.example.testapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.testapp.components.channel.ChannelListActivity
import com.example.testapp.components.channel.list.viewmodel.ChannelListViewModel
import com.example.testapp.components.channel.list.viewmodel.bindView
import com.example.testapp.components.channel.list.viewmodel.factory.ChannelListViewModelFactory
import com.example.testapp.databinding.ActivityMainBinding

public class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.chatActivityButton.setOnClickListener {
            startActivity(ChannelListActivity.createIntent(this))
        }

        binding.chatViewButton.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

    }

}