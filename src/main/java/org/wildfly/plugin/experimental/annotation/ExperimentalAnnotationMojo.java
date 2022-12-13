package org.wildfly.plugin.experimental.annotation;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;
import org.jboss.jandex.Result;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Goal which touches a timestamp file.
 *
 * @goal touch
 * 
 * @phase process-sources
 */
@Mojo(name="index-experimental-annotations", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ExperimentalAnnotationMojo
    extends AbstractMojo
{

    @Parameter(property = "filters", required = true)
    List<Filter> filters = new ArrayList<>();

    @Parameter(property = "outputFile", required = true)
    File outputFile;

    @Component
    MavenProject mavenProject;

    Set<String> foundClasses = new HashSet<>();

    public void execute() throws MojoExecutionException {
        try {
            Log log = getLog();
            log.info("Running plugin");

            log.info(filters.toString());

            List<Dependency> dependencies = mavenProject.getDependencies();

            Set<String> allGroupIds = new HashSet<>();
            for (Filter filter : filters) {
                allGroupIds.addAll(filter.getGroupIds());
            }

            for (Artifact artifact : mavenProject.getArtifacts()) {
                // log.info(artifact.getGroupId() + ":" + artifact.getArtifactId());
                if (artifact.getType().equals("jar") && allGroupIds.contains(artifact.getGroupId())) {
                    searchExperimentalAnnotation(artifact);
                }
            }

            Path path = Paths.get(outputFile.toURI());
            Files.createDirectories(path.getParent());
            Files.write(path, foundClasses);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void searchExperimentalAnnotation(Artifact artifact) throws IOException {
        for (Filter filter : filters) {
            if (filter.getGroupIds().contains(artifact.getGroupId())) {
                searchExperimentalAnnotation(artifact, filter);
            }
        }
    }

    private void searchExperimentalAnnotation(Artifact artifact, Filter filter) throws IOException {
        Indexer indexer = new Indexer();
        getLog().info("Indexing " + artifact.getFile());
        Result result = JarIndexer.createJarIndex(artifact.getFile(), indexer, false, true, false);
        Index index = result.getIndex();

        Collection<AnnotationInstance> annotations = index.getAnnotations(filter.getAnnotation());

        for (AnnotationInstance annotation : annotations) {
            if (annotation.target().kind() == AnnotationTarget.Kind.CLASS) {
                ClassInfo classInfo = annotation.target().asClass();
                if (!filter.getExcludedClasses().contains(classInfo.name().toString())) {
                    boolean isAnn = classInfo.isAnnotation();
                    if (isAnn) {
                        boolean hasAnn = annotation.target().hasDeclaredAnnotation(filter.getAnnotation());
                        System.out.println(classInfo.name().toString());
                        foundClasses.add(classInfo.name().toString());
                    }
                }
            }
        }
    }
}
