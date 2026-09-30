package com.example.NotesNest.widgets;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.example.NotesNest.R;
import com.example.NotesNest.databases.entities.NoteEntity;
import com.example.NotesNest.databases.repositories.NoteRepository;
import com.example.NotesNest.utils.AppPreferences;
import com.example.NotesNest.utils.MarkdownHelper;

import java.util.ArrayList;
import java.util.List;

public class NoteWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new NoteWidgetFactory(getApplicationContext(), intent);
    }

    private static class NoteWidgetFactory implements RemoteViewsService.RemoteViewsFactory {

        private final Context context;
        private final int widgetId;
        private final List<CharSequence> noteLines = new ArrayList<>();
        private int textColor = Color.BLACK;

        public NoteWidgetFactory(Context context, Intent intent) {
            this.context = context;
            this.widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            noteLines.clear();

            AppPreferences pref = AppPreferences.getInstance();
            if (!pref.isUserPremium()) {
                return;
            }

            String noteId = pref.getWidgetNoteId(context, widgetId);
            if (noteId == null) return;

            try {
                NoteRepository repository = new NoteRepository((Application) context.getApplicationContext());
                NoteEntity note = repository.getNoteByIdSync(noteId);

                if (note != null && note.content != null && !note.content.isEmpty()) {
                    try {
                        int bg = Color.parseColor(note.colorHex);
                        double brightness = Color.red(bg) * 0.299 + Color.green(bg) * 0.587 + Color.blue(bg) * 0.114;
                        textColor = brightness > 186 ? Color.BLACK : Color.WHITE;
                    } catch (Exception e) {
                        textColor = Color.BLACK;
                    }

                    String rawContent = note.content;
                    if (MarkdownHelper.isHtmlContent(rawContent)) {
                        rawContent = MarkdownHelper.convertHtmlToMarkdown(rawContent);
                    }

                    String[] lines = rawContent.split("\n");
                    for (String line : lines) {
                        CharSequence formatted = MarkdownHelper.renderMarkdownForWidget(line);
                        noteLines.add(formatted);
                    }
                }
            } catch (Exception ignored) {}
        }

        @Override
        public void onDestroy() {
            noteLines.clear();
        }

        @Override
        public int getCount() {
            return noteLines.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= noteLines.size()) {
                return null;
            }

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note_line_item);
            CharSequence lineText = noteLines.get(position);
            views.setTextViewText(R.id.tvNoteLine, lineText);
            views.setTextColor(R.id.tvNoteLine, textColor);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
