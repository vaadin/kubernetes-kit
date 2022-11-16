package com.vaadin.kubernetes.starter.sessiontracker.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.DebugMode;
import com.vaadin.kubernetes.starter.sessiontracker.serialization.debug.Track;

import static org.mockito.Mockito.mock;

@Disabled
class MyTest {

    @Test
    void testMe5() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientHandler handler = new DgbTrHa();

        Map<String, Object> data = Map.of("OBJ1", new SerializableParent(),
                "OBJ2", new SerializableChild(), "OBJ3",
                new SerializableHolder(new SerializableHolder(
                        new SerializableHolder(new SerializableParent()))),
                "OBJ4", Stream
                        .of(new SerializableParent(), new NotSerializable(),
                                new SerializableChild(),
                                Stream.of(new SerializableParent(),
                                        new SerializableChild()).toArray())
                        .collect(Collectors.toList()),
                "OBJ5", new ToStringThrows(), "OBJ6",
                new ClassCastSimulation());
        TransientInjectableObjectOutputStream tos = TransientInjectableObjectOutputStream.newInstance(os,
                handler);
        tos.writeWithTransients(data);

        TransientInjectableObjectInputStream tis = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler);
        Map<String, Object> out = tis.readWithTransients();
        System.out.println(out);

    }

    @Test
    void testMe3() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        TransientHandler handler = new DgbTrHa();

        Map<String, Object> data = Map.of("OBJ1", new SerializableParent(),
                "OBJ2", new SerializableChild(), "OBJ3",
                new SerializableHolder(new SerializableHolder(
                        new SerializableHolder(new SerializableParent()))),
                "OBJ4", Stream
                        .of(new SerializableParent(), new NotSerializable(),
                                new SerializableChild(),
                                Stream.of(new SerializableParent(),
                                        new SerializableChild()).toArray())
                        .collect(Collectors.toList()),
                "OBJ5", new ToStringThrows(), "OBJ6",
                new ClassCastSimulation());
        try (TransientInjectableObjectOutputStream tos = TransientInjectableObjectOutputStream
                .newInstance(os, handler)) {
            tos.writeWithTransients(data);
        }

        try (TransientInjectableObjectInputStream tis = new TransientInjectableObjectInputStream(
                new ByteArrayInputStream(os.toByteArray()), handler)) {
            Map<String, Object> out = tis.readWithTransients();
            System.out.println(out);
        }

    }

    private static class DgbTrHa
            implements TransientHandler, DebugMode {

        @Override
        public List<TransientDescriptor> inspect(Object object) {
            return Collections.emptyList();
        }

        @Override
        public void inject(Object object,
                List<TransientDescriptor> transients) {

        }

        @Override
        public Object onSerialize(Object object, Track track) {
            try {
                object.toString();
            } catch (Exception ex) {
                System.out
                        .println("======================== TOSTRING throws for "
                                + object.getClass());
                // return null;
                return new FailingToStringReplacer((Serializable) object);
            }
            return object;
        }

        @Override
        public Optional<Serializable> onNotSerializableFound(Object object) {
            return Optional.of(new Null(object));
        }

        @Override
        public Object onDeserialized(Object object, Track track) {
            if (object instanceof Track) {
                System.out.println("=================== Tracked "
                        + ((Track) object).id + ", " + object.getClass());
            }
            return object;
        }
    }

    static class Null implements Serializable {

        private final String className;

        public Null(Object obj) {
            this.className = obj.getClass().getName();
        }

        private Object readResolve() {
            return null;
        }
    }

    static class FailingToStringReplacer implements Serializable {
        private transient Serializable target;
        private final Class<?> targetClass;
        private final Map<String, Object> targetFields;

        public FailingToStringReplacer(Serializable target) {
            this.target = target;
            this.targetClass = target.getClass();
            this.targetFields = new LinkedHashMap<>();
        }

        @Override
        public String toString() {
            return "Replacement for " + target.getClass()
                    + " because of throwing toString()";
        }

        private void writeObject(ObjectOutputStream os) throws IOException {
            ObjectStreamClass lookup = ObjectStreamClass.lookup(targetClass);
            ObjectStreamField[] fields = lookup.getFields();
            for (ObjectStreamField f : fields) {

                try {
                    Object val = findVarHandle(targetClass, f).get(target);
                    targetFields.put(f.getName(), val);
                } catch (Exception ex) {
                    System.out.println(
                            "cannot get value for field " + f.toString());
                }

            }
            os.defaultWriteObject();
        }

        private static VarHandle findVarHandle(Class<?> targetClass,
                ObjectStreamField f)
                throws NoSuchFieldException, IllegalAccessException {
            return MethodHandles
                    .privateLookupIn(targetClass, MethodHandles.lookup())
                    .findVarHandle(targetClass, f.getName(), f.getType());
        }

        private void readObject(ObjectInputStream is)
                throws IOException, ClassNotFoundException {
            is.defaultReadObject();
            ObjectStreamClass descr = ObjectStreamClass.lookup(targetClass);
            target = (Serializable) newInstance(descr);
            for (ObjectStreamField f : descr.getFields()) {
                try {
                    if (targetFields.containsKey(f.getName())) {
                        findVarHandle(targetClass, f)
                                .set(targetFields.get(f.getName()));
                    }
                } catch (Exception ex) {
                    System.out.println(
                            "cannot set value for field " + f.toString());
                }
            }
        }

        private Object readResolve() {
            return target;
        }

        private Object newInstance(ObjectStreamClass descr) {
            try {
                return MethodHandles
                        .privateLookupIn(ObjectStreamClass.class,
                                MethodHandles.lookup())
                        .findVirtual(ObjectStreamClass.class, "newInstance",
                                MethodType.methodType(Object.class))
                        .invoke(descr);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Test
    void testMe() throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException {
        OutputStream os = new ByteArrayOutputStream();
        TransientHandler handler = mock(TransientHandler.class);
        TransientInjectableObjectOutputStream ifs = new ByteBuddy()
                .subclass(TransientInjectableObjectOutputStream.class,
                        ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
                .implement(Ifs.class)
                .defineMethod("getDepth", int.class, Modifier.PUBLIC)
                .intercept(FieldAccessor.ofField("depth")).make()
                .load(TransientInjectableObjectOutputStream.class
                        .getClassLoader())
                .getLoaded().getDeclaredConstructor(OutputStream.class,
                        TransientHandler.class)
                .newInstance(os, handler);

        int x = ((Ifs) ifs).getDepth();
        System.out.println(x);
    }

    static {
        /*
         * ByteBuddyAgent.install(); new ByteBuddy() .rebase(OutputStream.class)
         * .method(ElementMatchers.isDeclaredBy(ObjectOutputStream.class)
         * .and(ElementMatchers.isPrivate()) )
         * .intercept(MethodDelegation.to(GreetingInterceptor.class)) .make()
         * .load(TransientInjectableObjectOutputStream.class.getClassLoader(),
         * ClassReloadingStrategy.fromInstalledAgent());
         * 
         */
    }

    @Test
    public void testMe2() throws IOException {
        OutputStream os = new ByteArrayOutputStream();
        TransientHandler handler = mock(TransientHandler.class);

        TransientInjectableObjectOutputStream t = TransientInjectableObjectOutputStream
                .newInstance(os, handler);
        t.writeWithTransients("XXX");
    }

    @Test
    void testMe4() throws Exception {
        OutputStream os = new ByteArrayOutputStream();
        TransientHandler handler = mock(TransientHandler.class);
        TransientInjectableObjectOutputStream ifs = new ByteBuddy()
                .subclass(TransientInjectableObjectOutputStream.class,
                        ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
                .method(ElementMatchers.named("writeObject0").and(
                        ElementMatchers.isDeclaredBy(ObjectOutputStream.class)))
                .intercept(MethodDelegation.to(GreetingInterceptor.class))
                .make()
                .load(TransientInjectableObjectOutputStream.class
                        .getClassLoader())
                .getLoaded().getDeclaredConstructor(OutputStream.class,
                        TransientHandler.class)
                .newInstance(os, handler);

        ifs.writeWithTransients("TEST");

    }

    public static class GreetingInterceptor {

        // @RuntimeType
        public static void intercept(@SuperCall Callable<?> zuper,
                @AllArguments(AllArguments.Assignment.SLACK) Object... args)
                throws Exception {
            System.out.println("Hello ");
            zuper.call();
        }
    }

    public interface Ifs {
        int getDepth();
    }

    public static class TestImpl {
        public static void onDepth(@FieldValue("inspector") Object dd) {
            System.out.println("XXXXXXXXXXXXXxx " + dd);
        }
    }

    private static class SerializableParent implements Serializable {
        private SerializableChild child = new SerializableChild();
    }

    private static class SerializableChild implements Serializable {
        private String data = "TEST";
    }

    private static class SerializableHolder implements Serializable {
        private final Serializable data;

        public SerializableHolder(Serializable data) {
            this.data = data;
        }
    }

    private static class NotSerializable {

    }

    private static class ChildNotSerializable implements Serializable {
        private NotSerializable data = new NotSerializable();
    }

    private static class DeepNested implements Serializable {

        private final ChildNotSerializable root = new ChildNotSerializable();
        private final DeepNested.Inner inner = new DeepNested.Inner();
        private final DeepNested.StaticInner staticInner = new DeepNested.StaticInner();
        private final List<Object> collection = new ArrayList<>(
                List.of(new DeepNested.Inner(), new DeepNested.StaticInner(),
                        new ChildNotSerializable(), new NotSerializable()));

        class Inner implements Serializable {
            private NotSerializable staticInner = new NotSerializable();
        }

        static class StaticInner implements Serializable {
            private NotSerializable staticInner1 = new NotSerializable();

            private NotSerializable staticInner2 = null;

            private NotSerializable staticInner3 = new NotSerializable();
        }

    }

    private static class DeserializationFailure implements Serializable {

        private void readObject(ObjectInputStream is) {
            throw new RuntimeException("Simulate deserialization error");
        }
    }

    private static class ChildDeserializationFailure implements Serializable {
        private DeserializationFailure data = new DeserializationFailure();
    }

    private static class ToStringThrows implements Serializable {
        String x = null;

        @Override
        public String toString() {
            return "Booom! " + x.length();
        }
    }

    private static class ClassCastSimulation implements Serializable {

        private ClassCastSimulationChild child = new ClassCastSimulationChild();
    }

    private static class ClassCastSimulationChild implements Serializable {

        private Object readResolve() {
            return new Object();
        }
    }

    private static class SerializableLambdaSimulation implements Serializable {

    }

}
