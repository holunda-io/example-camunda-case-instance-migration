package io.holunda.camunda.example.caseinstancemigration.migration.migrator;

import io.holunda.camunda.example.caseinstancemigration.Application;
import io.holunda.camunda.example.caseinstancemigration.migration.command.MigrateCaseInstanceVersionCmd;
import io.holunda.camunda.example.caseinstancemigration.migration.command.AddNewActivitiesCmd;
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.repository.CaseDefinition;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.runtime.CaseInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.cmmn.Cmmn;
import org.camunda.bpm.model.cmmn.CmmnModelInstance;
import org.camunda.bpm.model.cmmn.instance.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("test")
public class CaseInstanceMigratorTest {

    @Autowired
    private CaseService caseService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ProcessEngine processEngine;

    @Autowired
    private ApplicationContext ctx;

    private static String CASE_KEY = "my_case_mock";

    private static String HUMAN_TASK_KEY = "my_case_mock_human_task";
    private static String HUMAN_TASK_2_KEY = "my_case_mock_human_task_2";

    private CaseDefinition oldCaseDefinition;

    private CaseDefinition newCaseDefinition;

    @Test
    public void shouldMigrateCaseInstancesWithTasksUsingCommand() {
        deployCaseMock();

        oldCaseDefinition = repositoryService.createCaseDefinitionQuery().caseDefinitionKey(CASE_KEY).latestVersion().singleResult();

        CaseInstance caseInstance = caseService.createCaseInstanceByKey(CASE_KEY);

        deployCaseMock();

        // We should now have a latest definition different from the old one
        newCaseDefinition = repositoryService.createCaseDefinitionQuery().caseDefinitionKey(CASE_KEY).latestVersion().singleResult();
        assertThat(newCaseDefinition.getId()).isNotEqualTo(oldCaseDefinition.getId());

        // Currently all case executions should have the OLD definition
        List<CaseExecution> caseExecutions = caseService.createCaseExecutionQuery().caseInstanceId(caseInstance.getCaseInstanceId()).list();
        assertThat(caseExecutions.stream().allMatch(e -> e.getCaseDefinitionId().equals(oldCaseDefinition.getId()))).isTrue();

        // Currently the task should have the OLD definition
        Task task = taskService.createTaskQuery().caseInstanceId(caseInstance.getCaseInstanceId()).taskDefinitionKey(HUMAN_TASK_KEY).singleResult();
        assertThat(task.getCaseDefinitionId()).isEqualTo(oldCaseDefinition.getId());

        // Perform migration
        ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getCommandExecutorTxRequired().execute(new MigrateCaseInstanceVersionCmd(CASE_KEY, ctx));

        assertThatExecutionsAreMigrated(caseInstance.getCaseInstanceId());
        assertThatTasksAreMigrated(caseInstance.getCaseInstanceId());
    }

    @Test
    public void shouldAddNewActivitiesDuringMigration() {
        deployCaseMock();

        oldCaseDefinition = repositoryService.createCaseDefinitionQuery().caseDefinitionKey(CASE_KEY).latestVersion().singleResult();

        CaseInstance caseInstance = caseService.createCaseInstanceByKey(CASE_KEY);

        assertThat(caseService.createCaseExecutionQuery().caseInstanceId(caseInstance.getCaseInstanceId()).count()).isEqualTo(2);

        deployCaseMockWithAdditionalActivity();

        // We should now have a latest definition different from the old one
        newCaseDefinition = repositoryService.createCaseDefinitionQuery().caseDefinitionKey(CASE_KEY).latestVersion().singleResult();
        assertThat(newCaseDefinition.getId()).isNotEqualTo(oldCaseDefinition.getId());

        // Perform migration
        ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getCommandExecutorTxRequired().execute(new MigrateCaseInstanceVersionCmd(CASE_KEY, ctx));

        assertThatExecutionsAreMigrated(caseInstance.getCaseInstanceId());
        assertThatTasksAreMigrated(caseInstance.getCaseInstanceId());

        ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getCommandExecutorTxRequired().execute(new AddNewActivitiesCmd(CASE_KEY, ctx));

        assertThat(caseService.createCaseExecutionQuery().caseInstanceId(caseInstance.getCaseInstanceId()).count()).isEqualTo(3);
    }

