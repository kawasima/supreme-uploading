package example.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.time.LocalDate;
import java.util.List;

/**
 * @author kawasima
 */
@Entity
@Data
public class Television {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String description;
    private Float size;
    private Integer pixelWidth;
    private Integer pixelHeight;
    private Integer speed;
    private BacklightType backlight;
    private Boolean splitWindow;
    private List<Tuner> tunerList;
    private Boolean lanPort;
    private Boolean dlnaReady;
    private Boolean dtcpIpReady;
    private Boolean wirelessReady;
    private Float powerConsumption;
    private Float weight;

    private LocalDate launchDate;
    private LocalDate registeredDate;

    @ManyToOne
    private Maker maker;
}
