package io.holunda.camunda.example.caseinstancemigration.migration.command;

import io.holunda.camunda.example.caseinstancemigration.migration.migrator.CaseInstanceMigrator;
import jersey.repackaged.com.google.common.collect.ImmutableList;
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.repository.CaseDefinition;
import org.camunda.bpm.engine.repository.ResourceDefinition;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MigrateCaseInstanceVersionCmd implements Command<Void> {

    private String caseDefinitionKey;

    private CaseInstanceMigrator migrator;

    private RepositoryService repositoryService;

    private CaseService caseService;

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrateCaseInstanceVersionCmd.class);

    public MigrateCaseInstanceVersionCmd(final String caseDefinitionKey, final ApplicationContext ctx) {
        Assert.notNull(caseDefinitionKey, "caseDefinitionKey is missing");
        Assert.notNull(ctx, "ApplicationContext is missing");
        this.caseDefinitionKey = caseDefinitionKey;

        migrator = ctx.getBean(CaseInstanceMigrator.class);
        repositoryService = ctx.getBean(RepositoryService.class);
        caseService = ctx.getBean(CaseService.class);
    }

    @Override
    public Void execute(CommandContext commandContext) {

        migrateCasesToLatestVersion(caseDefinitionKey);

        return null;
    }

    private void migrateCasesToLatestVersion(final String caseDefinitionKey) {
        Assert.notNull(caseDefinitionKey, "caseDefinitionKey is missing");

        if (LOGGER.isInfoEnabled())
            LOGGER.info("Starting to migrate case instances to latest version");

        final String latestCaseDefId = getLatestCaseDefinitionId(caseDefinitionKey);

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Latest case definition id is '%s'", latestCaseDefId));

        migrateAllCaseInstances(latestCaseDefId, caseDefinitionKey);

        LOGGER.info("Migration completed");
    }

    private void migrateAllCaseInstances(final String targetCaseDefId, final String caseDefinitionKey) {
        Assert.notNull(targetCaseDefId, "targetCaseDefId is missing");
        Assert.notNull(caseDefinitionKey, "caseDefinitionId is missing");

        final List<String> caseDefinitionIds = ImmutableList.copyOf(getAllButTargetCaseDefinitionIds(caseDefinitionKey, targetCaseDefId));

        final List<CaseInstance> caseInstancesToMigrate = new ArrayList<>();

        caseDefinitionIds.forEach(caseDefinitionId -> caseInstancesToMigrate.addAll(caseService.createCaseInstanceQuery().caseDefinitionId(caseDefinitionId).list()));

        if (LOGGER.isInfoEnabled())
            LOGGER.info(String.format("Found %d case instances to migrate", caseInstancesToMigrate.size()));

        caseInstancesToMigrate.forEach(caseInstance -> {
            try {
                migrator.migrateOneCaseInstance(caseInstance.getCaseInstanceId(), targetCaseDefId);
            } catch (Exception e) {
                LOGGER.error(String.format("Exception during migration of case instance '%s", caseInstance.getCaseInstanceId()), e);
            }
        });
    }

    private String getLatestCaseDefinitionId(String caseDefinitionKey) {
        Assert.notNull(caseDefinitionKey, "caseDefinitionKey is missing");

        final CaseDefinition caseDefinition = repositoryService.createCaseDefinitionQuery().caseDefinitionKey(caseDefinitionKey).latestVersion().singleResult();

        if (caseDefinition == null) {
            throw new IllegalStateException("Could not determine latest case model version");
        }

        return caseDefinition.getId();
    }

    private List<String> getAllButTargetCaseDefinitionIds(final String caseDefinitionKey, final String targetCaseDefinitionId) {
        Assert.notNull(caseDefinitionKey, "caseDefinitionKey is missing");
        Assert.notNull(targetCaseDefinitionId, "targetCaseDefinitionId is missing");

        final List<CaseDefinition> caseDefinitions = ImmutableList.copyOf(repositoryService.createCaseDefinitionQuery().caseDefinitionKey(caseDefinitionKey).list());

        return ImmutableList.copyOf(caseDefinitions.stream().filter(def -> !def.getId().equals(targetCaseDefinitionId)).map(ResourceDefinition::getId).collect(Collectors.toList()));
    }
}
