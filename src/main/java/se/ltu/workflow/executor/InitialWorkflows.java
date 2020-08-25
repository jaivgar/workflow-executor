package se.ltu.workflow.executor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import eu.arrowhead.client.library.ArrowheadService;
import eu.arrowhead.common.SSLProperties;
import eu.arrowhead.common.dto.shared.OrchestrationFormRequestDTO;
import eu.arrowhead.common.dto.shared.OrchestrationResponseDTO;
import eu.arrowhead.common.dto.shared.OrchestrationResultDTO;
import eu.arrowhead.common.dto.shared.ServiceInterfaceResponseDTO;
import eu.arrowhead.common.dto.shared.ServiceQueryFormDTO;
import eu.arrowhead.common.dto.shared.OrchestrationFlags.Flag;
import eu.arrowhead.common.dto.shared.OrchestrationFormRequestDTO.Builder;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.InvalidParameterException;

import se.ltu.workflow.executor.dto.WorkflowDTO;
import se.ltu.workflow.executor.dto.QueuedWorkflowDTO;
import se.ltu.workflow.executor.service.Workflow;
import se.ltu.workflow.executor.state_machine.Event;
import se.ltu.workflow.executor.state_machine.Guard;
import se.ltu.workflow.executor.state_machine.LogicExpression;
import se.ltu.workflow.executor.state_machine.LogicOperator;
import se.ltu.workflow.executor.state_machine.State;
import se.ltu.workflow.executor.state_machine.StateMachine;
import se.ltu.workflow.executor.state_machine.Transition;

/**
 * This class provides the initial configuration of the Workflows used by the system
 * 
 * It could also be a static class, but chosen to be singleton for flexibility and better
 * testing.
 */
@Component
public class InitialWorkflows {

    final private Set<Workflow> workflowsInput;
    
    @Autowired
    private ArrowheadService arrowheadService;
    
    @Autowired
    protected SSLProperties sslProperties;
    
    private final Logger logger = LogManager.getLogger(InitialWorkflows.class);
    
    public InitialWorkflows() {
        workflowsInput = new HashSet<>();
        
        workflowsInput.add(testWorkflow());
    }
    
    public Set<Workflow> getWorkflows() {
        return workflowsInput;
    }
    
