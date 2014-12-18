package uk.ac.ebi.pride.toolsuite.gui.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.pride.toolsuite.gui.PrideInspectorContext;
import uk.ac.ebi.pride.toolsuite.gui.access.EmptyDataAccessController;
import uk.ac.ebi.pride.toolsuite.gui.desktop.Desktop;
import uk.ac.ebi.pride.toolsuite.gui.task.TaskAdapter;
import uk.ac.ebi.pride.utilities.data.controller.DataAccessController;
import uk.ac.ebi.pride.utilities.data.controller.DataAccessException;
import uk.ac.ebi.pride.utilities.data.controller.impl.ControllerImpl.MzIdentMLControllerImpl;
import uk.ac.ebi.pride.utilities.data.core.Protein;
import uk.ac.ebi.pride.utilities.data.core.ProteinGroup;
import uk.ac.ebi.pride.utilities.data.core.SpectraData;
import uk.ac.ebi.pride.utilities.pia.intermediate.IntermediateProtein;
import uk.ac.ebi.pride.utilities.pia.intermediate.prideimpl.PrideIntermediateProtein;
import uk.ac.ebi.pride.utilities.pia.intermediate.prideimpl.PrideUtilities;
import uk.ac.ebi.pride.utilities.pia.modeller.PIAModeller;
import uk.ac.ebi.pride.utilities.pia.modeller.filter.AbstractFilter;
import uk.ac.ebi.pride.utilities.pia.modeller.protein.inference.InferenceProteinGroup;
import uk.ac.ebi.pride.utilities.pia.modeller.protein.inference.OccamsRazorInference;
import uk.ac.ebi.pride.utilities.pia.modeller.protein.inference.ReportAllInference;
import uk.ac.ebi.pride.utilities.pia.modeller.scores.CvScore;
import uk.ac.ebi.pride.utilities.pia.modeller.scores.peptide.PeptideScoring;
import uk.ac.ebi.pride.utilities.pia.modeller.scores.peptide.PeptideScoringUseBestPSM;
import uk.ac.ebi.pride.utilities.pia.modeller.scores.protein.ProteinScoring;
import uk.ac.ebi.pride.utilities.pia.modeller.scores.protein.ProteinScoringAdditive;
import uk.ac.ebi.pride.utilities.pia.modeller.scores.protein.ProteinScoringMultiplicative;
import uk.ac.ebi.pride.utilities.term.CvTermReference;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Task to open mzML/MzIdentML or PRIDE xml files.
 * <p/>
 * <p/>
 * @author rwang
 * @author ypriverol
 * Date: 01-Feb-2010
 * Time: 10:37:49
 */
public class OpenFileTask<D extends DataAccessController> extends TaskAdapter<Void, String> {
    private static final Logger logger = LoggerFactory.getLogger(OpenFileTask.class);
    /**
     * file to open
     */
    private File inputFile;

    /**
     * reference pride inspector context
     */
    private PrideInspectorContext context;

    /**
     * the class type of the data access controller to open the file
     */
    private Class<D> dataAccessControllerClass;

    private List<File> msFiles = null;

    private Boolean inMemory   = false;
    
    private Boolean runProteinInferenceLater = false;

    public OpenFileTask(File inputFile, Class<D> dataAccessControllerClass, String name, String description) {
        this.inputFile = inputFile;
        this.dataAccessControllerClass = dataAccessControllerClass;
        this.setName(name);
        this.setDescription(description);

        context = ((PrideInspectorContext) Desktop.getInstance().getDesktopContext());
    }

    public OpenFileTask(File inputFile, List<File> msFiles, Class<D> dataAccessControllerClass,
                        String name, String description, Boolean inMemory) {
        this.inputFile = inputFile;
        this.dataAccessControllerClass = dataAccessControllerClass;
        this.setName(name);
        this.setDescription(description);
        context = ((PrideInspectorContext) Desktop.getInstance().getDesktopContext());
        this.msFiles = msFiles;
        this.inMemory = inMemory;
    }
    
    
    
    public OpenFileTask(File inputFile, List<File> msFiles, Class<D> dataAccessControllerClass,
            String name, String description, Boolean inMemory, Boolean runProteinInferenceLater) {
        this(inputFile, msFiles, dataAccessControllerClass, name, description, inMemory);
        
        this.runProteinInferenceLater = runProteinInferenceLater;
    }
    
    
    @Override
    protected Void doInBackground() throws Exception {
        boolean opened = alreadyOpened(inputFile);
        if (opened) {
            openExistingDataAccessController(inputFile);
        } else {
            checkInterruption();
            // publish a notice for starting the file loading
            publish("Loading " + inputFile.getName());
            if(inMemory)
                createNewDataAccessController(inputFile,Boolean.TRUE);
            else
                createNewDataAccessController(inputFile);
        }
        return null;
    }

