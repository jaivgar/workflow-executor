package se.ltu.workflow.executor.service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public class QueuedWorkflow extends Workflow{
    
    private static int countWorkflows = -1;
    
    final int id;
    final ZonedDateTime queueTime;
    ZonedDateTime startTime;
    ZonedDateTime endTime;
    
    public QueuedWorkflow(Workflow workflow, WStatus workflowStatus) {
        super(workflow.getWorkflowName(), workflow.getWorkflowConfig(), workflow.getWorkflowLogic());
        this.id = countWorkflows++;
        this.workflowStatus = workflowStatus;
        // If I want a String representation of the date
        //String dateNow = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        this.queueTime = ZonedDateTime.now(ZoneOffset.UTC);
        this.startTime = null;
        this.endTime = null;
    }
    
    public static int getCountWorkflows() {
        return countWorkflows;
    }

    public ZonedDateTime getStartTime() {
        return startTime;
    }

    public ZonedDateTime getEndTime() {
        return endTime;
    }

    public int getId() {
        return id;
    }

    public ZonedDateTime getQueueTime() {
        return queueTime;
    }
    
    public Boolean startWorkflow() {
        if (this.startTime != null) {
            return false;
        }
        else {
            this.startTime = ZonedDateTime.now(ZoneOffset.UTC);
            return true;
        }
    }
    
    public Boolean endWorkflow(ZonedDateTime endTime) {
        if (this.endTime != null) {
            return false;
        }
        else {
            this.endTime = ZonedDateTime.now(ZoneOffset.UTC);
            return true;
        }
    }

    
}
