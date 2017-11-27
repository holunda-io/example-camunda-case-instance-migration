package io.holunda.camunda.example.caseinstancemigration.migration.domain;

import javax.persistence.*;

@Entity
@Table(name = "ACT_RU_CASE_EXECUTION")
public final class CamundaCaseExecution {

    @Id
    @Column(name = "ID_")
    private String id;

    @Version
    @Column(name = "REV_")
    private Integer revision;

    @Column(name = "CASE_INST_ID_")
    private String caseInstanceId;

    @Column(name = "CASE_DEF_ID_")
    private String caseDefinitionId;

    private CamundaCaseExecution() {
    } //NOSONAR

    public String getId() {
        return id;
    }

    public void setCaseDefinitionId(String caseDefinitionId) {
        this.caseDefinitionId = caseDefinitionId;
    }
}
