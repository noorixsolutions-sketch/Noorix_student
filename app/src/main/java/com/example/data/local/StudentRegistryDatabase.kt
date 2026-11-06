package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RegisteredStudentEntity::class,
        ParentFamilyGroupEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudentRegistryDatabase : RoomDatabase() {
    abstract fun studentRegistryDao(): StudentRegistryDao
    abstract fun parentFamilyGroupDao(): ParentFamilyGroupDao

    companion object {
        @Volatile
        private var INSTANCE: StudentRegistryDatabase? = null

        fun getInstance(context: Context): StudentRegistryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudentRegistryDatabase::class.java,
                    "student_registry.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