    private void assertThatExecutionsAreMigrated(String caseInstanceId) {
        List<CaseExecution> caseExecutions = caseService.createCaseExecutionQuery().caseInstanceId(caseInstanceId).list();

        // Now all case executions should have the NEW definition
        assertThat(caseExecutions.stream().allMatch(e -> e.getCaseDefinitionId().equals(newCaseDefinition.getId()))).isTrue();
    }

    private void assertThatTasksAreMigrated(String caseInstanceId) {
        Task task = taskService.createTaskQuery().caseInstanceId(caseInstanceId).taskDefinitionKey(HUMAN_TASK_KEY).singleResult();

        // Now the task should have the NEW definition
        assertThat(task.getCaseDefinitionId()).isEqualTo(newCaseDefinition.getId());
    }

    private void deployCaseMock() {
        CmmnModelInstance caseMock = Cmmn.createEmptyModel();

        Definitions definitions = caseMock.newInstance(Definitions.class);
        definitions.setTargetNamespace("http://camunda.org/examples");
        caseMock.setDefinitions(definitions);

        Case caseElement = caseMock.newInstance(Case.class);
        caseElement.setId(CASE_KEY);
        definitions.addChildElement(caseElement);

        CasePlanModel casePlanModel = caseMock.newInstance(CasePlanModel.class);
        casePlanModel.setId(CASE_KEY + "_case");
        caseElement.addChildElement(casePlanModel);

        HumanTask humanTask = caseMock.newInstance(HumanTask.class);
        humanTask.setId(HUMAN_TASK_KEY + "_task");
        casePlanModel.addChildElement(humanTask);

        PlanItem planItem = caseMock.newInstance(PlanItem.class);
        planItem.setId(HUMAN_TASK_KEY);
        casePlanModel.addChildElement(planItem);

        planItem.setDefinition(humanTask);
        Cmmn.validateModel(caseMock);
        repositoryService.createDeployment().addModelInstance("mock/" + "my_case_mock.cmmn", caseMock).deploy();
    }

    private void deployCaseMockWithAdditionalActivity() {
        CmmnModelInstance caseMock = Cmmn.createEmptyModel();

        Definitions definitions = caseMock.newInstance(Definitions.class);
        definitions.setTargetNamespace("http://camunda.org/examples");
        caseMock.setDefinitions(definitions);

        Case caseElement = caseMock.newInstance(Case.class);
        caseElement.setId(CASE_KEY);
        definitions.addChildElement(caseElement);

        CasePlanModel casePlanModel = caseMock.newInstance(CasePlanModel.class);
        casePlanModel.setId(CASE_KEY + "_case");
        caseElement.addChildElement(casePlanModel);

        HumanTask humanTask = caseMock.newInstance(HumanTask.class);
        humanTask.setId(HUMAN_TASK_KEY + "_task");
        casePlanModel.addChildElement(humanTask);

        PlanItem planItem = caseMock.newInstance(PlanItem.class);
        planItem.setId(HUMAN_TASK_KEY);
        casePlanModel.addChildElement(planItem);

        HumanTask humanTask2 = caseMock.newInstance(HumanTask.class);
        humanTask2.setId(HUMAN_TASK_2_KEY + "_task");
        casePlanModel.addChildElement(humanTask2);

        PlanItem planItem2 = caseMock.newInstance(PlanItem.class);
        planItem2.setId(HUMAN_TASK_2_KEY);
        casePlanModel.addChildElement(planItem2);

        planItem.setDefinition(humanTask);
        planItem2.setDefinition(humanTask2);
        Cmmn.validateModel(caseMock);
        repositoryService.createDeployment().addModelInstance("mock/" + "my_case_mock.cmmn", caseMock).deploy();
    }

}
