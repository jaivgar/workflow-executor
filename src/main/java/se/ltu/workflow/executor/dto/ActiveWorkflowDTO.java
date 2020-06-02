package se.ltu.workflow.executor.dto;

public class ActiveWorkflowDTO {

    enum Status{
        ACTIVE,
        IDLE;
    }
    
    int id;
    String workflowName;
    Status workflowStatus;
    
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getWorkflowName() {
        return workflowName;
    }
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }
    public Status getWorkflowStatus() {
        return workflowStatus;
    }
    public void setWorkflowStatus(Status workflowStatus) {
        this.workflowStatus = workflowStatus;
    }
    
    

}
