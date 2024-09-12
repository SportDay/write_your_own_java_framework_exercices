package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JSONWriter {

    @FunctionalInterface
    private interface Generator {
        String generate(JSONWriter writer, Object bean);
    }

    public String toJSONQ1(Object o) {
        return switch (o) {
            case null -> "null";
            case Boolean b -> String.valueOf(b);
            case Integer i -> String.valueOf(i);
            case Double d -> String.valueOf(d);
            case String str -> "\"" + str + "\"";
            default -> throw new IllegalArgumentException("" + o);
        };
    }

    public String toJSONQ2(Object o) {
        return switch (o) {
            case null -> "null";
            case String str -> "\"" + str + "\"";
            case Boolean b -> String.valueOf(b);
            case Integer i -> String.valueOf(i);
            case Double d -> String.valueOf(d);
            default -> {
                BeanInfo beanInfo = Utils.beanInfo(o.getClass());

                PropertyDescriptor[] properties = beanInfo.getPropertyDescriptors();

                yield Arrays.stream(properties).filter(e -> !e.getName().equals("class"))
                        .map(e -> '"' + e.getName() + "\": " + toJSONQ2(Utils.invokeMethod(o, e.getReadMethod())))
                        .collect(Collectors.joining(", ", "{", "}"));
            }
        };
    }

    public String toJSONQ6WithAnnotation(Object o) {
        return switch (o) {
            case null -> "null";
            case String str -> "\"" + str + "\"";
            case Boolean b -> String.valueOf(b);
            case Integer i -> String.valueOf(i);
            case Double d -> String.valueOf(d);
            default -> {
                BeanInfo beanInfo = Utils.beanInfo(o.getClass());

                PropertyDescriptor[] properties = beanInfo.getPropertyDescriptors();

                yield Arrays.stream(properties).filter(e -> !e.getName().equals("class"))
                        .map(e -> {
                            var getter = e.getReadMethod();
                            var annotation = getter.getAnnotation(JSONProperty.class);
                            var val = annotation == null ? e.getName() : annotation.value();
                            return '"' + val + "\": " + toJSONQ6WithAnnotation(Utils.invokeMethod(o, getter));
                        })
                        .collect(Collectors.joining(", ", "{", "}"));
            }
        };
    }

    private static final ClassValue<PropertyDescriptor[]> DATA_CLASS_VALUE_Q3 = new ClassValue<>() {
        @Override
        protected PropertyDescriptor[] computeValue(Class<?> type) {
            BeanInfo beanInfo = Utils.beanInfo(type);
            return beanInfo.getPropertyDescriptors();
        }
    };

    public String toJSONQ3(Object o) {
        return switch (o) {
            case null -> "null";
            case String str -> "\"" + str + "\"";
            case Boolean b -> String.valueOf(b);
            case Integer i -> String.valueOf(i);
            case Double d -> String.valueOf(d);
            default -> {

                PropertyDescriptor[] properties = DATA_CLASS_VALUE_Q3.get(o.getClass());

                yield Arrays.stream(properties).filter(e -> !e.getName().equals("class"))
                        .map(e -> {
                            var getter = e.getReadMethod();
                            var annotation = getter.getAnnotation(JSONProperty.class);
                            var val = annotation == null ? e.getName() : annotation.value();
                            return '"' + val + "\": " + toJSONQ3(Utils.invokeMethod(o, getter));
                        })
                        .collect(Collectors.joining(", ", "{", "}"));
            }
        };
    }

    private static final ClassValue<Generator> DATA_CLASS_VALUE_Q4 = new ClassValue<>() {
        @Override
        protected Generator computeValue(Class<?> type) {
            var generators = Arrays.stream(Utils.beanInfo(type).getPropertyDescriptors())
                    .filter(p -> !p.getName().equals("class"))
                    .<Generator>map(e -> {
                        var getter = e.getReadMethod();
                        var annotation = getter.getAnnotation(JSONProperty.class);
                        var val = annotation == null ? e.getName() : annotation.value();
                        var prefix = '"' + val + "\": ";
                        return (writer, bean) -> prefix + writer.toJSON(Utils.invokeMethod(bean, getter));
                    }).toList();

            return (writer, bean) -> generators.stream().map(generator -> generator.generate(writer, bean))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
    };

    public String toJSONQ4(Object o) {
        return switch (o) {
            case null -> "null";
            case String str -> "\"" + str + "\"";
            case Boolean b -> String.valueOf(b);
            case Integer i -> String.valueOf(i);
            case Double d -> String.valueOf(d);
            default -> {
                Generator gen = DATA_CLASS_VALUE_Q4.get(o.getClass());
                yield gen.generate(this, o);
            }
        };
    }

    public String toJSON(Object o) {
        return switch (o) {
            case null -> "null";
            case String str -> "\"" + str + "\"";
            case Boolean b -> String.valueOf(b);
            case Integer i -> String.valueOf(i);
            case Double d -> String.valueOf(d);
            default -> {
                Generator gen = DATA_CLASS_VALUE_Q4.get(o.getClass());
                yield gen.generate(this, o);
            }
        };
    }


    private static final HashMap<Class<?>, Object> sub = new HashMap<>();

    public void configure(Class<?> oClass, Function<?, ?> func) {

    }

}
