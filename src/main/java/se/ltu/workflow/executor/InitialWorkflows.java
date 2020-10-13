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
 * This class provides the initial configuration of the Workflows used by the Workflow
 * Executor system.
 */
/* It could also be a static class, but chosen to be singleton for flexibility and better
 * testing.
 */
@Component
public class InitialWorkflows {

    /**
     * Workflows created at start-up should be added to this Set, for the Workflow Executor
     * to load them into memory and be able to offer their execution as a service.
     */
    final private Set<Workflow> workflowsInput;
    
    /**
     * Utility field to use the Arrowhead Client Library functionality, like contacting the
     * Arrowhead Core systems, or  requesting orchestration.
     */
    @Autowired
    private ArrowheadService arrowheadService;
    
    /**
     * Utility field to use the Arrowhead Client Library functionality, needed to obtain the
     * preferred interface of a system depending on the security level, indicated by this field.
     */
    @Autowired
    protected SSLProperties sslProperties;
    
    private final Logger logger = LogManager.getLogger(InitialWorkflows.class);
    
    /**
     * Constructs an instance of {@code InitialWorkflows} by adding all the Workflows created in
     * this class, one per method, to the {@code workflowsInput}.
     */
    public InitialWorkflows() {
        workflowsInput = new HashSet<>();
        
        workflowsInput.add(testWorkflow());
        
        FischertechnikWorkflow factoryWorkflows = new FischertechnikWorkflow();
        workflowsInput.add(factoryWorkflows.milling());
        workflowsInput.add(factoryWorkflows.drilling());
        workflowsInput.add(factoryWorkflows.millingAndDrilling());
    }
    
    public Set<Workflow> getWorkflows() {
        return workflowsInput;
    }
    
