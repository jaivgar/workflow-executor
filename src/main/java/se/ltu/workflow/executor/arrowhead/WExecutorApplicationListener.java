package se.ltu.workflow.executor.arrowhead;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import eu.arrowhead.client.library.ArrowheadService;
import eu.arrowhead.client.library.config.ApplicationInitListener;
import eu.arrowhead.client.library.util.ClientCommonConstants;

import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.core.CoreSystem;
import eu.arrowhead.common.dto.shared.ServiceRegistryRequestDTO;
import eu.arrowhead.common.dto.shared.ServiceRegistryResponseDTO;
import eu.arrowhead.common.dto.shared.ServiceSecurityType;
import eu.arrowhead.common.dto.shared.SystemRequestDTO;
import eu.arrowhead.common.exception.ArrowheadException;

import se.ltu.workflow.executor.WExecutorConstants;
import se.ltu.workflow.executor.arrowhead.security.ProviderSecurityConfig;

@Component
public class WExecutorApplicationListener extends ApplicationInitListener{
    
    //=================================================================================================
    // members
    
    @Autowired
    private ArrowheadService arrowheadService;
    
    @Autowired
    private ProviderSecurityConfig providerSecurityConfig;
    
    @Value(ClientCommonConstants.$TOKEN_SECURITY_FILTER_ENABLED_WD)
    private boolean tokenSecurityFilterEnabled;
    
    @Value(CommonConstants.$SERVER_SSL_ENABLED_WD)
    private boolean sslEnabled;
    
    @Value(ClientCommonConstants.$CLIENT_SYSTEM_NAME)
    private String mySystemName;
    
    @Value(ClientCommonConstants.$CLIENT_SERVER_ADDRESS_WD)
    private String mySystemAddress;
    
    @Value(ClientCommonConstants.$CLIENT_SERVER_PORT_WD)
    private int mySystemPort;
    
    /*@Autowired
    ServletWebServerApplicationContext server;
    */
    
    private final Logger logger = LogManager.getLogger(WExecutorApplicationListener.class);
    
    //=================================================================================================
    // methods

    //-------------------------------------------------------------------------------------------------
    @Override
    protected void customInit(final ContextRefreshedEvent event) {

        /* Testing the server
         * logger.info("The Web Server was initialized at port:" + 
         * server.getWebServer().getPort());
         */
        
        // Checking the availability of Arrowhead Core systems:
        // Service Registry needed to register the services provided by this system
        if (!waitCoreSystemReady(CoreSystem.SERVICE_REGISTRY, 10)) {
            throw new RuntimeException("Service Registry not available in network");
        }

        if (sslEnabled) {
            // Authorization needed to check authorization rules and obtain tokens
            if (!waitCoreSystemReady(CoreSystem.AUTHORIZATION, 10)) {
                throw new RuntimeException("Service Registry not available in network");
            }

            //Initialize Arrowhead Context
            arrowheadService.updateCoreServiceURIs(CoreSystem.AUTHORIZATION);
            
            if (tokenSecurityFilterEnabled) {
                setTokenSecurityFilter();
                logger.info("TokenSecurityFilter activated");
            }
            else {
                logger.info("TokenSecurityFilter will not be actived");
            }
        }
        
        // Orchestrator needed to find the services provided by other systems
        if (!waitCoreSystemReady(CoreSystem.ORCHESTRATOR, 10)) {
            throw new RuntimeException("Service Registry not available in network");
        }
        
        /* Initialize Arrowhead Context, needed before orchestration can proceed (In this 
         * system or in any State Machine executed by the Workflow Executor
         */
        arrowheadService.updateCoreServiceURIs(CoreSystem.ORCHESTRATOR);

        // Register Workflow Executor services into ServiceRegistry
        // This service provides the workflows types/templates that are stored in the executor
        final ServiceRegistryRequestDTO provideWorkflowTypesServiceRequest = createServiceRegistryRequest(
                WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION, 
                WExecutorConstants.WEXECUTOR_URI + WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_URI, 
                HttpMethod.GET,
                null);
        
        ServiceRegistryResponseDTO serviceRegistrationResponse = arrowheadService.
                forceRegisterServiceToServiceRegistry(provideWorkflowTypesServiceRequest);
        validateRegistration(serviceRegistrationResponse);
        
        // This service provides the workflows in execution or commnaded for execution
        final ServiceRegistryRequestDTO provideWorkflowExecutingServiceRequest = createServiceRegistryRequest(
                WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION, 
                WExecutorConstants.WEXECUTOR_URI + WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_URI, 
                HttpMethod.GET,
                null);
        
        ServiceRegistryResponseDTO SRResponseWorkflowExecuting = arrowheadService.
                forceRegisterServiceToServiceRegistry(provideWorkflowExecutingServiceRequest);
        validateRegistration(SRResponseWorkflowExecuting);
        
        // This service allows a consumer to command the execution of the workflows available
        final ServiceRegistryRequestDTO executeWorkflowServiceRequest = createServiceRegistryRequest(
                WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION, 
                WExecutorConstants.WEXECUTOR_URI + WExecutorConstants.EXECUTE_WORKFLOW_URI, 
                HttpMethod.POST,
                Map.of(WExecutorConstants.REQUEST_OBJECT_KEY_WORKFLOW, WExecutorConstants.REQUEST_OBJECT_WORKFLOW));
        
        ServiceRegistryResponseDTO SRResponseExecuteWorkflow = arrowheadService.
                forceRegisterServiceToServiceRegistry(executeWorkflowServiceRequest);
        validateRegistration(SRResponseExecuteWorkflow);
        
        // The initial workflows to be preloaded in this system are written in the InitialWorkflows
        // class, that will be injected in the WExecutorService which contains the business logic
        
        // From now on until shutdown, all actions are taken in the WExecutorController class

    }
    
