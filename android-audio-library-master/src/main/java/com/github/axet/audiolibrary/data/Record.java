package com.github.axet.audiolibrary.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

@Entity(tableName = "records")
public class Record {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "file_path")
    private String filePath;

    @ColumnInfo(name = "phone")
    private String phone;

    @ColumnInfo(name = "contact_name")
    private String contactName;

    @ColumnInfo(name = "contact_id")
    private String contactId;

    @ColumnInfo(name = "call_in_or_out")
    private String callType;

    @ColumnInfo(name = "now")
    private Long now;

    public Record(String filePath, String phone, String contactName, String contactId, String callType, Long now) {
        this.filePath = filePath;
        this.phone = phone;
        this.contactName = contactName;
        this.contactId = contactId;
        this.callType = callType;
        this.now = now;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public Long getNow() {
        return now;
    }

    public void setNow(Long now) {
        this.now = now;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
