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
    
    private final Logger logger = LogManager.getLogger(WExecutorApplicationListener.class);
    
    //=================================================================================================
    // methods

    //-------------------------------------------------------------------------------------------------
    @Override
    protected void customInit(final ContextRefreshedEvent event) {

        //Checking the availability of necessary core systems
        checkCoreSystemReachability(CoreSystem.SERVICE_REGISTRY);
        if (sslEnabled) {
            checkCoreSystemReachability(CoreSystem.AUTHORIZATION);

            //Initialize Arrowhead Context
            arrowheadService.updateCoreServiceURIs(CoreSystem.AUTHORIZATION);
            
            if (tokenSecurityFilterEnabled) {
                setTokenSecurityFilter();
                logger.info("TokenSecurityFilter activated");
            }
            else {
                logger.info("TokenSecurityFilter in not active");
            }
        }

        // Register Workflow Executor services into ServiceRegistry
        // First service provides the workflow that the executor knows
        final ServiceRegistryRequestDTO provideWorkflowServiceRequest = createServiceRegistryRequest(
                WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION, 
                WExecutorConstants.WEXECUTOR_URI + WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_URI, 
                HttpMethod.POST,
                Map.of(WExecutorConstants.REQUEST_PARAM_KEY_WORKFLOW, WExecutorConstants.REQUEST_PARAM_WORKFLOW));
        
        ServiceRegistryResponseDTO serviceRegistrationResponse1 = arrowheadService.
                forceRegisterServiceToServiceRegistry(provideWorkflowServiceRequest);
        validateRegistration(serviceRegistrationResponse1);
        
        // Second service allows a consumer to command the execution of the workflow
        final ServiceRegistryRequestDTO startWorkflowServiceRequest = createServiceRegistryRequest(
                WExecutorConstants.START_WORKFLOW_SERVICE_DEFINITION, 
                WExecutorConstants.WEXECUTOR_URI + WExecutorConstants.START_WORKFLOW_URI, 
                HttpMethod.POST,
                Map.of(WExecutorConstants.REQUEST_PARAM_KEY_WORKFLOW, WExecutorConstants.REQUEST_PARAM_WORKFLOW));
        ServiceRegistryResponseDTO serviceRegistrationResponse2 = arrowheadService.
                forceRegisterServiceToServiceRegistry(startWorkflowServiceRequest);
        validateRegistration(serviceRegistrationResponse2);

    }
    
    //-------------------------------------------------------------------------------------------------
    @Override
    public void customDestroy() {
        //Unregister services
        arrowheadService.unregisterServiceFromServiceRegistry(WExecutorConstants.PROVIDE_AVAILABLE_WORKFLOW_SERVICE_DEFINITION);
        arrowheadService.unregisterServiceFromServiceRegistry(WExecutorConstants.START_WORKFLOW_SERVICE_DEFINITION);
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
        for (Map.Entry<String,String> entries : metadata.entrySet()) {
            serviceRegistryRequest.getMetadata().put(entries.getKey(),entries.getValue());
        }
        
        return serviceRegistryRequest;
    }
    
    //-------------------------------------------------------------------------------------------------
    private void validateRegistration (ServiceRegistryResponseDTO response) {
        //TODO: validate the fields corresponding to this system
        if(Objects.isNull(response)){
            throw new ArrowheadException("Response from Service Registry is empty");
        }
    }
    
}
