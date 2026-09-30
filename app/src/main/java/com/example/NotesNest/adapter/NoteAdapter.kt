package com.example.NotesNest.adapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.NotesNest.R
import com.example.NotesNest.activity.EditNoteActivity
import com.example.NotesNest.databases.ViewModels.CategoryViewModel
import com.example.NotesNest.databases.ViewModels.NoteViewModel
import com.example.NotesNest.databases.entities.NoteEntity
import com.example.NotesNest.utils.CommonDialogs
import com.example.NotesNest.utils.DateTimeUtils
import com.example.NotesNest.utils.HtmlListConverter
import com.example.NotesNest.utils.NoteDiffCallback

class NoteAdapter(
    private val noteList: MutableList<NoteEntity>,
    private val context: Context,
    private val categoryViewModel: CategoryViewModel,
    private val noteViewModel: NoteViewModel
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_note_layout, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = noteList[position]

        holder.textViewTitle.text = note.title
        com.example.NotesNest.utils.MarkdownHelper.renderMarkdown(holder.textViewContent, note.content)

        val bgColor = try {
            Color.parseColor(note.colorHex)
        } catch (e: Exception) {
            Color.WHITE
        }

        holder.mainLayout.setCardBackgroundColor(bgColor)

        // Dynamic luminance accessibility contrast:
        // High luminance (light card) -> use dark text; Low luminance (dark card) -> use white text.
        val luminance = ColorUtils.calculateLuminance(bgColor)
        val primaryTextColor = if (luminance > 0.45) Color.BLACK else Color.WHITE
        val secondaryTextColor = if (luminance > 0.45) Color.argb(180, 0, 0, 0) else Color.argb(180, 255, 255, 255)

        holder.textViewTitle.setTextColor(primaryTextColor)
        holder.textViewContent.setTextColor(primaryTextColor)
        holder.textDate.setTextColor(secondaryTextColor)
        holder.textTime.setTextColor(secondaryTextColor)
        holder.ivPinned.setColorFilter(primaryTextColor)
        holder.readMoreView.setColorFilter(primaryTextColor)

        DateTimeUtils.setDateTime(note.createdAt, holder.textDate, holder.textTime)

        // Pinning logic
        holder.ivPinned.visibility = View.VISIBLE
        holder.ivPinned.setImageResource(if (note.isPinned) R.drawable.ic_pinned else R.drawable.ic_unpinned)
        holder.ivPinned.alpha = if (note.isPinned) 1.0f else 0.35f

        holder.ivPinned.setOnClickListener {
            val updatedNote = NoteEntity().apply {
                id = note.id
                userId = note.userId
                categoryId = note.categoryId
                title = note.title
                content = note.content
                colorHex = note.colorHex
                createdAt = note.createdAt
                isSynced = note.isSynced
                isDeleted = note.isDeleted
                isPinned = !note.isPinned
                updatedAt = System.currentTimeMillis()
            }
            noteViewModel.updateNote(updatedNote)
        }

        // On Click -> Show Full Note Dialog
        holder.mainLayout.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener

            val currentNote = noteList[currentPos]
            CommonDialogs.showNoteContentDialog(
                context,
                currentNote,
                object : CommonDialogs.NoteActionCallback {
                    override fun onNoteUpdated(n: NoteEntity) {
                        noteViewModel.updateNote(n)
                    }

                    override fun setDateTime(timeStamp: Long, dateView: TextView, timeView: TextView) {
                        DateTimeUtils.setDateTime(timeStamp, dateView, timeView)
                    }

                    override fun setCategory(categoryView: TextView, categoryId: String?) {
                        bindCategory(categoryId, currentNote.userId, categoryView)
                    }
                }
            )
        }

        // On Long Click -> Show Options Dialog
        holder.mainLayout.setOnLongClickListener { view ->
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@setOnLongClickListener true

            val currentNote = noteList[currentPos]
            CommonDialogs.showOptionsDialog(
                view,
                currentNote,
                currentPos,
                object : CommonDialogs.NoteOptionsListener {
                    override fun onEdit(n: NoteEntity) {
                        val intent = Intent(context, EditNoteActivity::class.java).apply {
                            putExtra("itemId", n.id)
                            putExtra("dataType", "All Notes")
                        }
                        context.startActivity(intent)
                    }

                    override fun onDelete(n: NoteEntity, pos: Int) {
                        deleteNote(n, pos)
                    }

                    override fun onPin(n: NoteEntity) {
                        val updatedNote = NoteEntity().apply {
                            id = n.id
                            userId = n.userId
                            categoryId = n.categoryId
                            title = n.title
                            content = n.content
                            colorHex = n.colorHex
                            createdAt = n.createdAt
                            isSynced = n.isSynced
                            isDeleted = n.isDeleted
                            isPinned = !n.isPinned
                            updatedAt = System.currentTimeMillis()
                        }
                        noteViewModel.updateNote(updatedNote)
                    }
                }
            )
            true
        }
    }

    override fun onViewRecycled(holder: NoteViewHolder) {
        super.onViewRecycled(holder)
        holder.textViewContent.text = null
        holder.readMoreView.visibility = View.GONE
    }

    private fun bindCategory(categoryId: String?, userId: String?, categoryView: TextView?) {
        if (categoryView == null || categoryId == null || userId == null) {
            categoryView?.visibility = View.GONE
            return
        }

        (context as? LifecycleOwner)?.let { lifecycleOwner ->
            categoryViewModel.getCategoryById(categoryId, userId).observe(lifecycleOwner) { category ->
                if (category != null) {
                    categoryView.text = category.name
                    categoryView.visibility = View.VISIBLE
                } else {
                    categoryView.visibility = View.GONE
                }
            }
        }
    }

    private fun deleteNote(note: NoteEntity, position: Int) {
        noteViewModel.deleteNote(note)
        (context as? Activity)?.runOnUiThread {
            notifyItemRemoved(position)
        }
    }

    override fun getItemCount(): Int = noteList.size

    fun updateData(newNotes: List<NoteEntity>?, isCategoryChange: Boolean = false) {
        if (newNotes == null) return

        if (isCategoryChange) {
            noteList.clear()
            noteList.addAll(newNotes)
            notifyDataSetChanged()
        } else {
            val diffCallback = NoteDiffCallback(ArrayList(noteList), ArrayList(newNotes))
            val diffResult = DiffUtil.calculateDiff(diffCallback)

            noteList.clear()
            noteList.addAll(newNotes)
            diffResult.dispatchUpdatesTo(this)
        }
    }

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
        val textViewContent: TextView = itemView.findViewById(R.id.tvNoteMessage)
        val textDate: TextView = itemView.findViewById(R.id.tvNoteDate)
        val textTime: TextView = itemView.findViewById(R.id.tvNoteTime)
        val mainLayout: CardView = itemView.findViewById(R.id.layoutNoteItem)
        val readMoreView: ImageView = itemView.findViewById(R.id.ivReadMoreView)
        val ivPinned: ImageView = itemView.findViewById(R.id.ivPinned)
    }
}
