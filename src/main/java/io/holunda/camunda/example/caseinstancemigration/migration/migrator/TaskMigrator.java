package io.holunda.camunda.example.caseinstancemigration.migration.migrator;

import io.holunda.camunda.example.caseinstancemigration.migration.domain.CamundaTask;
import io.holunda.camunda.example.caseinstancemigration.migration.domain.CamundaTaskRepository;
import jersey.repackaged.com.google.common.collect.ImmutableList;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.event.HistoryEventTypes;
import org.camunda.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.camunda.bpm.engine.impl.history.producer.DefaultHistoryEventProducer;
import org.camunda.bpm.engine.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

@Component
public class TaskMigrator {

    private final CamundaTaskRepository camundaTaskRepository;

    private final TaskService taskService;

    private final ProcessEngine processEngine;

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskMigrator.class);

    public TaskMigrator(CamundaTaskRepository camundaTaskRepository,
                        TaskService taskService,
                        ProcessEngine processEngine) {
        this.taskService = taskService;
        this.camundaTaskRepository = camundaTaskRepository;
        this.processEngine = processEngine;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void migrateAllTasksForCaseInstance(final String caseInstanceId, final String targetCaseDefId) {
        Assert.notNull(caseInstanceId, "caseInstanceId is missing");
        Assert.notNull(targetCaseDefId, "targetCaseDefId is missing");

        final List<CamundaTask> tasksToMigrate = ImmutableList.copyOf(camundaTaskRepository.findByCaseInstanceId(caseInstanceId));

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Found %d tasks to migrate for case instance '%s'", tasksToMigrate.size(), caseInstanceId));

        tasksToMigrate.forEach(t -> migrateOneTask(t, targetCaseDefId));
    }

    private void migrateOneTask(CamundaTask task, final String targetCaseDefId) {
        Assert.notNull(task, "task is missing");
        Assert.notNull(targetCaseDefId, "targetCaseDefId is missing");

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Migrating task '%s'", task.getId()));

        task.setCaseDefinitionId(targetCaseDefId);

        camundaTaskRepository.save(task);

        produceTaskHistoryEvent(task.getId());
    }

    private void produceTaskHistoryEvent(final String taskId) {
        Assert.notNull(taskId, "taskId is missing");

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Creating history event for task '%s'", taskId));

        final TaskEntity task = (TaskEntity) taskService.createTaskQuery().taskId(taskId).singleResult();

        if (task == null) {
            throw new IllegalStateException(String.format("Task '%s' not found", taskId));
        }

        if (getHistoryLevel().isHistoryEventProduced(HistoryEventTypes.CASE_INSTANCE_UPDATE, null)) {
            final HistoryEvent event = new DefaultHistoryEventProducer().createTaskInstanceMigrateEvt(task);
            getHistoryEventHandler().handleEvent(event);
        }
    }


    private HistoryEventHandler getHistoryEventHandler() {
        return ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getHistoryEventHandler();
    }

    private HistoryLevel getHistoryLevel() {
        return ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getHistoryLevel();
    }
}
