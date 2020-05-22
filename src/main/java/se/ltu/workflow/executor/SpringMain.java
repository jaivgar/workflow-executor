package se.ltu.workflow.executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import eu.arrowhead.common.CommonConstants;

@SpringBootApplication
// Not sure yet if I would need the beans from eu.arrowhead
@ComponentScan(basePackages = {CommonConstants.BASE_PACKAGE, WExecutorConstants.BASE_PACKAGE})
public class SpringMain {

    public static void main(final String[] args) {
        /*
         * The code that runs at start up of the system and at shutdown, is located in
         * the class WExecutorApplicationListener
         */
        SpringApplication.run(SpringMain.class, args);
    }   
}
