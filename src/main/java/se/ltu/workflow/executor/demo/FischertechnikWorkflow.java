package se.ltu.workflow.executor.demo;

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
import se.ltu.workflow.executor.WExecutorConstants;
import se.ltu.workflow.executor.service.Workflow;
import se.ltu.workflow.executor.state_machine.Event;
import se.ltu.workflow.executor.state_machine.LogicExpression;
import se.ltu.workflow.executor.state_machine.LogicOperator;
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
    private static final String SERVICES_TO_ADDRESS = "servicesToAddress";
    
    private static final Event INIT_OK_EV = new Event("Init Success");
    private static final Event INIT_FAIL_EV = new Event("Init Failure");
    
    private static final Event SERVICES_OK_EV = new Event("Services found");
    
    private static final Event WAITING_PRODUCT_EV = new Event("Waiting for product");
    private static final Event PRODUCT_READY_EV = new Event("Product ready");
    private static final Event PRODUCT_IN_CONVEYOR_EV = new Event("Product moving in conveyor belt");
    private static final Event PRODUCT_END_CONVEYOR_EV = new Event("Product at the end of conveyor belt");
    private static final Event PRODUCT_IN_SLIDER_EV = new Event("Product pushed by slider motor");
    private static final Event PRODUCT_END_SLIDER_EV = new Event("Product at the end of slider motor");
    private static final Event PRODUCT_IN_MILLING_EV = new Event("Product arrived at milling");
    private static final Event PRODUCT_READY_MILLING_EV = new Event("Product ready for being milled");
    private static final Event PRODUCT_END_MILLING_EV = new Event("Product end milling operation");
    
    private static final Event MOTOR_SLIDER_ORIGIN_EV = new Event("Motor slider at starting position");
    
    private static final Event WORK_ONGOING_EV = new Event("Ongoing work");
    private static final Event WORK_DONE_EV = new Event("Work done");
    
    private static final String PRODUCT_READY_MILLING = "Milling ready";
    private static final String PRODUCT_READY_DRILLING = "Drilling ready";
    private static final String SLIDER_1_ORIGIN = "Slider 1 at starting position";
    private static final String SLIDER_2_ORIGIN = "Slider 2 at starting position";
    
    private static final String STATE_MACHINE_RESULT = "State Machine result";
    private static final String ERROR_MESSAGE = "Error message";
    
    private static final String NMILLING = "numberOfMilling";
    private static final String TIMEMILLING = "timeOfMillingInMilis";
    
    private final Logger logger = LogManager.getLogger(FischertechnikWorkflow.class);
    
    public Workflow milling() {
        String workflowName = "milling";
        
        Map<String, List<String>> workflowConfig = new HashMap<>(Map.ofEntries(
                Map.entry(NMILLING, new ArrayList<>(List.of("Integer")) ),
                Map.entry(TIMEMILLING, new ArrayList<>(List.of("Integer")))));
        
        // Here we can see the limits of the State Machine, when an State has a transition froim 
        StateMachine workflowMachine = new StateMachine(
            Arrays.asList(
                    new State("Find factory services and input config", 0),
                    new State("Detect product", 1,7),
                    new State("Product going through input conveyor", 1,2),
                    new State("Product pushed by slider motor", 2,3),
                    new State("Product going through milling conveyor", 3,4,5,6),
                    new State("Product at milling station"),
                    
//                    new State("Stop slider motor 1", 6,7),
//                    new State("Stop milling conveyor", 5,7),
//                    new State("Start milling", 8),
//                    new State("Stop milling and start milling conveyor", 9),
//                    new State("Stop milling conveyor and start drilling conveyor", 10),
//                    new State("Stop drilling conveyor with delay", 11),
//                    new State("Start slider motor 2", 12),
//                    new State("Stop and back slider motor 2 & start output conveyor", 13,14),
//                    new State("Stop slider motor 2", 14,15),
//                    new State("Stop output conveyor", 13,15),
//                    new State("Send workflow results", 16),
                    
                    new State("End workflow")
                    ),
            Arrays.asList(
                new Transition( // Transition 0
                    null, 
                    null, 
                    (env, events) -> {
                        System.out.println("Transition 0: Examine State Machine input configuration"
                                + " and services");
                        events.add(findServices(env,events) ? SERVICES_OK_EV : INIT_FAIL_EV);
                        if(events.contains(INIT_FAIL_EV)) {
                            env.put(ERROR_MESSAGE, "Services needed for Milling not available in workstation");
                            return;
                        }
                        
                        //Check that slider motors are at starting position
                        if(getSensor(env, 2)) {
                            env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        }else {
                            env.put(ERROR_MESSAGE, SLIDER_1_ORIGIN + " = FALSE ");
                            events.add(INIT_FAIL_EV);
                            return;
                        }
                        if(getSensor(env, 4)) {
                            env.put(SLIDER_2_ORIGIN, Boolean.TRUE);
                        }else {
                            env.put(ERROR_MESSAGE, SLIDER_2_ORIGIN + " = FALSE ");
                            events.add(INIT_FAIL_EV);
                            return;
                        }
                        
                        // Setup the environment
                        if(!env.containsKey("numberOfMilling")) {
                            logger.info("Input configuration for number of drilling operations is missing.\n"
                                    + "Setting default configuration to 1 number of drillings");
                            env.put("numberOfMilling", 1);
                        }
                        if(!env.containsKey("timeOfMillingInMilis")) {
                            logger.info("Input configuration for time of drilling operations is missing.\n"
                                    + "Setting default configuration to 1 second for each drilling");
                            env.put("timeOfMillingInMilis", 1000);
                        }
                        env.put(PRODUCT_READY_MILLING, Boolean.FALSE);
//                        env.put(PRODUCT_READY_DRILLING, Boolean.FALSE);
                        
                    },
                    1),
                new Transition( // Transition 1
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR, 
                            List.of(SERVICES_OK_EV,
                                    WAITING_PRODUCT_EV)),
                    null, 
                    (env, events) -> {
                        System.out.println("Transition 1: Wait for product to arrive and start conveyor");
                        if (!getSensor(env, 7)) {
                            logger.info("Product detected, starting conveyor belt");
                            events.add(PRODUCT_READY_EV);
                            setActuator(env, 5, true);
                        }
                        else {
                            events.add(WAITING_PRODUCT_EV);
                        }
                    }, 
                    2),
                new Transition( // Transition 2
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR, 
                            List.of(PRODUCT_READY_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null, 
                    (env, events) -> {
                        System.out.println("Transition 2: Wait for product to leave feed conveyor "
                                + "and stop it");
                        if (!getSensor(env, 5)) {
                            logger.info("Product detected at end of conveyor belt, "
                                    + "wait a second before stopping the conveyor");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            events.add(PRODUCT_END_CONVEYOR_EV);
                            // Stop feeding conveyor
                            setActuator(env, 5, false);
                            // Start slider motor 1
                            setActuator(env, 1, true);
                            env.put(SLIDER_1_ORIGIN, Boolean.FALSE);
                        }
                        else {
                            events.add(PRODUCT_IN_CONVEYOR_EV);
                        }
                    }, 
                    3),
                new Transition( // Transition 3
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR, 
                            List.of(PRODUCT_END_CONVEYOR_EV,
                                    PRODUCT_IN_SLIDER_EV)),
                    null, 
                    (env, events) -> {
                        System.out.println("Transition 3: Wait for product to leave slider motor 1");
                        if (getSensor(env, 1)) {
                            logger.info("Product detected at end of slider motor, star milling conveyor"
                                    + "and push slider backwards");
                            // Stop forward movement of slider motor 1
                            setActuator(env, 1, false);
                            // Start milling conveyor
                            setActuator(env, 6, true);
                            // Start backwards movement of slider motor 1
                            setActuator(env, 2, true);
                            events.add(PRODUCT_END_SLIDER_EV);
                        }
                        else {
                            events.add(PRODUCT_IN_SLIDER_EV);
                        }
                        
                    }, 
                    4),
                new Transition( // Transition 4
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR, 
                            List.of(PRODUCT_END_SLIDER_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null, 
                    (env, events) -> {
                        System.out.println("Transition 4: Wait for product to arrive to milling station");
                        
                        if (!getSensor(env, 6) && !(Boolean)env.get(PRODUCT_READY_MILLING)) {
                            logger.info("Product detected at milling station");
                            setActuator(env, 6, false);
                            env.put(PRODUCT_READY_MILLING, Boolean.TRUE);
                        }
                        if (getSensor(env, 2) && !(Boolean)env.get(SLIDER_1_ORIGIN)) {
                            logger.info("Motor slider detected at starting position");
                            events.add(MOTOR_SLIDER_ORIGIN_EV);
                            return;
                        }
                        if ((Boolean)env.get(PRODUCT_READY_MILLING) && (Boolean)env.get(SLIDER_1_ORIGIN)) {
                            events.add(PRODUCT_READY_MILLING_EV);
                            return;
                        }
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    }, 
                    4),
                new Transition( // Transition 5
                    new LogicExpression<Event,Set<Event>>(
                            null, 
                            List.of(MOTOR_SLIDER_ORIGIN_EV)),
                    null, 
                    (env, events) -> {
                        System.out.println("Transition 5: Slider motor returned to starting position");
                        setActuator(env, 2, false);
                        env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    }, 
                    4),
                new Transition( // Transition 6
                    new LogicExpression<Event,Set<Event>>(
                            null, 
                            List.of(PRODUCT_READY_MILLING_EV)),
                    null, 
                    (env, events) -> {
                        System.out.println("Transition 6: Mill product");
                        for(int i = 0; i < (int)env.get(NMILLING); i++) {
                            setActuator(env, 7, true);
                            try {
                                Thread.sleep((int)env.get(TIMEMILLING));
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            setActuator(env, 7, false);
                        }
//                        events.add(e);
                    }, 
                    5),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    9),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    10),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    11),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    12),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    13),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    14),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    15),
