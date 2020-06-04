package se.ltu.workflow.executor.service;

import java.util.Queue;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WExecutorService {
    
    private Set<Workflow> workflowsStored;
    private Queue<QueuedWorkflow> workflowsForExecution;
    
    public Boolean addWorkflowType(Workflow newWorkflowType) {
        return true;
    }
    
    public void initializeWorkflows() {
        
    }
}
