package social.nickrest.util;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import lombok.experimental.UtilityClass;
import social.nickrest.serialize.Serializer;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

@UtilityClass
public class ResourceUtil {

    public static InputStream getResourceAsStream(Class<?> classToPullFrom, String resource) {
        return classToPullFrom.getClassLoader().getResourceAsStream(resource);
    }

    public static JsonObject getJsonObject(InputStream stream) {
        JsonReader reader = new JsonReader(new BufferedReader(new InputStreamReader(stream)));
        return Serializer.GSON.fromJson(reader, JsonObject.class);
    }

    public static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static List<Class<?>> getClassesForPackageIgnoreExceptions(String packageName) {
        try {
            return getClassesForPackage(packageName);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static List<Class<?>> getClassesForPackage(String packageName) throws ClassNotFoundException {
        ArrayList<File> directories = new ArrayList<File>();
        try {
            ClassLoader cld = Thread.currentThread().getContextClassLoader();

            if (cld == null) {
                throw new ClassNotFoundException("Can't get class loader.");
            }

            String path = packageName.replace('.', '/');
            Enumeration<URL> resources = cld.getResources(path);
            while (resources.hasMoreElements()) {
                directories.add(new File(URLDecoder.decode(resources.nextElement().getPath(), StandardCharsets.UTF_8)));
            }
        } catch (NullPointerException | IOException e) {
            throw new RuntimeException("Something went wrong while getting all resources for " + packageName, e);
        }

        ArrayList<Class<?>> classes = new ArrayList<>();
        for (File directory : directories) {
            if (directory.exists()) {
                String[] files = directory.list();

                if(files == null) continue;

                for (String file : files) {
                    if (!file.endsWith(".class")) continue;

                    try {
                        classes.add(Class.forName(packageName + '.' + file.substring(0, file.length() - 6)));
                    } catch (NoClassDefFoundError ignored) {}
                }
            } else {
                throw new ClassNotFoundException(packageName + " (" + directory.getPath() + ") does not appear to be a valid package");
            }
        }
        return classes;
    }
}
