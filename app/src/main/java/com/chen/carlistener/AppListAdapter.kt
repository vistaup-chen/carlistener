package com.chen.carlistener

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.github.promeg.pinyinhelper.Pinyin

/**
 * 应用列表适配器 — 支持字母分组头（中文转拼音首字母）
 */
class AppListAdapter(
    private val context: Context,
    private val pm: PackageManager
) : BaseAdapter() {

    companion object {
        const val TYPE_APP = 0
        const val TYPE_HEADER = 1
        private const val TYPE_COUNT = 2
    }

    private var allItems: List<ListItem> = emptyList()
    private var filteredItems: List<ListItem> = emptyList()
    private val selectedPackages = mutableSetOf<String>()

    data class ListItem(
        val type: Int,
        val letter: Char = ' ',
        val appInfo: ApplicationInfo? = null
    )

    fun setData(appList: List<ApplicationInfo>, selected: Set<String>) {
        selectedPackages.clear()
        selectedPackages.addAll(selected)

        val items = mutableListOf<ListItem>()
        var lastLetter = ' '

        for (app in appList) {
            val label = pm.getApplicationLabel(app).toString()
            val letter = getFirstLetter(label)

            if (letter != lastLetter) {
                items.add(ListItem(TYPE_HEADER, letter))
                lastLetter = letter
            }
            items.add(ListItem(TYPE_APP, appInfo = app))
        }

        allItems = items
        filteredItems = items
        notifyDataSetChanged()
    }

    private fun getFirstLetter(text: String): Char {
        val first = text.firstOrNull() ?: return '#'
        if (first in 'A'..'Z') return first
        if (first in 'a'..'z') return first.uppercaseChar()
        if (first in '0'..'9') return '#'

        // 中文用 TinyPinyin 转拼音首字母
        if (Pinyin.isChinese(first)) {
            val c = Pinyin.toPinyin(first)[0].uppercaseChar()
            if (c in 'A'..'Z') return c
        }

        return '#'
    }

    fun filter(query: String) {
        filteredItems = if (query.isEmpty()) {
            allItems
        } else {
            val lowerQuery = query.lowercase()
            val filtered = allItems.filter { item ->
                if (item.type == TYPE_APP && item.appInfo != null) {
                    val label = pm.getApplicationLabel(item.appInfo).toString().lowercase()
                    val pkg = item.appInfo.packageName.lowercase()
                    label.contains(lowerQuery) || pkg.contains(lowerQuery)
                } else {
                    false
                }
            }
            val withHeaders = mutableListOf<ListItem>()
            var lastLetter = ' '
            for (item in filtered) {
                val label = pm.getApplicationLabel(item.appInfo!!).toString()
                val letter = getFirstLetter(label)
                if (letter != lastLetter) {
                    withHeaders.add(ListItem(TYPE_HEADER, letter))
                    lastLetter = letter
                }
                withHeaders.add(item)
            }
            withHeaders
        }
        notifyDataSetChanged()
    }

    fun getAvailableLetters(): Set<Char> {
        return filteredItems.filter { it.type == TYPE_HEADER }.map { it.letter }.toSet()
    }

    fun getLetterPosition(letter: Char): Int {
        val upper = letter.uppercaseChar()
        return filteredItems.indexOfFirst {
            it.type == TYPE_HEADER && it.letter == upper
        }.coerceAtLeast(0)
    }

    override fun getCount(): Int = filteredItems.size
    override fun getItem(position: Int): Any = filteredItems[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = TYPE_COUNT
    override fun getItemViewType(position: Int): Int = filteredItems[position].type

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = filteredItems[position]
        return when (item.type) {
            TYPE_HEADER -> getHeaderView(item, convertView, parent)
            else -> getAppView(item, convertView, parent)
        }
    }

    private fun getHeaderView(item: ListItem, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val tv = view.findViewById<TextView>(android.R.id.text1)
        tv.text = "───── ${item.letter} ─────"
        tv.textSize = 13f
        tv.setTextColor(0xFF888888.toInt())
        tv.setPadding(24, 12, 24, 4)
        view.isEnabled = false
        view.isClickable = false
        return view
    }

    private fun getAppView(item: ListItem, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val tv = view.findViewById<TextView>(android.R.id.text1)
        val appInfo = item.appInfo!!
        val label = pm.getApplicationLabel(appInfo).toString()
        val pkg = appInfo.packageName
        val check = if (pkg in selectedPackages) "☑ " else "☐ "
        tv.text = "$check$label（$pkg）"
        tv.textSize = 14f
        tv.setPadding(32, 8, 32, 8)
        return view
    }
}
