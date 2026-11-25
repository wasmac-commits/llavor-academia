package academy.llavor.academia.i18n;
import jakarta.persistence.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class I18nSchemaTest {
    @Test void preparedMigrationMatchesEveryMappedColumnAndVersion()throws Exception{
        String sql=Files.readString(Path.of("database/migrations/V005__local_interface_catalog.sql"));
        for(Class<?> type:List.of(academy.llavor.academia.i18n.entity.LocalCatalog.class)){
            String name=type.getAnnotation(Table.class).name();
            Matcher table=Pattern.compile("CREATE TABLE "+name+" \\(([\\s\\S]*?)\\) ENGINE=").matcher(sql);assertTrue(table.find(),name);String ddl=table.group(1);
            Set<String> mapped=new HashSet<>(),declared=new HashSet<>();
            var columns=Pattern.compile("(?m)^    ([a-z_]+) (BIGINT|VARCHAR\\([0-9]+\\)|TINYINT\\(1\\)|DATETIME\\(6\\)|LONGTEXT)[^\\r\\n]*,").matcher(ddl);
            while(columns.find())declared.add(columns.group(1));
            for(Class<?> current=type;current!=Object.class;current=current.getSuperclass())for(Field field:current.getDeclaredFields()){
                if(Modifier.isStatic(field.getModifiers()))continue;Column c=field.getAnnotation(Column.class);
                String column=c!=null&&!c.name().isEmpty()?c.name():field.getName();mapped.add(column);
                var definition=Pattern.compile("(?m)^    "+column+" ([^\\r\\n]+),$").matcher(ddl);assertTrue(definition.find(),name+"."+column);
                String value=definition.group(1);boolean required=field.isAnnotationPresent(Id.class)||(c!=null&&!c.nullable());assertEquals(required,value.contains("NOT NULL"),column);
                String expected=field.isAnnotationPresent(Lob.class)?"LONGTEXT":field.getType()==LocalDateTime.class?"DATETIME(6)":field.getType()==boolean.class?"TINYINT(1)":field.getType()==long.class||field.getType()==Long.class?"BIGINT":"VARCHAR("+c.length()+")";
                assertTrue(value.startsWith(expected),name+"."+column+" "+value);
            }
            assertEquals(mapped,declared,name);assertFalse(type.getAnnotation(Cacheable.class).value());
        }
        assertTrue(sql.contains("PREPARADA, NO EJECUTADA"));
    }
}
