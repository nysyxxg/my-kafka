package com.lun.kafka.vo;

public class PeopleInfo {
    public String sfzh;
    public String address;
    public String company;
    public String tel;
    public String qq;
    public String weixin;
    
    public PeopleInfo(String sfzh, String address, String company, String tel, String qq, String weixin) {
        this.sfzh = sfzh;
        this.address = address;
        this.company = company;
        this.tel = tel;
        this.qq = qq;
        this.weixin = weixin;
    }
    
    public String getWeixin() {
        return weixin;
    }
    
    public void setWeixin(String weixin) {
        this.weixin = weixin;
    }
    
    public String getSfzh() {
        return sfzh;
    }
    
    public void setSfzh(String sfzh) {
        this.sfzh = sfzh;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public String getCompany() {
        return company;
    }
    
    public void setCompany(String company) {
        this.company = company;
    }
    
    public String getTel() {
        return tel;
    }
    
    public void setTel(String tel) {
        this.tel = tel;
    }
    
    public String getQq() {
        return qq;
    }
    
    public void setQq(String qq) {
        this.qq = qq;
    }
    
    @Override
    public String toString() {
        return "PeopleInfo{" +
                "sfzh='" + sfzh + '\'' +
                ", address='" + address + '\'' +
                ", company='" + company + '\'' +
                ", tel='" + tel + '\'' +
                ", qq='" + qq + '\'' +
                ", weixin='" + weixin + '\'' +
                '}';
    }
}
