package com.d.dao.bishe.bean;

import java.io.Serializable;

/**
 * Created by dao on 2017/3/4.
 */

public class HW implements Serializable {
    private int height;
    private int width;

    @Override
    public String toString() {
        return "HW{" +
                "height=" + height +
                ", width=" + width +
                '}';
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public HW(int height, int width) {

        this.height = height;
        this.width = width;
    }
}
