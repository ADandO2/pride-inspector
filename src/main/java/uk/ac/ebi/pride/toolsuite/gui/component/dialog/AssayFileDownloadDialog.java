package uk.ac.ebi.pride.toolsuite.gui.component.dialog;

import uk.ac.ebi.pride.archive.web.service.model.file.FileDetail;
import uk.ac.ebi.pride.toolsuite.gui.PrideInspector;
import uk.ac.ebi.pride.toolsuite.gui.PrideInspectorContext;
import uk.ac.ebi.pride.toolsuite.gui.component.table.TableFactory;
import uk.ac.ebi.pride.toolsuite.gui.component.table.model.AssayFileDownloadTableModel;
import uk.ac.ebi.pride.toolsuite.gui.task.TaskEvent;
import uk.ac.ebi.pride.toolsuite.gui.task.TaskListener;
import uk.ac.ebi.pride.toolsuite.gui.task.TaskUtil;
import uk.ac.ebi.pride.toolsuite.gui.task.impl.AsperaDownloadTask;
import uk.ac.ebi.pride.toolsuite.gui.task.impl.DataTransferProtocolTask;
import uk.ac.ebi.pride.toolsuite.gui.task.impl.FTPDownloadTask;
import uk.ac.ebi.pride.toolsuite.gui.task.impl.GetAssayFileMetadataTask;
import uk.ac.ebi.pride.toolsuite.gui.utils.DataTransferConfiguration;
import uk.ac.ebi.pride.toolsuite.gui.utils.DataTransferPort;
import uk.ac.ebi.pride.toolsuite.gui.utils.DataTransferProtocol;
import uk.ac.ebi.pride.utilities.util.Tuple;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dialog for download files belongs to assay
 *
 * @author Rui Wang
 * @version $Id$
 */
public class AssayFileDownloadDialog extends JDialog implements ActionListener, TaskListener<List<DataTransferProtocol>, Void> {
    private static final String CANCEL_ACTION_COMMAND = "cancelAction";
    private static final String DOWNLOAD_ACTION_COMMAND = "downloadAction";

    /**
     * File mapping table
     */
    private JTable fileDownloadSelectionTable;

    /**
     * Open file after download
     */
    private JCheckBox openFileOptionCheckbox;

    /**
     * Accession for the assay to download
     */
    private final String assayAccession;

    /**
     * Application context
     */
    private final PrideInspectorContext prideInspectorContext;

    /**
     * File mapping table model
     */
//    private FileMappingTableModel fileMappingTableModel;
    public AssayFileDownloadDialog(Frame owner, String assayAccession) {
        super(owner);
        this.assayAccession = assayAccession;
        this.prideInspectorContext = (PrideInspectorContext) PrideInspector.getInstance().getDesktopContext();
        initComponents();
        postComponents();
    }

    public AssayFileDownloadDialog(Dialog owner, String assayAccession) {
        super(owner);
        this.assayAccession = assayAccession;
        this.prideInspectorContext = (PrideInspectorContext) PrideInspector.getInstance().getDesktopContext();
        initComponents();
        postComponents();
    }

    /**
     * Create GUI components
     */
    private void initComponents() {
        this.setSize(new Dimension(600, 300));

        JPanel contentPanel = new JPanel(new BorderLayout());
        this.setContentPane(contentPanel);

        // create table panel
        initTablePanel();

        // create button panel
        initControlPanel();

        this.setContentPane(contentPanel);
    }

    /**
     * Post component creation, populate the table with content
     */
    private void postComponents() {
        AssayFileDownloadTableModel model = (AssayFileDownloadTableModel) fileDownloadSelectionTable.getModel();

        GetAssayFileMetadataTask task = new GetAssayFileMetadataTask(assayAccession);
        task.addTaskListener(model);
        TaskUtil.startBackgroundTask(task);
    }

    /**
     * Initialize table panel
     */
    private void initTablePanel() {
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // create table title label
        JLabel label = new JLabel("File Download (assay: " + assayAccession + ")");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        tablePanel.add(label, BorderLayout.NORTH);

        // create table
        fileDownloadSelectionTable = TableFactory.createAssayFileDownloadTable();

        // scroll pane
        JScrollPane scrollPane = new JScrollPane(fileDownloadSelectionTable,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        tablePanel.add(scrollPane, BorderLayout.CENTER);

        this.getContentPane().add(tablePanel, BorderLayout.CENTER);
    }

    /**
     * Initialize control panel
     */
    private void initControlPanel() {
        // setup main pane
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BorderLayout());

        // open file after download checkbox
        JPanel openFileOptionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        openFileOptionCheckbox = new JCheckBox("Open after download");
        openFileOptionCheckbox.setSelected(true);
        openFileOptionPanel.add(openFileOptionCheckbox);
        controlPanel.add(openFileOptionPanel, BorderLayout.WEST);

        // control pane
        JPanel ctrlPane = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // cancel button
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setPreferredSize(new Dimension(90, 30));
        cancelButton.setActionCommand(CANCEL_ACTION_COMMAND);
        cancelButton.addActionListener(this);
        ctrlPane.add(cancelButton);

        // next button
        JButton addButton = new JButton("Download");
        addButton.setPreferredSize(new Dimension(90, 30));
        addButton.setActionCommand(DOWNLOAD_ACTION_COMMAND);
        addButton.addActionListener(this);
        ctrlPane.add(addButton);

        controlPanel.add(ctrlPane, BorderLayout.EAST);

        this.getContentPane().add(controlPanel, BorderLayout.SOUTH);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String evtName = e.getActionCommand();

        if (CANCEL_ACTION_COMMAND.equals(evtName)) {
            this.dispose();
        } else if (DOWNLOAD_ACTION_COMMAND.equals(evtName)) {
            List<FileDetail> filesToDownload = getFilesToDownload();

            if (filesToDownload.size() > 0) {
                SimpleFileDialog ofd = new SimpleFileDialog(prideInspectorContext.getOpenFilePath(), "Select Path Save To", true, null, false);
                ofd.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                ofd.setMultiSelectionEnabled(false);

                int result = ofd.showOpenDialog(AssayFileDownloadDialog.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = ofd.getSelectedFile();
                    String folderPath = selectedFile.getPath();
                    prideInspectorContext.setOpenFilePath(folderPath);
                    downloadFiles(folderPath, filesToDownload);
                    this.dispose();
                }
            }
        }
    }

