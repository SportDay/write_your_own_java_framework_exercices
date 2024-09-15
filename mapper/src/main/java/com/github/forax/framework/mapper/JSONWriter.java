package com.github.forax.framework.mapper;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JSONWriter {

  private static final ClassValue<PropertyDescriptor[]> DATA_CLASS_VALUE_Q3 = new ClassValue<>() {
    @Override
    protected PropertyDescriptor[] computeValue(Class<?> type) {
      BeanInfo beanInfo = Utils.beanInfo(type);
      return beanInfo.getPropertyDescriptors();
    }
  };
  private final HashMap<Class<?>, Generator> sub = new HashMap<>();

  private static List<PropertyDescriptor> beanProperties(Class<?> type) {
    var beanInfo = Utils.beanInfo(type);
    return Arrays.stream(beanInfo.getPropertyDescriptors())
            .filter(property -> !property.getName().equals("class"))
            .toList();
  }

  private static List<PropertyDescriptor> recordProperties(Class<?> type) {
    return Arrays.stream(type.getRecordComponents()).map(component -> {
              try {
                return new PropertyDescriptor(component.getName(), component.getAccessor(), null);
              } catch (IntrospectionException e) {
                throw new RuntimeException(e);
              }
            })
            .toList();
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

  public <T> void configure(Class<? extends T> oClass, Function<? super T, String> func) {
    Objects.requireNonNull(oClass);
    Objects.requireNonNull(func);

    var res = sub.putIfAbsent(oClass, (writer, bean) -> func.apply(oClass.cast(bean)));

    if (res != null) {
      throw new IllegalStateException();
    }
  }

  public String toJSONQ5(Object o) {
    return switch (o) {
      case null -> "null";
      case String str -> "\"" + str + "\"";
      case Boolean b -> String.valueOf(b);
      case Integer i -> String.valueOf(i);
      case Double d -> String.valueOf(d);
      default -> {
        var type = o.getClass();
        var gen = sub.get(type);
        if (gen == null) {
          gen = DATA_CLASS_VALUE_Q4.get(type);
        }
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
        var type = o.getClass();
        var gen = sub.get(type);
        if (gen == null) {
          gen = DATA_CLASS_VALUE_Q4.get(type);
        }
        yield gen.generate(this, o);
      }
    };
  }


  @FunctionalInterface
  private interface Generator {
    String generate(JSONWriter writer, Object bean);
  }

  private static final ClassValue<Generator> DATA_CLASS_VALUE_Q4 = new ClassValue<>() {
    @Override
    protected Generator computeValue(Class<?> type) {
      var properties = type.isRecord() ? recordProperties(type) : beanProperties(type);

      var generators = properties.stream()
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


}