    private void checkInterruption() throws InterruptedException {
        if (Thread.currentThread().interrupted()) {
            throw new InterruptedException();
        }
    }

    /**
     * Check whether the file has been opened before
     *
     * @param file the input file to check
     * @return boolean true if it has been opened before
     */
    private boolean alreadyOpened(File file) {
        boolean isOpened = false;

        List<DataAccessController> controllers = context.getControllers();
        for (DataAccessController controller : controllers) {
            if (file.equals(controller.getSource())) {
                isOpened = true;
            }
        }

        return isOpened;
    }

    /**
     * This method is called if the experiment is already open, then the experiment will be
     * bring to the foreground.
     *
     * @param file file to open.
     */
    private void openExistingDataAccessController(File file) {
        java.util.List<DataAccessController> controllers = context.getControllers();
        for (DataAccessController controller : controllers) {
            if (DataAccessController.Type.XML_FILE.equals(controller.getType()) &&
                    controller.getSource().equals(file)) {
                context.setForegroundDataAccessController(controller);
            }
        }
    }

    /**
     * Create new DB data access controller
     *
     * @param file file to open
     */
    private void createNewDataAccessController(File file) {
        try {
            // create dummy
            EmptyDataAccessController dummy = createEmptyDataAccessController();

            Constructor<D> cstruct = dataAccessControllerClass.getDeclaredConstructor(File.class);
            DataAccessController controller;
            
            if (runProteinInferenceLater) {
                cstruct = dataAccessControllerClass.getDeclaredConstructor(File.class, Boolean.TYPE, Boolean.TYPE);
                controller = cstruct.newInstance(inputFile, Boolean.FALSE, Boolean.TRUE);
            } else {
                cstruct = dataAccessControllerClass.getDeclaredConstructor(File.class);
                controller = cstruct.newInstance(inputFile);
            }

            if (MzIdentMLControllerImpl.class.equals(dataAccessControllerClass) && msFiles != null) {
                try {
                    //todo: this is strange way of implement
                    Map<SpectraData, File> msFileMap = ((MzIdentMLControllerImpl) controller).checkMScontrollers(msFiles);
                    ((MzIdentMLControllerImpl) controller).addMSController(msFileMap);
                } catch (DataAccessException e1) {
                    logger.error("Failed to check the files as controllers", e1);
                }
            }

            if (runProteinInferenceLater) {
                infereProteins(controller);
            }
            
            // this is important for cancelling
            if (Thread.interrupted()) {
                // remove dummy
                context.removeDataAccessController(dummy, false);
                throw new InterruptedException();
            } else {
                // add the real thing
                context.replaceDataAccessController(dummy, controller, false);
            }
        } catch (InterruptedException ex) {
            logger.warn("File loading has been interrupted: {}", file.getName());
        } catch (Exception err) {
            String msg = "Failed to loading from the file: " + file.getName();
            logger.error(msg, err);
//            GUIUtilities.error(Desktop.getInstance().getMainComponent(), msg, "Open File Error");
        }
    }

    /**
     * Create new DB data access controller
     *
     * @param file file to open
     */
    private void createNewDataAccessController(File file, Boolean inmemory) {
        try {
            // create dummy
            EmptyDataAccessController dummy = createEmptyDataAccessController();

            Constructor<D> cstruct = dataAccessControllerClass.getDeclaredConstructor(File.class, Boolean.TYPE);
            DataAccessController controller = cstruct.newInstance(inputFile, Boolean.TRUE);
            
            if (runProteinInferenceLater) {
                cstruct = dataAccessControllerClass.getDeclaredConstructor(File.class, Boolean.TYPE, Boolean.TYPE);
                controller = cstruct.newInstance(inputFile, Boolean.TRUE,  Boolean.TRUE);
            } else {
                cstruct = dataAccessControllerClass.getDeclaredConstructor(File.class, Boolean.TYPE);
                controller = cstruct.newInstance(inputFile, Boolean.TRUE);
            }
            
            if (MzIdentMLControllerImpl.class.equals(dataAccessControllerClass) && msFiles != null) {
                try {
                    //todo: this is strange way of implement
                    Map<SpectraData, File> msFileMap = ((MzIdentMLControllerImpl) controller).checkMScontrollers(msFiles);
                    ((MzIdentMLControllerImpl) controller).addMSController(msFileMap);
                } catch (DataAccessException e1) {
                    logger.error("Failed to check the files as controllers", e1);
                }
            }

            if (runProteinInferenceLater) {
                infereProteins(controller);
            }
            
            // this is important for cancelling
            if (Thread.interrupted()) {
                // remove dummy
                context.removeDataAccessController(dummy, false);
                throw new InterruptedException();
            } else {
                // add the real thing
                context.replaceDataAccessController(dummy, controller, false);
            }
        } catch (InterruptedException ex) {
            logger.warn("File loading has been interrupted: {}", file.getName());
        } catch (Exception err) {
            String msg = "Failed to loading from the file: " + file.getName();
            logger.error(msg, err);
//            GUIUtilities.error(Desktop.getInstance().getMainComponent(), msg, "Open File Error");
        }
    }

