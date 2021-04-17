package com.scuec.tool.duplicatefinder.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
    /**
     * 打开选择文件夹的窗口
     * @return
     */
    public static String selectFolder() {
        JFileChooser fileChooser = new JFileChooser();
        FileSystemView fsv = FileSystemView.getFileSystemView();
        fileChooser.setCurrentDirectory(fsv.getHomeDirectory());
        fileChooser.setDialogTitle("请选择扫描目录");
        fileChooser.setApproveButtonText("确定");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int retCode = fileChooser.showOpenDialog(fileChooser);
        if (JFileChooser.APPROVE_OPTION == retCode) {
            return fileChooser.getSelectedFile().getPath();
        }
        return null;
    }
    /**
     * 打开选择文件夹的窗口
     * @return
     */
    public static List<String> selectFolders() {
        JFileChooser fileChooser = new JFileChooser();
        FileSystemView fsv = FileSystemView.getFileSystemView();
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setCurrentDirectory(fsv.getHomeDirectory());
        fileChooser.setDialogTitle("请选择扫描目录");
        fileChooser.setApproveButtonText("确定");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int retCode = fileChooser.showOpenDialog(fileChooser);
        if (JFileChooser.APPROVE_OPTION == retCode) {
            return Arrays.stream(fileChooser.getSelectedFiles()).map(f -> f.getAbsolutePath()).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    public static String getHomeDirectory() {
        return FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath();
    }

    public static List<String> clear(List<String> strList) {
        List<String> clear = new ArrayList<>();
        if (CollectionUtils.isEmpty(strList)) {
            return clear;
        }
        for (String str : strList) {
            if (StringUtils.isNotBlank(str)) {
                clear.add(str.trim().toLowerCase());
            }
        }
        return clear;
    }

    public static boolean moveTo(String srcFilePath, String newPath) {
        return moveTo(new File(srcFilePath), newPath);
    }


    public static boolean moveTo(File srcFile, String newPath) {
        mkdirs(newPath);
        File dstFile = new File(newPath + File.separator + srcFile.getName());
        if (dstFile.exists()) { // 判断文件是否存在，存在直接返回失败
            return false;
        }
        return srcFile.renameTo(dstFile);
    }

    public static void mkdirs(String path) {
        File pathFile = new File(path);
        if (!pathFile.exists() || pathFile.isFile()) { // 校验目标文件夹是否存在，不存在则新增
            pathFile.mkdirs();
            LOGGER.info("文件夹创建成功！文件夹路径：{}", pathFile.getAbsolutePath());
        }
    }

    public static boolean removeFile(File file) {
        if (file.exists()) {
            return Desktop.getDesktop().moveToTrash(file);
        }
        return false;
    }
}
