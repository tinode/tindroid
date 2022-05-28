package com.example.testapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.testapp.ui.SharedViewModel
import com.example.testapp.ui.chats.ChatsFragment
import com.example.testapp.ui.messages.MessagesFragment


public class ChatActivity : AppCompatActivity() {

    private val model: SharedViewModel by lazy {
        ViewModelProvider(this).get(SharedViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ChatsFragment.newInstance())
                .commitNow()
        }

        model.getSelected().observe(this) {
            val fragment =
                if (it != null) MessagesFragment.newInstance(it) else ChatsFragment.newInstance()
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commitNow()
        }

    }
}