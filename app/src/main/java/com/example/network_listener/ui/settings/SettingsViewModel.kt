package com.example.network_listener.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Version: 1.0\nDeveloper: Riccardo Bianchi\nOrganization: University of Lausanne"
    }
    val text: LiveData<String> = _text
}
