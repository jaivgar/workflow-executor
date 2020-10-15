package se.ltu.workflow.executor.demo;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FactoryDeviceDTO {

    private String id;
    private String definition;
    private String value;

    /**
     * Creates a DTO object used to communicate with any of the services of the Fischer
     * factory.
     * 
     * @param id the ID of the factory device, it can be a sensor or an actuator
     * @param definition  a text description for humans to have more information about the device
     * @param value  a boolean value representing the status of the device
     * 
     * @throws IllegalArgumentException if the value is not a boolean: either "true" or "false"
     */
    public FactoryDeviceDTO(String id, String definition, String value) {
        
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("The input value has to match the boolean "
                    + "values of \"true\" or \"false\"");
        }
        
        this.id = Objects.requireNonNull(id, "Expected factory device id");
        definition = Objects.requireNonNull(definition, "Expected factory device definition");
        value = Objects.requireNonNull(value, "Expected factory device boolean value");
    }
    
    /**
     * Creates an empty object. 
     * <p>
     * To be used only as a reference to infer the class, not as a proper Object.
     */
    private FactoryDeviceDTO() {
        this.id = "-1";
        this.definition = "Not a true DTO, just a class reference";
    }
    
    /**
     * Creates an empty FactoryDeviceDTO object. 
     * <p>
     * To be used only as a reference to infer the class, not as a proper Object to test its values.
     */
    public static FactoryDeviceDTO classReference() {
        return new FactoryDeviceDTO();
    }

    public String getId() {
        return id;
    }

    public String getDefinition() {
        return definition;
    }

    public String getValue() {
        return value;
    }


    
    // From repository arrowhead-f/core-java-spring, pull request:Implement toString methods in DTOs #259
    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (final JsonProcessingException ex) {
            return "toString failure";
        }
    }
}
