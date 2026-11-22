package academy.llavor.academia.installation.dto;
import academy.llavor.academia.installation.entity.AcademyInstance;
import java.util.UUID;
public record AcademyInstanceView(UUID instanceId,String academyCode,UUID portalInstanceId){public static AcademyInstanceView from(AcademyInstance p){return new AcademyInstanceView(p.getInstanceId(),p.getAcademyCode(),p.getPortalInstanceId());}}
