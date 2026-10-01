package io.jettra.core.three.d.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HumanEntity {
    public float x, y, z;
    public float rotation;
    public float targetX, targetY, targetZ;
    public String name = "Citizen";
    public String action = "IDLE";
    public List<String> history = new ArrayList<>();
    public int r = 255, g = 255, b = 255, a = 255;
    public float animTimer;
    public boolean isSelected;
    public float buildTimer;
    public float energy = 100.0f;
    public float socialNeed = 50.0f;
    public float intelligence = 1.0f;
    public float homeX, homeY, homeZ;
    public boolean hasHome;
    public String currentThought = "";
    public boolean isWolf;
    public boolean isAnimal;
    public boolean isCar;
    public boolean isMachine;
    public int entityType;
    public String dream = "";
    public String job = "Ciudadano";
    public float appCooldown;
    public float thoughtTimer;
    public boolean isTraveling;
    public float travelTimer;
    
    // PECS Model - Physical
    public float hunger = 100; // 100 is full, 0 is starving
    public float thirst = 100; // 100 is full, 0 is dehydrated
    public float health = 100;
    public float mood = 100;
    public float stamina = 100;
    public float metabolismRate = 0.5f;
    public int age = 20;
    public String gender = "Other";
    public float infectionLevel = 0; // 0 to 100
    public boolean isDead = false;

    // PECS Model - Emotional (Personality Big Five)
    public float openness = 0.5f;
    public float conscientiousness = 0.5f;
    public float extraversion = 0.5f;
    public float agreeableness = 0.5f;
    public float neuroticism = 0.5f;

    // PECS Model - Cognitive
    public String currentGoal = "Explorando";
    
    // PECS Model - Social
    public String spouse = "";
    public List<String> children = new ArrayList<>();
    public List<String> friends = new ArrayList<>();
    public List<String> rivals = new ArrayList<>();
    public String familyID;
    public String partner;

    public HumanEntity() {
        this.metabolismRate = 0.01f + (float)Math.random() * 0.04f;
        this.gender = Math.random() > 0.5 ? "Male" : "Female";
        this.age = 18 + (int)(Math.random() * 40);
        
        // Randomize Personality
        this.openness = (float)Math.random();
        this.conscientiousness = (float)Math.random();
        this.extraversion = (float)Math.random();
        this.agreeableness = (float)Math.random();
        this.neuroticism = (float)Math.random();
        this.intelligence = 0.5f + (float)Math.random() * 0.5f;
    }

    public void addHistory(String msg) {
        history.add(msg);
        if (history.size() > 3) {
            history.remove(0);
        }
    }
}
