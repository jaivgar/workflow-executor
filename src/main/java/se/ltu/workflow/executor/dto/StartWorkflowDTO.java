package se.ltu.workflow.executor.dto;

import java.time.ZonedDateTime;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.ltu.workflow.executor.service.QueuedWorkflow;
import se.ltu.workflow.executor.service.WStatus;

public class StartWorkflowDTO {
    
    final private int id;
    final private String workflowName;
    final private WStatus workflowStatus;
    final private ZonedDateTime queueTime;
    
    public StartWorkflowDTO(int id, String workflowName, WStatus workflowStatus, ZonedDateTime queueTime) {
        this.id = Objects.requireNonNull(id, "Expected workflow id");
        this.workflowName = Objects.requireNonNull(workflowName, "Expected workflow name");
        this.workflowStatus = Objects.requireNonNull(workflowStatus, "Expected a valid workflowStatus");
        this.queueTime = Objects.requireNonNull(queueTime, "Expected the time when workflow was added"
                + " to queue, queuetime");
    }
    
    /**
     * Creates a new {@code StartWorkflowDTO} and automatically sets its fields
     * from the underlining queued workflow.
     * 
     * @param queuedWorkflow  The QueuedWorkflow object used as data source, can not be null
     * @return  The DTO object with the same parameters as the underlining workflow
     * @throws IllegalArgumentException if the input parameter queuedWorkflow is null
     */
    public static StartWorkflowDTO fromQueuedWorkflow(QueuedWorkflow queuedWorkflow) {
        // If I want a String representation of the date
        //String dateNow = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        if(queuedWorkflow == null) {
            throw new IllegalArgumentException("Input argument \"QueuedWorkflow\" can not be null");
        }
        if (!queuedWorkflow.getWorkflowStatus().equals(WStatus.DONE))
            return new StartWorkflowDTO(queuedWorkflow.getId(), 
                                     queuedWorkflow.getWorkflowName(),
                                     queuedWorkflow.getWorkflowStatus(),
                                     queuedWorkflow.getQueueTime());
        throw new IllegalArgumentException("Workflow is finished, so create a finish DTO from it, not a start");
    }

    public int getId() {
        return id;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public WStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public ZonedDateTime getQueueTime() {
        return queueTime;
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
