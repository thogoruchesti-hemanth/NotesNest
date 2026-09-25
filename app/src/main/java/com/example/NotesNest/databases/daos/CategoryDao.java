package com.example.NotesNest.databases.daos;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.NotesNest.databases.entities.CategoryEntity;

import java.util.List;

@Dao
public interface CategoryDao {

    // 🔹 Insert a single category (ignore duplicates by name)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(CategoryEntity category);

    // 🔹 Insert multiple categories (useful for pre-populating)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<CategoryEntity> categories);

    // 🔹 Update an existing category
    @Update
    void update(CategoryEntity category);

    // 🔹 Delete a category
    @Delete
    void delete(CategoryEntity category);

    // 🔹 Delete category by ID
    @Query("DELETE FROM categories WHERE id = :categoryId AND userId = :userId")
    void deleteCategoryById(String categoryId, String userId);

    // 🔹 Get all categories as LiveData (reactive, offline-friendly)
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    LiveData<List<CategoryEntity>> getAllCategories(String userId);

    // 🔹 Get category by ID
    @Query("SELECT * FROM categories WHERE id = :categoryId AND userId = :userId LIMIT 1")
    LiveData<CategoryEntity> getCategoryById(String categoryId, String userId);

    // 🔹 Check if category exists by name
    @Query("SELECT COUNT(*) FROM categories WHERE name = :categoryName AND userId = :userId")
    int countCategoryByName(String categoryName, String userId);

    // 🔹 Get category by name
    @Query("SELECT * FROM categories WHERE name = :categoryName AND userId = :userId LIMIT 1")
    LiveData<CategoryEntity> getCategoryByName(String categoryName, String userId);

    // 🔹 Delete by name
    @Query("DELETE FROM categories WHERE name = :categoryName AND userId = :userId")
    void deleteByName(String categoryName, String userId);

    // 🔹 Delete all categories for a user
    @Query("DELETE FROM categories WHERE userId = :userId")
    void deleteAll(String userId);

    @Query("SELECT name FROM categories WHERE id = :categoryId AND userId = :userId LIMIT 1")
    String getCategoryName(String categoryId, String userId);

    @Query("SELECT * FROM categories")
    List<CategoryEntity> getAllCategoriesForBackup();
    
    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    CategoryEntity getCategoryByIdSync(String categoryId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAllReplace(List<CategoryEntity> categories);
}
