package com.example.network_listener.ui.cells

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.network_listener.CellInfoAdapter
import com.example.network_listener.CellInfoModel
import com.example.network_listener.databinding.FragmentCellsBinding

class CellFragment : Fragment() {

    // Binding object instance corresponding to the fragment_cells.xml layout
    // This property is non-null between the onCreateView and onDestroyView lifecycle callbacks.
    private var _binding: FragmentCellsBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    // ViewModel shared between the Activity and this Fragment
    private val cellInfoViewModel: CellInfoViewModel by activityViewModels()

    // Adapter for the RecyclerView
    private lateinit var cellInfoAdapter: CellInfoAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment using View Binding
        _binding = FragmentCellsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the adapter with an empty mutable list
        cellInfoAdapter = CellInfoAdapter(mutableListOf())

        // Set the layout manager for the RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        // Set the adapter for the RecyclerView
        binding.recyclerView.adapter = cellInfoAdapter

        // Observe the cellInfoDictionary LiveData from the ViewModel
        cellInfoViewModel.cellInfoDictionary.observe(viewLifecycleOwner) { dictionary ->
            // Get the firstSeen and lastSeen dictionaries from the ViewModel
            val firstSeenDict = cellInfoViewModel.firstSeenDictionary.value ?: emptyMap()
            val lastSeenDict = cellInfoViewModel.lastSeenDictionary.value ?: emptyMap()

            // Map the dictionary entries to a list of CellInfoModel objects
            val cellInfoList = dictionary.entries.map { (identifier, map) ->
                CellInfoModel(
                    type = map["type"] ?: "N/A",
                    cellId = map["cellId"] ?: "N/A",
                    pci = map["pci"] ?: "N/A",
                    psc = map["psc"] ?: "N/A",
                    locationAreaCode = map["tac"] ?: map["lac"] ?: "N/A",
                    mobileCountryCode = map["mcc"] ?: "N/A",
                    mobileNetworkCode = map["mnc"] ?: "N/A",
                    bandwidth = map["bandwidth"] ?: "N/A",
                    earfcn = map["earfcn"] ?: "N/A",
                    signalStrength = map["signalStrength"] ?: "N/A",
                    operator = map["operator"] ?: "N/A",
                    firstSeen = firstSeenDict[identifier] ?: "N/A",
                    lastSeen = lastSeenDict[identifier] ?: "N/A"
                )
            }
            // Update the adapter with the new list of CellInfoModel objects
            cellInfoAdapter.updateData(cellInfoList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear the binding object when the view is destroyed
        _binding = null
    }
}
