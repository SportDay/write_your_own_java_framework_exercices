package com.github.forax.framework.orm;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ORM {

  private static final String DEFAULT_VALUE = "VARCHAR(255)";

  private ORM() {
    throw new AssertionError();
  }

  @FunctionalInterface
  public interface TransactionBlock {
    void run() throws SQLException;
  }

  private static final Map<Class<?>, String> TYPE_MAPPING = Map.of(
          int.class, "INTEGER",
          Integer.class, "INTEGER",
          long.class, "BIGINT",
          Long.class, "BIGINT",
          String.class, "VARCHAR(255)"
  );

  private static Class<?> findBeanTypeFromRepository(Class<?> repositoryType) {
    var repositorySupertype = Arrays.stream(repositoryType.getGenericInterfaces())
            .flatMap(superInterface -> {
              if (superInterface instanceof ParameterizedType parameterizedType
                      && parameterizedType.getRawType() == Repository.class) {
                return Stream.of(parameterizedType);
              }
              return null;
            })
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("invalid repository interface " + repositoryType.getName()));
    var typeArgument = repositorySupertype.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException("invalid type argument " + typeArgument + " for repository interface " + repositoryType.getName());
  }

  private static class UncheckedSQLException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 42L;

    private UncheckedSQLException(SQLException cause) {
      super(cause);
    }

    @Override
    public SQLException getCause() {
      return (SQLException) super.getCause();
    }
  }


  // --- do not change the code above

  //TODO

  //Chaque thread a ca propre case de memoire locale donca pas de porbleme de concurence.

  private static final ThreadLocal<Connection> DATA_THREAD_LOCAL = new ThreadLocal<>();


  static Connection currentConnection() {
    var connecion = DATA_THREAD_LOCAL.get();
    if (connecion == null) {
      throw new IllegalStateException();
    }
    return connecion;
  }

  public static void transaction(JdbcDataSource dataSource, TransactionBlock block) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(block);

    try(var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      DATA_THREAD_LOCAL.set(connection);
      block.run();
      try {
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      }
    }finally {
      DATA_THREAD_LOCAL.remove();
    }
  }

  static String findTableName(Class<?> beanType){
    var annotation = beanType.getAnnotation(Table.class);
    if (annotation != null){
      return annotation.value().toUpperCase(Locale.ROOT);
    }
    return  beanType.getSimpleName().toUpperCase(Locale.ROOT);
  }

  static String findColumnName(PropertyDescriptor property){
    var annotation = property.getReadMethod().getAnnotation(Column.class);
    if (annotation != null){
      return annotation.value().toUpperCase(Locale.ROOT);
    }
    return property.getDisplayName().toUpperCase(Locale.ROOT);
  }

  private static boolean isPrimaryKey(PropertyDescriptor p){
    return p.getReadMethod().getAnnotation(Id.class) != null;
  }

  private static String dbbType(PropertyDescriptor p) {
    var name = findColumnName(p);
    var method = p.getReadMethod();
    var rawType = p.getPropertyType();

    var type = TYPE_MAPPING.getOrDefault(rawType, DEFAULT_VALUE);
    var other = "";


    if (rawType.isPrimitive()) {
      other += " NOT NULL";
    }

    if (method.getAnnotation(GeneratedValue.class) != null){
      other += " AUTO_INCREMENT";
    }

    return "`" + name + "` " + type  + other;
  }

  public static void createTable(Class<?> beanType) throws SQLException {
    Objects.requireNonNull(beanType);
    var beanInfo = Utils.beanInfo(beanType);
    var tableName = findTableName(beanType);


    var propertyDescriptors = Arrays.stream(beanInfo.getPropertyDescriptors())
            .filter(p -> !p.getName().equals("class")).toList();

    var sb = new StringBuilder();
    sb.append("CREATE TABLE ").append(tableName).append(" (\n");

    var separator = "";
    String primaryCollumn = null;
    for(var p : propertyDescriptors){
      if (isPrimaryKey(p)){
        primaryCollumn = findColumnName(p);
      }
      sb.append(separator).append(dbbType(p));
      separator = ",\n";
    }

    if (primaryCollumn != null) {
      sb.append(separator).append("PRIMARY KEY(").append(primaryCollumn).append(")");
    }
    sb.append(");\n");

    var query = sb.toString();

    var connection = currentConnection();
    try(Statement statement = connection.createStatement()) {
      statement.executeUpdate(query);
    }
  }
}
