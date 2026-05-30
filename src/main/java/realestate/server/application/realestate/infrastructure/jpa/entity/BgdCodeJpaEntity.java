package realestate.server.application.realestate.infrastructure.jpa.entity;

import realestate.server.application.common.entity.BaseEntity;
import realestate.server.application.realestate.domain.AbolishStatus;
import realestate.server.application.realestate.domain.BgdCode;
import realestate.server.application.realestate.infrastructure.jpa.converter.AbolishStatusConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Table(name = "bgd_code")
public class BgdCodeJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String bgdCode;

    private String bgdName;

    @Convert(converter = AbolishStatusConverter.class)
    private AbolishStatus abolishStatus;

    public BgdCode toDomain() {
        return new BgdCode(bgdCode, bgdName);
    }

}
