package example.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * @author kawasima
 */
@Data
@Entity
public class Postal {
    @Id
    @GeneratedValue
    private Long id;

    private String code;
    private String oldPostalCd;
    private String postalCd;

    private String prefectureName;
    private String cityName;
    private String townName;

    private String prefectureKanaName;
    private String cityKanaName;
    private String townKanaName;

    private Boolean option1;
    private Boolean option2;
    private Boolean option3;
    private Boolean option4;
    private Boolean option5;
    private String option6;

}
