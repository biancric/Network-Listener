package com.example.network_listener

data class CellInfoModel(
    val type: String,
    val cellId: String,
    val pci: String,
    val psc: String,
    val locationAreaCode: String,
    val mobileCountryCode: String,
    val mobileNetworkCode: String,
    val signalStrength: String,
    val operator: String,
    val firstSeen: String,
    val lastSeen: String = "",
    val earfcn: String = "",
    val bandwidth: String = ""
)
