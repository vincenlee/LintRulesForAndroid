package com.ytjojo.lintjar.plugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.BasePlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.AndroidSourceSet;
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import com.android.build.gradle.internal.dsl.LintOptions
import com.android.build.gradle.tasks.Lint
import com.android.builder.model.AndroidProject
import com.android.builder.model.SourceProvider
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.codehaus.groovy.runtime.StringGroovyMethods
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.repositories.DefaultMavenLocalArtifactRepository
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.UnionFileTree
import org.gradle.api.internal.jvm.ClassDirectoryBinaryNamingScheme
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.impldep.org.apache.maven.artifact.repository.MavenArtifactRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.tooling.GradleConnector

import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream
import java.util.zip.ZipFile

class LintRulesPlugin implements Plugin<Project> {
    Project mProject;
    void apply(Project project) {
        this.mProject = project;
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
        newOptions.xmlReport = true

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
        String maventLocal2 = project.repositories.mavenLocal().url.toString() - 'file:' + group.replace('.', '/') + '/' + name.replace('.', '/') + '/' + version
        // name  对应的就是依赖的 artifactId
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

    private void exlude(Project project, String group, List<String> modules) {
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
                            File lintJarFile = new File(lintDir + "lint.jar")
                            if (lintJarFile.exists()) {
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

    private void download(String repoDepUrl, File depFile) {
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

    private HashSet<String> getRepUrls(Project project) {
        def repoUrls = new HashSet()
        repoUrls.add(project.repositories.jcenter().url.toString())
        repoUrls.add(project.repositories.mavenCentral().url.toString())
        //      repoUrls.add(project.repositories.google().url.toString())  gradle plugin 3.0 版本之后要加此仓库
        project.repositories.each {
            /**
             * 不处理 FlatDirectoryArtifactRepository 和 IvyArtifactRepository
             */
            if (!(it instanceof DefaultMavenLocalArtifactRepository) && (it instanceof MavenArtifactRepository)) {
                repoUrls.add(it.url)
            }
        }
        return repoUrls;


    }

    static String getDependencyByDownload(Project project, String dependency, HashSet<String> repoUrls, String mavenLocalDepPath) {
        def aarPath = ''
        try {
            def providedAarParent = new File(mavenLocalDepPath)
            if (!providedAarParent.exists()) {
                providedAarParent.mkdirs()
            }
            def split = dependency.split(':')
            def depPath = split[0].replace('.', '/') + '/' + split[1].replace('.', '/') + '/' + split[2]
            def depName = split[1] + '-' + split[2] + '.aar'
            def depFile = new File(providedAarParent, depName)
            if (depFile.exists()) {
                return depFile.absolutePath
            }
            for (String url : repoUrls) {
                def repoDepUrl = url.toString() + depPath + '/' + depName
                if (!repoDepUrl.startsWith('file:')) {
                    project.logger.info 'repoDepUrl : ' + repoDepUrl
                    FileUtils.copyURLToFile(new URL(repoDepUrl), depFile)
                    aarPath = "${providedAarParent.absolutePath}/${depName}"
                    break
                }
            }
        } catch (Exception e) {
            project.logger.error e.toString()
            aarPath = null
        }
        return aarPath
    }

    private void wrong(Project project) {


        Configuration configuration = project.getConfigurations().create("providedAar") {
            //进行传递依赖
            it.setTransitive(true)
            it.resolutionStrategy {
                //SNAPSHOT版本更新时间为0s
                cacheChangingModulesFor(0, 'seconds')
                //动态版本更新实际为5分钟
                cacheDynamicVersionsFor(5, 'minutes')
            }
        }
        //遍历解析出来的所有依赖
        configuration.incoming.dependencies.all {
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

    private void changeLog(Project project) {
        //redirect warning log to info log
        def listenerBackedLoggerContext = project.getLogger().getMetaClass().getProperty(project.getLogger(), "context")
        def originalOutputEventListener = listenerBackedLoggerContext.getOutputEventListener()
        def originalOutputEventLevel = listenerBackedLoggerContext.getLevel()
        listenerBackedLoggerContext.setOutputEventListener({ def outputEvent ->
            def logLevel = originalOutputEventLevel.name()
            if (!("QUIET".equalsIgnoreCase(logLevel) || "ERROR".equalsIgnoreCase(logLevel))) {
                if ("WARN".equals(outputEvent.getLogLevel().name())) {
                    String message = outputEvent.getMessage()
                    //Provided dependencies can only be jars.
                    //provided dependencies can only be jars.
                    if (message != null && (message.contains("Provided dependencies can only be jars.") || message.contains("provided dependencies can only be jars. "))) {
                        project.logger.info(message)
                        return
                    }
                }
                if (originalOutputEventListener != null) {
                    originalOutputEventListener.onOutput(outputEvent)
                }
            }
        })
    }

    private void configureR2Generation(Project project, DomainObjectSet<? extends BaseVariant> variants) {
        variants.all { variant ->
            def outputDir = new File(project.buildDir, "generated/source/r2/${variant.dirName}")
            def task = project.tasks.create("generate${variant.name.capitalize()}R2")
            task.outputs.dir(outputDir)
            variant.registerJavaGeneratingTask(task, outputDir)
            def rpackage = getPackageName(variant)
            def once = new AtomicBoolean(false);
            variant.getOutputs().each { output ->
                def processResources = output.processResources;
                task.dependsOn(processResources)
                if (once.compareAndSet(false, task)) {
                    def pathToR = rpackage.replace('.', File.separatorChar)
                    def rFile = new File(new File(processResources.sourceOutputDir, pathToR), "R.java")
                    task.getInputs().file(rFile)
                    task.doLast {

                    }
                }

            }
        }

    }

    // Parse the variant's main manifest file in order to get the package id which is used to create
    // R.java in the right place.
    private String getPackageName(BaseVariant variant) {
        def slurper = new XmlSlurper(false, false)
        variant.sourceSets.listIterator().collect { it.manifestFile }
        def list = variant.sourceSets.map { it.manifestFile }

        // According to the documentation, the earlier files in the list are meant to be overridden by the later ones.
        // So the first file in the sourceSets list should be main.
        def result = slurper.parse(list[0])
        return result.getProperty("@package").toString()
    }

    public TestedExtension getAndroidExtension(Project project) {
        return (TestedExtension)project.getProperties().get("android")
    }

    public void addAppAction(Action<BaseVariant> action, AppExtension extension) {
        extension.getApplicationVariants().all(action)
    }

    public void addLibraryAction(Action<BaseVariant> action, LibraryExtension extension) {
        extension.getLibraryVariants().all(action)
    }

    public DomainObjectSet<? extends BaseVariant> getAndroidVariants(Project project) {
        DomainObjectSet<? extends BaseVariant> domainObjectSet = null
        TestedExtension androidExtension = null;
        project.getPlugins().withType()
        project.getPlugins().withType(AppPlugin.class, {
            androidExtension = project.getExtensions().getByType(AppExtension.class);
            domainObjectSet = ((AppExtension) androidExtension).getApplicationVariants()

        });

        project.getPlugins().withType(LibraryPlugin.class, { libraryPlugin ->
            androidExtension = project.getExtensions().getByType(LibraryExtension.class);
            domainObjectSet = ((LibraryExtension) androidExtension).getLibraryVariants();

        });

//        project.getPlugins().withType(FeaturePlugin.class, {atomPlugin ->
//            androidExtension = project.getExtensions().getByType(FeatureExtension.class);
//           domainObjectSet = ((FeatureExtension)androidExtension).getFeatureVariants();
//        });

        return domainObjectSet

    }

    protected void addAllSourceJarDependence(Project project,DomainObjectSet<? extends BaseVariant> domainObjectSet) {

        domainObjectSet.all{variant ->
            Jar sourcesJarTask = project.getTasks().create("sources" + capitalize((CharSequence) variant.getName()) + "Jar", Jar.class, {jar ->
                jar.setDescription("Generate the sources jar for the " + variant.getName() + " variant");
                jar.setGroup("jar");

                jar.setClassifier("sources");
                jar.setAppendix(variant.getName());
                jar.from(getJavaTask(variant).property("source"));
            });

            allSourcesJarTask.dependsOn(sourcesJarTask);
            if (publishVariant(variant)) {
                getProject().getArtifacts().add(Dependency.ARCHIVES_CONFIGURATION, sourcesJarTask);
            }
        }
    }
    protected boolean publishVariant(BaseVariant variant) {
        if (variant instanceof TestVariant || variant instanceof UnitTestVariant) {
            return false;
        }

        return  getAndroidExtension(mProject).getDefaultPublishConfig().equals(variant.getName());
    }

    Task allSourcesJarTask
    private void createAllSourcesJarTask(){
        allSourcesJarTask = project.getTasks().create("sourcesJar",  {asjTask ->
            asjTask.setDescription("Generate the sources jar for all variants");
            asjTask.setGroup("jar");
        });
    }

    public void applyPlugins(Class<Plugin<Project>> pluginClass){
        mProject.getPluginManager().apply(pluginClass)
    }

    public FileCollection getCompileClasspath(BaseVariant variant) {
        return getJavaTask(variant).getClasspath();
    }
    /**
     * Returns the classpath used to compile this source.
     *
     * @return The classpath. Never returns null.
     * @see SourceSet#getCompileClasspath()
     */
    protected FileCollection getCompileClasspath(AndroidSourceSet androidSourceSet) {
        return getProject().getConfigurations().getByName(androidSourceSet.getCompileConfigurationName());
    }

    /**
     * All Java source files for this source set. This includes, for example, source which is directly compiled, and
     * source which is indirectly compiled through joint compilation.
     *
     * @return the Java source. Never returns null.
     * @see SourceSet#getAllJava()
     */
    protected static FileTree getAllJava(AndroidSourceSet androidSourceSet) {
        return androidSourceSet.getJava().getSourceFiles();
    }


    /**
     * Returns the name of a task for this source set.
     *
     * @param verb The action, may be null.
     * @param target The target, may be null
     * @return The task name, generally of the form ${verb}${name}${noun}
     * @see SourceSet#getTaskName(String, String)
     */
    protected static String getTaskName(AndroidSourceSet sourceSet, String verb, String target) {
        return new ClassDirectoryBinaryNamingScheme(sourceSet.getName()).getTaskName(verb, target);
    }


    public static FileTree getAllJava(BaseVariant variant) {
        return getJavaTask(variant).getSource();
    }

    /**
     * @see SourceSet#getOutput()
     */
    public FileTree getOutput(BaseVariant variant) {
        return getProject().fileTree(getJavaTask(variant).getDestinationDir());
    }

    public Project getProject(){
        return  mProject
    }

    /**
     * @see SourceSet#getOutput()
     */
    protected FileCollection getOutput(AndroidSourceSet androidSourceSet) {
        Stream<BaseVariant> testVariants = Stream.concat(getTestVariants().stream(), getUnitTestVariants().stream());
        Stream<BaseVariant> variants = Stream.concat(getAndroidVariants().stream(), testVariants);
        variants.
        FileTreeInternal[] sourceTrees = variants
                .filter{variant -> getJavaTask(variant) != null}
                .filter{variant -> variant.getSourceSets().contains(androidSourceSet)}
                .map{variant -> getJavaTask(variant).getDestinationDir()}
                .map{outputDir -> (FileTreeInternal) getProject().fileTree(outputDir)}
                .toArray(new FileTreeInternal[0]);
        return new UnionFileTree(sourceTrees);
    }

    private Map<String, String> excludeProperties(Configuration configuration,String group, String module) {
        configuration.exclude( ImmutableMap.<String, String>builder()
                .put("group", group)
                .put("module", module)
                .build())

    }

    protected void withBasePlugin(Action<Plugin> action) {
        project.getPlugins().withType(getBasePlugin(), action);
    }

    protected JavaPluginConvention getJavaPluginConvention() {
        return project.getConvention().getPlugin(JavaPluginConvention.class);
    }

    protected Class<? extends Plugin> getBasePlugin() {
        return JavaBasePlugin.class;
    }

    public FileCollection getCurentClassPath(BaseVariant variant,TestedExtension extension){
        return  getJavaTask(variant).classpath + project.files(extension.androidBuilder.getBootClasspath(false))
    }

    /**
     * Returns directory for plugin's private working directory for argument
     * workDir = new File(project.buildDir, "android-scala")
     * @param variant the Variant
     * @return
     */
    public File getVariantWorkDir(File workDir,BaseVariant variant) {
       return new File([workDir, "variant", variant.name].join(File.separator))
    }

    public BaseExtension getBaseExtension(){
        def androidPlugin = ['android', 'com.android.application', 'android-library', 'com.android.library',
                             'com.android.test', 'com.android.feature']
                .collect { project.plugins.findPlugin(it) as BasePlugin }
                .find { it != null }

        if (androidPlugin == null) {
            throw new GradleException('You must apply the Android plugin or the Android library plugin before using the groovy-android plugin')
        }
        def androidExtension = project.extensions.getByName("android") as BaseExtension
        return androidExtension

    }

    public static getRuntimeJars(BasePlugin plugin, BaseExtension extension) {
        if (plugin.metaClass.getMetaMethod('getRuntimeJarList')) {
            return plugin.runtimeJarList
        }

        if (extension.metaClass.getMetaMethod('getBootClasspath')) {
            return extension.bootClasspath
        }

        return plugin.bootClasspath
    }

    public static List<String> getJavaTaskCompilerArgs(JavaCompile javaTask, boolean skipJavaC) {
        def compilerArgs = javaTask.options.compilerArgs

        if (skipJavaC) {
            // if we skip java c the java compiler will still look for the annotation processor directory
            // we should create it for it.
            compilerArgs.findAll { !it.startsWith('-') }
                    .each { new File(it).mkdirs() }
        }

        return compilerArgs
    }


    public static String getVariantName(BaseVariant variant) {
        return variant.name
    }

    public static JavaCompile getJavaTask(BaseVariant variant) {
        // just get actual javac we don't support jack.

        return  variant.hasProperty('javaCompiler') ? variant.javaCompiler : variant.javaCompile
    }

    public static Iterable<SourceProvider> getSourceProviders(BaseVariant variantData) {
        return variantData.sourceSets
    }


//    private static List<File> getGeneratedSourceDirs(BaseVariant variantData) {
//        return variantData.getSourceFolders(SourceKind.JAVA).collect { it.dir }
//    }

    public static String pathJoin(String...paths) {
        paths.join(File.separator)
    }
    public static String capitalize(String a){
//        StringGroovyMethods.capitalize(a)
        String s = a.substring(1)
        return "${a[0].toUpperCase()}${s.toLowerCase()}"
    }
}