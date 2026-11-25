package academy.llavor.academia.web.navigation;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
/** No published academic navigation source exists in this stage. */
@ApplicationScoped
public class EmptyLearningNavigation implements AuthorizedLearningNavigation {
    public List<Entry> snapshot(){return List.of();}
}
