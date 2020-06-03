package se.ltu.workflow.executor.service;

import java.util.Map;

import se.ltu.workflow.executor.state_machine.StateMachine;

public class Workflow {
    
    final String workflowName;
    /*TODO: The workflowStatus was before in the child class QueuedWorkflow, 
     * still unsure of where to set it
     */
    WStatus workflowStatus;
    final Map<String,String> workflowConfig;
    final StateMachine workflowLogic;
    
    public Workflow(String workflowName, Map<String, String> workflowConfig, StateMachine workflowLogic) {
        this.workflowName = workflowName;
        this.workflowStatus = WStatus.IDLE;
        this.workflowConfig = workflowConfig;
        this.workflowLogic = workflowLogic;
    }

    public WStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public Map<String, String> getWorkflowConfig() {
        return workflowConfig;
    }

    public StateMachine getWorkflowLogic() {
        return workflowLogic;
    }
    
    
  
}
