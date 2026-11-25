package academy.llavor.academia.web.navigation;
import java.util.List;
/** Presentation adapter for a future locally installed authorized navigation snapshot.
 * Implementations must not fetch Portal during rendering. No course/content contract yet.
 * Every future destination must independently enforce its backend authorization. */
public interface AuthorizedLearningNavigation {
    List<Entry> snapshot();
    record Entry(String labelKey,String outcome) {
        public String getLabelKey(){return labelKey;}
        public String getOutcome(){return outcome;}
    }
}
