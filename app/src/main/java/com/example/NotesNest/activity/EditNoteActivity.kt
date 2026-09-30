package com.example.NotesNest.activity

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.ViewModelProvider
import com.example.NotesNest.R
import com.example.NotesNest.databases.ViewModels.CategoryViewModel
import com.example.NotesNest.databases.ViewModels.NoteViewModel
import com.example.NotesNest.databases.entities.CategoryEntity
import com.example.NotesNest.databases.entities.NoteEntity
import com.example.NotesNest.databinding.ActivityEditNoteBinding
import com.example.NotesNest.utils.AppPreferences
import com.example.NotesNest.utils.CommonDialogs
import com.example.NotesNest.utils.Constants.DEFAULT_COLORS
import com.example.NotesNest.utils.MarkdownHelper
import com.example.NotesNest.utils.UndoRedoHelper
import com.example.NotesNest.utils.constants.PrefKeys
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

class EditNoteActivity : AppCompatActivity() {

    private val defaultColor = DEFAULT_COLORS[Random().nextInt(DEFAULT_COLORS.size)]
    private val categories = ArrayList<CategoryEntity>()
    private lateinit var preferences: AppPreferences
    private lateinit var noteViewModel: NoteViewModel
    private lateinit var categoryViewModel: CategoryViewModel

    private var isEditing = false
    private var isPendingBold = false
    private var isPendingItalic = false
    private var noteId: String? = null
    private var selectedColor = defaultColor
    private var selectedCategoryId: String? = null
    private var originalCreatedAt = -1L
    private var isPinned = false
    private var isNoteSaved = false
    private var lastAddedCategoryName: String? = null
    private lateinit var binding: ActivityEditNoteBinding
    private lateinit var undoRedoHelper: UndoRedoHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityEditNoteBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during inflation: ${e.message}", e)
            Toast.makeText(this, "Resource loading error. Please restart the app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        preferences = AppPreferences.getInstance()
        binding.btnColorPicker.imageTintList = ColorStateList.valueOf(Color.BLACK)
        binding.etNote.isNestedScrollingEnabled = true

        noteId = intent.getStringExtra(EXTRA_ITEM_ID)

        applyWindowInsets()
        initViewModels()
        setupListeners()
        setupTextWatchers()
        observeViewModels()

        if (savedInstanceState != null) {
            noteId = savedInstanceState.getString("noteId")
            isEditing = savedInstanceState.getBoolean("isEditing", false)
            selectedColor = savedInstanceState.getString("selectedColor", defaultColor) ?: defaultColor
            selectedCategoryId = savedInstanceState.getString("selectedCategoryId")
            isPinned = savedInstanceState.getBoolean("isPinned", false)
            updatePinUI()
        }

        handleIncomingIntent()

        if (!isEditing) {
            selectedColor = defaultColor
            updateBackgroundColor()
            restoreDraftIfNeeded()
        }

        updateMetadataLine()
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.edit_note_layout)
        val header = findViewById<View>(R.id.headerLayout)
        val keyboardSpacer = findViewById<View>(R.id.keyboard_spacer)

        if (root == null) return

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val systemBars: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime: Insets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            header?.setPadding(header.paddingLeft, systemBars.top + (8 * resources.displayMetrics.density).toInt(), header.paddingRight, header.paddingBottom)

            val bottomInset = maxOf(systemBars.bottom, ime.bottom)
            keyboardSpacer?.let {
                val params = it.layoutParams
                if (params != null) {
                    params.height = bottomInset
                    it.layoutParams = params
                }
            }