//                new Transition(
//                    new LogicExpression<Event,Set<Event>>(null, List.of(new Event(SERVICE_OK))),
//                    null, 
//                    (env, events) -> {
//                        
//                    }, 
//                    16),
                new Transition(
                    new LogicExpression<Event,Set<Event>>(LogicOperator.OR, List.of(INIT_FAIL_EV)),
                    null, 
                    (env, events) -> {
                        System.out.println(env.get(ERROR_MESSAGE));
                        
                        // Stop factory
                        setActuator(env, 1, false);
                        setActuator(env, 2, false);
                        setActuator(env, 3, false);
                        setActuator(env, 4, false);
                        setActuator(env, 5, false);
                        setActuator(env, 6, false);
                        setActuator(env, 7, false);
                        setActuator(env, 8, false);
                        setActuator(env, 9, false);
                        setActuator(env, 10, false);
                    }, 
                    6)
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
    
    // Specific methods to use the Fischer factory
    //-------------------------------------------------------------------------------------------------
    
    /**
     * Finds the services specified in {@code servicesFactory} and stores the orchestration
     * result in the environment in the variable {@code ServicesAddress}
     * 
     * @param env  The environment of the State Machine to store the results if services found
     * @param events  The events of the State Machine that will serve to communicate the result of
     * this call
     */
    private Boolean findServices(Map<String, Object> env, Set<Event> events) {
        Map<String,OrchestrationResultDTO> serviceAndAddress = new HashMap<>();
        try {
            for(String serviceDefinition : servicesFactory) {
                serviceAndAddress.put(serviceDefinition, orchestrate(serviceDefinition));
            }
            env.put(SERVICES_TO_ADDRESS, serviceAndAddress);
            return true;
        } catch (ArrowheadException e) {
            return false;
        }
    }
    
    
    /**
     * Gets the value of the sensor requested, always in the range 1-9
     * 
     * @param sensorNumber The sensor number, has to be inside the range 1-9
     * @return The boolean value of the sensor
     * @throws IllegalArgumentException if the input number is out of the sensors id range
     */
    private Boolean getSensor(Map<String, Object> env, int sensorNumber) throws IllegalArgumentException{
        if(sensorNumber < 1 || sensorNumber > 9) {
            throw new IllegalArgumentException("The input sensor number is out of range, "
                    + "is has to be between 1 and 9");
        }
        var serviceAndAddress = (Map<String, OrchestrationResultDTO>) env.get(SERVICES_TO_ADDRESS);
        OrchestrationResultDTO service = serviceAndAddress.get(servicesFactory.get(0));
        // Copy service information into a new object and modify it
        var infoRequest = new OrchestrationResultDTO(
                service.getProvider(), 
                service.getService(), 
                service.getServiceUri(), 
                service.getSecure(), 
                service.getMetadata(), 
                service.getInterfaces(), 
                service.getVersion());
        infoRequest.setServiceUri(infoRequest.getServiceUri() + "/I" + sensorNumber);
        HttpMethod method = HttpMethod.GET;
        logger.info("Sending " + method + " request to: " +  infoRequest.getServiceUri());
        return consumeFactoryService(infoRequest, method);
    }
    
    /**
     * Gets the value of the actuator requested, always in the range 1-10
     * 
     * @param actuatorNumber The actuator number, has to be inside the range 1-10
     * @return The boolean value of the actuator
     * @throws IllegalArgumentException if the input number is out of the actuators id range
     */
    private Boolean getActuator(Map<String, Object> env, int actuatorNumber) throws IllegalArgumentException{
        if(actuatorNumber < 1 || actuatorNumber > 10) {
            throw new IllegalArgumentException("The input actuator number is out of range, "
                    + "is has to be between 1 and 10");
        }
        var serviceAndAddress = (Map<String, OrchestrationResultDTO>) env.get(SERVICES_TO_ADDRESS);
        OrchestrationResultDTO service = serviceAndAddress.get(servicesFactory.get(1));
        // Copy service information into a new object and modify it
        var infoRequest = new OrchestrationResultDTO(
                service.getProvider(), 
                service.getService(), 
                service.getServiceUri(), 
                service.getSecure(), 
                service.getMetadata(), 
                service.getInterfaces(), 
                service.getVersion());
        infoRequest.setServiceUri(infoRequest.getServiceUri() + "/Q" + actuatorNumber);
        HttpMethod method = HttpMethod.GET;
        logger.info("Sending " + method + " request to: " +  infoRequest.getServiceUri());
        return consumeFactoryService(infoRequest, method);
    }
    
    /**
     * Sets the value of the actuator requested, always in the range 1-10
     * 
     * @param actuatorNumber  The actuator number, has to be inside the range 1-10
     * @param value  The future value of the actuator when the method returns
     * @return The boolean value of the actuator
     * @throws IllegalArgumentException if the input number is out of the actuators id range
     */
    private Boolean setActuator(Map<String, Object> env, int actuatorNumber, Boolean value) throws IllegalArgumentException{
        if(actuatorNumber < 1 || actuatorNumber > 10) {
            throw new IllegalArgumentException("The input actuator number is out of range, "
                    + "is has to be between 1 and 10");
        }
        var serviceAndAddress = (Map<String, OrchestrationResultDTO>) env.get(SERVICES_TO_ADDRESS);
        OrchestrationResultDTO service = serviceAndAddress.get(servicesFactory.get(1));
        // Copy service information into a new object and modify it
        var infoRequest = new OrchestrationResultDTO(
                service.getProvider(), 
                service.getService(), 
                service.getServiceUri(), 
                service.getSecure(), 
                service.getMetadata(), 
                service.getInterfaces(), 
                service.getVersion());
        infoRequest.setServiceUri(infoRequest.getServiceUri() + "/Q" + actuatorNumber 
                + "/" + Boolean.toString(value));
        HttpMethod method = HttpMethod.PUT;
        logger.info("Sending " + method + " request to: " +  infoRequest.getServiceUri());
        return consumeFactoryService(infoRequest, method);
    }
    
    /**
     * Sends the Http request to the services offered by the OPC-UA server
     * 
     * @param URI
     * @return
     */
    private Boolean consumeFactoryService(OrchestrationResultDTO orch, HttpMethod method) {
        var serviceResult = consumeService(FactoryDeviceDTO.classReference(), orch, method, null, null);
        return Boolean.parseBoolean(serviceResult.getValue());
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
     * orchestration process is finished, and HTTP method of the service, GET, POST, PUT...
     * <p>
     * Depending on the service, to consume it we might need to provide a payload or metadata, which are optional
     * parameters for this method.
     * 
     * @param <T>
     * @param ResponseDTO
     * @param orchestrationResult
     * @param payload
     * @param metadata
     * @return
     */
    private <T> T consumeService(T ResponseDTO, final OrchestrationResultDTO orchestrationResult, HttpMethod method, 
            final Object payload, String[] metadata) {
        final String token = orchestrationResult.getAuthorizationTokens() == null ? null : orchestrationResult.getAuthorizationTokens().get(getInterface());
        
        return (T) arrowheadService.consumeServiceHTTP(
                ResponseDTO.getClass(), // The type of object that the Response will be matched to
                method,
                orchestrationResult.getProvider().getAddress(), 
                orchestrationResult.getProvider().getPort(), 
                orchestrationResult.getServiceUri(),
                getInterface(), 
                token, 
                payload, 
                metadata);
    }

}