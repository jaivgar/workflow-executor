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
import org.springframework.stereotype.Service;

import se.ltu.workflow.executor.InitialWorkflows;

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
    
    final private Thread workflowsExecuting;
    
    public WExecutorService() {
        workflowsStored = new HashSet<>();
        workflowsForExecution = new LinkedBlockingQueue<>();
        
        // This thread will consume workflows and execute their State Machines
        Runnable workflowsRunning = () -> {
            
            QueuedWorkflow workflowOngoing;
            Boolean workflowSuccesful;
            
            try {
                while(true) {
                    /* Retrieve first element, deleting it from Queue, this method 
                     * will block the thread whenever Queue is empty
                     */
                    workflowOngoing = workflowsForExecution.take();
                    
                    workflowOngoing.setWorkflowStatus(WStatus.ACTIVE);
                    
                    // This method will trigger the execution of the State Machine as the representation of the Workflow
                    while(workflowOngoing.getWorkflowLogic().update());
                    
                    // This is one way to obtain results from a State Machine, in this case we use similar HTTP codes
                    if((int)workflowOngoing.getWorkflowLogic().getEnvironment().get("OutputStateMachine") == 200) {
                        workflowOngoing.setWorkflowStatus(WStatus.DONE);
                    }
                }
                
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.error("Thread executing State Machines inside WExecutorService has runned into problem");
                System.exit(1);
            }
            
        };
        workflowsExecuting = new Thread(workflowsRunning, "Running worklfows thread");
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
    
    public QueuedWorkflow executeWorkflow(String workflowName, Map<String, String> workflowConfig) {
        
        // Search for the workflow in the workflowsStored
        /* One Option was to use the contains() method of Set class, that required to Override the default
         * equals() method of Object class and to create a Workflow with only a name and config.
         * 
         * But the goal was to enforced a complete object in the Workflow constructor, so switched towards a
         * foreach search of the elements in the Set for a matching name.
         */
        Workflow requestedWorkflow = new Workflow(workflowName, workflowConfig, null);
        for (Workflow w : workflowsStored) {
            if (w.equals(requestedWorkflow)) {
                requestedWorkflow = w;
                logger.info("Workflow with requested parameter found in memory: " + requestedWorkflow.getWorkflowName()); 
            }
        }
        if(requestedWorkflow.getWorkflowLogic() == null) {
            // The workflow was not found, so exit with a negative value (null or exception?)
            return null;
        }
        // Create a new QueuedWorkflow with the configuration parameters and add to Queue
        requestedWorkflow.getWorkflowConfig().putAll(workflowConfig);
        QueuedWorkflow toExecuteWork = new QueuedWorkflow(requestedWorkflow);
        try {
            workflowsForExecution.add(toExecuteWork);
        } catch (IllegalStateException e) {
            logger.error("The capacity of internal memory of Workflow Executor is full, to many Workflows waiting to be executed");
        }
        
        // A thread is forever running checking for Workflows in the Queue
        
        // Return the created QueuedWorkflow (Or the one from the Queue?)
        return toExecuteWork;
    }
    
    private Boolean addWorkflowType(Workflow newWorkflowType) {
        // TODO
        return false;
    }
}
