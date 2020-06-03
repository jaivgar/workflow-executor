package se.ltu.workflow.executor.service;

/**
 * Status of activation of workflow.
 * <p>
 * Workflows can start in either of these tree states:
 * <p><ul>
 * <li>{@code IDLE}: If the Workflow Executor is busy executing a similar or 
 * incompatible workflow, it states the workflow as idle.
 * <p>
 * <li>{@code SCHEDULE}: If the workflow is targeted to start after a specific
 * delay, the Workflow Executor will add the schedule state to that workflow.
 * <p>
 * <li>{@code ACTIVE}: If the Workflow Executor is not busy, and can start
 * executing the workflow right away, it will state the workflow as active.
 * </ul><p>
 * 
 * At any point in time there can be only one {@code ACTIVE} workflow, that will
 * be executed by the Workflow Executor. There can be any number of IDLE, SCHEDULE
 * and DONE workflows in storage.
 * <p>
 * After the ACTIVE state the workflow will continue to the DONE state, meaning it has 
 * been executed as is now ended.
 * <p>
 * Future releases may enable multiple ACTIVE workflows.
 *
 */
public enum WStatus {
    IDLE,
    SCHEDULE,
    ACTIVE,
    DONE;
}
