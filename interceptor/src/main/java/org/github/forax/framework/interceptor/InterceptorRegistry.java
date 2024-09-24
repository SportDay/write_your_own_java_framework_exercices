package org.github.forax.framework.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class InterceptorRegistry {
  private final HashMap<Class<?>, Set<AroundAdvice>> adviceMap = new HashMap<>();

  private final HashMap<Class<?>, Set<Interceptor>> interceptorMap = new HashMap<>();

  private final HashMap<Method, Invocation> invocationCache = new HashMap<>();


  static Invocation getInvocation(List<Interceptor> interceptorList) {
    Invocation invocation = Utils::invokeMethod;
    for (var el : interceptorList.reversed()) {
      var newInvocation = invocation;
      invocation = (instance, method, args) -> el.intercept(instance, method, args, newInvocation);
    }
    return invocation;
  }

/*  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice aroundAdvice) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(aroundAdvice);
    adviceMap.computeIfAbsent(annotationClass, _ -> new LinkedHashSet<>()).add(aroundAdvice);
  }*/

  public void addAroundAdvice(Class<? extends Annotation> annotationClass, AroundAdvice aroundAdvice) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(aroundAdvice);

    addInterceptor(annotationClass, (instance, method, args, invocation) -> {
      aroundAdvice.before(instance, method, args);
      Object res = null;
      try {
        res = invocation.proceed(instance,method, args);
      } finally {
        aroundAdvice.after(instance, method, args, res);
      }
      return res;
    });
  }

  public void addInterceptor(Class<? extends Annotation> annotationClass, Interceptor interceptor) {
    Objects.requireNonNull(annotationClass);
    Objects.requireNonNull(interceptor);

    interceptorMap.computeIfAbsent(annotationClass, _ -> new LinkedHashSet<>()).add(interceptor);
    invocationCache.clear();
  }

  private Invocation getInvocationFromCache(Method method) {
    return invocationCache.computeIfAbsent(method, m -> getInvocation(findInterceptors(m)));
  }

/*  List<Interceptor> findInterceptors(Method method) {
    Objects.requireNonNull(method);
    return Arrays.stream(method.getAnnotations())
            .flatMap(annotation -> interceptorMap.getOrDefault(annotation.annotationType(), Set.of()).stream())
            .toList();
  }*/

  List<Interceptor> findInterceptors(Method method) {
    return Stream.of(
                    Arrays.stream(method.getDeclaringClass().getAnnotations()),
                    Arrays.stream(method.getAnnotations()),
                    Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream))
            .flatMap(s -> s)
            .map(Annotation::annotationType)
            .distinct()
            .flatMap(annotationType -> interceptorMap.getOrDefault(annotationType, Set.of()).stream())
            .toList();
  }

  List<AroundAdvice> findAdvices(Method method) {
    return Arrays.stream(method.getAnnotations())
            .flatMap(annotation -> adviceMap.getOrDefault(annotation.annotationType(), Set.of()).stream()).toList();
  }

/*  public <T> T createProxy(Class<? extends T> interfaceType, T instance) {
    Objects.requireNonNull(interfaceType);
    Objects.requireNonNull(instance);

    return interfaceType.cast(Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] {interfaceType},
            (ro, method, args) -> {
              var advices = findAdvices(method);
              for (var advice : advices) {
                advice.before(instance, method, args);
              }

              Object res = null;
              try {
                res = Utils.invokeMethod(instance, method, args);
              } finally {
                for (var advice : advices.reversed()) {
                  advice.after(instance, method, args, res);
                }
              }
              return res;
            }));
  }*/

  public <T> T createProxy(Class<? extends T> interfaceType, T instance) {
    Objects.requireNonNull(interfaceType);
    Objects.requireNonNull(instance);

    return interfaceType.cast(Proxy.newProxyInstance(interfaceType.getClassLoader(), new Class<?>[] {interfaceType},
            (ro, method, args) -> {
//              var interceptors = findInterceptors(method);
//              var invocations = getInvocation(interceptors);
              var invocations = getInvocationFromCache(method);
              return invocations.proceed(instance,method,args);
            }));
  }

  // TODO
}
