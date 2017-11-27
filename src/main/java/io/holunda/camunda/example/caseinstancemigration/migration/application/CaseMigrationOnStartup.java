package io.holunda.camunda.example.caseinstancemigration.migration.application;


import io.holunda.camunda.example.caseinstancemigration.migration.command.MigrateCaseInstanceVersionCmd;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.spring.boot.starter.event.PostDeployEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CaseMigrationOnStartup {

    @Value("${camunda.bpm.migration.case-instance-migration-on-startup:false}")
    private Boolean migrateOnStartup;

    private final ProcessEngine processEngine;

    private final ApplicationContext ctx;

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseMigrationOnStartup.class);

    public CaseMigrationOnStartup(@Value("${camunda.bpm.migration.case-instance-migration-on-startup:false}") Boolean migrateOnStartup,
                                  ProcessEngine processEngine,
                                  ApplicationContext ctx) {
        this.migrateOnStartup = migrateOnStartup;
        this.processEngine = processEngine;
        this.ctx = ctx;
    }

    @EventListener
    public void migrateOnStartup(PostDeployEvent event) {
        if (migrateOnStartup) {
            ((ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration()).getCommandExecutorTxRequired().execute(new MigrateCaseInstanceVersionCmd("myCaseDefinitionKey", ctx));
        } else if (LOGGER.isInfoEnabled()) {
            LOGGER.info("CaseInstance migration on application startup is disabled");
        }
    }
}
