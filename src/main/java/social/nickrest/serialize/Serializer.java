package social.nickrest.serialize;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import social.nickrest.serialize.annotate.DontSerialize;
import social.nickrest.util.ResourceUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

@RequiredArgsConstructor
@Getter
public class Serializer {

    public static Class<?>[] notJsonTree = {
            Integer.class, Long.class, Double.class, Float.class, Boolean.class, Byte.class, Short.class, Character.class, String.class
    };

    private final Map<String, Boolean> arguments = new HashMap<>();
    private final List<Class<?>> classes = new ArrayList<>();

    public static final Gson GSON = new Gson();

    private final Class<?> classToPullFrom;
    private final JsonObject jsonObject;
    private final File toSaveTo;

    public void serialize(Object object) {
        serialize(object, null);
    }

    public void deserialize(Object object) {
        deserialize(object, null, true);
    }

    public void deserialize(Object object, String location) {
        deserialize(object, location, true);
    }

    public void deserialize(Object object, boolean errorIfMissing) {
        deserialize(object, null, errorIfMissing);
    }

    /**
     * saves the serialized object to file
     *
     * @param object for class to serialize
     * @param location for nonStatic classes make this null if you want it to be static
     */
    public void serialize(Object object, String location) {
        if(!classes.contains(object.getClass())) {
            throw new RuntimeException("The class " + object.getClass().getName() + " cannot be serialized.");
        }

        if(location == null) {
            location = object.getClass().getName().replace(".", "/") + ".json";
        }

        if(toSaveTo.exists() && !toSaveTo.isDirectory()) {
            throw new RuntimeException("The file " + toSaveTo.getName() + " is not a directory.");
        }

        if(!toSaveTo.exists() && !toSaveTo.mkdirs()) {
            throw new RuntimeException("The directory " + toSaveTo.getName() + " could not be created.");
        }

        File file = new File(toSaveTo, location);

        if(file.exists() && !file.delete()) {
            throw new RuntimeException("The file " + file.getName() + " could not be deleted.");
        }

        String[] directories = location.split("/");
        StringBuilder locationPath = new StringBuilder(toSaveTo.getAbsolutePath() + File.separator);
        for(int i = 0; i < directories.length - 1; i++) {
            String directory = directories[i];
            String FILE_EXTENSION_REGEX = "\\.[a-zA-Z0-9]+";
            if(directory.matches(FILE_EXTENSION_REGEX)) {
                continue;
            }

            File directoryFile = new File(locationPath + directory);
            if(!directoryFile.exists() && !directoryFile.mkdirs()) {
                throw new RuntimeException("The directory " + directory + " could not be created.");
            }

            locationPath.append(directory).append("\\");
        }

        try {
            FileWriter writer = new FileWriter(locationPath.toString() + File.separator + object.getClass().getSimpleName() + ".json");
            JsonObject objectJson = new JsonObject();

            Field[] fields = object.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                if (field.isAnnotationPresent(DontSerialize.class) && !arguments.getOrDefault("serialize-ignored-fields", false)) {
                    continue;
                }

                try {
                    if (Arrays.asList(notJsonTree).contains(field.getType())) {
                        JsonElement element = GSON.toJsonTree(field.get(object));
                        objectJson.add(field.getName(), element);
                    } else {
                        objectJson.add(field.getName(), objectFromObj(field.get(object)));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Something went wrong while serializing the field " + field.getName());
                }
            }

            writer.write(objectJson.toString());
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong while saving the file " + file.getName(), e);
        }
    }