    private java.util.List<FileDetail> getFilesToDownload() {
        AssayFileDownloadTableModel assayFileDownloadTableModel = (AssayFileDownloadTableModel) fileDownloadSelectionTable.getModel();
        java.util.List<Tuple<FileDetail, Boolean>> data = assayFileDownloadTableModel.getData();

        java.util.List<FileDetail> fileDetails = new ArrayList<FileDetail>();

        for (Tuple<FileDetail, Boolean> fileDetailBooleanTuple : data) {
            if (fileDetailBooleanTuple.getValue()) {
                fileDetails.add(fileDetailBooleanTuple.getKey());
            }
        }

        return fileDetails;
    }

    private void downloadFiles(String folderPath, java.util.List<FileDetail> filesToDownload) {
        File path = new File(folderPath);

        DataTransferProtocol dataTransferProtocol = prideInspectorContext.getDataTransferProtocol();

        // open file after download
        boolean selected = openFileOptionCheckbox.isSelected();
        TaskDialog dialog = new TaskDialog(PrideInspector.getInstance().getMainComponent(), "Download assay files from PRIDE", "Downloading in progress...please wait");

        switch (dataTransferProtocol) {
            case ASPERA:
                // create a dialog to show progress
                dialog.setVisible(true);

                AsperaDownloadTask asperaDownloadTask = new AsperaDownloadTask(filesToDownload, path, selected);
                asperaDownloadTask.addTaskListener(dialog);
                TaskUtil.startBackgroundTask(asperaDownloadTask);
                break;
            case FTP:
                // create a dialog to show progress
                dialog.setVisible(true);

                FTPDownloadTask ftpDownloadTask = new FTPDownloadTask(filesToDownload, path, selected);
                ftpDownloadTask.addTaskListener(dialog);
                TaskUtil.startBackgroundTask(ftpDownloadTask);
                break;
            case NONE:
                selectDataTransferProtocol();
                break;

        }
    }

    /**
     * Select the best data transfer protocol for download database
     * <p/>
     * Should be called only once at the beginning of establish the panel
     */
    private void selectDataTransferProtocol() {
        // ftp
        String ftpHost = prideInspectorContext.getProperty("ftp.EBI.host");
        int ftpPort = Integer.parseInt(prideInspectorContext.getProperty("ftp.EBI.port"));
        DataTransferConfiguration ftpProtocolConfiguration = new DataTransferConfiguration(DataTransferProtocol.FTP, ftpHost,
                new DataTransferPort(DataTransferPort.Type.TCP, ftpPort));

        // aspera
        String asperaHost = prideInspectorContext.getProperty("aspera.EBI.host");
        int asperaTcpPort = Integer.parseInt(prideInspectorContext.getProperty("aspera.xfer.tcpPort"));
        int asperaUdpPort = Integer.parseInt(prideInspectorContext.getProperty("aspera.xfer.udpPort"));
        DataTransferConfiguration asperaProtocolConfiguration = new DataTransferConfiguration(DataTransferProtocol.ASPERA, asperaHost,
                new DataTransferPort(DataTransferPort.Type.TCP, asperaTcpPort), new DataTransferPort(DataTransferPort.Type.UDP, asperaUdpPort));

        DataTransferProtocolTask task = new DataTransferProtocolTask(ftpProtocolConfiguration, asperaProtocolConfiguration);
        task.addTaskListener(this);
        TaskUtil.startBackgroundTask(task);
    }

    @Override
    public void started(TaskEvent<Void> event) {

    }

    @Override
    public void process(TaskEvent<List<Void>> event) {

    }

    @Override
    public void finished(TaskEvent<Void> event) {

    }

    @Override
    public void failed(TaskEvent<Throwable> event) {
        prideInspectorContext.setDataTransferProtocol(DataTransferProtocol.NONE);
    }

    @Override
    public void succeed(TaskEvent<List<DataTransferProtocol>> event) {
        List<DataTransferProtocol> value = event.getValue();

        if (value.size() == 0) {
            // show warning
            JOptionPane.showMessageDialog(PrideInspector.getInstance().getMainComponent(), "<html>FTP or ASPERA are required for file download. <br/> Please ensure that either port 21 is opened for FTP or ports 22, 33001 are opened for ASPERA</html>", "File Download", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Collections.sort(value, new DataTransferProtocol.PriorityComparator());
        prideInspectorContext.setDataTransferProtocol(value.get(0));

        String filePath = prideInspectorContext.getOpenFilePath();
        List<FileDetail> filesToDownload = getFilesToDownload();
        downloadFiles(filePath, filesToDownload);
    }

    @Override
    public void cancelled(TaskEvent<Void> event) {
        prideInspectorContext.setDataTransferProtocol(DataTransferProtocol.NONE);
    }

    @Override
    public void interrupted(TaskEvent<InterruptedException> iex) {
        prideInspectorContext.setDataTransferProtocol(DataTransferProtocol.NONE);
    }

    @Override
    public void progress(TaskEvent<Integer> progress) {

    }
}
