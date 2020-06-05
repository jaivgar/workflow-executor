package se.ltu.workflow.executor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class WExecutorService {
    
    private Set<Workflow> workflowsStored;
    
    // This should be a ConcurrentLinkedQueue if working in concurrent threads
    private Queue<QueuedWorkflow> workflowsForExecution;
    
    public void initializeWorkflows() {
        addWorkflowType(null);
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
        // Create a new QueuedWorkflow with that information and add to Queue
        // Spun a new thread to execute the Workflow, if no other under execution at the moment
        // Return the created QueuedWorkflow (Or the one from the Queue)
        return null;
    }
    
    private Boolean addWorkflowType(Workflow newWorkflowType) {
        return true;
    }
}
