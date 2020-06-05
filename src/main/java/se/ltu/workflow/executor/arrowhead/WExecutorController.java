package se.ltu.workflow.executor.arrowhead;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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
		return "Got it!";
	}
	
	//-------------------------------------------------------------------------------------------------
	@GetMapping(path = WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_URI)
	@ResponseBody public List<WorkflowDTO> getAvailableWorkflows() {   
	    logger.info("Receiving request for service: " + WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
	    
	    List<WorkflowDTO> availableWorkflowsDTO = new ArrayList<>();
	    
	    for (Workflow w : executorService.getWorkflowTypes()) {
	        availableWorkflowsDTO.add(WorkflowDTO.fromWorkflow(w));
	    }
	    return availableWorkflowsDTO;
	}
	
	//-------------------------------------------------------------------------------------------------
    @GetMapping(path = WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_URI)
    @ResponseBody public List<QueuedWorkflowDTO> getExecutingWorkflows() {
        logger.info("Receiving request for service: " + WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
        
        List<QueuedWorkflowDTO> inExecutionWorkflowsDTO = new ArrayList<>();
        
        for(QueuedWorkflow qw : executorService.getWorkflowsExecuting()) {
            inExecutionWorkflowsDTO.add(QueuedWorkflowDTO.fromQueuedWorkflow(qw));
        }
        
        return inExecutionWorkflowsDTO;
    }
	
	//-------------------------------------------------------------------------------------------------
    @PostMapping(path = WExecutorConstants.EXECUTE_WORKFLOW_URI)
    @ResponseBody public QueuedWorkflowDTO executeWorkflow(@RequestBody final WorkflowDTO workflowWanted){
        logger.info("Receiving request for service: " + WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION);
        
        return QueuedWorkflowDTO.fromQueuedWorkflow(
                executorService.executeWorkflow(workflowWanted.getWorkflowName(), 
                                                workflowWanted.getWorkflowConfig()));
    }
    
}
