package io.holunda.camunda.example.caseinstancemigration.migration.migrator.steps;

import io.holunda.camunda.example.caseinstancemigration.migration.domain.CamundaCaseExecution;

public interface CaseExecutionMigrationStep {

    CamundaCaseExecution migrate(final CamundaCaseExecution camundaCaseExecution);
}
