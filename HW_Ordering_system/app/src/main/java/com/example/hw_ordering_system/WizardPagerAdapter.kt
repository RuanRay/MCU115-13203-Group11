package com.example.hw_ordering_system

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

const val PAGE_NONE = -1
const val PAGE_MAIN_MEAL = 0
const val PAGE_SIDE_DISHES = 1
const val PAGE_DRINK = 2
const val PAGE_CONFIRM = 3

class WizardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        PAGE_MAIN_MEAL -> MainMealFragment()
        PAGE_SIDE_DISHES -> SideDishesFragment()
        PAGE_DRINK -> DrinkFragment()
        else -> ConfirmFragment()
    }
}
