package com.example.NotesNest.backups;

import android.content.Context;
import android.util.Log;

import com.example.NotesNest.databases.AppDatabase;
import com.example.NotesNest.databases.entities.CategoryEntity;
import com.example.NotesNest.databases.entities.NoteEntity;
import com.example.NotesNest.databases.entities.ReminderEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BackupProcessor {

    private static final String TAG = "BackupProcessor";
    private final AppDatabase database;
    private final Gson gson;

    public BackupProcessor(Context context) {
        this.database = AppDatabase.getInstance(context);
        this.gson = new GsonBuilder().create();
    }

    /**
     * Streams the entire database directly to an OutputStream.
     * Fixes OutOfMemoryError for users with thousands of notes.
     */
    public void exportToStream(OutputStream out) throws IOException {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.beginObject(); // Root object

            // 1. Export Notes
            writer.name("notes");
            writer.beginArray();
            List<NoteEntity> notes = database.noteDao().getAllNotesForBackup();
            for (NoteEntity note : notes) {
                gson.toJson(note, NoteEntity.class, writer);
            }
            writer.endArray();

            // 2. Export Categories
            writer.name("categories");
            writer.beginArray();
            List<CategoryEntity> categories = database.categoryDao().getAllCategoriesForBackup();
            for (CategoryEntity category : categories) {
                gson.toJson(category, CategoryEntity.class, writer);
            }
            writer.endArray();

            // 3. Export Reminders
            writer.name("reminders");
            writer.beginArray();
            List<ReminderEntity> reminders = database.reminderDao().getAllRemindersForBackup();
            for (ReminderEntity reminder : reminders) {
                gson.toJson(reminder, ReminderEntity.class, writer);
            }
            writer.endArray();

            writer.endObject(); // End Root object
            writer.flush();
        }
    }

    /**
     * Streams the database from an InputStream and implements a true Merge strategy based on updatedAt.
     * Prevents older backups from overwriting newer local edits.
     */
    public void importFromStream(InputStream in) throws IOException {
        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.beginObject(); // Start reading the root object

            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("notes")) {
                    processNotesStream(reader);
                } else if (name.equals("categories")) {
                    processCategoriesStream(reader);
                } else if (name.equals("reminders")) {
                    processRemindersStream(reader);
                } else {
                    reader.skipValue(); // Skip unknown keys (e.g., from future versions)
                }
            }

            reader.endObject(); // End Root object
        }
    }

    private void processNotesStream(JsonReader reader) throws IOException {
        reader.beginArray();
        List<NoteEntity> batch = new ArrayList<>();
        while (reader.hasNext()) {
            NoteEntity backupNote = gson.fromJson(reader, NoteEntity.class);
            batch.add(backupNote);
            if (batch.size() >= 100) {
                processNoteBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            processNoteBatch(batch);
        }
        reader.endArray();
    }

    private void processNoteBatch(List<NoteEntity> batch) {
        database.runInTransaction(() -> {
            for (NoteEntity backupNote : batch) {
                NoteEntity localNote = database.noteDao().getNoteByIdSync(backupNote.id);
                if (localNote == null) {
                    database.noteDao().insert(backupNote);
                } else {
                    // Conflict Resolution: Only overwrite if backup is NEWER
                    if (backupNote.updatedAt > localNote.updatedAt) {
                        database.noteDao().update(backupNote);
                    } else {
                        Log.d(TAG, "Skipped older backup note: " + backupNote.id);
                    }
                }
            }
        });
    }

    private void processCategoriesStream(JsonReader reader) throws IOException {
        reader.beginArray();
        List<CategoryEntity> batch = new ArrayList<>();
        while (reader.hasNext()) {
            CategoryEntity backupCategory = gson.fromJson(reader, CategoryEntity.class);
            batch.add(backupCategory);
            if (batch.size() >= 50) {
                processCategoryBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            processCategoryBatch(batch);
        }
        reader.endArray();
    }

    private void processCategoryBatch(List<CategoryEntity> batch) {
        database.runInTransaction(() -> {
            for (CategoryEntity backupCategory : batch) {
                CategoryEntity localCategory = database.categoryDao().getCategoryByIdSync(backupCategory.id);
                if (localCategory == null) {
                    database.categoryDao().insert(backupCategory);
                } else {
                    database.categoryDao().update(backupCategory);
                }
            }
        });
    }

    private void processRemindersStream(JsonReader reader) throws IOException {
        reader.beginArray();
        List<ReminderEntity> batch = new ArrayList<>();
        while (reader.hasNext()) {
            ReminderEntity backupReminder = gson.fromJson(reader, ReminderEntity.class);
            batch.add(backupReminder);
            if (batch.size() >= 50) {
                processReminderBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            processReminderBatch(batch);
        }
        reader.endArray();
    }

    private void processReminderBatch(List<ReminderEntity> batch) {
        database.runInTransaction(() -> {
            for (ReminderEntity backupReminder : batch) {
                ReminderEntity localReminder = database.reminderDao().getReminderByIdSync(backupReminder.id);
                if (localReminder == null) {
                    database.reminderDao().insertReminder(backupReminder);
                } else {
                    database.reminderDao().updateReminder(backupReminder);
                }
            }
        });
    }
}