    private JsonObject objectFromObj(Object object) {
        JsonObject objectJson = new JsonObject();

        Field[] fields = object.getClass().getDeclaredFields();
        for(Field field : fields) {
            field.setAccessible(true);
            if(field.isAnnotationPresent(DontSerialize.class) && !arguments.getOrDefault("serialize-ignored-fields", false)) {
                continue;
            }

            try {
                if(Arrays.asList(notJsonTree).contains(field.getType())) {
                    JsonElement element = GSON.toJsonTree(field.get(object));
                    objectJson.add(field.getName(), element);
                } else {
                    JsonObject fieldJson = new JsonObject();
                    Field[] fieldFields = field.getType().getDeclaredFields();
                    for(Field fieldField : fieldFields) {
                        fieldField.setAccessible(true);
                        if(fieldField.isAnnotationPresent(DontSerialize.class) && !arguments.getOrDefault("serialize-ignored-fields", false)) {
                            continue;
                        }

                        try {
                            if(Arrays.asList(notJsonTree).contains(fieldField.getType())) {
                                JsonElement element = GSON.toJsonTree(fieldField.get(field.get(object)));
                                fieldJson.add(fieldField.getName(), element);
                            } else {
                                try {
                                    fieldJson.add(fieldField.getName(), objectFromObj(fieldField.get(field.get(object))));
                                } catch (IllegalAccessException e) {
                                    throw new RuntimeException("Something went wrong while serializing the field " + fieldField.getName());
                                }
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Something went wrong while serializing the field " + fieldField.getName());
                        }
                    }

                    objectJson.add(field.getName(), fieldJson);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Something went wrong while serializing the field " + field.getName());
            }
        }

        return objectJson;
    }

    /**
     * Loads a serialized object from a file
     *
     * @param object for class to deserialize
     *               This is the object that the data will be deserialized to.
     *               The data will be deserialized to the object's fields.
     * @param location for nonStatic classes make this null if you want it to be static
     *                 This is the location of the file to deserialize.
     *                This is the path to the file from the resources' folder.
     * @param errorIfMissing Whether to throw an error if the file is missing.
     * **/
    public void deserialize(Object object, String location, boolean errorIfMissing) {
        if(!classes.contains(object.getClass())) {
            throw new RuntimeException("The class " + object.getClass().getName() + " cannot be deserialized.");
        }

        if(location == null) {
            location = object.getClass().getName().replace(".", "/") + ".json";
        }

        File file = new File(toSaveTo, location);

        if(!file.exists() && errorIfMissing) {
            throw new RuntimeException("The file " + file.getName() + " does not exist.");
        } else if(!file.exists()) {
            return;
        }

        try {
            Scanner scanner = new Scanner(file);
            StringBuilder builder = new StringBuilder();

            while(scanner.hasNextLine()) {
                builder.append(scanner.nextLine());
            }

            scanner.close();

            JsonObject objectJson = GSON.fromJson(builder.toString(), JsonObject.class);

            Field[] fields = object.getClass().getDeclaredFields();
            for(Field field : fields) {
                field.setAccessible(true);
                if(field.isAnnotationPresent(DontSerialize.class) && !arguments.getOrDefault("deserialize-ignored-fields", false)) {
                    continue;
                }

                if(objectJson.has(field.getName())) {
                    try {
                        field.set(object, GSON.fromJson(objectJson.get(field.getName()), field.getType()));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Something went wrong while deserializing the field " + field.getName());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Something went wrong while deserializing the file " + file.getName());
        }
    }

    public void load() {
        if(jsonObject.has("arguments")) {
            JsonObject argumentsObj = jsonObject.getAsJsonObject("arguments");

            for(Map.Entry<String, JsonElement> entry : argumentsObj.entrySet()) {
                JsonElement element = entry.getValue();

                if(element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
                    arguments.put(entry.getKey(), element.getAsBoolean());
                }
            }
        }

        if(jsonObject.has("classes")) {
            for(JsonElement element : jsonObject.getAsJsonArray("classes")) {

                if(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    String className = element.getAsString();

                    if(className.endsWith(".*")) {
                        List<Class<?>> classes = ResourceUtil.getClassesForPackageIgnoreExceptions(className.substring(0, className.length() - 2));

                        if(classes == null) {
                            throw new RuntimeException("Something went wrong while getting all classes for " + className);
                        }

                        this.classes.addAll(classes);
                        continue;
                    }

                    Class<?> clazz = ResourceUtil.getClass(className);
                    if(clazz != null) {
                        classes.add(clazz);
                    }
                }

            }
        }
    }

    /**
     * Loads a serializer from a file location.
     *
     * @param clazzToPullFrom The class to pull the resource from.
     *                        This is used to get the class loader, so it knows where to get the resource from.
     * @param fileLocation The location of the file to load.
     *                     This is the path to the file from the resources' folder.
     * @param toSaveTo The file to save the serializer to.
     *                 This is the file that the serializer will save to when the save method is called.
     * @param load Whether to load the serializer.
     *             if true, the serializer will load the data from the json object.
     *             if false, the serializer won't load till it is called.
     * */
    public static Serializer load(Class<?> clazzToPullFrom, String fileLocation, File toSaveTo, boolean load) {
        InputStream stream = ResourceUtil.getResourceAsStream(clazzToPullFrom, fileLocation);
        JsonObject jsonObject = ResourceUtil.getJsonObject(stream);

        Serializer serializer = new Serializer(clazzToPullFrom, jsonObject, toSaveTo);
        if(load) {
            serializer.load();
        }

        return serializer;
    }

}