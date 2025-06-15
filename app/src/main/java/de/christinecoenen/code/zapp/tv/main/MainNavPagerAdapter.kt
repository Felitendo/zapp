package de.christinecoenen.code.zapp.tv.main

import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import de.christinecoenen.code.zapp.R
import de.christinecoenen.code.zapp.tv.about.AboutFragment
import de.christinecoenen.code.zapp.tv.channels.ChannelListFragment
import de.christinecoenen.code.zapp.tv.mediathek.MediathekListFragment
import kotlin.reflect.KClass

class MainNavPagerAdapter(
	private val context: Context,
	fragmentManger: FragmentManager
) : FragmentPagerAdapter(fragmentManger, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

	private val navItems = listOf(
		MainNavItem(R.string.activity_main_tab_live, ChannelListFragment::class),
		MainNavItem(R.string.activity_main_tab_mediathek, MediathekListFragment::class),
		MainNavItem(R.string.menu_about_short, AboutFragment::class),
	)

	override fun getCount(): Int = navItems.size

	override fun getItem(position: Int): Fragment {
		return navItems[position].createFragment()
	}

	override fun getPageTitle(position: Int): CharSequence {
		return navItems[position].getTitle(context)
	}

	private data class MainNavItem<T : Fragment>(
		@StringRes val titleResId: Int,
		val fragmentClass: KClass<T>
	) {
		fun createFragment(): T = fragmentClass.java.getDeclaredConstructor().newInstance()
		fun getTitle(context: Context) = context.getString(titleResId)
	}
}
