package se.ltu.workflow.executor.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.ltu.workflow.executor.service.Workflow;

public class WorkflowDTO {
    
    final String workflowName;
    final Map<String,List<String>> workflowConfig;

    public WorkflowDTO(String workflowName, Map<String,List<String>> workflowConfig){
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
    
    public String getWorkflowName() {
        return workflowName;
    }

    public Map<String, List<String>> getWorkflowConfig() {
        return workflowConfig;
    }
    
    // From repository arrowhead-f/core-java-spring, pull request:Implement toString methods in DTOs #259
    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (final JsonProcessingException ex) {
            return "toString failure";
        }
    }
}
