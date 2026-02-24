package com.jfrog;

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

public class CurationUtils {

    static final String ARTIFACTORY_PATH = "/artifactory/";
    static final String CURATION_AUDIT_PATH = "/api/curation/audit";

    public static void updateRepositoryUrls(RepositoryHandler repositories) {
        repositories.all(repo -> {
            if (repo instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepo = (MavenArtifactRepository) repo;
                String currentUrl = mavenRepo.getUrl().toString();
                if (currentUrl.contains(ARTIFACTORY_PATH) && !currentUrl.contains(CURATION_AUDIT_PATH)) {
                    mavenRepo.setUrl(currentUrl.replace(ARTIFACTORY_PATH, ARTIFACTORY_PATH + CURATION_AUDIT_PATH.substring(1) + "/"));
                }
            }
        });
    }
}
