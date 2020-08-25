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
    
    public static final String PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION = "provide-workflows-type";
    public static final String PROVIDE_AVAILABLE_WORKFLOW_URI = "/workflows";
    
    public static final String PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION = "provide-workflows-in-execution";
    public static final String PROVIDE_IN_EXECUTION_WORKFLOW_URI = "/workflows/execution";
    
    public static final String EXECUTE_WORKFLOW_SERVICE_DEFINITION = "execute-workflow";
    public static final String EXECUTE_WORKFLOW_URI = "/execute";
    public static final String REQUEST_OBJECT_KEY_WORKFLOW = "request-object";
    public static final String REQUEST_OBJECT_WORKFLOW = "workflow";
    
    

    
    
    /**
     * Do not create an instance of a class used to hold constants
     */
    private WExecutorConstants() {
        throw new UnsupportedOperationException();
    }

}
