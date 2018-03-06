package com.ytjojo.lintjar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.Variant;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.checks.ResourceUsageModel;
import com.android.tools.lint.checks.ResourceUsageModel.Resource;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Detector.JavaPsiScanner;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import static com.android.SdkConstants.ATTR_DISCARD;
import static com.android.SdkConstants.ATTR_KEEP;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_SHRINK_MODE;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TOOLS_PREFIX;
import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.tools.lint.detector.api.LintUtils.findSubstring;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static com.google.common.base.Charsets.UTF_8;

/**
 * Finds unused resources.
 */
@Deprecated
public class UnusedResourceDetector extends ResourceXmlDetector implements JavaPsiScanner,
        BinaryResourceScanner, XmlScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
            UnusedResourceDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES,
                    Scope.BINARY_RESOURCE_FILE, Scope.TEST_SOURCES));

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
            1,
            Severity.WARNING,
            IMPLEMENTATION)
            .setEnabledByDefault(false);

    private final UnusedResourceDetectorUsageModel mModel =
            new UnusedResourceDetectorUsageModel();

    /**
     * Whether the resource detector will look for inactive resources (e.g. resource and code
     * references in source sets that are not the primary/active variant)
     */
    public static boolean sIncludeInactiveReferences = true;

    /**
     * Constructs a new {@link UnusedResourceDetector}
     */
    public UnusedResourceDetector() {
    }

    private void addDynamicResources(
            @NonNull Context context) {
        Project project = context.getProject();
        AndroidProject model = project.getGradleProjectModel();
        if (model != null) {
            Variant selectedVariant = project.getCurrentVariant();
            if (selectedVariant != null) {
                for (BuildTypeContainer container : model.getBuildTypes()) {
                    if (selectedVariant.getBuildType().equals(container.getBuildType().getName())) {
                        addDynamicResources(project, container.getBuildType().getResValues());
                    }
                }
            }
            ProductFlavor flavor = model.getDefaultConfig().getProductFlavor();
            addDynamicResources(project, flavor.getResValues());
        }
    }

    private void addDynamicResources(@NonNull Project project,
            @NonNull Map<String, ClassField> resValues) {
        Set<String> keys = resValues.keySet();
        if (!keys.isEmpty()) {
            Location location = LintUtils.guessGradleLocation(project);
            for (String name : keys) {
                ClassField field = resValues.get(name);
                ResourceType type = ResourceType.getEnum(field.getType());
                if (type == null) {
                    // Highly unlikely. This would happen if in the future we add
                    // some new ResourceType, that the Gradle plugin (and the user's
                    // Gradle file is creating) and it's an older version of Studio which
                    // doesn't yet have this ResourceType in its enum.
                    continue;
                }
                Resource resource = mModel.declareResource(type, name, null);
                resource.recordLocation(location);
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            Project project = context.getProject();

            // Look for source sets that aren't part of the active variant;
            // we need to make sure we find references in those source sets as well
            // such that we don't incorrectly remove resources that are
            // used by some other source set.
            if (sIncludeInactiveReferences && project.isGradleProject() && !project.isLibrary()) {
                AndroidProject model = project.getGradleProjectModel();
                Variant variant = project.getCurrentVariant();
                if (model != null && variant != null) {
                    addInactiveReferences(model, variant);
                }
            }

            addDynamicResources(context);
            mModel.processToolsAttributes();

            List<Resource> unusedResources = mModel.findUnused();
            Set<Resource> unused = Sets.newHashSetWithExpectedSize(unusedResources.size());
            for (Resource resource : unusedResources) {
                if (resource.isDeclared()
                        && !resource.isPublic()
                        && resource.type != ResourceType.PUBLIC) {
                    unused.add(resource);
                }
            }

            // Remove id's if the user has disabled reporting issue ids
            if (!unused.isEmpty()) {
                // Remove all R.id references
                List<Resource> ids = Lists.newArrayList();
                for (Resource resource : unused) {
                    if (resource.type == ResourceType.ID) {
                        ids.add(resource);
                    }
                }
                unused.removeAll(ids);
            }

            if (!unused.isEmpty()) {
                mModel.unused = unused;

                // Request another pass, and in the second pass we'll gather location
                // information for all declaration locations we've found
                context.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE);
            }
        } else {
            assert context.getPhase() == 2;

            // Report any resources that we (for some reason) could not find a declaration
            // location for
            Collection<Resource> unused = mModel.unused;
            if (!unused.isEmpty()) {
                // Final pass: we may have marked a few resource declarations with
                // tools:ignore; we don't check that on every single element, only those
                // first thought to be unused. We don't just remove the elements explicitly
                // marked as unused, we revisit everything transitively such that resources
                // referenced from the ignored/kept resource are also kept.
                unused = mModel.findUnused(Lists.newArrayList(unused));
                if (unused.isEmpty()) {
                    return;
                }

                // Fill in locations for files that we didn't encounter in other ways
                for (Resource resource : unused) {
                    Location location = resource.locations;
                    //noinspection VariableNotUsedInsideIf
                    if (location != null) {
                        continue;
                    }

                    // Try to figure out the file if it's a file based resource (such as R.layout) --
                    // in that case we can figure out the filename since it has a simple mapping
                    // from the resource name (though the presence of qualifiers like -land etc
                    // makes it a little tricky if there's no base file provided)
                    ResourceType type = resource.type;
                    if (type != null && LintUtils.isFileBasedResourceType(type)) {
                        String name = resource.name;

                        List<File> folders = Lists.newArrayList();
                        List<File> resourceFolders = context.getProject().getResourceFolders();
                        for (File res : resourceFolders) {
                            File[] f = res.listFiles();
                            if (f != null) {
                                folders.addAll(Arrays.asList(f));
                            }
                        }
                        if (!folders.isEmpty()) {
                            // Process folders in alphabetical order such that we process
                            // based folders first: we want the locations in base folder
                            // order
                            Collections.sort(folders,
                                    (file1, file2) -> file1.getName().compareTo(file2.getName()));
                            for (File folder : folders) {
                                if (folder.getName().startsWith(type.getName())) {
                                    File[] files = folder.listFiles();
                                    if (files != null) {
                                        Arrays.sort(files);
                                        for (File file : files) {
                                            String fileName = file.getName();
                                            if (fileName.startsWith(name)
                                                    && fileName.startsWith(".",
                                                            name.length())) {
                                                resource.recordLocation(Location.create(file));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                List<Resource> sorted = Lists.newArrayList(unused);
                Collections.sort(sorted);

                Boolean skippedLibraries = null;

                for (Resource resource : sorted) {
                    Location location = resource.locations;
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

                    // Keep in sync with getUnusedResource() below
                    String message = String.format("The resource `%1$s` appears to be unused",
                            resource.getField());
                    if (location == null) {
                        location = Location.create(context.getProject().getDir());
                    }
                    context.report(getIssue(resource), location, message);
                }
            }
        }
    }

    /** Returns source providers that are <b>not</b> part of the given variant */
    @NonNull
    private static List<SourceProvider> getInactiveSourceProviders(
            @NonNull AndroidProject project,
            @NonNull Variant variant) {
        Collection<Variant> variants = project.getVariants();
        List<SourceProvider> providers = Lists.newArrayList();

        // Add other flavors
        Collection<ProductFlavorContainer> flavors = project.getProductFlavors();
        for (ProductFlavorContainer pfc : flavors) {
            if (variant.getProductFlavors().contains(pfc.getProductFlavor().getName())) {
                continue;
            }
            providers.add(pfc.getSourceProvider());
        }

        // Add other multi-flavor source providers
        for (Variant v : variants) {
            if (variant.getName().equals(v.getName())) {
                continue;
            }
            SourceProvider provider = v.getMainArtifact().getMultiFlavorSourceProvider();
            if (provider != null) {
                providers.add(provider);
            }
        }

        // Add other the build types
        Collection<BuildTypeContainer> buildTypes = project.getBuildTypes();
        for (BuildTypeContainer btc : buildTypes) {
            if (variant.getBuildType().equals(btc.getBuildType().getName())) {
                continue;
            }
            providers.add(btc.getSourceProvider());
        }

        // Add other the other variant source providers
        for (Variant v : variants) {
            if (variant.getName().equals(v.getName())) {
                continue;
            }
            SourceProvider provider = v.getMainArtifact().getVariantSourceProvider();
            if (provider != null) {
                providers.add(provider);
            }
        }

        return providers;
    }

    private void recordInactiveJavaReferences(@NonNull File resDir) {
        File[] files = resDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    recordInactiveJavaReferences(file);
                } else if (file.getName().endsWith(DOT_JAVA)) {
                    try {
                        String java = Files.toString(file, UTF_8);
                        mModel.tokenizeJavaCode(java);
                    } catch (Throwable ignore) {
                        // Tolerate parsing errors etc in these files; they're user
                        // sources, and this is even for inactive source sets.
                    }
                }
            }
        }
    }

    private void recordInactiveXmlResources(@NonNull File resDir) {
        File[] resourceFolders = resDir.listFiles();
        if (resourceFolders != null) {
            for (File folder : resourceFolders) {
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
                if (folderType != null) {
                    recordInactiveXmlResources(folderType, folder);
                }
            }
        }
    }

    // Used for traversing resource folders *outside* of the normal Gradle variant
    // folders: these are not necessarily on the project path, so we don't have PSI files
    // for them
    private void recordInactiveXmlResources(@NonNull ResourceFolderType folderType,
      @NonNull File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String path = file.getPath();
                boolean isXml = endsWithIgnoreCase(path, DOT_XML);
                try {
                    if (isXml) {
                        String xml = Files.toString(file, UTF_8);
                        Document document = XmlUtils.parseDocument(xml, true);
                        mModel.visitXmlDocument(file, folderType, document);
                    } else {
                        mModel.visitBinaryResource(folderType, file);
                    }
                } catch (Throwable ignore) {
                    // Tolerate parsing errors etc in these files; they're user
                    // sources, and this is even for inactive source sets.
                }
            }
        }
    }

    private void addInactiveReferences(@NonNull AndroidProject model,
                              @NonNull Variant variant) {
        for (SourceProvider provider : getInactiveSourceProviders(model, variant)) {
            for (File res : provider.getResDirectories()) {
                // Scan resource directory
                if (res.isDirectory()) {
                    recordInactiveXmlResources(res);
                }
            }
            for (File file : provider.getJavaDirectories()) {
                // Scan Java directory
                if (file.isDirectory()) {
                    recordInactiveJavaReferences(file);
                }
            }
        }
    }

    /**
     * Given an error message created by this lint check, return the corresponding
     * resource field name for the resource that is described as unused.
     * (Intended to support quickfix implementations for this lint check.)
     *
     * @param errorMessage the error message originally produced by this detector
     * @param format the format of the error message
     * @return the corresponding resource field name, e.g. {@code R.string.foo}
     */
    @Nullable
    public static String getUnusedResource(@NonNull String errorMessage, @NonNull TextFormat format) {
        errorMessage = format.toText(errorMessage);
        return findSubstring(errorMessage, "The resource ", " appears ");
    }

    private static Issue getIssue(@NonNull Resource resource) {
        return  ISSUE ;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        mModel.context = context;
        try {
            mModel.visitBinaryResource(context.getResourceFolderType(), context.file);
        } finally {
            mModel.context = null;
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        mModel.context = mModel.xmlContext = context;
        try {
            mModel.visitXmlDocument(context.file, context.getResourceFolderType(),
                    document);
        } finally {
            mModel.context = mModel.xmlContext = null;
        }
    }

    // ---- Implements JavaScanner ----

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

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(@NonNull JavaContext context,
            @Nullable JavaElementVisitor visitor, @NonNull PsiElement node,
            @NonNull ResourceType type, @NonNull String name, boolean isFramework) {
        if (!isFramework) {
            ResourceUsageModel.markReachable(mModel.addResource(type, name, null));
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
                            ResourceUrl url = ResourceEvaluator.getResourceConstant(resolved);
                            if (url != null && !url.framework) {
                                Resource resource = mModel.addResource(url.type, url.name, null);
                                ResourceUsageModel.markReachable(resource);
                            }
                        }
                        super.visitReferenceExpression(expression);
                    }
                });
            } else {
                PsiElement resolved = statement.resolve();
                if (resolved instanceof PsiField) {
                    ResourceUrl url = ResourceEvaluator.getResourceConstant(resolved);
                    if (url != null && !url.framework) {
                        Resource resource = mModel.addResource(url.type, url.name, null);
                        ResourceUsageModel.markReachable(resource);
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
                PsiExpression [] ss = argumentList.getExpressions();
                String name = null;
                String type = null;

                for(PsiExpression e :ss){
                    String text =e.getText();
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
                    ResourceType resourceType = ResourceType.getEnum(type);
                    Resource resource = mModel.addResource(resourceType,name, null);
                    ResourceUsageModel.markReachable(resource);
                }


            }
        }

        private boolean mScannedForStaticImports;
    }

    private static class UnusedResourceDetectorUsageModel extends ResourceUsageModel {
        public XmlContext xmlContext;
        public Context context;
        public Set<Resource> unused = Sets.newHashSet();

        @NonNull
        @Override
        protected String readText(@NonNull File file) {
            if (context != null) {
                return context.getClient().readFile(file).toString();
            }
            try {
                return Files.toString(file, UTF_8);
            } catch (IOException e) {
                return ""; // Lint API
            }
        }

        @Override
        protected Resource declareResource(ResourceType type, String name, Node node) {
            Resource resource = super.declareResource(type, name, node);
            if (context != null) {
                resource.setDeclared(context.getProject().getReportIssues());
                if (context.getPhase() == 2 && unused.contains(resource)) {
                    if (xmlContext != null && xmlContext.getDriver().isSuppressed(xmlContext,
                            getIssue(resource), node)) {
                        resource.setKeep(true);
                    } else {
                        // For positions we try to use the name node rather than the
                        // whole declaration element
                        if (node == null || xmlContext == null) {
                            resource.recordLocation(Location.create(context.file));
                        } else {
                            if (node instanceof Element) {
                                Node attribute = ((Element) node).getAttributeNode(ATTR_NAME);
                                if (attribute != null) {
                                    node = attribute;
                                }
                            }
                            resource.recordLocation(xmlContext.getLocation(node));
                        }
                    }
                }

                if (type == ResourceType.RAW &&isKeepFile(name, xmlContext)) {
                    // Don't flag raw.keep: these are used for resource shrinking
                    // keep lists
                    //    https://developer.android.com/studio/build/shrink-code.html
                    resource.setReachable(true);
                }
            }

            return resource;
        }

        private static boolean isKeepFile(
          @NonNull String name,
          @Nullable XmlContext xmlContext) {
            if ("keep".equals(name)) {
                return true;
            }

            if (xmlContext != null && xmlContext.document != null) {
                Element element = xmlContext.document.getDocumentElement();
                if (element != null && element.getFirstChild() == null) {
                    NamedNodeMap attributes = element.getAttributes();
                    boolean found = false;
                    for (int i = 0, n = attributes.getLength(); i < n; i++) {
                        Node attr = attributes.item(i);
                        String nodeName = attr.getNodeName();
                        if (!nodeName.startsWith(XMLNS_PREFIX)
                                && !nodeName.startsWith(TOOLS_PREFIX)) {
                            return false;
                        } else if (nodeName.endsWith(ATTR_SHRINK_MODE) ||
                                nodeName.endsWith(ATTR_DISCARD) ||
                                nodeName.endsWith(ATTR_KEEP)) {
                            found = true;
                        }
                    }

                    return found;
                }
            }


            return false;
        }
    }
}
