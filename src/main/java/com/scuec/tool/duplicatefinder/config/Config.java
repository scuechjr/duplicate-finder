package com.scuec.tool.duplicatefinder.config;

import com.scuec.tool.duplicatefinder.enums.ProcessTypeEnum;
import com.scuec.tool.duplicatefinder.enums.ScanFileTypeEnum;
import com.scuec.tool.duplicatefinder.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class Config {
    private List<String> scanFolderList = new ArrayList<>();
    private ScanFileTypeEnum scanFileType = ScanFileTypeEnum.ALL;
    private List<String> scanFileTypeList = new ArrayList<>();
    private ProcessTypeEnum processType = ProcessTypeEnum.SCAN;
    private String scanLogRoot = Utils.getHomeDirectory();

    public List<String> getScanFolderList() {
        return scanFolderList;
    }

    public void setScanFolderList(List<String> scanFolderList) {
        this.scanFolderList = scanFolderList;
    }

    public ScanFileTypeEnum getScanFileType() {
        return scanFileType;
    }

    public void setScanFileType(ScanFileTypeEnum scanFileType) {
        this.scanFileType = scanFileType;
    }

    public List<String> getScanFileTypeList() {
        return scanFileTypeList;
    }

    public void setScanFileTypeList(List<String> scanFileTypeList) {
        this.scanFileTypeList = scanFileTypeList;
    }

    public ProcessTypeEnum getProcessType() {
        return processType;
    }

    public void setProcessType(ProcessTypeEnum processType) {
        this.processType = processType;
    }

    public String getScanLogRoot() {
        return scanLogRoot;
    }

    public void setScanLogRoot(String scanLogRoot) {
        this.scanLogRoot = scanLogRoot;
    }
}
