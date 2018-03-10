package com.asb.otic.model;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class Score {
    private int id;
    private int size;
    private String name;
    private int no_wins;

    public Score() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getNoWins() {
        return no_wins;
    }

    public void setNoWins(int no_wins) {
        this.no_wins = no_wins;
    }
}