    /**
     * Creates a Workflow that enables to test the services of the Workflow Executor.
     * <p>
     * The Workflow includes a State Machine with 5 states, and when the State Machine
     * is executed for the first time it starts in State 0, as I have not specified
     * otherwise in the StateMachine constructor.
     * <p><ul>
     * <li>State 0 - Start: Checks the input configuration parameters of the Workflow
     * for the service definitions. This services will be tested in the State Machine.
     * It then ask the Orchestrator for orchestration of those services.
     * <li>State 1 - Service workflow type: Test the service which service definition
     * is "provide-workflows-type". Its answer should be that there is at least one
     * workflow created and available for execution, this one.
     * <li>State 2 - Service workflow in-execution: Test the service which service definition
     * is "provide-workflows-in-execution". Its answer should be that there is at least
     * a workflow executing at the moment, this one.
     * <li>State 3 - Service workflow in-execution: Test the service which service definition
     * is "execute-workflow". Be careful with this state as it can create an infinite loop of
     * recursively calling itself. It will be tested by calling the service without any
     * service definition as input parameter.
     * <li>State 4 - End: Wraps-up the State Machine, and before finishing execution can store
     * or send the outputs to a file or system. In this case it only adds an Http code to the
     * environment, but can contain behavior to handle errors signaled by other states.
     * </ul><p>
     * 
     * <p>
     * To change the behavior of the Workflow Executor, other workflows should be created and
     * added in the constructor of this class to the {@code workflowsInput} set.
     * 
     * @return The Workflow resulting from putting together a unique name, inputs parameters to
     * the workflow, and a State Machine.
     * 
     * <h4>Implementation notes</h4>
     * This method javadoc does not reflect its implementation yet, is in progress.
     */
    private Workflow testWorkflow() {
        
        String workflowTestName = "testWorkflowExecutor";
        
        Map<String, List<String>> workflowTestConfig = new HashMap<>(Map.ofEntries(
                Map.entry("scheduleTime", new ArrayList<>(List.of("String")) ),
                Map.entry("Services", new ArrayList<>(List.of("String")))));
        
        StateMachine workflowTestMachine = new StateMachine(
                Arrays.asList(
                    new State("Start orchestration", 0),
                    new State("Test services", 1,2,3,4),
                    new State("End")
                    ),
                Arrays.asList(
                    new Transition(
                        null, 
                        null, 
                        (env, events) -> {
                            /* Print the State, Events and Environment status at the start of each Transition 
                             * retrieved from State Machine context
                             */
                            // Events present here come from outside the State Machine
                            printEvents(events, "start of Transition 0");
                            printEnvironment(env, "start of Transition 0");
//                            printCurrentState(workflowTestName);
                            
                            // Check services needed in this State Machine are available in Local Cloud now
                            System.out.println("Transition 0: Look at State Machine input configuration");
                            List<String> toFindServices = (List<String>) env.get("Services");
                            if(toFindServices != null) {
                                Map<String,OrchestrationResultDTO> serviceAndAddress = new HashMap<>();
                                System.out.println("Transition 0: Start orchestration of services "
                                        + "present in configuration");
                                for(String serviceDefinition : toFindServices) {
                                    try {
                                        /* If the Arrowhead context in not updated with Orchestrator info at start-up,
                                         * it should be initialized here before any attempt of orchestration
                                         */
                                        OrchestrationResultDTO serviceFound = orchestrate(serviceDefinition);
                                        serviceAndAddress.put(serviceDefinition, serviceFound);
                                        env.put(serviceDefinition, true);
                                    } catch (final ArrowheadException e) {
                                        /* If the orchestration did not finish successfully, throw error Events to
                                         * transition to an error handling State of the State Machine
                                         */
                                        events.add(new Event("Init-Fail"));
                                        env.put("Error", "Service \"" + serviceDefinition + "\" not found in Local Cloud");
                                        e.printStackTrace();
                                        return;
                                    }
                                }
                                env.put("ServicesAddress", serviceAndAddress);
                                events.add(new Event("Init-Success"));
                            }
                            else {
                                env.put("Error","No serviceDefinitions (service name) provided in input configuration");
                                events.add(new Event("Init-Fail"));
                            }
                            /* Events present here, which are different than at the start of the Transition, come from 
                             * the Action executed inside the Transition
                             */
                            printEvents(events, "end of Transition 0");
                        },
                        // Next State:
                        1
                    ),
                    new Transition(
                        new LogicExpression<Event,Set<Event>>(LogicOperator.OR, 
                                                              List.of(new Event("Init-Success"), new Event("More-Work"))), 
                        new LogicExpression<Guard,Map<String, Object>>(null, List.of(
                                                   new Guard(WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION, 
                                                   true))),
                        (env, events) -> {
                            printEvents(events, "start of Transition 1");
                            printEnvironment(env, "start of Transition 1");
                            
                            // Test service
                            System.out.println("Transition 1: Test Service available Workflow types");
                            Map<String,OrchestrationResultDTO> serviceAndAddress = 
                                    (Map<String, OrchestrationResultDTO>) env.get("ServicesAddress");
                            /* Use the containsKey() method better than get() to distinguish between the 2 cases:
                             * when return value is null because key does not exist, or because value of key is null
                             */
                            if (serviceAndAddress.containsKey(WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION)) {
                                OrchestrationResultDTO WExecutorInfo = serviceAndAddress.get(
                                        WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
                                //List<WorkflowDTO> serviceResult = consumeService(Collections.emptyList(), WExecutorInfo, null, null);
                                List<WorkflowDTO> serviceResult = consumeService(new ArrayList<WorkflowDTO>(),
                                                                                    WExecutorInfo, null, null);
                                env.put("AvailableWorkflows", serviceResult);
                                System.out.println("Workflows available for execution: " + serviceResult);
                                if(serviceResult.isEmpty()) {
                                    // This workflow type should be in the list
                                    events.add(new Event("Work-error"));
                                    env.put("Error", "The list of Workflows Available is empty");
                                }
                                // Check if the request asked for testing more services
                                List<String> toFindServices = (List<String>) env.get("Services");
                                Boolean serviceRemoved = toFindServices.remove(
                                            WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
                                if(toFindServices.size()!= 0) {
                                    events.add(new Event("More-Work"));
                                }
                                else {
                                    events.add(new Event("Work-done"));
                                }
                                // Set flag corresponding to this service as false
                                Boolean newValueFlag = (Boolean) env.computeIfPresent(
                                                                WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION,
                                                                (k,v) -> v = false ); 
                            }
                            else {
                                events.add(new Event("Work-error"));
                                env.put("Error","Service " + WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION 
                                        + "was requested in input configuration, but its address could not be found, "
                                        + "possible error when orchestrated");
                            }
                            printEvents(events, "end of Transition 1");
                        },
                        // Next State:
                        1
                    ),
                    new Transition(
                        new LogicExpression<Event,Set<Event>>(LogicOperator.OR, 
                                                              List.of(new Event("Init-Success"), new Event("More-Work"))), 
                        new LogicExpression<Guard,Map<String, Object>>(null, List.of(
                                                new Guard(WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION, 
                                                true))),
                        (env, events) -> {
                            printEvents(events, "start of Transition 2");
                            printEnvironment(env, "start of Transition 2");
                            
                            // Test service
                            System.out.println("Transition 2: Test Service Workflows in-execution");
                            Map<String,OrchestrationResultDTO> serviceAndAddress = 
                                    (Map<String, OrchestrationResultDTO>) env.get("ServicesAddress");
                            
                            if (serviceAndAddress.containsKey(WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION)) {
                                OrchestrationResultDTO WExecutorInfo = serviceAndAddress.get(
                                        WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
                                //List<QueuedWorkflowDTO> serviceResult = consumeService(Collections.emptyList(), WExecutorInfo, null, null);
                                List<QueuedWorkflowDTO> serviceResult = consumeService(new ArrayList<QueuedWorkflowDTO>(), WExecutorInfo, null, null);
                                env.put("InExecutionWorkflows", serviceResult);
                                System.out.print(serviceResult);
                                // No workflow has started execution yet
                                if(serviceResult.isEmpty()) {
                                    // This workflow is on-going so it should be in the list
                                    events.add(new Event("Work-error"));
                                    env.put("Error", "The list of Workflows In-Execution is empty");
                                }
                                List<String> toFindServices = (List<String>) env.get("Services");
                                Boolean serviceRemoved = toFindServices.remove(
                                            WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
                                if(toFindServices.size()!= 0) {
                                    events.add(new Event("More-Work"));
                                }
                                else {
                                    events.add(new Event("Work-done"));
                                }
                                // Set flag corresponding to this service as false
                                Boolean newValueFlag = (Boolean) env.computeIfPresent(
                                                                WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION,
                                                                (k,v) -> v = false ); 
                            }
                            else {
                                events.add(new Event("Work-error"));
                                env.put("Error","Service " + WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION 
                                        + "was requested in input configuration, but its address could not be found, "
                                        + "possible error when orchestrated");
                            }
                            printEvents(events, "end of Transition 2");
                        },
                        // Next State:
                        1
                    ),
                    new Transition(
                        new LogicExpression<Event,Set<Event>>(LogicOperator.OR, 
                                                              List.of(new Event("Init-Success"), new Event("More-Work"))), 
                        new LogicExpression<Guard,Map<String, Object>>(null, List.of(
                                                new Guard(WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION, 
                                                true))),
                        (env, events) -> {
                            printEvents(events, "start of Transition 3");
                            printEnvironment(env, "start of Transition 3");
                            
                            // Test service
                            System.out.println("Transition 3: Test Service execute workflow");
                            Map<String,OrchestrationResultDTO> serviceAndAddress = 
                                    (Map<String, OrchestrationResultDTO>) env.get("ServicesAddress");
                            
                            if (serviceAndAddress.containsKey(WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION)) {
                                OrchestrationResultDTO WExecutorInfo = serviceAndAddress.get(
                                        WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION);
                                
                                // Create WorkflowDTO needed as input to the service request
                                WorkflowDTO requestBody = new WorkflowDTO(workflowTestName, null);
                                // Create QueuedWorkflowDTO used to store the service response
                                QueuedWorkflowDTO classReference = new QueuedWorkflowDTO();
                                //QueuedWorkflowDTO serviceResult = consumeService(null, WExecutorInfo, null, null);
                                QueuedWorkflowDTO serviceResult = consumeService(classReference, WExecutorInfo, requestBody, null);
                                if(serviceResult  == null) {
                                    // This workflow has already being executed once, so it should be possible to do it again
                                    events.add(new Event("Work-error"));
                                    env.put("Error", "The returned QueuedWorkflowDTO is not well formed, is empty");
                                }
                                
                                List<String> toFindServices = (List<String>) env.get("Services");
                                Boolean serviceRemoved = toFindServices.remove(
                                            WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION);
                                if(toFindServices.size()!= 0) {
                                    events.add(new Event("More-Work"));
                                }
                                else {
                                    events.add(new Event("Work-done"));
                                }
                                // Set flag corresponding to this service as false
                                Boolean newValueFlag = (Boolean) env.computeIfPresent(
                                                                WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION,
                                                                (k,v) -> v = false ); 
                            }
                            else {
                                events.add(new Event("Work-error"));
                                env.put("Error","Service " + WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION 
                                        + "was requested in input configuration, but its address could not be found, "
                                        + "possible error when orchestrated");
                            }
                            printEvents(events, "end of Transition 3");
                        },
                        // Next State:
                        1
                    ),
                    new Transition(
                        new LogicExpression<Event,Set<Event>>(LogicOperator.OR, 
                                List.of(new Event("Init-Fail"), new Event("Work-error"),new Event("Work-done"))), 
                        null, 
                        (env, events) -> {
                            printEvents(events, "start of Transition 4");
                            printEnvironment(env, "start of Transition 4");
                            
                            System.out.println("Transition 4: End State Machine and output its results");
                            if (env.containsKey("Error")) {
                                env.put("OutputStateMachine", 500);
                            }
                            else {
                                env.put("OutputStateMachine", 200);
                            }

                            printEvents(events, "end of State Machine");
                            printEnvironment(env, "end of State Machine");
                        }, 
                        // Next State:
                        2
                    )
                ));
        
        Workflow testWorkflow = new Workflow(workflowTestName, workflowTestConfig, workflowTestMachine);
        
        return testWorkflow;
    }
    
    /**
     * Prints nicely the events in any transition. 
     * <p>
     * Can provide extra information about where in the State Machine this events are with the
     * second argument of the method. Just choose a position like "Transition 2", or
     * "end of Transition 3", or "start of State 5".
     * 
     * @param events The set of Events in the transition from where this method is call, provided
     * as input when creating the transition, not null
     * @param reference The extra information provided as plain text that helps locate from where
     * the method was called, and helps to reference the events, not null
     */
    private void printEvents(Set<Event> events, String reference) {
        if (reference.isEmpty()) {
            System.out.println("The Events are: " + events);
        }
        else {
            System.out.println("The Events at " + reference + " are: " + events);
        }
    }
    
    /**
     * Prints nicely the environment in any transition. 
     * <p>
     * Can provide extra information about where in the State Machine this environment is with the
     * second argument of the method. Just choose a position like "Transition 2", or
     * "end of Transition 3", or "start of State 5".
     * 
     * @param env  The environment in the transition from where this method is call, provided
     * as input when creating the transition, not null
     * @param reference  The extra information provided as plain text that helps locate from where
     * the method was called, and helps to reference the environment, not null
     */
    private void printEnvironment(Map<String, Object> env, String reference) {
        
        if (reference.isEmpty()) {
            System.out.println("The Environment contains: " + env);
        }
        else {
            System.out.println("The Environment at " + reference + " contains: " + env);
        }
    }
    
    /**
     * Not implemented yet.
     * 
     */
    // TODO: Find other ways to extract the current state of the QueuedWorkflow
    private void printCurrentState(String workflowName) {
        /* To implement this method correctly I will need to inject the WExecutorService and 
         * retrieve the list with active Workflows. But then this State Machine could alter the 
         * WorkflowExecutor state and other Workflows stored in it, and it should not be authorized.
         */
    }
    
    /**
     * Retrieves and prints the state number and (name) in which the State Machine was at the
     * start of the transition. Not working as intended, always prints the same value.
     * <p>
     * If the workflowName is not the one corresponding to the Workflow from which the transition
     * that calls this method is executed, the output will not make sense. It will probably be
     * 0 as it is the default initial state for State Machines that have not specified a State, when
     * their status is IDLE, but there are no guarantees.
     * 
     * @param workflowName  The name of the Workflow that contains the State Machine and Transition
     * from where this method is call, not null
     * @throws IllegalArgumentException  if the workflowName parameter does not match the workflows
     * of this Workflow Executor system
     */
    /* How to NOT obtain the state from inside the State Machine, it will always print the same result,
     * the first state that by default is 0
     */
    @Deprecated
    private void printCurrentStateBad(String workflowName) {
        
        for(Workflow w : this.getWorkflows()) {
            if(w.getWorkflowName().equals(workflowName)) {
                System.out.println("State Machine in state " + w.getWorkflowLogic().getCurrentState() 
                        + " (" + w.getWorkflowLogic().getActiveState().name() + ")");
                /* I can not get the next state as I can not find a reference to the Transition from 
                 * which this method will be call
                 */
                //System.out.println("Changing to state " + w.getWorkflowLogic()... targetState());
                return;
            }
        }
        throw new IllegalArgumentException("Workflow name \""+ workflowName + "\" does not correspond to any Workflow stored in this system");
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
            logger.error("No orchestration response received");
        } else if (orchestrationResponse.getResponse().isEmpty()) {
            logger.error("No provider found during the orchestration");
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
    
    /**
     * Calls a Http service to consume it, with the response wrapped in the type of the first argument provided.
     * <p>
     * To consume the service, it only needs the information provided by the Orchestrator, after the
     * orchestration process is finished. This works assuming that the published service specifies in its
     * metadata which HTTP method (GET,POST,PUT,DELETE,...) should be used when calling the service.
     * <p>
     * Depending on the service, to consume it we might need to provide a payload or metadata, which are optional
     * parameters for this method.
     * <p>
     * At the moment there is a problem in eu.arrowhead.common.Utilities.createURI() which throws NullPointerException,
     * so the parameter metadata, which is optional, has to be filled with values in order do work. Therefore I remove the
     * final keyword from metadata and add empty strings, if it has not being filled by caller.
     * 
     * @param <T>
     * @param ResponseDTO
     * @param orchestrationResult
     * @param payload
     * @param metadata
     * @return
     */
    private <T> T consumeService(T ResponseDTO, final OrchestrationResultDTO orchestrationResult, 
            final Object payload, String[] metadata) {
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
