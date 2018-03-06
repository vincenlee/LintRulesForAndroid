package com.ytjojo.lintjar.plugin

import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.tasks.Lint
import com.android.builder.model.AndroidProject
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskState
import org.gradle.tooling.GradleConnector

import java.util.zip.ZipFile

class LintRulesPlugin implements Plugin<Project> {

    void apply(Project project) {
        def lintJarName
        def lintDir

        if (project.plugins.hasPlugin('com.android.library')) {
            lintJarName = "lint.jar"
            lintDir = "${project.buildDir.absolutePath}/intermediates/lint/"
        } else if (project.plugins.hasPlugin('com.android.application')) {
            lintJarName = null
            lintDir = "${System.getProperty("user.home")}/.android/lint"
            new File(lintDir).mkdirs()
        } else {
            throw new IllegalStateException("Can only be applied on android library projects.")
        }

        def lintTasks = project.getTasks().findAll { it.name.startsWith('lint') }


        def lintRules = project.configurations.create('lintRules')

        Project lintRulesProject
        def copyLintJarTask = project.tasks.create('createLintJar', Copy) {
            lintRules.getAllDependencies().all { Dependency dependency ->
                if (dependency instanceof ProjectDependency) {
                    lintRulesProject = dependency.getDependencyProject()
                    lintRulesProject.evaluate()

                    if (!lintRulesProject.plugins.hasPlugin('java')) {
                        throw new IllegalStateException("${lintRulesProject.name} must be a java project.")
                    }

                    from(lintRulesProject.files(lintRulesProject.tasks.getByName('jar').archivePath)) {
                        if (lintJarName != null) {
                            rename {
                                String fileName -> lintJarName
                            }
                        }
                    }
                    into lintDir
                } else {
                    throw new IllegalStateException("Only project dependencies are supported.")
                }
            }
        }

        def cleanup = project.tasks.create('cleanup').doLast {
            new File(new File(lintDir), lintRulesProject.tasks.getByName('jar').archiveName).delete()
        }

        project.afterEvaluate {
            DependencySet lintRulesDependencies = lintRules.getAllDependencies()
            if (lintRulesDependencies.size() == 0) {
                return
            } else if (lintRulesDependencies.size() > 1) {
                throw new IllegalStateException("Only one lint rules dependency is supported.")
            }

            def jarTask = lintRulesProject.tasks.getByName('jar')
            copyLintJarTask.dependsOn(jarTask)

            if (project.plugins.hasPlugin('com.android.application')) {
                project.tasks.findAll {
                    it.name.startsWith('lint')
                }.each {
                    it.dependsOn(copyLintJarTask)
                    it.finalizedBy(cleanup)
                }
            } else {
                def compileLintTask = project.tasks.getByName('compileLint')
                compileLintTask.dependsOn(copyLintJarTask)
            }
        }
    }

    private void lintTaskHook(Project project, Lint lintTask) {

        def newOptions = new LintOptions()
        newOptions.lintConfig = lintFile
        newOptions.warningsAsErrors = true
        newOptions.abortOnError = true
        newOptions.htmlReport = true
//不放在build下，防止被clean掉
        newOptions.htmlOutput = project.file("${project.projectDir}/lint-report/lint-report.html")
        newOptions.xmlReport = false

        lintTask.lintOptions = newOptions
        lintTask.doFirst {

            if (lintFile.exists()) {
                lintOldFile = project.file("lintOld.xml")
                lintFile.renameTo(lintOldFile)
            }
            def isLintXmlReady = copyLintXml(project, lintFile)

            if (!isLintXmlReady) {
                if (lintOldFile != null) {
                    lintOldFile.renameTo(lintFile)
                }
                throw new GradleException("lint.xml不存在")
            }

        }

        project.gradle.taskGraph.afterTask { task, TaskState state ->
            if (task == lintTask) {
                lintFile.delete()
                if (lintOldFile != null) {
                    lintOldFile.renameTo(lintFile)
                }
            }
        }
    }

