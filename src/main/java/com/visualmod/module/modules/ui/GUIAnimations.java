package com.visualmod.ui;

public class GUIAnimations {

    public float animate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }
}
