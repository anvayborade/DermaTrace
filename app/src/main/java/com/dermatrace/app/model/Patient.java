package com.dermatrace.app.model;

public class Patient {
    public long id;
    public String name;
    public String age;
    public String sex;
    public String notes;
    public long createdAt;

    @Override
    public String toString() {
        return name;
    }
}
