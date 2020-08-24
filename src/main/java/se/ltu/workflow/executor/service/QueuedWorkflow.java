package se.ltu.workflow.executor.service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class QueuedWorkflow extends Workflow{
    
    private static int countWorkflows = 0;
    
    final private int id;
    final private ZonedDateTime queueTime;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    
    public QueuedWorkflow(Workflow workflow) {
        super(workflow.getWorkflowName(), workflow.getWorkflowConfig(), workflow.getWorkflowLogic());
        this.id = countWorkflows++;
        // If I want a String representation of the date
        //String dateNow = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        this.queueTime = ZonedDateTime.now(ZoneOffset.UTC);
        this.startTime = null;
        this.endTime = null;
    }
    
    public static int getCountWorkflows() {
        return countWorkflows;
    }
    
    public int getId() {
        return id;
    }
    
    public ZonedDateTime getQueueTime() {
        return queueTime;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }
    
    @Override
    public Boolean startWorkflow() {
        
        if (this.startTime != null) {
            throw new IllegalStateException("This Worflow has already been started, internal Workflow Executor Error");
        }
        else {
            this.startTime = ZonedDateTime.now(ZoneOffset.UTC);
        }
        
        // Call super method to start State Machine execution
        Boolean workflowExecution = super.startWorkflow();
        endWorkflow();
        return workflowExecution;
    }
    
    
    private void endWorkflow() {
        if (this.endTime != null) {
            throw new IllegalStateException("This Worflow has already been started, internal Workflow Executor Error");
        }
        else {
            this.endTime = ZonedDateTime.now(ZoneOffset.UTC);
        }
    }

    
}
