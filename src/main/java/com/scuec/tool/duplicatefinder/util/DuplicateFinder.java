package com.scuec.tool.duplicatefinder.util;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class DuplicateFinder {
    private static final ExecutorService executor = Executors.newFixedThreadPool(20);
    private static final String DOT = ".";
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public interface ScanListener {
        void duplicate(String first, String duplicate);

        void process(long count);

        void finish(long count);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateFinder.class);
    private final List<ScanListener> listeners = new ArrayList<>();
    private final Map<String, String> md5AndFilePathMap = new ConcurrentHashMap<>();
    private final AtomicLong count = new AtomicLong(0);
    private List<String> scanFileTypes = new ArrayList<>();
    private final List<String> filterSuffixes = new ArrayList<>();

    public static DuplicateFinder create() {
        return new DuplicateFinder();
    }

    public static DuplicateFinder create(ScanListener listener) {
        return new DuplicateFinder(listener);
    }

    public static DuplicateFinder create(List<ScanListener> listeners) {
        return new DuplicateFinder(listeners);
    }

    public DuplicateFinder() {
    }

    public DuplicateFinder(ScanListener listener) {
        if (null != listener) {
            this.listeners.add(listener);
        }
    }

    public DuplicateFinder(List<ScanListener> listeners) {
        this.listeners.addAll(listeners);
    }

    public DuplicateFinder addListener(ScanListener listener) {
        if (null != listener) {
            this.listeners.add(listener);
        }
        return this;
    }

    public long count(String dir, String... suffixes) {
        return count(dir, Arrays.asList(suffixes));
    }

    public long count(String dir, List<String> suffixes) {
        return count(Collections.singletonList(dir), suffixes);
    }

    public long count(List<String> dirs, String... suffixes) {
        return count(dirs, Arrays.asList(suffixes));
    }

    public long count(List<String> dirs, List<String> suffixes) {
        suffixes = Utils.clear(suffixes);

        long count = 0;
        for (String dir : dirs) {
            File file = new File(dir);
            if (file.isDirectory()) {
                count += count(file, suffixes);
            }
        }

        return count;
    }

    public long count(File dir, List<String> suffixes) {
        File[] files = dir.listFiles();
        if (null == files) {
            return 0;
        }
        long count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                count += count(file, suffixes);
            } else {
                String suffix = getFileSuffix(file.getName());
                if (CollectionUtils.isNotEmpty(suffixes) && !suffixes.contains(suffix)) {
                    continue;
                }
                count++;
            }
        }
        return count;
    }

    public void scan(boolean async, String dir) {
        scan(async, dir, new ArrayList<>());
    }

    public void scan(boolean async, String dir, String... suffixes) {
        scan(async, dir, Arrays.asList(suffixes));
    }

    public void scan(boolean async, String dir, List<String> suffixes) {
        scan(async, Collections.singletonList(dir), suffixes);
    }

    public void scan(boolean async, List<String> dirs, String... suffixes) {
        scan(async, dirs, Arrays.asList(suffixes));
    }

    public void scan(boolean async, List<String> dirs, List<String> suffixes) {
        if (async) {
            scan(dirs, suffixes);
        } else {
            syncScan(dirs, suffixes);
        }
    }

    public void syncScan(List<String> dirs, List<String> suffixes) {
        if (isRunning.compareAndSet(false, true)) {
            initContext(suffixes);

            CountDownLatch cdl = new CountDownLatch(dirs.size());
            for (String dir : dirs) {
                executor.submit(() -> {
                    try {
                        scan(new File(dir));
                    } finally {
                        cdl.countDown();
                    }
                });
            }
            try {
                cdl.await();
                notifyFinish(count.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isRunning.compareAndSet(true, false);
            }
        }
    }

    public void scan(List<String> dirs, List<String> suffixes) {
        if (isRunning.compareAndSet(false, true)) {
            initContext(suffixes);

            CyclicBarrier cb = new CyclicBarrier(dirs.size(), () -> {
                notifyFinish(count.get());
                isRunning.compareAndSet(true, false);
            });

            for (String dir : dirs) {
                executor.submit(() -> {
                    try {
                        scan(new File(dir));
                    } finally {
                        try {
                            cb.await();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }


    public boolean stop() {
        return isRunning.compareAndSet(true, false);
    }

    private void initContext(List<String> suffixes) {
        md5AndFilePathMap.clear();
        count.set(0);
        filterSuffixes.clear();
        filterSuffixes.addAll(Utils.clear(suffixes));
    }

    private void notifyDuplicate(String first, String duplicate) {
        for (ScanListener listener : listeners) {
            try {
                listener.duplicate(first, duplicate);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void notifyProcess(Long count) {
        for (ScanListener listener : listeners) {
            try {
                listener.process(count);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void notifyFinish(Long count) {
        for (ScanListener listener : listeners) {
            try {
                listener.finish(count);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void scan(File dir) {
        for (File file : dir.listFiles()) {
            if (isStop()) {
                break;
            }
            if (file.isDirectory()) {
                scan(file);
            } else {
                String suffix = getFileSuffix(file.getName());
                if (isFilter(suffix)) {
                    continue;
                }
                count.incrementAndGet();
                String fileMd5 = getMD5(file);
                String filePath = md5AndFilePathMap.get(fileMd5);
                if (null != filePath) {
                    try {
                        if (fileEquals(file, new File(filePath))) {
                            notifyDuplicate(filePath, file.getAbsolutePath());
                        } else {
                            LOGGER.warn("md5重复的不同文件，file1: {}, file2: {}", filePath, file.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        LOGGER.error("文件比较异常，file1: {}, file2: {}", filePath, file.getAbsolutePath(), e);
                    }
                } else {
                    md5AndFilePathMap.put(fileMd5, file.getAbsolutePath());
                }
                notifyProcess(count.get());
            }
        }
    }

    private boolean isStop() {
        return !isRunning.get();
    }

    private boolean isFilter(String suffix) {
        return CollectionUtils.isNotEmpty(filterSuffixes) && !filterSuffixes.contains(suffix);
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
            }
        }
    }
}
