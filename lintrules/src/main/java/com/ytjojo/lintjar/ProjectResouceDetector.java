package com.ytjojo.lintjar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.lint.checks.ResourceUsageModel;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.ast.ClassDeclaration;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.android.SdkConstants.ANDROID_PKG;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REF_PREFIX;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.DOT_GIF;
import static com.android.SdkConstants.DOT_JPEG;
import static com.android.SdkConstants.DOT_JPG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.DRAWABLE_FOLDER;
import static com.android.SdkConstants.MIPMAP_FOLDER;
import static com.android.SdkConstants.RESOURCE_CLR_STYLEABLE;
import static com.android.SdkConstants.RESOURCE_CLZ_ARRAY;
import static com.android.SdkConstants.RESOURCE_CLZ_ID;
import static com.android.SdkConstants.R_ATTR_PREFIX;
import static com.android.SdkConstants.R_CLASS;
import static com.android.SdkConstants.R_ID_PREFIX;
import static com.android.SdkConstants.R_PREFIX;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_PLURALS;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STRING_ARRAY;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.tools.lint.detector.api.LintUtils.endsWith;
import static com.android.utils.SdkUtils.getResourceFieldName;

/**
 * Created by Administrator on 2018/1/10 0010.
 */

public class ProjectResouceDetector extends ResourceXmlDetector implements Detector.JavaPsiScanner {


