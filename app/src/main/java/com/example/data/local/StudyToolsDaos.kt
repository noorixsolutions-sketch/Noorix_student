package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM study_bookmarks WHERE bookmarkId = :bookmarkId")
    suspend fun delete(bookmarkId: String)

    @Query("SELECT * FROM study_bookmarks ORDER BY createdAtMs DESC")
    fun allFlow(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM study_bookmarks WHERE conceptId = :conceptId ORDER BY createdAtMs DESC")
    fun forConceptFlow(conceptId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM study_bookmarks WHERE bookmarkId = :bookmarkId")
    suspend fun exists(bookmarkId: String): Int
}

@Dao
interface StudyNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: StudyNoteEntity)

    @Query("DELETE FROM study_notes WHERE noteId = :noteId")
    suspend fun delete(noteId: String)

    @Query("SELECT * FROM study_notes WHERE conceptId = :conceptId ORDER BY updatedAtMs DESC")
    fun forConceptFlow(conceptId: String): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes ORDER BY updatedAtMs DESC")
    fun allFlow(): Flow<List<StudyNoteEntity>>
}

@Dao
interface FlashcardProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: FlashcardProgressEntity)

    @Query("SELECT * FROM flashcard_progress")
    fun allFlow(): Flow<List<FlashcardProgressEntity>>

    @Query("SELECT * FROM flashcard_progress WHERE conceptId = :conceptId")
    fun forConceptFlow(conceptId: String): Flow<List<FlashcardProgressEntity>>

    @Query("SELECT * FROM flashcard_progress WHERE cardId = :cardId LIMIT 1")
    suspend fun forCard(cardId: String): FlashcardProgressEntity?
}
