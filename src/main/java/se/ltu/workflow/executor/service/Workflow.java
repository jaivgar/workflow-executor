package se.ltu.workflow.executor.service;

import java.util.Map;

import se.ltu.workflow.executor.state_machine.StateMachine;

public class Workflow {
    
    final private String workflowName;
    /*TODO: The workflowStatus was before in the child class QueuedWorkflow, 
     * still unsure of where to set it
     */
    private WStatus workflowStatus;
    final private Map<String,String> workflowConfig;
    final private StateMachine workflowLogic;
    
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
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        if (getWorkflowName() == null)
            return false;
        Workflow other = (Workflow) obj;
        boolean sameName = this.getWorkflowName().equals(other.getWorkflowName());
        /* The configuration parameters do not need to match exactly, leaving some flexibility for optional
         * configuration, and failing at execution time if there are no default values for those parameters.
         * boolean sameConfigParameters = this.getWorkflowConfig().keySet().equals(
                ((Workflow) obj).getWorkflowConfig().keySet());
           return sameName && sameConfigParameters;
        */
        return sameName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getWorkflowName() == null) ? 0 : getWorkflowName().hashCode());
        return result;
    }
  
}
