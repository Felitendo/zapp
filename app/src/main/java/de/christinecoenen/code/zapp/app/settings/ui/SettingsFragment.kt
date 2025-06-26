package de.christinecoenen.code.zapp.app.settings.ui

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.app.settings.repository.SettingsRepository
import de.christinecoenen.code.zapp.utils.system.PreferenceFragmentHelper
import org.koin.android.ext.android.inject

class SettingsFragment : BaseSettingsFragment() {

	private val settingsRepository: SettingsRepository by inject()
	private val preferenceFragmentHelper = PreferenceFragmentHelper(this, settingsRepository)

	private val channelSelectionClickListener = Preference.OnPreferenceClickListener {
		val direction =
			SettingsFragmentDirections.toChannelSelectionFragment()
		findNavController().navigate(direction)
		true
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.preferences)

		preferenceFragmentHelper.initPreferences(channelSelectionClickListener)
	}

	override fun onDestroy() {
		super.onDestroy()
		preferenceFragmentHelper.destroy()
	}
}
