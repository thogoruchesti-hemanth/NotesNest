package com.example.NotesNest.utils

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.widget.EditText

class UndoRedoHelper(private val editText: EditText) {

    private var isUndoOrRedo = false
    private val editHistory = EditHistory()
    private val changeListener = EditTextChangeListener()
    private var historyChangeListener: ((Boolean, Boolean) -> Unit)? = null

    init {
        editText.addTextChangedListener(changeListener)
    }
    
    fun setOnHistoryChangeListener(listener: (canUndo: Boolean, canRedo: Boolean) -> Unit) {
        this.historyChangeListener = listener
        notifyHistoryChanged()
    }

    private fun notifyHistoryChanged() {
        historyChangeListener?.invoke(canUndo, canRedo)
    }

    fun undo() {
        val edit = editHistory.getPrevious() ?: return
        isUndoOrRedo = true
        val text = editText.editableText
        val start = edit.start
        val end = start + if (edit.after != null) edit.after.length else 0
        text.replace(start, end, edit.before ?: "")
        isUndoOrRedo = false
        
        editText.setSelection((edit.start + (edit.before?.length ?: 0)).coerceAtMost(editText.length()))
        notifyHistoryChanged()
    }

    fun redo() {
        val edit = editHistory.getNext() ?: return
        isUndoOrRedo = true
        val text = editText.editableText
        val start = edit.start
        val end = start + if (edit.before != null) edit.before.length else 0
        text.replace(start, end, edit.after ?: "")
        isUndoOrRedo = false
        
        editText.setSelection((edit.start + (edit.after?.length ?: 0)).coerceAtMost(editText.length()))
        notifyHistoryChanged()
    }
    
    fun clearHistory() {
        editHistory.clear()
        notifyHistoryChanged()
    }
    
    val canUndo: Boolean
        get() = editHistory.canUndo()
        
    val canRedo: Boolean
        get() = editHistory.canRedo()

    private inner class EditTextChangeListener : TextWatcher {
        private var beforeText: CharSequence? = null
        private var currentStart: Int = 0
        private var currentCount: Int = 0

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (isUndoOrRedo) return
            beforeText = SpannableStringBuilder(s.subSequence(start, start + count))
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (isUndoOrRedo) return
            currentStart = start
            currentCount = count
        }

        override fun afterTextChanged(s: Editable?) {
            if (isUndoOrRedo) return
            if (s != null) {
                val afterText = SpannableStringBuilder(s.subSequence(currentStart, currentStart + currentCount))
                editHistory.add(EditItem(currentStart, beforeText, afterText))
            }
            notifyHistoryChanged()
        }
    }

    private class EditHistory {
        private var position = 0
        private val history = mutableListOf<EditItem>()
        private val maxHistorySize = 100

        fun clear() {
            position = 0
            history.clear()
        }
        
        fun canUndo(): Boolean {
            return position > 0
        }
        
        fun canRedo(): Boolean {
            return position < history.size
        }

        fun add(item: EditItem) {
            while (history.size > position) {
                history.removeAt(position)
            }
            history.add(item)
            position++
            
            if (history.size > maxHistorySize) {
                history.removeAt(0)
                position--
            }
        }

        fun getPrevious(): EditItem? {
            if (position == 0) return null
            position--
            return history[position]
        }

        fun getNext(): EditItem? {
            if (position >= history.size) return null
            val item = history[position]
            position++
            return item
        }
    }

    private data class EditItem(
        val start: Int,
        val before: CharSequence?,
        val after: CharSequence?
    )
}
