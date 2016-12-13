/*
 *
 *   Copyright (c) 2016 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version
 *   2.0 (the "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *   implied.  See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package io.fabric8.vertx.maven.plugin.utils;


import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * @author kameshs
 */
public class MojoUtils {

    /*===  Plugin Keys ====*/

    private static final String RESOURCES_PLUGIN_KEY = "org.apache.maven.plugins:maven-resources-plugin";

    /*===  Plugins ====*/

    private static final String G_MAVEN_RESOURCES_PLUGIN = "org.apache.maven.plugins";
    private static final String A_MAVEN_RESOURCES_PLUGIN = "maven-resources-plugin";
    private static final String V_MAVEN_RESOURCES_PLUGIN = "maven-resources-plugin-version";

    private static final String G_MAVEN_COMPILER_PLUGIN = "org.apache.maven.plugins";
    private static final String A_MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin";
    private static final String V_MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin-version";

    /*===  Goals ====*/
    private static final String GOAL_COMPILE = "compile";
    private static final String GOAL_RESOURCES = "resources";

    private final Properties properties = new Properties();

    public MojoUtils() {
        loadProperties();
    }

    /**
     * @param project
     * @param mavenSession
     * @param buildPluginManager
     * @throws MojoExecutionException
     */
    public void copyResources(MavenProject project, MavenSession mavenSession,
                              BuildPluginManager buildPluginManager) throws MojoExecutionException {

        Optional<Plugin> resourcesPlugin = hasPlugin(project, RESOURCES_PLUGIN_KEY);

        Xpp3Dom pluginConfig = configuration(element("outputDirectory", "${project.build.outputDirectory}"));

        if (resourcesPlugin.isPresent()) {

            Optional<Xpp3Dom> optConfiguration = buildConfiguration(project, A_MAVEN_RESOURCES_PLUGIN, GOAL_RESOURCES);

            if (optConfiguration.isPresent()) {
                pluginConfig = optConfiguration.get();
            }

            executeMojo(
                resourcesPlugin.get(),
                goal(GOAL_RESOURCES),
                pluginConfig,
                executionEnvironment(project, mavenSession, buildPluginManager)
            );

        } else {
            executeMojo(
                plugin(G_MAVEN_RESOURCES_PLUGIN, A_MAVEN_RESOURCES_PLUGIN,
                    properties.getProperty(V_MAVEN_RESOURCES_PLUGIN)),
                goal(GOAL_RESOURCES),
                pluginConfig,
                executionEnvironment(project, mavenSession, buildPluginManager)
            );
        }

    }

    /**
     * @param project
     * @param pluginKey
     * @return
     */
    public Optional<Plugin> hasPlugin(MavenProject project, String pluginKey) {
        Optional<Plugin> optPlugin = project.getBuildPlugins().stream()
            .filter(plugin -> pluginKey.equals(plugin.getKey()))
            .findFirst();

        if (!optPlugin.isPresent() && project.getPluginManagement() != null) {
            optPlugin = project.getPluginManagement().getPlugins().stream()
                .filter(plugin -> pluginKey.equals(plugin.getKey()))
                .findFirst();
        }
        return optPlugin;
    }

    /**
     * Checks whether the project has the dependency
     *
     * @param project    - the project to check existence of dependency
     * @param groupId    - the dependency groupId
     * @param artifactId - the dependency artifactId
     * @return true if the project has the dependency
     */
    public boolean hasDependency(MavenProject project, String groupId, String artifactId) {

        Optional<Dependency> dep = project.getDependencies().stream()
            .filter(d -> groupId.equals(d.getGroupId())
                && artifactId.equals(d.getArtifactId())).findFirst();

        return dep.isPresent();
    }

    /**
     * @param project
     * @param mavenSession
     * @param buildPluginManager
     * @throws Exception
     */
    public void compile(MavenProject project, MavenSession mavenSession,
                        BuildPluginManager buildPluginManager) throws Exception {

        Optional<Plugin> mvnCompilerPlugin = project.getBuildPlugins().stream()
            .filter(plugin -> A_MAVEN_COMPILER_PLUGIN.equals(plugin.getArtifactId()))
            .findFirst();

        String pluginVersion = properties.getProperty(V_MAVEN_COMPILER_PLUGIN);

        if (mvnCompilerPlugin.isPresent()) {
            pluginVersion = mvnCompilerPlugin.get().getVersion();
        }

        Optional<Xpp3Dom> optConfiguration = buildConfiguration(project, A_MAVEN_COMPILER_PLUGIN, GOAL_COMPILE);

        if (optConfiguration.isPresent()) {

            Xpp3Dom configuration = optConfiguration.get();

            executeMojo(
                plugin(G_MAVEN_COMPILER_PLUGIN, A_MAVEN_COMPILER_PLUGIN, pluginVersion),
                goal(GOAL_COMPILE),
                configuration,
                executionEnvironment(project, mavenSession, buildPluginManager)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> goals(Object goals) {
        if (goals instanceof List) {
            return (List<String>) goals;
        } else {
            return null;
        }
    }

    /**
     * @param project
     * @param artifactId
     * @param goal
     * @return
     */
    public Optional<Xpp3Dom> buildConfiguration(MavenProject project, String artifactId, String goal) {

        Optional<Plugin> pluginOptional = project.getBuildPlugins().stream()
            .filter(plugin -> artifactId
                .equals(plugin.getArtifactId())).findFirst();

        Plugin plugin;

        if (pluginOptional.isPresent()) {

            plugin = pluginOptional.get();

            //Goal Level Configuration
            List<String> goals = goals(plugin.getGoals());

            if (goals != null && goals.contains(goal)) {
                return Optional.ofNullable((Xpp3Dom) plugin.getConfiguration());
            }

            //Execution Configuration
            Optional<PluginExecution> executionOptional = plugin.getExecutions().stream()
                .filter(e -> e.getGoals().contains(goal)).findFirst();

            executionOptional
                .ifPresent(pluginExecution -> Optional.ofNullable((Xpp3Dom) pluginExecution.getConfiguration()));

        } else {
            return Optional.empty();
        }

        //Global Configuration
        return Optional.ofNullable((Xpp3Dom) plugin.getConfiguration());
    }

    private void loadProperties() {
        InputStream in = this.getClass().getResourceAsStream("/vertx-maven-plugin.properties");
        try {
            properties.load(in);
        } catch (IOException e) {
            //ignore it mostly this means its not packaged rightly
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                //ignore it mostly this means its not packaged rightly
            }
        }
    }

    public String getVersion(String artifactKey) {
        return properties.getProperty(artifactKey);
    }
}
