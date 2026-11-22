package academy.llavor.academia.parameter.security;
import java.util.function.Function;
/** Technical-only opaque material. Never Serializable, a record or a bean-readable value. */
public final class SensitiveParameterValue {
 private final String material;
 SensitiveParameterValue(String material){this.material=material;}
 public <T>T use(Function<String,T> operation){return operation.apply(material);}
 @Override public String toString(){return "[protected parameter]";}
}
