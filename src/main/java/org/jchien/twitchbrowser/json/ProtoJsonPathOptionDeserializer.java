package org.jchien.twitchbrowser.json;

import com.google.gson.*;
import com.google.protobuf.*;
import org.jchien.twitchbrowser.TwitchBrowserProto;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jchien
 */
public class ProtoJsonPathOptionDeserializer<E extends AbstractMessage> implements JsonDeserializer<E> {
    private final Class<? extends AbstractMessage> klazz;

    private final List<JsonPathAndFieldDescriptor> jsonPathsAndFieldDescriptors;

    public ProtoJsonPathOptionDeserializer(Class<? extends AbstractMessage> klazz) {
        this.klazz = klazz;

        final Descriptors.Descriptor msgDescriptor = invokeStaticOrDie("getDescriptor");
        final GeneratedMessage.GeneratedExtension<DescriptorProtos.FieldOptions, List<String>> jsonPathExt = TwitchBrowserProto.jsonPath;
        final List<Descriptors.FieldDescriptor> fieldDescriptors = msgDescriptor.getFields();

        final List<JsonPathAndFieldDescriptor> jsonPathsAndFieldDescriptors = new ArrayList<>();
        for (Descriptors.FieldDescriptor fd : fieldDescriptors) {
            List<String> partsList = fd.getOptions().getExtension(jsonPathExt);
            jsonPathsAndFieldDescriptors.add(new JsonPathAndFieldDescriptor(partsList, fd));
        }
        this.jsonPathsAndFieldDescriptors = jsonPathsAndFieldDescriptors;
    }

    private class JsonPathAndFieldDescriptor {
        final List<String> jsonPath;
        final Descriptors.FieldDescriptor fieldDescriptor;

        public JsonPathAndFieldDescriptor(List<String> jsonPath, Descriptors.FieldDescriptor fieldDescriptor) {
            this.jsonPath = jsonPath;
            this.fieldDescriptor = fieldDescriptor;
        }
    }

    private <T> T invokeStaticOrDie(String methodName) {
        try {
            return (T) klazz.getMethod(methodName).invoke(null);
        } catch (IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            // blow up for now
            throw new RuntimeException("unable to call " + methodName + "() on " + klazz.getName(), e);
        }
    }

    @Override
    public E deserialize(JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        Message.Builder builder = invokeStaticOrDie("newBuilderForType");
        final JsonObject root = json.getAsJsonObject();
        for (JsonPathAndFieldDescriptor jpafd : jsonPathsAndFieldDescriptors) {
            parseValue(builder, jpafd.fieldDescriptor, jpafd.jsonPath, root);
        }
        return (E) builder.build();
    }

    private void parseValue(final Message.Builder builder,
                            final Descriptors.FieldDescriptor fieldDescriptor,
                            final List<String> jsonPath,
                            final JsonObject root) {
        JsonObject o = root;
        for (int i=0 ; i < jsonPath.size() - 1; i++) {
            o = o.getAsJsonObject(jsonPath.get(i));
        }
        final JsonPrimitive p = o.getAsJsonPrimitive(jsonPath.get(jsonPath.size()-1));

        // interpret value using appropriate method based on field type
        Descriptors.FieldDescriptor.JavaType fieldType = fieldDescriptor.getJavaType();
        switch (fieldType) {
            case INT:
                builder.setField(fieldDescriptor, p.getAsInt());
                break;
            case LONG:
                builder.setField(fieldDescriptor, p.getAsLong());
                break;
            case STRING:
                builder.setField(fieldDescriptor, p.getAsString());
                break;
            default:
                throw new RuntimeException("unsupported JavaType: " + fieldType + " for field " + fieldDescriptor.getName());
        }
    }
}
