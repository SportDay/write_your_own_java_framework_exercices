package com.github.forax.framework.orm;

import org.h2.jdbcx.JdbcDataSource;

import java.beans.BeanInfo;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ORM {

  private static final String DEFAULT_VALUE = "VARCHAR(255)";
  private static final Map<Class<?>, String> TYPE_MAPPING =
          Map.of(int.class, "INTEGER", Integer.class, "INTEGER", long.class, "BIGINT", Long.class, "BIGINT",
                  String.class, "VARCHAR(255)");
  private static final ThreadLocal<Connection> DATA_THREAD_LOCAL = new ThreadLocal<>();

  private ORM() {
    throw new AssertionError();
  }

  private static Class<?> findBeanTypeFromRepository(Class<?> repositoryType) {
    var repositorySupertype = Arrays.stream(repositoryType.getGenericInterfaces()).flatMap(superInterface -> {
      if (superInterface instanceof ParameterizedType parameterizedType &&
              parameterizedType.getRawType() == Repository.class) {
        return Stream.of(parameterizedType);
      }
      return null;
    }).findFirst().orElseThrow(
            () -> new IllegalArgumentException("invalid repository interface " + repositoryType.getName()));
    var typeArgument = repositorySupertype.getActualTypeArguments()[0];
    if (typeArgument instanceof Class<?> beanType) {
      return beanType;
    }
    throw new IllegalArgumentException(
            "invalid type argument " + typeArgument + " for repository interface " + repositoryType.getName());
  }

  static Connection currentConnection() {
    var connecion = DATA_THREAD_LOCAL.get();
    if (connecion == null) {
      throw new IllegalStateException();
    }
    return connecion;
  }


  // --- do not change the code above

  //TODO

  //Chaque thread a ca propre case de memoire locale donca pas de porbleme de concurence.

  public static void transaction(JdbcDataSource dataSource, TransactionBlock block) throws SQLException {
    Objects.requireNonNull(dataSource);
    Objects.requireNonNull(block);

    try (var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      DATA_THREAD_LOCAL.set(connection);
      try {
        block.run();
      } catch (UncheckedSQLException e) {
        connection.rollback();
        throw e.getCause();
      }
      connection.commit();
    } finally {
      DATA_THREAD_LOCAL.remove();
    }
  }

  static String findTableName(Class<?> beanType) {
    var annotation = beanType.getAnnotation(Table.class);
    if (annotation != null) {
      return annotation.value().toUpperCase(Locale.ROOT);
    }
    return beanType.getSimpleName().toUpperCase(Locale.ROOT);
  }

  static String findColumnName(PropertyDescriptor property) {
    var annotation = property.getReadMethod().getAnnotation(Column.class);
    if (annotation != null) {
      return annotation.value().toUpperCase(Locale.ROOT);
    }
    return property.getDisplayName().toUpperCase(Locale.ROOT);
  }

  private static boolean isPrimaryKey(PropertyDescriptor p) {
    return p.getReadMethod().getAnnotation(Id.class) != null;
  }

  private static boolean isAutoIncrement(PropertyDescriptor p) {
    return p.getReadMethod().getAnnotation(GeneratedValue.class) != null;
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

    if (isAutoIncrement(p)) {
      other += " AUTO_INCREMENT";
    }

    return "`" + name + "` " + type + other;
  }

  public static void createTable(Class<?> beanType) throws SQLException {
    Objects.requireNonNull(beanType);
    var beanInfo = Utils.beanInfo(beanType);
    var tableName = findTableName(beanType);


    var propertyDescriptors =
            Arrays.stream(beanInfo.getPropertyDescriptors()).filter(p -> !p.getName().equals("class")).toList();

    var sb = new StringBuilder();
    sb.append("CREATE TABLE ").append(tableName).append(" (\n");

    var separator = "";
    String primaryCollumn = null;
    for (var p : propertyDescriptors) {
      if (isPrimaryKey(p)) {
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
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(query);
    }
  }

  public static <R extends Repository<?, ?>> R createRepository(Class<R> type) {
    var beanType = findBeanTypeFromRepository(type);
    var beanInfo = Utils.beanInfo(beanType);
    var constructor = Utils.defaultConstructor(beanType);
    var tableName = findTableName(beanType);
    var primaryProperty = findId(beanInfo);
    var idName = primaryProperty == null ? null : findColumnName(primaryProperty);

    var findAllQuery = "SELECT * FROM " + tableName;
    var findByIdQuery = "SELECT * FROM " + tableName + " WHERE " + idName + " = ?";
    return type.cast(
            Proxy.newProxyInstance(Repository.class.getClassLoader(), new Class[] {type}, (o, method, args) -> {
              var connection = currentConnection();
              try {
                return switch (method.getName()) {
                  case "findAll" -> findAll(connection, findAllQuery, beanInfo, constructor);
                  case "save" -> save(connection, tableName, beanInfo, args[0], primaryProperty);
                  case "findById" -> findAll(connection, findByIdQuery, beanInfo, constructor, args[0]).stream().findFirst();
                  case "toString", "hashCode", "equals" -> throw new UnsupportedOperationException();
                  default -> throw new IllegalStateException();
                };
              } catch (SQLException e){
                throw new UncheckedSQLException(e);
              }
            }));
  }

  static List<?> findAll(Connection connection, String sqlQuery, BeanInfo beanInfo, Constructor<?> constructor, Object ... args)
          throws SQLException {
    try (var statement = connection.prepareStatement(sqlQuery)) {
      if (args != null){
        int index = 1;
        for(var arg : args){
          statement.setObject(index++, arg);
        }
      }
      try (var resultSet = statement.executeQuery()) {
        var toReturn = new ArrayList<>();
        while (resultSet.next()) {
          var bean = toEntityClass(beanInfo, constructor, resultSet);
          toReturn.add(bean);
        }
        return toReturn;
      }
    }
  }


  static Object toEntityClass(BeanInfo beanInfo, Constructor<?> constructor, ResultSet resultSet) throws SQLException {
    var bean = Utils.newInstance(constructor);
    var index = 1;
    for (var property : beanInfo.getPropertyDescriptors()) {
      if (property.getName().equals("class")) {
        continue;
      }
      var setter = property.getWriteMethod();
      var value = resultSet.getObject(index++);
      Utils.invokeMethod(bean, setter, value);
    }
    return bean;
  }

  public static Object save(Connection connection, String tableName, BeanInfo beanInfo, Object bean, PropertyDescriptor idProperty) throws SQLException {
    var sqlQuery = createMergeQuery(tableName, beanInfo);
    try (var statement = connection.prepareStatement(sqlQuery, Statement.RETURN_GENERATED_KEYS)) {
        int index = 1;
        for(var property : beanInfo.getPropertyDescriptors()){
          if(property.getName().equals("class")){
            continue;
          }
          var readMethod = property.getReadMethod();
          var result = Utils.invokeMethod(bean, readMethod);
          statement.setObject(index++, result);
        }
        statement.executeUpdate();

        if(idProperty != null) {
          try (var resultSet = statement.getGeneratedKeys()) {
            if (resultSet.next()) {
              var key = resultSet.getObject(1);
              Utils.invokeMethod(bean, idProperty.getWriteMethod(), key);
            }
          }
        }
    }
    return bean;
  }

  static PropertyDescriptor findId(BeanInfo beanInfo){
    return Arrays.stream(beanInfo.getPropertyDescriptors()).filter(ORM::isPrimaryKey).findFirst().orElse(null);
  }

  @FunctionalInterface
  public interface TransactionBlock {
    void run() throws SQLException;
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


  static String createSaveQuery(String tableName, BeanInfo beanInfo){
    var propertyDescriptors = beanInfo.getPropertyDescriptors();
    return "INSERT INTO " + tableName + " " + Arrays.stream(propertyDescriptors).filter(p -> !p.getName().equals("class")).map(e -> findColumnName(e).toLowerCase(
            Locale.ROOT)).collect(
            Collectors.joining(", ", "(", ")")) + " VALUES (" + String.join(", ", Collections.nCopies(propertyDescriptors.length - 1, "?")) + ");";
  }


  static String createMergeQuery(String tableName, BeanInfo beanInfo){
    var propertyDescriptors = beanInfo.getPropertyDescriptors();
    return "MERGE INTO " + tableName + " " + Arrays.stream(propertyDescriptors).filter(p -> !p.getName().equals("class")).map(e -> findColumnName(e).toLowerCase(
            Locale.ROOT)).collect(
            Collectors.joining(", ", "(", ")")) + " VALUES (" + String.join(", ", Collections.nCopies(propertyDescriptors.length - 1, "?")) + ");";
  }

}
