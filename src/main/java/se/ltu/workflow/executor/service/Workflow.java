package se.ltu.workflow.executor.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.ltu.workflow.executor.state_machine.StateMachine;

public class Workflow {
    
    final private String workflowName;
    private WStatus workflowStatus;
    final private Map<String,List<String>> workflowConfig;
    final private StateMachine workflowLogic;
    
    /* Add variable to store Workflow results?
     * Not needed if we can use workflowConfig, if it is a mutable Map.
     * However what class should be used to store Workflow results? It depends on 
     * the Workflow so unless using generics, the user will have to cast later.
     */
    
    private final Logger logger = LogManager.getLogger(Workflow.class);
    
    public Workflow(String workflowName, Map<String, List<String>> workflowConfig, StateMachine workflowLogic) {
        this.workflowName = workflowName;
        this.workflowStatus = WStatus.IDLE;
        this.workflowConfig = workflowConfig;
        this.workflowLogic = workflowLogic;
    }
    
    /**
     * A copy constructor of Workflow class.
     * <p>
     * Creates a new Workflow object from the object provided as argument.
     * 
     * @param w The original workflow that will be used as template to create the new Workflow object
     * @throws IllegalAccessException when the class implementing the Map interface for workflowConfig
     * has no default constructor to make a copy of it
     */
    public Workflow(Workflow w) throws IllegalAccessException {
        this.workflowName = w.getWorkflowName();
        this.workflowStatus = w.getWorkflowStatus();
        
        Map<String, List<String>> tempWorkflowConfig = null;
        try {
            tempWorkflowConfig = w.getWorkflowConfig().getClass().getDeclaredConstructor().newInstance();
            tempWorkflowConfig.putAll(w.getWorkflowConfig());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (tempWorkflowConfig != null) {
            this.workflowConfig = tempWorkflowConfig;
        }
        else {
            throw new IllegalAccessException("Copy constructor can not duplicate WorkflowConfig, "
                    + "Map implementation class has no default constructor");
            /* Better this Map implementation than an immutable Map, as we add the parameters 
             * to the workflowConfig Map before execution
             */
           // this.workflowConfig = new HashMap<String, List<String>>(w.getWorkflowConfig());
        }
        this.workflowLogic = new StateMachine(w.getWorkflowLogic());
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
     * otherwise it will throw an {@code IllegalStateException}.
     * 
     * 
     * @return The same as in {@link se.ltu.workflow.executor.state_machine.StateMachine#update()}. 
     * True when the workflow can keep executing, false otherwise.
     */
    public Boolean startWorkflow() {
        if (this.getWorkflowStatus() != WStatus.ACTIVE) {
            throw new IllegalStateException("Workflow is not ACTIVE yet, so it should not be executed");
        }

        // Add configuration parameters to State Machine
        workflowConfig.forEach((k,v)-> this.getWorkflowLogic().setVariable(k, v));
        
        // Log the entry path to the State Machine
        logger.info("Workflow " + this.getWorkflowName() + " starts execution in state "
                + this.getWorkflowLogic().getCurrentState() + " (" 
                + this.getWorkflowLogic().getActiveState().name() + ")");
        logger.debug("Events present: " + this.getWorkflowLogic().getEvents());
        logger.debug("Environment contains variables: " + this.getWorkflowLogic().getEnvironment());
        
        // Execute all the transitions of the State Machine
        while(this.getWorkflowLogic().update()) {
            // Log the progress through the states
            logger.info("Workflow " + this.getWorkflowName() + " in state "
                    + this.getWorkflowLogic().getCurrentState() + " (" 
                    + this.getWorkflowLogic().getActiveState().name() + ")");
            logger.debug("Events present: " + this.getWorkflowLogic().getEvents());
            logger.debug("Environment contains variables: " + this.getWorkflowLogic().getEnvironment());
        }
            
        // Set to status DONE the Workflow executed
        this.setWorkflowStatus(WStatus.DONE);
        logger.info("Workflow status set to " + WStatus.DONE);
        // Return the result? Or nothing?
        return false;
        
    }
    
    /**
     * This method ends the Workflow, storing all outputs before it can be candidate for
     * garbage collection.
     * <p>
     * This method has a pre-condition: the Workflow from which it is called must be in DONE state,
     * otherwise it will throw an {@code IllegalStateException}.
     * <p>
     * Implementation status: At the moment the outputs are not stored anywhere.
     * 
     * @return True when the Workflow ends successfully, which now is always the case
     */
    public Boolean endWorkflow() {
        if (this.getWorkflowStatus() != WStatus.DONE) {
            throw new IllegalStateException("Workflow is not DONE yet, so it should not be ended");
        }
        //cleanWorkflow();
        logger.info("Workflow " + this.getWorkflowName() + " is finished");
        return true;
    }
    
    
    /**
     * This method updates the Workflow, by cleaning all inputs and outputs of the State Machine 
     * and returning it to its default status.
     * <p>
     * Implementation status: Currently only sets the current state to 0, not resets the Events 
     * neither the Environment
     */
    public void cleanWorkflow() {
        // Can not clean Events and Environment of State Machine, no methods available?
        this.workflowLogic.setCurrentState(0);

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

    /**
     * Provides a hashCode for the Workflow objects, as they will be stored in Sets and Maps that will
     * use at some point its hash for storage or comparison operations.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getWorkflowName() == null) ? 0 : getWorkflowName().hashCode());
        return result;
    }
  
}