    private String getGradleCachePath(Project project, String group, String name, String version) {


        String maventLocal = java.lang.System.getProperty("user.home") + "/.m2/repository"
        String maventLocal2 = project.repositories.mavenLocal().url.toString() - 'file:' + group.replace('.', '/') + '/' + name.replace('.', '/') + '/' + version // name  对应的就是依赖的 artifactId
        // name  对应的就是依赖的 artifactId
        def defaultGradleUserHome = new File(java.lang.System.getProperty("user.home") + "/.gradle")
        def gradleUserHome = System.getProperty('gradle.user.home')
        if (gradleUserHome == null) {
            gradleUserHome = System.getenv("GRADLE_USER_HOME")
            if (gradleUserHome == null) {
                gradleUserHome = defaultGradleUserHome.absolutePath
            }
        }
        "${gradleUserHome}/caches/modules-2/files-2.1/"
    }

    private void ss(Project project) {
        project.getConfigurations().getByName("compile").incoming.dependencies.all {
            if (it.group.equals("com.ytjojo.lintrules") && it.name.contains("lintrules")) {
                //过滤收集aar和jar
                FileCollection collection = configuration.fileCollection(it).filter {
                    return it.name.endsWith(".aar") || it.name.endsWith(".jar")
                }
                //遍历过滤后的文件
                collection.each {

                    if (it.name.endsWith(".aar")) {
                        //如果是aar，则提取里面的jar文件
                        FileCollection jarFormAar = project.zipTree(it).filter {
                            it.name == "classes.jar"
                        }
                        //将jar依赖添加到provided的scope中
                        project.dependencies.add("provided", jarFormAar)
                    } else if (it.name.endsWith(".jar")) {
                        //如果是jar则直接添加
                        //将jar依赖添加到provided的scope中
                        project.dependencies.add("provided", project.files(it))
                    }
                }
            }
        }


    }

    private void exlude(Project project,String group,List<String> modules) {
        project.configurations {
            project.getConfigurations().all {
                for (String item : modules) {
                    HashMap<String, String> excludeModules = new HashMap<>(modules.size());
                    excludeModules.put("module", item)
                    if (group != null && !group.isEmpty()) {
                        excludeModules.put("group", group)
                    }
                    it.exclude(excludeModules)
                }

            }
        }


    }

    private boolean isAssemble(Project project) {
        List<String> taskNames = project.gradle.getStartParameter().getTaskNames()
        for (String task : taskNames) {
            if (task.toUpperCase().contains("ASSEMBLE")
                    || task.contains("aR")
                    || task.toUpperCase().contains("INSTALL")
                    || task.toUpperCase().contains("RESGUARD")) {
                return true
            }
        }
        return false
    }

    private Project getMainProject(Project project) {
        Set<Project> allSubProjects;
        if (project.rootProject == null) {
            allSubProjects = project.subprojects;
        } else {
            allSubProjects = project.rootProject.allprojects;
        }
        allSubProjects.each {
            if (it.plugins.hasPlugin('com.android.application')) {
                return it;
            }
        }
        return null;
    }

    /**
     * 自动添加依赖，只在运行assemble任务的才会添加依赖，因此在开发期间组件之间是完全感知不到的，这是做到完全隔离的关键
     * 支持两种语法：module或者groupId:artifactId:version(@aar),前者之间引用module工程，后者使用maven中已经发布的aar
     * @param assembleTask
     * @param project
     */
    private void addCompileModules(Project project) {
        String components
        components = (String) project.properties.get("compileModules")

        if (components == null || components.length() == 0) {
            System.out.println("there is no add dependencies ")
            return
        }
        String[] compileComponents = components.split(",")
        if (compileComponents == null || compileComponents.length == 0) {
            System.out.println("there is no add dependencies ")
            return
        }
        for (String str : compileComponents) {
            System.out.println("compileModules is " + str)
            if (str.contains(":")) {
                /**
                 * 示例语法:groupId:artifactId:version(@aar)
                 * compileModules=com.luojilab.reader:readercomponent:1.0.0
                 * 注意，前提是已经将组件aar文件发布到maven上，并配置了相应的repositories
                 */
                project.dependencies.add("compile", str)
                System.out.println("add dependencies lib  : " + str)
            } else {
                /**
                 * 示例语法:module
                 * compileModules=readercomponent,sharecomponent
                 */
                project.dependencies.add("compile", project.project(':' + str))
                System.out.println("add dependencies project : " + str)
            }
        }
    }

