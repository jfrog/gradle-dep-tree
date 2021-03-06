package com.jfrog;

import com.jfrog.tasks.GenerateDepTrees;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import java.util.Set;

@SuppressWarnings("unused")
public class GradleDepTree implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        for (Project proj : (Set<Project>) project.getAllprojects()) {
            proj.getTasks().maybeCreate(GenerateDepTrees.TASK_NAME, GenerateDepTrees.class);
        }
    }
}