            windowInsets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("noteId", noteId)
        outState.putBoolean("isEditing", isEditing)
        outState.putString("selectedColor", selectedColor)
        outState.putString("selectedCategoryId", selectedCategoryId)
        outState.putBoolean("isPinned", isPinned)
    }

    private fun initViewModels() {
        noteViewModel = ViewModelProvider(this)[NoteViewModel::class.java]
        categoryViewModel = ViewModelProvider(this)[CategoryViewModel::class.java]
    }

    private fun setupListeners() {
        setBackListener()
        setPinListener()
        setSaveListener()
        setColorPickerListener()
        setFormattingListeners()
    }

    private fun setBackListener() {
        binding.btnBack.setOnClickListener {
            handleAutoSaveAndFinish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleAutoSaveAndFinish()
            }
        })
    }

    private fun handleAutoSaveAndFinish() {
        val title = binding.etTitle.text.toString().trim()
        val markdownContent = getMarkdownFromEditText().trim()

        if (title.isNotEmpty() || markdownContent.isNotEmpty()) {
            performSave(title, markdownContent)
        } else {
            preferences.clearDraft()
            finish()
        }
    }

    private fun setPinListener() {
        binding.btnPin.setOnClickListener {
            isPinned = !isPinned
            updatePinUI()
        }
    }

    private fun setSaveListener() {
        binding.btnSave.setOnClickListener { saveNote() }
    }

    private fun setColorPickerListener() {
        binding.btnColorPicker.setOnClickListener {
            CommonDialogs.showColorPicker(this, selectedColor) { color ->
                selectedColor = color
                updateBackgroundColor()
            }
        }
    }

    private enum class PrefixType {
        NONE, H1, H2, BULLET, NUMBERED, CHECKLIST
    }

    private class HeadingSpan(val level: Int) : StyleSpan(Typeface.BOLD)

    private var isHandlingAutoList = false

    private fun setupTextWatchers() {
        binding.etNote.addTextChangedListener(object : TextWatcher {
            private var insertedStart = 0
            private var insertedCount = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                insertedStart = start
                insertedCount = count

                if (count > before && !isHandlingAutoList) {
                    val editable = binding.etNote.text ?: return
                    val typedStart = start
                    val typedEnd = start + count

                    if (isPendingBold && isPendingItalic) {
                        editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), typedStart, typedEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                        isPendingBold = false
                        isPendingItalic = false
                    } else if (isPendingBold) {
                        editable.setSpan(StyleSpan(Typeface.BOLD), typedStart, typedEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                        isPendingBold = false
                    } else if (isPendingItalic) {
                        editable.setSpan(StyleSpan(Typeface.ITALIC), typedStart, typedEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                        isPendingItalic = false
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (s == null) return

                if (::undoRedoHelper.isInitialized && undoRedoHelper.isUndoOrRedoState) {
                    insertedCount = 0
                    cleanupZeroLengthSpans(s)
                    updateHeadingSpans(s)
                    updateMetadataLine()
                    updateFormattingButtonHighlights()
                    return
                }

                if (!isHandlingAutoList && insertedCount == 1 && insertedStart < s.length && s[insertedStart] == '\n') {
                    insertedCount = 0
                    handleAutoListContinuation(s, insertedStart)
                }

                cleanupZeroLengthSpans(s)
                updateHeadingSpans(s)
                updateMetadataLine()
                updateFormattingButtonHighlights()
            }
        })

        binding.etNote.setOnClickListener {
            isPendingBold = false
            isPendingItalic = false
            updateFormattingButtonHighlights()
        }

        binding.etNote.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val editText = v as? EditText ?: return@setOnTouchListener false
                val layout = editText.layout
                if (layout != null) {
                    val y = event.y - editText.totalPaddingTop + editText.scrollY
                    val line = layout.getLineForVertical(y.toInt())
                    val lineStart = layout.getLineStart(line)
                    val lineEnd = layout.getLineEnd(line)
                    val text = editText.text.toString()
                    if (lineStart < text.length && lineEnd <= text.length && lineStart < lineEnd) {
                        val lineText = text.substring(lineStart, lineEnd)
                        val trimmed = lineText.trimStart()
                        val leading = lineText.takeWhile { it == ' ' || it == '\t' }
                        val offset = layout.getOffsetForHorizontal(line, event.x - editText.totalPaddingLeft + editText.scrollX)

                        if (offset >= lineStart && offset <= lineStart + leading.length + 3) {
                            if (trimmed.startsWith("☐ ")) {
                                isHandlingAutoList = true
                                editText.text.replace(lineStart + leading.length, lineStart + leading.length + 2, "☑ ")
                                isHandlingAutoList = false
                                updateFormattingButtonHighlights()
                                v.performClick()
                                return@setOnTouchListener true
                            } else if (trimmed.startsWith("☑ ")) {
                                isHandlingAutoList = true
                                editText.text.replace(lineStart + leading.length, lineStart + leading.length + 2, "☐ ")
                                isHandlingAutoList = false
                                updateFormattingButtonHighlights()
                                v.performClick()
                                return@setOnTouchListener true
                            }
                        }
                    }
                }
            }
            false
        }
    }

    private fun updateMetadataLine() {
        val content = binding.etNote.text.toString().trim()
        val wordCount = if (content.isEmpty()) 0 else content.split(Regex("\\s+")).size
        val charCount = content.length

        val dateStr = if (isEditing && originalCreatedAt > 0) {
            SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(originalCreatedAt))
        } else {
            SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        }

        binding.tvMetadata.text = String.format(Locale.getDefault(), "%s • %d words (%d chars)", dateStr, wordCount, charCount)
    }

    private fun setFormattingListeners() {
        undoRedoHelper = UndoRedoHelper(binding.etNote)
        undoRedoHelper.setOnHistoryChangeListener { canUndo, canRedo ->
            binding.btnUndo.alpha = if (canUndo) 1.0f else 0.5f
            binding.btnUndo.isEnabled = canUndo
            binding.btnRedo.alpha = if (canRedo) 1.0f else 0.5f
            binding.btnRedo.isEnabled = canRedo
            updateFormattingButtonHighlights()
        }
        binding.btnUndo.setOnClickListener {
            isPendingBold = false
            isPendingItalic = false
            undoRedoHelper.undo()
        }
        binding.btnRedo.setOnClickListener {
            isPendingBold = false
            isPendingItalic = false
            undoRedoHelper.redo()
        }

        binding.btnBold.setOnClickListener { toggleBold() }
        binding.btnItalic.setOnClickListener { toggleItalic() }
        binding.btnBulletList.setOnClickListener { toggleLinePrefix(PrefixType.BULLET) }
        binding.btnNumberedList.setOnClickListener { toggleLinePrefix(PrefixType.NUMBERED) }
        binding.btnChecklist.setOnClickListener { toggleLinePrefix(PrefixType.CHECKLIST) }
        binding.btnH1.setOnClickListener { toggleLinePrefix(PrefixType.H1) }
        binding.btnH2.setOnClickListener { toggleLinePrefix(PrefixType.H2) }
    }

    private fun cleanupZeroLengthSpans(editable: Editable?) {
        if (editable == null) return
        val spans = editable.getSpans(0, editable.length, StyleSpan::class.java)
        for (span in spans) {
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (start == end) {
                editable.removeSpan(span)
            }
        }
    }

    private fun isStyleActive(style: Int): Boolean {
        val editable = binding.etNote.text ?: return false
        val start = binding.etNote.selectionStart.coerceAtLeast(0)
        val end = binding.etNote.selectionEnd.coerceAtLeast(0)

        val spans = editable.getSpans(start, end, StyleSpan::class.java)

        if (start == end) {
            for (span in spans) {
                val s = span.style
                if (s == style || s == Typeface.BOLD_ITALIC) {
                    val flags = editable.getSpanFlags(span)
                    val spanFlags = flags and Spannable.SPAN_POINT_MARK_MASK
                    val spanStart = editable.getSpanStart(span)
                    val spanEnd = editable.getSpanEnd(span)
                    
                    if (spanStart == spanEnd) continue
                    
                    val isInclusiveEnd = spanFlags == Spannable.SPAN_EXCLUSIVE_INCLUSIVE || spanFlags == Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    val isInclusiveStart = spanFlags == Spannable.SPAN_INCLUSIVE_EXCLUSIVE || spanFlags == Spannable.SPAN_INCLUSIVE_INCLUSIVE

                    val applies = (start > spanStart && start < spanEnd) ||
                        (start == spanEnd && isInclusiveEnd) ||
                        (start == spanStart && isInclusiveStart)
                        
                    if (applies) return true
                }
            }
            if (style == Typeface.BOLD && isPendingBold) return true
            if (style == Typeface.ITALIC && isPendingItalic) return true
            
            return false
        } else {
            for (span in spans) {
                val s = span.style
                if (s == style || s == Typeface.BOLD_ITALIC) return true
            }
            return false
        }
    }

    private fun getAdjustedStartOffset(editable: Editable, start: Int, end: Int): Int {
        val text = editable.toString()
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        val lineEnd = text.indexOf('\n', start).let {
            if (it < 0) text.length else it
        }
        val lineStr = text.substring(lineStart, lineEnd)
        val leadingSpaces = lineStr.takeWhile { it == ' ' || it == '\t' }
        val trimmed = lineStr.substring(leadingSpaces.length)

        var prefixLength = 0
        when {
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> prefixLength = leadingSpaces.length + 6
            trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") -> prefixLength = leadingSpaces.length + 2
            Regex("^\\d+\\.\\s+").containsMatchIn(trimmed) -> {
                val match = Regex("^\\d+\\.\\s+").find(trimmed)!!
                prefixLength = leadingSpaces.length + match.value.length
            }
        }

        val contentStart = lineStart + prefixLength
        return if (start < contentStart && contentStart < end) contentStart else start
    }

    private fun toggleBold() {
        val editable = binding.etNote.text ?: return
        val start = binding.etNote.selectionStart.coerceAtLeast(0)
        val end = binding.etNote.selectionEnd.coerceAtLeast(0)

        val currentlyBold = isStyleActive(Typeface.BOLD)

        if (start != end) {
            val applyStart = getAdjustedStartOffset(editable, start, end).coerceAtMost(end)
            val beforeState = SpannableStringBuilder(editable, applyStart, end)

            val spans = editable.getSpans(applyStart, end, StyleSpan::class.java)
            for (span in spans) {
                if (span.style == Typeface.BOLD) {
                    editable.removeSpan(span)
                } else if (span.style == Typeface.BOLD_ITALIC) {
                    val sStart = editable.getSpanStart(span)
                    val sEnd = editable.getSpanEnd(span)
                    val flags = editable.getSpanFlags(span)
                    editable.removeSpan(span)
                    editable.setSpan(StyleSpan(Typeface.ITALIC), sStart, sEnd, flags)
                }
            }
            if (!currentlyBold) {
                // If it has italic, upgrade to bold_italic
                val italicSpans = editable.getSpans(applyStart, end, StyleSpan::class.java).filter { it.style == Typeface.ITALIC }
                if (italicSpans.isNotEmpty()) {
                    for (span in italicSpans) {
                        val sStart = editable.getSpanStart(span)
                        val sEnd = editable.getSpanEnd(span)
                        val flags = editable.getSpanFlags(span)
                        editable.removeSpan(span)
                        editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), sStart, sEnd, flags)
                    }
                } else {
                    editable.setSpan(StyleSpan(Typeface.BOLD), applyStart, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                }
            }

            val afterState = SpannableStringBuilder(editable, applyStart, end)
            if (::undoRedoHelper.isInitialized) {
                undoRedoHelper.addEdit(applyStart, beforeState, afterState)
            }
        } else {
            if (currentlyBold) {
                isPendingBold = false
                val spans = editable.getSpans(0, editable.length, StyleSpan::class.java)
                for (span in spans) {
                    if (span.style == Typeface.BOLD) {
                        val sEnd = editable.getSpanEnd(span)
                        if (sEnd >= start) {
                            val sStart = editable.getSpanStart(span)
                            editable.removeSpan(span)
                            if (sStart < start) {
                                editable.setSpan(StyleSpan(Typeface.BOLD), sStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                    } else if (span.style == Typeface.BOLD_ITALIC) {
                        val sEnd = editable.getSpanEnd(span)
                        if (sEnd >= start) {
                            val sStart = editable.getSpanStart(span)
                            editable.removeSpan(span)
                            if (sStart < start) {
                                editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), sStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            editable.setSpan(StyleSpan(Typeface.ITALIC), start, sEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                        }
                    }
                }
            } else {
                isPendingBold = true
            }
        }
        updateFormattingButtonHighlights()
    }

    private fun toggleItalic() {
        val editable = binding.etNote.text ?: return
        val start = binding.etNote.selectionStart.coerceAtLeast(0)
        val end = binding.etNote.selectionEnd.coerceAtLeast(0)

        val currentlyItalic = isStyleActive(Typeface.ITALIC)

        if (start != end) {
            val applyStart = getAdjustedStartOffset(editable, start, end).coerceAtMost(end)
            val beforeState = SpannableStringBuilder(editable, applyStart, end)

            val spans = editable.getSpans(applyStart, end, StyleSpan::class.java)
            for (span in spans) {
                if (span.style == Typeface.ITALIC) {
                    editable.removeSpan(span)
                } else if (span.style == Typeface.BOLD_ITALIC) {
                    val sStart = editable.getSpanStart(span)
                    val sEnd = editable.getSpanEnd(span)
                    val flags = editable.getSpanFlags(span)
                    editable.removeSpan(span)
                    editable.setSpan(StyleSpan(Typeface.BOLD), sStart, sEnd, flags)
                }
            }
            if (!currentlyItalic) {
                val boldSpans = editable.getSpans(applyStart, end, StyleSpan::class.java).filter { it.style == Typeface.BOLD }
                if (boldSpans.isNotEmpty()) {
                    for (span in boldSpans) {
                        val sStart = editable.getSpanStart(span)
                        val sEnd = editable.getSpanEnd(span)
                        val flags = editable.getSpanFlags(span)
                        editable.removeSpan(span)
                        editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), sStart, sEnd, flags)
                    }
                } else {
                    editable.setSpan(StyleSpan(Typeface.ITALIC), applyStart, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                }
            }

            val afterState = SpannableStringBuilder(editable, applyStart, end)
            if (::undoRedoHelper.isInitialized) {
                undoRedoHelper.addEdit(applyStart, beforeState, afterState)
            }
        } else {
            if (currentlyItalic) {
                isPendingItalic = false
                val spans = editable.getSpans(0, editable.length, StyleSpan::class.java)
                for (span in spans) {
                    if (span.style == Typeface.ITALIC) {
                        val sEnd = editable.getSpanEnd(span)
                        if (sEnd >= start) {
                            val sStart = editable.getSpanStart(span)
                            editable.removeSpan(span)
                            if (sStart < start) {
                                editable.setSpan(StyleSpan(Typeface.ITALIC), sStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                    } else if (span.style == Typeface.BOLD_ITALIC) {
                        val sEnd = editable.getSpanEnd(span)
                        if (sEnd >= start) {
                            val sStart = editable.getSpanStart(span)
                            editable.removeSpan(span)
                            if (sStart < start) {
                                editable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), sStart, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                            editable.setSpan(StyleSpan(Typeface.BOLD), start, sEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
                        }
                    }
                }
            } else {
                isPendingItalic = true
            }
        }
        updateFormattingButtonHighlights()
    }

    private fun detectPrefixType(lineText: String, editable: Editable? = null, lineStart: Int = 0, lineEnd: Int = 0): PrefixType {
        if (editable != null && lineEnd > lineStart) {
            val headingSpans = editable.getSpans(lineStart, lineEnd, HeadingSpan::class.java)
            if (headingSpans.isNotEmpty()) {
                val h = headingSpans.first()
                if (h.level == 1) return PrefixType.H1
                if (h.level == 2) return PrefixType.H2
            }
        }
        val trimmed = lineText.trimStart()
        return when {
            trimmed.startsWith("## ") || trimmed == "##" -> PrefixType.H2
            trimmed.startsWith("# ") || trimmed == "#" -> PrefixType.H1
            trimmed.startsWith("☐ ") || trimmed.startsWith("☑ ") || trimmed == "☐" || trimmed == "☑" ||
                    trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") ||
                    trimmed == "- [ ]" || trimmed == "- [x]" || trimmed == "- [X]" -> PrefixType.CHECKLIST
            trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                    trimmed == "•" || trimmed == "-" || trimmed == "*" -> PrefixType.BULLET
            Regex("^\\d+\\.\\s+").containsMatchIn(trimmed) || Regex("^\\d+\\.$").containsMatchIn(trimmed) -> PrefixType.NUMBERED
            else -> PrefixType.NONE
        }
    }

    private fun stripPrefix(lineText: String): String {
        val leadingSpaces = lineText.takeWhile { it == ' ' || it == '\t' }
        val trimmed = lineText.substring(leadingSpaces.length)
        var stripped = when {
            trimmed.startsWith("## ") -> trimmed.substring(3)
            trimmed == "##" -> ""
            trimmed.startsWith("# ") -> trimmed.substring(2)
            trimmed == "#" -> ""
            trimmed.startsWith("☐ ") || trimmed.startsWith("☑ ") -> trimmed.substring(2)
            trimmed == "☐" || trimmed == "☑" -> ""
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> trimmed.substring(6)
            trimmed == "- [ ]" || trimmed == "- [x]" || trimmed == "- [X]" -> ""
            trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") -> trimmed.substring(2)
            trimmed == "•" || trimmed == "-" || trimmed == "*" -> ""
            Regex("^\\d+\\.\\s+").containsMatchIn(trimmed) -> trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), "")
            Regex("^\\d+\\.$").containsMatchIn(trimmed) -> trimmed.replaceFirst(Regex("^\\d+\\.$"), "")
            else -> trimmed
        }

        stripped = stripped.replace(Regex("[•\\-\\s]+$"), "")
        stripped = stripped.replace(Regex("(\\s*\\d+\\.\\s*)+$"), "")

        return leadingSpaces + stripped
    }

    private fun toggleLinePrefix(targetType: PrefixType) {
        val editable = binding.etNote.text ?: return
        val text = editable.toString()
        val selStart = binding.etNote.selectionStart.coerceIn(0, editable.length)
        val selEnd = binding.etNote.selectionEnd.coerceIn(0, editable.length)

        val startLineOffset = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)).let {
            if (it < 0) 0 else it + 1
        }
        val nextNewline = text.indexOf('\n', selEnd)
        val endLineOffset = if (nextNewline < 0) editable.length else nextNewline

        val blockText = text.substring(startLineOffset, endLineOffset)
        val rawLines = if (targetType != PrefixType.NONE && blockText.contains(" • ")) {
            blockText.split(Regex("\\s*•\\s*")).filter { it.isNotEmpty() }
        } else {
            blockText.split("\n")
        }

        var currentOffset = startLineOffset
        val allSelectedHaveTargetType = rawLines.all { line ->
            val lEnd = currentOffset + line.length
            val res = detectPrefixType(line, editable, currentOffset, lEnd) == targetType
            currentOffset = lEnd + 1
            res
        }

        val oldHeadingSpans = editable.getSpans(startLineOffset, endLineOffset, HeadingSpan::class.java)
        for (span in oldHeadingSpans) {
            editable.removeSpan(span)
        }
        val oldSizeSpans = editable.getSpans(startLineOffset, endLineOffset, RelativeSizeSpan::class.java)
        for (span in oldSizeSpans) {
            editable.removeSpan(span)
        }

        val newLines = ArrayList<String>(rawLines.size)
        for ((index, line) in rawLines.withIndex()) {
            if (allSelectedHaveTargetType) {
                if (targetType == PrefixType.CHECKLIST) {
                    val trimmed = line.trimStart()
                    val leading = line.takeWhile { it == ' ' || it == '\t' }
                    if (trimmed.startsWith("☐ ")) {
                        newLines.add(leading + "☑ " + trimmed.substring(2))
                    } else if (trimmed.startsWith("- [ ] ")) {
                        newLines.add(leading + "☑ " + trimmed.substring(6))
                    } else {
                        newLines.add(stripPrefix(line))
                    }
                } else {
                    newLines.add(stripPrefix(line))
                }
            } else {
                val stripped = stripPrefix(line)
                val prefix = when (targetType) {
                    PrefixType.H1, PrefixType.H2 -> ""
                    PrefixType.BULLET -> "• "
                    PrefixType.NUMBERED -> "${index + 1}. "
                    PrefixType.CHECKLIST -> "☐ "
                    PrefixType.NONE -> ""
                }
                newLines.add(prefix + stripped)
            }
        }

        val newBlockText = newLines.joinToString("\n")
        editable.replace(startLineOffset, endLineOffset, newBlockText)

        if (!allSelectedHaveTargetType && (targetType == PrefixType.H1 || targetType == PrefixType.H2)) {
            var lineStart = startLineOffset
            for (line in newLines) {
                val lineEnd = lineStart + line.length
                val level = if (targetType == PrefixType.H1) 1 else 2
                val relSize = if (targetType == PrefixType.H1) 1.35f else 1.20f
                val flag = if (lineStart == lineEnd) Spannable.SPAN_INCLUSIVE_INCLUSIVE else Spannable.SPAN_EXCLUSIVE_INCLUSIVE
                editable.setSpan(HeadingSpan(level), lineStart, lineEnd, flag)
                editable.setSpan(RelativeSizeSpan(relSize), lineStart, lineEnd, flag)
                lineStart = lineEnd + 1
            }
        }

        val delta = newBlockText.length - blockText.length
        val newSelEnd = (selEnd + delta).coerceIn(0, editable.length)
        binding.etNote.setSelection(newSelEnd)

        updateHeadingSpans(editable)
        updateFormattingButtonHighlights()
    }

    private fun updateHeadingSpans(editable: Editable?) {
        if (editable == null) return

        val text = editable.toString()
        val headingSpans = editable.getSpans(0, editable.length, HeadingSpan::class.java)

        for (span in headingSpans) {
            val spanStart = editable.getSpanStart(span)
            val spanEnd = editable.getSpanEnd(span)

            if (spanStart >= spanEnd || spanStart >= text.length) {
                editable.removeSpan(span)
                continue
            }

            val lineStart = text.lastIndexOf('\n', (spanStart - 1).coerceAtLeast(0)).let {
                if (it < 0) 0 else it + 1
            }
            val nextNewline = text.indexOf('\n', spanStart)
            val lineEnd = if (nextNewline < 0) text.length else nextNewline

            // Truncate heading span if it extends past newline onto next line
            if (spanEnd > lineEnd) {
                editable.removeSpan(span)
                if (lineStart < lineEnd) {
                    editable.setSpan(HeadingSpan(span.level), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            val sizeSpans = editable.getSpans(lineStart, lineEnd, RelativeSizeSpan::class.java)
            for (s in sizeSpans) {
                editable.removeSpan(s)
            }

            if (lineStart < lineEnd) {
                val relSize = if (span.level == 1) 1.35f else 1.20f
                editable.setSpan(RelativeSizeSpan(relSize), lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                editable.removeSpan(span)
            }
        }
    }

    private fun handleAutoListContinuation(editable: Editable, newlineOffset: Int) {
        if (newlineOffset <= 0) return

        val text = editable.toString()
        val prevLineEnd = newlineOffset
        val prevLineStart = text.lastIndexOf('\n', prevLineEnd - 1).let {
            if (it < 0) 0 else it + 1
        }

        val prevLineText = text.substring(prevLineStart, prevLineEnd)
        val prefixType = detectPrefixType(prevLineText, editable, prevLineStart, prevLineEnd)

        if (prefixType == PrefixType.BULLET || prefixType == PrefixType.NUMBERED || prefixType == PrefixType.CHECKLIST) {
            val stripped = stripPrefix(prevLineText)
            if (stripped.trim().isEmpty()) {
                isHandlingAutoList = true
                editable.replace(prevLineStart, newlineOffset, "")
                isHandlingAutoList = false
            } else {
                val nextPrefix = when (prefixType) {
                    PrefixType.BULLET -> "• "
                    PrefixType.CHECKLIST -> "☐ "
                    PrefixType.NUMBERED -> {
                        val numMatch = Regex("^\\s*(\\d+)\\.").find(prevLineText)
                        val nextNum = (numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1) + 1
                        "$nextNum. "
                    }
                    else -> ""
                }

                if (nextPrefix.isNotEmpty()) {
                    isHandlingAutoList = true
                    editable.insert(newlineOffset + 1, nextPrefix)
                    isHandlingAutoList = false
                }
            }
        }
    }

    private fun updateFormattingButtonHighlights() {
        val isBold = isStyleActive(Typeface.BOLD)
        val isItalic = isStyleActive(Typeface.ITALIC)

        val editable = binding.etNote.text
        var activePrefix = PrefixType.NONE

        if (editable != null && editable.isNotEmpty()) {
            val selStart = binding.etNote.selectionStart.coerceIn(0, editable.length)

            val text = editable.toString()
            val startLineOffset = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0)).let {
                if (it < 0) 0 else it + 1
            }
            val nextNewline = text.indexOf('\n', selStart)
            val endLineOffset = if (nextNewline < 0) text.length else nextNewline

            val currentLineText = text.substring(startLineOffset, endLineOffset)
            activePrefix = detectPrefixType(currentLineText, editable, startLineOffset, endLineOffset)
        }

        setButtonHighlight(binding.btnBold, isBold)
        setButtonHighlight(binding.btnItalic, isItalic)
        setButtonHighlight(binding.btnH1, activePrefix == PrefixType.H1)
        setButtonHighlight(binding.btnH2, activePrefix == PrefixType.H2)
        setButtonHighlight(binding.btnBulletList, activePrefix == PrefixType.BULLET)
        setButtonHighlight(binding.btnNumberedList, activePrefix == PrefixType.NUMBERED)
        setButtonHighlight(binding.btnChecklist, activePrefix == PrefixType.CHECKLIST)
    }

    private val highlightBgColor by lazy { Color.parseColor("#33FFF3B6") }

    private fun setButtonHighlight(button: ImageButton, isActive: Boolean) {
        val yellowColor = ContextCompat.getColor(this, R.color.tabSelectedTextColor)

        if (isActive) {
            button.backgroundTintList = ColorStateList.valueOf(highlightBgColor)
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(yellowColor))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(Color.WHITE))
        }
    }

    private fun getMarkdownFromEditText(): String {
        val editable = binding.etNote.text ?: return ""
        val text = editable.toString()
        if (text.isEmpty()) return ""

        val lines = text.split("\n")
        val sb = StringBuilder()
        var lineStart = 0

        for ((i, line) in lines.withIndex()) {
            val lineEnd = lineStart + line.length
            val headingSpans = editable.getSpans(lineStart, lineEnd, HeadingSpan::class.java)

            var resultLine = formatLineSpanStyles(editable, text, lineStart, lineEnd)

            if (headingSpans.isNotEmpty()) {
                val level = headingSpans.first().level
                val prefix = if (level == 1) "# " else "## "
                resultLine = prefix + resultLine
            }

            sb.append(resultLine)
            if (i < lines.size - 1) sb.append("\n")

            lineStart = lineEnd + 1
        }

        return sb.toString()
    }

    private fun formatLineSpanStyles(editable: Editable, fullText: String, start: Int, end: Int): String {
        if (start >= end) return ""
        val lineStr = fullText.substring(start, end)

        var prefixLength = 0
        var prefixStr = ""
        val leadingSpaces = lineStr.takeWhile { it == ' ' || it == '\t' }
        val trimmed = lineStr.substring(leadingSpaces.length)

        when {
            trimmed.startsWith("☐ ") -> { prefixLength = leadingSpaces.length + 2; prefixStr = leadingSpaces + "- [ ] " }
            trimmed.startsWith("☑ ") -> { prefixLength = leadingSpaces.length + 2; prefixStr = leadingSpaces + "- [x] " }
            trimmed.startsWith("- [ ] ") -> { prefixLength = leadingSpaces.length + 6; prefixStr = leadingSpaces + "- [ ] " }
            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> { prefixLength = leadingSpaces.length + 6; prefixStr = leadingSpaces + "- [x] " }
            trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") -> { prefixLength = leadingSpaces.length + 2; prefixStr = leadingSpaces + "- " }
            Regex("^\\d+\\.\\s+").containsMatchIn(trimmed) -> {
                val match = Regex("^\\d+\\.\\s+").find(trimmed)!!
                prefixLength = leadingSpaces.length + match.value.length
                prefixStr = leadingSpaces + match.value
            }
        }

        val contentStart = start + prefixLength
        if (contentStart >= end) return prefixStr + fullText.substring(contentStart, end)

        val spans = editable.getSpans(contentStart, end, StyleSpan::class.java).filter { it !is HeadingSpan }
        if (spans.isEmpty()) return prefixStr + fullText.substring(contentStart, end)

        val styles = IntArray(end - contentStart)
        for (span in spans) {
            val s = editable.getSpanStart(span).coerceAtLeast(contentStart) - contentStart
            val e = editable.getSpanEnd(span).coerceAtMost(end) - contentStart
            for (k in s until e) {
                if (k in styles.indices) {
                    if (span.style == Typeface.BOLD) styles[k] = styles[k] or 1
                    else if (span.style == Typeface.ITALIC) styles[k] = styles[k] or 2
                    else if (span.style == Typeface.BOLD_ITALIC) styles[k] = styles[k] or 3
                }
            }
        }

        val contentText = fullText.substring(contentStart, end)
        val sb = StringBuilder()
        var currentStyle = 0
        var chunkStart = 0

        for (i in 0..contentText.length) {
            val style = if (i < contentText.length) styles[i] else 0
            if (style != currentStyle || i == contentText.length) {
                if (chunkStart < i) {
                    val chunk = contentText.substring(chunkStart, i)
                    val trimmedChunk = chunk.trim()
                    if (trimmedChunk.isEmpty()) {
                        sb.append(chunk)
                    } else {
                        val leading = chunk.takeWhile { it.isWhitespace() }
                        val trailing = chunk.takeLastWhile { it.isWhitespace() }
                        val mark = when (currentStyle) {
                            1 -> "**"
                            2 -> "*"
                            3 -> "***"
                            else -> ""
                        }
                        sb.append(leading).append(mark).append(trimmedChunk).append(mark).append(trailing)
                    }
                }
                currentStyle = style
                chunkStart = i
            }
        }

        return prefixStr + sb.toString()
    }

    private fun loadContentToEditText(content: String?) {
        if (content.isNullOrEmpty()) {
            binding.etNote.setText("")
            if (::undoRedoHelper.isInitialized) {
                undoRedoHelper.clearHistory()
            }
            return
        }

        val rawText = if (MarkdownHelper.isHtmlContent(content)) {
            MarkdownHelper.convertHtmlToMarkdown(content)
        } else {
            content
        }

        val lines = rawText.split("\n")
        val processedLines = ArrayList<String>(lines.size)
        val headingLevels = IntArray(lines.size)

        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("## ")) {
                headingLevels[i] = 2
                processedLines.add(line.replaceFirst("## ", ""))
            } else if (trimmed.startsWith("# ")) {
                headingLevels[i] = 1
                processedLines.add(line.replaceFirst("# ", ""))
            } else if (trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
                processedLines.add(line.replaceFirst(Regex("-\\s*\\[[xX]\\]\\s*"), "☑ "))
            } else if (trimmed.startsWith("- [ ] ")) {
                processedLines.add(line.replaceFirst(Regex("-\\s*\\[\\s*\\]\\s*"), "☐ "))
            } else if (trimmed.startsWith("* [x] ") || trimmed.startsWith("* [X] ")) {
                processedLines.add(line.replaceFirst(Regex("\\*\\s*\\[[xX]\\]\\s*"), "☑ "))
            } else if (trimmed.startsWith("* [ ] ")) {
                processedLines.add(line.replaceFirst(Regex("\\*\\s*\\[\\s*\\]\\s*"), "☐ "))
            } else if (trimmed.startsWith("- ") && !trimmed.startsWith("- [ ]") && !trimmed.startsWith("- [x]") && !trimmed.startsWith("- [X]")) {
                processedLines.add(line.replaceFirst("- ", "• "))
            } else if (trimmed.startsWith("* ") && !trimmed.startsWith("* [ ]") && !trimmed.startsWith("* [x]")) {
                processedLines.add(line.replaceFirst("* ", "• "))
            } else {
                processedLines.add(line)
            }
        }

        val parsedText = processedLines.joinToString("\n")
        val spannable = SpannableStringBuilder(parsedText)

        // Parse ***bold italic***
        val boldItalicRegex = Regex("\\*\\*\\*(.*?)\\*\\*\\*")
        var match = boldItalicRegex.find(spannable)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            val innerText = match.groupValues[1]
            spannable.replace(start, end, innerText)
            spannable.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, start + innerText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            match = boldItalicRegex.find(spannable, start + innerText.length)
        }

        // Parse **bold**
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        match = boldRegex.find(spannable)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            val innerText = match.groupValues[1]
            spannable.replace(start, end, innerText)
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, start + innerText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            match = boldRegex.find(spannable, start + innerText.length)
        }

        // Parse *italic*
        val italicRegex = Regex("\\*(.*?)\\*")
        match = italicRegex.find(spannable)
        while (match != null) {
            val start = match.range.first
            val end = match.range.last + 1
            val innerText = match.groupValues[1]
            spannable.replace(start, end, innerText)
            spannable.setSpan(StyleSpan(Typeface.ITALIC), start, start + innerText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            match = italicRegex.find(spannable, start + innerText.length)
        }

        var currentOffset = 0
        val textString = spannable.toString()
        val finalLines = textString.split("\n")
        for ((i, line) in finalLines.withIndex()) {
            val lineEnd = currentOffset + line.length
            val level = if (i < headingLevels.size) headingLevels[i] else 0
            if (level == 1) {
                spannable.setSpan(HeadingSpan(1), currentOffset, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(RelativeSizeSpan(1.35f), currentOffset, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (level == 2) {
                spannable.setSpan(HeadingSpan(2), currentOffset, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(RelativeSizeSpan(1.20f), currentOffset, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            currentOffset = lineEnd + 1
        }

        binding.etNote.setText(spannable)
        if (::undoRedoHelper.isInitialized) {
            undoRedoHelper.clearHistory()
        }
    }

    private fun observeViewModels() {
        val userId = preferences.userId
        categoryViewModel.getAllCategories(userId).observe(this) { loaded ->
            if (loaded == null) return@observe
            categories.clear()
            categories.addAll(loaded)
            populateCategoryChips()
        }

        val currentNoteId = noteId
        if (currentNoteId != null) {
            noteViewModel.getNoteById(currentNoteId).observe(this) { note ->
                if (note == null) return@observe
                isEditing = true
                binding.etTitle.setText(note.title)

                loadContentToEditText(note.content)

                selectedColor = note.colorHex ?: defaultColor
                originalCreatedAt = note.createdAt
                isPinned = note.isPinned
                selectedCategoryId = note.categoryId
                updatePinUI()
                updateBackgroundColor()
                updateSelectedChip()
                updateMetadataLine()
            }
        }
    }

    private fun populateCategoryChips() {
        binding.categoryChipGroup.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (category in categories) {
            if ("all".equals(category.id, ignoreCase = true)) continue
            val chip = inflater.inflate(R.layout.item_category_chip, binding.categoryChipGroup, false) as Chip
            chip.text = category.name.uppercase(Locale.ROOT)
            chip.tag = category.id
            chip.id = View.generateViewId()

            if (lastAddedCategoryName != null && lastAddedCategoryName.equals(category.name, ignoreCase = true)) {
                selectedCategoryId = category.id
                lastAddedCategoryName = null
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCategoryId = chip.tag as? String
                } else if (binding.categoryChipGroup.checkedChipId == View.NO_ID) {
                    selectedCategoryId = null
                }
                updateChipColors()
            }
            binding.categoryChipGroup.addView(chip)
        }

        val addChip = inflater.inflate(R.layout.item_category_chip, binding.categoryChipGroup, false) as Chip
        addChip.text = " + "
        addChip.chipIcon = ContextCompat.getDrawable(this, R.drawable.ic_add_small)
        addChip.isCheckable = false
        addChip.setOnClickListener { showQuickAddCategoryDialog() }
        binding.categoryChipGroup.addView(addChip)

        updateSelectedChip()
        updateChipColors()
    }

    private fun showQuickAddCategoryDialog() {
        CommonDialogs.showInputDialog(this, "Enter category name", "Add", "Cancel") { name ->
            if (name.isNullOrBlank()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@showInputDialog
            }
            val trimmedName = name.trim()
            val entity = CategoryEntity().apply {
                this.name = trimmedName
                this.order = categories.size
                this.userId = preferences.userId
            }

            lastAddedCategoryName = trimmedName
            categoryViewModel.insertCategory(entity)
            Toast.makeText(this, "Category added", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelectedChip() {
        val catId = selectedCategoryId ?: run {
            binding.categoryChipGroup.clearCheck()
            return
        }
        for (i in 0 until binding.categoryChipGroup.childCount) {
            val chip = binding.categoryChipGroup.getChildAt(i) as? Chip ?: continue
            if (catId == chip.tag) {
                chip.isChecked = true
                binding.categoryScrollView.post {
                    val scrollX = chip.left - (binding.categoryScrollView.width / 2) + (chip.width / 2)
                    binding.categoryScrollView.smoothScrollTo(maxOf(0, scrollX), 0)
                }
                break
            }
        }
    }

    private fun updateChipColors() {
        val baseColor = try {
            Color.parseColor(selectedColor)
        } catch (e: Exception) {
            Color.WHITE
        }

        val luminance = ColorUtils.calculateLuminance(baseColor)

        // Selected Category Chip: Solid Black background, White text
        val selectedBg = Color.BLACK
        val selectedText = Color.WHITE

        // Normal / Deselected Category Chip: Soft translucent tint
        val unselectedBg = if (luminance > 0.45) {
            ColorUtils.blendARGB(baseColor, Color.WHITE, 0.50f)
        } else {
            ColorUtils.blendARGB(baseColor, Color.BLACK, 0.25f)
        }
        val unselectedText = if (luminance > 0.45) Color.argb(200, 0, 0, 0) else Color.argb(200, 255, 255, 255)

        val bgStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(selectedBg, unselectedBg)
        )

        val textStateList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(selectedText, unselectedText)
        )

        for (i in 0 until binding.categoryChipGroup.childCount) {
            val chip = binding.categoryChipGroup.getChildAt(i) as? Chip ?: continue
            chip.chipBackgroundColor = bgStateList
            chip.setTextColor(textStateList)
            chip.chipStrokeWidth = 0f
        }
    }

    private fun handleIncomingIntent() {
        val intent = intent ?: return
        if (intent.hasExtra(EXTRA_ITEM_ID)) {
            noteId = intent.getStringExtra(EXTRA_ITEM_ID)
            isEditing = noteId != null
        }
        if (!isEditing && intent.hasExtra("selectedCategoryId")) {
            selectedCategoryId = intent.getStringExtra("selectedCategoryId")
        }
    }

    private fun saveNote() {
        val title = binding.etTitle.text.toString().trim()
        val markdownContent = getMarkdownFromEditText().trim()

        if (title.isEmpty() && markdownContent.isEmpty()) {
            Toast.makeText(this, "Cannot save empty note", Toast.LENGTH_SHORT).show()
            return
        }

        performSave(title, markdownContent)
    }

    private fun performSave(title: String, markdownContent: String) {
        val timestamp = System.currentTimeMillis()
        val userId = preferences.userId

        val note = NoteEntity().apply {
            if (isEditing && noteId != null) {
                this.id = noteId!!
            }
            this.userId = userId
            this.title = title
            this.content = markdownContent
            this.createdAt = if (isEditing) originalCreatedAt else timestamp
            this.updatedAt = timestamp
            this.categoryId = selectedCategoryId
            this.colorHex = selectedColor
            this.isPinned = this@EditNoteActivity.isPinned
        }

        if (isEditing) noteViewModel.updateNote(note)
        else noteViewModel.insertNote(note)

        isNoteSaved = true
        preferences.clearDraft()
        finish()
    }

    private fun updatePinUI() {
        val color = try {
            Color.parseColor(selectedColor)
        } catch (e: Exception) {
            Color.WHITE
        }
        val luminance = ColorUtils.calculateLuminance(color)
        val activeColor = if (luminance > 0.45) ContextCompat.getColor(this, R.color.black) else ContextCompat.getColor(this, R.color.white)

        if (isPinned) {
            binding.btnPin.setImageResource(R.drawable.ic_pinned)
            ImageViewCompat.setImageTintList(binding.btnPin, ColorStateList.valueOf(ContextCompat.getColor(this, R.color.black)))
        } else {
            binding.btnPin.setImageResource(R.drawable.ic_unpinned)
            ImageViewCompat.setImageTintList(binding.btnPin, ColorStateList.valueOf(activeColor))
        }
    }

    private fun updateBackgroundColor() {
        val color = try {
            Color.parseColor(selectedColor)
        } catch (e: Exception) {
            Color.WHITE
        }
        val root = findViewById<View>(R.id.edit_note_layout)
        root?.setBackgroundColor(color)

        val luminance = ColorUtils.calculateLuminance(color)
        val textColor = if (luminance > 0.45) Color.BLACK else Color.WHITE
        val hintColor = if (luminance > 0.45) Color.argb(128, 0, 0, 0) else Color.argb(128, 255, 255, 255)

        binding.etTitle.setTextColor(textColor)
        binding.etTitle.setHintTextColor(hintColor)
        binding.tvHeaderTitle.setTextColor(textColor)
        binding.tvMetadata.setTextColor(if (luminance > 0.45) Color.argb(180, 0, 0, 0) else Color.argb(180, 255, 255, 255))
        binding.etNote.setTextColor(textColor)
        binding.etNote.setHintTextColor(hintColor)

        binding.btnBack.setColorFilter(textColor)
        binding.btnSave.setColorFilter(textColor)
        binding.btnColorPicker.backgroundTintList = ColorStateList.valueOf(color)
        updatePinUI()
        updateChipColors()
    }

    private fun restoreDraftIfNeeded() {
        if (isEditing || !preferences.hasValidDraft()) return
        binding.etTitle.setText(preferences.getString(PrefKeys.KEY_DRAFT_TITLE, ""))
        var draftContent = preferences.getString(PrefKeys.KEY_DRAFT_CONTENT, "")
        if (MarkdownHelper.isHtmlContent(draftContent)) {
            draftContent = MarkdownHelper.convertHtmlToMarkdown(draftContent)
        }
        loadContentToEditText(draftContent)
        updateMetadataLine()
    }

    override fun onPause() {
        super.onPause()
        if (!isEditing && noteId == null && !isNoteSaved) {
            preferences.saveDraft(
                binding.etTitle.text.toString(),
                getMarkdownFromEditText(),
                selectedColor
            )
        }
    }

    companion object {
        private const val TAG = "EditNoteActivity"
        const val EXTRA_ITEM_ID = "itemId"
    }
}
