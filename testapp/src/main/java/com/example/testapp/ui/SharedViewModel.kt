package com.example.testapp.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.testapp.client.models.Channel

public class SharedViewModel : ViewModel() {
    private val selected: MutableLiveData<Channel?> = MutableLiveData<Channel?>()
    public fun select(item: Channel?) {
        selected.value = item
    }

    public fun getSelected(): LiveData<Channel?> {
        return selected
    }
}
