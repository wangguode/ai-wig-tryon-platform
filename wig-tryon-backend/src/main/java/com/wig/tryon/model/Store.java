package com.wig.tryon.model;

import java.util.List;

/**
 * 门店信息。
 */
public class Store {

    public Long id;
    public String name;
    public String address;
    public String district;
    public String distance;
    public double rating;
    public int reviews;
    public String hours;
    public String phone;
    public boolean isOpen;
    public String image;
    public List<String> tags;
    public List<String> cases;
    public String advisor;
    public String wecomLink;     // 企业微信添加链接（核心转化入口）
    public double latitude;
    public double longitude;

    public Store() {
    }
}
