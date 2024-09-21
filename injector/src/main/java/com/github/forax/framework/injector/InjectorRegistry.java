package com.github.forax.framework.injector;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

public final class InjectorRegistry {

  private final HashMap<Class<?>, Object> instancesQ1 = new HashMap<>();
  private final HashMap<Class<?>, Supplier<?>> suppliers = new HashMap<>();

  void registerInstanceQ1(Class<?> type, Object obj) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(obj);
    var res = instancesQ1.putIfAbsent(type, obj);
    if (res != null) {
      throw new IllegalStateException();
    }
  }

  <T> T lookupInstanceQ1(Class<T> type) {
    Objects.requireNonNull(type);
    var obj = instancesQ1.get(type);
    if (obj == null) {
      return null;
    }
    if (!type.isInstance(obj)) {
      throw new IllegalStateException();
    }
    return type.cast(obj);
  }

  void registerInstance(Class<?> type, Object obj) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(obj);
    var res = suppliers.putIfAbsent(type, ()-> obj);
    if (res != null) {
      throw new IllegalStateException();
    }
  }

  <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);
    var res = suppliers.get(type);
    if (res == null) {
      throw new IllegalStateException();
    }
    var obj = res.get();
    if (obj == null) {
      throw new IllegalStateException();
    }
    if (!type.isInstance(obj)) {
      throw new IllegalStateException();
    }
    return type.cast(obj);
  }

  public <T> void registerProvider(Class<T> type, Supplier<T> sup) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(sup);
    var res = suppliers.putIfAbsent(type, sup);
    if (res != null) {
      throw new IllegalStateException();
    }
  }


  private Constructor<?> findInjectableConstructor(Class<?> type){
    var constructors = Arrays.stream(type.getConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Inject.class)).toList();
    return switch (constructors.size()){
      case 0 -> Utils.defaultConstructor(type);
      case 1 -> constructors.getFirst();
      default -> throw new IllegalStateException();
    };
  } 
  static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
    BeanInfo bean = Utils.beanInfo(type);
    /*var tmpPrpertyDesc = bean.getPropertyDescriptors();
    var toReturn = new ArrayList<PropertyDescriptor>();
    for(var e : tmpPrpertyDesc){
      var method = e.getWriteMethod();
      if(method != null) {
        var annotation = method.getAnnotation(Inject.class);
        if (annotation != null) {
          toReturn.add(e);
        }
      }
    }
    return Collections.unmodifiableList(toReturn);*/
    return Arrays.stream(bean.getPropertyDescriptors())
            .filter(p -> !p.getName().equals("class"))
            .filter(p -> {
              var setter = p.getWriteMethod();
              return setter != null && setter.getAnnotation(Inject.class) != null;
            }).toList();
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type, "type cannot be null");
    Objects.requireNonNull(providerClass, "providerClass cannot be null");
    var constructor = findInjectableConstructor(providerClass);
    var properties = findInjectableProperties(providerClass);
    var parameterTypes = constructor.getParameterTypes();
    registerProvider(type, () -> {
      var args = Arrays.stream(parameterTypes)
              .map(this::lookupInstance)
              .toArray();
      var instance = Utils.newInstance(constructor, args);
      for (var property : properties) {
        var propertyType = property.getPropertyType();
        var value = lookupInstance(propertyType);
        Utils.invokeMethod(instance, property.getWriteMethod(), value);
      }
      return providerClass.cast(instance);
    });

  }
}
