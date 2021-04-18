package com.scuec.tool.duplicatefinder.config;

import com.alibaba.fastjson.JSON;
import com.scuec.tool.duplicatefinder.util.Utils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;

public class ConfigUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUtils.class);
    private static final String DEFAULT_ROOT_NAME = ".duplicatefinder";
    private static final String CONFIG_NAME = "setting.json";

    public static Config loadConfig() {
        return loadConfig(Utils.getHomeDirectory());
    }

    public static Config loadConfig(String root) {
        File file = new File(getConfigFilePath(root));
        if (null != file && file.exists() && file.isFile()) {
            try {
                String text = FileUtils.readFileToString(file, Charset.defaultCharset());
                return JSON.parseObject(text, Config.class);
            } catch (IOException e) {
                LOGGER.warn("配置文件加载异常！", e);
            }
        } else {
            Utils.mkdirs(getAbsoluteRoot(root));
        }
        return new Config();
    }

    public static boolean saveConfig(Config config) {
        return saveConfig(config, Utils.getHomeDirectory());
    }

    public static boolean saveConfig(Config config, String root) {
        if (null != config) {
            String filepath = getConfigFilePath(root);
            try {
                PrintWriter pw = new PrintWriter(filepath);
                try {
                    pw.write(JSON.toJSONString(config));
                } finally {
                    if (null != pw) {
                        pw.close();
                    }
                }
                return true;
            } catch (FileNotFoundException e) {
                LOGGER.warn("配置文件保存异常！", e);
            }

        }
        return false;
    }

    private static String getConfigFilePath(String root) {
        return root + File.separator + DEFAULT_ROOT_NAME + File.separator + CONFIG_NAME;
    }

    private static String getAbsoluteRoot(String root) {
        return root + File.separator + DEFAULT_ROOT_NAME;
    }
}
