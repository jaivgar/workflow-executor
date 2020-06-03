package se.ltu.workflow.executor.dto;

import java.util.Collections;
import java.util.Map;

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
        this.workflowName = workflowName;
        // Collections.unmodifiableMap() to make the Map inmutable
        this.workflowConfig = Collections.unmodifiableMap(workflowConfig);
    }
}
