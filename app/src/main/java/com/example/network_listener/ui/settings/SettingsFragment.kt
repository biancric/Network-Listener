package com.example.network_listener.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.example.network_listener.MainActivity
import com.example.network_listener.R
import com.example.network_listener.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    // Binding object instance corresponding to the fragment_settings.xml layout
    // This property is non-null between the onCreateView and onDestroyView lifecycle callbacks.
    private var _binding: FragmentSettingsBinding? = null
    private lateinit var sharedPreferences: SharedPreferences

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Get the ViewModel for this fragment
        val settingsViewModel =
            ViewModelProvider(this).get(SettingsViewModel::class.java)

        // Inflate the layout for this fragment using View Binding
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Get the shared preferences for the app
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Initialize UI elements
        val themeGroup: RadioGroup = binding.themeGroup
        val switchAlwaysOn: SwitchCompat = binding.switchAlwaysOn
        val textAppInfo: TextView = binding.textAppInfo

        // Load and set saved preferences
        when (sharedPreferences.getString("theme", "system")) {
            "light" -> themeGroup.check(R.id.radio_light)
            "dark" -> themeGroup.check(R.id.radio_dark)
            "system" -> themeGroup.check(R.id.radio_system)
        }

        switchAlwaysOn.isChecked = sharedPreferences.getBoolean("always_on_display", false)

        // Save preferences on change
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radio_light -> "light"
                R.id.radio_dark -> "dark"
                R.id.radio_system -> "system"
                else -> "system"
            }
            sharedPreferences.edit().putString("theme", theme).apply()
            applyTheme(theme)
        }

        switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("always_on_display", isChecked).apply()
            (requireActivity() as MainActivity).setDisplayOn(isChecked)
        }

        // Observe the ViewModel's text LiveData and update the UI
        settingsViewModel.text.observe(viewLifecycleOwner) {
            textAppInfo.text = it
        }

        return root
    }

    // Apply the selected theme and recreate the activity to apply changes
    private fun applyTheme(theme: String) {
        when (theme) {
            "light" -> requireActivity().setTheme(R.style.Theme_Light)
            "dark" -> requireActivity().setTheme(R.style.Theme_Dark)
            "system" -> requireActivity().setTheme(R.style.Theme_System)
        }
        requireActivity().recreate()
    }

    // Clear the binding object when the view is destroyed to avoid memory leaks
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
