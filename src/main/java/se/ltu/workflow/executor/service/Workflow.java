package se.ltu.workflow.executor.service;

import java.util.List;
import java.util.Map;

import se.ltu.workflow.executor.state_machine.StateMachine;

public class Workflow {
    
    final private String workflowName;
    /*TODO: The workflowStatus was before in the child class QueuedWorkflow, 
     * still unsure of where to set it
     */
    private WStatus workflowStatus;
    final private Map<String,List<String>> workflowConfig;
    final private StateMachine workflowLogic;
    
    //TODO: Add variable to store Workflow results?
    
    public Workflow(String workflowName, Map<String, List<String>> workflowConfig, StateMachine workflowLogic) {
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

    public Map<String, List<String>> getWorkflowConfig() {
        return workflowConfig;
    }

    public StateMachine getWorkflowLogic() {
        return workflowLogic;
    }
    
    /**
     * This method encapsulates the logic to execute a Workflow. 
     * <p>
     * Its internal logic is represented by a State Machine, which is part of the object. 
     * Therefore the Workflow execution is equivalent to the execution of the State Machine.
     * <p>
     * This method has a pre-condition: the Workflow from which it is called must be in ACTIVE state,
     * otherwise it will throw an {@code IllegalArgumentException}.
     * 
     * 
     * @return The same as in {@link se.ltu.workflow.executor.state_machine.StateMachine#update()}. 
     * True when the workflow can keep executing, false otherwise.
     */
    public Boolean startWorkflow() {
        if (this.getWorkflowStatus() != WStatus.ACTIVE) {
            throw new IllegalStateException("Workflow is not ACTIVE yet, so it should not be executed");
        }
        else {
            // Add configuration to State Machine
            workflowConfig.forEach((k,v)-> this.getWorkflowLogic().setVariable(k, v));
            
            // Execute all the transitions of the State Machine
            while(this.getWorkflowLogic().update());
                
            // Set to status DONE the Workflow executed
            this.setWorkflowStatus(WStatus.DONE);
            
            // Return the result? Or nothing?
            return false;
        }
    }
    
    /**
     * Indicates whether some other Workflow is "equal to" this one, by comparing its name
     */
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
