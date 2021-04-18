package com.scuec.tool.duplicatefinder;

import com.scuec.tool.duplicatefinder.config.Config;
import com.scuec.tool.duplicatefinder.config.ConfigUtils;
import com.scuec.tool.duplicatefinder.enums.ProcessTypeEnum;
import com.scuec.tool.duplicatefinder.enums.ScanFileTypeEnum;
import com.scuec.tool.duplicatefinder.util.DuplicateFinder;
import com.scuec.tool.duplicatefinder.util.DuplicateProcessor;
import com.scuec.tool.duplicatefinder.util.Utils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class FinderUI extends JFrame {
    private GridBagLayout layout = new GridBagLayout();
    private GridBagConstraints constraints = new GridBagConstraints();
    private Thread finderCountThread = null;

    public FinderUI() {
        initFrame();
    }

    private void showFrame() {
        setBounds(400, 400, 800, 600);    //设置容器大小
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void initFrame() {
        this.setLayout(layout);
        constraints.fill = GridBagConstraints.BOTH;    //组件填充显示区域
        layoutComponents();
        initActionListener();
        initApplicationConfig();
    }

    private void initApplicationConfig() {
        Config config = ConfigUtils.loadConfig();

        folders.clear();
        for (String folder : config.getScanFolderList()) {
            folders.add(new Vector<>(Collections.singleton(folder)));
        }

        if (ScanFileTypeEnum.ALL.equals(config.getScanFileType())) {
            allFileTypeRadio.setSelected(true);
        } else if (ScanFileTypeEnum.NORMAL.equals(config.getScanFileType())) {
            normalFileTypeRadio.setSelected(true);
        }

        if (ProcessTypeEnum.SCAN.equals(config.getProcessType())) {
            scanRadio.setSelected(true);
        } else if (ProcessTypeEnum.MOVE.equals(config.getProcessType())) {
            moveRadio.setSelected(true);
        } else {
            removeRadio.setSelected(true);
        }

        scanRootPath.setText(config.getScanLogRoot());
    }

    private Config getApplicationConfig() {
        Config config = ConfigUtils.loadConfig();

        List<String> scanFolderList = new ArrayList<>();
        for (Vector<String> folder : folders) {
            scanFolderList.addAll(folder);
        }
        config.setScanFolderList(scanFolderList);

        if (allFileTypeRadio.isSelected()) {
            config.setScanFileType(ScanFileTypeEnum.ALL);
        } else if (normalFileTypeRadio.isSelected()) {
            config.setScanFileType(ScanFileTypeEnum.NORMAL);
        }

        if (scanRadio.isSelected()) {
            config.setProcessType(ProcessTypeEnum.SCAN);
        } else if (moveRadio.isSelected()) {
            config.setProcessType(ProcessTypeEnum.MOVE);
        } else {
            config.setProcessType(ProcessTypeEnum.REMOVE);
        }

        config.setScanLogRoot(scanRootPath.getText());

        return config;
    }

    private void initActionListener() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    ConfigUtils.saveConfig(getApplicationConfig());
                } finally {
                    super.windowClosing(e);
                }
            }
        });
        addFolder.addActionListener(e -> {
            java.util.List<String> folderPaths = Utils.selectFolders();
            if (CollectionUtils.isNotEmpty(folderPaths)) {
                folderPaths.forEach(path -> folders.addElement(new Vector(Collections.singleton(path))));
                scanFolderTable.updateUI();
            }
        });
        delFolder.addActionListener(e -> {
            if (scanFolderTable.getSelectedRowCount() > 0) {
                // 获取被选中的行号
                int[] rows = scanFolderTable.getSelectedRows();

                // 对选中的行号进行倒排序，先删除后边的，再删除前边的，防止删除越界
                java.util.List<Integer> rowList = new ArrayList<>();
                for (int row : rows) {
                    rowList.add(row);
                }
                rowList.sort(Comparator.reverseOrder());
                for (Integer row : rowList) {
                    folders.removeElementAt(row);
                }
                // 回复表格选中状态
                scanFolderTable.clearSelection();
                scanFolderTable.updateUI();
            }
        });

        scan.addActionListener(e -> {
            if (CollectionUtils.isNotEmpty(folders) && "开始扫描".equals(scan.getText())) {
                scan.setText("停止扫描");
                scanProgressBar.setStringPainted(true);
                scanProgressBar.setString("计算待扫描文件数量 ...");
                scanProgressBar.setVisible(true);
                scanResult.setVisible(false);

                // 启动扫描
                java.util.List<String> dirs = folders.stream().map(row -> row.get(0)).collect(Collectors.toList());
                DuplicateFinder finder = DuplicateFinder.create(new DuplicateFinder.ScanListener() {
                    @Override
                    public void duplicate(String first, String duplicate) {
                        duplicateCount.incrementAndGet();
                    }

                    @Override
                    public void process(long count) {
                        scanProgressBar.setValue((int)count);
                        scanCount.incrementAndGet();
                        scanProgressBar.setString("扫描文件 " + scanCount.get() + "/" + totalCount.get());
                    }

                    @Override
                    public void finish(long count) {
                        scan.setText("开始扫描");
                        scanProgressBar.setVisible(false);
                        scanResult.setVisible(true);
                        scanResult.setText("本次扫描文件" + scanCount.get() + "个，发现重复文件" + duplicateCount.get() + "个");
                    }

                    @Override
                    public void totalCount(long count) {
                        totalCount.set(count);
                    }
                });
                ProcessTypeEnum processType = ProcessTypeEnum.SCAN;
                if (moveRadio.isSelected()) {
                    processType = ProcessTypeEnum.MOVE;
                } else if (removeRadio.isSelected()) {
                    processType = ProcessTypeEnum.REMOVE;
                }
                finder.addListener(new DuplicateProcessor(scanRootPath.getText(), processType));

                finderCountThread = new Thread(() -> {
                    long count = finder.count(dirs, allFileTypeRadio.isSelected() ? new String[]{} : normalFileType);
                    totalCount.set(count);
                    scanProgressBar.setMaximum((int) count);

                    duplicateCount.set(0);
                    scanCount.set(0);
                    finder.scan(true, dirs, allFileTypeRadio.isSelected() ? new String[]{} : normalFileType);
                });
                finderCountThread.start();
            } else {
                scan.setText("开始扫描");
                scanProgressBar.setVisible(false);
                scanResult.setVisible(true);
                scanResult.setText("本次扫描文件" + scanCount.get() + "个，发现重复文件" + duplicateCount.get() + "个");
                if (null != finderCountThread && finderCountThread.isAlive()) {
                    finderCountThread.interrupt();
                }
            }
        });

        scanRootPath.addMouseListener(new LocalMouseListener() {
            @Override
            public void mousePressed(MouseEvent e) {
                String folder = Utils.selectFolder();
                if (StringUtils.isNotBlank(folder)) {
                    scanRootPath.setText(folder);
                }
            }
        });
    }

    private abstract class LocalMouseListener implements MouseListener {
        @Override
        public void mouseClicked(MouseEvent e) {}

        @Override
        public void mouseReleased(MouseEvent e) {}

        @Override
        public void mouseEntered(MouseEvent e) {}

        @Override
        public void mouseExited(MouseEvent e) {}
    }

    JButton addFolder = new JButton("添加文件夹");
    JButton delFolder = new JButton("删除文件夹");
    Vector<Vector<String>> folders = new Vector<>();
    Vector<String> titles = new Vector<>(Collections.singleton("文件路径"));
    JTable scanFolderTable = new JTable(folders, titles);
    JButton scan = new JButton("开始扫描");
    JProgressBar scanProgressBar = new JProgressBar(0, 0, 100);
    JLabel scanResult = new JLabel();
    AtomicLong totalCount = new AtomicLong(0);
    AtomicLong scanCount = new AtomicLong(0);
    AtomicLong duplicateCount = new AtomicLong(0);
    JRadioButton normalFileTypeRadio = new JRadioButton("常见格式(jpeg,mov,mp3,mp4,txt,docx,xlsx)");
    String[] normalFileType = new String[]{"jpeg","mov","mp3","mp4","txt","docx","xlsx"};
    JRadioButton allFileTypeRadio = new JRadioButton("全部格式");
    JRadioButton removeRadio = new JRadioButton("直接删除");
    JRadioButton moveRadio = new JRadioButton("迁移文件");
    JRadioButton scanRadio = new JRadioButton("仅扫描");
    JTextField scanRootPath = new JTextField();

    private void layoutComponents() {
        // 第一行
        JLabel scanFolderLabel = new JLabel("选择扫描文件夹");
        setComponentPosition(scanFolderLabel, 0, 0, 8, 1, true, false);
        setComponentPosition(addFolder, 8, 0, 2, 1, true, false);
        setComponentPosition(delFolder, 10, 0, 2, 1, true, false);

        JScrollPane pane = new JScrollPane(scanFolderTable);
        setComponentPosition(pane, 0, 1, 12, 4, true, true);

        JLabel scanRuleLabel = new JLabel("扫描规则");
        setComponentPosition(scanRuleLabel, 0, 5, 8, 1, true, false);

        JLabel scanFileTypeLabel = new JLabel("文件格式");
        setComponentPosition(scanFileTypeLabel, 0, 6, 2, 1, true, false);
        setComponentPosition(normalFileTypeRadio, 2, 6, 4, 1, true, false);
        setComponentPosition(allFileTypeRadio, 6, 6, 4, 1, true, false);
        ButtonGroup group = new ButtonGroup();
        group.add(normalFileTypeRadio);
        group.add(allFileTypeRadio);
        allFileTypeRadio.setSelected(true); // 默认扫描全部文件

        JLabel dealStrategyLabel = new JLabel("处理策略");
        setComponentPosition(dealStrategyLabel, 0, 7, 2, 1, true, false);
        setComponentPosition(scanRadio, 2, 7, 2, 1, true, false);
        setComponentPosition(removeRadio, 4, 7, 2, 1, true, false);
        setComponentPosition(moveRadio, 6, 7, 2, 1, true, false);
        ButtonGroup dealStrategyGroup = new ButtonGroup();
        dealStrategyGroup.add(scanRadio);
        dealStrategyGroup.add(removeRadio);
        dealStrategyGroup.add(moveRadio);
        scanRadio.setSelected(true);

        JLabel logPathLabel = new JLabel("日志目录");
        setComponentPosition(logPathLabel, 0, 8, 2, 1, true, false);
        setComponentPosition(scanRootPath, 2, 8, 12, 1, true, false);
        scanRootPath.setText(Utils.getHomeDirectory());

        scanProgressBar.setVisible(false);
        setComponentPosition(scanProgressBar, 0, 9, 8, 1);
        setComponentPosition(scanResult, 0, 9, 8, 1);
        setComponentPosition(scan, 8, 9, 4, 1);
    }

    private void setComponentPosition(JComponent component, int x, int y, int w, int h) {
        setComponentPosition(component, x, y, w, h, false, false);
    }

    private void setComponentPosition(JComponent component, int x, int y, int w, int h, boolean fillX, boolean fillY) {
        constraints.weightx = fillX ? 1 : 0;
        constraints.weighty = fillY ? 1 : 0;
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = w;
        constraints.gridheight = h;
        constraints.insets = new Insets(5, 5, 5, 5);
        layout.setConstraints(component, constraints);
        this.add(component);
    }

    public static void main(String[] argv) {
        new FinderUI().showFrame();
    }
}
