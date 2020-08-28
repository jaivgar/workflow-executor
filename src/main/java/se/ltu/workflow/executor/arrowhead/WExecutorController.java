package se.ltu.workflow.executor.arrowhead;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.CommonConstants;

import se.ltu.workflow.executor.WExecutorConstants;
import se.ltu.workflow.executor.dto.QueuedWorkflowDTO;
import se.ltu.workflow.executor.dto.WorkflowDTO;
import se.ltu.workflow.executor.service.QueuedWorkflow;
import se.ltu.workflow.executor.service.WExecutorService;
import se.ltu.workflow.executor.service.Workflow;

@RestController
@RequestMapping(WExecutorConstants.WEXECUTOR_URI)
public class WExecutorController {
	
	//=================================================================================================
	// members

    @Autowired
    private WExecutorService executorService;
    
    private final Logger logger = LogManager.getLogger(WExecutorController.class);

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@GetMapping(path = CommonConstants.ECHO_URI)
	public String echoService() {
	    logger.info("Receiving echo request");
		return "Got it!";
	}
	
	//-------------------------------------------------------------------------------------------------
	// No need of @ResponseBody since is included in @RestController
	@GetMapping(path = WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_URI)
	public List<WorkflowDTO> getAvailableWorkflows() {   
	    logger.info("Receiving request for service: " + WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
	    
	    List<WorkflowDTO> availableWorkflowsDTO = new ArrayList<>();
	    
	    for (Workflow w : executorService.getWorkflowTypes()) {
	        availableWorkflowsDTO.add(WorkflowDTO.fromWorkflow(w));
	    }
	    return availableWorkflowsDTO;
	}
	
	//-------------------------------------------------------------------------------------------------
    @GetMapping(path = WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_URI)
    public List<QueuedWorkflowDTO> getExecutingWorkflows() {
        logger.info("Receiving request for service: " + WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
        
        List<QueuedWorkflowDTO> inExecutionWorkflowsDTO = new ArrayList<>();
        
        for(QueuedWorkflow qw : executorService.getWorkflowsExecuting()) {
            inExecutionWorkflowsDTO.add(QueuedWorkflowDTO.fromQueuedWorkflow(qw));
        }
        
        return inExecutionWorkflowsDTO;
    }
	
	//-------------------------------------------------------------------------------------------------
    @PostMapping(path = WExecutorConstants.EXECUTE_WORKFLOW_URI, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    // It only allows one HTTP status(if there are no exceptions) @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ResponseEntity<QueuedWorkflowDTO> executeWorkflow(@RequestBody final WorkflowDTO workflowWanted){
        logger.info("Receiving request for service: " + WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION);
        QueuedWorkflowDTO resultBody;
        try {
            resultBody = QueuedWorkflowDTO.fromQueuedWorkflow(
                    executorService.executeWorkflow(workflowWanted.getWorkflowName(), 
                                                    workflowWanted.getWorkflowConfig()));
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        
        // resultBody can not be null since then fromQueuedWorkflow() would have thrown exception
        return ResponseEntity.status(HttpStatus.CREATED).body(resultBody);

    }
    
}
