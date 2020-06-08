package se.ltu.workflow.executor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
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
import se.ltu.workflow.executor.arrowhead.WExecutorApplicationListener;
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
        
        Map<String, String> workflowTestConfig = new HashMap<>(Map.ofEntries(
                Map.entry("scheduleTime", "String"),
                Map.entry("Operation", "String")));
        
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
                            // Check services needed in State Machine are available in Local Cloud
                            System.out.println("Look at State Machine configuration");
                            List<String> toFindServices = (List<String>) env.get("Services");
                            if(toFindServices != null) {
                                List<String> serviceURIs = new ArrayList<>();
                                for(String serviceDefinition : toFindServices) {
                                    try {
                                        OrchestrationResultDTO serviceFound = 
                                                orchestrate(serviceDefinition);
                                        serviceURIs.add(serviceFound.getServiceUri());
                                    } catch (final ArrowheadException e) {
                                        events.add(new Event("Init-Fail"));
                                        e.printStackTrace();
                                        return;
                                    }
                                }
                                env.put("Services-Address", serviceURIs);
                                events.add(new Event("Init-Success"));
                            }
                        },
                        // Next State:
                        1
                    ),
                    new Transition(
                        new LogicExpression<Event,Set<Event>>(null, List.of(new Event("Init-Success"))), 
                        null, 
                        (env, events) -> {
                            // Check services needed in State Machine are available in Local Cloud
                            System.out.println("Look at State Machine configuration");
                            List<String> toFindServices = (List<String>) env.get("Services");
                            if(toFindServices != null) {
                                for(String serviceDefinition : toFindServices) {
                                    try {
                                        orchestrate(serviceDefinition);
                                    } catch (final ArrowheadException e) {
                                        events.add(new Event("Init-Fail"));
                                    }
                                }
                            }
                        },
                        // Next State:
                        1
                    ),
                    new Transition(
                        //new LogicExpression<Event,Set<Event>>(null, new HashSet<Event>(Stream.of("EVENT-X").collect(Collectors.toUnmodifiableSet()))),
                        new LogicExpression<Event,Set<Event>>(null, List.of(new Event("EVENT-X"))), 
                        new LogicExpression<Guard,Map<String, Object>>(
                                                                    LogicOperator.NOT, 
                                                                    List.of(new Guard("x", 1))), 
                        (env, events) -> {
                            System.out.println("Transition active: add variable b");
                            env.put("b", 
                                    env.get("b") != null ? (int)env.get("b") + 1 : 1);
                            System.out.println("The events are: " + events);
                            System.out.println("The env is: " + env);
                        }, 
                        2
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
}
