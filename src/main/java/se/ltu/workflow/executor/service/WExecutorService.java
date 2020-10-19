package se.ltu.workflow.executor.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import eu.arrowhead.common.dto.shared.OrchestrationResultDTO;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.UnavailableServerException;
import se.ltu.workflow.executor.InitialWorkflows;
import se.ltu.workflow.executor.WExecutorConstants;
import se.ltu.workflow.executor.arrowhead.WExecutorUtils;
import se.ltu.workflow.executor.dto.FinishWorkflowDTO;

@Service
public class WExecutorService {
    
    @Autowired
    private InitialWorkflows initData;
    
    private final Logger logger = LogManager.getLogger(WExecutorService.class);
    
    final private Set<Workflow> workflowsStored;
    
    /* This should be a ConcurrentLinkedQueue or LinkedBlockingQueue, when working in multiple threads.
     * The expected behavior is to have a consumer blocking when no item is present in the Queue,
     * therefore the LinkedBlockingQueue fits better in this case as it allows automatic locking when take(),
     * but the ConcurrentLinkedQueue offers better performance enabling simultaneous reads, or 
     * simultaneous writes.
     */
    final private BlockingQueue<QueuedWorkflow> workflowsForExecution;
    
    private OrchestrationResultDTO WManagerService;
    
    final private Thread workflowsExecuting;
    
