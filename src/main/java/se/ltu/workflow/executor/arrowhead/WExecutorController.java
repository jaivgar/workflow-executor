package se.ltu.workflow.executor.arrowhead;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import eu.arrowhead.common.CommonConstants;

import se.ltu.workflow.executor.WExecutorConstants;
import se.ltu.workflow.executor.dto.QueuedWorkflowDTO;
import se.ltu.workflow.executor.dto.WorkflowDTO;
import se.ltu.workflow.executor.service.WExecutorService;

@RestController
@RequestMapping(WExecutorConstants.WEXECUTOR_URI)
public class WExecutorController {
	
	//=================================================================================================
	// members

    @Autowired
    private WExecutorService executor;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@GetMapping(path = CommonConstants.ECHO_URI)
	public String echoService() {
		return "Got it!";
	}
	
	//-------------------------------------------------------------------------------------------------
	@PostMapping(path = WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_URI)
	@ResponseBody public List<WorkflowDTO> getAvailableWorkflows() {
	    return null;
	}
	
	//-------------------------------------------------------------------------------------------------
    @PostMapping(path = WExecutorConstants.START_WORKFLOW_URI)
    @ResponseBody public QueuedWorkflowDTO startWorkflow(
            @RequestParam(name = WExecutorConstants.REQUEST_PARAM_WORKFLOW) final WorkflowDTO workflowWanted)
    {
        return null;
    }
}
