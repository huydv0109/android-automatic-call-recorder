package com.github.axet.audiolibrary.data;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface RecordDao {

    @Query("SELECT * FROM records")
    List<Record> getAll();

//    @Query("SELECT * FROM record WHERE recorded_time IN (:recordTimes)")
//    List<Record> loadAllByRecordTimes(int[] recordTimes);

    @Query("SELECT * FROM records WHERE file_path = :file_path")
    Record getRecordByFilePath(String file_path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecord(Record record);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(List<Record> entities);

    @Update(onConflict = OnConflictStrategy.IGNORE)
    void update(List<Record> entities);

//    public void upsert(List<MyEntity> entities) {
//        insert(models);
//        update(models);
//    }


//    @Query("SELECT * FROM record WHERE first_name LIKE :first AND "
//            + "last_name LIKE :last LIMIT 1")
//    User findByName(String first, String last);
//
//    @Insert
//    void insertAll(User... users);
//
//    @Delete
//    void delete(User user);
}
