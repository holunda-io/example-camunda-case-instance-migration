package io.holunda.camunda.example.caseinstancemigration.migration.migrator.steps;

import io.holunda.camunda.example.caseinstancemigration.migration.domain.CamundaCaseExecution;
import org.springframework.stereotype.Component;

@Component
public class ExampleCaseExecutionMigrationStep implements CaseExecutionMigrationStep {

  @Override
  public CamundaCaseExecution migrate(CamundaCaseExecution camundaCaseExecution) {
    // perform some migration here...
    return camundaCaseExecution;
  }
}
