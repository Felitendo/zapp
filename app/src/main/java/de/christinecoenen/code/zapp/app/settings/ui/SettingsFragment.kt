package de.christinecoenen.code.zapp.app.settings.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.app.settings.repository.SettingsRepository
import de.christinecoenen.code.zapp.utils.api.ProxyTestHelper
import de.christinecoenen.code.zapp.utils.system.PreferenceFragmentHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

class SettingsFragment : BaseSettingsFragment() {

	private val settingsRepository: SettingsRepository by inject()
	private val preferenceFragmentHelper = PreferenceFragmentHelper(this, settingsRepository)
	private val proxyTestHelper = ProxyTestHelper()

	private val channelSelectionClickListener = Preference.OnPreferenceClickListener {
		val direction =
			SettingsFragmentDirections.toChannelSelectionFragment()
		findNavController().navigate(direction)
		true
	}

	private val testProxyClickListener = Preference.OnPreferenceClickListener {
		testProxyConnection()
		true
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.preferences)

		preferenceFragmentHelper.initPreferences(channelSelectionClickListener, testProxyClickListener)
	}

	override fun onDestroy() {
		super.onDestroy()
		preferenceFragmentHelper.destroy()
	}

	private fun testProxyConnection() {
		// Show testing message
		val snackbar = Snackbar.make(
			requireView(),
			getString(R.string.proxy_test_running),
			Snackbar.LENGTH_INDEFINITE
		)
		snackbar.show()

		lifecycleScope.launch {
			try {
				// Add an additional timeout at the coroutine level to ensure the test never hangs indefinitely
				val success = withTimeoutOrNull(35000L) { // 35 seconds total timeout
					proxyTestHelper.testProxyConnection()
				}
				
				snackbar.dismiss()
				
				val messageResId = if (success == true) {
					R.string.proxy_test_success
				} else {
					R.string.proxy_test_failed
				}
				
				Snackbar.make(
					requireView(),
					getString(messageResId),
					Snackbar.LENGTH_LONG
				).show()
			} catch (e: Exception) {
				snackbar.dismiss()
				Snackbar.make(
					requireView(),
					getString(R.string.proxy_test_failed),
					Snackbar.LENGTH_LONG
				).show()
			}
		}
	}
}
