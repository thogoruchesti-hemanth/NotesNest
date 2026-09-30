package com.example.NotesNest.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.NotesNest.fragments.NotesFragment
import com.example.NotesNest.fragments.ProfileFragment
import com.example.NotesNest.fragments.RemindersFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            1 -> RemindersFragment()
            2 -> ProfileFragment()
            else -> NotesFragment()
        }
    }
}