    private void hookConfigrationToCopyLintJar(Project project) {
        project.rootProject.buildscript {
            Project rootProject = project.rootProject;
            Configuration classpathConfiguration = rootProject.getConfigurations().getByName("classpath")
            classpathConfiguration.incoming.afterResolve {
                it.dependencies.all {
                    println("afterResolve" + it.group + "   " + it.name)
                    FileCollection collection = classpathConfiguration.fileCollection(it).filter {
                        return it.name.endsWith(".aar") || it.name.endsWith(".jar")
                    }
                    //遍历过滤后的文件
                    collection.each {
                        println(it.absolutePath)
                        rootProject.subprojects { subProject ->
                            String lintDir = "${subProject.buildDir.absolutePath}/intermediates/lint/"
                            println(lintDir + "  " + it.absolutePath)
                            File lintJarFile =  new File(lintDir +"lint.jar")
                            if(lintJarFile.exists()){
                                lintJarFile.delete()
                            }
                            FileUtils.copyFile(it, lintJarFile)
                        }

                    }
                }
            }
            classpathConfiguration.dependencies.each {
                println(it.name + "    dependencies          " + it.group)
            }
        }
    }
    private void download(String repoDepUrl,File depFile) {
        if (!repoDepUrl.startsWith('file:')) {
            project.logger.info 'repoDepUrl : ' + repoDepUrl
            FileUtils.copyURLToFile(new URL(repoDepUrl), depFile)
        }
    }







    static void getJarFromAar(Project project, File aar) {
        def aarName = aar.name
        def zipFile = new ZipFile(aar)
        try {
            def entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                def element = entries.nextElement()
                project.logger.info element.name
                if (element.name == 'classes.jar') {
                    InputStream inputStream = zipFile.getInputStream(element)
                    def file = new File(aar.parent, aarName - '.aar' + '.jar')
                    project.logger.info file.absolutePath
                    OutputStream outputStream = new FileOutputStream(file)
                    IOUtils.copy(inputStream, outputStream)
                    outputStream.close()
                    inputStream.close()
                }
            }
        } catch (Exception e) {
            project.logger.error e.toString()
        } finally {
            zipFile.close()
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    String getAndroidGradlePluginVersionCompat() {
        String version = null
        try {
            Class versionModel = Class.forName("com.android.builder.model.Version")
            def versionFiled = versionModel.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            versionFiled.setAccessible(true)
            version = versionFiled.get(null)
        } catch (Exception e) {
            version = "unknown"
        }
        return version
    }

    /**
     * 导出是否在jenkins环境中,build.gradle中apply后可直接使用isJenkins()
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    boolean isJenkins() {
        Map<String, String> environmentMap = System.getenv()
        boolean result = false
        if (environmentMap != null && environmentMap.containsKey("JOB_NAME") && environmentMap.containsKey("BUILD_NUMBER")) {
            result = true
        }
        return result
    }

    private AndroidProject getAndroidProject(Project project) {
        GradleConnector gradleConn = GradleConnector.newConnector()
        gradleConn.forProjectDirectory(project.getProjectDir())
        AndroidProject modelProject = gradleConn.connect().getModel(AndroidProject.class)
        return modelProject
    }
}