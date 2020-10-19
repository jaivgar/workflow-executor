package se.ltu.workflow.executor.arrowhead;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpMethod;

import eu.arrowhead.client.library.ArrowheadService;
import eu.arrowhead.client.library.util.ArrowheadBeans;
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

public class WExecutorUtils {
    
    /**
     * Utility field to use the Arrowhead Client Library functionality, like contacting the
     * Arrowhead Core systems, or requesting orchestration.
     * <p>
     * As this is included through a Spring bean, it can not be static and the methods that create
     * the different workflows can not be static either.
     */
    private static ArrowheadService arrowheadService = ArrowheadBeans.getArrowheadService();
    
    /**
     * Utility field to use the Arrowhead Client Library functionality, needed to obtain the
     * preferred interface of a system depending on the security level, indicated by this field.
     */
    protected static SSLProperties sslProperties = ArrowheadBeans.getSSLProperties();
    
    private static final Logger logger = LogManager.getLogger(WExecutorUtils.class);

 // General methods to consumer Arrowhead services
    //-------------------------------------------------------------------------------------------------
    public static OrchestrationResultDTO orchestrate(final String serviceDefinition) throws ArrowheadException{
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
    public static void validateOrchestrationResult(final OrchestrationResultDTO orchestrationResult, 
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
    public static String getInterface() {
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
     * @param <T>  Usually a DTO class that can be mapped to the response of the Http request
     * @param ResponseDTO  The type of DTO class used to receive the body of the response
     * @param orchestrationResult  The results of asking for orchestration in Arrowhead
     * @param method  The HTTP method used to consume the service
     * @param extraPath  Path that be added to the URI of the service retrieved from service registry, can be null
     * @param payload  The body of the request, can be null
     * @param metadata  The metadata that can be send with the request, can be null
     * 
     * @return  The response body of the Http request wrapped in the class specified by &ltT&gt
     */
    public static <T> T consumeService(final Class<T> ResponseDTOType, final OrchestrationResultDTO orchestrationResult,
            final HttpMethod method, String extraPath, final Object payload, final String[] metadata) {
        final String token = orchestrationResult.getAuthorizationTokens() == null ? null : orchestrationResult.getAuthorizationTokens().get(getInterface());

        if(extraPath == null) extraPath = "";
        
        return (T) arrowheadService.consumeServiceHTTP(
                ResponseDTOType, // The type of object that the Response will be matched to
                method,
                orchestrationResult.getProvider().getAddress(), 
                orchestrationResult.getProvider().getPort(), 
                orchestrationResult.getServiceUri() + extraPath,
                getInterface(), 
                token, 
                payload, 
                metadata);
    }

}
