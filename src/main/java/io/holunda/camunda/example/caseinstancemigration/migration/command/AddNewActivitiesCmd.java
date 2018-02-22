package io.holunda.camunda.example.caseinstancemigration.migration.command;

import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.impl.cmmn.entity.repository.CaseDefinitionEntity;
import org.camunda.bpm.engine.impl.cmmn.entity.runtime.CaseExecutionEntity;
import org.camunda.bpm.engine.impl.cmmn.execution.CmmnExecution;
import org.camunda.bpm.engine.impl.cmmn.model.CmmnActivity;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import java.util.List;
import java.util.stream.Collectors;

public class AddNewActivitiesCmd implements Command<Void> {

    private final String caseDefinitionKey;

    private final CaseService caseService;

    private final RepositoryService repositoryService;

    private static final Logger LOGGER = LoggerFactory.getLogger(AddNewActivitiesCmd.class);

    public AddNewActivitiesCmd(final String caseDefinitionKey, final ApplicationContext ctx) {
        Assert.notNull(caseDefinitionKey, "caseDefinitionKey is missing");
        Assert.notNull(ctx, "ApplicationContext is missing");
        this.caseDefinitionKey = caseDefinitionKey;

        repositoryService = ctx.getBean(RepositoryService.class);
        caseService = ctx.getBean(CaseService.class);
    }

    @Override
    public Void execute(CommandContext commandContext) {
        caseService.createCaseInstanceQuery().caseDefinitionKey(caseDefinitionKey).list().forEach(this::addNewActivitiesToCaseInstance);

        return null;
    }

    private void addNewActivitiesToCaseInstance(final CaseInstance caseInstance) {
        Assert.notNull(caseInstance, "caseInstance is missing");

        List<String> activityIds = ((CaseExecutionEntity) caseInstance).getCaseExecutions().stream().map(CaseExecutionEntity::getActivityId).collect(Collectors.toList());

        CaseDefinitionEntity caseDefinition = (CaseDefinitionEntity) repositoryService.getCaseDefinition(caseInstance.getCaseDefinitionId());

        List<CmmnActivity> newActivities = caseDefinition.getActivities().get(0).getActivities().stream().filter(a -> !activityIds.contains(a.getId())).collect(Collectors.toList());

        newActivities.forEach(a -> LOGGER.info(String.format("Adding activity '%s' to case instance '%s'", a.getId(), caseInstance.getCaseInstanceId())));

        List<CmmnExecution> childExecutions = ((CaseExecutionEntity) caseInstance).createChildExecutions(newActivities);
        ((CaseExecutionEntity) caseInstance).triggerChildExecutionsLifecycle(childExecutions);
    }
}
