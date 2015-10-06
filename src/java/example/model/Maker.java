package example.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Created by kawasima on 15/10/02.
 */
@Data
@Entity
public class Maker {
    @Id
    @GeneratedValue
    private Long id;

    private String name;
}
