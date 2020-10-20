package se.ltu.workflow.executor.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
     * Name of workflows created in this class
     */
    private static final String WORKFLOW_NAME_MILL = "milling";
    private static final String WORKFLOW_NAME_DRILL = "drilling";
    private static final String WORKFLOW_NAME_MILL_AND_DRILL = "millingAndDrilling";
    
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
    private static final Event PRODUCT_END_MILLING_EV = new Event("Product ended milling operation");
    private static final Event PRODUCT_IN_DRILLING_EV = new Event("Product arrived at drilling");
    private static final Event PRODUCT_READY_DRILLING_EV = new Event("Product ready for being drilled");
    private static final Event PRODUCT_END_DRILLING_EV = new Event("Product ended drilling operation");
    private static final Event PRODUCT_DONE_EV = new Event("Product ended manufacturing");
    
    private static final Event MOTOR_SLIDER_ORIGIN_EV = new Event("Motor slider at starting position");
    
    private static final Event WORK_ONGOING_EV = new Event("Ongoing work");
    private static final Event WORK_DONE_EV = new Event("Work done");
    private static final Event WORK_ERROR_EV = new Event("Work error");
    
    private static final String PRODUCT_READY_MILLING = "Milling ready";
    private static final String PRODUCT_DONE_MILLING = "Milling done";
    private static final String PRODUCT_READY_DRILLING = "Drilling ready";
    private static final String PRODUCT_DONE_DRILLING = "Drilling done";
    private static final String PRODUCT_AT_OUTPUT = "Product at output location";
    
    private static final String SLIDER_1_ORIGIN = "Slider 1 at starting position";
    private static final String SLIDER_2_ORIGIN = "Slider 2 at starting position";
    
    private static final String STATE_MACHINE_RESULT = "State machine result";
    private static final String SUCCESS = "success";
    private static final String ERROR = "error";
    private static final String ERROR_MESSAGE = "Error message";
    
    private static final String NMILLING = "numberOfMilling";
    private static final String TIMEMILLING = "timeOfMillingInSec";
    private static final String NDRILLING = "numberOfDrilling";
    private static final String TIMEDRILLING = "timeOfDrillingInSec";
    
    private final Logger logger = LogManager.getLogger(FischertechnikWorkflow.class);
    
    public Workflow milling() {
        String workflowName = WORKFLOW_NAME_MILL;
        
        Map<String, List<String>> workflowConfig = new HashMap<>(Map.ofEntries(
                Map.entry(NMILLING, new ArrayList<>(List.of("Integer")) ),
                Map.entry(TIMEMILLING, new ArrayList<>(List.of("Integer")))));
        
        StateMachine workflowMachine = new StateMachine(
            Arrays.asList(
                    new State("Find factory services and input config", 0),
                    new State("Detect product", 1,12),
                    new State("Product going through input conveyor", 1,2),
                    new State("Product pushed by slider motor 1", 2,3),
                    new State("Product going through milling conveyor", 3,4,5,6),
                    new State("Product at milling station", 7), // State 5
                    new State("Product leave milling and drilling stations", 8),
                    new State("Product pushed by slider motor 2", 8,9),
                    new State("Product going through output conveyor", 9,10),
                    new State("Product finished, send results",10,11),
                    new State("End workflow succesfully"), // State 10
                    new State("End workflow with errors")
                    ),
            Arrays.asList(
                new Transition( // Transition 0
                    null,
                    null,
                    (env, events) -> {
                        logger.info("Transition 0: Examine State Machine input configuration"
                                + " and services");
                        
                        // Check the input configuration
                        if(!validateMillingConfig(env, events, 1, 1)) return;
                        
                        // Search services needed for State Machine
                        events.add(findServices(env,events) ? SERVICES_OK_EV : INIT_FAIL_EV);
                        if(events.contains(INIT_FAIL_EV)) {
                            env.put(ERROR_MESSAGE, "Services needed for Milling not available in workstation");
                            return;
                        }
                        
                        //Check that slider motors are at starting position
                        if(getSensor(env, 2)) {
                            env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        }else {
                            events.clear();
                            events.add(INIT_FAIL_EV);
                            env.put(ERROR_MESSAGE, SLIDER_1_ORIGIN + " = FALSE ");
                            return;
                        }
                        if(getSensor(env, 4)) {
                            env.put(SLIDER_2_ORIGIN, Boolean.TRUE);
                        }else {
                            events.clear();
                            events.add(INIT_FAIL_EV);
                            env.put(ERROR_MESSAGE, SLIDER_2_ORIGIN + " = FALSE ");
                            return;
                        }
                        
                        // Setup the environment
                        env.put(PRODUCT_READY_MILLING, Boolean.FALSE);
                        env.put(PRODUCT_DONE_MILLING, Boolean.FALSE);
                        env.put(PRODUCT_AT_OUTPUT, Boolean.FALSE);
                        
                    },
                    1),
                new Transition( // Transition 1
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(SERVICES_OK_EV,
                                    WAITING_PRODUCT_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 1: Wait for product to arrive and start conveyor");
                        if (!getSensor(env, 7)) {
                            logger.debug("Product detected, starting conveyor belt");
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
                        logger.info("Transition 2: Wait for product to leave feed conveyor "
                                + "and stop it");
                        if (!getSensor(env, 5)) {
                            logger.debug("Product detected at end of conveyor belt, "
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
                        logger.info("Transition 3: Wait for product to leave slider motor 1");
                        if (getSensor(env, 1)) {
                            logger.debug("Product detected at end of slider motor, star milling conveyor"
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
                        logger.info("Transition 4: Wait for product to arrive to milling station");
                        
                        if (!(Boolean)env.get(PRODUCT_READY_MILLING) && !getSensor(env, 6)) {
                            logger.debug("Product detected at milling station");
                            setActuator(env, 6, false);
                            env.put(PRODUCT_READY_MILLING, Boolean.TRUE);
                        }
                        if (!(Boolean)env.get(SLIDER_1_ORIGIN) && getSensor(env, 2)) {
                            logger.debug("Motor slider detected at starting position");
                            events.add(MOTOR_SLIDER_ORIGIN_EV);
                            return;
                        }
                        if ((Boolean)env.get(PRODUCT_READY_MILLING) && (Boolean)env.get(SLIDER_1_ORIGIN)) {
                            events.add(PRODUCT_IN_MILLING_EV);
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
                        logger.info("Transition 5: Slider motor returned to starting position");
                        setActuator(env, 2, false);
                        env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    },
                    4),
                /* Transition needed to change state from 4 to 5.
                 * In the future each state will have a environment method that will solve this problem,
                 * as the checks of values outside the state Machine and the throw of Events will be done
                 * by those environment methods.
                 */
                new Transition( // Transition 6
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_IN_MILLING_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 6: Product ready for milling, change state");
                        events.add(PRODUCT_READY_MILLING_EV);
                    },
                    5),
                new Transition( // Transition 7
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_READY_MILLING_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 7: Mill product");
                        for(int i = 0; i < (int)env.get(NMILLING); i++) {
                            setActuator(env, 7, true);
                            try {
                                Thread.sleep((int)env.get(TIMEMILLING)*1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            setActuator(env, 7, false);
                        }
                        env.put(PRODUCT_DONE_MILLING, Boolean.TRUE);
                        events.add(PRODUCT_END_MILLING_EV);
                        // Milling conveyor
                        setActuator(env, 6, true);
                        // Drilling conveyor
                        setActuator(env, 8, true);
                    },
                    6),
                new Transition( // Transition 8
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_MILLING_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 8: Leave milling and drilling station");
                        if(!getSensor(env, 8)) {
                            setActuator(env, 6, false);
                            logger.debug("Product detected at end of conveyor belt, "
                                    + "wait 1,5 seconds before stopping the conveyor");
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            // Stop drilling conveyor
                            setActuator(env, 8, false);
                            events.add(PRODUCT_END_CONVEYOR_EV);
                            // Start slider motor 2
                            setActuator(env, 3, true);
                            env.put(SLIDER_2_ORIGIN, Boolean.FALSE);
                        }else {
                            events.add(PRODUCT_IN_CONVEYOR_EV);
                        }
                    },
                    7),
                new Transition( // Transition 9
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_CONVEYOR_EV,
                                    PRODUCT_IN_SLIDER_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 9: Wait for product to leave slider motor 2");
                        if (getSensor(env, 3)) {
                            logger.debug("Product detected at end of slider motor, start output conveyor"
                                    + "and push slider backwards");
                            // Stop forward movement of slider motor 2
                            setActuator(env, 3, false);
                            // Start output conveyor
                            setActuator(env, 10, true);
                            // Start backwards movement of slider motor 2
                            setActuator(env, 4, true);
                            events.add(PRODUCT_END_SLIDER_EV);
                        }
                        else {
                            events.add(PRODUCT_IN_SLIDER_EV);
                        }
                    },
                    8),
                new Transition( // Transition 10
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_SLIDER_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 10: Wait for product to arrive "
                                + "to the end of factory");
                        if (!(Boolean)env.get(PRODUCT_AT_OUTPUT) && !getSensor(env, 9)) {
                            logger.debug("Product detected at output location");
                            setActuator(env, 10, false);
                            env.put(PRODUCT_AT_OUTPUT, Boolean.TRUE);
                        }
                        if (!(Boolean)env.get(SLIDER_2_ORIGIN) && getSensor(env, 4)) {
                            logger.debug("Motor slider 2 detected at starting position");
                            setActuator(env, 4, false);
                            env.put(SLIDER_2_ORIGIN, Boolean.TRUE);
                        }
                        if ((Boolean)env.get(PRODUCT_AT_OUTPUT) && (Boolean)env.get(SLIDER_2_ORIGIN)) {
                            events.add(PRODUCT_DONE_EV);
                            return;
                        }
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    },
                    9),
                new Transition( // Transition 11
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_DONE_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 11: Send success results and stop factory");
                        // Set the results to be passed to the caller of State Machine in the environment
                        env.put(STATE_MACHINE_RESULT, SUCCESS);
                        logger.debug("At the end State Machine = " + env.get(STATE_MACHINE_RESULT));
                        storeResultsAndStopFactory(env);
                    },
                    10),
                new Transition( // Transition 12
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(INIT_FAIL_EV,
                                    WORK_ERROR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 12: Send error results and stop factory");
                        
                        env.put(STATE_MACHINE_RESULT, ERROR);
                        logger.debug("At the end State Machine = " + env.get(STATE_MACHINE_RESULT));
                        logger.debug(env.get(ERROR_MESSAGE));
                        if(env.containsKey(SERVICES_TO_ADDRESS)) {
                            storeResultsAndStopFactory(env);
                        }
                    },
                    11)
            )
        );
        
        Workflow millingWorkflow = new Workflow(workflowName, workflowConfig, workflowMachine);
        
        return millingWorkflow;
    }
    
    public Workflow drilling() {
        String workflowName = "drilling";
        
        Map<String, List<String>> workflowConfig = new HashMap<>(Map.ofEntries(
                Map.entry(NDRILLING, new ArrayList<>(List.of("Integer")) ),
                Map.entry(TIMEDRILLING, new ArrayList<>(List.of("Integer")))));
        
        StateMachine workflowMachine = new StateMachine(
            Arrays.asList(
                    new State("Find factory services and input config", 0),
                    new State("Detect product", 1,12),
                    new State("Product going through input conveyor", 1,2),
                    new State("Product pushed by slider motor 1", 2,3),
                    new State("Product going through milling conveyor", 3,4,5,6),
                    new State("Product at drilling station", 7), // State 5
                    new State("Product leave drilling station", 8),
                    new State("Product pushed by slider motor 2", 8,9),
                    new State("Product going through output conveyor", 9,10),
                    new State("Product finished, send results",10,11),
                    new State("End workflow succesfully"), // State 10
                    new State("End workflow with errors")
                    ),
            Arrays.asList(
                new Transition( // Transition 0
                    null,
                    null,
                    (env, events) -> {
                        logger.info("Transition 0: Examine State Machine input configuration"
                                + " and services");
                        
                        // Check the input configuration
                        if(!validateDrillingConfig(env, events, 1, 1)) return;
                        
                        // Search services needed for State Machine
                        events.add(findServices(env,events) ? SERVICES_OK_EV : INIT_FAIL_EV);
                        if(events.contains(INIT_FAIL_EV)) {
                            env.put(ERROR_MESSAGE, "Services needed for Drilling not available in workstation");
                            return;
                        }
                        
                        //Check that slider motors are at starting position
                        if(getSensor(env, 2)) {
                            env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        }else {
                            events.clear();
                            events.add(INIT_FAIL_EV);
                            env.put(ERROR_MESSAGE, SLIDER_1_ORIGIN + " = FALSE ");
                            return;
                        }
                        if(getSensor(env, 4)) {
                            env.put(SLIDER_2_ORIGIN, Boolean.TRUE);
                        }else {
                            events.clear();
                            events.add(INIT_FAIL_EV);
                            env.put(ERROR_MESSAGE, SLIDER_2_ORIGIN + " = FALSE ");
                            return;
                        }
                        
                        // Setup the environment
                        env.put(PRODUCT_READY_DRILLING, Boolean.FALSE);
                        env.put(PRODUCT_DONE_DRILLING, Boolean.FALSE);
                        env.put(PRODUCT_AT_OUTPUT, Boolean.FALSE);              
                    },
                    1),
                new Transition( // Transition 1
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(SERVICES_OK_EV,
                                    WAITING_PRODUCT_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 1: Wait for product to arrive and start conveyor");
                        if (!getSensor(env, 7)) {
                            logger.debug("Product detected, starting conveyor belt");
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
                        logger.info("Transition 2: Wait for product to leave feed conveyor "
                                + "and stop it");
                        if (!getSensor(env, 5)) {
                            logger.debug("Product detected at end of conveyor belt, "
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
                        logger.info("Transition 3: Wait for product to leave slider motor 1");
                        if (getSensor(env, 1)) {
                            logger.debug("Product detected at end of slider motor, star milling conveyor"
                                    + "and push slider backwards");
                            events.add(PRODUCT_END_SLIDER_EV);
                            // Stop forward movement of slider motor 1
                            setActuator(env, 1, false);
                            // Start milling conveyor
                            setActuator(env, 6, true);
                            // Start backwards movement of slider motor 1
                            setActuator(env, 2, true);
                            // Start drilling conveyor
                            setActuator(env, 8, true);
                            
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
                        logger.info("Transition 4: Wait for product to arrive to drilling station");
                        if (!getSensor(env, 8) && !(Boolean)env.get(PRODUCT_READY_DRILLING)) {
                            logger.debug("Product detected at drilling station");
                            // Stop drilling conveyor
                            setActuator(env, 8, false);
                            // Stop milling conveyor
                            setActuator(env, 6, false);
                            env.put(PRODUCT_READY_DRILLING, Boolean.TRUE);
                        }
                        if (!(Boolean)env.get(SLIDER_1_ORIGIN) && getSensor(env, 2)) {
                            logger.debug("Motor slider 1 detected at starting position");
                            events.add(MOTOR_SLIDER_ORIGIN_EV);
                            return;
                        }
                        if ((Boolean)env.get(PRODUCT_READY_DRILLING) && (Boolean)env.get(SLIDER_1_ORIGIN)) {
                            events.add(PRODUCT_IN_DRILLING_EV);
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
                        logger.info("Transition 5: Slider motor returned to starting position");
                        setActuator(env, 2, false);
                        env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    },
                    4),
                /* Transition needed to change state from 4 to 5.
                 * In the future each state will have a environment method that will solve this problem,
                 * as the checks of values outside the state Machine and the throw of Events will be done
                 * by those environment methods.
                 */
                new Transition( // Transition 6
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_IN_DRILLING_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 6: Product ready for drilling, change state");
                        events.add(PRODUCT_READY_DRILLING_EV);
                    },
                    5),
                new Transition( // Transition 7
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_READY_DRILLING_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 7: Drill product");
                        for(int i = 0; i < (int)env.get(NDRILLING); i++) {
                            setActuator(env, 9, true);
                            try {
                                Thread.sleep((int)env.get(TIMEDRILLING)*1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            setActuator(env, 9, false);
                        }
                        env.put(PRODUCT_DONE_DRILLING, Boolean.TRUE);
                        events.add(PRODUCT_END_DRILLING_EV);
                    },
                    6),
                new Transition( // Transition 8
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_DRILLING_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 8: Leave drilling station");
                        // Drilling conveyor
                        setActuator(env, 8, true);
                        logger.debug("Product detected at end of conveyor belt, "
                                + "wait 2 seconds before stopping the conveyor");
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        // Stop drilling conveyor
                        setActuator(env, 8, false);
                        events.add(PRODUCT_END_CONVEYOR_EV);
                        // Start slider motor 2
                        setActuator(env, 3, true);
                        env.put(SLIDER_2_ORIGIN, Boolean.FALSE);
                    },
                    7),
                new Transition( // Transition 9
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_CONVEYOR_EV,
                                    PRODUCT_IN_SLIDER_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 9: Wait for product to leave slider motor 2");
                        if (getSensor(env, 3)) {
                            logger.debug("Product detected at end of slider motor, start output conveyor"
                                    + "and push slider backwards");
                            // Stop forward movement of slider motor 2
                            setActuator(env, 3, false);
                            // Start output conveyor
                            setActuator(env, 10, true);
                            // Start backwards movement of slider motor 2
                            setActuator(env, 4, true);
                            events.add(PRODUCT_END_SLIDER_EV);
                        }
                        else {
                            events.add(PRODUCT_IN_SLIDER_EV);
                        }
                    },
                    8),
                new Transition( // Transition 10
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_SLIDER_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 10: Wait for product to arrive "
                                + "to the end of factory");
                        if (!(Boolean)env.get(PRODUCT_AT_OUTPUT) && !getSensor(env, 9)) {
                            logger.debug("Product detected at output location");
                            setActuator(env, 10, false);
                            env.put(PRODUCT_AT_OUTPUT, Boolean.TRUE);
                        }
                        if (!(Boolean)env.get(SLIDER_2_ORIGIN) && getSensor(env, 4)) {
                            logger.debug("Motor slider 2 detected at starting position");
                            setActuator(env, 4, false);
                            env.put(SLIDER_2_ORIGIN, Boolean.TRUE);
                        }
                        if ((Boolean)env.get(PRODUCT_AT_OUTPUT) && (Boolean)env.get(SLIDER_2_ORIGIN)) {
                            events.add(PRODUCT_DONE_EV);
                            return;
                        }
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    },
                    9),
                new Transition( // Transition 11
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_DONE_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 11: Send success results and stop factory");
                        // Set the results to be passed to the caller of State Machine in the environment
                        env.put(STATE_MACHINE_RESULT, SUCCESS);
                        logger.debug("At the end State Machine = " + env.get(STATE_MACHINE_RESULT));
                        storeResultsAndStopFactory(env);
                    },
                    10),
                new Transition( // Transition 12
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(INIT_FAIL_EV,
                                    WORK_ERROR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 12: Send error results and stop factory");
                        env.put(STATE_MACHINE_RESULT, ERROR);
                        logger.debug("At the end State Machine = " + env.get(STATE_MACHINE_RESULT));
                        logger.debug(env.get(ERROR_MESSAGE));
                        if(env.containsKey(SERVICES_TO_ADDRESS)) {
                            storeResultsAndStopFactory(env);
                        }
                    },
                    11)
                )
            );
        
        Workflow millingWorkflow = new Workflow(workflowName, workflowConfig, workflowMachine);
        
        return millingWorkflow;
    }
    
    public Workflow millingAndDrilling() {
        String workflowName = WORKFLOW_NAME_MILL_AND_DRILL;
        
        Map<String, List<String>> workflowConfig = new HashMap<>(Map.ofEntries(
                Map.entry(NMILLING, new ArrayList<>(List.of("Integer")) ),
                Map.entry(TIMEMILLING, new ArrayList<>(List.of("Integer")) ),
                Map.entry(NDRILLING, new ArrayList<>(List.of("Integer")) ),
                Map.entry(TIMEDRILLING, new ArrayList<>(List.of("Integer")))));
        
        StateMachine workflowMachine = new StateMachine(
            Arrays.asList(
                    new State("Find factory services and input config", 0),
                    new State("Waiting for product to enter factory", 1,2,13),
                    new State("Product going through input conveyor", 2,3),
                    new State("Product pushed by slider motor 1", 3,4),
                    new State("Product going through milling conveyor", 4,5,6),
                    new State("Product at milling station", 7), // State 5
                    new State("Product leave milling and going to drilling", 7,8),
                    new State("Product at drilling station", 9),
                    new State("Product leave drilling station", 10),
                    new State("Product pushed by slider motor 2", 10,11),
                    new State("Product going through output conveyor", 11,12), // State 10
                    new State("Product finished, send successful results"),
                    new State("End workflow with errors")
                    ),
            Arrays.asList(
                new Transition( // Transition 0
                    null,
                    null,
                    (env, events) -> {
                        logger.info("Transition 0: Examine State Machine input configuration"
                                + " and services");
                        
                        // Check the input configuration
                        if(!validateMillingConfig(env, events, 1, 1)) return;
                        if(!validateDrillingConfig(env, events, 1, 1)) return;
                        
                        /* Include the logic creating the OK event after all the test have been a success,
                         * or clear the events at every test that fail to overwrite OK event
                         */
                        events.add(findServices(env,events) ? SERVICES_OK_EV : INIT_FAIL_EV);
                        if(events.contains(INIT_FAIL_EV)) {
                            env.put(ERROR_MESSAGE, "Services needed for Drilling not available in workstation");
                            return;
                        }
                        
                        //Check that slider motors are at starting position
                        if(getSensor(env, 2)) {
                            env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        }else {
                            events.clear();
                            events.add(INIT_FAIL_EV);
                            env.put(ERROR_MESSAGE, SLIDER_1_ORIGIN + " = FALSE ");
                            return;
                        }
                        if(getSensor(env, 4)) {
                            env.put(SLIDER_2_ORIGIN, Boolean.TRUE);
                        }else {
                            events.clear();
                            events.add(INIT_FAIL_EV);
                            env.put(ERROR_MESSAGE, SLIDER_2_ORIGIN + " = FALSE ");
                            return;
                        }
                        
                        // Setup the environment
                        env.put(PRODUCT_READY_MILLING, Boolean.FALSE);
                        env.put(PRODUCT_DONE_MILLING, Boolean.FALSE);
                        env.put(PRODUCT_READY_DRILLING, Boolean.FALSE);
                        env.put(PRODUCT_DONE_DRILLING, Boolean.FALSE);
                        env.put(PRODUCT_AT_OUTPUT, Boolean.FALSE);
                        
                    },
                    1),
                new Transition( // Transition 1
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(SERVICES_OK_EV,
                                    WAITING_PRODUCT_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 1: Read input factory sensor and start feeding conveyor");
                        if (!getSensor(env, 7)) {
                            logger.debug("Product detected, starting conveyor belt");
                            events.add(PRODUCT_READY_EV);
                            setActuator(env, 5, true);
                        }
                        else {
                            events.add(WAITING_PRODUCT_EV);
                        }
                    },
                    1),
                new Transition( // Transition 2
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_READY_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 2: Read sensor end of feeding conveyor "
                                + "and stop conveyor");
                        if (!getSensor(env, 5)) {
                            logger.debug("Product detected at end of conveyor belt, "
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
                    2),
                new Transition( // Transition 3
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_CONVEYOR_EV,
                                    PRODUCT_IN_SLIDER_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 3: Read limit switch of slider motor 1 and back motor");
                        if (getSensor(env, 1)) {
                            logger.debug("Product detected at end of slider motor, star milling conveyor"
                                    + "and push slider backwards");
                            events.add(PRODUCT_END_SLIDER_EV);
                            // Stop forward movement of slider motor 1
                            setActuator(env, 1, false);
                            // Start milling conveyor
                            setActuator(env, 6, true);
                            // Start backwards movement of slider motor 1
                            setActuator(env, 2, true);
                        }
                        else {
                            events.add(PRODUCT_IN_SLIDER_EV);
                        }
                    },
                    3),
                new Transition( // Transition 4
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_SLIDER_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 4: Read milling station sensor to stop milling conveyor"
                                + " and read starting position limit switch of slider motor 1");
                        if (!getSensor(env, 6) && !(Boolean)env.get(PRODUCT_READY_MILLING)) {
                            logger.debug("Product detected at milling station");
                            setActuator(env, 6, false);
                            env.put(PRODUCT_READY_MILLING, Boolean.TRUE);
                        }
                        if (!(Boolean)env.get(SLIDER_1_ORIGIN) && getSensor(env, 2)) {
                            logger.debug("Motor slider 1 detected at starting position");
                            events.add(MOTOR_SLIDER_ORIGIN_EV);
                            return;
                        }
                        if ((Boolean)env.get(PRODUCT_READY_MILLING) && (Boolean)env.get(SLIDER_1_ORIGIN)) {
                            events.add(PRODUCT_IN_MILLING_EV);
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
                        logger.info("Transition 5: Stop slider motor 1 at starting position");
                        setActuator(env, 2, false);
                        env.put(SLIDER_1_ORIGIN, Boolean.TRUE);
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    },
                    4),
                new Transition( // Transition 6
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_IN_MILLING_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 6: Mill product");
                        for(int i = 0; i < (int)env.get(NMILLING); i++) {
                            setActuator(env, 7, true);
                            try {
                                Thread.sleep((int)env.get(TIMEMILLING)*1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            setActuator(env, 7, false);
                        }
                        env.put(PRODUCT_DONE_MILLING, Boolean.TRUE);
                        events.add(PRODUCT_END_MILLING_EV);
                        // Milling conveyor
                        setActuator(env, 6, true);
                        // Drilling conveyor
                        setActuator(env, 8, true);
                    },
                    5),
                new Transition( // Transition 7
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_MILLING_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 7: Wait product arrives at drilling station");
                        if (!getSensor(env, 8)){
                            // Drilling conveyor
                            setActuator(env, 8, false);
                            // Milling conveyor
                            setActuator(env, 6, false);
                            events.add(PRODUCT_READY_DRILLING_EV);
                        }else {
                            events.add(PRODUCT_IN_CONVEYOR_EV);
                        }
                        
                    },
                    6),
                new Transition( // Transition 8
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_READY_DRILLING_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 8: Drill product");
                        for(int i = 0; i < (int)env.get(NDRILLING); i++) {
                            setActuator(env, 9, true);
                            try {
                                Thread.sleep((int)env.get(TIMEDRILLING)*1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            setActuator(env, 9, false);
                        }
                        env.put(PRODUCT_DONE_DRILLING, Boolean.TRUE);
                        events.add(PRODUCT_END_DRILLING_EV);
                    },
                    7),
                new Transition( // Transition 9
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_END_DRILLING_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 9: Move product out of drilling station");
                        // Drilling conveyor
                        setActuator(env, 8, true);
                        logger.debug("Product leaving drilling conveyor belt, "
                                + "wait 2 seconds before stopping the conveyor");
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        // Stop drilling conveyor
                        setActuator(env, 8, false);
                        events.add(PRODUCT_END_CONVEYOR_EV);
                        // Start slider motor 2
                        env.put(SLIDER_2_ORIGIN, Boolean.FALSE);
                        setActuator(env, 3, true);
                        
                    },
                    8),
                new Transition( // Transition 10
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_CONVEYOR_EV,
                                    PRODUCT_IN_SLIDER_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 10: Read limit switch of slider motor 2 and back motor"
                                + " while activating output conveyor");
                        if (getSensor(env, 3)) {
                            logger.debug("Product detected at end of slider motor 2, "
                                    + "start output conveyor and push slider 2 backwards");
                            // Stop forward movement of slider motor 2
                            setActuator(env, 3, false);
                            // Start output conveyor
                            setActuator(env, 10, true);
                            // Start backwards movement of slider motor 2
                            setActuator(env, 4, true);
                            events.add(PRODUCT_END_SLIDER_EV);
                        }
                        else {
                            events.add(PRODUCT_IN_SLIDER_EV);
                        }
                    },
                    9),
                new Transition( // Transition 11
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(PRODUCT_END_SLIDER_EV,
                                    PRODUCT_IN_CONVEYOR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 11: Read sensor at the end of output conveyor");
                        if (!(Boolean)env.get(PRODUCT_AT_OUTPUT) && !getSensor(env, 9)) {
                            logger.debug("Product detected at output location");
                            setActuator(env, 10, false);
                            env.put(PRODUCT_AT_OUTPUT, Boolean.TRUE);
                        }
                        if (!(Boolean)env.get(SLIDER_2_ORIGIN) && getSensor(env, 4)) {
                            logger.debug("Motor slider 2 detected at starting position");
                            setActuator(env, 4, false);
                            env.put(SLIDER_2_ORIGIN, Boolean.TRUE);
                        }
                        if ((Boolean)env.get(PRODUCT_AT_OUTPUT) && (Boolean)env.get(SLIDER_2_ORIGIN)) {
                            events.add(PRODUCT_DONE_EV);
                            return;
                        }
                        events.add(PRODUCT_IN_CONVEYOR_EV);
                    },
                    10),
                new Transition( // Transition 12
                    new LogicExpression<Event,Set<Event>>(
                            null,
                            List.of(PRODUCT_DONE_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 12: Send successful results and stop factory");
                        // Set the results to be passed to the caller of State Machine in the environment
                        env.put(STATE_MACHINE_RESULT, SUCCESS);
                        logger.debug("At the end State Machine = " + env.get(STATE_MACHINE_RESULT));
                        storeResultsAndStopFactory(env);
                    },
                    11),
                new Transition( // Transition 13
                    new LogicExpression<Event,Set<Event>>(
                            LogicOperator.OR,
                            List.of(INIT_FAIL_EV,
                                    WORK_ERROR_EV)),
                    null,
                    (env, events) -> {
                        logger.info("Transition 13: Send error results and stop factory");
                        env.put(STATE_MACHINE_RESULT, ERROR);
                        logger.debug("At the end State Machine = " + env.get(STATE_MACHINE_RESULT));
                        logger.debug(env.get(ERROR_MESSAGE));
                        if(env.containsKey(SERVICES_TO_ADDRESS)) {
                            storeResultsAndStopFactory(env);
                        }
                    },
                    12)
                )
            );
        
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
     * Test that the input configuration for a milling operations is correct
     * 
     * @param env  The environment will hold the correct value of the configuration or the
     * error message
     * @param events  The events of the State Machine that will serve to communicate the result of
     * this call
     * @param defaultNumberMilling  The default value to be used if request did not contain 
     * configuration information
     * @param defaultTimeMilling  The default value to be used if request did not contain 
     * configuration information
     * 
     * @return  True if everything ended successfully, false otherwise
     */
    private Boolean validateMillingConfig(Map<String, Object> env, Set<Event> events, 
            int defaultNumberMilling, int defaultTimeMilling) {
        if(!env.containsKey(NMILLING)) {
            logger.info("Input configuration for number of milling operations is missing.\n"
                    + "Setting default configuration to " + defaultNumberMilling 
                    + " number of millings");
            env.put(NMILLING, defaultNumberMilling);
        }else {
            try {
                // Transform List<String> from WorkflowDTO into integer
                var rawConfig = List.copyOf((Collection<? extends String>) env.get(NMILLING));
                if (rawConfig.size()>1) {
                    events.add(INIT_FAIL_EV);
                    env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                            + "number of milling should contain only one integer");
                    return false;
                }
                var configNMilling = Integer.parseUnsignedInt((String) rawConfig.get(0));
                env.put(NMILLING, configNMilling);
            } catch (NumberFormatException e) {
                events.add(INIT_FAIL_EV);
                env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                        + "number of milling can not be parsed to an integer");
                return false;
            }
        }
        if(!env.containsKey(TIMEMILLING)) {
            logger.info("Input configuration for time of milling operations is missing.\n"
                    + "Setting default configuration to " + defaultNumberMilling 
                    + " second for each milling");
            env.put(TIMEMILLING, defaultTimeMilling);
        }else {
            try {
                var rawConfig = List.copyOf((Collection<? extends String>) env.get(TIMEMILLING));
                if (rawConfig.size()>1) {
                    events.add(INIT_FAIL_EV);
                    env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                            + "time of milling should contain only one integer");
                    return false;
                }
                var configTimeMilling = Integer.parseUnsignedInt((String) rawConfig.get(0));
                env.put(TIMEMILLING, configTimeMilling);
            } catch (NumberFormatException e) {
                events.add(INIT_FAIL_EV);
                env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                        + "time of milling can not be parsed to an integer");
                return false;
            }
        }
        return true;
    }
    
    /**
     * Test that the input configuration for a drilling operations is correct
     * 
     * @param env  The environment will hold the correct value of the configuration or the
     * error message
     * @param events  The events of the State Machine that will serve to communicate the result of
     * this call
     * @param defaultNumberDrilling  The default value to be used if request did not contain 
     * configuration information
     * @param defaultTimeDrilling  The default value to be used if request did not contain 
     * configuration information
     * 
     * @return  True if everything ended successfully, false otherwise
     */
    private Boolean validateDrillingConfig(Map<String, Object> env, Set<Event> events,
            int defaultNumberDrilling, int defaultTimeDrilling) {
        if(!env.containsKey(NDRILLING)) {
            logger.info("Input configuration for number of drilling operations is missing.\n"
                    + "Setting default configuration to " + defaultNumberDrilling 
                    + " number of drillings");
            env.put(NDRILLING, defaultNumberDrilling);
        }else {
            try {
                var rawConfig = List.copyOf((Collection<? extends String>) env.get(NDRILLING));
                if (rawConfig.size()>1) {
                    events.add(INIT_FAIL_EV);
                    env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                            + "number of drilling should contain only one integer");
                    return false;
                }
                var configNDrilling = Integer.parseUnsignedInt((String) rawConfig.get(0));
                env.put(NDRILLING, configNDrilling);
            } catch (NumberFormatException e) {
                events.add(INIT_FAIL_EV);
                env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                        + "number of drilling can not be parsed to an integer ");
            }
        }
        if(!env.containsKey(TIMEDRILLING)) {
            logger.info("Input configuration for time of drilling operations is missing.\n"
                    + "Setting default configuration to " + defaultTimeDrilling 
                    + " second for each drilling");
            env.put(TIMEDRILLING, defaultTimeDrilling);
        }else {
            try {
                var rawConfig = List.copyOf((Collection<? extends String>) env.get(TIMEDRILLING));
                if (rawConfig.size()>1) {
                    events.add(INIT_FAIL_EV);
                    env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                            + "time of drilling should contain only one integer");
                    return false;
                }
                var configTimeDrilling = Integer.parseUnsignedInt((String) rawConfig.get(0));
                env.put(TIMEDRILLING, configTimeDrilling);
            } catch (NumberFormatException e) {
                events.add(INIT_FAIL_EV);
                env.put(ERROR_MESSAGE, "Input configuration with wrong format: "
                        + "time of drilling can not be parsed to an integer ");
            }
        }
        return true;
    }
    
    /**
     * Gets the value of the sensor requested, always in the range 1-9
     * 
     * @param sensorNumber The sensor number, has to be inside the range 1-9
     * @return The boolean value of the sensor
     * @throws IllegalArgumentException if the input number is out of the sensors id range
     */
    @SuppressWarnings("unchecked")
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
    @SuppressWarnings("unchecked")
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
    @SuppressWarnings("unchecked")
    private Boolean setActuator(Map<String, Object> env, int actuatorNumber, Boolean value)
            throws IllegalArgumentException{
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
    
    private void storeResultsAndStopFactory (Map<String, Object> env) {
        
        
        
        // The results will be send by the Workflow Executor System at State Machine end
//        try {
//            var wManagerService = orchestrate(WMANAGER_RESULT_SERVICE_DEFINITION);
//            if (env.get(STATE_MACHINE_RESULT).equals(SUCCESS)) {
//                
//            }
//        }
//        catch (ArrowheadException e) {
//            logger.error("Workflow Manager was not found in local cloud,"
//                    + "WManager system needed to send results of workflow");
//        }
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
        return sslProperties.isSslEnabled()
                ? WExecutorConstants.INTERFACE_SECURE
                : WExecutorConstants.INTERFACE_INSECURE;
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
