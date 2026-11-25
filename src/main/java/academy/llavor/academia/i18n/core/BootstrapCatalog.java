package academy.llavor.academia.i18n.core;
import java.time.Instant;
import java.util.*;
/** Minimal recovery catalog only; business UI remains untouched in this stage. */
public final class BootstrapCatalog {
    private BootstrapCatalog(){}
    public static CatalogSnapshot spanish(UUID portal){return new CatalogSnapshot(1,portal,"ui",0,Instant.EPOCH,
        Map.of("es","Espa\u00f1ol"),Map.of("es",Map.of("common.language.select","Idioma","common.action.continue","Continuar","common.greeting","Hola, {0}")));}
}
