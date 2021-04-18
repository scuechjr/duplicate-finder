package com.scuec.tool.duplicatefinder.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class DuplicateFinder {
    private static final ExecutorService executor = Executors.newFixedThreadPool(20);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public interface ScanListener {
        void duplicate(String first, String duplicate);

        void process(long count);

        void finish(long count);

        void totalCount(long count);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateFinder.class);
    private static final String UNDER_LINE = "_";
    private final List<ScanListener> listeners = new ArrayList<>();
    private final Map<String, List<File>> fileMap = new ConcurrentHashMap<>();
    private final AtomicLong count = new AtomicLong(0);
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

        notifyTotalCount(count);
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
                String suffix = Utils.getFileSuffix(file.getName());
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
        fileMap.clear();
        count.set(0);
        filterSuffixes.clear();
        filterSuffixes.addAll(Utils.clear(suffixes));
    }

    private void notifyTotalCount(Long count) {
        for (ScanListener listener : listeners) {
            try {
                listener.totalCount(count);
            } catch (Throwable t) {
                LOGGER.warn("totalCount 监听器执行异常", t);
            }
        }
    }

    private void notifyDuplicate(String first, String duplicate) {
        for (ScanListener listener : listeners) {
            try {
                listener.duplicate(first, duplicate);
            } catch (Throwable t) {
                LOGGER.warn("duplicate 监听器执行异常", t);
            }
        }
    }

    private void notifyProcess(Long count) {
        for (ScanListener listener : listeners) {
            try {
                listener.process(count);
            } catch (Throwable t) {
                LOGGER.warn("process 监听器执行异常", t);
            }
        }
    }

    private void notifyFinish(Long count) {
        for (ScanListener listener : listeners) {
            try {
                listener.finish(count);
            } catch (Throwable t) {
                LOGGER.warn("finish 监听器执行异常", t);
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
                String suffix = Utils.getFileSuffix(file.getName());
                if (isFilter(suffix)) {
                    continue;
                }
                count.incrementAndGet();
                String key = mapKey(suffix, file);
                List<File> fileList = fileMap.get(key);
                if (CollectionUtils.isEmpty(fileList) || notMatchIn(file, fileList)) {
                    List<File> newFileList = Collections.singletonList(file);
                    List<File> mapFileList = fileMap.putIfAbsent(key, newFileList);
                    if (null != mapFileList) {
                        synchronized (mapFileList) {
                            List<File> diff = diff(mapFileList, fileList);
                            if (CollectionUtils.isEmpty(diff) || notMatchIn(file, fileList)) {
                                mapFileList.add(file);
                            }
                        }
                    }
                }
                notifyProcess(count.get());
            }
        }
    }

    private List<File> diff(List<File> fileList, List<File> subFileList) {
        if (CollectionUtils.isEmpty(subFileList)) {
            return fileList;
        }
        if (fileList.size() == subFileList.size()) {
            return new ArrayList<>();
        }
        List<File> diffList = new ArrayList<>();
        for (File file : fileList) {
            if (!subFileList.contains(file)) {
                diffList.add(file);
            }
        }
        return diffList;
    }

    private boolean notMatchIn(File file, List<File> fileList) {
        for (File item : fileList) {
            try {
                if (FileUtils.contentEquals(file, item)) {
                    notifyDuplicate(item.getAbsolutePath(), file.getAbsolutePath());
                    return false;
                }
            } catch (IOException e) {
                LOGGER.warn("文件比较异常，file1: {}, file2: {}", item.getAbsolutePath(), file.getAbsolutePath(), e);
            }
        }
        return true;
    }

    private String mapKey(String suffix, File file) {
        return suffix + UNDER_LINE + file.length() + UNDER_LINE + Utils.getMD5(file);
    }

    private boolean isStop() {
        return !isRunning.get();
    }

    private boolean isFilter(String suffix) {
        return CollectionUtils.isNotEmpty(filterSuffixes) && !filterSuffixes.contains(suffix);
    }
}
