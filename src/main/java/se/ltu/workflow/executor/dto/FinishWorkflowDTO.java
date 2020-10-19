package se.ltu.workflow.executor.dto;

import java.time.ZonedDateTime;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.ltu.workflow.executor.service.QueuedWorkflow;
import se.ltu.workflow.executor.service.WStatus;

public class FinishWorkflowDTO {
    
    final private int id;
    final private String workflowName;
    final private WStatus workflowStatus;
    final private Boolean success;
    // In the future errorMessage should be an optional field
    final private String errorMessage;
    final private ZonedDateTime queueTime;
    final private ZonedDateTime startTime;
    final private ZonedDateTime endTime;
    
    public FinishWorkflowDTO(int id, String workflowName, WStatus workflowStatus, Boolean success, String errorMessage,
            ZonedDateTime queueTime, ZonedDateTime startTime, ZonedDateTime endTime) {
        this.id = Objects.requireNonNull(id, "Expected workflow id");
        this.workflowName = Objects.requireNonNull(workflowName, "Expected workflow name");
        this.workflowStatus = Objects.requireNonNull(workflowStatus, "Expected a valid workflowStatus");
        this.success = Objects.requireNonNull(success, "Expected the success boolean flag");
        this.errorMessage = Objects.requireNonNull(errorMessage, "Expected the message explaining error");
        this.queueTime = Objects.requireNonNull(queueTime, "Expected the time when workflow was added"
                + " to queue, queuetime");
        this.startTime = Objects.requireNonNull(startTime, "Expected the time when workflow was started"
                + " in Workflow Executor system, startTime");
        this.endTime = Objects.requireNonNull(endTime, "Expected the time when workflow was finished"
              + " in Workflow Executor system, endTime");
    }
    
    /**
     * Creates an empty object.
     * <p>
     * To be used only as a reference to infer the class, not as a proper Object.
     */
    private FinishWorkflowDTO() {
        id = -1;
        workflowName = null;
        workflowStatus = null;
        success = null;
        errorMessage = null;
        queueTime = null;
        startTime = null;
        endTime = null;
    }
    
    /**
     * Creates a new {@code FinishWorkflowDTO} and automatically sets its fields
     * from the underlining finished workflow.
     * 
     * @param queuedWorkflow  The QueuedWorkflow object used as data source, can not be null
     * @return  The DTO object with the same parameters as the underlining workflow
     * @throws IllegalArgumentException if the input parameter queuedWorkflow is null
     */
    public static FinishWorkflowDTO fromQueuedWorkflow(QueuedWorkflow finishedWorkflow) {
        // If I want a String representation of the date
        //String dateNow = ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT );
        if(finishedWorkflow == null) {
            throw new IllegalArgumentException("Input argument \"QueuedWorkflow\" can not be null");
        }
        
        if (finishedWorkflow.getWorkflowStatus().equals(WStatus.DONE))
            return new FinishWorkflowDTO(finishedWorkflow.getId(),
                                    finishedWorkflow.getWorkflowName(),
                                    finishedWorkflow.getWorkflowStatus(),
                                    finishedWorkflow.getSuccess(),
                                    finishedWorkflow.getPossibleErrorMessage().orElse(""),
                                    finishedWorkflow.getQueueTime(),
                                    finishedWorkflow.getStartTime(),
                                    finishedWorkflow.getEndTime());
        
        throw new IllegalArgumentException("Workflow is not finished yet to create this DTO from it");
    }
    
    /**
     * Creates an empty FinishWorkflowDTO object.
     * <p>
     * To be used only as a reference to infer the class, not as a proper Object to test its values.
     */
    public static FinishWorkflowDTO classReference() {
        return new FinishWorkflowDTO();
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

    public Boolean getSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
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
