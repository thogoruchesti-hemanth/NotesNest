package com.example.NotesNest.utils

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.Log
import android.widget.EditText

class UndoRedoHelper(private val editText: EditText) {

    private var isUndoOrRedo = false
    val isUndoOrRedoState: Boolean
        get() = isUndoOrRedo
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
        try {
            val text = editText.editableText
            val textLength = text.length
            val start = edit.start.coerceIn(0, textLength)
            val afterLen = edit.after?.length ?: 0
            val end = (start + afterLen).coerceIn(start, textLength)

            text.replace(start, end, edit.before ?: "")

            val newSelection = (start + (edit.before?.length ?: 0)).coerceIn(0, text.length)
            editText.setSelection(newSelection)
        } catch (e: Exception) {
            Log.e(TAG, "Error during undo: ${e.message}", e)
        } finally {
            isUndoOrRedo = false
            notifyHistoryChanged()
        }
    }

    fun redo() {
        val edit = editHistory.getNext() ?: return
        isUndoOrRedo = true
        try {
            val text = editText.editableText
            val textLength = text.length
            val start = edit.start.coerceIn(0, textLength)
            val beforeLen = edit.before?.length ?: 0
            val end = (start + beforeLen).coerceIn(start, textLength)

            text.replace(start, end, edit.after ?: "")

            val newSelection = (start + (edit.after?.length ?: 0)).coerceIn(0, text.length)
            editText.setSelection(newSelection)
        } catch (e: Exception) {
            Log.e(TAG, "Error during redo: ${e.message}", e)
        } finally {
            isUndoOrRedo = false
            notifyHistoryChanged()
        }
    }
    
    fun clearHistory() {
        editHistory.clear()
        notifyHistoryChanged()
    }

    fun addEdit(start: Int, before: CharSequence?, after: CharSequence?) {
        if (isUndoOrRedo) return
        editHistory.add(EditItem(start, before, after))
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
            val sLen = s.length
            val safeStart = start.coerceIn(0, sLen)
            val safeEnd = (start + count).coerceIn(safeStart, sLen)
            beforeText = if (s is android.text.Spanned) {
                SpannableStringBuilder(s, safeStart, safeEnd)
            } else {
                SpannableStringBuilder(s.subSequence(safeStart, safeEnd))
            }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            if (isUndoOrRedo) return
            currentStart = start
            currentCount = count
        }

        override fun afterTextChanged(s: Editable?) {
            if (isUndoOrRedo) return
            if (s != null) {
                val sLen = s.length
                val safeStart = currentStart.coerceIn(0, sLen)
                val safeEnd = (currentStart + currentCount).coerceIn(safeStart, sLen)
                val afterText = SpannableStringBuilder(s, safeStart, safeEnd)
                editHistory.add(EditItem(safeStart, beforeText, afterText))
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

    companion object {
        private const val TAG = "UndoRedoHelper"
    }
}
