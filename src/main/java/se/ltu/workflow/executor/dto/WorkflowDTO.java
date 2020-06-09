package se.ltu.workflow.executor.dto;

import java.util.Collections;
import java.util.Map;

import se.ltu.workflow.executor.service.Workflow;

public class WorkflowDTO {
    
    final String workflowName;
    final Map<String,String> workflowConfig;
    
    public String getWorkflowName() {
        return workflowName;
    }

    public Map<String, String> getWorkflowConfig() {
        return workflowConfig;
    }

    public WorkflowDTO(String workflowName, Map<String,String> workflowConfig){
        if(workflowName == null) {
            throw new IllegalArgumentException("WorkflowName can not be null, is a unique identifier");
        }
        this.workflowName = workflowName;
        // Collections.unmodifiableMap() to make the Map inmutable
        this.workflowConfig = Collections.unmodifiableMap(workflowConfig);
    }
    
    public static WorkflowDTO fromWorkflow(Workflow w) {
        return new WorkflowDTO(w.getWorkflowName(), w.getWorkflowConfig());
    }
}
