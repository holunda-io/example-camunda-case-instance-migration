package io.holunda.camunda.example.caseinstancemigration.migration.migrator;

import com.google.common.collect.ImmutableList;
import io.holunda.camunda.example.caseinstancemigration.migration.domain.CamundaCaseExecution;
import io.holunda.camunda.example.caseinstancemigration.migration.domain.CamundaCaseExecutionRepository;
import io.holunda.camunda.example.caseinstancemigration.migration.migrator.steps.CaseExecutionMigrationStep;
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionEntity;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.event.HistoryEventTypes;
import org.camunda.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.camunda.bpm.engine.impl.history.producer.DefaultCmmnHistoryEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CaseInstanceMigrator {

    private final CamundaCaseExecutionRepository camundaCaseExecutionRepository;

    private final ProcessEngine processEngine;

    private final CaseService caseService;

    private final TaskMigrator taskMigrator;

    private final List<CaseExecutionMigrationStep> migrationSteps;

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseInstanceMigrator.class);

    public CaseInstanceMigrator(CamundaCaseExecutionRepository camundaCaseExecutionRepository,
                                ProcessEngine processEngine,
                                CaseService caseService,
                                TaskMigrator taskMigrator,
                                List<CaseExecutionMigrationStep> migrationSteps) {
        this.camundaCaseExecutionRepository = camundaCaseExecutionRepository;
        this.processEngine = processEngine;
        this.caseService = caseService;
        this.taskMigrator = taskMigrator;
        this.migrationSteps = migrationSteps;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void migrateOneCaseInstance(final String caseInstanceId, final String targetCaseDefId) {
        Assert.notNull(caseInstanceId, "caseInstanceId it missing");
        Assert.notNull(targetCaseDefId, "targetCaseDefId is missing");

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Migrating case instance '%s'", caseInstanceId));

        final List<CamundaCaseExecution> executionsToMigrate = ImmutableList.copyOf(camundaCaseExecutionRepository.findByCaseInstanceId(caseInstanceId));

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Found %d executions for case instance '%s'", executionsToMigrate.size(), caseInstanceId));

        executionsToMigrate.forEach(execution -> migrateOneExecution(execution, targetCaseDefId));

        produceCaseInstanceHistoryEventsForOneCaseInstance(caseInstanceId);

        taskMigrator.migrateAllTasksForCaseInstance(caseInstanceId, targetCaseDefId);
    }

    private void migrateOneExecution(CamundaCaseExecution execution, final String targetCaseDefId) {
        Assert.notNull(execution, "execution is missing");
        Assert.notNull(targetCaseDefId, "targetCaseDefId is missing");

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Migrating execution '%s'", execution.getId()));

        execution.setCaseDefinitionId(targetCaseDefId);

        final AtomicReference<CamundaCaseExecution> executionRef = new AtomicReference<>(execution);

        migrationSteps.forEach(step -> executionRef.set(step.migrate(executionRef.get())));

        camundaCaseExecutionRepository.save(executionRef.get());
    }

    private void produceCaseInstanceHistoryEventsForOneCaseInstance(final String caseInstanceId) {
        Assert.notNull(caseInstanceId, "caseInstanceId is missing");

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Creating history event for case instance '%s'", caseInstanceId));

        final CaseExecutionEntity caseInstance = (CaseExecutionEntity) caseService.createCaseInstanceQuery().caseInstanceId(caseInstanceId).singleResult();

        if (caseInstance == null) {
            throw new IllegalStateException(String.format("Case instance '%s' not found", caseInstanceId));
        }

        if (getHistoryLevel().isHistoryEventProduced(HistoryEventTypes.CASE_INSTANCE_UPDATE, null)) {
            final HistoryEvent event = new DefaultCmmnHistoryEventProducer().createCaseInstanceUpdateEvt(caseInstance);
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