    //-------------------------------------------------------------------------------------------------
    @Override
    public void customDestroy() {
        //Unregister services
        arrowheadService.unregisterServiceFromServiceRegistry(WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
        logger.info("Unregistering Service: " + WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
        
        arrowheadService.unregisterServiceFromServiceRegistry(WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
        logger.info("Unregistering Service: " + WExecutorConstants.PROVIDE_IN_EXECUTION_WORKFLOW_SERVICE_DEFINITION);
        
        arrowheadService.unregisterServiceFromServiceRegistry(WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION);
        logger.info("Unregistering Service: " + WExecutorConstants.EXECUTE_WORKFLOW_SERVICE_DEFINITION);
    }

    //=================================================================================================
    // assistant methods

    //-------------------------------------------------------------------------------------------------
    private void setTokenSecurityFilter() {
        final PublicKey authorizationPublicKey = arrowheadService.queryAuthorizationPublicKey();
        if (authorizationPublicKey == null) {
            throw new ArrowheadException("Authorization public key is null");
        }
        
        KeyStore keystore;
        try {
            keystore = KeyStore.getInstance(sslProperties.getKeyStoreType());
            keystore.load(sslProperties.getKeyStore().getInputStream(), sslProperties.getKeyStorePassword().toCharArray());
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException ex) {
            throw new ArrowheadException(ex.getMessage());
        }           
        final PrivateKey providerPrivateKey = Utilities.getPrivateKey(keystore, sslProperties.getKeyPassword());
        
        providerSecurityConfig.getTokenSecurityFilter().setAuthorizationPublicKey(authorizationPublicKey);
        providerSecurityConfig.getTokenSecurityFilter().setMyPrivateKey(providerPrivateKey);
    }
    
    //-------------------------------------------------------------------------------------------------
    private ServiceRegistryRequestDTO createServiceRegistryRequest(final String serviceDefinition, 
            final String serviceUri, final HttpMethod httpMethod, Map<String,String> metadata) {
        
        final ServiceRegistryRequestDTO serviceRegistryRequest = new ServiceRegistryRequestDTO();
        serviceRegistryRequest.setServiceDefinition(serviceDefinition);
        
        // Add system information to request
        final SystemRequestDTO systemRequest = new SystemRequestDTO();
        systemRequest.setSystemName(mySystemName);
        systemRequest.setAddress(mySystemAddress);
        systemRequest.setPort(mySystemPort);        

        // Add security information to request
        if (tokenSecurityFilterEnabled) {
            systemRequest.setAuthenticationInfo(Base64.getEncoder().encodeToString(arrowheadService.getMyPublicKey().getEncoded()));
            serviceRegistryRequest.setSecure(ServiceSecurityType.TOKEN.name());
            serviceRegistryRequest.setInterfaces(List.of(WExecutorConstants.INTERFACE_SECURE));
        } else if (sslEnabled) {
            systemRequest.setAuthenticationInfo(Base64.getEncoder().encodeToString(arrowheadService.getMyPublicKey().getEncoded()));
            serviceRegistryRequest.setSecure(ServiceSecurityType.CERTIFICATE.name());
            serviceRegistryRequest.setInterfaces(List.of(WExecutorConstants.INTERFACE_SECURE));
        } else {
            serviceRegistryRequest.setSecure(ServiceSecurityType.NOT_SECURE.name());
            serviceRegistryRequest.setInterfaces(List.of(WExecutorConstants.INTERFACE_INSECURE));
        }
        
        serviceRegistryRequest.setProviderSystem(systemRequest);
        serviceRegistryRequest.setServiceUri(serviceUri);
        
        // Add metadata to the Request
        serviceRegistryRequest.setMetadata(new HashMap<>());
        serviceRegistryRequest.getMetadata().put(WExecutorConstants.HTTP_METHOD, httpMethod.name());
        if(metadata != null) {
            for (Map.Entry<String,String> entries : metadata.entrySet()) {
                serviceRegistryRequest.getMetadata().put(entries.getKey(),entries.getValue());
            }
        }
        
        return serviceRegistryRequest;
    }
    
    //-------------------------------------------------------------------------------------------------
    private void validateRegistration (ServiceRegistryResponseDTO response) {
        //TODO: validate the fields corresponding to this system
        if(Objects.isNull(response)){
            throw new ArrowheadException("Response from Service Registry is empty");
        }
        
        logger.info("System: " + response.getProvider().getSystemName() +" registering Service: " + response.getServiceDefinition().getServiceDefinition());
    }
    
    //-------------------------------------------------------------------------------------------------
    /**
     * Calls repeatedly the Arrowhead Core System to check if it is deployed
     * and available in the network, and waits for {@code minutes} until it 
     * answers before giving up and returning.
     * 
     * @param coreSystem  The Arrowhead Core Systems to check its availability
     * @param minutes  The total number of minutes that the method will keep checking
     *                  the availability of the system
     * @return  True if the system answer with the ready status, false otherwise
     */
    private Boolean waitCoreSystemReady(final CoreSystem coreSystem, int minutes) {
        int nCalls = 0;
        Boolean system = arrowheadService.echoCoreSystem(coreSystem);
        while (!system) {
            logger.info("Waiting for '{}' to be available ...", coreSystem.name());
            try {
                // Waits for 15 seconds before trying again
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            system = arrowheadService.echoCoreSystem(coreSystem);
            nCalls ++;
            if (nCalls/4 >= minutes) {
                logger.info("'{}' core system was NOT reachable.", coreSystem.name());
                return false;
            }
        }
        logger.info("'{}' core system is reachable.", coreSystem.name());
        return true;
    }
    
}
