package com.jfrog;

import com.jfrog.tasks.GenerateDependencyTree;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import java.util.Set;

@SuppressWarnings("unused")
public class GradleDepsTree implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        for (Project proj : (Set<Project>) project.getAllprojects()) {
            proj.getTasks().maybeCreate(GenerateDependencyTree.TASK_NAME, GenerateDependencyTree.class);
        }
    }
}
