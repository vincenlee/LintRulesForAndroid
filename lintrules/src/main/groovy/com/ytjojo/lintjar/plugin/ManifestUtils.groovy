package com.ytjojo.lintjar.plugin

import org.apache.commons.io.IOUtils

import java.util.jar.JarEntry
import java.util.jar.JarFile

public final class ManifestUtils {
 
    private static final String MANIFEST_DIRECTORY_LOCATION = "META-INF" + File.separator + "MANIFEST.MF";
 
    private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF"; 
 
    /**

     * Creates a {@link Reader} for the manifest in the supplied exploded JAR directory. 

     *  

     * @param directory the exploded JAR directory. 

     * @return the <code>Reader</code> or <code>null</code> if the manifest cannot be found. 
     */ 
    public static final Reader manifestReaderFromExplodedDirectory(File directory) { 
        if (directory == null || !directory.isDirectory()) { 
            throw new IllegalArgumentException("Must supply a valid directory"); 
        } 
        try { 
            File manifestFile = new File(directory.getAbsolutePath() + File.separator + MANIFEST_DIRECTORY_LOCATION); 
            if (manifestFile.exists()) { 
                return new FileReader(manifestFile);
            } else { 
                return null; 
            } 
        } catch (IOException e) {
            throw new RuntimeException("Unable to read MANIFEST for exploded directory '" + directory.getAbsolutePath() + "'.", e); 
        } 
    } 
 
    /**

     * Creates a {@link Reader} for the manifest in the supplied JAR file. 

     *  

     * @param file the JAR file. 

     * @return the <code>Reader</code> or <code>null</code> if the manifest cannot be found. 
     */ 
    public static final Reader manifestReaderFromJar(File file) { 
        JarFile jar = null; 
        try { 
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry(MANIFEST_ENTRY);
            if (entry != null) { 
                StringWriter writer = new StringWriter();
                IOUtils.copy(new InputStreamReader(jar.getInputStream(entry)), writer);
                jar.close(); 
                return new StringReader(writer.toString());
            } else { 
                return null; 
            } 
        } catch (Exception e) { 
            throw new RuntimeException("Cannot read MANIFEST.MF from jar '" + file.getAbsolutePath() + "'.", e); 
        } finally { 
            if (jar != null) { 
                try { 
                    jar.close(); 
                } catch (IOException ioe) { 
                    throw new RuntimeException("Failed to close jar '" + file.getAbsolutePath() + "'.", ioe); 
                } 
            } 
        } 
    } 
    public static final String getLintRegistryFromJar(File file) {
        JarFile jar = null;
        try {
            jar = new JarFile(file);
            JarEntry entry = jar.getJarEntry(MANIFEST_ENTRY);
            if (entry != null) {
                Properties properties = new Properties()
                properties.load(jar.getInputStream(entry))
                jar.close();
                return  properties.get("Lint-Registry")
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot read MANIFEST.MF from jar '" + file.getAbsolutePath() + "'.", e);
        } finally {
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException ioe) {
                    throw new RuntimeException("Failed to close jar '" + file.getAbsolutePath() + "'.", ioe);
                }
            }
        }
    }
}