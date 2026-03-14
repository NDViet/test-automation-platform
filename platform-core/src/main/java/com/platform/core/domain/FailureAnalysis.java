package com.platform.core.domain;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "failure_analyses",
       indexes = {
           @Index(name = "idx_fa_test_id",     columnList = "test_id"),
           @Index(name = "idx_fa_project_id",  columnList = "project_id"),
           @Index(name = "idx_fa_category",    columnList = "category"),
           @Index(name = "idx_fa_analysed_at", columnList = "analysed_at")
       })
@EntityListeners(AuditingEntityListener.class)
public class FailureAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "test_id", nullable = false, length = 500)
    private String testId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** The specific TestCaseResult that triggered this analysis. */
    @Column(name = "test_case_result_id")
    private UUID testCaseResultId;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "root_cause", length = 500)
    private String rootCause;

    @Column(name = "detailed_analysis", columnDefinition = "TEXT")
    private String detailedAnalysis;

    @Column(name = "suggested_fix", columnDefinition = "TEXT")
    private String suggestedFix;

    @Column(name = "is_flaky_candidate", nullable = false)
    private boolean flakyCandidate;

    @Column(name = "affected_component", length = 200)
    private String affectedComponent;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @CreatedDate
    @Column(name = "analysed_at", nullable = false, updatable = false)
    private Instant analysedAt;

    protected FailureAnalysis() {}

    private FailureAnalysis(Builder b) {
        this.testId           = b.testId;
        this.projectId        = b.projectId;
        this.testCaseResultId = b.testCaseResultId;
        this.category         = b.category;
        this.confidence       = b.confidence;
        this.rootCause        = b.rootCause;
        this.detailedAnalysis = b.detailedAnalysis;
        this.suggestedFix     = b.suggestedFix;
        this.flakyCandidate   = b.flakyCandidate;
        this.affectedComponent = b.affectedComponent;
        this.modelVersion     = b.modelVersion;
        this.inputTokens      = b.inputTokens;
        this.outputTokens     = b.outputTokens;
    }

    public static Builder builder() { return new Builder(); }

    public UUID getId()                { return id; }
    public String getTestId()          { return testId; }
    public UUID getProjectId()         { return projectId; }
    public UUID getTestCaseResultId()  { return testCaseResultId; }
    public String getCategory()        { return category; }
    public double getConfidence()      { return confidence; }
    public String getRootCause()       { return rootCause; }
    public String getDetailedAnalysis(){ return detailedAnalysis; }
    public String getSuggestedFix()    { return suggestedFix; }
    public boolean isFlakyCandidate()  { return flakyCandidate; }
    public String getAffectedComponent(){ return affectedComponent; }
    public String getModelVersion()    { return modelVersion; }
    public int getInputTokens()        { return inputTokens; }
    public int getOutputTokens()       { return outputTokens; }
    public int getTotalTokens()        { return inputTokens + outputTokens; }
    public Instant getAnalysedAt()     { return analysedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FailureAnalysis f)) return false;
        return Objects.equals(id, f.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    public static class Builder {
        private String testId;
        private UUID projectId;
        private UUID testCaseResultId;
        private String category;
        private double confidence;
        private String rootCause;
        private String detailedAnalysis;
        private String suggestedFix;
        private boolean flakyCandidate;
        private String affectedComponent;
        private String modelVersion;
        private int inputTokens;
        private int outputTokens;

        public Builder testId(String v)            { this.testId = v; return this; }
        public Builder projectId(UUID v)           { this.projectId = v; return this; }
        public Builder testCaseResultId(UUID v)    { this.testCaseResultId = v; return this; }
        public Builder category(String v)          { this.category = v; return this; }
        public Builder confidence(double v)        { this.confidence = v; return this; }
        public Builder rootCause(String v)         { this.rootCause = v; return this; }
        public Builder detailedAnalysis(String v)  { this.detailedAnalysis = v; return this; }
        public Builder suggestedFix(String v)      { this.suggestedFix = v; return this; }
        public Builder flakyCandidate(boolean v)   { this.flakyCandidate = v; return this; }
        public Builder affectedComponent(String v) { this.affectedComponent = v; return this; }
        public Builder modelVersion(String v)      { this.modelVersion = v; return this; }
        public Builder inputTokens(int v)          { this.inputTokens = v; return this; }
        public Builder outputTokens(int v)         { this.outputTokens = v; return this; }
        public FailureAnalysis build()             { return new FailureAnalysis(this); }
    }
}