    private Workflow testWorkflow() {
        
        String workflowTestName = "test123";
        
        Map<String, List<String>> workflowTestConfig = new HashMap<>(Map.ofEntries(
                Map.entry("scheduleTime", new ArrayList<>(List.of("String")) ),
                Map.entry("Services", new ArrayList<>(List.of("String")))));
        
        StateMachine workflowTestMachine = new StateMachine(
                Arrays.asList(
                    new State("Start", 0),
                    new State("Init", 1,3),
                    new State("Work", 2,3),
                    new State("End")
                    ),
                Arrays.asList(
                    new Transition(
                        null, 
                        null, 
                        (env, events) -> {
                            // Check services needed in this State Machine are available in Local Cloud now
                            System.out.println("Transition 0: Look at State Machine configuration");
                            List<String> toFindServices = (List<String>) env.get("Services");
                            if(toFindServices != null) {
                                Map<String,OrchestrationResultDTO> serviceAndAddress = new HashMap<>();
                                System.out.println("Transition 0: Start orchestration of services "
                                        + "in configuration");
                                for(String serviceDefinition : toFindServices) {
                                    try {
                                        OrchestrationResultDTO serviceFound = orchestrate(serviceDefinition);
                                        serviceAndAddress.put(serviceDefinition, serviceFound);
                                    } catch (final ArrowheadException e) {
                                        events.add(new Event("Init-Fail"));
                                        e.printStackTrace();
                                        env.put("Error", "Service \"" + serviceDefinition + "\" not found in Local Cloud");
                                        return;
                                    }
                                }
                                env.put("ServicesAddress", serviceAndAddress);
                                events.add(new Event("Init-Success"));
                            }
                            else {
                                env.put("Error","No Services provided for initial configuration");
                                events.add(new Event("Init-Fail"));
                            }
                        },
                        // Next State:
                        1
                    ),
                    new Transition(
                        new LogicExpression<Event,Set<Event>>(LogicOperator.OR, 
                                                              List.of(new Event("Init-Success"), new Event("More-Work"))), 
                        null,
                        (env, events) -> {
                            // Test services found
                            System.out.println("Transition 1: Test services found");
                            Map<String,OrchestrationResultDTO> serviceAndAddress = 
                                    (Map<String, OrchestrationResultDTO>) env.get("ServicesAddress");

                            System.out.println("Transition 1: Test Available Workflow type service");
                            if (serviceAndAddress.containsKey(WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION)) {
                                OrchestrationResultDTO WExecutorInfo = serviceAndAddress.get(
                                        WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
                                List<WorkflowDTO> serviceResult = consumeService(Collections.emptyList(), WExecutorInfo, null, null);
                                env.put("AvailableWorkflows", serviceResult);
                                // Not sure if we can print a list like this, but let's see
                                System.out.print(serviceResult);
                                if(serviceResult.isEmpty()) {
                                    // This workflow should be in the list
                                    events.add(new Event("Work-error"));
                                    env.put("Error", "The list of Workflows Available is empty");
                                }
                            }
                            
                            System.out.println("Transition 1: Test In-execution Workflow service");
                            if (serviceAndAddress.containsKey(WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION)) {
                                OrchestrationResultDTO WExecutorInfo = serviceAndAddress.get(
                                        WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
                                List<QueuedWorkflowDTO> serviceResult = consumeService(Collections.emptyList(), WExecutorInfo, null, null);
                                env.put("InExecutionWorkflows", serviceResult);
                                // Not sure if we can print a list like this, but let's see
                                System.out.print(serviceResult);
                                if(serviceResult.isEmpty()) {
                                    // No workflow has started execution yet
                                    events.add(new Event("Work-ongoing"));
                                }
                                else {
                                    events.add(new Event("Work-done"));
                                    env.put("WorkResult", 1);
                                }
                            }
                        },
                        // Next State:
                        2
                    ),
                    new Transition(
                        //new LogicExpression<Event,Set<Event>>(null, new HashSet<Event>(Stream.of("EVENT-X").collect(Collectors.toUnmodifiableSet()))),
                        null, 
                        new LogicExpression<Guard,Map<String, Object>>(LogicOperator.NOT, 
                                                                       List.of(new Guard("WorkResult", 1))), 
                        (env, events) -> {
                            System.out.println("Transition 2: Start Workflow execution");
                            Map<String,OrchestrationResultDTO> serviceAndAddress = 
                                    (Map<String, OrchestrationResultDTO>) env.get("ServicesAddress");
                            if (serviceAndAddress.containsKey(WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION)) {
                                OrchestrationResultDTO WExecutorInfo = serviceAndAddress.get(
                                        WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
                                QueuedWorkflowDTO serviceResult = consumeService(null, WExecutorInfo, null, null);
                                if(serviceResult  == null) {
                                    events.add(new Event("Work-error"));
                                    env.put("Error", "The returned QueuedWorkflowDTO is not well formed, is empty");
                                }
                                else {
                                    events.add(new Event("More-Work"));
                                }
                            }
                        },
                        // Next State:
                        2
                    ),
                    new Transition(
                        new LogicExpression<Event,Set<Event>>(LogicOperator.OR, 
                                List.of(new Event("Init-Fail"), new Event("Work-error"),new Event("Work-done"))), 
                        null, 
                        (env, events) -> {
                            System.out.println("Transition 3: End State Machine and output results");
                            if (env.containsKey("Error")) {
                                env.put("OutputStateMachine", 500);
                            }
                            else {
                                env.put("OutputStateMachine", 200);
                            }

                            System.out.println("The events are: " + events);
                            System.out.println("The env is: " + env);
                        }, 
                        // Next State:
                        3
                    )
                ));
        
        Workflow testWorkflow = new Workflow(workflowTestName, workflowTestConfig, workflowTestMachine);
        
        return testWorkflow;
    }
    
    //-------------------------------------------------------------------------------------------------
    private OrchestrationResultDTO orchestrate(final String serviceDefinition) throws ArrowheadException{
        final ServiceQueryFormDTO serviceQueryForm = new ServiceQueryFormDTO.Builder(serviceDefinition)
                .interfaces(getInterface())
                .build();
        
        final Builder orchestrationFormBuilder = arrowheadService.getOrchestrationFormBuilder();
        final OrchestrationFormRequestDTO orchestrationFormRequest = orchestrationFormBuilder
                .requestedService(serviceQueryForm)
                .flag(Flag.MATCHMAKING, true)
                .flag(Flag.OVERRIDE_STORE, true)
                .build();
        
        /* This exception should be handled when executing the Workflow, as Services 
         * change at Runtime
         */
        //try {      
        final OrchestrationResponseDTO orchestrationResponse = arrowheadService.
                proceedOrchestration(orchestrationFormRequest);    
        /*
        } catch (final ArrowheadException ex) {
            ex.printStackTrace();
        }*/
        if (orchestrationResponse == null) {
            logger.info("No orchestration response received");
        } else if (orchestrationResponse.getResponse().isEmpty()) {
            logger.info("No provider found during the orchestration");
        } else {
            final OrchestrationResultDTO orchestrationResult = orchestrationResponse
                    .getResponse().get(0);
            validateOrchestrationResult(orchestrationResult, serviceDefinition);
            return orchestrationResult;
        }
        throw new ArrowheadException("Unsuccessful orchestration: " + serviceDefinition);
    }
    
    //-------------------------------------------------------------------------------------------------
    private void validateOrchestrationResult(final OrchestrationResultDTO orchestrationResult, 
            final String serviceDefinition) 
    {
        if (!orchestrationResult.getService().getServiceDefinition().equalsIgnoreCase(serviceDefinition)) {
            throw new InvalidParameterException("Requested and orchestrated service definition do not match");
        }
        
        boolean hasValidInterface = false;
        for (final ServiceInterfaceResponseDTO serviceInterface : orchestrationResult.getInterfaces()) {
            if (serviceInterface.getInterfaceName().equalsIgnoreCase(getInterface())) {
                hasValidInterface = true;
                break;
            }
        }
        if (!hasValidInterface) {
            throw new InvalidParameterException("Requested and orchestrated interface do not match");
        }
    }
    
    //-------------------------------------------------------------------------------------------------
    private String getInterface() {
        return sslProperties.isSslEnabled() ? WExecutorConstants.INTERFACE_SECURE : WExecutorConstants.INTERFACE_INSECURE;
    }
    
    //-------------------------------------------------------------------------------------------------
    private <T> T consumeService(T ResponseDTO, final OrchestrationResultDTO orchestrationResult, 
            final Object payload, final String[] metadata) {
        final String token = orchestrationResult.getAuthorizationTokens() == null ? null : orchestrationResult.getAuthorizationTokens().get(getInterface());
        
        return (T) arrowheadService.consumeServiceHTTP(
                ResponseDTO.getClass(), // The type of object that the Response will be matched to
                HttpMethod.valueOf(orchestrationResult.getMetadata().get(WExecutorConstants.HTTP_METHOD)),
                orchestrationResult.getProvider().getAddress(), 
                orchestrationResult.getProvider().getPort(), 
                orchestrationResult.getServiceUri(),
                getInterface(), 
                token, 
                payload, 
                metadata);
    }
}
