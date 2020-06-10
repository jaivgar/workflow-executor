package se.ltu.workflow.executor.dto;

import java.time.ZonedDateTime;

import se.ltu.workflow.executor.service.QueuedWorkflow;
import se.ltu.workflow.executor.service.WStatus;

public class QueuedWorkflowDTO {
    
    final int id;
    final String workflowName;
    final WStatus workflowStatus;
    final ZonedDateTime queueTime;
    final ZonedDateTime startTime;
    final ZonedDateTime endTime;
    
    public int getId() {
        return id;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public WStatus getWorkflowStatus() {
        return workflowStatus;
    }
    
    /**
     * Constructor used by factory methods to create {@code QueuedWorkflowDTO}.
     * 
     * The idea behind not using a Builder pattern for this object is due to the 
     * lack of object complexity and compile time validation as explained in the 
     * <a href="https://reflectoring.io/java-immutables/">article</a>. Instead the
     * class will provide factory methods.
     * 
     * @param id  The unique ID of this workflow in storage, not null
     * @param workflowName  The name of this type of workflows, not null
     * @param workflowStatus  The status of activation of the workflow, not null
     * @param queueTime  The time at which workflow was created and registered in system
     * , not null
     * @param startTime  The time at which workflow starts execution creation and status 
     * is ACTIVE, may be null
     * @param endTime  The time at which workflow has been completed and status is DONE
     * , may be null
     */
    private QueuedWorkflowDTO(int id, String workflowName, WStatus workflowStatus, 
            ZonedDateTime queueTime, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.id = id;
        this.workflowName = workflowName;
        this.workflowStatus = workflowStatus;
        this.queueTime = queueTime;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    /**
     * Creates a new {@code QueuedWorkflowDTO} and automatically sets its fields
     * form the underlining stored workflow.
     * 
     * @param workflow  The workflow object used as data source
     * @return  The DTO object with the same parameters as the underlining workflow
     */
    public static QueuedWorkflowDTO fromQueuedWorkflow(QueuedWorkflow workflow) {
        // If I want a String representation of the date
        //String dateNow = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        if(workflow == null) {
            throw new IllegalArgumentException("Input argument \"workflow\" can not be null");
        }
        return new QueuedWorkflowDTO(workflow.getId(), 
                                     workflow.getWorkflowName(),
                                     workflow.getWorkflowStatus(),
                                     workflow.getQueueTime(),
                                     workflow.getStartTime(),
                                     workflow.getEndTime());
    }
    
    /*
     * To Delete after revision and copying the rules to the QueuedWorkflow for object creation:
     
    /**
     * Creates a new {@code ActiveWorkflowDTO} and automatically sets its end time.
     * <p>
     * The starting time is the actual time and the ending time is set to null, as
     * we can not predict when it will be done
     * 
     * @param id  The unique ID of this Workflow in storage, not null
     * @param workflowName  The name of this type of Workflows, not null
     * @param workflowStatus  The status of activation, not null
     * @param startTime  The time
     * 
     * @return The DTO object with the specified parameters and a start time
     * 
     * @throws IllegalArgumentException if the argument {@code workflowStatus} is set
     * to a value of <i>DONE</i>
     *
    public static QueuedWorkflowDTO startingWorkflowDTO(int id, String workflowName, 
            WStatus workflowStatus, ZonedDateTime startTime) 
    {
        if (workflowStatus == WStatus.DONE) {
            throw new IllegalArgumentException("Workflow is finish, so its starting time can not be set");
        }
        // If I want a String representation of the date
        //String dateNow = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        return new QueuedWorkflowDTO(id, workflowName, workflowStatus, startTime, null);
    }
    
    /**
     * Creates a new {@code ActiveWorkflowDTO} without start or end times, as the
     * Workflow has already being created and only changed status.
     * 
     * @param id  The unique ID of this Workflow in storage, not null
     * @param workflowName  The name of this type of Workflows, not null
     * @param workflowStatus  The new status of activation, not null
     * 
     * @return The DTO object with the specified parameters(a new status)
     *
    public static QueuedWorkflowDTO updatedWorkflowDTO(int id, String workflowName, WStatus workflowStatus) {
        if (workflowStatus == WStatus.DONE) {
            throw new IllegalArgumentException("Workflow is finish, so it should not be updated");
        }
        return new QueuedWorkflowDTO(id, workflowName, workflowStatus, null, null);
    }
    
    /**
     * Creates a new {@code ActiveWorkflowDTO} and automatically sets its start
     * and end times.
     * <p>
     * The ending time is the actual time and the starting time is set to null, as
     * we do not store the time when it was created (at the moment)
     * 
     * @param id  The unique ID of this Workflow in storage, not null
     * @param workflowName  The name of this type of Workflows, not null
     * @param workflowStatus  The status of activation, not null
     * 
     * @return The DTO object with the specified parameters and a end time
     *
    public static QueuedWorkflowDTO endingWorkflowDTO(int id, String workflowName, WStatus workflowStatus) {
        if (workflowStatus != WStatus.DONE) {
            throw new IllegalArgumentException("Workflow is not finish, so its ending time can not be set");
        }
        ZonedDateTime endTime = ZonedDateTime.now(ZoneOffset.UTC);
        return new QueuedWorkflowDTO(id, workflowName, workflowStatus, null, endTime);
    }

    */
}
