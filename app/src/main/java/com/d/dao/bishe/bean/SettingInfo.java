package com.d.dao.bishe.bean;

import java.io.Serializable;
import java.util.List;

/**
 * Created by dao on 2017/3/4.
 */

public class SettingInfo implements Serializable {
    private String serverIp;
    private String serverPort;
    private int bitRate;
    private int frameRate;
    private List<HW> sizeList;
    private HW selectSize;

    @Override
    public String toString() {
        return "SettingInfo{" +
                "serverIp='" + serverIp + '\'' +
                ", serverPort='" + serverPort + '\'' +
                ", bitRate=" + bitRate +
                ", frameRate=" + frameRate +
                ", sizeList=" + sizeList +
                ", selectSize=" + selectSize +
                '}';
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getBitRate() {
        return bitRate;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public List<HW> getSizeList() {
        return sizeList;
    }

    public void setSizeList(List<HW> sizeList) {
        this.sizeList = sizeList;
    }

    public HW getSelectSize() {
        return selectSize;
    }

    public void setSelectSize(HW selectSize) {
        this.selectSize = selectSize;
    }

    public SettingInfo() {
    }
}