    private static final Implementation IMPLEMENTATION = new Implementation(
            ProjectResouceDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES,
                    Scope.TEST_SOURCES));

    /** Unused resources (other than ids). */
    public static final Issue ISSUE = Issue.create(
            "MultProjectUnusedResources",
            "MultProjectUnused resources",
            "MultProjectUnused resources make applications larger and slow down builds.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            IMPLEMENTATION);

    /** Unused id's */
    public static final Issue ISSUE_DUPLICATE = Issue.create(
            "MultProjectDuplicateResource",
            "MultProjectDuplicate Resource",
            "This resource definition appears not to be needed since it is aready definition " +
                    "from library project.strong reason to delete these. ",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            IMPLEMENTATION);


    public ProjectResouceDetector() {

    }

    /**
     * 记录定义的资源 @+id/tv_name value下的resources values 文件夹下的 *.xml 中一级Element下资源 如Array style string id
     * R.type.name 如 R.array.selectlist R.string.app_name
     * R文件中所有值
     * 记录layout开头文件夹和drawable开头文件夹资源值 R.type.filename
     */

    /**
     * 记录@drawable/corner_white_back @layout/activity_main @color/white @string/app_name ..被引用的资源值为R.type.name
     * 同时记录java文件引用资源值
     */
    private Map<String, Location> mUnused;

    private Map<String, Map<String, Location>> mDuplicateDefinitionResource;//key resource value map of key project value loacation;
    private Map<String, Map<String, Location>> mUnUsedResource;//key project value collection of unUesdResouces;
    private Map<String, Set<String>> mProjectReferences;//key project value collection of References;
    private Map<String, Set<String>> mProjectDeclarations;//key project value collection of Declarations;

    @Override
    public void run(@NonNull Context context) {
        context.getMainProject();
        context.getProject().getDirectLibraries();
        context.getClient().getKnownProjects();
        assert false;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            mProjectDeclarations = new HashMap<>(12);
            mProjectReferences = new HashMap<>(12);
            String mainName = context.getProject().getName();
            mProjectDeclarations.put(mainName, new HashSet<String>(300));
            mProjectReferences.put(mainName, new HashSet<String>(300));
            mDependencedOnProjectMap = new LinkedHashMap<>();
            recordDependenciesTree(context);
            recordAllDependencies(context);
            ClassVistorUtile.jar(new File(context.getProject().getDir().getAbsolutePath()+"/build/intermediates/lint/lint.jar"));
        }

    }

    private void recordAllDependencies(@NonNull Context context) {
        if (!context.getProject().isLibrary()) {
            Project mainProject = context.getMainProject();
            List<Project> libraries = mainProject.getDirectLibraries();
            if (libraries != null) {
                for (Project project : libraries) {
                    recordAllLibraryDependencies(project);
                }
            }

        }
    }

    private void recordAllLibraryDependencies(Project project) {
        List<Project> libraries = project.getDirectLibraries();
        final String projectName = project.getName();

        final Set<String> dependencedOnThhisProjects = mDependencedOnProjectMap.get(projectName);
        if (libraries != null) {
            for (Project lib : libraries) {
                Set<String> denpendencedOns = mDependencedOnProjectMap.get(lib.getName());
                denpendencedOns.addAll(dependencedOnThhisProjects);
                recordAllLibraryDependencies(lib);
            }

        }
    }

    private Map<String, Set<String>> mDependencedOnProjectMap;

    private void recordDependenciesTree(@NonNull Context context) {
        if (!context.getProject().isLibrary()) {
            Project mainProject = context.getMainProject();
            List<Project> libraries = mainProject.getDirectLibraries();
            final String mainName = mainProject.getName();
            if (libraries != null) {
                for (Project project : libraries) {
                    Set<String> dependencedOns = new HashSet<>(20);
                    dependencedOns.add(mainName);
                    mDependencedOnProjectMap.put(project.getName(), dependencedOns);
                    recordLibrariesDependencedOn(project);
                }
            }

        }
    }

    private void recordLibrariesDependencedOn(Project project) {
        List<Project> libraries = project.getDirectLibraries();
        final String projectName = project.getName();
        if (libraries != null) {
            for (Project lib : libraries) {

                Set<String> denpendencedOns = mProjectDeclarations.get(lib.getName());
                if (denpendencedOns == null) {
                    denpendencedOns = new HashSet<>(20);
                    mDependencedOnProjectMap.put(lib.getName(), denpendencedOns);
                }
                denpendencedOns.add(projectName);
                recordLibrariesDependencedOn(lib);
            }

        }
    }

    @Override
    public void beforeCheckLibraryProject(@NonNull Context context) {
        super.beforeCheckLibraryProject(context);
        if (context.getPhase() == 1) {
            String libraryName = context.getProject().getName();
            mProjectDeclarations.put(libraryName, new HashSet<String>(300));
            mProjectReferences.put(libraryName, new HashSet<String>(300));
        }
    }

    @Override
    public void afterCheckLibraryProject(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            // If this is a library project not being analyzed, ignore it
            return;
        }
        if (context.getDriver().getPhase() == 1) {
            checkResourceFolder(context, context.getProject());
        }else {
            locationDrawableFolder(context,context.getProject());
        }

    }
    // ---- Implements JavaScanner ----

    //记录被定义的资源，只有layout drawable mipmap 三种 而且只是xml类型的
    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;

        boolean isXmlFile = LintUtils.isXmlFile(file);
        if (isXmlFile || LintUtils.isBitmapFile(file)) {
            String fileName = file.getName();
            String parentName = file.getParentFile().getName();
            int dash = parentName.indexOf('-');
            String typeName = parentName.substring(0, dash == -1 ? parentName.length() : dash);
            ResourceType type = ResourceType.getEnum(typeName);
            if (type != null && LintUtils.isFileBasedResourceType(type)) {
                String baseName = fileName.substring(0, fileName.length() - DOT_XML.length());
                String resource = R_PREFIX + typeName + '.' + baseName;
                if (context.getPhase() == 1) {
                    mProjectDeclarations.get(context.getProject().getName()).add(resource);
                } else {
                    assert context.getPhase() == 2;
                    if(duplicateContain(context,resource)){

                        recordDuplicatLocation(context, resource, Location.create(file));
                    }
                    if(unUsedContain(context,resource)){
                        recordUnusedLocation(context,resource,Location.create(file));
                    }
                }
            }
        }
    }


    private void checkDuplicate(Context context) {
        final Set<String> allProject = new HashSet<>(20);
        allProject.addAll(mDependencedOnProjectMap.keySet());
        allProject.add(context.getProject().getName());
        mDuplicateDefinitionResource = new HashMap<>(200);
        for (String name : allProject) {
            final Set<String> declarations = mProjectDeclarations.get(name);
            List<String> ids = new ArrayList<String>();
            for (String resource : declarations) {
                if (resource.startsWith(R_ID_PREFIX)) {
                    ids.add(resource);
                }
            }
            declarations.removeAll(ids);
            for (String nameAnother : allProject) {
                if (nameAnother.equals(name)) {
                    continue;
                }
                Set<String> declaratinsAnother = mProjectDeclarations.get(nameAnother);
                for (String declaration : declaratinsAnother) {
                    if (declarations.contains(declaration)) {
                        Map<String, Location> projectLocations = mDuplicateDefinitionResource.get(declaration);
                        if (projectLocations == null) {
                            projectLocations = new HashMap<>(20);
                            projectLocations.put(name, null);
                            projectLocations.put(nameAnother, null);
                            mDuplicateDefinitionResource.put(declaration, projectLocations);
                        } else {
                            projectLocations.put(name, null);
                            projectLocations.put(nameAnother, null);
                        }
                    }
                }

            }
        }
        for (String name : allProject) {
            final Set<String> references = mProjectReferences.get(name);
            final Set<String> declarations = mProjectDeclarations.get(name);
            if (references != null && declarations != null && !references.isEmpty() && !declarations.isEmpty()) {
                declarations.removeAll(references);
            }

        }
        for (String name : allProject) {
            final Set<String> declarations = mProjectDeclarations.get(name);
            if (name.equals(context.getProject().getName()) && declarations != null && !declarations.isEmpty()) {
                continue;
            }
            Set<String> libraries = mDependencedOnProjectMap.get(name);
            if (libraries != null && !libraries.isEmpty()) {
                for (String lib : libraries) {
                    Set<String> references = mProjectReferences.get(lib);
                    declarations.removeAll(references);
                }
            }

        }
        mUnUsedResource = new HashMap<>();
        for (String name : allProject) {
            final Set<String> declarations = mProjectDeclarations.get(name);
            if (declarations != null && !declarations.isEmpty()) {
                HashMap<String, Location> map = new HashMap<>(50);
                for (String declaration : declarations) {
                    map.put(declaration, null);
                }
                mUnUsedResource.put(name, map);
            }

        }

    }

    private void locationDrawableFolder(Context context, Project project) {
        List<File> resourceFolders = project.getResourceFolders();
        for (File res : resourceFolders) {
            File[] folders = res.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    String folderName = folder.getName();
                    if (folderName.startsWith(DRAWABLE_FOLDER)
                            || folderName.startsWith(MIPMAP_FOLDER)) {
                        File[] files = folder.listFiles();
                        if (files != null) {
                            locationDrawableDir(context, folder, files);
                        }
                    }
                }
            }
        }
    }

    private void locationDrawableDir(Context context, File folder, File[] files) {
        for (File file : files) {
            // TODO: Combine this check with the check for expected sizes such that
            // I don't check file sizes twice!
            String fileName = file.getName();

            if (endsWith(fileName, DOT_PNG) || endsWith(fileName, DOT_JPG)
                    || endsWith(fileName, DOT_JPEG) || endsWith(fileName, DOT_WEBP)) {
                String folderName = folder.getName();
                String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));
                String reference = null;
                if (folderName.startsWith(DRAWABLE_FOLDER)) {
                    reference = R_PREFIX + "drawable." + baseFileName;
                } else if (folderName.startsWith(MIPMAP_FOLDER)) {
                    reference = R_PREFIX + "mipmap." + baseFileName;
                }
                if(duplicateContain(context,reference)){
                    recordDuplicatLocation(context,reference,Location.create(file));
                }
                if(unUsedContain(context,reference)){
                    recordUnusedLocation(context, reference, Location.create(file));

                }

            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            checkResourceFolder(context, context.getProject());
            checkDuplicate(context);

            locationDrawableFolder(context, context.getProject());
            if (!context.getDriver().hasParserErrors()) {
                // Request another pass, and in the second pass we'll gather location
                // information for all declaration locations we've found
                context.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE);
            }
        } else {
            assert context.getPhase() == 2;

            // Report any resources that we (for some reason) could not find a declaration
            // location for
            if (!mDuplicateDefinitionResource.isEmpty()) {

                List<String> sorted = new ArrayList(mDuplicateDefinitionResource.keySet());
                Collections.sort(sorted);
                Boolean skippedLibraries = null;
                for (String resource : sorted) {
                    Map<String, Location> projectLocationMap = mDuplicateDefinitionResource.get(resource);
                    Set<Map.Entry<String, Location>> entrySet = projectLocationMap.entrySet();
                    for(Map.Entry<String,Location> entry: entrySet){
                        Location location = entry.getValue();
                        String projectName = entry.getKey();

                        if (location != null) {
                            // We were prepending locations, but we want to prefer the base folders
                            location = Location.reverse(location);
                        }

                        if (location == null) {
                            if (skippedLibraries == null) {
                                skippedLibraries = false;
                                for (Project project : context.getDriver().getProjects()) {
                                    if (!project.getReportIssues()) {
                                        skippedLibraries = true;
                                        break;
                                    }
                                }
                            }
                            if (skippedLibraries) {
                                // Skip this resource if we don't have a location, and one or
                                // more library projects were skipped; the resource was very
                                // probably defined in that library project and only encountered
                                // in the main project's java R file
                                continue;
                            }
                        }

                        String message = String.format("The resource `%1$s` in project `%2$s` appears to be duplicate",
                                resource,projectName);
                        context.report(ISSUE_DUPLICATE, location, message);
                    }

                }
            }
            if (!mUnUsedResource.isEmpty()) {

                List<String> sorted = new ArrayList(mUnUsedResource.keySet());
                Collections.sort(sorted);
                Boolean skippedLibraries = null;
                for (String projectName : sorted) {
                    Map<String, Location> resourceLocationMap = mUnUsedResource.get(projectName);
                    sorted = new ArrayList(resourceLocationMap.keySet());
                    Collections.sort(sorted);
                    for(String resource: sorted){
                        Location location = resourceLocationMap.get(resource);

                        if (location != null) {
                            // We were prepending locations, but we want to prefer the base folders
                            location = Location.reverse(location);
                        }

                        if (location == null) {
                            if (skippedLibraries == null) {
                                skippedLibraries = false;
                                for (Project project : context.getDriver().getProjects()) {
                                    if (!project.getReportIssues()) {
                                        skippedLibraries = true;
                                        break;
                                    }
                                }
                            }
                            if (skippedLibraries) {
                                // Skip this resource if we don't have a location, and one or
                                // more library projects were skipped; the resource was very
                                // probably defined in that library project and only encountered
                                // in the main project's java R file
                                continue;
                            }
                        }

                        String message = String.format("The resource `%1$s` in project `%2$s` appears to be unused",
                                resource,projectName);
                        context.report(ISSUE, location, message);
                    }

                }
            }
        }
    }


    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_STYLE,
                TAG_RESOURCES,
                TAG_ARRAY,
                TAG_STRING_ARRAY,
                TAG_INTEGER_ARRAY,
                TAG_PLURALS
        );
    }


    private boolean unUsedContain(Context context,String resource){
        String projectName = context.getProject().getName();
        Map<String, Location> unUsed = mUnUsedResource.get(projectName);
        if(unUsed !=null && unUsed.containsKey(resource)){
            return true;
        }
        return false;
    }

    private void recordUnusedLocation(Context context, String resource, Location location) {
        String projectName = context.getProject().getName();
        Map<String, Location> unUsed = mUnUsedResource.get(projectName);
        if (unUsed !=null && unUsed.containsKey(resource)) {
            if (unUsed.get(resource) != null) {
                location.setSecondary(mUnused.get(resource));
            }
            unUsed.put(resource, location);
        }


    }
    private boolean duplicateContain(Context context,String resource){
        String projectName = context.getProject().getName();
        Map<String, Location> projectDuplicateResources = mDuplicateDefinitionResource.get(resource);
        if (projectDuplicateResources != null) {

            if(projectDuplicateResources.containsKey(projectName)){
                return true;
            }
        }
        return false;
    }

    private void recordDuplicatLocation(Context context, String resource, Location location) {
        String projectName = context.getProject().getName();
        Map<String, Location> projectDuplicateResources = mDuplicateDefinitionResource.get(resource);
        if (projectDuplicateResources != null) {
            if (projectDuplicateResources.get(projectName) != null) {
                location.setSecondary(projectDuplicateResources.get(projectName));
            }
            projectDuplicateResources.put(projectName, location);
        }
    }


    @Override
    public void afterCheckFile(@NonNull Context context) {
        super.afterCheckFile(context);
    }

    //记录value目录下的xml资源，如id String style declare-styleable array
    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (TAG_RESOURCES.equals(element.getTagName())) {
            for (Element item : LintUtils.getChildren(element)) {
                Attr nameAttribute = item.getAttributeNode(ATTR_NAME);
                /**
                 *
                 * <item type="id" name="topbar_rightView" /> name=item type = id
                 *
                 * <array name="edit_phrase">
                 * <item>@string/edit</item>
                 * <item>@string/delete</item>
                 * </array>
                 */
                if (nameAttribute != null) {
                    String name = getResourceFieldName(nameAttribute.getValue());
                    String type = item.getTagName();
                    if (type.equals(TAG_ITEM)) {
                        type = item.getAttribute(ATTR_TYPE);
                        if (type == null || type.isEmpty()) {
                            type = RESOURCE_CLZ_ID;
                        }
                    } else if (type.equals("declare-styleable")) {   //$NON-NLS-1$
                        type = RESOURCE_CLR_STYLEABLE;
                    } else if (type.contains("array")) {             //$NON-NLS-1$
                        // <string-array> etc
                        type = RESOURCE_CLZ_ARRAY;
                    }
                    String resource = R_PREFIX + type + '.' + name;

                    if (context.getPhase() == 1) {
                        mProjectDeclarations.get(context.getProject().getName()).add(resource);
                        checkChildRefs(context, item);
                    } else {
                        assert context.getPhase() == 2;
                        if(unUsedContain(context,resource)){
                            recordUnusedLocation(context,resource, context.getLocation(nameAttribute));
                        }
                        if (duplicateContain(context,resource)) {
                            recordDuplicatLocation(context, resource, context.getLocation(nameAttribute));
                        }
                    }
                }
            }
        } else //noinspection VariableNotUsedInsideIf
            if (context.getDriver().getPhase() == 1) {
                assert TAG_STYLE.equals(element.getTagName())
                        || TAG_ARRAY.equals(element.getTagName())
                        || TAG_PLURALS.equals(element.getTagName())
                        || TAG_INTEGER_ARRAY.equals(element.getTagName())
                        || TAG_STRING_ARRAY.equals(element.getTagName());
                for (Element item : LintUtils.getChildren(element)) {
                    checkChildRefs(context, item);
                }
            }
    }

    private static final String ANALYTICS_FILE = "analytics.xml"; //$NON-NLS-1$

    /**
     * Returns true if this XML file corresponds to an Analytics configuration file;
     * these contain some attributes read by the library which won't be flagged as
     * used by the application
     *
     * @param context the context used for scanning
     * @return true if the file represents an analytics file
     */
    public static boolean isAnalyticsFile(Context context) {
        File file = context.file;
        return file.getPath().endsWith(ANALYTICS_FILE) && file.getName().equals(ANALYTICS_FILE);
    }

    //检查Element是否引用了其他资源
    private void checkChildRefs(@NonNull XmlContext context, Element item) {
        // Look for ?attr/ and @dimen/foo etc references in the item children
        NodeList childNodes = item.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();

                int index = text.indexOf(ATTR_REF_PREFIX);
                if (index != -1) {
                    String name = text.substring(index + ATTR_REF_PREFIX.length()).trim();
                    mProjectReferences.get(context.getProject().getName()).add(R_ATTR_PREFIX + name);
                } else {
                    index = text.indexOf('@');
                    if (index != -1 && text.indexOf('/', index) != -1
                            && !text.startsWith("@android:", index)) {  //$NON-NLS-1$
                        // Compute R-string, e.g. @string/foo => R.string.foo
                        String token = text.substring(index + 1).trim().replace('/', '.');
                        String r = R_PREFIX + token;
                        mProjectReferences.get(context.getProject().getName()).add(r);
                    }
                }
            }
        }
    }

    //记录@drawable/corner_white_back @layout/activity_main @color/white @string/app_name ..
    //统计下引用的资源
    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();

        if (value.startsWith("@+") && !value.startsWith("@+android")) { //$NON-NLS-1$ //$NON-NLS-2$
            String resource = R_PREFIX + value.substring(2).replace('/', '.');
            // We already have the declarations when we scan the R file, but we're tracking
            // these here to get attributes for position info
            //追踪@+id/tv_name
            if (context.getPhase() == 1) {
                mProjectDeclarations.get(context.getProject().getName()).add(resource);
            } else if (context.getPhase() == 2) {
                if(duplicateContain(context,resource)){
                    recordDuplicatLocation(context,resource,context.getLocation(attribute));
                }
                if(unUsedContain(context,resource)){
                    recordUnusedLocation(context, resource, context.getLocation(attribute));
                }
                return;
            }
        } else if (context.getDriver().getPhase() == 1) {
            if (value.startsWith("@")              //$NON-NLS-1$
                    && !value.startsWith("@android:")) {  //$NON-NLS-1$
                // Compute R-string, e.g. @string/foo => R.string.foo
                String r = R_PREFIX + value.substring(1).replace('/', '.');
                mProjectReferences.get(context.getProject().getName()).add(r);
            } else if (value.startsWith(ATTR_REF_PREFIX)) {
                mProjectReferences.get(context.getProject().getName()).add(R_ATTR_PREFIX + value.substring(ATTR_REF_PREFIX.length()));
            }
        }

        if ((attribute.getNamespaceURI() != null)
                && !ANDROID_URI.equals(attribute.getNamespaceURI()) && (context.getDriver().getPhase() == 1)) {
            mProjectReferences.get(context.getProject().getName()).add(R_ATTR_PREFIX + attribute.getLocalName());
        }
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.SLOW;
    }



    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    //记录java文件使用的资源，比如setImageResruce（R.drawable.ic_logo)

    @Override
    public void visitResourceReference(@NonNull JavaContext context,
            @Nullable JavaElementVisitor visitor, @NonNull PsiElement node,
            @NonNull ResourceType type, @NonNull String name, boolean isFramework) {
        if (context.getDriver().getPhase() == 1 && !isFramework) {
            String reference = R_PREFIX + type.getName() + '.' + name;
            mProjectReferences.get(context.getProject().getName()).add(reference);
        }
    }


    private void checkResourceFolder(Context context, @NonNull Project project) {
        List<File> resourceFolders = project.getResourceFolders();
        for (File res : resourceFolders) {
            File[] folders = res.listFiles();
            if (folders != null) {
                for (File folder : folders) {
                    String folderName = folder.getName();
                    if (folderName.startsWith(DRAWABLE_FOLDER)
                            || folderName.startsWith(MIPMAP_FOLDER)) {
                        File[] files = folder.listFiles();
                        if (files != null) {
                            checkDrawableDir(context, folder, files);
                        }
                    }
                }

            }
        }
    }

    /**
     * Pattern for icon names that include their dp size as part of the name
     */
    private static final Pattern DP_NAME_PATTERN = Pattern.compile(".+_(\\d+)dp\\.[a-zA-Z]+");

    /**
     * Like {@link LintUtils#isBitmapFile(File)} but (a) operates on Strings instead
     * of files and (b) also considers XML drawables as images
     */
    public static boolean isDrawableFile(String name) {
        // endsWith(name, DOT_PNG) is also true for endsWith(name, DOT_9PNG)
        return endsWith(name, DOT_PNG) || endsWith(name, DOT_JPG) || endsWith(name, DOT_GIF)
                || endsWith(name, DOT_XML) || endsWith(name, DOT_JPEG) || endsWith(name, DOT_WEBP);
    }


    private static final boolean INCLUDE_LDPI;

    static {
        boolean includeLdpi = false;

        String value = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        if (value != null) {
            includeLdpi = Boolean.valueOf(value);
        }
        INCLUDE_LDPI = includeLdpi;
    }

    /**
     * Pattern for the expected density folders to be found in the project
     */
    private static final Pattern DENSITY_PATTERN = Pattern.compile(
            "^drawable-(nodpi|xxxhdpi|xxhdpi|xhdpi|hdpi|mdpi"
                    + (INCLUDE_LDPI ? "|ldpi" : "") + ")$");


    private void checkDrawableDir(Context context, File folder, File[] files) {

        for (File file : files) {
            // TODO: Combine this check with the check for expected sizes such that
            // I don't check file sizes twice!
            String fileName = file.getName();

            if (endsWith(fileName, DOT_PNG) || endsWith(fileName, DOT_JPG)
                    || endsWith(fileName, DOT_JPEG) || endsWith(fileName, DOT_WEBP)) {
                String folderName = folder.getName();
                String baseFileName = fileName.substring(0, fileName.lastIndexOf('.'));
                if (folderName.startsWith(DRAWABLE_FOLDER)) {
                    mProjectDeclarations.get(context.getProject().getName()).add(R_PREFIX + "drawable." + baseFileName);
                } else if (folderName.startsWith(MIPMAP_FOLDER)) {
                    mProjectDeclarations.get(context.getProject().getName()).add(R_PREFIX + "mipmap." + baseFileName);
                }
            }
        }
    }
    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        List<Class<? extends PsiElement>> types = new ArrayList<>(3);
        types.add(PsiImportStaticStatement.class);
        types.add(PsiCallExpression.class);
        types.add(PsiMethodCallExpression.class);
        return types;
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        if (context.getDriver().getPhase() == 1) {
            return new UnusedResourceVisitor(context);
        } else {
            // Second pass, computing resource declaration locations: No need to look at Java
            return null;
        }
    }

    // Look for references and declarations
    private class UnusedResourceVisitor extends JavaElementVisitor {
        private final JavaContext mContext;
        public UnusedResourceVisitor(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitImportStaticStatement(PsiImportStaticStatement statement) {
            if (mScannedForStaticImports) {
                return;
            }
            if (statement.isOnDemand()) {
                // Wildcard import of whole type:
                // import static pkg.R.type.*;
                // We have to do a more expensive analysis here to
                // for example recognize "x" as a reference to R.string.x
                mScannedForStaticImports = true;
                statement.getContainingFile().accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitReferenceExpression(PsiReferenceExpression expression) {
                        PsiElement resolved = expression.resolve();
                        if (resolved instanceof PsiField) {
                            addResourceReferences(resolved);
                        }
                        super.visitReferenceExpression(expression);
                    }
                });
            } else {
                PsiElement resolved = statement.resolve();
                if (resolved instanceof PsiField) {
                    addResourceReferences(resolved);
                }
            }
        }
        private void addResourceReferences(PsiElement node){
            // R.type.name
            if (node instanceof PsiReferenceExpression) {
                PsiReferenceExpression expression = (PsiReferenceExpression) node;
                if (expression.getQualifier() instanceof PsiReferenceExpression) {
                    PsiReferenceExpression select = (PsiReferenceExpression) expression.getQualifier();
                    if (select.getQualifier() instanceof PsiReferenceExpression) {
                        PsiReferenceExpression reference = (PsiReferenceExpression) select
                                .getQualifier();
                        if (R_CLASS.equals(reference.getReferenceName())) {
                            String type = select.getReferenceName();
                            String name = expression.getReferenceName();
                            if (type != null && name != null) {
                                boolean isFramework =
                                        reference.getQualifier() instanceof PsiReferenceExpression
                                                && ANDROID_PKG
                                                .equals(((PsiReferenceExpression) reference.
                                                        getQualifier()).getReferenceName());

                                mProjectReferences.get(mContext.getProject().getName()).add( R_PREFIX + type + '.' + name);
                            }
                        }
                    }
                }
            } else if (node instanceof PsiField) {
                PsiField field = (PsiField) node;
                PsiClass typeClass = field.getContainingClass();
                if (typeClass != null) {
                    PsiClass rClass = typeClass.getContainingClass();
                    if (rClass != null && R_CLASS.equals(rClass.getName())) {
                        String name = field.getName();
                        String type = typeClass.getName();
                        if (type != null && name != null) {
                            String qualifiedName = rClass.getQualifiedName();
                            boolean isFramework = qualifiedName != null
                                    && qualifiedName.startsWith(ANDROID_PKG_PREFIX);
                            mProjectReferences.get(mContext.getProject().getName()).add( R_PREFIX + type + '.' + name);
                        }
                    }
                }
            }
        }
        @Override public void visitCallExpression(PsiCallExpression callExpression) {
            PsiMethod method = callExpression.resolveMethod();
            if (method == null) {
                return;
            }
            if(!method.getName().equals("getIdentifier")){
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            JavaEvaluator evaluator = mContext.getEvaluator();
            String owner = evaluator.getInternalName(containingClass);
            if (owner == null) {
                return; // Couldn't resolve type
            }
            String name = LintUtils.getInternalMethodName(method);
            String desc = evaluator.getInternalDescription(method, false, false);
            if (desc == null) {
                // Couldn't compute description of method for some reason; probably
                // failure to resolve parameter types
                return;
            }
            String className = containingClass.getQualifiedName();
            PsiClass superClass = containingClass;
            boolean isResource = false;
            while (!superClass.getQualifiedName().equals("java.lang.Object")){
                if(superClass.getQualifiedName().equals("android.content.res.Resources")){
                    isResource =true;
                    break;
                }
                superClass = superClass.getSuperClass();
            }
            if(!isResource){
                return;
            }
            PsiExpressionList argumentList = callExpression.getArgumentList();

        }

        @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {

            if(expression.getMethodExpression().getReferenceName().equals("getIdentifier")){
                PsiClass containingClass = expression.resolveMethod().getContainingClass();
                PsiClass superClass = containingClass;
                boolean isResource = false;
                while (!superClass.getQualifiedName().equals("java.lang.Object")){
                    if(superClass.getQualifiedName().equals("android.content.res.Resources")){
                        isResource =true;
                        break;
                    }
                    superClass = superClass.getSuperClass();
                }
                if(!isResource){
                    return;
                }
                PsiExpressionList argumentList = expression.getArgumentList();
                PsiExpression[] ss = argumentList.getExpressions();
                String name = null;
                String type = null;

                for(PsiExpression e :ss){
                    String text = null;
                    if(e instanceof PsiLiteralExpression){
                        PsiLiteralExpression literalExpression = (PsiLiteralExpression) e;
                        text = (String) literalExpression.getValue();
                    }

                    if(name == null){
                        name = text;
                        continue;
                    }
                    if(type == null){
                        type = text;
                        break;
                    }
                }
                if(type !=null && name !=null){
                    String reference = R_PREFIX + type + '.' + name;
                    mProjectReferences.get(mContext.getProject().getName()).add(reference);
                }


            }
        }

        private boolean mScannedForStaticImports;
    }

}
