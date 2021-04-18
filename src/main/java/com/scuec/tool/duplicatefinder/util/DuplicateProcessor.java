package com.scuec.tool.duplicatefinder.util;

import com.alibaba.fastjson.JSON;
import com.scuec.tool.duplicatefinder.enums.ProcessTypeEnum;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DuplicateProcessor implements DuplicateFinder.ScanListener {
    private static final String ROOT_NAME = "finder";
    private static final String MOVE_DIR = "move.dir";
    private static final String DOT = ".";
    private String scanRootPath;
    private String moveDirPath;
    private ProcessTypeEnum processType;
    private FinderLogger logger;
    private String token = DateFormatUtils.format(new Date(), "yyyyMMddHHmmss");

    public DuplicateProcessor(String scanRootPath, ProcessTypeEnum processType)  {
        this.scanRootPath = scanRootPath;
        this.processType = processType;
        try {
            logger = new FinderLogger(scanRootPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void duplicate(String first, String duplicate) {
        logger.scanLog("scan", "start", first, duplicate);
        if (ProcessTypeEnum.MOVE.equals(processType)) {
            String moveDir = getMoveDir();
            if (logger.scanLog("move", "start", first, duplicate, moveDir)) {
                Utils.moveTo(duplicate, moveDir);
                logger.scanLog("move", "end", first, duplicate, moveDir);
            }
        } else if (ProcessTypeEnum.REMOVE.equals(processType)) {
            if (logger.scanLog("delete", "start", first, duplicate)) {
                Utils.removeFile(new File(duplicate));
                logger.scanLog("delete", "end", first, duplicate);
            }
        }
        logger.scanLog("scan", "end", first, duplicate);
    }

    private String getMoveDir() {
        if (StringUtils.isNotBlank(moveDirPath)) {
            return moveDirPath;
        }
        synchronized (ROOT_NAME) {
            if (StringUtils.isBlank(moveDirPath)) {
                moveDirPath = scanRootPath + File.separator + ROOT_NAME + File.separator + MOVE_DIR + DOT + DateFormatUtils.format(new Date(), "yyyyMMddHHmmss");
                Utils.mkdirs(moveDirPath);
            }
        }
        return moveDirPath;
    }

    @Override
    public void process(long count) {

    }

    @Override
    public void finish(long count) {
        logger.flush();
        this.moveDirPath = null;
    }

    @Override
    public void totalCount(long count) {

    }

    public class FinderLogger {
        private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
        private static final String SCAN_LOG = "scan.log";
        private final PrintWriter writer;

        public FinderLogger(String rootBase) throws FileNotFoundException {
            this.writer = new PrintWriter(rootBase + File.separator + ROOT_NAME + File.separator + SCAN_LOG + DOT + token);
        }

        public boolean scanLog(String type, String label, String first, String duplicate) {
            return scanLog(type, label, first, duplicate, null);
        }

        public boolean scanLog(String type, String label, String first, String duplicate, String other) {
            Map<String, String> map = new HashMap<>();
            map.put("time", DateFormatUtils.format(new Date(), DATE_PATTERN));
            map.put("type", type);
            map.put("label", label);
            map.put("first", first);
            map.put("duplicate", duplicate);
            map.put("other", other);
            return scanLog(JSON.toJSONString(map));
        }

        public boolean scanLog(String log) {
            try {
                writer.write(log + "\n");
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        public void flush() {
            writer.flush();
        }
    }
}
