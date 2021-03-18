package org.hibernate.bugs;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

@Entity
public class Child {

    @Id
    @GeneratedValue(generator = "ID_GENERATOR")
    protected Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PARENT_ID", nullable = false)
    protected Parent parent;

    private String name = "default";

    public Child() {
    }

    public Child(Parent parent) {
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}