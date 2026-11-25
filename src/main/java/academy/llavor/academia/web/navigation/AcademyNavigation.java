package academy.llavor.academia.web.navigation;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;
@Named @RequestScoped
public class AcademyNavigation {
    @Inject AuthorizedLearningNavigation source;
    private List<AuthorizedLearningNavigation.Entry> units;
    public List<AuthorizedLearningNavigation.Entry> getUnits(){
        if(units==null)units=List.copyOf(source.snapshot());
        return units;
    }
}
