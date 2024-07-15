package com.example.network_listener.ui.cells

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.network_listener.CellInfoModel

class CellInfoViewModel : ViewModel() {

    private val _cellInfo = MutableLiveData<List<CellInfoModel>>()

    private val _cellInfoDictionary = MutableLiveData<Map<String, Map<String, String>>>()
    val cellInfoDictionary: LiveData<Map<String, Map<String, String>>> = _cellInfoDictionary

    private val _firstSeenDictionary = MutableLiveData<Map<String, String>>()
    val firstSeenDictionary: LiveData<Map<String, String>> = _firstSeenDictionary

    private val _lastSeenDictionary = MutableLiveData<Map<String, String>>()
    val lastSeenDictionary: LiveData<Map<String, String>> = _lastSeenDictionary

    fun updateCellInfo(cellInfoList: List<CellInfoModel>) {
        _cellInfo.value = cellInfoList
    }

    fun updateCellInfoDictionary(dictionary: Map<String, Map<String, String>>) {
        _cellInfoDictionary.value = dictionary
    }

    fun updateFirstSeenDictionary(dictionary: Map<String, String>) {
        _firstSeenDictionary.value = dictionary
    }

    fun updateLastSeenDictionary(dictionary: Map<String, String>) {
        _lastSeenDictionary.value = dictionary
    }
}
