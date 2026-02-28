package com.brixo.slidehub.ai.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Análisis técnico de un repositorio GitHub, generado por Gemini y cacheado en MongoDB
 * (PLAN-EXPANSION.md Fase 3, tarea 30).
 *
 * Colección: {@code repo_analysis}
 * Reutilizado en la generación de notas (pipeline Fase 3) y en el tutor de despliegue
 * (Fase 5).
 */
@Document(collection = "repo_analysis")
public class RepoAnalysis {

    @Id
    private String id;

    @Indexed(unique = true)
    private String repoUrl;

    private LocalDateTime analyzedAt;

    /** Lenguaje principal detectado (Java, PHP, JavaScript, TypeScript, etc.). */
    private String language;

    /** Framework principal (Spring Boot, Laravel, Next.js, etc.). */
    private String framework;

    /** Lista de tecnologías/librerías detectadas en el repositorio. */
    private List<String> technologies;

    /** Sistema de build (Maven, Gradle, npm, Composer, etc.). */
    private String buildSystem;

    /** Resumen breve del propósito del proyecto. */
    private String summary;

    /** Explicación de la arquitectura del proyecto. */
    private String structure;

    /** Recomendaciones de despliegue en Render, Vercel, Railway, etc. */
    private String deploymentHints;

    /** Contenido de un Dockerfile candidato para este proyecto. */
    private String dockerfile;

    // ── Constructores ─────────────────────────────────────────────────────────

    public RepoAnalysis() {}

    // ── Getters y setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public List<String> getTechnologies() { return technologies; }
    public void setTechnologies(List<String> technologies) { this.technologies = technologies; }

    public String getBuildSystem() { return buildSystem; }
    public void setBuildSystem(String buildSystem) { this.buildSystem = buildSystem; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getStructure() { return structure; }
    public void setStructure(String structure) { this.structure = structure; }

    public String getDeploymentHints() { return deploymentHints; }
    public void setDeploymentHints(String deploymentHints) { this.deploymentHints = deploymentHints; }

    public String getDockerfile() { return dockerfile; }
    public void setDockerfile(String dockerfile) { this.dockerfile = dockerfile; }
}
