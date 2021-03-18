package org.hibernate.bugs;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
public class Parent {

    @Id
    @GeneratedValue(generator = "ID_GENERATOR")
    private Long id;

    private String name;

    @OneToMany(fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.DETACH, CascadeType.MERGE, CascadeType.REMOVE},
            orphanRemoval = true,
            mappedBy = "parent")
    public List<Child> children = new ArrayList<>();

    public Parent() {
    }

    public List<Child> getChildren() {
        return children;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Parent parent = (Parent) o;
        return Objects.equals(id, parent.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