    private EmptyDataAccessController createEmptyDataAccessController() {
        EmptyDataAccessController dummy = new EmptyDataAccessController();
        dummy.setName(inputFile.getName());

        if (dataAccessControllerClass == MzIdentMLControllerImpl.class) {
            dummy.setType(DataAccessController.Type.MZIDENTML);
        } else {
            dummy.setType(DataAccessController.Type.XML_FILE);
        }

        // add a closure hook
        this.addOwner(dummy);
        context.addDataAccessController(dummy);
        return dummy;
    }
    
    
    
    /**
     * Run the protein inference using the PIA algorithms and add the resulting
     * protein ambiguity groups to the {@link DataAccessController}
     * 
     * @param controller
     */
    private void infereProteins(DataAccessController controller) {
        PIAModeller piaModeller = new PIAModeller();
        
        CvScore cvScore = null;
        String scoreAccession = null;
        // try to get the main-score
        for (CvTermReference termRef : controller.getAvailablePeptideLevelScores()) {
            CvScore newCvScore;
            scoreAccession = termRef.getAccession();
            newCvScore = CvScore.getCvRefByAccession(termRef.getAccession());
            if ((newCvScore != null) && newCvScore.getIsMainScore()) {
                cvScore = newCvScore;
                scoreAccession = cvScore.getAccession();
                break;
            }
        }
        
        // add the input file to modeller and import data
        Integer controllerID = piaModeller.addPrideControllerAsInput(controller);
        piaModeller.importAllDataFromFile(controllerID);
        
        // first create the intermediate structure from the data given by the controller
        piaModeller.buildIntermediateStructure();
        
        PeptideScoring pepScoring = new PeptideScoringUseBestPSM(scoreAccession, false);
        ProteinScoring protScoring;
        if ((cvScore != null) && !cvScore.getHigherScoreBetter()) {
            protScoring = new ProteinScoringMultiplicative(false, pepScoring);
        } else {
            protScoring = new ProteinScoringAdditive(false, pepScoring);
        }
        
        // TODO: allow for filters
        List<AbstractFilter> filters = null;
        
        // perform the protein inferences
        piaModeller.getProteinModeller().infereProteins(pepScoring, protScoring, OccamsRazorInference.class, filters, false);
        
        // create the protein groups
        int nrGroups = piaModeller.getProteinModeller().getInferredProteins().size();
        Map<Comparable, Map<Comparable, List<Comparable>>> prideProteinGroupMapping = new HashMap<Comparable, Map<Comparable,List<Comparable>>>(nrGroups);
        
        int count = 0;
        for (InferenceProteinGroup piaGroup : piaModeller.getProteinModeller().getInferredProteins()) {
            
            Map<Comparable, List<Comparable>> proteinPeptideMap = null;
            
            if ((filters == null) || (filters.size() < 1)) {
                
                Set<IntermediateProtein> proteinSet = new HashSet<IntermediateProtein>(piaGroup.getProteins());
                // include the subGroups
                for (InferenceProteinGroup subGroup : piaGroup.getSubGroups()) {
                    proteinSet.addAll(subGroup.getProteins());
                }
                
                proteinPeptideMap = new HashMap<Comparable, List<Comparable>>(proteinSet.size());
                
                for (IntermediateProtein protein : proteinSet) {
                    Comparable proteinID = ((PrideIntermediateProtein)protein).getPrideProteinID();
                    // null as the peptide list is interpreted as taking all peptides (PSMs)
                    proteinPeptideMap.put(proteinID, null);
                }
            } else {
                // TODO: implement filtered out PSMs
                /*
                ProteinGroup group =
                        PrideUtilities.convertInferenceProteinGroup(piaGroup, true, (filters == null) || (filters.size() < 1));
                */
            }
            
            prideProteinGroupMapping.put(piaGroup.getID(), proteinPeptideMap);
        }
        
        controller.setInferredProteinGroups(prideProteinGroupMapping);
    }
}
