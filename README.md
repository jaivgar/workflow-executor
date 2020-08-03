# Workflow Executor for Arrowhead Framework 4.1.3
##### The project contains the Workflow Executor core system which is commonly used in couple with the Workflow Manager system

The Workflow Executor is an Arrowhead system with the goal to automate Workflows, stored as State Machines inside this system. 

It will be commanded by the Workflow Manager, which in turn will interact with other systems to agree upon what workflows should be executed in that Arrowhead Local Cloud. Then the Workflow Executor will start the execution of one of its State Machines, that will consume the Services provides by other systems in that Local Cloud. Finally the Workflow Executor will return the results of the workflow, the outputs of the State Machine, to the Workflow Manager.


## Requirements

The project has the following dependencies:
* JRE/JDK 11 [Download from here](https://www.oracle.com/technetwork/java/javase/downloads/jdk11-downloads-5066655.html)
* Maven 3.5+ [Download from here](http://maven.apache.org/download.cgi) | [Install guide](https://www.baeldung.com/install-maven-on-windows-linux-mac)
* GitHub Packages [Configuring Maven for GitHub Packages](https://help.github.com/en/packages/using-github-packages-with-your-projects-ecosystem/configuring-apache-maven-for-use-with-github-packages)

If these dependencies are met, running the commands ```mvn install -DskipTests ``` should finish succesfully.

## Project Origins

This project is a merge of the code from [client-skeleton project](https://github.com/arrowhead-f/client-skeleton-java-spring), that provides the Arrowhead components for this sytem to be deployed inside an Arrowhead Local Cloud and contact with the Arrowhead Core systems, and the [statemachine-engine project](https://github.com/jaivgar/StateMachine-engine) that provides the base of the business logic run inside the system.

## Arrowhead

For more information about Arrowhead and its principles, you can visit the [homepage](https://arrowhead.eu).

For the Arrowhead Framework code, you can visit the [Arrowhead Consortia](https://github.com/arrowhead-f) in github, where you can find the [core systems repository](https://github.com/arrowhead-f/core-java-spring)
