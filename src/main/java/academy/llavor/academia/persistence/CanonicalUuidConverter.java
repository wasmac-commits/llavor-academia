package academy.llavor.academia.persistence;

import academy.llavor.academia.provisioning.validation.ProvisioningValidation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.UUID;

/** Portable VARCHAR(36); no provider-specific binary UUID representation. */
@Converter
public class CanonicalUuidConverter implements AttributeConverter<UUID, String> {
    @Override public String convertToDatabaseColumn(UUID value) {
        return value == null ? null : ProvisioningValidation.uuid(value).toString();
    }
    @Override public UUID convertToEntityAttribute(String value) {
        return value == null ? null : ProvisioningValidation.parseUuid(value);
    }
}
