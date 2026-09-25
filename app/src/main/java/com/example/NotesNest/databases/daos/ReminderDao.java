package com.example.NotesNest.databases.daos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.NotesNest.databases.entities.ReminderEntity;

import java.util.List;

@Dao
public interface ReminderDao {

    // ------------------- Basic CRUD -------------------
    @Insert
    long insertReminder(ReminderEntity reminder);

    @Update
    int updateReminder(ReminderEntity reminder);

    @Delete
    void deleteReminder(ReminderEntity reminder);

    @Query("SELECT * FROM reminders WHERE id = :id AND userId = :userId AND isDeleted = 0")
    ReminderEntity getById(String id, String userId);

    // ------------------- Get All Reminders -------------------
    @Query("SELECT * FROM reminders WHERE userId = :userId AND isDeleted = 0 ORDER BY notificationTime ASC")
    List<ReminderEntity> getAllReminders(String userId);

    @Query("SELECT * FROM reminders WHERE userId = :userId")
    LiveData<List<ReminderEntity>> getAllRemindersLive(String userId);

    // ------------------- Get Single Reminder with LiveData -------------------
    @Query("SELECT * FROM reminders WHERE id = :id AND userId = :userId AND isDeleted = 0")
    LiveData<ReminderEntity> getReminderByIdLive(String id, String userId);

    @Query("SELECT * FROM reminders")
    List<ReminderEntity> getAllRemindersForBackup();
    
    @Query("SELECT * FROM reminders WHERE id = :reminderId LIMIT 1")
    ReminderEntity getReminderByIdSync(String reminderId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ReminderEntity> reminders);
}
