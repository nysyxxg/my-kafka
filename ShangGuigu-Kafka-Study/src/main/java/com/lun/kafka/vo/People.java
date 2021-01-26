package com.lun.kafka.vo;

public class People {
    public String id;
    public String name;
    public String age;
    public String sex;
    public String title;
    public String sfzh;
    
    public People(){
    }
    public People(String id, String name, String age, String sex, String title, String sfzh, PeopleInfo peopleInfo) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.sex = sex;
        this.title = title;
        this.sfzh = sfzh;
        this.peopleInfo = peopleInfo;
    }
    
    public PeopleInfo peopleInfo;
    
    public PeopleInfo getPeopleInfo() {
        return peopleInfo;
    }
    
    public void setPeopleInfo(PeopleInfo peopleInfo) {
        this.peopleInfo = peopleInfo;
    }
    
    public String getSfzh() {
        return sfzh;
    }
    
    public void setSfzh(String sfzh) {
        this.sfzh = sfzh;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getAge() {
        return age;
    }
    
    public void setAge(String age) {
        this.age = age;
    }
    
    public String getSex() {
        return sex;
    }
    
    public void setSex(String sex) {
        this.sex = sex;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    @Override
    public String toString() {
        return "People{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", age='" + age + '\'' +
                ", sex='" + sex + '\'' +
                ", title='" + title + '\'' +
                ", sfzh='" + sfzh + '\'' +
                ", peopleInfo=" + peopleInfo +
                '}';
    }
}
