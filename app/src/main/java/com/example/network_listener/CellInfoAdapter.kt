package com.example.network_listener

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Adapter class for displaying cell information in a RecyclerView
class CellInfoAdapter(private val cellInfoList: MutableList<CellInfoModel>) : RecyclerView.Adapter<CellInfoAdapter.CellInfoViewHolder>() {

    // ViewHolder class to hold references to the UI components for each item
    class CellInfoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // TextView references for displaying cell information
        val type: TextView = view.findViewById(R.id.type)
        val cellId: TextView = view.findViewById(R.id.cellId)
        val locationAreaCode: TextView = view.findViewById(R.id.locationAreaCode)
        val mobileCountryCode: TextView = view.findViewById(R.id.mobileCountryCode)
        val mobileNetworkCode: TextView = view.findViewById(R.id.mobileNetworkCode)
        val bandwidth: TextView = view.findViewById(R.id.bandwidth)
        val earfcn: TextView = view.findViewById(R.id.earfcn)
        val signalStrength: TextView = view.findViewById(R.id.signalStrength)
        val operator: TextView = view.findViewById(R.id.operator)
        val firstSeen: TextView = view.findViewById(R.id.firstSeen)
        val lastSeen: TextView = view.findViewById(R.id.lastSeen)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellInfoViewHolder {
        // Inflate the item layout
        val view = LayoutInflater.from(parent.context).inflate(R.layout.cell_info_item, parent, false)
        return CellInfoViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: CellInfoViewHolder, position: Int) {
        // Get element from your dataset at this position
        // Replace the contents of the view with that element
        val cellInfo = cellInfoList[position]
        holder.type.text = cellInfo.type
        holder.cellId.text = cellInfo.cellId
        holder.locationAreaCode.text = cellInfo.locationAreaCode
        holder.mobileCountryCode.text = cellInfo.mobileCountryCode
        holder.mobileNetworkCode.text = cellInfo.mobileNetworkCode
        holder.bandwidth.text = cellInfo.bandwidth
        holder.earfcn.text = cellInfo.earfcn
        holder.signalStrength.text = cellInfo.signalStrength
        holder.operator.text = cellInfo.operator
        holder.firstSeen.text = cellInfo.firstSeen
        holder.lastSeen.text = cellInfo.lastSeen
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return cellInfoList.size
    }

    // Update the data in the adapter and notify the RecyclerView to refresh
    fun updateData(newCellInfoList: List<CellInfoModel>) {
        cellInfoList.clear()
        cellInfoList.addAll(newCellInfoList)
        notifyDataSetChanged()
    }
}
