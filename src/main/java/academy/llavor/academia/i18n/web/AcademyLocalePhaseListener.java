package academy.llavor.academia.i18n.web;
import jakarta.faces.event.*;
public final class AcademyLocalePhaseListener implements PhaseListener {
    private static final long serialVersionUID=1L;
    public PhaseId getPhaseId(){return PhaseId.RESTORE_VIEW;}
    public void beforePhase(PhaseEvent event){}
    public void afterPhase(PhaseEvent event){var faces=event.getFacesContext();if(faces.getViewRoot()!=null){var language=faces.getApplication().evaluateExpressionGet(faces,"#{academyLanguageBean}",AcademyLanguageBean.class);faces.getViewRoot().setLocale(language.getLocale());}}
}
