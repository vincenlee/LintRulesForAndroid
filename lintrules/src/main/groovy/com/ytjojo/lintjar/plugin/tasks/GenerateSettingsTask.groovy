package com.ytjojo.lintjar.plugin.tasks

import com.android.build.gradle.api.BaseVariant
import com.ytjojo.lintjar.plugin.PluginUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.yaml.snakeyaml.Yaml;

public class GenerateSettingsTask extends DefaultTask {
    @Input
    String packageName

    @Input
    def flavorName

    @Input
    def buildTypeName

    @Input
    def variantDirName

    @InputFiles
    def settingsFiles() {
        ['default', flavorName, buildTypeName].collect { [it, "${it}_secret"] }.flatten().collect {
            project.file(Util.pathJoin('config', "${it}.yml"))
        }
    }

    @OutputDirectory
    File outputDir() {
        project.file("${project.buildDir}/generated/source/settings/${variantDirName}")
    }

    @OutputFile
    File outputFile() {
        project.file("${outputDir().absolutePath}/${packageName.replace('.', '/')}/Settings.java")
    }

    @TaskAction
    def taskAction() {
        def yaml = new Yaml()
        def settingMaps = settingsFiles().collect { loadConfig(yaml, it) }

        def settings = PluginUtils.deepMerge(*settingMaps)
        if (!settings.isEmpty()) {
            def source = generateSource()
            def outputFile = outputFile()
            if (!outputFile.isFile()) {
                outputFile.delete()
                outputFile.parentFile.mkdirs()
            }

            outputFile.text = "package ${packageName};\n" + source
        }
    }

    private String generateSource(){
        return null
    }

    static public Map loadConfig(Yaml yaml, File f) {
        f.isFile() ? f.withReader { yaml.load(it) as Map } : [:]
    }

    public static void applyTask(Project project){
        def processVariant = { extension, BaseVariant variant ->
            def task = project.tasks.create(
                    name: "generate${variant.name.capitalize()}Settings",
                    type: GenerateSettingsTask) {
                packageName    variant.generateBuildConfig.buildConfigPackageName
                flavorName     variant.flavorName
                buildTypeName  variant.buildType.name
                variantDirName variant.dirName
            }

            variant.registerJavaGeneratingTask(task, task.outputDir())
            extension.sourceSets[variant.name].java.srcDirs += [task.outputDir()]
        }

        project.plugins.withId('com.android.application') {
            def app = project.extensions.findByType(AppExtension)
            if (app != null) {
                app.applicationVariants.all(processVariant.curry(app))
            }
        }
        project.plugins.withId('com.android.library') {
            def library = project.extensions.findByType(LibraryExtension)
            if (library != null) {
                library.libraryVariants.all(processVariant.curry(library))
            }
        }
    }
}