    public WExecutorService() {
        workflowsStored = new HashSet<>();
        workflowsForExecution = new LinkedBlockingQueue<>();
        
        // This thread will consume workflows and execute their State Machines
        Runnable workflowsRunning = () -> {
            
            QueuedWorkflow workflowOngoing;
            
            try {
                while(true) {
                    /* Retrieve first element, deleting it from Queue (which is not what we want!).
                     * But this is the only retrieving method that will block the thread whenever Queue is empty
                     */
                    workflowOngoing = workflowsForExecution.take();
                    // Put back the Workflow in the Queue, but the thread will not block this time
                    /* TODO: Should synchronize the access to this variable, race conditions can occur if the 
                     * getWorklfowExecuting method is called between the take and the put.
                     */
                    workflowsForExecution.put(workflowOngoing);
                    
                    logger.info("Consuming Workflow " + workflowOngoing.getWorkflowName()
                        + " with ID=" + workflowOngoing.getId());
                    
                    // Set this Workflow as the active one
                    workflowOngoing.setWorkflowStatus(WStatus.ACTIVE);
                    
                    
                    /* This method will trigger the execution of the State Machine as the representation
                     * of the Workflow
                     */
                    workflowOngoing.executeWorkflow();
                    logger.info("The Workflow entered queue at: " + workflowOngoing.getQueueTime());
                    logger.info("The Workflow started at: " + workflowOngoing.getStartTime());
                    logger.info("The Workflow finished at: " + workflowOngoing.getEndTime());
                    
                    /* Look at results of workflow provided inside State Machine as agreed upon
                     * variables stored in the environment
                     */
                    sendWorkflowResults(workflowOngoing);
                    
                    // Remove Workflow from Queue after its execution
                    workflowsForExecution.take();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.error("Thread executing State Machines inside WExecutorService has been "
                        + "unexpectedly interrupted, shutting down Workflow Executor");
                System.exit(1);
            }
        };
        
        workflowsExecuting = new Thread(workflowsRunning, "Workflow consumer");
        workflowsExecuting.start();
    }
    
    @PostConstruct
    private void initConfig() {
        workflowsStored.addAll(initData.getWorkflows());
    }
    
    public List<Workflow> getWorkflowTypes() {
        List<Workflow> workflowTypes = new ArrayList<>();
        for (Workflow w : workflowsStored) {
            workflowTypes.add(w);
        }
        return workflowTypes;
    }
    
    public List<QueuedWorkflow> getWorkflowsExecuting() {
        List<QueuedWorkflow> workflowInExecution = new ArrayList<>();
        for (QueuedWorkflow w : workflowsForExecution) {
            workflowInExecution.add(w);
        }
        return workflowInExecution;
    }
    
    public QueuedWorkflow executeWorkflow(String workflowName, Map<String, List<String>> workflowConfig) {
        
        // Search for the workflow in the workflowsStored
        /* One Option was to use the contains() method of Set class, that required to Override the default
         * equals() method of Object class and to create a Workflow with only a name and config to compare.
         * 
         * But then to retrieve it we will have to iterate through the set, therefore it seems
         * more straightforward to iterate through the elements from the start, and compare each
         * time with the reference. This also enables to stop when we find our target.
         */
        Workflow requestedWorkflow = new Workflow(workflowName, workflowConfig, null);
        for (Workflow w : workflowsStored) {
            if (w.equals(requestedWorkflow)) {
                try {
                    requestedWorkflow = new Workflow(w);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    logger.error("The Workflow stored does not support execution, "
                            + "due to problem when copying its Workflow Configuration, "
                            + "repair WorkflowConfiguration Map implementation.");
                    return null;
                }
                logger.info("Workflow with requested parameter found in memory: "
                        + requestedWorkflow.getWorkflowName());
                break;
            }
        }
        if(requestedWorkflow.getWorkflowLogic() == null) {
            // The workflow was not found, so exit with a negative value (null or exception?)
            return null;
        }
        
        // Create a new QueuedWorkflow with the configuration parameters and add to Queue
        requestedWorkflow.getWorkflowConfig().clear();
        // As workflowConfig is optional, it can be null
        if (workflowConfig != null) {
            requestedWorkflow.getWorkflowConfig().putAll(workflowConfig);
        }
        QueuedWorkflow toExecuteWork = new QueuedWorkflow(requestedWorkflow);
        try {
            workflowsForExecution.add(toExecuteWork);
        } catch (IllegalStateException e) {
            logger.error("The capacity of internal memory of Workflow Executor is full, "
                    + "too many Workflows waiting to be executed");
        }
        
        // A thread is forever running checking for Workflows in the Queue
        
        // Return the created QueuedWorkflow, the same reference as the one added to the Queue
        return toExecuteWork;
    }
    
    /**
     * Sends workflow results to Workflow Manager
     * 
     * @param finishedWorkflow  The workflow to extract the results from
     */
    private void sendWorkflowResults(QueuedWorkflow finishedWorkflow) {
        // Check that a reference to WManager exist in memory
        try {
            if(WManagerService == null)
                WManagerService = WExecutorUtils
                    .orchestrate(WExecutorConstants.WMANAGER_RESULT_SERVICE_DEFINITION);
        } catch (ArrowheadException e) {
            logger.warn("Workflow Manager is not present in Workstation");
            return;
        }
        // Check that the WManager is still alive at stored address by sending an echo request
        try {
            var echoWManagerService = new OrchestrationResultDTO(
                    WManagerService.getProvider(),
                    // We really do not care about Service, as echo is not registered in service registry
                    WManagerService.getService(),
                    WManagerService.getServiceUri(),
                    WManagerService.getSecure(),
                    WManagerService.getMetadata(),
                    WManagerService.getInterfaces(),
                    WManagerService.getVersion());
            // Modify URI for echo service
            echoWManagerService.setServiceUri(
                    WExecutorConstants.WMANAGER_URI + WExecutorConstants.ECHO_URI);
            
            var echoResponse = WExecutorUtils.consumeService(
                    String.class,
                    echoWManagerService,
                    HttpMethod.GET,
                    null,
                    null,
                    null);
            logger.debug("Workflow Manager answered echo with: " + echoResponse);
        } catch (UnavailableServerException e) {
            logger.warn("Workflow Manager is not present anymore in Workstation or change address,"
                    + "retrying orchestration");
            try {
                WManagerService = WExecutorUtils
                    .orchestrate(WExecutorConstants.WMANAGER_RESULT_SERVICE_DEFINITION);
            } catch (ArrowheadException e2) {
                logger.warn("Workflow Manager is not present in Workstation");
                return;
            }
        }
        
        var WManagerResponse = WExecutorUtils.consumeService(
                String.class, // This parameter can not be null, but we do not care about the response
                WManagerService,
                HttpMethod.POST,
                null,
                FinishWorkflowDTO.fromQueuedWorkflow(finishedWorkflow),
                null);
        logger.info("WManager received result results of workflow: "
                + "id = " + finishedWorkflow.getId() + ", workflowName = " + finishedWorkflow.getWorkflowName());
    }
    
    /**
     * Adds new Workflows to the Workflow Executor, to be stored locally and executed on demand.
     * 
     * @param newWorkflowType
     * 
     * @return True if the Workflow is new and is added to internal memory. False if it is not added,
     * because it already exist or due to other problems.
     */
    private Boolean addWorkflowType(Workflow newWorkflowType) {
        
        return workflowsStored.add(newWorkflowType);
    }
}
