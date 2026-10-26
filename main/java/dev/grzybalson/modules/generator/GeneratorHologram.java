package dev.grzybalson.modules.generator;

public class GeneratorHologram {

    private String name;
    private String location;

    public GeneratorHologram(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return this.location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
