package se.ltu.workflow.executor;

/**
 * Stores the constants need across the whole Workflow Executor system
 *
 */
public class WExecutorConstants {

    //=================================================================================================
    // members
    
    public static final String BASE_PACKAGE = "se.ltu.workflow.executor";
    
    public static final String INTERFACE_SECURE = "HTTPS-SECURE-JSON";
    public static final String INTERFACE_INSECURE = "HTTP-INSECURE-JSON";
    public static final String HTTP_METHOD = "http-method";
    
    public static final String WEXECUTOR_URI = "/workflow-executor";
    
    public static final String START_WORKFLOW_SERVICE_DEFINITION = "start-workflows";
    public static final String START_WORKFLOW_URI = "/start/{workflow}";
    public static final String REQUEST_PARAM_KEY_WORKFLOW = "request-param-workflow";
    public static final String REQUEST_PARAM_WORKFLOW = "workflow";
    
    
    public static final String PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION = "provide-workflows";
    public static final String PROVIDE_AVAILABLE_WORKFLOW_URI = "/workflows";
    
    
    /**
     * Do not create an instance of a class used to hold constants
     */
    private WExecutorConstants() {
        throw new UnsupportedOperationException();
    }

}
