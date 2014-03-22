package cc.concurrent.mango.runtime.operator;

import cc.concurrent.mango.*;
import cc.concurrent.mango.exception.structure.CacheByAnnotationException;
import cc.concurrent.mango.jdbc.JdbcTemplate;
import cc.concurrent.mango.runtime.*;
import cc.concurrent.mango.util.Strings;
import cc.concurrent.mango.util.reflect.Reflection;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ash
 */
public abstract class AbstractOperator implements Operator {

    protected JdbcTemplate jdbcTemplate;
    protected CacheHandler cacheHandler;
    protected CacheDescriptor cacheDescriptor;

    private DbDescriptor dbDescriptor;
    private DataSourceFactory dataSourceFactory;
    private SQLType sqlType;
    private String[] aliases;

    private final static String TABLE = "table";

    protected AbstractOperator(Method method, SQLType sqlType) {
        this.jdbcTemplate = new JdbcTemplate();
        this.sqlType = sqlType;
        buildAliases(method);
        buildDbDescriptor(method);
        buildCacheDescriptor(method);
    }

    @Override
    public void setDataSourceFactory(DataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
    }

    @Override
    public void setCacheHandler(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    protected Object getCacheKeyObj(RuntimeContext context) {
        return context.getPropertyValue(cacheDescriptor.getParameterName(), cacheDescriptor.getPropertyPath());
    }

    protected String getSingleKey(RuntimeContext context) {
        return getKey(getCacheKeyObj(context));
    }

    protected String getKey(Object keyObj) {
        return cacheDescriptor.getPrefix() + keyObj;
    }

    protected TypeContext buildTypeContext(Type[] methodArgTypes) {
        Map<String, Type> parameterTypeMap = new HashMap<String, Type>();
        String table = dbDescriptor.getTable();
        if (!Strings.isNullOrEmpty(table)) { // 在@DB中设置过全局表名
            parameterTypeMap.put(TABLE, String.class);
        }
        for (int i = 0; i < methodArgTypes.length; i++) {
            parameterTypeMap.put(getParameterNameByIndex(i), methodArgTypes[i]);
        }
        return new TypeContextImpl(parameterTypeMap);
    }

    protected RuntimeContext buildRuntimeContext(Object[] methodArgs) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        String table = dbDescriptor.getTable();
        if (!Strings.isNullOrEmpty(table)) { // 在@DB中设置过全局表名
            parameters.put(TABLE, table);
        }
        for (int i = 0; i < methodArgs.length; i++) {
            parameters.put(getParameterNameByIndex(i), methodArgs[i]);
        }
        return new RuntimeContextImpl(parameters);
    }

    protected DataSource getDataSource() {
        return dataSourceFactory.getDataSource(dbDescriptor.getDataSourceName(), sqlType);
    }

    private void buildDbDescriptor(Method method) {
        String dataSourceName = "";
        String table = "";
        DB dbAnno = method.getDeclaringClass().getAnnotation(DB.class);
        if (dbAnno != null) {
            dataSourceName = dbAnno.dataSource();
            table = dbAnno.table();
        }
        dbDescriptor = new DbDescriptor(dataSourceName, table);
    }

    private void buildCacheDescriptor(Method method) {
        Class<?> daoClass = method.getDeclaringClass();
        Cache cacheAnno = daoClass.getAnnotation(Cache.class);
        cacheDescriptor = new CacheDescriptor();
        if (cacheAnno != null) { // dao类使用cache
            CacheIgnored cacheIgnoredAnno = method.getAnnotation(CacheIgnored.class);
            if (cacheIgnoredAnno == null) { // method不禁用cache
                cacheDescriptor.setUseCache(true);
                cacheDescriptor.setPrefix(cacheAnno.prefix());
                cacheDescriptor.setExpire(Reflection.instantiate(cacheAnno.expire()));
                cacheDescriptor.setNum(cacheAnno.num());
                Annotation[][] pass = method.getParameterAnnotations();
                int num = 0;
                for (int i = 0; i < pass.length; i++) {
                    Annotation[] pas = pass[i];
                    for (Annotation pa : pas) {
                        if (CacheBy.class.equals(pa.annotationType())) {
                            cacheDescriptor.setParameterName(getParameterNameByIndex(i));
                            cacheDescriptor.setPropertyPath(((CacheBy) pa).value());
                            num++;
                        }
                    }
                }
                if (num != 1) { //TODO 合适得异常处理
                    throw new CacheByAnnotationException("need 1 but " + num);
                }
            }
        }
    }

    private void buildAliases(Method method) {
        Annotation[][] pass = method.getParameterAnnotations();
        aliases = new String[pass.length];
        for (int i = 0; i < pass.length; i++) {
            Annotation[] pas = pass[i];
            for (Annotation pa : pas) {
                if (Rename.class.equals(pa.annotationType())) {
                    aliases[i] = ((Rename) pa).value();
                }
            }
        }
    }

    private String getParameterNameByIndex(int index) {
        String alias = aliases[index];
        return alias != null ? alias : String.valueOf(index + 1);
    }


}