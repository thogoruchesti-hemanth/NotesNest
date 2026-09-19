package com.example.NotesNest.databases;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.NotesNest.databases.daos.CategoryDao;
import com.example.NotesNest.databases.daos.NoteDao;
import com.example.NotesNest.databases.daos.ReminderDao;
import com.example.NotesNest.databases.entities.CategoryEntity;
import com.example.NotesNest.databases.entities.NoteEntity;
import com.example.NotesNest.databases.entities.NoteFTSEntity;
import com.example.NotesNest.databases.entities.ReminderEntity;

@Database(
        entities = {
                NoteEntity.class,
                CategoryEntity.class,
                NoteFTSEntity.class,
                ReminderEntity.class
        },
        version = 7
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reminders ADD COLUMN isDone INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 1. Migrate Categories Table
            database.execSQL("ALTER TABLE categories RENAME TO categories_old");
            database.execSQL("CREATE TABLE categories (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "name TEXT, " +
                    "`order` INTEGER NOT NULL, " +
                    "userId TEXT)");
            database.execSQL("INSERT INTO categories (id, name, `order`, userId) " +
                    "SELECT CAST(id AS TEXT), name, `order`, userId FROM categories_old");
            database.execSQL("DROP TABLE categories_old");

            // 2. Migrate Notes Table
            database.execSQL("ALTER TABLE notes RENAME TO notes_old");
            database.execSQL("CREATE TABLE notes (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT, " +
                    "categoryId TEXT, " +
                    "title TEXT, " +
                    "content TEXT, " +
                    "colorHex TEXT, " +
                    "createdAt INTEGER NOT NULL, " +
                    "updatedAt INTEGER NOT NULL, " +
                    "isSynced INTEGER NOT NULL, " +
                    "isDeleted INTEGER NOT NULL, " +
                    "isPinned INTEGER NOT NULL, " +
                    "FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE SET NULL)");
            
            // Note: Transferring data. We assume 'message' was renamed to 'content' and 'isPinned' is new (default 0)
            database.execSQL("INSERT INTO notes (id, userId, categoryId, title, content, colorHex, createdAt, updatedAt, isSynced, isDeleted, isPinned) " +
                    "SELECT CAST(id AS TEXT), userId, CAST(categoryId AS TEXT), title, content, colorHex, createdAt, updatedAt, isSynced, isDeleted, 0 FROM notes_old");
            database.execSQL("DROP TABLE notes_old");
            database.execSQL("CREATE INDEX index_notes_userId ON notes(userId)");
            database.execSQL("CREATE INDEX index_notes_categoryId ON notes(categoryId)");

            // 3. Migrate Reminders Table
            database.execSQL("ALTER TABLE reminders RENAME TO reminders_old");
            database.execSQL("CREATE TABLE reminders (" +
                    "id TEXT NOT NULL PRIMARY KEY, " +
                    "userId TEXT, " +
                    "type TEXT, " +
                    "title TEXT, " +
                    "message TEXT, " +
                    "name TEXT, " +
                    "notificationTime INTEGER NOT NULL, " +
                    "isRepeated INTEGER NOT NULL, " +
                    "repeatType TEXT, " +
                    "notifyType TEXT, " +
                    "gradientStartColor INTEGER NOT NULL, " +
                    "gradientEndColor INTEGER NOT NULL, " +
                    "isDeleted INTEGER NOT NULL)");
            database.execSQL("INSERT INTO reminders (id, userId, type, title, message, name, notificationTime, isRepeated, repeatType, notifyType, gradientStartColor, gradientEndColor, isDeleted) " +
                    "SELECT CAST(id AS TEXT), userId, type, title, message, name, notificationTime, isRepeated, repeatType, notifyType, gradientStartColor, gradientEndColor, isDeleted FROM reminders_old");
            database.execSQL("DROP TABLE reminders_old");
            database.execSQL("CREATE INDEX index_reminders_userId ON reminders(userId)");
            database.execSQL("CREATE INDEX index_reminders_notificationTime ON reminders(notificationTime)");
            
            // 4. Rebuild FTS Table
            database.execSQL("DROP TABLE IF EXISTS notes_fts");
            database.execSQL(String.format("CREATE VIRTUAL TABLE %s USING fts4(content='%s', %s, %s)", 
                    "notes_fts", "notes", "title", "content"));
        }
    };

    // ------------------- Singleton -------------------
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "notesnest.db"
                            )
                            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                            .fallbackToDestructiveMigration()
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    // ------------------- DAOs -------------------
    public abstract NoteDao noteDao();

    public abstract CategoryDao categoryDao();

    public abstract ReminderDao reminderDao();

}
