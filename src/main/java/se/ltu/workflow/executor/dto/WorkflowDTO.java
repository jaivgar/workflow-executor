package se.ltu.workflow.executor.dto;

import java.util.Map;

public class WorkflowDTO {
    
    String workflowName;
    Map<String,String> workflowConfig;
    
    public String getWorkflowName() {
        return workflowName;
    }
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }
    public Map<String, String> getWorkflowConfig() {
        return workflowConfig;
    }
    public void setWorkflowConfig(Map<String, String> workflowConfig) {
        this.workflowConfig = workflowConfig;
    }

    
}
