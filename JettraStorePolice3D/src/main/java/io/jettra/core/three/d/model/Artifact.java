package io.jettra.core.three.d.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Artifact {
    public float x, y, z;
    public float scale;
    public int r, g, b, a = 255;
    public int type; // 0: House, 1: School, 2: City Center, 3: Radio
    public String creator;
    public String info;

    public Artifact() {}
}
