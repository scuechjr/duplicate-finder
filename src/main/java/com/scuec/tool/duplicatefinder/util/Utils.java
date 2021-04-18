package com.scuec.tool.duplicatefinder.util;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);
    private static final String DOT = ".";


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

    /**
     * 根据文件名获取文件后缀
     *
     * @param filepath
     * @return
     */
    public static String getFileSuffix(String filepath) {
        int index = filepath.lastIndexOf(DOT);
        return index > -1 && index < filepath.length() ? filepath.substring(index + 1).toLowerCase() : null;
    }

    /**
     * 比较两个文件是否相同
     *
     * @param file1
     * @param file2
     * @return
     * @throws IOException
     */
    public static boolean fileEquals(File file1, File file2) throws IOException {
        String file1Suffix = getFileSuffix(file1.getName());
        String file2Suffix = getFileSuffix(file2.getName());
        return Objects.equals(file1Suffix, file2Suffix) && FileUtils.contentEquals(file1, file2);
    }

    /**
     * 获取一个文件的md5值(可处理大文件)
     *
     * @return md5 value
     */
    public static String getMD5(File file) {
        FileInputStream fileInputStream = null;
        try {
            MessageDigest MD5 = MessageDigest.getInstance("MD5");
            fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                MD5.update(buffer, 0, length);
            }
            return new String(Hex.encodeHex(MD5.digest()));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.warn("stream close error!", e);
            }
        }
    }
}
