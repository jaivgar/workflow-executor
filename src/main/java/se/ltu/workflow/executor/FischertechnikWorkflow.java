package se.ltu.workflow.executor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import se.ltu.workflow.executor.service.Workflow;
import se.ltu.workflow.executor.state_machine.Event;
import se.ltu.workflow.executor.state_machine.LogicExpression;
import se.ltu.workflow.executor.state_machine.State;
import se.ltu.workflow.executor.state_machine.StateMachine;
import se.ltu.workflow.executor.state_machine.Transition;



@Component
public class FischertechnikWorkflow {
    
    /**
     * Utility field to use the Arrowhead Client Library functionality, like contacting the
     * Arrowhead Core systems, or  requesting orchestration.
     * <p>
     * As this is included through a Spring bean, it can not be static and the methods that create
     * the different workflows can not be static either.
     */
    @Autowired
    private ArrowheadService arrowheadService;
    
    /**
     * Utility field to use the Arrowhead Client Library functionality, needed to obtain the
     * preferred interface of a system depending on the security level, indicated by this field.
     */
    @Autowired
    protected SSLProperties sslProperties;
    
    /**
     * Service definitions of services needed to operate Fischer factory
     */
    private static List<String> servicesFactory = List.of("sensorvalue", "actuatorvalue");
    
    private static final String INIT_OK = "Init-Success";
    private static final String SERVICE_OK = "Services found";
    
    
    private final Logger logger = LogManager.getLogger(FischertechnikWorkflow.class);
    
    public Workflow milling() {
        String workflowName = "milling";
        
        Map<String, List<String>> workflowConfig = new HashMap<>(Map.ofEntries(
                Map.entry("numberOfMilling", new ArrayList<>(List.of("Integer")) ),
                Map.entry("timeOfMillingInMilis", new ArrayList<>(List.of("Integer")))));
        
        StateMachine workflowMachine = new StateMachine(
                Arrays.asList(
                        new State("Find factory services and input config", 0,17),
                        new State("Detect product", 1),
                        new State("Start input conveyor", 2),
                        new State("Stop input conveyor with delay", 3),
                        new State("Start slider motor 1", 4),
                        new State("Stop and back slider motor 1 & start milling conveyor", 5,6),
                        new State("Stop slider motor 1", 6,7),
                        new State("Stop milling conveyor", 5,7),
                        new State("Start milling", 8),
                        new State("Stop milling and start milling conveyor", 9),
                        new State("Stop milling conveyor and start drilling conveyor", 10),
                        new State("Stop drilling conveyor with delay", 11),
                        new State("Start slider motor 2", 12),
                        new State("Stop and back slider motor 2 & start output conveyor", 13,14),
                        new State("Stop slider motor 2", 14,15),
                        new State("Stop output conveyor", 13,15),
                        new State("Send workflow results", 16),
                        new State("End workflow")
                        ),
                Arrays.asList(
                    new Transition(
                        null, 
                        null, 
                        (env, events) -> {
                            System.out.println("Transition 0: Look at State Machine input configuration"
                                    + " and services");
                            findServices(env,events);
                            if(!env.containsKey("numberOfMilling")) {
                                env.put("numberOfMilling", 1);
                            }
                            if(!env.containsKey("timeOfMillingInMilis")) {
                                env.put("timeOfMillingInMilis", 1000);
                            }
                        },
                        1),
                    new Transition(
                            new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
                            null, 
                            (env, events) -> {
                                
                            }, 
                            2)
                )
                );
        
        Workflow millingWorkflow = new Workflow(workflowName, workflowConfig, workflowMachine);
        
        return millingWorkflow;
        
    }
    
    public Workflow drilling() {
        String workflowName = "testWorkflowExecutor";
        
        Map<String, List<String>> workflowConfig = new HashMap<>(Map.ofEntries(
                Map.entry("numberOfDrilling", new ArrayList<>(List.of("Integer")) ),
                Map.entry("timeOfDrillingInMilis", new ArrayList<>(List.of("Integer")))));
        
        StateMachine workflowMachine = null;
        
        Workflow millingWorkflow = new Workflow(workflowName, workflowConfig, workflowMachine);
        
        return millingWorkflow;
        
    }
    
    public Workflow millingAndDrilling() {
        String workflowName = "testWorkflowExecutor";
        
        Map<String, List<String>> workflowConfig = new HashMap<>(Map.ofEntries(
                Map.entry("numberOfMilling", new ArrayList<>(List.of("Integer")) ),
                Map.entry("timeOfMillingInMilis", new ArrayList<>(List.of("Integer")) ),
                Map.entry("numberOfDrilling", new ArrayList<>(List.of("Integer")) ),
                Map.entry("timeOfDrillingInMilis", new ArrayList<>(List.of("Integer")))));
        
        StateMachine workflowMachine = null;
        
        Workflow millingWorkflow = new Workflow(workflowName, workflowConfig, workflowMachine);
        
        return millingWorkflow;
        
    }
    
    
    private void findServices(Map<String, Object> env, Set<Event> events) {
        Map<String,OrchestrationResultDTO> serviceAndAddress = new HashMap<>();
        try {
            for(String serviceDefinition : servicesFactory) {
                serviceAndAddress.put(serviceDefinition, orchestrate(serviceDefinition));
            }
            env.put("ServicesAddress", serviceAndAddress);
            events.add(new Event("Services found"));
        } catch (ArrowheadException e) {
            events.add(new Event("Error Init"));
        }
    }
    
    // General methods to consumer Arrowhead services
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
